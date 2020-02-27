/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableCompletable;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent;
import io.servicetalk.transport.netty.internal.CloseHandler.CloseEventObservedException;
import io.servicetalk.transport.netty.internal.WriteStreamSubscriber.AbortedFirstWrite;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.ChannelOutputShutdownEvent;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.ThrowableUtils.unknownStackTrace;
import static io.servicetalk.transport.netty.internal.ChannelSet.CHANNEL_CLOSEABLE_KEY;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.Flush.composeFlushes;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.fromNettyEventLoop;
import static io.servicetalk.transport.netty.internal.NettyPipelineSslUtils.extractSslSession;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * Implementation of {@link NettyConnection} backed by a netty {@link Channel}.
 *
 * @param <Read> Type of objects read from this connection.
 * @param <Write> Type of objects written to this connection.
 */
public final class DefaultNettyConnection<Read, Write> extends NettyChannelListenableAsyncCloseable
        implements NettyConnection<Read, Write> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultNettyConnection.class);

    private static final TerminalPredicate PIPELINE_UNSUPPORTED_PREDICATE = new TerminalPredicate<>();

    private static final ChannelOutboundListener PLACE_HOLDER_OUTBOUND_LISTENER = new NoopChannelOutboundListener();
    private static final ChannelOutboundListener SINGLE_ITEM_OUTBOUND_LISTENER = new NoopChannelOutboundListener();

    private static final ClosedChannelException CLOSED_CHANNEL_INACTIVE = unknownStackTrace(
            new ClosedChannelException(), NettyToStChannelInboundHandler.class, "channelInactive(..)");
    private static final ClosedChannelException CLOSED_FAIL_ACTIVE =
            unknownStackTrace(new ClosedChannelException(), DefaultNettyConnection.class, "failIfWriteActive(..)");
    private static final ClosedChannelException CLOSED_HANDLER_REMOVED =
            unknownStackTrace(new ClosedChannelException(), NettyToStChannelInboundHandler.class, "handlerRemoved(..)");
    private static final AtomicReferenceFieldUpdater<DefaultNettyConnection, ChannelOutboundListener>
            writableListenerUpdater = newUpdater(DefaultNettyConnection.class, ChannelOutboundListener.class,
                                                 "channelOutboundListener");

    private final TerminalPredicate<Read> terminalMsgPredicate;
    private final CloseHandler closeHandler;
    private final NettyChannelPublisher<Read> nettyChannelPublisher;
    private final Publisher<Read> readPublisher;
    private final ExecutionContext executionContext;
    @Nullable
    private final CompletableSource.Processor onClosing;
    private final SingleSource.Processor<Throwable, Throwable> transportError = newSingleProcessor();
    private final FlushStrategyHolder flushStrategyHolder;
    private volatile ChannelOutboundListener channelOutboundListener = PLACE_HOLDER_OUTBOUND_LISTENER;
    /**
     * Potentially contains more information when a protocol or channel level close event was observed.
     * <p>
     * Always accessed from the event loop, doesn't require synchronization.
     */
    @Nullable
    private CloseEvent closeReason;
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

    private DefaultNettyConnection(Channel channel, BufferAllocator allocator, Executor executor,
                                   TerminalPredicate<Read> terminalMsgPredicate, CloseHandler closeHandler,
                                   FlushStrategy flushStrategy, ExecutionStrategy executionStrategy) {
        this(channel, allocator, executor, terminalMsgPredicate, closeHandler, flushStrategy, executionStrategy, null);
    }

    private DefaultNettyConnection(Channel channel, BufferAllocator allocator, Executor executor,
                                   TerminalPredicate<Read> terminalMsgPredicate, CloseHandler closeHandler,
                                   FlushStrategy flushStrategy, ExecutionStrategy executionStrategy,
                                   @Nullable SSLSession sslSession) {
        super(channel, executor);
        nettyChannelPublisher = new NettyChannelPublisher<>(channel, terminalMsgPredicate, closeHandler);
        this.readPublisher = nettyChannelPublisher.recoverWith(this::enrichErrorPublisher);
        this.terminalMsgPredicate = requireNonNull(terminalMsgPredicate);
        this.executionContext = new DefaultExecutionContext(allocator, fromNettyEventLoop(channel.eventLoop()),
                executor, executionStrategy);
        this.closeHandler = requireNonNull(closeHandler);
        flushStrategyHolder = new FlushStrategyHolder(flushStrategy);
        if (closeHandler != UNSUPPORTED_PROTOCOL_CLOSE_HANDLER) {
            onClosing = newCompletableProcessor();
            closeHandler.registerEventHandler(channel, evt -> { // Called from EventLoop only!
                if (closeReason == null) {
                    closeReason = evt;
                    // Notify onClosing ASAP to notify the LoadBalancer to stop using the connection.
                    onClosing.onComplete();
                    transportError.onSuccess(evt.wrapError(null, channel));
                    LOGGER.debug("{} Emitted CloseEvent: {}", channel, evt);
                }
            });
            // Users may depend on onClosing to be notified for all kinds of closures and not just graceful close.
            // So, we should make sure that onClosing at least terminates with the channel.
            // Since, onClose is guaranteed to be notified for any kind of closures, we cascade it to onClosing.
            // An alternative would be to intercept channelInactive() in the pipeline but adding a pipeline handler
            // in the pipeline may race with closure as we have already created the channel. If that happens, we may
            // miss channelInactive event.
            toSource(onClose()
                    // If we do offload subscribe, we will hold up a thread for the lifetime of the connection.
                    // As we do offload "publish" for "onClosing", we can avoid offloading of "onClose" as we know
                    // Subscriber end of CompletableProcessor (onClosing) will not block.
                    .publishAndSubscribeOnOverride(immediate()))
                    .subscribe(onClosing);
        } else {
            onClosing = null;
        }
        this.sslSession = sslSession;
    }

    @SuppressWarnings("unchecked")
    static <Read, Write> Single<DefaultNettyConnection<Read, Write>> initChannel(
            Channel channel, BufferAllocator allocator, Executor executor, FlushStrategy flushStrategy,
            ChannelInitializer initializer, ExecutionStrategy executionStrategy) {
        return initChannel(channel, allocator, executor, PIPELINE_UNSUPPORTED_PREDICATE,
                UNSUPPORTED_PROTOCOL_CLOSE_HANDLER, flushStrategy, initializer, executionStrategy);
    }

    /**
     * Given a {@link Channel} this will initialize the {@link ChannelPipeline} just to create a
     * {@link DefaultNettyConnection}. It is assumed this is a child channel and all TLS handshaking is completed.
     * @param channel A newly created {@link Channel}.
     * @param allocator The {@link BufferAllocator} to use for the {@link DefaultNettyConnection}.
     * @param executor The {@link Executor} to use for the {@link DefaultNettyConnection}.
     * @param terminalMsgPredicate Used to determine which inbound signal on the {@link #read()} stream terminates the
     * current message framing and will allow a resubscribe to consume the next framing.
     * @param closeHandler Manages the half closure of the {@link DefaultNettyConnection}.
     * @param flushStrategy Manages flushing of data for the {@link DefaultNettyConnection}.
     * @param executionStrategy Used to derive the {@link #executionContext()}.
     * @param sslSession Provides access to the {@link SSLSession} associated with this connection.
     * @param <Read> Type of objects read from the {@link NettyConnection}.
     * @param <Write> Type of objects written to the {@link NettyConnection}.
     * @return A {@link Single} that completes with a {@link DefaultNettyConnection} after the channel is activated and
     * ready to use.
     */
    public static <Read, Write> DefaultNettyConnection<Read, Write> initChildChannel(
            Channel channel, BufferAllocator allocator, Executor executor, TerminalPredicate<Read> terminalMsgPredicate,
            CloseHandler closeHandler, FlushStrategy flushStrategy, ExecutionStrategy executionStrategy,
            @Nullable SSLSession sslSession) {
        DefaultNettyConnection<Read, Write> connection = new DefaultNettyConnection<>(channel, allocator,
                executor, terminalMsgPredicate, closeHandler, flushStrategy, executionStrategy, sslSession);
        channel.pipeline().addLast(new NettyToStChannelInboundHandler<>(connection, null,
                null, false));
        return connection;
    }

    /**
     * Given a {@link Channel} this will initialize the {@link ChannelPipeline} and create a
     * {@link DefaultNettyConnection}. The resulting single will complete after the TLS handshake has completed
     * (if applicable) or otherwise after the channel is active and ready to use.
     * @param channel A newly created {@link Channel}.
     * @param allocator The {@link BufferAllocator} to use for the {@link DefaultNettyConnection}.
     * @param executor The {@link Executor} to use for the {@link DefaultNettyConnection}.
     * @param terminalMsgPredicate Used to determine which inbound signal on the {@link #read()} stream terminates the
     * current message framing and will allow a resubscribe to consume the next framing.
     * @param closeHandler Manages the half closure of the {@link DefaultNettyConnection}.
     * @param flushStrategy Manages flushing of data for the {@link DefaultNettyConnection}.
     * @param initializer Synchronously initializes the pipeline upon subscribe.
     * @param executionStrategy {@link ExecutionStrategy} to use for this connection.
     * @param <Read> Type of objects read from the {@link NettyConnection}.
     * @param <Write> Type of objects written to the {@link NettyConnection}.
     * @return A {@link Single} that completes with a {@link DefaultNettyConnection} after the channel is activated and
     * ready to use.
     */
    public static <Read, Write> Single<DefaultNettyConnection<Read, Write>> initChannel(
            Channel channel, BufferAllocator allocator, Executor executor, TerminalPredicate<Read> terminalMsgPredicate,
            CloseHandler closeHandler, FlushStrategy flushStrategy, ChannelInitializer initializer,
            ExecutionStrategy executionStrategy) {
        return new SubscribableSingle<DefaultNettyConnection<Read, Write>>() {
            @Override
            protected void handleSubscribe(
                    final SingleSource.Subscriber<? super DefaultNettyConnection<Read, Write>> subscriber) {
                final NettyToStChannelInboundHandler<Read, Write> nettyInboundHandler;
                final DelayedCancellable delayedCancellable;
                try {
                    delayedCancellable = new DelayedCancellable();
                    DefaultNettyConnection<Read, Write> connection = new DefaultNettyConnection<>(channel, allocator,
                            executor, terminalMsgPredicate, closeHandler, flushStrategy, executionStrategy);
                    channel.attr(CHANNEL_CLOSEABLE_KEY).set(connection);
                    // We need the NettyToStChannelInboundHandler to be last in the pipeline. We accomplish that by
                    // calling the ChannelInitializer before we do addLast for the NettyToStChannelInboundHandler.
                    // This could mean if there are any synchronous events generated via ChannelInitializer handlers
                    // that NettyToStChannelInboundHandler will not see them. This is currently not an issue and would
                    // require some pipeline modifications if we wanted to insert NettyToStChannelInboundHandler first,
                    // but not allow any other handlers to be after it.
                    initializer.init(channel);
                    ChannelPipeline pipeline = connection.channel().pipeline();
                    nettyInboundHandler = new NettyToStChannelInboundHandler<>(connection, subscriber,
                            delayedCancellable, NettyPipelineSslUtils.isSslEnabled(pipeline));
                } catch (Throwable cause) {
                    channel.close();
                    subscriber.onSubscribe(IGNORE_CANCEL);
                    subscriber.onError(cause);
                    return;
                }
                subscriber.onSubscribe(delayedCancellable);
                // We have to add to the pipeline AFTER we call onSubscribe, because adding to the pipeline may invoke
                // callbacks that interact with the subscriber.
                channel.pipeline().addLast(nettyInboundHandler);
            }
        };
    }

    private Publisher<Read> enrichErrorPublisher(final Throwable t) {
        return failed(enrichError(t));
    }

    private Completable enrichErrorCompletable(final Throwable t) {
        return Completable.failed(enrichError(t));
    }

    private Throwable enrichError(final Throwable t) {
        final Throwable throwable;
        if (t instanceof AbortedFirstWrite) {
            final Throwable cause = t.getCause();
            if (closeReason != null) {
                throwable = new RetryableClosureException(closeReason.wrapError(cause, channel()));
            } else {
                throwable = cause;
            }
        } else if (t instanceof RetryableClosureException) {
            throwable = t;
        } else if (t instanceof CloseEventObservedException) {
            throwable = closeReason == ((CloseEventObservedException) t).event() ? t : wrapIfReasonIsKnown(t);
        } else {
            throwable = wrapIfReasonIsKnown(t);
        }
        transportError.onSuccess(throwable);
        return throwable;
    }

    private Throwable wrapIfReasonIsKnown(final Throwable t) {
        return closeReason != null ? closeReason.wrapError(t, channel()) : t;
    }

    private void cleanupOnWriteTerminated() {
        channelOutboundListener = PLACE_HOLDER_OUTBOUND_LISTENER;
    }

    @Override
    public Publisher<Read> read() {
        return readPublisher;
    }

    @Override
    public TerminalPredicate<Read> terminalMsgPredicate() {
        return terminalMsgPredicate;
    }

    @Override
    public Completable write(Publisher<Write> write) {
        return write(write, RequestNSupplier::newDefaultSupplier);
    }

    @Override
    public Completable write(Publisher<Write> write, Supplier<RequestNSupplier> requestNSupplierFactory) {
        return cleanupStateWhenDone(new SubscribableCompletable() {
            @Override
            protected void handleSubscribe(Subscriber completableSubscriber) {
                WriteStreamSubscriber subscriber = new WriteStreamSubscriber(channel(), requestNSupplierFactory.get(),
                        completableSubscriber, closeHandler);
                if (failIfWriteActive(subscriber, completableSubscriber)) {
                    toSource(composeFlushes(channel(), write, flushStrategyHolder.currentStrategy()))
                            .subscribe(subscriber);
                }
            }
        }).onErrorResume(this::enrichErrorCompletable);
    }

    @Override
    public Completable writeAndFlush(Single<Write> write) {
        requireNonNull(write);
        return cleanupStateWhenDone(new SubscribableCompletable() {
            @Override
            protected void handleSubscribe(Subscriber completableSubscriber) {
                WriteSingleSubscriber subscriber = new WriteSingleSubscriber(
                        channel(), requireNonNull(completableSubscriber), closeHandler);
                if (failIfWriteActive(subscriber, completableSubscriber)) {
                    toSource(write).subscribe(subscriber);
                }
            }
        }).onErrorResume(this::enrichErrorCompletable);
    }

    @Override
    public Completable writeAndFlush(Write write) {
        requireNonNull(write);
        return cleanupStateWhenDone(new NettyFutureCompletable(() -> {
            if (writableListenerUpdater.compareAndSet(DefaultNettyConnection.this, PLACE_HOLDER_OUTBOUND_LISTENER,
                    SINGLE_ITEM_OUTBOUND_LISTENER)) {
                return channel().writeAndFlush(write);
            }
            return channel().newFailedFuture(
                    new IllegalStateException("A write is already active on this connection."));
        })).onErrorResume(this::enrichErrorCompletable);
    }

    /**
     * This connection does not allow concurrent writes and so this method can determine if there is a writing pending.
     *
     * @return {@code true} if a write is already active.
     */
    boolean isWriteActive() {
        return channelOutboundListener != PLACE_HOLDER_OUTBOUND_LISTENER;
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
    public SocketAddress localAddress() {
        return channel().localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return channel().remoteAddress();
    }

    @Override
    public SSLSession sslSession() {
        return sslSession;
    }

    @Override
    public ExecutionContext executionContext() {
        return executionContext;
    }

    private void invokeUserCloseHandler() {
        closeHandler.userClosing(channel());
    }

    @Override
    public Completable onClosing() {
        return onClosing == null ? onClose() : fromSource(onClosing).publishOn(executionContext().executor());
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
        return completable.beforeFinally(this::cleanupOnWriteTerminated);
    }

    private boolean failIfWriteActive(ChannelOutboundListener newChannelOutboundListener, Subscriber subscriber) {
        if (writableListenerUpdater.compareAndSet(this, PLACE_HOLDER_OUTBOUND_LISTENER, newChannelOutboundListener)) {
            // It is possible that we have set the writeSubscriber, then the channel becomes inactive, and we will
            // never notify the write writeSubscriber of the inactive event. So if the channel is inactive we notify
            // the writeSubscriber.
            if (!channel().isActive()) {
                newChannelOutboundListener.channelClosed(CLOSED_FAIL_ACTIVE);
                return false;
            }
            return true;
        }
        subscriber.onSubscribe(IGNORE_CANCEL);
        subscriber.onError(new IllegalStateException("A write is already active on this connection."));
        return false;
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
        return fromSource(transportError).publishOn(executionContext().executor());
    }

    /**
     * An interface which provides methods that are invoked when outbound channel events occur. The implementors of
     * this interface are effectively "listening" to these events via method calls.
     */
    interface ChannelOutboundListener {
        /**
         * Notification that the writability of the channel has changed.
         * <p>
         * Always called from the event loop thread.
         */
        void channelWritable();

        /**
         * Notification that the channel's outbound side has been closed and will no longer accept writes.s
         * <p>
         * Always called from the event loop thread.
         */
        void channelOutboundClosed();

        /**
         * Notification that the channel has been closed.
         * <p>
         * This may not always be called from the event loop thread. For example if the channel is closed when a new
         * write happens then this method will be called from the writer thread.
         *
         * @param closedException the exception which describes the close rational.
         */
        void channelClosed(Throwable closedException);
    }

    private static final class NoopChannelOutboundListener implements ChannelOutboundListener {
        @Override
        public void channelWritable() {
        }

        @Override
        public void channelOutboundClosed() {
        }

        @Override
        public void channelClosed(Throwable closedException) {
        }
    }

    private static final class NettyToStChannelInboundHandler<Read, Write> implements ChannelInboundHandler {
        private final DefaultNettyConnection<Read, Write> connection;
        private final boolean waitForSslHandshake;
        @Nullable
        private final DelayedCancellable delayedCancellable;
        @Nullable
        private SingleSource.Subscriber<? super DefaultNettyConnection<Read, Write>> subscriber;

        NettyToStChannelInboundHandler(DefaultNettyConnection<Read, Write> connection,
                                       @Nullable
                                       SingleSource.Subscriber<? super DefaultNettyConnection<Read, Write>> subscriber,
                                       @Nullable
                                       DelayedCancellable delayedCancellable,
                                       boolean waitForSslHandshake) {
            this.connection = connection;
            this.subscriber = subscriber;
            this.delayedCancellable = delayedCancellable;
            this.waitForSslHandshake = waitForSslHandshake;
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            if (ctx.channel().isWritable()) {
                connection.channelOutboundListener.channelWritable();
            } else if (connection.flushStrategyHolder.currentStrategy().shouldFlushOnUnwritable()) {
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
            tryFailSubscriber(CLOSED_HANDLER_REMOVED);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            connection.nettyChannelPublisher.exceptionCaught(cause);
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
            if (evt == CloseHandler.ProtocolPayloadEndEvent.OUTBOUND) {
                connection.channelOutboundListener.channelOutboundClosed();
            } else if (evt == ChannelOutputShutdownEvent.INSTANCE) {
                connection.closeHandler.channelClosedOutbound(ctx);
                connection.channelOutboundListener.channelClosed(CLOSED_CHANNEL_INACTIVE);
            } else if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                // Notify close handler first to enhance error reporting
                connection.closeHandler.channelClosedInbound(ctx);
                // ChannelInputShutdownEvent is not always triggered and can get triggered before we tried to read
                // all the available data. ChannelInputShutdownReadComplete is the one that seems to (at least in
                // the current netty version) gets triggered reliably at the appropriate time.
                connection.nettyChannelPublisher.channelInboundClosed();
            } else if (evt instanceof SslHandshakeCompletionEvent) {
                connection.sslSession = extractSslSession(ctx.pipeline(), (SslHandshakeCompletionEvent) evt,
                        this::tryFailSubscriber);
                if (subscriber != null) {
                    assert waitForSslHandshake;
                    completeSubscriber();
                }
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
            tryFailSubscriber(CLOSED_CHANNEL_INACTIVE);
            connection.channelOutboundListener.channelClosed(CLOSED_CHANNEL_INACTIVE);
            connection.nettyChannelPublisher.channelInboundClosed();
        }

        private void doChannelActive(ChannelHandlerContext ctx) {
            if (waitForSslHandshake) {
                // Force a read to get the SSL handshake started, any application data that makes it past the SslHandler
                // will be queued in the NettyChannelPublisher.
                ctx.read();
            } else if (subscriber != null) {
                completeSubscriber();
            }
        }

        private void completeSubscriber() {
            assert subscriber != null;
            SingleSource.Subscriber<? super DefaultNettyConnection<Read, Write>> subscriberCopy = subscriber;
            subscriber = null;
            subscriberCopy.onSuccess(connection);
        }

        private void tryFailSubscriber(Throwable cause) {
            if (subscriber != null) {
                connection.channel().close();
                SingleSource.Subscriber<? super DefaultNettyConnection<Read, Write>> subscriberCopy = subscriber;
                subscriber = null;
                subscriberCopy.onError(cause);
            }
        }
    }
}
