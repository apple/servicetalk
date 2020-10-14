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
import io.servicetalk.http.netty.H2KeepAlivePolicies;
import io.servicetalk.http.netty.H2ProtocolConfig;
import io.servicetalk.http.netty.HttpProtocolConfigs;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;
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
import java.util.Collection;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.grpc.api.GrpcExecutionStrategies.defaultStrategy;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.cached;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;
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
                         final Function<String, H2ProtocolConfig> protocolConfigSupplier,
                         final long idleTimeoutMillis) throws Exception {
        this.idleTimeoutMillis = idleTimeoutMillis;
        GrpcServerBuilder serverBuilder = GrpcServers.forAddress(localAddress(0))
                .ioExecutor(SERVER_CTX.ioExecutor())
                .executionStrategy(defaultStrategy(SERVER_CTX.executor()));
        if (!keepAlivesFromClient) {
            serverBuilder.protocols(protocolConfigSupplier.apply("servicetalk-tests-wire-logger"));
        } else {
            serverBuilder.socketOption(ServiceTalkSocketOptions.IDLE_TIMEOUT, idleTimeoutMillis)
                    .protocols(HttpProtocolConfigs.h2()
                            .enableFrameLogging("servicetalk-tests-h2-frame-logger").build());
        }
        ctx = serverBuilder.listenAndAwait(new ServiceFactory(new InfiniteStreamsService()));
        GrpcClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                GrpcClients.forAddress(serverHostAndPort(ctx))
                        .ioExecutor(CLIENT_CTX.ioExecutor())
                        .executionStrategy(defaultStrategy(CLIENT_CTX.executor()));
        if (keepAlivesFromClient) {
            clientBuilder.protocols(protocolConfigSupplier.apply("servicetalk-tests-wire-logger"));
        } else {
            clientBuilder.socketOption(ServiceTalkSocketOptions.IDLE_TIMEOUT, idleTimeoutMillis)
                    .protocols(HttpProtocolConfigs.h2()
                            .enableFrameLogging("servicetalk-tests-h2-frame-logger").build());
        }
        client = clientBuilder.build(new TesterProto.Tester.ClientFactory());
    }

    @Parameterized.Parameters(name = "keepAlivesFromClient? {0}, idleTimeout: {2}")
    public static Collection<Object[]> data() {
        return asList(newParam(true, ofSeconds(1), ofSeconds(2)),
                newParam(false, ofSeconds(1), ofSeconds(2)));
    }

    private static Object[] newParam(final boolean keepAlivesFromClient, final Duration keepAliveIdleDuration,
                                     final Duration idleTimeoutDuration) {
        return new Object[] {keepAlivesFromClient,
                (Function<String, H2ProtocolConfig>) frameLogger ->
                        HttpProtocolConfigs.h2()
                                .keepAlivePolicy(H2KeepAlivePolicies.whenIdleFor(keepAliveIdleDuration))
                                .enableFrameLogging(frameLogger).build(),
                idleTimeoutDuration.toMillis()};
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
