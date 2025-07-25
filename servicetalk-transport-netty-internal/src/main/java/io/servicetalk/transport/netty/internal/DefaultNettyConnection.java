/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.concurrent.api.internal.SubscribableCompletable;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.DataObserver;
import io.servicetalk.transport.api.ConnectionObserver.ReadObserver;
import io.servicetalk.transport.api.ConnectionObserver.StreamObserver;
import io.servicetalk.transport.api.ConnectionObserver.WriteObserver;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.RetryableException;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.CloseHandler.AbortWritesEvent;
import io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent;
import io.servicetalk.transport.netty.internal.CloseHandler.CloseEventObservedException;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopConnectionObserver;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopDataObserver;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopReadObserver;
import io.servicetalk.transport.netty.internal.WriteStreamSubscriber.AbortedFirstWriteException;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.ChannelOutputShutdownEvent;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.SslCloseCompletionEvent;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.transport.netty.internal.ChannelCloseUtils.close;
import static io.servicetalk.transport.netty.internal.ChannelSet.CHANNEL_CLOSEABLE_KEY;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static io.servicetalk.transport.netty.internal.Flush.composeFlushes;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.fromNettyEventLoop;
import static io.servicetalk.transport.netty.internal.NettyPipelineSslUtils.extractSslSession;
import static io.servicetalk.transport.netty.internal.SocketOptionUtils.getOption;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;
import static java.util.function.UnaryOperator.identity;

/**
 * Implementation of {@link NettyConnection} backed by a netty {@link Channel}.
 *
 * @param <Read> Type of objects read from this connection.
 * @param <Write> Type of objects written to this connection.
 */
