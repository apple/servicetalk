/*
 * Copyright © 2019-2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.netty.NettyHttpServer.NettyHttpServerConnection;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpServerConfig;
import io.servicetalk.tcp.netty.internal.TcpServerBinder;
import io.servicetalk.tcp.netty.internal.TcpServerChannelInitializer;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.MultiplexedObserver;
import io.servicetalk.transport.api.ConnectionObserver.StreamObserver;
import io.servicetalk.transport.api.EarlyConnectionAcceptor;
import io.servicetalk.transport.api.LateConnectionAcceptor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.ChannelCloseUtils;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.InfluencerConnectionAcceptor;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopMultiplexedObserver;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http2.Http2SettingsFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.netty.HttpDebugUtils.showPipeline;
import static io.servicetalk.transport.netty.internal.ChannelSet.CHANNEL_CLOSEABLE_KEY;
import static io.servicetalk.transport.netty.internal.CloseHandler.forNonPipelined;
import static io.servicetalk.transport.netty.internal.NettyPipelineSslUtils.extractSslSession;
import static java.util.Objects.requireNonNull;

final class H2ServerParentConnectionContext extends H2ParentConnectionContext implements ServerContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(H2ServerParentConnectionContext.class);
    private final SocketAddress listenAddress;
    @Nullable
    private ConnectionObserver observer;
    @Nullable
    private DefaultH2ServerParentConnection parentConnectionHandler;
    private H2ServerParentConnectionContext(final Channel channel, final HttpExecutionContext executionContext,
                                            final FlushStrategy flushStrategy,
                                            final long idleTimeoutMs,
                                            @Nullable final SslConfig sslConfig,
                                            @Nullable final SSLSession sslSession,
                                            final SocketAddress listenAddress,
                                            final KeepAliveManager keepAliveManager) {
        super(channel, executionContext, flushStrategy, idleTimeoutMs, sslConfig, sslSession, keepAliveManager);
        this.listenAddress = requireNonNull(listenAddress);
    }

    @Override
    public void acceptConnections(final boolean accept) {
        channel().parent().config().setAutoRead(accept);
    }

    @Override
    public SocketAddress listenAddress() {
        return listenAddress;
    }

    /**
     * Notifies the observer that the multiplexed connection has been established and enables auto-read
     * so that HTTP/2 frames begin flowing through the pipeline.
     * <p>
     * These two actions are combined into a single method because they <b>must</b> happen in this exact order:
     * the {@code multiplexedObserver} must be set before any frames arrive, and enabling auto-read is what
     * triggers frame delivery. Separating them would risk a future caller enabling auto-read without first
     * setting the observer, causing frames to be processed with a {@code NoopMultiplexedObserver}.
     * <p>
     * Thread-safety invariant — this method may run on an offload executor thread (e.g., when a
     * {@link LateConnectionAcceptor} requires offloading). The {@code observer}
     * and {@code parentConnectionHandler} fields were written during {@link #initChannel} (on the event loop)
     * and are safely published here via the reactive chain's {@code onSuccess} delivery, which provides a
     * happens-before edge.
     * <p>
     * The actual work is always dispatched to the event loop to ensure that the {@code multiplexedObserver}
     * write and the subsequent {@code setAutoRead(true)} (which triggers {@code channel.read()} inline)
     * both execute on the same thread that will read the observer when frames arrive. This makes the
     * visibility guarantee trivial — single-threaded access, no memory ordering concerns.
     */
    void notifyConnectionEstablishedAndEnableAutoRead() {
        final EventLoop eventLoop = nettyChannel().eventLoop();
        if (eventLoop.inEventLoop()) {
            doNotifyAndEnableAutoRead();
        } else {
            eventLoop.execute(this::doNotifyAndEnableAutoRead);
        }
    }

    private void doNotifyAndEnableAutoRead() {
        if (observer != null && parentConnectionHandler != null) {
            parentConnectionHandler.multiplexedObserver = observer.multiplexedConnectionEstablished(this);
            observer = null;
            parentConnectionHandler = null;
        }
        nettyChannel().config().setAutoRead(true);
    }

    static Single<HttpServerContext> bind(final HttpExecutionContext executionContext,
                                          final ReadOnlyHttpServerConfig config,
                                          final SocketAddress listenAddress,
                                          @Nullable final InfluencerConnectionAcceptor connectionAcceptor,
                                          final StreamingHttpService service,
                                          @Nullable final EarlyConnectionAcceptor earlyConnectionAcceptor,
                                          @Nullable final LateConnectionAcceptor lateConnectionAcceptor) {
        if (config.h2Config() == null) {
            return failed(newH2ConfigException());
        }
        final ReadOnlyTcpServerConfig tcpServerConfig = config.tcpConfig();
        // Called AFTER all connection acceptors have completed.
        return TcpServerBinder.bind(listenAddress, tcpServerConfig, executionContext, connectionAcceptor,
                (channel, connectionObserver) -> initChannel(listenAddress, channel, executionContext, config,
                        new TcpServerChannelInitializer(tcpServerConfig, connectionObserver, executionContext), service,
                        connectionObserver),
                        H2ServerParentConnectionContext::notifyConnectionEstablishedAndEnableAutoRead,
                        earlyConnectionAcceptor, lateConnectionAcceptor)
                .map(delegate -> {
                    LOGGER.debug("Started HTTP/2 server with prior-knowledge for address {}", delegate.listenAddress());
                    // The ServerContext returned by TcpServerBinder takes care of closing the connectionAcceptor.
                    return new NettyHttpServer.NettyHttpServerContext(delegate, service, executionContext);
                });
    }

    private static Throwable newH2ConfigException() {
        return new IllegalStateException(
                "HTTP/2 channel initialization failure due to missing HTTP/2 configuration");
    }

    static Single<H2ServerParentConnectionContext> initChannel(final SocketAddress listenAddress,
                final Channel channel, final HttpExecutionContext httpExecutionContext,
                final ReadOnlyHttpServerConfig config, final ChannelInitializer initializer,
                final StreamingHttpService service,
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
                    // We need the NettyToStChannelInboundHandler to be last in the pipeline. We accomplish that by
                    // calling the ChannelInitializer before we do addLast for the NettyToStChannelInboundHandler.
                    // This could mean if there are any synchronous events generated via ChannelInitializer handlers
                    // that NettyToStChannelInboundHandler will not see them. This is currently not an issue and would
                    // require some pipeline modifications if we wanted to insert NettyToStChannelInboundHandler first,
                    // but not allow any other handlers to be after it.
                    initializer.init(channel);

                    pipeline = channel.pipeline();
                    @Nullable
                    final SslConfig sslConfig = config.tcpConfig().sslConfig();
                    @Nullable
                    final SSLSession sslSession = extractSslSession(sslConfig, pipeline);
                    H2ServerParentConnectionContext connection = new H2ServerParentConnectionContext(channel,
                            httpExecutionContext, config.tcpConfig().flushStrategy(),
                            config.tcpConfig().idleTimeoutMs(), sslConfig, sslSession, listenAddress,
                            new KeepAliveManager(channel, h2ServerConfig.keepAlivePolicy()));
                    channel.attr(CHANNEL_CLOSEABLE_KEY).set(connection);
                    delayedCancellable = new DelayedCancellable();
                    parentChannelInitializer = new DefaultH2ServerParentConnection(connection, subscriber,
                            delayedCancellable, shouldWaitForSslHandshake(sslSession, sslConfig), observer,
                            true /* deferAutoRead */);
                    connection.observer = observer;
                    connection.parentConnectionHandler = parentChannelInitializer;

                    new H2ServerParentChannelInitializer(h2ServerConfig,
                        new io.netty.channel.ChannelInitializer<Http2StreamChannel>() {
                            @Override
                            protected void initChannel(final Http2StreamChannel streamChannel) {
                                connection.trackActiveStream(streamChannel);
                                StreamObserver streamObserver =
                                        parentChannelInitializer.multiplexedObserver.onNewStream();
                                final int streamId = streamChannel.stream().id();
                                assert streamId > 0;
                                streamObserver.streamIdAssigned(streamId);

                                // Netty To ServiceTalk type conversion
                                final CloseHandler closeHandler = forNonPipelined(false, streamChannel.config());
                                streamChannel.pipeline().addLast(new H2ToStH1ServerDuplexHandler(
                                        connection.executionContext().bufferAllocator(),
                                        h2ServerConfig.headersFactory(), closeHandler, streamObserver));

                                // ServiceTalk <-> Netty netty utilities
                                DefaultNettyConnection<Object, Object> streamConnection =
                                        DefaultNettyConnection.initChildChannel(streamChannel,
                                                connection,
                                                closeHandler,
                                                // TODO(scott): after flushStrategy is no longer on the connection
                                                // level we can use DefaultNettyConnection.initChannel instead of this
                                                // custom method.
                                                connection.defaultFlushStrategy(),
                                                connection.idleTimeoutMs,
                                                HTTP_2_0,
                                                connection.nettyChannel().config(),
                                                streamObserver,
                                                false, __ -> false,
                                                NettyHttp2ExceptionUtils::wrapIfNecessary);

                                // ServiceTalk HTTP service handler
                                new NettyHttpServerConnection(streamConnection, service, HTTP_2_0,
                                        h2ServerConfig.headersFactory(),
                                        config.allowDropTrailersReadFromTransport()).process(false);
                            }
                    }).init(channel);
                } catch (Throwable cause) {
                    ChannelCloseUtils.close(channel, cause);
                    deliverErrorFromSource(subscriber, cause);
                    return;
                }
                try {
                    subscriber.onSubscribe(delayedCancellable);
                } catch (Throwable cause) {
                    ChannelCloseUtils.close(channel, cause);
                    handleExceptionFromOnSubscribe(subscriber, cause);
                    return;
                }
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
                                        final ConnectionObserver observer,
                                        final boolean deferAutoRead) {
            super(parentContext, delayedCancellable, waitForSslHandshake, observer, deferAutoRead);
            this.subscriber = requireNonNull(subscriber);
        }

        @Override
        void tryCompleteSubscriber() {
            if (subscriber != null) {
                Subscriber<? super H2ServerParentConnectionContext> subscriberCopy = subscriber;
                subscriber = null;
                // multiplexedObserver will be set via notifyConnectionEstablishedAndEnableAutoRead() in
                // connectionConsumer,
                // after all connection acceptors pass. Safe default: NoopMultiplexedObserver.INSTANCE
                subscriberCopy.onSuccess((H2ServerParentConnectionContext) parentContext);
            }
        }

        @Override
        boolean tryFailSubscriber(Throwable cause) {
            if (subscriber != null) {
                ChannelCloseUtils.close(parentContext.nettyChannel(), cause);
                Subscriber<? super H2ServerParentConnectionContext> subscriberCopy = subscriber;
                subscriber = null;
                subscriberCopy.onError(cause);
                return true;
            } else {
                return false;
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
