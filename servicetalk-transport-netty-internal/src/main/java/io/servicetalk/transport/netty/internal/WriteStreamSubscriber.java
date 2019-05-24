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
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.EmptySubscription;
import io.servicetalk.concurrent.internal.FlowControlUtil;
import io.servicetalk.transport.netty.internal.NettyConnection.RequestNSupplier;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.util.concurrent.EventExecutor;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

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
 *  If previous request for more items has been fulfilled i.e. if {@code n} items were requested then
 * {@link #onNext(Object)} has been invoked {@code n} times. Then capacity equals
 * {@link Channel#bytesBeforeUnwritable()}.
 * <p>
 * If previous request for more items has not been fulfilled then the capacity is the difference between the last seen
 * value of {@link Channel#bytesBeforeUnwritable()} and now.<p>
 *
 * If the capacity determined above is positive then invoke {@link RequestNSupplier} to determine number of items
 * required to fill that capacity.
 */
final class WriteStreamSubscriber implements PublisherSource.Subscriber<Object>,
                                             DefaultNettyConnection.WritableListener, Cancellable {
    private static final byte SOURCE_TERMINATED = 1;
    private static final byte CHANNEL_CLOSED = 2;
    private static final byte CHANNEL_CLOSED_OUTBOUND = 4;
    private static final byte SUBSCRIBER_TERMINATED = 8;
    private static final Subscription CANCELLED = new EmptySubscription();
    private static final AtomicLongFieldUpdater<WriteStreamSubscriber> requestedUpdater =
            AtomicLongFieldUpdater.newUpdater(WriteStreamSubscriber.class, "requested");
    private static final AtomicReferenceFieldUpdater<WriteStreamSubscriber, Subscription> subscriptionUpdater =
            AtomicReferenceFieldUpdater.newUpdater(WriteStreamSubscriber.class, Subscription.class, "subscription");
    private final Subscriber subscriber;
    private final Channel channel;
    /**
     * We rely upon a single event loop for ordering. Even if the channel's EventLoop changes, we need to stick to the
     * original EventLoop or else we may get re-ordering of events.
     */
    private final EventExecutor eventLoop;
    private final RequestNSupplier requestNSupplier;
    private final AllWritesPromise promise;
    @SuppressWarnings("unused")
    @Nullable
    private volatile Subscription subscription;
    @SuppressWarnings("unused")
    private volatile long requested;

    /**
     * This is invoked from the context of on* methods. ReactiveStreams spec says that invocations to Subscriber's on*
     * methods, when done from multiple threads, must use external synchronization (Rule 1.3). This means, this variable
     * does not have to be volatile.
     */
    private boolean enqueueWrites;
    private final CloseHandler closeHandler;

    WriteStreamSubscriber(Channel channel, RequestNSupplier requestNSupplier, Subscriber subscriber,
                          CloseHandler closeHandler) {
        this.eventLoop = requireNonNull(channel.eventLoop());
        this.subscriber = subscriber;
        this.channel = channel;
        this.requestNSupplier = requestNSupplier;
        promise = new AllWritesPromise(channel);
        this.closeHandler = closeHandler;
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
            requestMoreIfRequired(concurrentSubscription);
        } else {
            eventLoop.execute(() -> requestMoreIfRequired(concurrentSubscription));
        }
    }

    @Override
    public void onNext(Object o) {
        requestedUpdater.decrementAndGet(this);
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
            eventLoop.execute(() -> {
                doWrite(o);
                requestMoreIfRequired(subscription);
            });
        } else {
            doWrite(o);
            requestMoreIfRequired(subscription);
        }
    }

    void doWrite(Object msg) {
        long capacityBefore = channel.bytesBeforeUnwritable();
        promise.writeNext(msg);
        long capacityAfter = channel.bytesBeforeUnwritable();
        requestNSupplier.onItemWrite(msg, capacityBefore, capacityAfter);
    }

    @Override
    public void onError(Throwable cause) {
        if (enqueueWrites || !eventLoop.inEventLoop()) {
            eventLoop.execute(() -> promise.sourceTerminated(requireNonNull(cause)));
        } else {
            promise.sourceTerminated(requireNonNull(cause));
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
        requestMoreIfRequired(subscription);
    }

    @Override
    public void channelClosedOutbound() {
        assert eventLoop.inEventLoop();
        promise.channelClosedOutbound();
    }

    @Override
    public void channelClosed(Throwable closedException) {
        Subscription oldVal = subscriptionUpdater.getAndSet(this, CANCELLED);
        if (eventLoop.inEventLoop()) {
            channelClosed0(oldVal, closedException);
        } else {
            eventLoop.execute(() -> channelClosed0(oldVal, closedException));
        }
    }

    private void channelClosed0(@Nullable Subscription oldVal, Throwable closedException) {
        assert eventLoop.inEventLoop();
        if (oldVal == null) {
            // If there was no subscriber when the channel closed, we need to call onSubscribe before we terminate.
            subscriber.onSubscribe(IGNORE_CANCEL);
        } else {
            oldVal.cancel();
        }
        promise.channelClosed(closedException);
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

    private void requestMoreIfRequired(@Nullable Subscription subscription) {
        // subscription could be null if channelWritable is invoked before onSubscribe.
        if (subscription == null || subscription == CANCELLED) {
            return;
        }

        long n = requestNSupplier.requestNFor(channel.bytesBeforeUnwritable());
        if (n > 0) {
            requestedUpdater.accumulateAndGet(this, n, FlowControlUtil::addWithOverflowProtection);
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

        AllWritesPromise(final Channel channel) {
            super(channel);
        }

        void writeNext(Object msg) {
            assert eventLoop.inEventLoop();
            if (!written) {
                written = true;
            }
            activeWrites++;
            channel.write(msg, this);
        }

        void sourceTerminated(@Nullable Throwable cause) {
            assert eventLoop.inEventLoop();
            if (hasFlag(SUBSCRIBER_TERMINATED)) {
                // We have terminated prematurely perhaps due to write failure.
                return;
            }
            this.failureCause = cause;
            setFlag(SOURCE_TERMINATED);
            if (activeWrites == 0) {
                try {
                    terminateSubscriber(cause);
                } catch (Throwable t) {
                    super.tryFailure(t);
                    return;
                }
                // We are here because the Publisher that was being written terminated, not the actual channel writes.
                // Hence, we set the promise result to success to notify the listeners. If the writes fail before the
                // source terminates, we would have already terminated the Subscriber.
                super.trySuccess();
            }
        }

        void channelClosed(Throwable cause) {
            assert eventLoop.inEventLoop();
            if (hasFlag(CHANNEL_CLOSED) || hasFlag(SUBSCRIBER_TERMINATED)) {
                return;
            }
            setFlag(CHANNEL_CLOSED);
            tryFailure(written ? new AbortedFirstWrite(cause) : cause);
        }

        void channelClosedOutbound() {
            assert eventLoop.inEventLoop();
            if (hasFlag(CHANNEL_CLOSED)) {
                return;
            }
            if (hasFlag(SUBSCRIBER_TERMINATED)) {
                // We have already terminated the subscriber (all writes have finished (one has failed)) then we
                // just close the channel now.
                closeHandler.closeChannelOutbound(channel);
                return;
            }
            // Writes are pending, we will close the channel once writes are done.
            setFlag(CHANNEL_CLOSED_OUTBOUND);
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
                return false;
            }
            if (--activeWrites == 0 && hasFlag(SOURCE_TERMINATED) && !hasFlag(SUBSCRIBER_TERMINATED)) {
                setFlag(SUBSCRIBER_TERMINATED);
                try {
                    terminateSubscriber(failureCause);
                } catch (Throwable t) {
                    super.tryFailure(t);
                    return false;
                }
                super.trySuccess();
            }
            return true;
        }

        private boolean setFailure0(Throwable cause) {
            assert eventLoop.inEventLoop();
            /*
             * It is assumed the underlying transport is ordered and reliable such that if a single write fails then the
             * remaining writes will also fail. So, for any write failure we close the channel and ignore any further
             * results.
             */
            if (hasFlag(SUBSCRIBER_TERMINATED)) {
                return false;
            }
            setFlag(SUBSCRIBER_TERMINATED);
            Subscription oldVal = subscriptionUpdater.getAndSet(WriteStreamSubscriber.this, CANCELLED);
            if (oldVal != null && oldVal != CANCELLED) {
                oldVal.cancel();
            }
            try {
                terminateSubscriber(cause);
            } catch (Throwable t) {
                super.tryFailure(t);
                return false;
            }
            super.tryFailure(cause);
            return true;
        }

        private void terminateSubscriber(@Nullable final Throwable cause) {
            if (hasFlag(CHANNEL_CLOSED_OUTBOUND) || cause != null) {
                closeHandler.closeChannelOutbound(channel);
            }
            if (cause == null) {
                subscriber.onComplete();
            } else {
                subscriber.onError(cause);
                if (!hasFlag(CHANNEL_CLOSED)) {
                    // Close channel on error.
                    channel.close();
                }
            }
        }

        private boolean hasFlag(final byte flag) {
            return (state & flag) == flag;
        }

        private void setFlag(final byte flag) {
            state |= flag;
        }
    }

    static final class AbortedFirstWrite extends Exception {
        AbortedFirstWrite(final Throwable cause) {
            super(null, cause, false, false);
        }
    }
}
