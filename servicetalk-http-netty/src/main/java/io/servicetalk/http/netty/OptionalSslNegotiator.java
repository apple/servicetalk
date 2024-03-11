/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.logging.api.UserDataLoggerConfig;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpServerConfig;
import io.servicetalk.tcp.netty.internal.TcpServerBinder;
import io.servicetalk.tcp.netty.internal.TcpServerChannelInitializer;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.EarlyConnectionAcceptor;
import io.servicetalk.transport.api.LateConnectionAcceptor;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.SslListenMode;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.InfluencerConnectionAcceptor;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.util.Mapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Negotiates SSL/non-SSL connections when SSL is enabled and {@link SslListenMode} is set to
 * {@link SslListenMode#SSL_OPTIONAL}.
 */
final class OptionalSslNegotiator {

    private static final Logger LOGGER = LoggerFactory.getLogger(OptionalSslNegotiator.class);

    private OptionalSslNegotiator() {
        // No instances
    }

    static Single<HttpServerContext> bind(final HttpExecutionContext executionContext,
                                          final HttpServerConfig config,
                                          final SocketAddress listenAddress,
                                          @Nullable final InfluencerConnectionAcceptor connectionAcceptor,
                                          final StreamingHttpService service,
                                          final boolean drainRequestPayloadBody,
                                          @Nullable final EarlyConnectionAcceptor earlyConnectionAcceptor,
                                          @Nullable final LateConnectionAcceptor lateConnectionAcceptor) {
        final ReadOnlyHttpServerConfig roConfig = config.asReadOnly();
        final ReadOnlyTcpServerConfig roTcpConfig = roConfig.tcpConfig();

        final BiFunction<Channel, ConnectionObserver, Single<NettyConnectionContext>> channelInit =
                (channel, connectionObserver) -> new OptionalSslChannelSingle(channel).flatMap(isTls -> {
                    if (isTls) {
                        // Important: This code needs to be kept in-sync with the similar, but not identical
                        // code inside DefaultHttpServerBuilder#doBind
                        if (roTcpConfig.isAlpnConfigured()) {
                            return DeferredServerChannelBinder.alpnInitChannel(listenAddress, channel,
                                    roConfig, executionContext,
                                    service, drainRequestPayloadBody, connectionObserver);
                        } else if (roTcpConfig.sniMapping() != null) {
                            return DeferredServerChannelBinder.sniInitChannel(listenAddress, channel,
                                    roConfig, executionContext,
                                    service, drainRequestPayloadBody, connectionObserver);
                        } else if (roConfig.isH2PriorKnowledge()) {
                            return H2ServerParentConnectionContext.initChannel(listenAddress, channel,
                                    executionContext, roConfig,
                                    new TcpServerChannelInitializer(roTcpConfig, connectionObserver,
                                            executionContext), service,
                                    drainRequestPayloadBody, connectionObserver);
                        } else {
                            return NettyHttpServer.initChannel(channel, executionContext, roConfig,
                                    new TcpServerChannelInitializer(roTcpConfig, connectionObserver,
                                            executionContext), service,
                                    drainRequestPayloadBody, connectionObserver);
                        }
                    } else {
                        // Handler negotiated a non-TLS connection, so disable the SSL config for the coming
                        // steps.
                        final ReadOnlyHttpServerConfig roConfigWithoutSsl =
                                new NonSslForcingReadOnlyHttpServerConfig(roConfig);

                        if (roConfig.h2Config() != null) {
                            return H2ServerParentConnectionContext.initChannel(listenAddress, channel,
                                    executionContext, roConfigWithoutSsl,
                                    new TcpServerChannelInitializer(roConfigWithoutSsl.tcpConfig(),
                                            connectionObserver, executionContext), service,
                                    drainRequestPayloadBody, connectionObserver);
                        } else {
                            return NettyHttpServer.initChannel(channel, executionContext, roConfigWithoutSsl,
                                    new TcpServerChannelInitializer(roConfigWithoutSsl.tcpConfig(),
                                            connectionObserver, executionContext), service,
                                    drainRequestPayloadBody, connectionObserver);
                        }
                    }
                });

        final Consumer<NettyConnectionContext> connectionConsumer = serverConnection -> {
            if (serverConnection instanceof NettyHttpServer.NettyHttpServerConnection) {
                ((NettyHttpServer.NettyHttpServerConnection) serverConnection).process(true);
            }
        };

        return TcpServerBinder.bind(listenAddress, roConfig.tcpConfig(), executionContext, connectionAcceptor,
                        channelInit, connectionConsumer, earlyConnectionAcceptor, lateConnectionAcceptor)
                .map(delegate -> {
                    LOGGER.debug("Started HTTP server with Optional TLS for address {}", delegate.listenAddress());
                    // The ServerContext returned by TcpServerBinder takes care of closing the connectionAcceptor.
                    return new NettyHttpServer.NettyHttpServerContext(delegate, service, executionContext);
                });
    }

    private static final class NonSslForcingReadOnlyHttpServerConfig implements ReadOnlyHttpServerConfig {

        private final ReadOnlyHttpServerConfig delegate;

        NonSslForcingReadOnlyHttpServerConfig(final ReadOnlyHttpServerConfig delegate) {
            this.delegate = delegate;
        }

        @Override
        public ReadOnlyTcpServerConfig tcpConfig() {
            final ReadOnlyTcpServerConfig delegateTcpConfig = delegate.tcpConfig();
            return new ReadOnlyTcpServerConfig() {
                @Nullable
                @Override
                public SslContext sslContext() {
                    return null;
                }

                @Nullable
                @Override
                public ServerSslConfig sslConfig() {
                    return null;
                }

                @Override
                public boolean isAlpnConfigured() {
                    return false;
                }

                @Nullable
                @Override
                public Mapping<String, SslContext> sniMapping() {
                    return null;
                }

                @Override
                public TransportObserver transportObserver() {
                    return delegateTcpConfig.transportObserver();
                }

                @Override
                public SslListenMode sslListenMode() {
                    return delegateTcpConfig.sslListenMode();
                }

                @Override
                public int sniMaxClientHelloLength() {
                    return delegateTcpConfig.sniMaxClientHelloLength();
                }

                @Override
                public Duration sniClientHelloTimeout() {
                    return delegateTcpConfig.sniClientHelloTimeout();
                }

                @Override
                @SuppressWarnings("rawtypes")
                public Map<ChannelOption, Object> listenOptions() {
                    return delegateTcpConfig.listenOptions();
                }

                @Override
                @SuppressWarnings("rawtypes")
                public Map<ChannelOption, Object> options() {
                    return delegateTcpConfig.options();
                }

                @Override
                public long idleTimeoutMs() {
                    return delegateTcpConfig.idleTimeoutMs();
                }

                @Override
                public FlushStrategy flushStrategy() {
                    return delegateTcpConfig.flushStrategy();
                }

                @Nullable
                @Override
                public UserDataLoggerConfig wireLoggerConfig() {
                    return delegateTcpConfig.wireLoggerConfig();
                }
            };
        }

        @Nullable
        @Override
        public H1ProtocolConfig h1Config() {
            return delegate.h1Config();
        }

        @Nullable
        @Override
        public H2ProtocolConfig h2Config() {
            return delegate.h2Config();
        }

        @Override
        public boolean allowDropTrailersReadFromTransport() {
            return delegate.allowDropTrailersReadFromTransport();
        }

        @Override
        public boolean isH2PriorKnowledge() {
            return delegate.isH2PriorKnowledge();
        }

        @Nullable
        @Override
        public HttpLifecycleObserver lifecycleObserver() {
            return delegate.lifecycleObserver();
        }
    }
}
