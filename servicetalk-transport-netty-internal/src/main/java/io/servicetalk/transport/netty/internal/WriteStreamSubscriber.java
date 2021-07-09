/*
 * Copyright © 2018, 2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.transport.api.ConnectionObserver.WriteObserver;
import io.servicetalk.transport.api.RetryableException;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection.ChannelOutboundListener;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.EmptySubscriptions.newEmptySubscription;
import static io.servicetalk.transport.netty.internal.ChannelCloseUtils.assignConnectionError;
import static java.util.Objects.requireNonNull;

/**
 * A {@link PublisherSource.Subscriber} for any {@link Publisher} written via {@link DefaultNettyConnection}.
 *
 * <h2>Flow control</h2>
 *
 * This bridges flow control in netty and a {@link Publisher} using {@link Channel#bytesBeforeUnwritable()}.<p>
 * This checks for requesting more items from {@link Publisher} at the following points:
 * <ul>
 *     <li>When {@link #onSubscribe(Subscription)} is called.</li>
 *     <li>When a write completes successfully.</li>
 *     <li>When {@link #channelWritable()} is invoked.</li>
 * </ul>
 *
 * When there is a need for requesting more items, it tries to determine the capacity in netty's write buffer
 * (determined by {@link Channel#bytesBeforeUnwritable()}).
 * <p>
 *
 * If previous request for more items has been fulfilled i.e. if {@code n} items were requested then
 * {@link #onNext(Object)} has been invoked {@code n} times. Then capacity equals
 * {@link Channel#bytesBeforeUnwritable()}.
 * <p>
 * If previous request for more items has not been fulfilled then the capacity is the difference between the last seen
 * value of {@link Channel#bytesBeforeUnwritable()} and now.<p>
 *
 * If the capacity determined above is positive then invoke {@link WriteDemandEstimator} to determine number of items
 * required to fill that capacity.
 */
