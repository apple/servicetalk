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

import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.ClientFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.ServiceFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.closeAsyncGracefully;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.never;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.concurrent.TimeUnit.SECONDS;

class PendingStreamingClosureTest {

    @Nullable
    private TesterClient client;
    @Nullable
    private ServerContext ctx;
    private boolean graceful;

    private void setUp(boolean graceful) {
        this.graceful = graceful;
    }

    @BeforeEach
    void beforeEach() throws Exception {
        ctx = GrpcServers.forAddress(localAddress(0))
                .listenAndAwait(new ServiceFactory(new DefaultService()));
        client = GrpcClients.forAddress(serverHostAndPort(ctx))
                .build(new ClientFactory());
    }

    @AfterEach
    void tearDown() throws Exception {
        CompositeCloseable closeable = newCompositeCloseable().appendAll(client, ctx);
        if (graceful) {
            closeAsyncGracefully(closeable, 1, SECONDS).toFuture().get();
        } else {
            closeable.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void biDiStream(boolean graceful) throws Exception {
        setUp(graceful);
        CountDownLatch latch = new CountDownLatch(1);
        client.testBiDiStream(succeeded(TestRequest.newBuilder().setName("name").build()).concat(never()))
                .beforeOnNext(__ -> latch.countDown())
                .ignoreElements().subscribe();
        // Make sure we started the request-response on both client and server and then close in teardown;
        latch.await();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void requestStream(boolean graceful) throws Exception {
        setUp(graceful);
        CountDownLatch latch = new CountDownLatch(1);
        client.testRequestStream(succeeded(TestRequest.newBuilder().setName("name").build())
                .concat(defer(() -> {
                    latch.countDown();
                    return never();
                })))
                .ignoreElement().subscribe();
        // Make sure we started the request-response on both client and server and then close in teardown;
        latch.await();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void responseStream(boolean graceful) throws Exception {
        setUp(graceful);
        CountDownLatch latch = new CountDownLatch(1);
        client.testResponseStream(TestRequest.newBuilder().setName("name").build())
                .beforeOnNext(__ -> latch.countDown())
                .ignoreElements().subscribe();
        // Make sure we started the request-response on both client and server and then close in teardown;
        latch.await();
    }

    private static final class DefaultService implements TesterService {

        @Override
        public Single<TestResponse> test(final GrpcServiceContext ctx, final TestRequest request) {
            return succeeded(TestResponse.newBuilder().setMessage("foo").build());
        }

        @Override
        public Publisher<TestResponse> testBiDiStream(final GrpcServiceContext ctx,
                                                      final Publisher<TestRequest> request) {
            return request.map(__ -> TestResponse.newBuilder().setMessage("foo").build());
        }

        @Override
        public Publisher<TestResponse> testResponseStream(final GrpcServiceContext ctx, final TestRequest request) {
            return ctx.executionContext().executor().timer(1, SECONDS)
                    .toSingle()
                    .map(__ -> TestResponse.newBuilder().setMessage("foo").build())
                    .repeat(value -> true);
        }

        @Override
        public Single<TestResponse> testRequestStream(final GrpcServiceContext ctx,
                                                      final Publisher<TestRequest> request) {
            return request.collect(() -> null, (testResponse, __) -> testResponse)
                    .map(o -> TestResponse.newBuilder().setMessage("foo").build());
        }
    }
}
