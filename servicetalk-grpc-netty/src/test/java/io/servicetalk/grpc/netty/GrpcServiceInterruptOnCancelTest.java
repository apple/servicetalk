/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.grpc.netty;

import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.BlockingStreamingGrpcServerResponse;
import io.servicetalk.grpc.api.DefaultGrpcClientMetadata;
import io.servicetalk.grpc.api.GrpcClientMetadata;
import io.servicetalk.grpc.api.GrpcPayloadWriter;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterService;
import io.servicetalk.grpc.netty.TesterProto.Tester.ClientFactory;
import io.servicetalk.http.api.DisableInterruptOnCancelHttpServiceFilter;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.grpc.api.GrpcStatusCode.DEADLINE_EXCEEDED;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A blocking gRPC route is adapted through the same {@code BlockingHttpService} conversion as a plain HTTP blocking
 * service, so the filter governs it when appended on the underlying HTTP server builder through
 * {@code GrpcServerBuilder#initializeHttp}. No gRPC-specific plumbing is involved.
 */
class GrpcServiceInterruptOnCancelTest {

    private static final TestRequest REQUEST = TestRequest.newBuilder().setName("test").build();
    private static final int AWAIT_S = 5;
    private static final int MAX_MESSAGES = 100;
    private static final int MESSAGES_AFTER_FAILURE = 2;
    private static final long MESSAGE_GAP_MILLIS = 10;

    @ParameterizedTest(name = "{displayName} [{index}] disableInterrupt={0}")
    @ValueSource(booleans = {false, true})
    void blockingRouteRespectsTheFilter(boolean disableInterrupt) throws Exception {
        AtomicBoolean interrupted = new AtomicBoolean();
        CountDownLatch cancelObserved = new CountDownLatch(1);
        CountDownLatch cancelLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(1);

        BlockingTesterService service = new BlockingTesterService() {
            @Override
            public TestResponse test(final GrpcServiceContext ctx, final TestRequest request) {
                try {
                    cancelLatch.await();
                } catch (InterruptedException e) {
                    interrupted.set(true);
                }
                if (Thread.interrupted()) {
                    interrupted.set(true);
                }
                doneLatch.countDown();
                return TestResponse.newBuilder().setMessage(request.getName()).build();
            }
        };

        ServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .initializeHttp(builder -> {
                    builder.appendServiceFilter(cancelObserver(cancelObserved));
                    if (disableInterrupt) {
                        builder.appendServiceFilter(DisableInterruptOnCancelHttpServiceFilter.INSTANCE);
                    }
                })
                .listenAndAwait(service);
        try {
            try (BlockingTesterClient client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                    .buildBlocking(new ClientFactory())) {
                GrpcClientMetadata metadata = new DefaultGrpcClientMetadata(ofMillis(200));
                GrpcStatusException e = assertThrows(GrpcStatusException.class,
                        () -> client.test(metadata, REQUEST));
                assertThat(e.status().code(), is(DEADLINE_EXCEEDED));
            }
            // The deadline is enforced client-side, so the server-side cancellation can arrive after the client has
            // already failed. Wait for it, otherwise releasing the handler here would race the interrupt and the
            // assertion below would pass even when the interrupt was armed.
            assertThat("the deadline must cancel the server-side response",
                    cancelObserved.await(AWAIT_S, SECONDS), is(true));
            if (disableInterrupt) {
                cancelLatch.countDown();
            }

            assertThat("when an interrupt is expected it must be the only thing that releases the service thread",
                    doneLatch.await(AWAIT_S, SECONDS), is(true));
            assertThat("a blocking gRPC route must follow the filter setting",
                    interrupted.get(), is(!disableInterrupt));
        } finally {
            cancelLatch.countDown();
            serverContext.closeAsync().toFuture().get();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] disableInterrupt={0}")
    @ValueSource(booleans = {false, true})
    void blockingStreamingRouteRespectsTheFilter(boolean disableInterrupt) throws Exception {
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicReference<Throwable> writeFailure = new AtomicReference<>();
        CountDownLatch firstResponseSent = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(1);

        BlockingTesterService service = new BlockingTesterService() {
            @Override
            public void testResponseStream(final GrpcServiceContext ctx, final TestRequest request,
                                           final BlockingStreamingGrpcServerResponse<TestResponse> response)
                    throws Exception {
                GrpcPayloadWriter<TestResponse> writer = response.sendMetaData();
                try {
                    writer.write(TestResponse.newBuilder().setMessage("first").build());
                    writer.flush();
                    firstResponseSent.countDown();
                    int messagesAfterFailure = 0;
                    for (int i = 0; i < MAX_MESSAGES && messagesAfterFailure < MESSAGES_AFTER_FAILURE; i++) {
                        if (sleepWasInterrupted(MESSAGE_GAP_MILLIS) || Thread.interrupted()) {
                            interrupted.set(true);
                        }
                        try {
                            writer.write(TestResponse.newBuilder().setMessage("x").build());
                            writer.flush();
                        } catch (Exception e) {
                            // gRPC serializes before writing, so the first failure arrives wrapped in a
                            // SerializationException rather than as the IOException the HTTP layer throws.
                            writeFailure.compareAndSet(null, e);
                        }
                        if (writeFailure.get() != null) {
                            messagesAfterFailure++;
                        }
                    }
                    writer.close();
                } catch (Exception e) {
                    writeFailure.compareAndSet(null, e);
                } finally {
                    doneLatch.countDown();
                }
            }
        };

        ServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .initializeHttp(builder -> {
                    if (disableInterrupt) {
                        builder.appendServiceFilter(DisableInterruptOnCancelHttpServiceFilter.INSTANCE);
                    }
                })
                .listenAndAwait(service);
        try {
            try (BlockingTesterClient client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                    .buildBlocking(new ClientFactory())) {
                BlockingIterator<TestResponse> iterator = client.testResponseStream(REQUEST).iterator();
                assertThat("the route never produced its first response", iterator.hasNext(), is(true));
                iterator.next();
                // The client gives up on the rest of the stream, which cancels the server-side response body.
                iterator.close();
            }

            assertThat("the route must finish regardless of the client having abandoned the stream",
                    doneLatch.await(AWAIT_S, SECONDS), is(true));
            assertThat("a write against the abandoned stream must fail whatever the filter decides",
                    writeFailure.get(), is(notNullValue()));
            assertThat("the failure must report the terminated payload writer, not something else",
                    hasIoExceptionCause(writeFailure.get()), is(true));
            assertThat("a blocking-streaming gRPC route must follow the filter setting",
                    interrupted.get(), is(!disableInterrupt));
        } finally {
            serverContext.closeAsync().toFuture().get();
        }
    }

    private static boolean hasIoExceptionCause(final Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException) {
                return true;
            }
        }
        return false;
    }

    private static boolean sleepWasInterrupted(final long millis) {
        try {
            Thread.sleep(millis);
            return false;
        } catch (InterruptedException e) {
            return true;
        }
    }

    private static StreamingHttpServiceFilterFactory cancelObserver(final CountDownLatch cancelObserved) {
        return delegate -> new StreamingHttpServiceFilter(delegate) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                return delegate().handle(ctx, request, responseFactory).afterCancel(cancelObserved::countDown);
            }
        };
    }
}