public final class DefaultNettyConnection<Read, Write> extends NettyChannelListenableAsyncCloseable
        implements NettyConnection<Read, Write>, ChannelOutboundListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultNettyConnection.class);

    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<DefaultNettyConnection, ChannelOutboundListener>
            writableListenerUpdater = newUpdater(DefaultNettyConnection.class, ChannelOutboundListener.class,
                                                 "channelOutboundListener");

    @Nullable
    private final ConnectionContext parent;
    private final CloseHandler closeHandler;
    private final NettyChannelPublisher<Read> nettyChannelPublisher;
    private final Publisher<Read> readPublisher;
    private final ExecutionContext<?> executionContext;
    private final SingleSource.Processor<Throwable, Throwable> transportError = newSingleProcessor();
    private final FlushStrategyHolder flushStrategyHolder;
    private final long idleTimeoutMs;
    private final Protocol protocol;
    @Nullable
    private final SslConfig sslConfig;
    private volatile ChannelOutboundListener channelOutboundListener = NoopChannelOutboundListener.INSTANCE;
    /**
     * Potentially contains more information when a protocol or channel level close event was observed.
     * <p>
     * Always accessed from the event loop, doesn't require synchronization.
     */
    @Nullable
    private volatile CloseEvent closeReason;
    /**
     * This doesn't need to be volatile because this object is only accessed in the following scenarios:
     * <ul>
     * <li>it is set on the EventLoop</li>
     * <li>it maybe read on the same EventLoop thread</li>
     * <li>it may be read in a {@link SingleSource.Subscriber} and we rely upon the {@link Single} visibility
     * constraints</li>
     * </ul>
     */
    @Nullable
    private SSLSession sslSession;
    @Nullable
    private final ChannelConfig parentChannelConfig;
    private volatile DataObserver dataObserver;
    private final boolean isClient;
    private final Predicate<Object> shouldWait;
    private final UnaryOperator<Throwable> enrichProtocolError;
    private final TerminalSignalConsumer cleanupStateConsumer = new TerminalSignalConsumer() {
        @Override
        public void onComplete() {
            cleanupOnWriteTerminated();
        }

        @Override
        public void onError(final Throwable throwable) {
            cleanupOnWriteTerminated();
        }

        @Override
        public void cancel() {
            // If close events happen, we still need to process them, however we should dereference the current
            // WriteStreamSubscriber and allow another write to be processed.
            channelOutboundListener = DefaultNettyConnection.this;
        }

        private void cleanupOnWriteTerminated() {
            channelOutboundListener = NoopChannelOutboundListener.INSTANCE;
        }
    };

    private DefaultNettyConnection(
            Channel channel, @Nullable ConnectionContext parent, ExecutionContext<?> executionContext,
            CloseHandler closeHandler, FlushStrategy flushStrategy,
            long idleTimeoutMs, Protocol protocol,
            @Nullable SslConfig sslConfig, @Nullable SSLSession sslSession,
            @Nullable ChannelConfig parentChannelConfig, DataObserver dataObserver, boolean isClient,
            Predicate<Object> shouldWait, UnaryOperator<Throwable> enrichProtocolError) {
        super(channel,
                executionContext.executionStrategy().isCloseOffloaded() ? executionContext.executor() : immediate());
        nettyChannelPublisher = new NettyChannelPublisher<>(channel, closeHandler);
        this.parent = parent;
        this.readPublisher = registerReadObserver(nettyChannelPublisher.onErrorMap(this::enrichError));
        this.executionContext = executionContext;
        this.closeHandler = requireNonNull(closeHandler);
        this.flushStrategyHolder = new FlushStrategyHolder(flushStrategy);
        this.idleTimeoutMs = idleTimeoutMs;
        if (closeHandler != UNSUPPORTED_PROTOCOL_CLOSE_HANDLER) {
            closeHandler.registerEventHandler(channel, evt -> {
                assert channel.eventLoop().inEventLoop();
                if (closeReason == null) {
                    closeReason = evt;
                    notifyOnClosing();
                    transportError.onSuccess(evt.wrapError(null, channel));
                    LOGGER.debug("{} Emitted CloseEvent: {}", channel, evt);
                }
            });
        }
        this.sslConfig = sslConfig;
        this.sslSession = sslSession;
        this.parentChannelConfig = parentChannelConfig;
        this.protocol = requireNonNull(protocol);
        this.dataObserver = dataObserver;
        this.isClient = isClient;
        this.shouldWait = requireNonNull(shouldWait);
        this.enrichProtocolError = requireNonNull(enrichProtocolError);
    }

    /**
     * Given a {@link Channel} this will initialize the {@link ChannelPipeline} just to create a
     * {@link DefaultNettyConnection}. It is assumed this is a child channel and all TLS handshaking is completed.
     * @param channel A newly created {@link Channel}.
     * @param executionContext Used to derive the {@link #executionContext()}.
     * @param closeHandler Manages the half closure of the {@link DefaultNettyConnection}.
     * @param flushStrategy Manages flushing of data for the {@link DefaultNettyConnection}.
     * @param idleTimeoutMs Value for {@link ServiceTalkSocketOptions#IDLE_TIMEOUT IDLE_TIMEOUT} socket option.
     * @param protocol {@link Protocol} for the returned {@link DefaultNettyConnection}.
     * @param sslSession Provides access to the {@link SSLSession} associated with this connection.
     * @param parentChannelConfig {@link ChannelConfig} of the parent {@link Channel} to query {@link SocketOption}s.
     * @param streamObserver {@link StreamObserver} to report internal events.
     * @param isClient tells if this {@link Channel} is for the client.
     * @param enrichProtocolError enriches protocol-specific {@link Throwable}s.
     * @param <Read> Type of objects read from the {@link NettyConnection}.
     * @param <Write> Type of objects written to the {@link NettyConnection}.
     * @return A {@link Single} that completes with a {@link DefaultNettyConnection} after the channel is activated and
     * ready to use.
     * @deprecated Use {@code #initChildChannel(Channel, ConnectionContext, CloseHandler, FlushStrategy, long, Protocol,
     * ChannelConfig, StreamObserver, boolean, Predicate, UnaryOperator)}.
     */
    @Deprecated // FIXME: 0.43 - remove deprecated method
    public static <Read, Write> DefaultNettyConnection<Read, Write> initChildChannel(
            Channel channel, ExecutionContext<?> executionContext,
            CloseHandler closeHandler, FlushStrategy flushStrategy,
            Long idleTimeoutMs, Protocol protocol, @Nullable SSLSession sslSession,
            @Nullable ChannelConfig parentChannelConfig, StreamObserver streamObserver, boolean isClient,
            UnaryOperator<Throwable> enrichProtocolError) {
        return initChildChannel(channel, executionContext, closeHandler, flushStrategy, idleTimeoutMs, protocol,
                sslSession, parentChannelConfig, streamObserver, isClient, __ -> false, enrichProtocolError);
    }

    /**
     * Given a {@link Channel} this will initialize the {@link ChannelPipeline} just to create a
     * {@link DefaultNettyConnection}. It is assumed this is a child channel and all TLS handshaking is completed.
     * @param channel A newly created {@link Channel}.
     * @param executionContext Used to derive the {@link #executionContext()}.
     * @param closeHandler Manages the half closure of the {@link DefaultNettyConnection}.
     * @param flushStrategy Manages flushing of data for the {@link DefaultNettyConnection}.
     * @param idleTimeoutMs Value for {@link ServiceTalkSocketOptions#IDLE_TIMEOUT IDLE_TIMEOUT} socket option.
     * @param protocol {@link Protocol} for the returned {@link DefaultNettyConnection}.
     * @param sslSession Provides access to the {@link SSLSession} associated with this connection.
     * @param parentChannelConfig {@link ChannelConfig} of the parent {@link Channel} to query {@link SocketOption}s.
     * @param streamObserver {@link StreamObserver} to report internal events.
     * @param isClient tells if this {@link Channel} is for the client.
     * @param shouldWait predicate that tells when request payload body should wait for continuation signal.
     * @param enrichProtocolError enriches protocol-specific {@link Throwable}s.
     * @param <Read> Type of objects read from the {@link NettyConnection}.
     * @param <Write> Type of objects written to the {@link NettyConnection}.
     * @return A {@link Single} that completes with a {@link DefaultNettyConnection} after the channel is activated and
     * ready to use.
     * @deprecated Use {@code #initChildChannel(Channel, ConnectionContext, CloseHandler, FlushStrategy, long, Protocol,
     * ChannelConfig, StreamObserver, boolean, Predicate, UnaryOperator)}.
     */
    @Deprecated // FIXME: 0.43 - remove deprecated method
    public static <Read, Write> DefaultNettyConnection<Read, Write> initChildChannel(
            Channel channel, ExecutionContext<?> executionContext,
            CloseHandler closeHandler, FlushStrategy flushStrategy,
            Long idleTimeoutMs, Protocol protocol, @Nullable SSLSession sslSession,
            @Nullable ChannelConfig parentChannelConfig, StreamObserver streamObserver, boolean isClient,
            Predicate<Object> shouldWait, UnaryOperator<Throwable> enrichProtocolError) {
        return initChildChannel(channel, executionContext, closeHandler, flushStrategy, idleTimeoutMs, protocol, null,
                sslSession, parentChannelConfig, streamObserver, isClient, shouldWait, enrichProtocolError);
    }

    /**
     * Given a {@link Channel} this will initialize the {@link ChannelPipeline} just to create a
     * {@link DefaultNettyConnection}. It is assumed this is a child channel and all TLS handshaking is completed.
     * @param channel A newly created {@link Channel}.
     * @param executionContext Used to derive the {@link #executionContext()}.
     * @param closeHandler Manages the half closure of the {@link DefaultNettyConnection}.
     * @param flushStrategy Manages flushing of data for the {@link DefaultNettyConnection}.
     * @param idleTimeoutMs Value for {@link ServiceTalkSocketOptions#IDLE_TIMEOUT IDLE_TIMEOUT} socket option.
     * @param protocol {@link Protocol} for the returned {@link DefaultNettyConnection}.
     * @param sslConfig The {@link SslConfig} to use for the {@link DefaultNettyConnection}.
     * @param sslSession Provides access to the {@link SSLSession} associated with this connection.
     * @param parentChannelConfig {@link ChannelConfig} of the parent {@link Channel} to query {@link SocketOption}s.
     * @param streamObserver {@link StreamObserver} to report internal events.
     * @param isClient tells if this {@link Channel} is for the client.
     * @param shouldWait predicate that tells when request payload body should wait for continuation signal.
     * @param enrichProtocolError enriches protocol-specific {@link Throwable}s.
     * @param <Read> Type of objects read from the {@link NettyConnection}.
     * @param <Write> Type of objects written to the {@link NettyConnection}.
     * @return A {@link Single} that completes with a {@link DefaultNettyConnection} after the channel is activated and
     * ready to use.
     * @deprecated Use {@code #initChildChannel(Channel, ConnectionContext, CloseHandler, FlushStrategy, long, Protocol,
     * ChannelConfig, StreamObserver, boolean, Predicate, UnaryOperator)}.
     */
    @Deprecated // FIXME: 0.43 - remove deprecated method
    public static <Read, Write> DefaultNettyConnection<Read, Write> initChildChannel(
            Channel channel, ExecutionContext<?> executionContext,
            CloseHandler closeHandler, FlushStrategy flushStrategy,
            long idleTimeoutMs, Protocol protocol,
            @Nullable SslConfig sslConfig, @Nullable SSLSession sslSession,
            @Nullable ChannelConfig parentChannelConfig, StreamObserver streamObserver, boolean isClient,
            Predicate<Object> shouldWait, UnaryOperator<Throwable> enrichProtocolError) {
        return initChildChannel(channel, null, executionContext, closeHandler, flushStrategy, idleTimeoutMs, protocol,
                null, sslSession, parentChannelConfig, streamObserver, isClient, shouldWait, enrichProtocolError);
    }

    /**
     * Given a {@link Channel} this will initialize the {@link ChannelPipeline} just to create a
     * {@link DefaultNettyConnection}. It is assumed this is a child channel and all TLS handshaking is completed.
     * @param channel A newly created {@link Channel}.
     * @param parent The parent context as {@link NettyConnectionContext}.
     * @param closeHandler Manages the half closure of the {@link DefaultNettyConnection}.
     * @param flushStrategy Manages flushing of data for the {@link DefaultNettyConnection}.
     * @param idleTimeoutMs Value for {@link ServiceTalkSocketOptions#IDLE_TIMEOUT IDLE_TIMEOUT} socket option.
     * @param protocol {@link Protocol} for the returned {@link DefaultNettyConnection}.
     * @param parentChannelConfig {@link ChannelConfig} of the parent {@link Channel} to query {@link SocketOption}s.
     * @param streamObserver {@link StreamObserver} to report internal events.
     * @param isClient tells if this {@link Channel} is for the client.
     * @param shouldWait predicate that tells when request payload body should wait for continuation signal.
     * @param enrichProtocolError enriches protocol-specific {@link Throwable}s.
     * @param <Read> Type of objects read from the {@link NettyConnection}.
     * @param <Write> Type of objects written to the {@link NettyConnection}.
     * @return A {@link Single} that completes with a {@link DefaultNettyConnection} after the channel is activated and
     * ready to use.
     */
    public static <Read, Write> DefaultNettyConnection<Read, Write> initChildChannel(
            Channel channel, ConnectionContext parent, CloseHandler closeHandler, FlushStrategy flushStrategy,
            long idleTimeoutMs, Protocol protocol, @Nullable ChannelConfig parentChannelConfig,
            StreamObserver streamObserver, boolean isClient, Predicate<Object> shouldWait,
            UnaryOperator<Throwable> enrichProtocolError) {
        return initChildChannel(channel, requireNonNull(parent), parent.executionContext(), closeHandler,
                flushStrategy, idleTimeoutMs, protocol, parent.sslConfig(), parent.sslSession(),
                parentChannelConfig, streamObserver, isClient, shouldWait, enrichProtocolError);
    }

    private static <Read, Write> DefaultNettyConnection<Read, Write> initChildChannel(
            // FIXME: 0.43 - remove @Nullable from `parent` after all other overloads are removed
            Channel channel, @Nullable ConnectionContext parent, ExecutionContext<?> executionContext,
            CloseHandler closeHandler, FlushStrategy flushStrategy,
            long idleTimeoutMs, Protocol protocol,
            @Nullable SslConfig sslConfig, @Nullable SSLSession sslSession,
            @Nullable ChannelConfig parentChannelConfig, StreamObserver streamObserver, boolean isClient,
            Predicate<Object> shouldWait, UnaryOperator<Throwable> enrichProtocolError) {
        assert parent == null || parent.executionContext() == executionContext;
        assert channel.eventLoop() == toEventLoopAwareNettyIoExecutor(executionContext.ioExecutor()).eventLoopGroup();

        // For h2 the parent channel must use auto read because control frames and flow controlled frames are on the
        // same socket, and we must read in timely manner to avoid deadlock. Child channel should not use auto
        // read as read is explicitly called by NettyChannelPublisher according to the Subscription.request(n) demand.
        channel.config().setAutoRead(false);

        DefaultNettyConnection<Read, Write> connection = new DefaultNettyConnection<>(channel, parent, executionContext,
                closeHandler, flushStrategy, idleTimeoutMs, protocol, sslConfig, sslSession, parentChannelConfig,
                streamObserver.streamEstablished(), isClient, shouldWait, enrichProtocolError);
        channel.pipeline().addLast(new NettyToStChannelHandler<>(connection, null,
                null, false, NoopConnectionObserver.INSTANCE));
        return connection;
    }

    /**
     * Given a {@link Channel} this will initialize the {@link ChannelPipeline} and create a
     * {@link DefaultNettyConnection}. The resulting single will complete after the TLS handshake has completed
     * (if applicable) or otherwise after the channel is active and ready to use.
     * @param channel A newly created {@link Channel}.
     * @param allocator The {@link BufferAllocator} to use for the {@link DefaultNettyConnection}.
     * @param executor The {@link Executor} to use for the {@link DefaultNettyConnection}.
     * @param ioExecutor The {@link IoExecutor} to use for the {@link DefaultNettyConnection}.
     * @param closeHandler Manages the half closure of the {@link DefaultNettyConnection}.
     * @param flushStrategy Manages flushing of data for the {@link DefaultNettyConnection}.
     * @param idleTimeoutMs Value for {@link ServiceTalkSocketOptions#IDLE_TIMEOUT IDLE_TIMEOUT} socket option.
     * @param initializer Synchronously initializes the pipeline upon subscribe.
     * @param executionStrategy {@link ExecutionStrategy} to use for this connection.
     * @param protocol {@link Protocol} for the returned {@link DefaultNettyConnection}.
     * @param observer {@link ConnectionObserver} to report network events.
     * @param isClient tells if this {@link Channel} is for the client.
     * @param <Read> Type of objects read from the {@link NettyConnection}.
     * @param <Write> Type of objects written to the {@link NettyConnection}.
     * @return A {@link Single} that completes with a {@link DefaultNettyConnection} after the channel is activated and
     * ready to use.
     * @deprecated Use {@code #initChannel(Channel, ExecutionContext, CloseHandler, FlushStrategy, long, SslConfig,
     * ChannelInitializer, Protocol, ConnectionObserver, boolean, Predicate)}.
     */
    @Deprecated // FIXME: 0.43 - remove deprecated method
    public static <Read, Write> Single<DefaultNettyConnection<Read, Write>> initChannel(
            Channel channel, BufferAllocator allocator, Executor executor, @Nullable IoExecutor ioExecutor,
            CloseHandler closeHandler, FlushStrategy flushStrategy, Long idleTimeoutMs,
            ChannelInitializer initializer, ExecutionStrategy executionStrategy, Protocol protocol,
            ConnectionObserver observer, boolean isClient) {
        return initChannel(channel, allocator, executor, ioExecutor, closeHandler, flushStrategy, idleTimeoutMs,
                initializer, executionStrategy, protocol, observer, isClient, __ -> false);
    }

    /**
     * Given a {@link Channel} this will initialize the {@link ChannelPipeline} and create a
     * {@link DefaultNettyConnection}. The resulting single will complete after the TLS handshake has completed
     * (if applicable) or otherwise after the channel is active and ready to use.
     * @param channel A newly created {@link Channel}.
     * @param allocator The {@link BufferAllocator} to use for the {@link DefaultNettyConnection}.
     * @param executor The {@link Executor} to use for the {@link DefaultNettyConnection}.
     * @param ioExecutor The {@link IoExecutor} to use for the {@link DefaultNettyConnection}.
     * @param closeHandler Manages the half closure of the {@link DefaultNettyConnection}.
     * @param flushStrategy Manages flushing of data for the {@link DefaultNettyConnection}.
     * @param idleTimeoutMs Value for {@link ServiceTalkSocketOptions#IDLE_TIMEOUT IDLE_TIMEOUT} socket option.
     * @param initializer Synchronously initializes the pipeline upon subscribe.
     * @param executionStrategy {@link ExecutionStrategy} to use for this connection.
     * @param protocol {@link Protocol} for the returned {@link DefaultNettyConnection}.
     * @param observer {@link ConnectionObserver} to report network events.
     * @param isClient tells if this {@link Channel} is for the client.
     * @param shouldWait predicate that tells when request payload body should wait for continuation signal.
     * @param <Read> Type of objects read from the {@link NettyConnection}.
     * @param <Write> Type of objects written to the {@link NettyConnection}.
     * @return A {@link Single} that completes with a {@link DefaultNettyConnection} after the channel is activated and
     * ready to use.
     * @deprecated Use {@code #initChannel(Channel, ExecutionContext, CloseHandler, FlushStrategy, long, SslConfig,
     * ChannelInitializer, Protocol, ConnectionObserver, boolean, Predicate)}.
     */
    @Deprecated // FIXME: 0.43 - remove deprecated method
    public static <Read, Write> Single<DefaultNettyConnection<Read, Write>> initChannel(
            Channel channel, BufferAllocator allocator, Executor executor, @Nullable IoExecutor ioExecutor,
            CloseHandler closeHandler, FlushStrategy flushStrategy, Long idleTimeoutMs,
            ChannelInitializer initializer, ExecutionStrategy executionStrategy, Protocol protocol,
            ConnectionObserver observer, boolean isClient, Predicate<Object> shouldWait) {
        return initChannel(channel, allocator, executor, ioExecutor, closeHandler, flushStrategy, idleTimeoutMs, null,
                initializer, executionStrategy, protocol, observer, isClient, shouldWait);
    }

    /**
     * Given a {@link Channel} this will initialize the {@link ChannelPipeline} and create a
     * {@link DefaultNettyConnection}. The resulting single will complete after the TLS handshake has completed
     * (if applicable) or otherwise after the channel is active and ready to use.
     * @param channel A newly created {@link Channel}.
     * @param allocator The {@link BufferAllocator} to use for the {@link DefaultNettyConnection}.
     * @param executor The {@link Executor} to use for the {@link DefaultNettyConnection}.
     * @param ioExecutor The {@link IoExecutor} to use for the {@link DefaultNettyConnection}.
     * @param closeHandler Manages the half closure of the {@link DefaultNettyConnection}.
     * @param flushStrategy Manages flushing of data for the {@link DefaultNettyConnection}.
     * @param idleTimeoutMs Value for {@link ServiceTalkSocketOptions#IDLE_TIMEOUT IDLE_TIMEOUT} socket option.
     * @param sslConfig The {@link SslConfig} to use for the {@link DefaultNettyConnection}.
     * @param initializer Synchronously initializes the pipeline upon subscribe.
     * @param executionStrategy {@link ExecutionStrategy} to use for this connection.
     * @param protocol {@link Protocol} for the returned {@link DefaultNettyConnection}.
     * @param observer {@link ConnectionObserver} to report network events.
     * @param isClient tells if this {@link Channel} is for the client.
     * @param shouldWait predicate that tells when request payload body should wait for continuation signal.
     * @param <Read> Type of objects read from the {@link NettyConnection}.
     * @param <Write> Type of objects written to the {@link NettyConnection}.
     * @return A {@link Single} that completes with a {@link DefaultNettyConnection} after the channel is activated and
     * ready to use.
     * @deprecated Use {@code #initChannel(Channel, ExecutionContext, CloseHandler, FlushStrategy, long, SslConfig,
     * ChannelInitializer, Protocol, ConnectionObserver, boolean, Predicate)}.
     */
    @Deprecated
    public static <Read, Write> Single<DefaultNettyConnection<Read, Write>> initChannel(
            Channel channel, BufferAllocator allocator, Executor executor, @Nullable IoExecutor ioExecutor,
            CloseHandler closeHandler, FlushStrategy flushStrategy, long idleTimeoutMs,
            @Nullable SslConfig sslConfig,
            ChannelInitializer initializer, ExecutionStrategy executionStrategy, Protocol protocol,
            ConnectionObserver observer, boolean isClient, Predicate<Object> shouldWait) {
        boolean supportsIoThread = null != ioExecutor && ioExecutor.isIoThreadSupported();
        ExecutionContext<?> executionContext = new DefaultExecutionContext<>(allocator,
                fromNettyEventLoop(channel.eventLoop(), supportsIoThread), executor, executionStrategy);
        return initChannel(channel, executionContext, closeHandler, flushStrategy, idleTimeoutMs, sslConfig,
                initializer, protocol, observer, isClient, shouldWait);
    }

    /**
     * Given a {@link Channel} this will initialize the {@link ChannelPipeline} and create a
     * {@link DefaultNettyConnection}. The resulting single will complete after the TLS handshake has completed
     * (if applicable) or otherwise after the channel is active and ready to use.
     * @param channel A newly created {@link Channel}.
     * @param executionContext The {@link ExecutionContext} to use for the {@link DefaultNettyConnection}. Note:
     * {@link ExecutionContext#ioExecutor()} must be backed by a single {@link EventLoop} thread identical to
     * {@link Channel#eventLoop()} for the specified channel.
     * @param closeHandler Manages the half closure of the {@link DefaultNettyConnection}.
     * @param flushStrategy Manages flushing of data for the {@link DefaultNettyConnection}.
     * @param idleTimeoutMs Value for {@link ServiceTalkSocketOptions#IDLE_TIMEOUT IDLE_TIMEOUT} socket option.
     * @param sslConfig The {@link SslConfig} to use for the {@link DefaultNettyConnection}.
     * @param initializer Synchronously initializes the pipeline upon subscribe.
     * @param protocol {@link Protocol} for the returned {@link DefaultNettyConnection}.
     * @param observer {@link ConnectionObserver} to report network events.
     * @param isClient tells if this {@link Channel} is for the client.
     * @param shouldWait predicate that tells when request payload body should wait for continuation signal.
     * @param <Read> Type of objects read from the {@link NettyConnection}.
     * @param <Write> Type of objects written to the {@link NettyConnection}.
     * @return A {@link Single} that completes with a {@link DefaultNettyConnection} after the channel is activated and
     * ready to use.
     */
    public static <Read, Write> Single<DefaultNettyConnection<Read, Write>> initChannel(
            Channel channel, ExecutionContext<?> executionContext, CloseHandler closeHandler,
            FlushStrategy flushStrategy, long idleTimeoutMs, @Nullable SslConfig sslConfig,
            ChannelInitializer initializer, Protocol protocol, ConnectionObserver observer, boolean isClient,
            Predicate<Object> shouldWait) {
        assert channel.eventLoop() == toEventLoopAwareNettyIoExecutor(executionContext.ioExecutor()).eventLoopGroup();
        return new SubscribableSingle<DefaultNettyConnection<Read, Write>>() {
            @Override
            protected void handleSubscribe(
                    final SingleSource.Subscriber<? super DefaultNettyConnection<Read, Write>> subscriber) {
                final ChannelPipeline pipeline;
                final NettyToStChannelHandler<Read, Write> nettyInboundHandler;
                final DelayedCancellable delayedCancellable;
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
                    final SSLSession sslSession = extractSslSession(sslConfig, pipeline);
                    DefaultNettyConnection<Read, Write> connection = new DefaultNettyConnection<>(channel, null,
                            executionContext, closeHandler, flushStrategy, idleTimeoutMs, protocol, sslConfig,
                            sslSession, null, NoopDataObserver.INSTANCE, isClient, shouldWait, identity());
                    channel.attr(CHANNEL_CLOSEABLE_KEY).set(connection);
                    delayedCancellable = new DelayedCancellable();
                    nettyInboundHandler = new NettyToStChannelHandler<>(connection, subscriber,
                            delayedCancellable, shouldWaitForSslHandshake(sslSession, sslConfig, pipeline), observer);
                } catch (Throwable cause) {
                    close(channel, cause);
                    deliverErrorFromSource(subscriber, cause);
                    return;
                }
                try {
                    subscriber.onSubscribe(delayedCancellable);
                } catch (Throwable cause) {
                    close(channel, cause);
                    handleExceptionFromOnSubscribe(subscriber, cause);
                    return;
                }
                // We have to add to the pipeline AFTER we call onSubscribe, because adding to the pipeline may invoke
                // callbacks that interact with the subscriber.
                pipeline.addLast(nettyInboundHandler);
            }
        };
    }

    private static boolean shouldWaitForSslHandshake(@Nullable final SSLSession sslSession,
                                                     @Nullable final SslConfig sslConfig,
                                                     final ChannelPipeline pipeline) {
        return sslConfig != null && sslSession == null && pipeline.get(DeferSslHandler.class) == null;
    }

    private Publisher<Read> registerReadObserver(final Publisher<Read> readPublisher) {
        return readPublisher.liftSync(target -> {
            final DataObserver dataObserver = this.dataObserver;
            if (dataObserver == NoopDataObserver.INSTANCE) {
                return target;
            }
            final ReadObserver observer = dataObserver.onNewRead();
            if (observer == NoopReadObserver.INSTANCE) {
                return target;
            }
            return new PublisherSource.Subscriber<Read>() {
                @Override
                public void onSubscribe(final Subscription subscription) {
                    target.onSubscribe(new Subscription() {
                        @Override
                        public void request(final long n) {
                            observer.requestedToRead(n);
                            subscription.request(n);
                        }

                        @Override
                        public void cancel() {
                            observer.readCancelled();
                            subscription.cancel();
                        }
                    });
                }

                @Override
                public void onNext(@Nullable final Read read) {
                    observer.itemRead(read);
                    target.onNext(read);
                }

                @Override
                public void onError(final Throwable t) {
                    observer.readFailed(t);
                    target.onError(t);
                }

                @Override
                public void onComplete() {
                    observer.readComplete();
                    target.onComplete();
                }
            };
        });
    }

    private Throwable enrichError(final Throwable t) {
        Throwable throwable;
        CloseEvent closeReason;
        if (t instanceof AbortedFirstWriteException) {
            if ((closeReason = this.closeReason) != null) {
                throwable = new RetryableClosedChannelException(wrapWithCloseReason(closeReason, t.getCause()));
            } else if (t.getCause() instanceof RetryableException) {
                // Unwrap additional layer of RetryableException if the cause is already retryable
                throwable = t.getCause();
            } else if (t.getCause() instanceof ClosedChannelException) {
                throwable = new RetryableClosedChannelException((ClosedChannelException) t.getCause());
            } else {
                throwable = t;
            }
        } else if (t instanceof RetryableClosedChannelException) {
            throwable = t;
        } else {
            if ((closeReason = this.closeReason) != null) {
                throwable = wrapWithCloseReason(closeReason, t);
            } else {
                throwable = enrichProtocolError.apply(t);
            }
        }
        transportError.onSuccess(throwable);
        return throwable;
    }

    private ClosedChannelException wrapWithCloseReason(final CloseEvent closeReason, final Throwable t) {
        if (t instanceof CloseEventObservedException && ((CloseEventObservedException) t).event() == closeReason) {
            return (ClosedChannelException) t;
        }
        return closeReason.wrapError(t, channel());
    }

    @Override
    public Publisher<Read> read() {
        return readPublisher;
    }

    @Override
    public Completable write(Publisher<Write> write) {
        return write(write, flushStrategyHolder::currentStrategy, WriteDemandEstimators::newDefaultEstimator);
    }

    @Override
    public Completable write(final Publisher<Write> write,
                             final Supplier<FlushStrategy> flushStrategySupplier,
                             final Supplier<WriteDemandEstimator> demandEstimatorSupplier) {
        return cleanupStateWhenDone(new SubscribableCompletable() {
            @Override
            protected void handleSubscribe(Subscriber completableSubscriber) {
                final WriteObserver writeObserver = DefaultNettyConnection.this.dataObserver.onNewWrite();
                WriteStreamSubscriber subscriber = new WriteStreamSubscriber(channel(), demandEstimatorSupplier.get(),
                        completableSubscriber, closeHandler, writeObserver, enrichProtocolError, isClient, shouldWait);
                if (failIfWriteActive(subscriber, completableSubscriber)) {
                    toSource(composeFlushes(channel(), write, flushStrategySupplier.get(), writeObserver)
                            // AsyncContext state should be shared between publisher we write and returned completable
                            .shareContextOnSubscribe())
                            .subscribe(subscriber);
                }
            }
        }).onErrorMap(this::enrichError);
    }

    /**
     * Visible for testing.
     * <p>
     * This connection does not allow concurrent writes and so this method can determine if there is a writing pending.
     *
     * @return {@code true} if a write is already active.
     */
    boolean isWriteActive() {
        final ChannelOutboundListener listener = channelOutboundListener;
        return listener != NoopChannelOutboundListener.INSTANCE && listener != this;
    }

    @Override
    protected void doCloseAsyncGracefully() {
        EventLoop eventLoop = channel().eventLoop();
        if (eventLoop.inEventLoop()) {
            invokeUserCloseHandler();
        } else {
            eventLoop.execute(DefaultNettyConnection.this::invokeUserCloseHandler);
        }
    }

    @Override
    public String connectionId() {
        return "0x" + channel().id().asShortText();
    }

    @Override
    public SocketAddress localAddress() {
        return channel().localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return channel().remoteAddress();
    }

    @Nullable
    @Override
    public SslConfig sslConfig() {
        return sslConfig;
    }

    @Override
    public SSLSession sslSession() {
        return sslSession;
    }

    @Override
    public ExecutionContext<?> executionContext() {
        return executionContext;
    }

    @Nullable
    @Override
    public <T> T socketOption(final SocketOption<T> option) {
        return getOption(option, parentChannelConfig != null ? parentChannelConfig : channel().config(), idleTimeoutMs);
    }

    @Override
    public Protocol protocol() {
        return protocol;
    }

    @Nullable
    @Override
    public ConnectionContext parent() {
        return parent;
    }

    private void invokeUserCloseHandler() {
        closeHandler.gracefulUserClosing(channel());
    }

    @Override
    public Channel nettyChannel() {
        return channel();
    }

    @Override
    public String toString() {
        return channel().toString();
    }

    private Completable cleanupStateWhenDone(Completable completable) {
        // This must happen before we actually trigger the original Subscribers methods so using before* variants.
        return completable.beforeFinally(cleanupStateConsumer);
    }

    @Override
    public void channelWritable() {
    }

    @Override
    public void continueWriting() {
    }

    @Override
    public void channelOutboundClosed() {
    }

    @Override
    public void terminateSource() {
    }

    @Override
    public void channelClosed(final Throwable closedException) {
        // Make sure the channel is closed. If this is from a timeout or non-transport error related cancellation
        // the transport may not yet have been closed.
        closeHandler.closeChannelOutbound(channel());
    }

    @Override
    public void listenerDiscard(final Throwable cause) {
    }

    private boolean failIfWriteActive(final ChannelOutboundListener newListener, final Subscriber subscriber) {
        for (;;) {
            final ChannelOutboundListener listener = this.channelOutboundListener;
            if (listener != NoopChannelOutboundListener.INSTANCE && listener != this) {
                deliverErrorFromSource(subscriber,
                        new IllegalStateException("A write is already active on this connection."));
                return false;
            } else if (writableListenerUpdater.compareAndSet(this, listener, newListener)) {
                // It is possible that we have set the writeSubscriber, then the channel becomes inactive, and we will
                // never notify the write writeSubscriber of the inactive event. So if the channel is inactive we notify
                // the writeSubscriber.
                // It is also possible that Channel is in closing state, we should abort new writes from the client-side
                // if a closeReason was observed:
                CloseEvent closeReason = this.closeReason;
                boolean channelActive = true;
                if ((isClient && closeReason != null) || !(channelActive = channel().isActive())) {
                    final StacklessClosedChannelException e = StacklessClosedChannelException.newInstance(
                            DefaultNettyConnection.class, "failIfWriteActive(...)");
                    Throwable cause = closeReason == null ? e : closeReason.wrapError(e, channel());
                    if (channelActive) {
                        newListener.listenerDiscard(cause);
                    } else {
                        newListener.channelClosed(cause);
                    }
                    return false;
                }
                return true;
            }
        }
    }

    @Override
    public Cancellable updateFlushStrategy(final FlushStrategyProvider strategyProvider) {
        return flushStrategyHolder.updateFlushStrategy(strategyProvider);
    }

    @Override
    public FlushStrategy defaultFlushStrategy() {
        return flushStrategyHolder.currentStrategy();
    }

    @Override
    public Single<Throwable> transportError() {
        return fromSource(transportError);
    }

    private static final class NoopChannelOutboundListener implements ChannelOutboundListener {
        private static final ChannelOutboundListener INSTANCE = new NoopChannelOutboundListener();

        private NoopChannelOutboundListener() {
        }

        @Override
        public void channelWritable() {
        }

        @Override
        public void continueWriting() {
        }

        @Override
        public void channelOutboundClosed() {
        }

        @Override
        public void terminateSource() {
        }

        @Override
        public void channelClosed(Throwable closedException) {
        }

        @Override
        public void listenerDiscard(final Throwable cause) {
        }
    }

    private static final class NettyToStChannelHandler<Read, Write> extends ChannelDuplexHandler {
        private final DefaultNettyConnection<Read, Write> connection;
        private final boolean waitForSslHandshake;
        @Nullable
        private final DelayedCancellable delayedCancellable;
        @Nullable
        private SingleSource.Subscriber<? super DefaultNettyConnection<Read, Write>> subscriber;
        private final ConnectionObserver observer;

        NettyToStChannelHandler(DefaultNettyConnection<Read, Write> connection,
                                @Nullable
                                SingleSource.Subscriber<? super DefaultNettyConnection<Read, Write>> subscriber,
                                @Nullable DelayedCancellable delayedCancellable,
                                boolean waitForSslHandshake,
                                ConnectionObserver observer) {
            this.connection = connection;
            this.subscriber = subscriber;
            this.delayedCancellable = delayedCancellable;
            this.waitForSslHandshake = waitForSslHandshake;
            this.observer = observer;
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
            connection.closeHandler.channelClose(ctx.channel());
            ctx.close(promise);
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            if (ctx.channel().isWritable()) {
                connection.channelOutboundListener.channelWritable();
            } else if (connection.flushStrategyHolder.currentStrategy().shouldFlushOnUnwritable()) {
                // TODO(scott): if we have a flush per write operation, shouldFlushOnUnwritable is more challenging.
                //  do we need to care about this any more?
                ctx.flush();
            }
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            if (delayedCancellable != null) {
                delayedCancellable.delayedCancellable(ctx.channel()::close);
            }
            // Double check In the event of a late handler (or test utility like EmbeddedChannel) check activeness.
            if (ctx.channel().isActive()) {
                doChannelActive(ctx);
            }
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            if (subscriber != null) {
                tryFailSubscriber(StacklessClosedChannelException.newInstance(
                        DefaultNettyConnection.class, "handlerRemoved(...)"));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            // NettyChannelPublisher will force closure of the channel in case of exception after the cause is
            // propagated to users. In case users don't have offloading, there is a risk to retry on the same IO thread.
            // We should notify LoadBalancer that this connection is closing to avoid retrying on the same connection.
            connection.notifyOnClosing();
            connection.nettyChannelPublisher.channelOnError(unwrapThrowable(cause));
        }

        /**
         * Unwraps certain types of netty exceptions to directly expose its cause to improve debuggability.
         */
        private static Throwable unwrapThrowable(final Throwable t) {
            final Throwable cause;
            if (t instanceof DecoderException && (cause = t.getCause()) instanceof SSLException) {
                return cause;
            }
            return t;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            @SuppressWarnings("unchecked")
            final Read t = (Read) msg;
            connection.nettyChannelPublisher.channelRead(t);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            connection.nettyChannelPublisher.onReadComplete();
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            LOGGER.debug("{} Received a user event: {}", ctx.channel(), evt);
            if (evt == CloseHandler.InboundDataEndEvent.INSTANCE) {
                connection.nettyChannelPublisher.channelOnComplete();
            } else if (evt == CloseHandler.OutboundDataEndEvent.INSTANCE) {
                connection.channelOutboundListener.channelOutboundClosed();
            } else if (evt == AbortWritesEvent.INSTANCE) {
                connection.channelOutboundListener.listenerDiscard(StacklessClosedChannelException.newInstance(
                        DefaultNettyConnection.class, "userEventTriggered(AbortWritesEvent)"));
            } else if (evt == ChannelOutputShutdownEvent.INSTANCE) {
                connection.closeHandler.channelClosedOutbound(ctx);
                connection.channelOutboundListener.channelClosed(StacklessClosedChannelException.newInstance(
                        DefaultNettyConnection.class, "userEventTriggered(ChannelOutputShutdownEvent)"));
            } else if (evt == SslCloseCompletionEvent.SUCCESS) {
                connection.closeHandler.channelCloseNotify(ctx);
            } else if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                // Notify close handler first to enhance error reporting and prevent LB from selecting this connection
                connection.closeHandler.channelClosedInbound(ctx);
                // ChannelInputShutdownEvent is not always triggered and can get triggered before we tried to read
                // all the available data. ChannelInputShutdownReadComplete is the one that seems to (at least in
                // the current netty version) gets triggered reliably at the appropriate time.
                connection.nettyChannelPublisher.channelOnError(StacklessClosedChannelException.newInstance(
                        DefaultNettyConnection.class, "userEventTriggered(ChannelInputShutdownReadComplete)"));
            } else if (evt instanceof SslHandshakeCompletionEvent) {
                connection.sslSession = extractSslSession(ctx.pipeline(), (SslHandshakeCompletionEvent) evt,
                        this::tryFailSubscriber);
                if (subscriber != null && waitForSslHandshake) {
                    completeSubscriber();
                }
            } else if (evt == ContinueUserEvent.INSTANCE) {
                assert connection.isClient;
                connection.channelOutboundListener.continueWriting();
            } else if (evt == CancelWriteUserEvent.INSTANCE) {
                assert connection.isClient;
                connection.channelOutboundListener.terminateSource();
            }
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) {
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            doChannelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            connection.notifyOnClosing();
            Throwable closedChannelException = StacklessClosedChannelException.newInstance(
                    DefaultNettyConnection.class, "channelInactive(...)");
            tryFailSubscriber(closedChannelException);
            connection.channelOutboundListener.channelClosed(closedChannelException);
            connection.nettyChannelPublisher.channelOnError(closedChannelException);
        }

        private void doChannelActive(ChannelHandlerContext ctx) {
            if (waitForSslHandshake) {
                if (!connection.isClient) {
                    // Force a read to get the SSL handshake started, any application data that makes it past the
                    // SslHandler will be queued in the NettyChannelPublisher.
                    ctx.read();
                }
            } else if (subscriber != null) {
                completeSubscriber();
            }
        }

        private void completeSubscriber() {
            assert subscriber != null;
            SingleSource.Subscriber<? super DefaultNettyConnection<Read, Write>> subscriberCopy = subscriber;
            subscriber = null;
            // TODO: how can we make sure we have the correct context information here.
            //  See HttpTransportObserverAsyncContextTest. It has some assertions for 'broken' behavior.
            connection.dataObserver = observer.connectionEstablished(connection);
            subscriberCopy.onSuccess(connection);
        }

        private void tryFailSubscriber(Throwable cause) {
            if (subscriber != null) {
                ChannelCloseUtils.close(connection.channel(), cause);
                SingleSource.Subscriber<? super DefaultNettyConnection<Read, Write>> subscriberCopy = subscriber;
                subscriber = null;
                subscriberCopy.onError(cause);
            }
        }
    }

    /**
     * Netty UserEvent to signal continuation.
     */
    public static final class ContinueUserEvent {
        public static final ContinueUserEvent INSTANCE = new ContinueUserEvent();

        private ContinueUserEvent() {
            // No instances.
        }

        @Override
        public String toString() {
            return this.getClass().getName();
        }
    }

    /**
     * Netty UserEvent to signal cancellation of the write stream.
     */
    public static final class CancelWriteUserEvent {
        public static final CancelWriteUserEvent INSTANCE = new CancelWriteUserEvent();

        private CancelWriteUserEvent() {
            // No instances.
        }

        @Override
        public String toString() {
            return this.getClass().getName();
        }
    }
}
