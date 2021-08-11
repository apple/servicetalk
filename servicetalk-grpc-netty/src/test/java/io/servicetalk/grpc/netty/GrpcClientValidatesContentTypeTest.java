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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.grpc.api.GrpcExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singletonList;

final class GrpcClientValidatesContentTypeTest {
    @Nullable
    private ServerContext serverContext;
    @Nullable
    private TesterProto.Tester.BlockingTesterClient client;

    void setUp(boolean withCharset) throws Exception {
        serverContext = GrpcServers.forAddress(localAddress(0))
                .appendHttpServiceFilter(service -> new StreamingHttpServiceFilter(service) {
                    @Override
                    public Single<StreamingHttpResponse> handle(
                            final HttpServiceContext ctx, final StreamingHttpRequest request,
                            final StreamingHttpResponseFactory responseFactory) {
                        return delegate().handle(ctx, request, responseFactory).map(resp -> {
                            resp.headers().set(CONTENT_TYPE, resp.headers().get(CONTENT_TYPE) +
                                    (withCharset ? "; charset=UTF-8" : ""));
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

    @ParameterizedTest(name = "with-charset={0}")
    @ValueSource(booleans = {true, false})
    void testBlockingAggregated(boolean withCharset) throws Exception {
        setUp(withCharset);
        client.test(newRequest());
    }

    @ParameterizedTest(name = "with-charset={0}")
    @ValueSource(booleans = {true, false})
    void testBlockingRequestStreaming(boolean withCharset) throws Exception {
        setUp(withCharset);
        client.testRequestStream(singletonList(newRequest()));
    }

    @ParameterizedTest(name = "with-charset={0}")
    @ValueSource(booleans = {true, false})
    void testBlockingResponseStreaming(boolean withCharset) throws Exception {
        setUp(withCharset);
        client.testResponseStream(newRequest()).forEach(__ -> { /* noop */ });
    }

    @ParameterizedTest(name = "with-charset={0}")
    @ValueSource(booleans = {true, false})
    void testBlockingBiDiStreaming(boolean withCharset) throws Exception {
        setUp(withCharset);
        client.testBiDiStream(singletonList(newRequest()))
                .forEach(__ -> { /* noop */ });
    }

    @ParameterizedTest(name = "with-charset={0}")
    @ValueSource(booleans = {true, false})
    void testAggregated(boolean withCharset) throws Exception {
        setUp(withCharset);
        client.asClient().test(newRequest()).toFuture().get();
    }

    @ParameterizedTest(name = "with-charset={0}")
    @ValueSource(booleans = {true, false})
    void testRequestStreaming(boolean withCharset) throws Exception {
        setUp(withCharset);
        client.asClient().testRequestStream(from(newRequest())).toFuture().get();
    }

    @ParameterizedTest(name = "with-charset={0}")
    @ValueSource(booleans = {true, false})
    void testResponseStreaming(boolean withCharset) throws Exception {
        setUp(withCharset);
        client.asClient().testResponseStream(newRequest()).toFuture().get();
    }

    @ParameterizedTest(name = "with-charset={0}")
    @ValueSource(booleans = {true, false})
    void testBiDiStreaming(boolean withCharset) throws Exception {
        setUp(withCharset);
        client.asClient().testBiDiStream(from(newRequest())).toFuture().get();
    }

    private static TestRequest newRequest() {
        return TestRequest.newBuilder().setName("request").build();
    }

    private TestResponse newResponse() {
        return TestResponse.newBuilder().setMessage("response").build();
    }
}
