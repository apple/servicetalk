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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.FlushStrategy;
import io.servicetalk.transport.api.FlushStrategyHolder;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.netty.NettyFutureCompletable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.netty.util.ReferenceCountUtil.release;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.internal.ThrowableUtil.unknownStackTrace;
import static io.servicetalk.transport.netty.internal.Flush.composeFlushes;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * Implementation of {@link Connection} backed by a netty {@link Channel}.
 *
 * @param <Read> Type of objects read from this connection.
 * @param <Write> Type of objects written to this connection.
 */
public final class NettyConnection<Read, Write> implements Connection<Read, Write> {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyConnection.class);

    private static final TerminalPredicate PIPELINE_UNSUPPORTED_PREDICATE = new TerminalPredicate();

    private static final WritableListener PLACE_HOLDER_WRITABLE_LISTENER = new NoopWritableListener();
    private static final WritableListener SINGLE_ITEM_WRITABLE_LISTENER = new NoopWritableListener();

    private static final ClosedChannelException CLOSED_CHANNEL_INACTIVE =
            unknownStackTrace(new ClosedChannelException(), NettyConnection.class, "channelInactive(..)");
    private static final ClosedChannelException CLOSED_FAIL_ACTIVE =
            unknownStackTrace(new ClosedChannelException(), NettyConnection.class, "failIfWriteActive(..)");
    private static final AtomicReferenceFieldUpdater<NettyConnection, WritableListener> writableListenerUpdater =
            newUpdater(NettyConnection.class, WritableListener.class, "writableListener");
    private volatile WritableListener writableListener = PLACE_HOLDER_WRITABLE_LISTENER;

    private volatile boolean readInProgress;
    @Nullable private volatile ReadAwareFlushStrategyHolder<Write> readAwareFlushStrategyHolder;

    private final BooleanSupplier readInProgressSupplier = () -> readInProgress;
    private final Channel channel;
    private final ConnectionContext context;
    private final Publisher<Read> read;
    private final TerminalPredicate<Read> terminalMsgPredicate;

    /**
     * Create a new instance.
     * @param channel Netty channel which represents the connection.
     * @param context The ServiceTalk entity which represent the connection.
     * @param read {@link Publisher} which emits all data read from the underlying channel.
     */
    @SuppressWarnings("unchecked")
    public NettyConnection(Channel channel, ConnectionContext context, Publisher<Read> read) {
        this(channel, context, read, PIPELINE_UNSUPPORTED_PREDICATE);
    }

    /**
     * Create a new instance.
     * @param channel Netty channel which represents the connection.
     * @param context The ServiceTalk entity which represent the connection.
     * @param read {@link Publisher} which emits all data read from the underlying channel.
     * @param terminalMsgPredicate {@link TerminalPredicate} to detect end of a <i>response</i>.
     */
    public NettyConnection(Channel channel, ConnectionContext context, Publisher<Read> read,
                           TerminalPredicate<Read> terminalMsgPredicate) {
        this.channel = requireNonNull(channel);
        this.context = requireNonNull(context);
        this.read = requireNonNull(read);
        this.terminalMsgPredicate = requireNonNull(terminalMsgPredicate);
        channel.pipeline().addLast(new ChannelInboundHandler() {
            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) {
                if (ctx.channel().isWritable()) {
                    writableListener.channelWritable();
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
                LOGGER.error("unexpected exception reached the end of the pipeline for channel={}", ctx.channel(), cause);
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                if (!readInProgress) {
                    readInProgress = true;
                }
                release(msg);
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) {
                readInProgress = false;
                ReadAwareFlushStrategyHolder<Write> holder = NettyConnection.this.readAwareFlushStrategyHolder;
                if (holder != null) {
                    holder.readComplete();
                }
            }

            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
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

    private void cleanupOnWriteTerminated() {
        readAwareFlushStrategyHolder = null;
        writableListener = PLACE_HOLDER_WRITABLE_LISTENER;
    }

    @Override
    public Publisher<Read> read() {
        return read;
    }

    @Override
    public TerminalPredicate<Read> getTerminalMsgPredicate() {
        return terminalMsgPredicate;
    }

    @Override
    public Completable write(Publisher<Write> write, FlushStrategy flushStrategy) {
        return write(write, flushStrategy, RequestNSupplier::newDefaultSupplier);
    }

    @Override
    public Completable write(Publisher<Write> write, FlushStrategy flushStrategy, Supplier<RequestNSupplier> requestNSupplierFactory) {
        return write(flushStrategy.apply(requireNonNull(write)), requestNSupplierFactory);
    }

    private Completable write(FlushStrategyHolder<Write> writeWithFlush, Supplier<RequestNSupplier> requestNSupplierFactory) {
        return cleanupStateWhenDone(new Completable() {
            @Override
            protected void handleSubscribe(Subscriber completableSubscriber) {
                WriteStreamSubscriber subscriber = new WriteStreamSubscriber(channel, requestNSupplierFactory.get(), completableSubscriber);
                if (failIfWriteActive(subscriber, completableSubscriber)) {
                    if (writeWithFlush instanceof ReadAwareFlushStrategyHolder) {
                        ReadAwareFlushStrategyHolder<Write> holder = (ReadAwareFlushStrategyHolder<Write>) writeWithFlush;
                        holder.setReadInProgressSupplier(readInProgressSupplier);
                        readAwareFlushStrategyHolder = holder;
                    }
                    composeFlushes(channel, writeWithFlush.getSource(), writeWithFlush.getFlushSignals()).subscribe(subscriber);
                }
            }
        });
    }

    @Override
    public Completable writeAndFlush(Single<Write> write) {
        requireNonNull(write);
        return cleanupStateWhenDone(new Completable() {
            @Override
            protected void handleSubscribe(Subscriber completableSubscriber) {
                WriteSingleSubscriber subscriber = new WriteSingleSubscriber(channel, requireNonNull(completableSubscriber));
                if (failIfWriteActive(subscriber, completableSubscriber)) {
                    write.subscribe(subscriber);
                }
            }
        });
    }

    @Override
    public Completable writeAndFlush(Write write) {
        requireNonNull(write);
        return cleanupStateWhenDone(new NettyFutureCompletable(() -> {
            if (writableListenerUpdater.compareAndSet(NettyConnection.this, PLACE_HOLDER_WRITABLE_LISTENER, SINGLE_ITEM_WRITABLE_LISTENER)) {
                return channel.writeAndFlush(write);
            }
            return channel.newFailedFuture(new IllegalStateException("A write is already active on this connection."));
        }));
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
    public Completable onClose() {
        return context.onClose();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return context.getLocalAddress();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return context.getRemoteAddress();
    }

    @Override
    public BufferAllocator getAllocator() {
        return context.getAllocator();
    }

    @Override
    public SSLSession getSslSession() {
        return context.getSslSession();
    }

    @Override
    public IoExecutor getIoExecutor() {
        return context.getIoExecutor();
    }

    @Override
    public String toString() {
        return channel.toString();
    }

    private Completable cleanupStateWhenDone(Completable completable) {
        // This must happen before we actually trigger the original Subscribers methods so using doBefore* variants.
        return completable.doBeforeFinally(this::cleanupOnWriteTerminated);
    }

    private boolean failIfWriteActive(WritableListener newWritableListener, Completable.Subscriber subscriber) {
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
         * @param closedException the exception which describes the close rational.
         */
        void channelClosed(Throwable closedException);
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
