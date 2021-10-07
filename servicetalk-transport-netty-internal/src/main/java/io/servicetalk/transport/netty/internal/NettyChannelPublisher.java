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

import io.servicetalk.concurrent.api.internal.SubscribablePublisher;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.TerminalNotification;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.ReferenceCounted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static io.servicetalk.transport.netty.internal.ChannelCloseUtils.assignConnectionError;
import static io.servicetalk.transport.netty.internal.ChannelCloseUtils.close;

final class NettyChannelPublisher<T> extends SubscribablePublisher<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(NettyChannelPublisher.class);
    // All state is only touched from eventloop.
    private long requestCount;
    private boolean requested;
    @Nullable
    private SubscriptionImpl subscription;
    @Nullable
    private Queue<Object> pending;
    @Nullable
    private Throwable fatalError;

    private final Channel channel;
    private final CloseHandler closeHandler;
    private final EventLoop eventLoop;

    NettyChannelPublisher(Channel channel, CloseHandler closeHandler) {
        this.eventLoop = channel.eventLoop();
        this.channel = channel;
        this.closeHandler = closeHandler;
    }

    @Override
    protected void handleSubscribe(Subscriber<? super T> nextSubscriber) {
        if (eventLoop.inEventLoop()) {
            subscribe0(nextSubscriber);
        } else {
            eventLoop.execute(() -> subscribe0(nextSubscriber));
        }
    }

    void channelRead(T data) {
        assertInEventloop();
        if (data instanceof ReferenceCounted) {
            channelReadReferenceCounted((ReferenceCounted) data);
            return;
        }
        if (fatalError != null) {
            return;
        }

        if (subscription == null || shouldBuffer()) {
            addPending(data);
            if (subscription != null) {
                processPending(subscription);
            }
        } else {
            emit(subscription, data);
        }
    }

    /**
     * Signifies all data has been read and {@link Subscriber#onComplete()} should be emitted.
     */
    void channelOnComplete() {
        assertInEventloop();
        if (fatalError != null) {
            return;
        }

        if (subscription == null || hasQueuedSignals()) {
            addPending(complete());
            if (subscription != null) {
                processPending(subscription);
            }
        } else {
            emitComplete(subscription);
        }
    }

    void channelOnError(Throwable throwable) {
        assertInEventloop();
        if (fatalError == null) {
            // The Throwable is propagated as-is downstream but subsequent subscribers should see a
            // ClosedChannelException (with original Throwable as the cause for context).
            fatalError = throwable instanceof ClosedChannelException ? throwable :
                    StacklessClosedChannelException.newInstance(NettyChannelPublisher.class, "channelOnError")
                    .initCause(throwable);
            channelOnError0(throwable);
        }
    }

    private void channelOnError0(Throwable throwable) {
        assignConnectionError(channel, throwable);
        if (subscription == null) {
            closeChannelInbound();
            if (hasQueuedSignals()) {
                addPending(error(throwable));
            }
        } else if (hasQueuedSignals()) {
            addPending(error(throwable));
            processPending(subscription);
        } else {
            emitError(subscription, throwable);
        }
    }

    private void channelReadReferenceCounted(ReferenceCounted data) {
        try {
            data.release();
        } finally {
            // We do not expect ref-counted objects here as ST does not support them and do not take care to clean them
            // in error conditions. Hence we fail-fast when we see such objects.
            emitCatchError(subscription,
                    new IllegalArgumentException("Reference counted leaked netty's pipeline. Object: " +
                            data.getClass().getSimpleName()), true);
        }
    }

    void onReadComplete() {
        assertInEventloop();
        requested = false;
        if (requestCount > 0) {
            requestChannel();
        }
    }

    // All private methods MUST be invoked from the eventloop.

    private void requestN(long n, SubscriptionImpl forSubscription) {
        if (forSubscription != subscription) {
            // Subscription shares common state hence a requestN after termination/cancellation must be ignored
            return;
        }
        if (isRequestNValid(n)) {
            requestCount = addWithOverflowProtection(requestCount, n);
            if (!processPending(forSubscription) && !requested && requestCount > 0) {
                // If subscriber wasn't terminated from the queue, then request more.
                requestChannel();
            }
        } else {
            resetSubscription();
            final IllegalArgumentException cause = newExceptionForInvalidRequestN(n);
            forSubscription.associatedSub.onError(cause);
            // The specification has been violated. There is no way to know if more demand for data will come, so we
            // force close the connection to ensure we don't hang indefinitely.
            close(channel, cause);
        }
    }

    private boolean processPending(SubscriptionImpl target) {
        // Should always be called from EventLoop. (assert done before calling)
        if (pending == null) {
            return false;
        }

        for (;;) {
            while (requestCount > 0) {
                Object p = pending.poll();
                if (p == null) {
                    return false;
                } else if ((p instanceof TerminalNotification && emit(target, (TerminalNotification) p)) ||
                        emit(target, p)) {
                    // stop draining the pending events if the current Subscription is still the same for which
                    // we started draining, continue emitting the remaining data if there is a new Subscriber
                    if (subscription == null || subscription == target) {
                        return true;
                    }
                    target = subscription;
                }
            }
            if (pending.peek() instanceof TerminalNotification) {
                emit(target, (TerminalNotification) pending.poll());
                // stop draining the pending events if the current Subscription is still the same for which
                // we started draining, continue emitting the remaining data if there is a new Subscriber
                if (subscription == null || subscription == target) {
                    return true;
                }
                target = subscription;
            } else {
                return false;
            }
        }
    }

    private boolean emit(SubscriptionImpl target, Object next) {
        assert requestCount > 0;
        --requestCount;
        try {
            @SuppressWarnings("unchecked")
            final T t = (T) next;
            target.associatedSub.onNext(t);
        } catch (Throwable cause) {
            emitCatchError(target, cause, true);
            return true;
        }
        return false;
    }

    @SuppressWarnings("StatementWithEmptyBody")
    private void emitCatchError(@Nullable SubscriptionImpl target, Throwable cause,
                                boolean drainPendingToNextTerminal) {
        // If we have items queued, we avoid delivering partial content to the next subscriber by draining until we see
        // a Terminal signal. We also don't enqueue future signals after we see a fatal error.
        if (pending != null && drainPendingToNextTerminal) {
            Object top;
            while ((top = pending.poll()) != null && !(top instanceof TerminalNotification)) {
                // intentionally empty
            }
        }
        if (fatalError == null) {
            fatalError = cause;
        }
        if (target != null) {
            emitError(target, cause);
        } else {
            LOGGER.debug("caught unexpected exception, closing channel {}", channel, cause);
            // If an incomplete subscriber is cancelled then close channel. A subscriber can cancel after getting
            // complete, which should not close the channel.
            closeChannelInbound();
        }
    }

    private boolean emit(SubscriptionImpl target, TerminalNotification terminal) {
        final Throwable cause = terminal.cause();
        if (cause == null) {
            emitComplete(target);
        } else {
            emitError(target, cause);
        }
        return true;
    }

    private void emitComplete(SubscriptionImpl target) {
        resetSubscription();
        try {
            target.associatedSub.onComplete();
        } catch (Throwable cause) {
            emitCatchError(null, cause, false);
        }
    }

    private void emitError(SubscriptionImpl target, Throwable throwable) {
        resetSubscription();
        try {
            target.associatedSub.onError(throwable);
        } finally {
            // We do not support resumption once we observe an error since we are not sure whether the channel is in a
            // state to be resumed. Users are responsible to catch-ignore resumable exceptions in the pipeline or from
            // the processing of a message in onNext().
            closeChannelInbound();
        }
    }

    private void cancel0(SubscriptionImpl forSubscription) {
        if (forSubscription != subscription) {
            // Subscription shares common state hence a requestN after termination/cancellation must be ignored
            return;
        }
        resetSubscription();

        // If a cancel occurs with a valid subscription we need to clear any pending data and set a fatalError so that
        // any future Subscribers don't get partial data delivered from the queue.
        emitCatchError(null, StacklessClosedChannelException.newInstance(NettyChannelPublisher.class, "cancel"), true);
    }

    private void closeChannelInbound() {
        closeHandler.closeChannelInbound(channel);
    }

    private void resetSubscription() {
        subscription = null;
        requestCount = 0;
    }

    private void requestChannel() {
        requested = true;
        channel.read();
    }

    private void addPending(Object p) {
        if (pending == null) {
            pending = new ArrayDeque<>(4);  // queue should be able to fit: headers + payloadBody + trailers
        }
        pending.add(p);
    }

    private boolean shouldBuffer() {
        return hasQueuedSignals() || requestCount == 0;
    }

    private boolean hasQueuedSignals() {
        return pending != null && !pending.isEmpty();
    }

    private void subscribe0(Subscriber<? super T> subscriber) {
        SubscriptionImpl subscription = this.subscription;
        if (subscription != null) {
            deliverErrorFromSource(subscriber,
                    new DuplicateSubscribeException(subscription.associatedSub, subscriber));
        } else {
            assert requestCount == 0;
            subscription = new SubscriptionImpl(subscriber);
            this.subscription = subscription;
            subscriber.onSubscribe(subscription);
            // Fatal error is removed from the queue once it is drained for a Subscriber.
            // In absence of the below, any subsequent Subscriber will not get any fatal error.
            if (subscription == this.subscription && !processPending(subscription) &&
                    (fatalError != null && !hasQueuedSignals())) {
                // We are already on the eventloop, so we are sure that nobody else is emitting to the Subscriber.
                emitError(subscription, fatalError);
            }
        }
    }

    private void assertInEventloop() {
        assert eventLoop.inEventLoop() : "Must be called from the associated eventloop.";
    }

    private final class SubscriptionImpl implements Subscription {

        final Subscriber<? super T> associatedSub;

        private SubscriptionImpl(Subscriber<? super T> associatedSub) {
            this.associatedSub = associatedSub;
        }

        @Override
        public void request(long n) {
            if (eventLoop.inEventLoop()) {
                NettyChannelPublisher.this.requestN(n, this);
            } else {
                eventLoop.execute(() -> NettyChannelPublisher.this.requestN(n, SubscriptionImpl.this));
            }
        }

        @Override
        public void cancel() {
            if (eventLoop.inEventLoop()) {
                NettyChannelPublisher.this.cancel0(this);
            } else {
                eventLoop.execute(() -> NettyChannelPublisher.this.cancel0(SubscriptionImpl.this));
            }
        }
    }
}