final class WriteStreamSubscriber implements PublisherSource.Subscriber<Object>, ChannelOutboundListener, Cancellable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WriteStreamSubscriber.class);
    @SuppressWarnings("rawtypes")
    private static final GenericFutureListener WRITE_BOUNDARY = future -> { };
    private static final byte SOURCE_TERMINATED = 1;
    private static final byte CHANNEL_CLOSED = 1 << 1;
    private static final byte CLOSE_OUTBOUND_ON_SUBSCRIBER_TERMINATION = 1 << 2;
    private static final byte SUBSCRIBER_TERMINATED = 1 << 3;
    private static final Subscription CANCELLED = newEmptySubscription();
    private static final AtomicReferenceFieldUpdater<WriteStreamSubscriber, Subscription> subscriptionUpdater =
            AtomicReferenceFieldUpdater.newUpdater(WriteStreamSubscriber.class, Subscription.class, "subscription");
    private final Subscriber subscriber;
    private final Channel channel;
    /**
     * We rely upon a single event loop for ordering. Even if the channel's EventLoop changes, we need to stick to the
     * original EventLoop or else we may get re-ordering of events.
     */
    private final EventExecutor eventLoop;
    private final WriteDemandEstimator demandEstimator;
    private final AllWritesPromise promise;
    @Nullable
    private volatile Subscription subscription;

    /**
     * This is invoked from the context of on* methods. ReactiveStreams spec says that invocations to Subscriber's on*
     * methods, when done from multiple threads, must use external synchronization (Rule 1.3). This means, this variable
     * does not have to be volatile.
     */
    private boolean enqueueWrites;
    private final CloseHandler closeHandler;
    private final boolean isClient;

    WriteStreamSubscriber(Channel channel, WriteDemandEstimator demandEstimator, Subscriber subscriber,
                          CloseHandler closeHandler, WriteObserver observer,
                          UnaryOperator<Throwable> enrichProtocolError, boolean isClient) {
        this.eventLoop = requireNonNull(channel.eventLoop());
        this.subscriber = subscriber;
        this.channel = channel;
        this.demandEstimator = demandEstimator;
        promise = new AllWritesPromise(channel, observer, enrichProtocolError);
        this.closeHandler = closeHandler;
        this.isClient = isClient;
    }

    @Override
    public void onSubscribe(Subscription s) {
        final Subscription concurrentSubscription = ConcurrentSubscription.wrap(s);
        if (!subscriptionUpdater.compareAndSet(this, null, concurrentSubscription)) {
            // Either onSubscribe was called twice or Subscription is cancelled, in both cases, we cancel the new
            // Subscription.
            s.cancel();
            return;
        }
        subscriber.onSubscribe(concurrentSubscription);
        if (eventLoop.inEventLoop()) {
            initialRequestN(concurrentSubscription);
        } else {
            eventLoop.execute(() -> initialRequestN(concurrentSubscription));
        }
    }

    @Override
    public void onNext(Object o) {
        if (!enqueueWrites && !eventLoop.inEventLoop()) {
            /*
             * If any onNext comes from out of the eventloop, we should enqueue all subsequent writes and terminal
             * notifications on the eventloop.
             * Otherwise, the order of writes will not be preserved in the following case:
             *
             * Write1 (Thread1) -> Write2 (Eventloop)
             *
             * If Thread1 != this channels Eventloop then Write2 may happen before Write1 as a write from the eventloop
             * will skip the task queue and directly send the write on the pipeline.
             */
            enqueueWrites = true;
        }
        if (enqueueWrites) {
            eventLoop.execute(() -> doWrite(o));
        } else {
            doWrite(o);
        }
    }

    void doWrite(Object msg) {
        // Ignore onNext if the channel is already closed.
        if (promise.isWritable()) {
            long capacityBefore = channel.bytesBeforeUnwritable();
            promise.writeNext(msg);
            long capacityAfter = channel.bytesBeforeUnwritable();
            demandEstimator.onItemWrite(msg, capacityBefore, capacityAfter);
            requestMoreIfRequired(subscription, capacityAfter);
        }
    }

    @Override
    public void onError(Throwable cause) {
        requireNonNull(cause);
        if (enqueueWrites || !eventLoop.inEventLoop()) {
            eventLoop.execute(() -> promise.sourceTerminated(cause));
        } else {
            promise.sourceTerminated(cause);
        }
    }

    @Override
    public void onComplete() {
        if (enqueueWrites || !eventLoop.inEventLoop()) {
            eventLoop.execute(() -> promise.sourceTerminated(null));
        } else {
            promise.sourceTerminated(null);
        }
    }

    @Override
    public void channelWritable() {
        assert eventLoop.inEventLoop();
        requestMoreIfRequired(subscription, -1L);
    }

    @Override
    public void channelOutboundClosed() {
        assert eventLoop.inEventLoop();
        promise.sourceTerminated(null);
    }

    @Override
    public void channelClosed(Throwable closedException) {
        Subscription oldVal = subscriptionUpdater.getAndSet(this, CANCELLED);
        if (eventLoop.inEventLoop()) {
            close0(oldVal, closedException);
        } else {
            eventLoop.execute(() -> close0(oldVal, closedException));
        }
    }

    private void close0(@Nullable Subscription oldVal, Throwable closedException) {
        assert eventLoop.inEventLoop();
        if (oldVal == null) {
            // If there was no subscriber when the channel closed, we need to call onSubscribe before we terminate.
            subscriber.onSubscribe(IGNORE_CANCEL);
        } else {
            oldVal.cancel();
        }
        promise.close(closedException);
    }

    @Override
    public void cancel() {
        // In order to prevent concurrent access to the subscription, we use the EventLoop. The alternative would be
        // some additional protection around calling subscription.request and subscription.cancel, but since this method
        // is expected to happen with low frequency and subscription.request is expected to high frequency we avoid
        // additional concurrency control on the hot path.
        // It is possible we may be cancelled now while we have a Runnable pending which will do request(n) but
        // rule 3.7 [1] says this is OK.
        // [1] https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.1/README.md#3.6
        Subscription oldVal = subscriptionUpdater.getAndSet(this, CANCELLED);
        if (oldVal == null || oldVal == CANCELLED) {
            return;
        }
        if (eventLoop.inEventLoop()) {
            oldVal.cancel();
        } else {
            eventLoop.execute(oldVal::cancel);
        }
    }

    private void initialRequestN(Subscription subscription) {
        if (isClient) {
            if (promise.isWritable()) {
                subscription.request(1L);   // Request meta-data only
            }
        } else {
            requestMoreIfRequired(subscription, -1L);
        }
    }

    private void requestMoreIfRequired(@Nullable Subscription subscription, long bytesBeforeUnwritable) {
        // subscription could be null if channelWritable is invoked before onSubscribe.
        // If promise is not writable, then we will not be able to write anyways, so do not request more.
        if (subscription == null || subscription == CANCELLED || !promise.isWritable()) {
            return;
        }

        long n = demandEstimator.estimateRequestN(bytesBeforeUnwritable >= 0 ? bytesBeforeUnwritable :
                channel.bytesBeforeUnwritable());
        if (n > 0) {
            subscription.request(n);
        }
    }

    /**
     * A special {@link DefaultChannelPromise} for write operations. It is assumed that all methods on this class are
     * only called from the eventloop.
     */
    private final class AllWritesPromise extends DefaultChannelPromise {
        private int activeWrites;
        private boolean written;
        private byte state;
        @Nullable
        private Throwable failureCause;
        /**
         * This deque contains all added listeners within {@link #WRITE_BOUNDARY write boundaries}.
         * <pre>
         *     {@link #WRITE_BOUNDARY}, listener1, listener2, {@link #WRITE_BOUNDARY}, listener3 ...
         * </pre>
         * We assume that no listener for a write is added after that write is completed (a.k.a late listeners).
         * Most of the messages have 3 listeners (headers, one payload body chunk, trailers). Messages with large
         * streaming payload body may have more listeners. However, the MIN_INITIAL_CAPACITY of ArrayDeque is 8.
         */
        private final Deque<GenericFutureListener<?>> listenersOnWriteBoundaries = new ArrayDeque<>(8);
        private final WriteObserver observer;
        private final UnaryOperator<Throwable> enrichProtocolError;

        AllWritesPromise(final Channel channel, final WriteObserver observer,
                         final UnaryOperator<Throwable> enrichProtocolError) {
            super(channel);
            this.observer = observer;
            this.enrichProtocolError = enrichProtocolError;
        }

        @Override
        public ChannelPromise addListener(final GenericFutureListener<? extends Future<? super Void>> listener) {
            assert channel.eventLoop().inEventLoop();
            if (hasFlag(SUBSCRIBER_TERMINATED)) {
                return this;
            }
            listenersOnWriteBoundaries.addLast(listener);
            return this;
        }

        @SafeVarargs
        @Override
        public final ChannelPromise addListeners(
                final GenericFutureListener<? extends Future<? super Void>>... listeners) {
            assert channel.eventLoop().inEventLoop();
            if (hasFlag(SUBSCRIBER_TERMINATED)) {
                return this;
            }
            for (GenericFutureListener<? extends Future<? super Void>> listener : listeners) {
                listenersOnWriteBoundaries.addLast(listener);
            }
            return this;
        }

        @Override
        public ChannelPromise removeListener(final GenericFutureListener<? extends Future<? super Void>> listener) {
            assert channel.eventLoop().inEventLoop();
            listenersOnWriteBoundaries.removeFirstOccurrence(listener);
            return this;
        }

        @SafeVarargs
        @Override
        public final ChannelPromise removeListeners(
                final GenericFutureListener<? extends Future<? super Void>>... listeners) {
            assert channel.eventLoop().inEventLoop();
            for (GenericFutureListener<? extends Future<? super Void>> listener : listeners) {
                listenersOnWriteBoundaries.removeFirstOccurrence(listener);
            }
            return this;
        }

        boolean isWritable() {
            assert channel.eventLoop().inEventLoop();
            return !hasAnyFlags(CHANNEL_CLOSED, SUBSCRIBER_TERMINATED, SOURCE_TERMINATED);
        }

        void writeNext(Object msg) {
            assert eventLoop.inEventLoop();
            activeWrites++;
            listenersOnWriteBoundaries.addLast(WRITE_BOUNDARY);
            channel.write(msg, this);
            if (!written) {
                written = true;
            }
        }

        void sourceTerminated(@Nullable Throwable cause) {
            assert eventLoop.inEventLoop();
            if (hasAnyFlags(SUBSCRIBER_TERMINATED, SOURCE_TERMINATED)) {
                // We have terminated prematurely perhaps due to write failure.
                return;
            }
            this.failureCause = cause;
            setFlag(SOURCE_TERMINATED);
            if (activeWrites == 0) {
                try {
                    setFlag(SUBSCRIBER_TERMINATED);
                    terminateSubscriber(cause);
                } catch (Throwable t) {
                    tryFailureOrLog(t);
                    return;
                }
                // We are here because the Publisher that was being written terminated, not the actual channel writes.
                // Hence, we set the promise result to success to notify the listeners. If the writes fail before the
                // source terminates, we would have already terminated the Subscriber.
                //
                // If we use trySuccess() here then it will reenter sourceTerminated() where it will see the state
                // as SUBSCRIBER_TERMINATED and do nothing. trySuccess(null) does not reenter.
                super.trySuccess(null);
            }
        }

        void close(Throwable cause) {
            assert eventLoop.inEventLoop();
            if (hasFlag(CHANNEL_CLOSED)) {
                return;
            }
            if (hasFlag(SUBSCRIBER_TERMINATED)) {
                setFlag(CHANNEL_CLOSED);
                // We have already terminated the subscriber (all writes have finished (one has failed)) then we
                // just close the channel now.
                closeHandler.closeChannelOutbound(channel);
            } else if (activeWrites > 0) {
                // Writes are pending, we will close the channel once writes are done.
                setFlag(CLOSE_OUTBOUND_ON_SUBSCRIBER_TERMINATION);
            } else {
                setFlag(CHANNEL_CLOSED);
                // subscriber has not terminated, no writes are pending and channel has closed so terminate the
                // subscriber with a failure.
                tryFailure(cause);
            }
        }

        @Override
        public boolean trySuccess(final Void result) {
            return setSuccess0();
        }

        @Override
        public boolean tryFailure(final Throwable cause) {
            return setFailure0(cause);
        }

        @Override
        public ChannelPromise setSuccess(final Void result) {
            setSuccess0();
            return this;
        }

        @Override
        public ChannelPromise setFailure(final Throwable cause) {
            setFailure0(cause);
            return this;
        }

        private boolean setSuccess0() {
            assert eventLoop.inEventLoop();
            if (hasFlag(SUBSCRIBER_TERMINATED)) {
                return nettySharedPromiseTryStatus();
            }
            observer.itemWritten();
            if (--activeWrites == 0 && hasFlag(SOURCE_TERMINATED)) {
                setFlag(SUBSCRIBER_TERMINATED);
                try {
                    terminateSubscriber(failureCause);
                } catch (Throwable t) {
                    tryFailureOrLog(t);
                    // Always return true since we have set the state to SUBSCRIBER_TERMINATED
                    return true;
                }
                return super.trySuccess(null);
            } else {
                notifyListenersTillNextWrite(failureCause);
            }
            return nettySharedPromiseTryStatus();
        }

        private boolean setFailure0(Throwable cause) {
            assert eventLoop.inEventLoop();
            // Application today assume ordered and reliable behavior from the transport such that if a single write
            // fails then the remaining writes will also fail. So, for any write failure we close the channel and
            // ignore any further results. For non-reliable protocols, when we support them, we will modify this
            // behavior as appropriate.
            if (hasFlag(SUBSCRIBER_TERMINATED)) {
                return nettySharedPromiseTryStatus();
            }
            setFlag(SUBSCRIBER_TERMINATED);
            Subscription oldVal = subscriptionUpdater.getAndSet(WriteStreamSubscriber.this, CANCELLED);
            if (oldVal != null && !hasFlag(SOURCE_TERMINATED)) {
                oldVal.cancel();
            }
            terminateSubscriber(cause);
            tryFailureOrLog(cause);
            // Always return true since we have set the state to SUBSCRIBER_TERMINATED
            return true;
        }

        private boolean nettySharedPromiseTryStatus() {
            // We take liberties with Netty's promise API because this promise is shared across multiple operations.
            // We always return true which may violate the API as follows:
            // if (promise.tryFailure())
            //   if (promise.tryFailure())
            //      unexpected!
            // However in practice this pattern isn't used and it is more common to do expensive recovery
            // (e.g. stack trace logging) if any of the try* operations return false.
            return true;
        }

        private void terminateSubscriber(@Nullable Throwable cause) {
            if (cause == null) {
                try {
                    observer.writeComplete();
                    subscriber.onComplete();
                } catch (Throwable t) {
                    tryFailureOrLog(t);
                }
                if (hasFlag(CLOSE_OUTBOUND_ON_SUBSCRIBER_TERMINATION)) {
                    closeHandler.closeChannelOutbound(channel);
                }
            } else {
                Throwable enrichedCause = enrichProtocolError.apply(cause);
                assignConnectionError(channel, enrichedCause);
                enrichedCause = !written ? new AbortedFirstWriteException(enrichedCause) : enrichedCause;
                try {
                    observer.writeFailed(enrichedCause);
                    subscriber.onError(enrichedCause);
                } catch (Throwable t) {
                    t.addSuppressed(enrichedCause);
                    tryFailureOrLog(t);
                }
                if (!hasFlag(CHANNEL_CLOSED)) {
                    // Close channel on error, connection error is already assigned to the channel's attribute
                    channel.close();
                }
            }
            // Notify listeners after the subscriber is terminated. Otherwise, WriteStreamSubscriber#channelClosed may
            // be invoked that leads to the Subscription cancellation.
            notifyAllListeners(cause);
        }

        private void notifyAllListeners(@Nullable Throwable cause) {
            final ChannelFuture future = cause == null ? channel.newSucceededFuture() : channel.newFailedFuture(cause);
            GenericFutureListener<?> mayBeListener;
            while ((mayBeListener = listenersOnWriteBoundaries.pollFirst()) != null) {
                if (mayBeListener != WRITE_BOUNDARY) {
                    notifyListener(eventLoop, future, mayBeListener);
                }
            }
        }

        private void notifyListenersTillNextWrite(@Nullable Throwable cause) {
            Object shdBeWriteBoundary = listenersOnWriteBoundaries.pollFirst();
            assert shdBeWriteBoundary == WRITE_BOUNDARY;

            final ChannelFuture future = cause == null ? channel.newSucceededFuture() : channel.newFailedFuture(cause);
            while (!listenersOnWriteBoundaries.isEmpty() && listenersOnWriteBoundaries.peekFirst() != WRITE_BOUNDARY) {
                notifyListener(eventLoop, future, listenersOnWriteBoundaries.pollFirst());
            }
        }

        private void tryFailureOrLog(final Throwable cause) {
            if (!super.tryFailure(cause)) {
                LOGGER.error("Failed to set failure on the write promise {}.", this, cause);
            }
        }

        private boolean hasFlag(final byte flag) {
            return (state & flag) == flag;
        }

        private boolean hasAnyFlags(final byte flag1, final byte flag2) {
            return (state & (flag1 | flag2)) > 0;
        }

        private boolean hasAnyFlags(final byte flag1, final byte flag2, final byte flag3) {
            return (state & (flag1 | flag2 | flag3)) > 0;
        }

        private void setFlag(final byte flag) {
            state |= flag;
        }
    }

    static final class AbortedFirstWriteException extends IOException implements RetryableException {
        private static final long serialVersionUID = -5626706348233302247L;

        AbortedFirstWriteException(final Throwable cause) {
            super(cause);
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }
}
