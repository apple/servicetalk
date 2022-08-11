/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcPayloadWriter;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterService;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singleton;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

class ParentConnectionContextTest {

    private final BlockingQueue<Throwable> errors = new LinkedBlockingQueue<>();

    @ParameterizedTest(name = "{index}: blocking={0}")
    @ValueSource(booleans = {false, true})
    void test(boolean blocking) throws Exception {
        try (ServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .listenAndAwait(blocking ? new BlockingTesterServiceImpl() : new TesterServiceImpl());
             BlockingTesterClient client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                     .buildBlocking(new TesterProto.Tester.ClientFactory())) {

            client.test(TestRequest.getDefaultInstance());
            client.testBiDiStream(singleton(TestRequest.getDefaultInstance())).forEach(__ -> { /* noop */ });
            client.testResponseStream(TestRequest.getDefaultInstance()).forEach(__ -> { /* noop */ });
            client.testRequestStream(singleton(TestRequest.getDefaultInstance()));
        }
        assertNoAsyncErrors(errors);
    }

    private final class TesterServiceImpl implements TesterService {

        @Override
        public Single<TestResponse> test(GrpcServiceContext ctx, TestRequest request) {
            assertServiceContext(ctx, errors);
            return succeeded(TestResponse.getDefaultInstance());
        }

        @Override
        public Publisher<TestResponse> testBiDiStream(GrpcServiceContext ctx, Publisher<TestRequest> request) {
            assertServiceContext(ctx, errors);
            return request.ignoreElements().concat(from(TestResponse.getDefaultInstance()));
        }

        @Override
        public Publisher<TestResponse> testResponseStream(GrpcServiceContext ctx, TestRequest request) {
            assertServiceContext(ctx, errors);
            return from(TestResponse.getDefaultInstance());
        }

        @Override
        public Single<TestResponse> testRequestStream(GrpcServiceContext ctx, Publisher<TestRequest> request) {
            assertServiceContext(ctx, errors);
            return request.ignoreElements().concat(succeeded(TestResponse.getDefaultInstance()));
        }
    }

    private final class BlockingTesterServiceImpl implements BlockingTesterService {

        @Override
        public TestResponse test(GrpcServiceContext ctx, TestRequest request) {
            assertServiceContext(ctx, errors);
            return TestResponse.getDefaultInstance();
        }

        @Override
        public void testBiDiStream(GrpcServiceContext ctx, BlockingIterable<TestRequest> request,
                                   GrpcPayloadWriter<TestResponse> responseWriter) throws Exception {
            assertServiceContext(ctx, errors);
            request.forEach(__ -> { /* noop */ });
            responseWriter.write(TestResponse.getDefaultInstance());
            responseWriter.close();
        }

        @Override
        public void testResponseStream(GrpcServiceContext ctx, TestRequest request,
                                       GrpcPayloadWriter<TestResponse> responseWriter) throws Exception {
            assertServiceContext(ctx, errors);
            responseWriter.write(TestResponse.getDefaultInstance());
            responseWriter.close();
        }

        @Override
        public TestResponse testRequestStream(GrpcServiceContext ctx, BlockingIterable<TestRequest> request) {
            assertServiceContext(ctx, errors);
            request.forEach(__ -> { /* noop */ });
            return TestResponse.getDefaultInstance();
        }
    }

    private static void assertServiceContext(GrpcServiceContext ctx, BlockingQueue<Throwable> errors) {
        try {
            assertThat(ctx, is(notNullValue()));
            ConnectionContext parent = ctx.parent();
            assertThat("gRPC stream must have reference to its parent ConnectionContext",
                    parent, is(notNullValue()));
            assertThat("Unexpected localAddress",
                    parent.localAddress(), is(sameInstance(ctx.localAddress())));
            assertThat("Unexpected remoteAddress",
                    parent.remoteAddress(), is(sameInstance(ctx.remoteAddress())));
            assertThat("Unexpected parent.parent()", parent.parent(), is(nullValue()));
        } catch (Throwable t) {
            errors.add(t);
        }
    }
}
