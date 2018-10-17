/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.Completable.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent;

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.ChannelOutputShutdownEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.netty.util.ReferenceCountUtil.release;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.internal.ThrowableUtil.unknownStackTrace;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.Flush.composeFlushes;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * Implementation of {@link NettyConnection} backed by a netty {@link Channel}.
 *
 * @param <Read> Type of objects read from this connection.
 * @param <Write> Type of objects written to this connection.
 */
public class DefaultNettyConnection<Read, Write> implements NettyConnection<Read, Write> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultNettyConnection.class);

    private static final TerminalPredicate PIPELINE_UNSUPPORTED_PREDICATE = new TerminalPredicate();

    private static final WritableListener PLACE_HOLDER_WRITABLE_LISTENER = new NoopWritableListener();
    private static final WritableListener SINGLE_ITEM_WRITABLE_LISTENER = new NoopWritableListener();

    private static final ClosedChannelException CLOSED_CHANNEL_INACTIVE =
            unknownStackTrace(new ClosedChannelException(), DefaultNettyConnection.class, "channelInactive(..)");
    private static final ClosedChannelException CLOSED_FAIL_ACTIVE =
            unknownStackTrace(new ClosedChannelException(), DefaultNettyConnection.class, "failIfWriteActive(..)");
    private static final AtomicReferenceFieldUpdater<DefaultNettyConnection, WritableListener> writableListenerUpdater =
            newUpdater(DefaultNettyConnection.class, WritableListener.class, "writableListener");
    private static final AtomicReferenceFieldUpdater<DefaultNettyConnection, FlushStrategy> flushStrategyUpdater =
            newUpdater(DefaultNettyConnection.class, FlushStrategy.class, "flushStrategy");
    private volatile WritableListener writableListener = PLACE_HOLDER_WRITABLE_LISTENER;

    private final Channel channel;
    private final ConnectionContext context;
    private final Publisher<Read> read;
    private final TerminalPredicate<Read> terminalMsgPredicate;
    private final CloseHandler closeHandler;

    @Nullable
    private final CompletableProcessor onClosing;
    private volatile FlushStrategy flushStrategy;

    /**
     * Potentially contains more information when a protocol or channel level close event was observed.
     * <p>
     * Always accessed from the event loop, doesn't require synchronization.
     */
    @Nullable
    private CloseEvent closeReason;

    /**
     * Create a new instance.
     *
     * @param channel Netty channel which represents the connection.
     * @param context The ServiceTalk entity which represents the connection.
     * @param read {@link Publisher} which emits all data read from the underlying channel.
     * @param flushStrategy {@link FlushStrategy} for this connection.
     */
    @SuppressWarnings("unchecked")
    public DefaultNettyConnection(Channel channel, ConnectionContext context, Publisher<Read> read,
                                  FlushStrategy flushStrategy) {
        this(channel, context, read, PIPELINE_UNSUPPORTED_PREDICATE, UNSUPPORTED_PROTOCOL_CLOSE_HANDLER, flushStrategy);
    }

    /**
     * Create a new instance.
     *
     * @param channel Netty channel which represents the connection.
     * @param context The ServiceTalk entity which represents the connection.
     * @param read {@link Publisher} which emits all data read from the underlying channel.
     * @param terminalMsgPredicate {@link TerminalPredicate} to detect end of a <i>response</i>.
     * @param closeHandler handles connection closure and half-closure.
     * @param flushStrategy {@link FlushStrategy} for this connection.
     */
    public DefaultNettyConnection(Channel channel, ConnectionContext context, Publisher<Read> read,
                                  TerminalPredicate<Read> terminalMsgPredicate, CloseHandler closeHandler,
                                  FlushStrategy flushStrategy) {
        this.channel = requireNonNull(channel);
        this.context = requireNonNull(context);
        this.read = read.onErrorResume(this::enrichErrorPublisher);
        this.terminalMsgPredicate = requireNonNull(terminalMsgPredicate);
        this.closeHandler = requireNonNull(closeHandler);
        this.flushStrategy = requireNonNull(flushStrategy);
        if (closeHandler != UNSUPPORTED_PROTOCOL_CLOSE_HANDLER) {
            onClosing = new CompletableProcessor();
            closeHandler.registerEventHandler(channel, evt -> { // Called from EventLoop only!
                if (closeReason == null) {
                    closeReason = evt;
                    LOGGER.debug("{} Emitted CloseEvent: {}", channel, evt);
                    onClosing.onComplete();
                }
            });
            // Users may depend on onClosing to be notified for all kinds of closures and not just graceful close.
            // So, we should make sure that onClosing at least terminates with the channel.
            // Since, onClose is guaranteed to be notified for any kind of closures, we cascade it to onClosing.
            // An alternative would be to intercept channelInactive() in the pipeline but adding a pipeline handler
            // in the pipeline may race with closure as we have already created the channel. If that happens, we may
            // miss channelInactive event.
            context.onClose().subscribe(onClosing);
        } else {
            onClosing = null;
        }
        channel.pipeline().addLast(new ChannelInboundHandler() {
            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) {
                if (ctx.channel().isWritable()) {
                    writableListener.channelWritable();
                } else if (DefaultNettyConnection.this.flushStrategy.flushOnUnwritable()) {
                    channel.flush();
                }
            }

            @Override
            public void handlerAdded(ChannelHandlerContext ctx) {
            }

            @Override
            public void handlerRemoved(ChannelHandlerContext ctx) {
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                LOGGER.error("unexpected exception reached the end of the pipeline for channel={}", ctx.channel(),
                        cause);
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                release(msg);
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) {
            }

            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                // Inbound shutdown event is handled in AbstractChannelReadHandler
                if (evt == ChannelOutputShutdownEvent.INSTANCE) {
                    closeHandler.channelClosedOutbound(ctx);
                    writableListener.channelClosedOutbound();
                }
                release(evt);
            }

            @Override
            public void channelRegistered(ChannelHandlerContext ctx) {
            }

            @Override
            public void channelUnregistered(ChannelHandlerContext ctx) {
            }

            @Override
            public void channelActive(ChannelHandlerContext ctx) {
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) {
                writableListener.channelClosed(CLOSED_CHANNEL_INACTIVE);
            }
        });
    }

    private Publisher<Read> enrichErrorPublisher(final Throwable t) {
        return Publisher.error(enrichError(t));
    }

    private Completable enrichErrorCompletable(final Throwable t) {
        return Completable.error(enrichError(t));
    }

    private Throwable enrichError(final Throwable t) {
        return closeReason != null ? closeReason.wrapError(t) : t;
    }

    private void cleanupOnWriteTerminated() {
        writableListener = PLACE_HOLDER_WRITABLE_LISTENER;
    }

    @Override
    public Publisher<Read> read() {
        return read;
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
        return cleanupStateWhenDone(new Completable() {
            @Override
            protected void handleSubscribe(Subscriber completableSubscriber) {
                WriteStreamSubscriber subscriber = new WriteStreamSubscriber(channel, requestNSupplierFactory.get(),
                        completableSubscriber, closeHandler);
                if (failIfWriteActive(subscriber, completableSubscriber)) {
                    composeFlushes(channel, write, flushStrategy).subscribe(subscriber);
                }
            }
        }).onErrorResume(this::enrichErrorCompletable);
    }

    @Override
    public Completable writeAndFlush(Single<Write> write) {
        requireNonNull(write);
        return cleanupStateWhenDone(new Completable() {
            @Override
            protected void handleSubscribe(Subscriber completableSubscriber) {
                WriteSingleSubscriber subscriber = new WriteSingleSubscriber(
                        channel, requireNonNull(completableSubscriber), closeHandler);
                if (failIfWriteActive(subscriber, completableSubscriber)) {
                    write.subscribe(subscriber);
                }
            }
        }).onErrorResume(this::enrichErrorCompletable);
    }

    @Override
    public Completable writeAndFlush(Write write) {
        requireNonNull(write);
        return cleanupStateWhenDone(new NettyFutureCompletable(() -> {
            if (writableListenerUpdater.compareAndSet(DefaultNettyConnection.this, PLACE_HOLDER_WRITABLE_LISTENER,
                    SINGLE_ITEM_WRITABLE_LISTENER)) {
                return channel.writeAndFlush(write);
            }
            return channel.newFailedFuture(new IllegalStateException("A write is already active on this connection."));
        })).onErrorResume(this::enrichErrorCompletable);
    }

    /**
     * This connection does not allow concurrent writes and so this method can determine if there is a writing pending.
     *
     * @return {@code true} if a write is already active.
     */
    boolean isWriteActive() {
        return writableListener != PLACE_HOLDER_WRITABLE_LISTENER;
    }

    @Override
    public Completable closeAsync() {
        return context.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return new Completable() {
            @Override
            protected void handleSubscribe(final Subscriber subscriber) {
                onClose().subscribe(subscriber);
                EventLoop eventLoop = channel.eventLoop();
                if (eventLoop.inEventLoop()) {
                    invokeUserCloseHandler();
                } else {
                    eventLoop.execute(DefaultNettyConnection.this::invokeUserCloseHandler);
                }
            }
        };
    }

    @Override
    public Completable onClose() {
        return context.onClose();
    }

    @Override
    public SocketAddress localAddress() {
        return context.localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return context.remoteAddress();
    }

    @Override
    public SSLSession sslSession() {
        return context.sslSession();
    }

    @Override
    public ExecutionContext executionContext() {
        return context.executionContext();
    }

    private void invokeUserCloseHandler() {
        closeHandler.userClosing(channel);
    }

    @Override
    public Completable onClosing() {
        return onClosing == null ? onClose() : onClosing.publishOn(executionContext().executor());
    }

    @Override
    public String toString() {
        return channel.toString();
    }

    private Completable cleanupStateWhenDone(Completable completable) {
        // This must happen before we actually trigger the original Subscribers methods so using doBefore* variants.
        return completable.doBeforeFinally(this::cleanupOnWriteTerminated);
    }

    private boolean failIfWriteActive(WritableListener newWritableListener, Subscriber subscriber) {
        if (writableListenerUpdater.compareAndSet(this, PLACE_HOLDER_WRITABLE_LISTENER, newWritableListener)) {
            // It is possible that we have set the writeSubscriber, then the channel becomes inactive, and we will
            // never notify the write writeSubscriber of the inactive event. So if the channel is inactive we notify
            // the writeSubscriber.
            if (!channel.isActive()) {
                newWritableListener.channelClosed(CLOSED_FAIL_ACTIVE);
                return false;
            }
            return true;
        }
        subscriber.onSubscribe(IGNORE_CANCEL);
        subscriber.onError(new IllegalStateException("A write is already active on this connection."));
        return false;
    }

    @Override
    public Cancellable updateFlushStrategy(final UnaryOperator<FlushStrategy> strategyProvider) {
        FlushStrategy old = flushStrategyUpdater.getAndUpdate(this, strategyProvider);
        return () -> updateFlushStrategy(__ -> old);
    }

    interface WritableListener {
        /**
         * Notification that the writability of the channel has changed.
         * <p>
         * Always called from the event loop thread.
         */
        void channelWritable();

        /**
         * Notification that the channel has been closed.
         * <p>
         * This may not always be called from the event loop. For example if the channel is closed when a new write
         * happens then this method will be called from the writer thread.
         *
         * @param closedException the exception which describes the close rational.
         */
        void channelClosed(Throwable closedException);

        /**
         * Notification that the channel outbound path has been closed.
         * <p>
         * This may indicate a write failed and was implicitly closed by the {@link AbstractChannel} or a {@link
         * CloseHandler} performing a graceful or forced close. This methods will always be called from the event loop.
         */
        default void channelClosedOutbound() {
            // Do nothing
        }
    }

    private static final class NoopWritableListener implements WritableListener {
        @Override
        public void channelWritable() {
        }

        @Override
        public void channelClosed(Throwable closedException) {
        }
    }
}
