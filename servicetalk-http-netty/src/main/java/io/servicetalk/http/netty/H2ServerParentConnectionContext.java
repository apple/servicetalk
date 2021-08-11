/*
 * Copyright © 2019-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.netty.NettyHttpServer.NettyHttpServerConnection;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpServerConfig;
import io.servicetalk.tcp.netty.internal.TcpServerBinder;
import io.servicetalk.tcp.netty.internal.TcpServerChannelInitializer;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.MultiplexedObserver;
import io.servicetalk.transport.api.ConnectionObserver.StreamObserver;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ChannelCloseUtils;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyPipelineSslUtils;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopMultiplexedObserver;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http2.Http2SettingsFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.netty.HeaderUtils.LAST_CHUNK_PREDICATE;
import static io.servicetalk.http.netty.HttpDebugUtils.showPipeline;
import static io.servicetalk.transport.netty.internal.ChannelSet.CHANNEL_CLOSEABLE_KEY;
import static io.servicetalk.transport.netty.internal.CloseHandler.forNonPipelined;
import static java.util.Objects.requireNonNull;

final class H2ServerParentConnectionContext extends H2ParentConnectionContext implements ServerContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(H2ServerParentConnectionContext.class);
    private final SocketAddress listenAddress;
    private H2ServerParentConnectionContext(final Channel channel, final BufferAllocator allocator,
                                            final Executor executor, final FlushStrategy flushStrategy,
                                            @Nullable final Long idleTimeoutMs,
                                            final HttpExecutionStrategy executionStrategy,
                                            final SocketAddress listenAddress,
                                            final KeepAliveManager keepAliveManager) {
        super(channel, allocator, executor, flushStrategy, idleTimeoutMs, executionStrategy, keepAliveManager);
        this.listenAddress = requireNonNull(listenAddress);
    }

    @Override
    public SocketAddress listenAddress() {
        return listenAddress;
    }

    static Single<ServerContext> bind(final HttpExecutionContext executionContext,
                                      final ReadOnlyHttpServerConfig config,
                                      final SocketAddress listenAddress,
                                      @Nullable final ConnectionAcceptor connectionAcceptor,
                                      final StreamingHttpService service,
                                      final boolean drainRequestPayloadBody) {
        if (config.h2Config() == null) {
            return failed(newH2ConfigException());
        }
        final ReadOnlyTcpServerConfig tcpServerConfig = config.tcpConfig();
        // Auto read is required for h2
        return TcpServerBinder.bind(listenAddress, tcpServerConfig, true, executionContext, connectionAcceptor,
                (channel, connectionObserver) -> initChannel(listenAddress, channel, executionContext, config,
                        new TcpServerChannelInitializer(tcpServerConfig, connectionObserver), service,
                        drainRequestPayloadBody, connectionObserver),
                serverConnection -> { /* nothing to do as h2 uses auto read on the parent channel */ })
                .map(delegate -> {
                    LOGGER.debug("Started HTTP/2 server with prior-knowledge for address {}", delegate.listenAddress());
                    // The ServerContext returned by TcpServerBinder takes care of closing the connectionAcceptor.
                    return new NettyHttpServer.NettyHttpServerContext(delegate, service);
                });
    }

    private static Throwable newH2ConfigException() {
        return new IllegalStateException(
                "HTTP/2 channel initialization failure due to missing HTTP/2 configuration");
    }

    static Single<H2ServerParentConnectionContext> initChannel(final SocketAddress listenAddress,
                final Channel channel, final HttpExecutionContext httpExecutionContext,
                final ReadOnlyHttpServerConfig config, final ChannelInitializer initializer,
                final StreamingHttpService service, final boolean drainRequestPayloadBody,
                final ConnectionObserver observer) {
        final H2ProtocolConfig h2ServerConfig = config.h2Config();
        if (h2ServerConfig == null) {
            return failed(newH2ConfigException());
        }
        return showPipeline(new SubscribableSingle<H2ServerParentConnectionContext>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super H2ServerParentConnectionContext> subscriber) {
                final DefaultH2ServerParentConnection parentChannelInitializer;
                final DelayedCancellable delayedCancellable;
                final ChannelPipeline pipeline;
                try {
                    delayedCancellable = new DelayedCancellable();
                    KeepAliveManager keepAliveManager = new KeepAliveManager(channel, h2ServerConfig.keepAlivePolicy());
                    final FlushStrategy parentFlushStrategy = config.tcpConfig().flushStrategy();
                    final BufferAllocator allocator = httpExecutionContext.bufferAllocator();
                    final Executor executor = httpExecutionContext.executor();
                    final HttpExecutionStrategy executionStrategy = httpExecutionContext.executionStrategy();
                    H2ServerParentConnectionContext connection = new H2ServerParentConnectionContext(channel,
                            allocator, executor, parentFlushStrategy, config.tcpConfig().idleTimeoutMs(),
                            executionStrategy, listenAddress, keepAliveManager);
                    channel.attr(CHANNEL_CLOSEABLE_KEY).set(connection);
                    // We need the NettyToStChannelInboundHandler to be last in the pipeline. We accomplish that by
                    // calling the ChannelInitializer before we do addLast for the NettyToStChannelInboundHandler.
                    // This could mean if there are any synchronous events generated via ChannelInitializer handlers
                    // that NettyToStChannelInboundHandler will not see them. This is currently not an issue and would
                    // require some pipeline modifications if we wanted to insert NettyToStChannelInboundHandler first,
                    // but not allow any other handlers to be after it.
                    initializer.init(channel);
                    pipeline = channel.pipeline();

                    parentChannelInitializer = new DefaultH2ServerParentConnection(connection, subscriber,
                            delayedCancellable, NettyPipelineSslUtils.isSslEnabled(pipeline), observer);

                    new H2ServerParentChannelInitializer(h2ServerConfig,
                        new io.netty.channel.ChannelInitializer<Http2StreamChannel>() {
                            @Override
                            protected void initChannel(final Http2StreamChannel streamChannel) {
                                connection.trackActiveStream(streamChannel);
                                StreamObserver streamObserver =
                                        parentChannelInitializer.multiplexedObserver.onNewStream();

                                // Netty To ServiceTalk type conversion
                                final CloseHandler closeHandler = forNonPipelined(false, streamChannel.config());
                                streamChannel.pipeline().addLast(new H2ToStH1ServerDuplexHandler(
                                        connection.executionContext().bufferAllocator(),
                                        h2ServerConfig.headersFactory(), closeHandler, streamObserver));

                                // ServiceTalk <-> Netty netty utilities
                                DefaultNettyConnection<Object, Object> streamConnection =
                                        DefaultNettyConnection.initChildChannel(streamChannel,
                                                connection.executionContext().bufferAllocator(),
                                                connection.executionContext().executor(), LAST_CHUNK_PREDICATE,
                                                closeHandler,
                                                // TODO(scott): after flushStrategy is no longer on the connection
                                                // level we can use DefaultNettyConnection.initChannel instead of this
                                                // custom method.
                                                connection.flushStrategyHolder.currentStrategy(),
                                                connection.idleTimeoutMs,
                                                connection.executionContext().executionStrategy(),
                                                HTTP_2_0,
                                                connection.sslSession(),
                                                channel.config(),
                                                streamObserver,
                                                false,
                                                Http2Exception::wrapIfNecessary);

                                // ServiceTalk HTTP service handler
                                new NettyHttpServerConnection(streamConnection, service, HTTP_2_0,
                                        h2ServerConfig.headersFactory(), drainRequestPayloadBody,
                                        config.allowDropTrailersReadFromTransport()).process(false);
                            }
                    }).init(channel);
                } catch (Throwable cause) {
                    ChannelCloseUtils.close(channel, cause);
                    deliverErrorFromSource(subscriber, cause);
                    return;
                }
                subscriber.onSubscribe(delayedCancellable);
                // We have to add to the pipeline AFTER we call onSubscribe, because adding to the pipeline may invoke
                // callbacks that interact with the subscriber.
                pipeline.addLast(parentChannelInitializer);
            }
        }, HTTP_2_0, channel);
    }

    private static final class DefaultH2ServerParentConnection extends AbstractH2ParentConnection {
        @Nullable
        private Subscriber<? super H2ServerParentConnectionContext> subscriber;
        private MultiplexedObserver multiplexedObserver = NoopMultiplexedObserver.INSTANCE;

        DefaultH2ServerParentConnection(final H2ServerParentConnectionContext parentContext,
                                        final Subscriber<? super H2ServerParentConnectionContext> subscriber,
                                        final DelayedCancellable delayedCancellable,
                                        final boolean waitForSslHandshake,
                                        final ConnectionObserver observer) {
            super(parentContext, delayedCancellable, waitForSslHandshake, observer);
            this.subscriber = requireNonNull(subscriber);
        }

        @Override
        boolean hasSubscriber() {
            return subscriber != null;
        }

        @Override
        void tryCompleteSubscriber() {
            if (subscriber != null) {
                Subscriber<? super H2ServerParentConnectionContext> subscriberCopy = subscriber;
                subscriber = null;
                multiplexedObserver = observer.multiplexedConnectionEstablished(parentContext);
                subscriberCopy.onSuccess((H2ServerParentConnectionContext) parentContext);
            }
        }

        @Override
        void tryFailSubscriber(Throwable cause) {
            if (subscriber != null) {
                ChannelCloseUtils.close(parentContext.nettyChannel(), cause);
                Subscriber<? super H2ServerParentConnectionContext> subscriberCopy = subscriber;
                subscriber = null;
                subscriberCopy.onError(cause);
            }
        }

        @Override
        boolean ackSettings(final ChannelHandlerContext ctx, final Http2SettingsFrame settingsFrame) {
            // Server side doesn't asynchronously need to ACK the settings because there is no need to coordinate
            // the maximum concurrent streams value with the application.
            // All SETTINGS frames are automatically ack'ed by netty, see
            // Http2FrameCodecBuilder#autoAckSettingsFrame(boolean) in H2ServerParentChannelInitializer.
            return false;
        }
    }
}
