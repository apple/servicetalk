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

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.grpc.api.GrpcClientMetadata;
import io.servicetalk.grpc.api.GrpcPayloadWriter;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterService;
import io.servicetalk.grpc.netty.TesterProto.Tester.ClientFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.TestBiDiStreamMetadata;
import io.servicetalk.grpc.netty.TesterProto.Tester.TestMetadata;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.grpc.api.GrpcStatusCode.DEADLINE_EXCEEDED;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.time.Duration.ofMillis;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Real server/client (real transport, no mocks) coverage confirming
 * {@link io.servicetalk.grpc.api.GrpcServerBuilder#interruptBlockingServiceOnCancel(boolean)} wires end-to-end
 * through {@code GrpcServiceConfig}/{@code GrpcRouter}, since gRPC routes are converted to
 * {@link io.servicetalk.http.api.StreamingHttpService} internally by {@code GrpcRouter}, before any
 * {@link io.servicetalk.http.api.HttpServerBuilder} ever sees them. Covers both blocking gRPC route shapes
 * ({@code BlockingRoute} and {@code BlockingStreamingRoute}), mirroring
 * {@code HttpPredicateRouterBuilderInterruptOnCancelTest}'s coverage of both
 * {@code BlockingHttpService}/{@code BlockingStreamingHttpService} routes for the predicate router.
 * <p>
 * A client-supplied deadline is used to trigger server-side cancellation deterministically -- it's a timer-based,
 * well-supported ServiceTalk feature, unlike forcing a raw socket disconnect at a precise moment (which was found
 * to be unreliable timing-wise for the equivalent plain-HTTP test).
 */
class GrpcServiceInterruptOnCancelTest {

    private static final TestRequest REQUEST = TestRequest.newBuilder().setName("test").build();

    @ParameterizedTest(name = "{displayName} [{index}] interrupt={0}")
    @ValueSource(booleans = {true, false})
    void blockingRouteRespectsToggle(boolean interrupt) throws Exception {
        AtomicBoolean interrupted = new AtomicBoolean();
        CountDownLatch doneLatch = new CountDownLatch(1);

        BlockingTesterService service = new BlockingTesterService() {
            @Override
            public TestResponse test(final GrpcServiceContext ctx, final TestRequest request) {
                sleepLoopUnrelatedToCancellation(interrupted);
                doneLatch.countDown();
                return TestResponse.newBuilder().setMessage(request.getName()).build();
            }
        };

        ServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .interruptBlockingServiceOnCancel(interrupt)
                .listenAndAwait(service);
        try {
            BlockingTesterClient client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                    .buildBlocking(new ClientFactory());
            try {
                // Deadline shorter than the handler's ~1s sleep loop, so the server-side deadline filter cancels
                // the response while the handler's unrelated blocking work is still running.
                GrpcClientMetadata metadata = new TestMetadata(ofMillis(200));
                GrpcStatusException e = assertThrows(GrpcStatusException.class,
                        () -> client.test(metadata, REQUEST));
                assertThat(e.status().code(), is(DEADLINE_EXCEEDED));
            } finally {
                client.close();
            }
            assertToggleRespected(interrupt, interrupted, doneLatch);
        } finally {
            serverContext.closeAsync().toFuture().get();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] interrupt={0}")
    @ValueSource(booleans = {true, false})
    void blockingStreamingRouteRespectsToggle(boolean interrupt) throws Exception {
        AtomicBoolean interrupted = new AtomicBoolean();
        CountDownLatch doneLatch = new CountDownLatch(1);

        BlockingTesterService service = new BlockingTesterService() {
            @Override
            public void testBiDiStream(final GrpcServiceContext ctx,
                                       final BlockingIterable<TestRequest> request,
                                       final GrpcPayloadWriter<TestResponse> responseWriter) throws Exception {
                try {
                    sleepLoopUnrelatedToCancellation(interrupted);
                } finally {
                    responseWriter.close();
                    doneLatch.countDown();
                }
            }
        };

        ServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .interruptBlockingServiceOnCancel(interrupt)
                .listenAndAwait(service);
        try {
            BlockingTesterClient client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                    .buildBlocking(new ClientFactory());
            try {
                // Deadline shorter than the handler's ~1s sleep loop, so the server-side deadline filter cancels
                // the response while the handler's unrelated blocking work is still running.
                GrpcClientMetadata metadata = new TestBiDiStreamMetadata(ofMillis(200));
                GrpcStatusException e = assertThrows(GrpcStatusException.class, () -> {
                    for (TestResponse ignored : client.testBiDiStream(metadata, singletonList(REQUEST))) {
                        // Drain until the deadline-exceeded error surfaces from the iterator.
                    }
                });
                assertThat(e.status().code(), is(DEADLINE_EXCEEDED));
            } finally {
                client.close();
            }
            assertToggleRespected(interrupt, interrupted, doneLatch);
        } finally {
            serverContext.closeAsync().toFuture().get();
        }
    }

    private static void sleepLoopUnrelatedToCancellation(AtomicBoolean interrupted) {
        // Unrelated blocking work that has nothing to do with the (to-be-)cancelled response, mirroring the
        // downstream bug report -- long enough to outlast the client's deadline above.
        for (int i = 0; i < 50; i++) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
            if (Thread.interrupted()) {
                interrupted.set(true);
            }
        }
    }

    private static void assertToggleRespected(boolean interrupt, AtomicBoolean interrupted, CountDownLatch doneLatch)
            throws InterruptedException {
        assertThat("handler must terminate without hanging the connection open",
                doneLatch.await(5, SECONDS), is(true));
        assertThat("interruptBlockingServiceOnCancel(" + interrupt + ") must control whether a blocking gRPC " +
                "route is interrupted on cancellation", interrupted.get(), is(interrupt));
    }
}
