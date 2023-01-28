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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class ConnectionContextToStringTest {

    @ParameterizedTest(name = "{displayName} [{index}] blocking={0}")
    @ValueSource(booleans = {false, true})
    void test(boolean blocking) throws Exception {
        try (GrpcServerContext server = GrpcServers.forAddress(localAddress(0))
                .listenAndAwait(blocking ? new BlockingTesterServiceImpl() : new TesterServiceImpl());
             TesterProto.Tester.BlockingTesterClient client = GrpcClients.forAddress(serverHostAndPort(server))
                     .buildBlocking(new TesterProto.Tester.ClientFactory())) {

            assertThat("GrpcServiceContext doesn't contain netty channel id for endpoint" +
                            TestRpc.methodDescriptor().javaMethodName(),
                    client.test(newRequest()).getMessage(), containsString("[id: "));
            assertThat("GrpcServiceContext doesn't contain netty channel id for endpoint" +
                            TestBiDiStreamRpc.methodDescriptor().javaMethodName(),
                    readMessage(client.testBiDiStream(singletonList(newRequest()))), containsString("[id: "));
            assertThat("GrpcServiceContext doesn't contain netty channel id for endpoint" +
                            TestResponseStreamRpc.methodDescriptor().javaMethodName(),
                    readMessage(client.testResponseStream(newRequest())), containsString("[id: "));
            assertThat("GrpcServiceContext doesn't contain netty channel id for endpoint" +
                            TestRequestStreamRpc.methodDescriptor().javaMethodName(),
                    client.testRequestStream(singletonList(newRequest())).getMessage(), containsString("[id: "));
        }
    }

    private static TestRequest newRequest() {
        return TestRequest.newBuilder().setName("foo").build();
    }

    private static TestResponse newResponse(GrpcServiceContext ctx) {
        return TestResponse.newBuilder().setMessage(ctx.toString()).build();
    }

    private static String readMessage(BlockingIterable<TestResponse> iterable) {
        BlockingIterator<TestResponse> iterator = iterable.iterator();
        if (iterator.hasNext()) {
            TestResponse response = iterator.next();
            assertThat(response, is(notNullValue()));
            // Discard all other items
            while (iterator.hasNext()) {
                iterator.next();
            }
            return response.getMessage();
        }
        throw new AssertionError("Empty iterable");
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
