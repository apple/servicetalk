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

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.grpc.api.GrpcPayloadWriter;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterService;
import io.servicetalk.grpc.netty.TesterProto.Tester.ClientFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.ServiceFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singleton;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@RunWith(Parameterized.class)
public class GrpcServiceContextTest {

    private static final String EXPECTED_PROTOCOL_NAME = "gRPC";

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final ServerContext serverContext;
    private final BlockingTesterClient client;

    public GrpcServiceContextTest(boolean streamingService) throws Exception {
        serverContext = GrpcServers.forAddress(localAddress(0)).listenAndAwait(streamingService ?
                new ServiceFactory(new TesterServiceImpl()) :
                new ServiceFactory(new BlockingTesterServiceImpl()));

        client = GrpcClients.forAddress(serverHostAndPort(serverContext)).buildBlocking(new ClientFactory());
    }

    @Parameters(name = "streamingService={0}")
    public static Object[] params() {
        return new Object[]{true, false};
    }

    @After
    public void tearDown() throws Exception {
        try {
            client.close();
        } finally {
            serverContext.close();
        }
    }

    @Test
    public void testAggregated() throws Exception {
        assertResponse(client.test(newRequest()));
    }

    @Test
    public void testRequestStream() throws Exception {
        assertResponse(client.testRequestStream(singleton(newRequest())));
    }

    @Test
    public void testBiDiStream() throws Exception {
        try (BlockingIterator<TestResponse> iterator = client.testBiDiStream(singleton(newRequest())).iterator()) {
            assertResponse(iterator.next());
            assertThat(iterator.hasNext(), is(false));
        }
    }

    @Test
    public void testResponseStream() throws Exception {
        try (BlockingIterator<TestResponse> iterator = client.testResponseStream(newRequest()).iterator()) {
            assertResponse(iterator.next());
            assertThat(iterator.hasNext(), is(false));
        }
    }

    private static void assertResponse(@Nullable TestResponse response) {
        assertThat(response, is(notNullValue()));
        assertThat(response.getMessage(), equalTo(EXPECTED_PROTOCOL_NAME));
    }

    private static TestRequest newRequest() {
        return TestRequest.newBuilder().setName("request").build();
    }

    private static TestResponse newResponse(GrpcServiceContext ctx) {
        return TestResponse.newBuilder().setMessage(ctx.protocol().name()).build();
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
        }

        @Override
        public void testResponseStream(GrpcServiceContext ctx, TestRequest request,
                                       GrpcPayloadWriter<TestResponse> responseWriter) throws Exception {
            responseWriter.write(newResponse(ctx));
        }
    }
}
