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
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.api.GrpcServerBuilder;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.ServiceFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import io.servicetalk.http.netty.H2ProtocolConfig;
import io.servicetalk.http.netty.HttpProtocolConfigs;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.grpc.api.GrpcExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.netty.H2KeepAlivePolicies.disabled;
import static io.servicetalk.http.netty.H2KeepAlivePolicies.whenIdleFor;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static io.servicetalk.transport.api.ServiceTalkSocketOptions.IDLE_TIMEOUT;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.cached;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class KeepAliveTest {

    @ClassRule
    public static final ExecutionContextRule SERVER_CTX = cached("server-io", "server-executor");
    @ClassRule
    public static final ExecutionContextRule CLIENT_CTX = cached("client-io", "client-executor");

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout(1, MINUTES);

    private final TesterClient client;
    private final ServerContext ctx;
    private final long idleTimeoutMillis;

    public KeepAliveTest(final boolean keepAlivesFromClient,
                         final Duration keepAliveIdleFor,
                         final Duration idleTimeout) throws Exception {
        this.idleTimeoutMillis = idleTimeout.toMillis();
        GrpcServerBuilder serverBuilder = GrpcServers.forAddress(localAddress(0))
                .ioExecutor(SERVER_CTX.ioExecutor())
                .executionStrategy(defaultStrategy(SERVER_CTX.executor()));
        if (!keepAlivesFromClient) {
            serverBuilder.protocols(h2Config(keepAliveIdleFor));
        } else {
            serverBuilder.socketOption(IDLE_TIMEOUT, idleTimeoutMillis)
                    .protocols(h2Config(null));
        }
        ctx = serverBuilder.listenAndAwait(new ServiceFactory(new InfiniteStreamsService()));
        GrpcClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                GrpcClients.forAddress(serverHostAndPort(ctx))
                        .ioExecutor(CLIENT_CTX.ioExecutor())
                        .executionStrategy(defaultStrategy(CLIENT_CTX.executor()));
        if (keepAlivesFromClient) {
            clientBuilder.protocols(h2Config(keepAliveIdleFor));
        } else {
            clientBuilder.socketOption(IDLE_TIMEOUT, idleTimeoutMillis)
                    .protocols(h2Config(null));
        }
        client = clientBuilder.build(new TesterProto.Tester.ClientFactory());
    }

    @Parameterized.Parameters(name = "keepAlivesFromClient? {0}, keepAliveIdleFor: {1}, idleTimeout: {2}")
    public static Object[][] data() {
        return new Object[][] {
                new Object[] {true, ofSeconds(1), ofSeconds(2)},
                new Object[] {false, ofSeconds(1), ofSeconds(2)},
        };
    }

    private static H2ProtocolConfig h2Config(@Nullable final Duration keepAliveIdleFor) {
        return HttpProtocolConfigs.h2()
                .enableFrameLogging("servicetalk-tests-h2-frame-logger", TRACE, () -> true)
                .keepAlivePolicy(keepAliveIdleFor == null ? disabled() : whenIdleFor(keepAliveIdleFor))
                .build();
    }

    @After
    public void tearDown() throws Exception {
        CompositeCloseable closeable = newCompositeCloseable().appendAll(client, ctx);
        closeable.close();
    }

    @Test
    public void bidiStream() throws Exception {
        try {
            client.testBiDiStream(never()).toFuture().get(idleTimeoutMillis + 100, MILLISECONDS);
            fail("Unexpected response available.");
        } catch (TimeoutException e) {
            // expected
        }
    }

    @Test
    public void requestStream() throws Exception {
        try {
            client.testRequestStream(never()).toFuture().get(idleTimeoutMillis + 100, MILLISECONDS);
            fail("Unexpected response available.");
        } catch (TimeoutException e) {
            // expected
        }
    }

    @Test
    public void responseStream() throws Exception {
        try {
            client.testResponseStream(TestRequest.newBuilder().build())
                    .toFuture().get(idleTimeoutMillis + 100, MILLISECONDS);
            fail("Unexpected response available.");
        } catch (TimeoutException e) {
            // expected
        }
    }

    private static final class InfiniteStreamsService implements TesterService {

        @Override
        public Publisher<TestResponse> testBiDiStream(final GrpcServiceContext ctx,
                                                      final Publisher<TestRequest> request) {
            return request.map(testRequest -> TestResponse.newBuilder().build());
        }

        @Override
        public Single<TestResponse> testRequestStream(final GrpcServiceContext ctx,
                                                      final Publisher<TestRequest> request) {
            return request.collect(() -> null, (testResponse, testRequest) -> null)
                    .map(__ -> TestResponse.newBuilder().build());
        }

        @Override
        public Publisher<TestResponse> testResponseStream(final GrpcServiceContext ctx, final TestRequest request) {
            return never();
        }

        @Override
        public Single<TestResponse> test(final GrpcServiceContext ctx, final TestRequest request) {
            return succeeded(TestResponse.newBuilder().build());
        }
    }
}
