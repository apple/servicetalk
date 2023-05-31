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
import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.api.GrpcServerBuilder;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.ServiceFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import io.servicetalk.http.netty.H2ProtocolConfig;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.CI;
import static io.servicetalk.grpc.api.GrpcExecutionStrategies.defaultStrategy;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNIMPLEMENTED;
import static io.servicetalk.grpc.netty.GrpcServers.forAddress;
import static io.servicetalk.grpc.netty.TesterProto.TestRequest.newBuilder;
import static io.servicetalk.grpc.netty.TesterProto.Tester.ClientFactory;
import static io.servicetalk.http.netty.H2KeepAlivePolicies.disabled;
import static io.servicetalk.http.netty.H2KeepAlivePolicies.whenIdleFor;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static io.servicetalk.transport.api.ServiceTalkSocketOptions.IDLE_TIMEOUT;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KeepAliveTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
        ExecutionContextExtension.cached("server-io", "server-executor")
                .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    @Nullable
    private TesterClient client;
    @Nullable
    private ServerContext ctx;
    private long idleTimeoutMillis;

    private void setUp(final boolean keepAlivesFromClient,
                       final Duration keepAliveIdleFor,
                       final Duration idleTimeout) throws Exception {
        this.idleTimeoutMillis = idleTimeout.toMillis();
        GrpcServerBuilder serverBuilder = forAddress(localAddress(0))
                .initializeHttp(builder -> {
                    builder.executor(SERVER_CTX.executor())
                            .ioExecutor(SERVER_CTX.ioExecutor())
                            .executionStrategy(defaultStrategy());
                    if (!keepAlivesFromClient) {
                        builder.protocols(h2Config(keepAliveIdleFor));
                    } else {
                        builder.socketOption(IDLE_TIMEOUT, idleTimeoutMillis)
                            .protocols(h2Config(null));
                    }
                });
        ctx = serverBuilder.listenAndAwait(new ServiceFactory(new InfiniteStreamsService()));
        GrpcClientBuilder<HostAndPort, InetSocketAddress> clientBuilder = GrpcClients.forAddress(serverHostAndPort(ctx))
                .initializeHttp(builder -> {
                    builder.executor(CLIENT_CTX.executor())
                            .ioExecutor(CLIENT_CTX.ioExecutor())
                            .executionStrategy(defaultStrategy());
                    if (keepAlivesFromClient) {
                        builder.protocols(h2Config(keepAliveIdleFor));
                    } else {
                        builder.socketOption(IDLE_TIMEOUT, idleTimeoutMillis)
                                .protocols(h2Config(null));
                    }
                });
        client = clientBuilder.build(new ClientFactory());
    }

    static Stream<Arguments> data() {
        return Stream.of(
                Arguments.of(true, ofMillis(CI ? 1000 : 200), ofMillis(CI ? 2000 : 400)),
                Arguments.of(false, ofMillis(CI ? 1000 : 200), ofMillis(CI ? 2000 : 400))
        );
    }

    private static H2ProtocolConfig h2Config(@Nullable final Duration keepAliveIdleFor) {
        return h2()
            .enableFrameLogging("servicetalk-tests-h2-frame-logger", TRACE, () -> true)
            .keepAlivePolicy(keepAliveIdleFor == null ? disabled() : whenIdleFor(keepAliveIdleFor))
            .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
        if (ctx != null) {
            ctx.close();
        }
    }

    @ParameterizedTest(name = "keepAlivesFromClient? {0}, keepAliveIdleFor: {1}, idleTimeout: {2}")
    @MethodSource("data")
    void bidiStream(final boolean keepAlivesFromClient,
                    final Duration keepAliveIdleFor,
                    final Duration idleTimeout) throws Exception {
        setUp(keepAlivesFromClient, keepAliveIdleFor, idleTimeout);
        assertThrows(TimeoutException.class, () -> client.testBiDiStream(never())
                .toFuture().get(idleTimeoutMillis + 100, MILLISECONDS));
    }

    @ParameterizedTest(name = "keepAlivesFromClient? {0}, keepAliveIdleFor: {1}, idleTimeout: {2}")
    @MethodSource("data")
    void requestStream(final boolean keepAlivesFromClient,
                       final Duration keepAliveIdleFor,
                       final Duration idleTimeout) throws Exception {
        setUp(keepAlivesFromClient, keepAliveIdleFor, idleTimeout);
        assertThrows(TimeoutException.class, () -> client.testRequestStream(never())
                .toFuture().get(idleTimeoutMillis + 100, MILLISECONDS));
    }

    @ParameterizedTest(name = "keepAlivesFromClient? {0}, keepAliveIdleFor: {1}, idleTimeout: {2}")
    @MethodSource("data")
    void responseStream(final boolean keepAlivesFromClient,
                        final Duration keepAliveIdleFor,
                        final Duration idleTimeout) throws Exception {
        setUp(keepAlivesFromClient, keepAliveIdleFor, idleTimeout);
        assertThrows(TimeoutException.class, () -> client.testResponseStream(newBuilder().build())
                .toFuture().get(idleTimeoutMillis + 100, MILLISECONDS));
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
            return request.ignoreElements().toSingle().map(__ -> TestResponse.newBuilder().build());
        }

        @Override
        public Publisher<TestResponse> testResponseStream(final GrpcServiceContext ctx, final TestRequest request) {
            return never();
        }

        @Override
        public Single<TestResponse> test(final GrpcServiceContext ctx, final TestRequest request) {
            return failed(new GrpcStatusException(UNIMPLEMENTED.status()));
        }
    }
}
