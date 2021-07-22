/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatusCode;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterClient;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StatelessTrailersTransformer;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.grpc.api.GrpcExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcClientRequiresTrailersTest {
    @Nullable
    private ServerContext serverContext;
    @Nullable
    private BlockingTesterClient client;

    private void setUp(boolean hasTrailers) throws Exception {
        serverContext = GrpcServers.forAddress(localAddress(0))
                .appendHttpServiceFilter(service -> new StreamingHttpServiceFilter(service) {
                    @Override
                    public Single<StreamingHttpResponse> handle(
                            final HttpServiceContext ctx, final StreamingHttpRequest request,
                            final StreamingHttpResponseFactory responseFactory) {
                        return delegate().handle(ctx, request, responseFactory).map(resp -> {
                            // Emulate gRPC server that does not add grpc-status to the trailers after payload body:
                            resp.transform(new StatelessTrailersTransformer<Buffer>() {
                                @Override
                                protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                                    trailers.remove("grpc-status");
                                    return hasTrailers ? trailers.set("some-trailer", "some-value") : trailers;
                                }
                            });
                            return resp;
                        });
                    }
                })
                .listenAndAwait(new TesterProto.Tester.TesterService() {
                    @Override
                    public Single<TestResponse> testRequestStream(final GrpcServiceContext ctx,
                                                                  final Publisher<TestRequest> request) {
                        return succeeded(newResponse());
                    }

                    @Override
                    public Publisher<TestResponse> testResponseStream(final GrpcServiceContext ctx,
                                                                      final TestRequest request) {
                        return from(newResponse());
                    }

                    @Override
                    public Publisher<TestResponse> testBiDiStream(final GrpcServiceContext ctx,
                                                                  final Publisher<TestRequest> request) {
                        return from(newResponse());
                    }

                    @Override
                    public Single<TestResponse> test(final GrpcServiceContext ctx, final TestRequest request) {
                        return succeeded(newResponse());
                    }
                });

        client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                .executionStrategy(noOffloadsStrategy())
                .buildBlocking(new TesterProto.Tester.ClientFactory());
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            client.close();
        } finally {
            serverContext.close();
        }
    }

    @ParameterizedTest(name = "has-trailers={0}")
    @ValueSource(booleans = {true, false})
    void testBlockingAggregated(boolean hasTrailers) throws Exception {
        setUp(hasTrailers);
        assertThrowsGrpcStatusException(() -> client.test(newRequest()));
    }

    @ParameterizedTest(name = "has-trailers={0}")
    @ValueSource(booleans = {true, false})
    void testBlockingRequestStreaming(boolean hasTrailers) throws Exception {
        setUp(hasTrailers);
        assertThrowsGrpcStatusException(() -> client.testRequestStream(singletonList(newRequest())));
    }

    @ParameterizedTest(name = "has-trailers={0}")
    @ValueSource(booleans = {true, false})
    void testBlockingResponseStreaming(boolean hasTrailers) throws Exception {
        setUp(hasTrailers);
        assertThrowsGrpcStatusException(() -> client.testResponseStream(newRequest()).forEach(__ -> { /* noop */ }));
    }

    @ParameterizedTest(name = "has-trailers={0}")
    @ValueSource(booleans = {true, false})
    void testBlockingBiDiStreaming(boolean hasTrailers) throws Exception {
        setUp(hasTrailers);
        assertThrowsGrpcStatusException(() -> client.testBiDiStream(singletonList(newRequest()))
                .forEach(__ -> { /* noop */ }));
    }

    @ParameterizedTest(name = "has-trailers={0}")
    @ValueSource(booleans = {true, false})
    void testAggregated(boolean hasTrailers) throws Exception {
        setUp(hasTrailers);
        assertThrowsExecutionException(() -> client.asClient().test(newRequest()).toFuture().get());
    }

    @ParameterizedTest(name = "has-trailers={0}")
    @ValueSource(booleans = {true, false})
    void testRequestStreaming(boolean hasTrailers) throws Exception {
        setUp(hasTrailers);
        assertThrowsExecutionException(() -> client.asClient().testRequestStream(from(newRequest())).toFuture().get());
    }

    @ParameterizedTest(name = "has-trailers={0}")
    @ValueSource(booleans = {true, false})
    void testResponseStreaming(boolean hasTrailers) throws Exception {
        setUp(hasTrailers);
        assertThrowsExecutionException(() -> client.asClient().testResponseStream(newRequest()).toFuture().get());
    }

    @ParameterizedTest(name = "has-trailers={0}")
    @ValueSource(booleans = {true, false})
    void testBiDiStreaming(boolean hasTrailers) throws Exception {
        setUp(hasTrailers);
        assertThrowsExecutionException(() -> client.asClient().testBiDiStream(from(newRequest())).toFuture().get());
    }

    private static TestRequest newRequest() {
        return TestRequest.newBuilder().setName("request").build();
    }

    private static TestResponse newResponse() {
        return TestResponse.newBuilder().setMessage("response").build();
    }

    private static void assertThrowsExecutionException(Executable executable) {
        ExecutionException ex = assertThrows(ExecutionException.class, executable);
        assertThat(ex.getCause(), is(instanceOf(GrpcStatusException.class)));
        assertGrpcStatusException((GrpcStatusException) ex.getCause());
    }

    private static void assertThrowsGrpcStatusException(Executable executable) {
        assertGrpcStatusException(assertThrows(GrpcStatusException.class, executable));
    }

    private static void assertGrpcStatusException(GrpcStatusException grpcStatusException) {
        assertThat(grpcStatusException.status().code(), is(GrpcStatusCode.UNKNOWN));
        assertThat(grpcStatusException.status().description(),
                equalTo("Response does not contain grpc-status header or trailer"));
    }
}
