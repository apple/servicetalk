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

import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.ClientFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.ServiceFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.servicetalk.concurrent.api.AsyncCloseables.closeAsyncGracefully;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.never;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;

@RunWith(Parameterized.class)
public class PendingStreamingClosureTest {

    private final TesterClient client;
    private final ServerContext ctx;

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    private final boolean graceful;

    public PendingStreamingClosureTest(boolean graceful) throws Exception {
        this.graceful = graceful;
        ctx = GrpcServers.forAddress(localAddress(0))
                .listenAndAwait(new ServiceFactory(new DefaultService()));
        client = GrpcClients.forAddress(serverHostAndPort(ctx))
                .build(new ClientFactory());
    }

    @Parameterized.Parameters(name = "graceful? {0}")
    public static Collection<Boolean> data() {
        return asList(true, false);
    }

    @After
    public void tearDown() throws Exception {
        CompositeCloseable closeable = newCompositeCloseable().appendAll(client, ctx);
        if (graceful) {
            closeAsyncGracefully(closeable, 1, SECONDS).toFuture().get();
        } else {
            closeable.close();
        }
    }

    @Test
    public void biDiStream() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        client.testBiDiStream(succeeded(TestRequest.newBuilder().setName("name").build()).concat(never()))
                .beforeOnNext(__ -> latch.countDown())
                .ignoreElements().subscribe();
        // Make sure we started the request-response on both client and server and then close in teardown;
        latch.await();
    }

    @Test
    public void requestStream() throws Exception {
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

    @Test
    public void responseStream() throws Exception {
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
