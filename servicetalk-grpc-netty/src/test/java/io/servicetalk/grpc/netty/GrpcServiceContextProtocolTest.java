/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcPayloadWriter;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterService;
import io.servicetalk.grpc.netty.TesterProto.Tester.ClientFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.ServiceFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singleton;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class GrpcServiceContextProtocolTest {
    @Nullable
    private String expectedValue;
    @Nullable
    private ServerContext serverContext;
    @Nullable
    private BlockingTesterClient client;

    private void setUp(HttpProtocolVersion httpProtocol, boolean streamingService) throws Exception {
        expectedValue = "gRPC over " + httpProtocol;

        serverContext = GrpcServers.forAddress(localAddress(0))
                .protocols(protocolConfig(httpProtocol))
                .listenAndAwait(streamingService ?
                        new ServiceFactory(new TesterServiceImpl()) :
                        new ServiceFactory(new BlockingTesterServiceImpl()));

        client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                .protocols(protocolConfig(httpProtocol))
                .buildBlocking(new ClientFactory());
    }

    static Stream<Arguments> params() {
        return Stream.of(Arguments.of(HTTP_2_0, true),
                Arguments.of(HTTP_2_0, false),
                Arguments.of(HTTP_1_1, true),
                Arguments.of(HTTP_1_1, false));
    }

    private static HttpProtocolConfig protocolConfig(HttpProtocolVersion httpProtocol) {
        if (HTTP_2_0.equals(httpProtocol)) {
            return h2Default();
        }
        if (HTTP_1_1.equals(httpProtocol)) {
            return h1Default();
        }
        throw new IllegalArgumentException("Unknown httpProtocol: " + httpProtocol);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            client.close();
        } finally {
            serverContext.close();
        }
    }

    @ParameterizedTest(name = "httpVersion={0) streamingService={0)")
    @MethodSource("params")
    void testAggregated(HttpProtocolVersion httpProtocol, boolean streamingService) throws Exception {
        setUp(httpProtocol, streamingService);
        assertResponse(client.test(newRequest()));
    }

    @ParameterizedTest(name = "httpVersion={0) streamingService={0)")
    @MethodSource("params")
    void testRequestStream(HttpProtocolVersion httpProtocol, boolean streamingService) throws Exception {
        setUp(httpProtocol, streamingService);
        assertResponse(client.testRequestStream(Arrays.asList(newRequest(), newRequest())));
    }

    @ParameterizedTest(name = "httpVersion={0) streamingService={0)")
    @MethodSource("params")
    void testBiDiStream(HttpProtocolVersion httpProtocol, boolean streamingService) throws Exception {
        setUp(httpProtocol, streamingService);
        try (BlockingIterator<TestResponse> iterator = client.testBiDiStream(singleton(newRequest())).iterator()) {
            assertResponse(iterator.next());
            assertThat(iterator.hasNext(), is(false));
        }
    }

    @ParameterizedTest(name = "httpVersion={0) streamingService={0)")
    @MethodSource("params")
    void testResponseStream(HttpProtocolVersion httpProtocol, boolean streamingService) throws Exception {
        setUp(httpProtocol, streamingService);
        try (BlockingIterator<TestResponse> iterator = client.testResponseStream(newRequest()).iterator()) {
            assertResponse(iterator.next());
            assertThat(iterator.hasNext(), is(false));
        }
    }

    private void assertResponse(@Nullable TestResponse response) {
        assertThat(response, is(notNullValue()));
        assertThat(response.getMessage(), equalTo(expectedValue));
    }

    private static TestRequest newRequest() {
        return TestRequest.newBuilder().setName("request").build();
    }

    private static TestResponse newResponse(GrpcServiceContext ctx) {
        return TestResponse.newBuilder()
                .setMessage(ctx.protocol().name() + " over " + ctx.protocol().httpProtocol())
                .build();
    }

    private static class TesterServiceImpl implements TesterService {

        @Override
        public Single<TestResponse> test(GrpcServiceContext ctx, TestRequest request) {
            return succeeded(newResponse(ctx));
        }

        @Override
        public Single<TestResponse> testRequestStream(GrpcServiceContext ctx, Publisher<TestRequest> request) {
            return succeeded(newResponse(ctx));
        }

        @Override
        public Publisher<TestResponse> testBiDiStream(GrpcServiceContext ctx, Publisher<TestRequest> request) {
            return from(newResponse(ctx));
        }

        @Override
        public Publisher<TestResponse> testResponseStream(GrpcServiceContext ctx, TestRequest request) {
            return from(newResponse(ctx));
        }
    }

    private static class BlockingTesterServiceImpl implements BlockingTesterService {
        @Override
        public TestResponse test(GrpcServiceContext ctx, TestRequest request) {
            return newResponse(ctx);
        }

        @Override
        public TestResponse testRequestStream(GrpcServiceContext ctx,
                                              BlockingIterable<TestRequest> request) {
            return newResponse(ctx);
        }

        @Override
        public void testBiDiStream(GrpcServiceContext ctx, BlockingIterable<TestRequest> request,
                                   GrpcPayloadWriter<TestResponse> responseWriter) throws Exception {
            responseWriter.write(newResponse(ctx));
            responseWriter.close();
        }

        @Override
        public void testResponseStream(GrpcServiceContext ctx, TestRequest request,
                                       GrpcPayloadWriter<TestResponse> responseWriter) throws Exception {
            responseWriter.write(newResponse(ctx));
            responseWriter.close();
        }
    }
}
