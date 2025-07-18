/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.grpc.api.BlockingStreamingGrpcServerResponse;
import io.servicetalk.grpc.api.GrpcPayloadWriter;
import io.servicetalk.grpc.api.GrpcServerContext;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterService;
import io.servicetalk.grpc.netty.TesterProto.Tester.TestBiDiStreamRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.TestRequestStreamRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.TestResponseStreamRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.TestRpc;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import io.servicetalk.transport.api.ConnectionInfo;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

class ConnectionContextToStringTest {

    @ParameterizedTest(name = "{displayName} [{index}] blocking={0}")
    @ValueSource(booleans = {false, true})
    void test(boolean blocking) throws Exception {
        try (GrpcServerContext server = GrpcServers.forAddress(localAddress(0))
                .listenAndAwait(blocking ? new BlockingTesterServiceImpl() : new TesterServiceImpl());
             TesterProto.Tester.BlockingTesterClient client = GrpcClients.forAddress(serverHostAndPort(server))
                     .buildBlocking(new TesterProto.Tester.ClientFactory())) {

            assertConnectionIdAndString(TestRpc.methodDescriptor().javaMethodName(), client.test(newRequest()));
            assertConnectionIdAndString(TestBiDiStreamRpc.methodDescriptor().javaMethodName(),
                    getFirst(client.testBiDiStream(singletonList(newRequest()))));
            assertConnectionIdAndString(TestResponseStreamRpc.methodDescriptor().javaMethodName(),
                    getFirst(client.testResponseStream(newRequest())));
            assertConnectionIdAndString(TestRequestStreamRpc.methodDescriptor().javaMethodName(),
                    client.testRequestStream(singletonList(newRequest())));
        }
    }

    private static void assertConnectionIdAndString(String endpoint, TestResponse response) {
        assertThat("GrpcServiceContext connectionId does not match expected pattern for endpoint: " + endpoint,
                response.getConnectionId(), matchesPattern("^0x[0-9a-fA-F]{8}$"));
        assertThat("GrpcServiceContext does not contain netty channel id for endpoint: " + endpoint,
                response.getMessage(), allOf(containsString("[id: 0x"), containsString(response.getConnectionId())));
    }

    private static TestRequest newRequest() {
        return TestRequest.newBuilder().setName("foo").build();
    }

    private static TestResponse newResponse(GrpcServiceContext ctx) {
        ConnectionInfo parent = ctx.parent();
        return TestResponse.newBuilder()
                .setMessage(ctx.toString())
                .setConnectionId(parent != null ? parent.connectionId() : ctx.connectionId())
                .build();
    }

    private static TestResponse getFirst(BlockingIterable<TestResponse> iterable) throws Exception {
        TestResponse response;
        try (BlockingIterator<TestResponse> iterator = iterable.iterator()) {
            if (!iterator.hasNext()) {
                throw new AssertionError("Empty iterable");
            }
            response = iterator.next();
            assertThat(response, is(notNullValue()));
            if (iterator.hasNext()) {
                throw new AssertionError("Expected one item, but found 2+");
            }
        }
        return response;
    }

    private static final class TesterServiceImpl implements TesterService {

        @Override
        public Single<TestResponse> test(GrpcServiceContext ctx, TestRequest request) {
            return succeeded(newResponse(ctx));
        }

        @Override
        public Publisher<TestResponse> testBiDiStream(GrpcServiceContext ctx, Publisher<TestRequest> request) {
            return request.ignoreElements().concat(from(newResponse(ctx)));
        }

        @Override
        public Publisher<TestResponse> testResponseStream(GrpcServiceContext ctx, TestRequest request) {
            return from(newResponse(ctx));
        }

        @Override
        public Single<TestResponse> testRequestStream(GrpcServiceContext ctx, Publisher<TestRequest> request) {
            return request.ignoreElements().concat(succeeded(newResponse(ctx)));
        }
    }

    private static final class BlockingTesterServiceImpl implements BlockingTesterService {

        @Override
        public TestResponse test(GrpcServiceContext ctx, TestRequest request) throws Exception {
            return newResponse(ctx);
        }

        @Override
        public void testBiDiStream(GrpcServiceContext ctx, BlockingIterable<TestRequest> request,
                                   GrpcPayloadWriter<TestResponse> responseWriter) {
            throw new UnsupportedOperationException("deprecated");
        }

        @Override
        public void testBiDiStream(GrpcServiceContext ctx, BlockingIterable<TestRequest> request,
                                   BlockingStreamingGrpcServerResponse<TestResponse> response) throws Exception {
            request.forEach(__ -> { /* ignore */ });
            try (GrpcPayloadWriter<TestResponse> responseWriter = response.sendMetaData()) {
                responseWriter.write(newResponse(ctx));
            }
        }

        @Override
        public void testResponseStream(GrpcServiceContext ctx, TestRequest request,
                                       GrpcPayloadWriter<TestResponse> responseWriter) throws Exception {
            throw new UnsupportedOperationException("deprecated");
        }

        @Override
        public void testResponseStream(GrpcServiceContext ctx, TestRequest request,
                                       BlockingStreamingGrpcServerResponse<TestResponse> response) throws Exception {
            try (GrpcPayloadWriter<TestResponse> responseWriter = response.sendMetaData()) {
                responseWriter.write(newResponse(ctx));
            }
        }

        @Override
        public TestResponse testRequestStream(GrpcServiceContext ctx, BlockingIterable<TestRequest> request) {
            return newResponse(ctx);
        }
    }
}
