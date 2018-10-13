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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.EmptySubscription;
import io.servicetalk.concurrent.internal.FlowControlUtil;
import io.servicetalk.transport.netty.internal.NettyConnection.RequestNSupplier;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A {@link org.reactivestreams.Subscriber} for any {@link Publisher} written via {@link DefaultNettyConnection}.
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
 * When there is a need for requesting more items, it tries to determine the capacity in netty's write buffer (determined by {@link Channel#bytesBeforeUnwritable()}).<p>
 *
 * If previous request for more items has been fulfilled i.e. if {@code n} items were requested then {@link #onNext(Object)} has been invoked {@code n} times. Then capacity equals {@link Channel#bytesBeforeUnwritable()}. <p>
 * If previous request for more items has not been fulfilled then the capacity is the difference between the last seen value of {@link Channel#bytesBeforeUnwritable()} and now.<p>
 *
 * If the capacity determined above is positive then invoke {@link RequestNSupplier} to determine number of items required to fill that capacity.
 */
final class WriteStreamSubscriber implements org.reactivestreams.Subscriber<Object>, DefaultNettyConnection.WritableListener, Cancellable {
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
    /**
     * It is assumed the underlying transport is ordered and reliable such that if a single write fails then the remaining
     * writes will also fail. This allows us to only subscribe to the status of the last write operation as a summary of the
     * status for all write operations.
     */
    @Nullable
    private ChannelPromise lastWritePromise;
    @SuppressWarnings("unused")
    @Nullable
    private volatile Subscription subscription;
    @SuppressWarnings("unused")
    private volatile long requested;

    /**
     * This is invoked from the context of on* methods. ReactiveStreams spec says that invocations to Subscriber's on* methods,
     * when done from multiple threads, must use external synchronization (Rule 1.3). This means, this variable does not have to be volatile.
     */
    private boolean enqueueWrites;
    private boolean terminated;
    private final CloseHandler closeHandler;

    WriteStreamSubscriber(Channel channel, RequestNSupplier requestNSupplier, Subscriber subscriber,
                          CloseHandler closeHandler) {
        this.eventLoop = requireNonNull(channel.eventLoop());
        this.subscriber = subscriber;
        this.channel = channel;
        this.requestNSupplier = requestNSupplier;
        this.closeHandler = closeHandler;
    }

    @Override
    public void onSubscribe(Subscription s) {
        final Subscription concurrentSubscription = ConcurrentSubscription.wrap(s);
        if (!subscriptionUpdater.compareAndSet(this, null, concurrentSubscription)) {
            // Either onSubscribe was called twice or Subscription is cancelled, in both cases, we cancel the new Subscription.
            s.cancel();
            return;
        }
        subscriber.onSubscribe(concurrentSubscription::cancel);
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
             * If any onNext comes from out of the eventloop, we should enqueue all subsequent writes and terminal notifications on the eventloop.
             * Otherwise, the order of writes will not be preserved in the following case:
             *
             * Write1 (Thread1) -> Write2 (Eventloop)
             *
             * If Thread1 != this channels Eventloop then Write2 may happen before Write1 as a write from the eventloop will skip the task queue
             * and directly send the write on the pipeline.
             */
            enqueueWrites = true;
        }
        lastWritePromise = channel.newPromise();
        if (enqueueWrites) {
            // Make sure we save a reference to the current lastWritePromise becuase it may change by the time the Runnable below is executed.
            ChannelPromise lastWritePromise = this.lastWritePromise;
            eventLoop.execute(() -> {
                doWrite(o, lastWritePromise);
                requestMoreIfRequired(subscription);
            });
        } else {
            doWrite(o, lastWritePromise);
            requestMoreIfRequired(subscription);
        }
    }

    void doWrite(Object msg, ChannelPromise writePromise) {
        long capacityBefore = channel.bytesBeforeUnwritable();
        channel.write(msg, writePromise);
        long capacityAfter = channel.bytesBeforeUnwritable();
        requestNSupplier.onItemWrite(msg, capacityBefore, capacityAfter);
    }

    @Override
    public void onError(Throwable cause) {
        if (lastWritePromise != null) {
            lastWritePromise.addListener(future -> terminateListenerAndCloseChannel(cause));
        } else if (eventLoop.inEventLoop()) {
            // If lastWritePromise is null that means there are no writes, enqueueWrites should only be set to true if
            // there are writes.
            assert !enqueueWrites;
            terminateListenerAndCloseChannel(cause);
        } else {
            // If lastWritePromise is null that means there are no writes, enqueueWrites should only be set to true if
            // there are writes.
            assert !enqueueWrites;
            eventLoop.execute(() -> terminateListenerAndCloseChannel(cause));
        }
    }

    @Override
    public void onComplete() {
        if (lastWritePromise != null) {
            lastWritePromise.addListener(this::terminateAndCloseIfLastWriteFailed);
        } else if (eventLoop.inEventLoop()) {
            // If lastWritePromise is null that means there are no writes, enqueueWrites should only be set to true if
            // there are writes.
            assert !enqueueWrites;
            terminateListener();
        } else {
            // If lastWritePromise is null that means there are no writes, enqueueWrites should only be set to true if
            // there are writes.
            assert !enqueueWrites;
            eventLoop.execute(this::terminateListener);
        }
    }

    @Override
    public void channelWritable() {
        assert eventLoop.inEventLoop();
        requestMoreIfRequired(subscription);
    }

    @Override
    public void channelClosedOutbound() {
        if (!terminated && lastWritePromise != null) {
            lastWritePromise.addListener(this::terminateAndCloseIfLastWriteFailed);
        }
    }

    private void terminateAndCloseIfLastWriteFailed(Future<?> future) {
        Throwable cause = future.cause();
        if (cause == null) {
            terminateListener();
        } else {
            terminateListenerAndCloseChannel(cause);
        }
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
        if (oldVal == null) {
            // If there was no subscriber when the channel closed, we need to call onSubscribe before we terminate.
            subscriber.onSubscribe(IGNORE_CANCEL);
        } else {
            oldVal.cancel();
        }
        terminateListener(closedException, false);
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

    private void terminateListenerAndCloseChannel(Throwable cause) {
        terminateListener(cause, true);
    }

    private void terminateListener() {
        terminateListener(null, false);
    }

    private void terminateListener(@Nullable Throwable cause, boolean closeChannelIfFailed) {
        if (terminated) {
            return;
        }
        terminated = true;
        if (cause != null) {
            if (closeChannelIfFailed) {
                closeHandler.closeChannelOutbound(channel);
            }
            subscriber.onError(cause);
        } else {
            subscriber.onComplete();
        }
    }
}
