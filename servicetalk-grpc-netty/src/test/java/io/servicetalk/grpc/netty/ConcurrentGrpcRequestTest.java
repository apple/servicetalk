/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.Processors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SourceAdapters;
import io.servicetalk.concurrent.internal.RejectedSubscribeException;
import io.servicetalk.grpc.api.DefaultGrpcClientMetadata;
import io.servicetalk.grpc.api.GrpcClientMetadata;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.ClientFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.ExecutorExtension.withCachedExecutor;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConcurrentGrpcRequestTest {

    private enum AsyncVariant {
        TEST,
        TEST_REQUEST_STREAM,
        TEST_RESPONSE_STREAM,
        TEST_BI_DI_STREAM,
        BLOCKING_TEST,
        BLOCKING_TEST_REQUEST_STREAM,
        BLOCKING_TEST_RESPONSE_STREAM,
        BLOCKING_TEST_BI_DI_STREAM
    }

    @RegisterExtension
    static final ExecutorExtension<Executor> executorExtension = withCachedExecutor().setClassLevel(true);

    private final CountDownLatch receivedFirstRequest = new CountDownLatch(1);
    private final AtomicInteger receivedRequests = new AtomicInteger();
    private final CompletableSource.Processor responseProcessor = Processors.newCompletableProcessor();
    private final ServerContext serverCtx;

    ConcurrentGrpcRequestTest() throws Exception {
        serverCtx = GrpcServers.forAddress(localAddress(0))
                .initializeHttp(builder -> builder.appendServiceFilter(s -> new StreamingHttpServiceFilter(s) {
                    @Override
                    public Single<StreamingHttpResponse> handle(HttpServiceContext ctx,
                                                                StreamingHttpRequest request,
                                                                StreamingHttpResponseFactory responseFactory) {
                        receivedFirstRequest.countDown();
                        Single<StreamingHttpResponse> response = delegate().handle(ctx, request, responseFactory);
                        if (receivedRequests.incrementAndGet() == 1) {
                            return response.concat(SourceAdapters.fromSource(responseProcessor));
                        }
                        return response;
                    }
                }))
                .listenAndAwait(new TesterService() {

                    @Override
                    public Single<TestResponse> test(GrpcServiceContext ctx, TestRequest request) {
                        return newResponse();
                    }

                    @Override
                    public Single<TestResponse> testRequestStream(GrpcServiceContext ctx,
                                                                  Publisher<TestRequest> request) {
                        return newResponse();
                    }

                    @Override
                    public Publisher<TestResponse> testResponseStream(GrpcServiceContext ctx, TestRequest request) {
                        return newResponse().toPublisher();
                    }

                    @Override
                    public Publisher<TestResponse> testBiDiStream(GrpcServiceContext ctx,
                                                                  Publisher<TestRequest> request) {
                        return newResponse().toPublisher();
                    }

                    private Single<TestResponse> newResponse() {
                        return Single.succeeded(TestResponse.newBuilder().setMessage("msg").build());
                    }
                });
    }

    @AfterEach
    void tearDown() throws Exception {
        serverCtx.close();
    }

    private static List<Arguments> asyncVariants() {
        List<Arguments> arguments = new ArrayList<>();
        for (AsyncVariant variant : AsyncVariant.values()) {
            arguments.add(Arguments.of(true, variant));
            // Blocking calls without metadata always create a new underlying request, there is no risk
            if (!variant.name().startsWith("BLOCKING")) {
                arguments.add(Arguments.of(false, variant));
            }
        }
        return arguments;
    }

    @ParameterizedTest(name = "{displayName} [{index}] withMetadata={0} variant={1}")
    @MethodSource("asyncVariants")
    void test(boolean withMetadata, AsyncVariant variant) throws Exception {
        GrpcClientMetadata metadata = withMetadata ? new DefaultGrpcClientMetadata() : null;
        try (TesterClient client = GrpcClients.forAddress(serverHostAndPort(serverCtx)).build(new ClientFactory())) {
            Single<TestResponse> firstSingle = newSingle(variant, client, metadata);
            Future<TestResponse> first = firstSingle.toFuture();
            receivedFirstRequest.await();
            Future<TestResponse> firstConcurrent = firstSingle.toFuture();
            Future<TestResponse> secondConcurrent = newSingle(variant, client, metadata).toFuture();

            assertRejected(firstConcurrent);
            if (metadata != null) {
                assertRejected(secondConcurrent);
            } else {
                // Requests are independent when metadata is not shared between them
                assertResponse(secondConcurrent);
            }
            responseProcessor.onComplete();
            assertResponse(first);

            // Sequential requests should be successful:
            assertResponse(firstSingle.toFuture());
            assertResponse(newSingle(variant, client, metadata).toFuture());
        }
        assertThat(receivedRequests.get(), is(metadata != null ? 3 : 4));
    }

    private static Single<TestResponse> newSingle(AsyncVariant variant, TesterClient client,
                                                  @Nullable GrpcClientMetadata metadata) {
        switch (variant) {
            case TEST:
                return metadata == null ?
                        client.test(newRequest()) :
                        client.test(metadata, newRequest());
            case TEST_REQUEST_STREAM:
                return metadata == null ?
                        client.testRequestStream(newStreamingRequest()) :
                        client.testRequestStream(metadata, newStreamingRequest());
            case TEST_RESPONSE_STREAM:
                return (metadata == null ?
                        client.testResponseStream(newRequest()) :
                        client.testResponseStream(metadata, newRequest()))
                        .firstOrError();
            case TEST_BI_DI_STREAM:
                return (metadata == null ?
                        client.testBiDiStream(newStreamingRequest()) :
                        client.testBiDiStream(metadata, newStreamingRequest()))
                        .firstOrError();
            case BLOCKING_TEST:
                return executorExtension.executor().submit(() -> metadata == null ?
                        client.asBlockingClient().test(newRequest()) :
                        client.asBlockingClient().test(metadata, newRequest()));
            case BLOCKING_TEST_REQUEST_STREAM:
                return executorExtension.executor().submit(() -> metadata == null ?
                        client.asBlockingClient().testRequestStream(newIterableRequest()) :
                        client.asBlockingClient().testRequestStream(metadata, newIterableRequest()));
            case BLOCKING_TEST_RESPONSE_STREAM:
                return executorExtension.executor().submit(() -> (metadata == null ?
                        client.asBlockingClient().testResponseStream(newRequest()) :
                        client.asBlockingClient().testResponseStream(metadata, newRequest()))
                        .iterator().next());
            case BLOCKING_TEST_BI_DI_STREAM:
                return executorExtension.executor().submit(() -> (metadata == null ?
                        client.asBlockingClient().testBiDiStream(newIterableRequest()) :
                        client.asBlockingClient().testBiDiStream(metadata, newIterableRequest()))
                        .iterator().next());
            default:
                throw new AssertionError("Unexpected variant: " + variant);
        }
    }

    private static TestRequest newRequest() {
        return TestRequest.newBuilder().setName("foo").build();
    }

    private static Publisher<TestRequest> newStreamingRequest() {
        return Publisher.from(newRequest());
    }

    private static Iterable<TestRequest> newIterableRequest() {
        return Collections.singletonList(newRequest());
    }

    private static void assertRejected(Future<?> future) {
        ExecutionException ee = assertThrows(ExecutionException.class, future::get);
        assertThat(ee.getCause(), is(instanceOf(GrpcStatusException.class)));
        GrpcStatusException gse = (GrpcStatusException) ee.getCause();
        assertThat(gse.getCause(), is(instanceOf(RejectedSubscribeException.class)));
    }

    private static void assertResponse(Future<TestResponse> future) throws Exception {
        assertThat(future.get().getMessage(), is("msg"));
    }
}
