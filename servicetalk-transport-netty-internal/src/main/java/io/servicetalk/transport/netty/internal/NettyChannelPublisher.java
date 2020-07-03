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

import io.servicetalk.concurrent.api.internal.SubscribablePublisher;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.TerminalNotification;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.ReferenceCounted;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.util.Objects.requireNonNull;

final class NettyChannelPublisher<T> extends SubscribablePublisher<T> {

    // All state is only touched from eventloop.
    private long requestCount;
    private boolean requested;
    private boolean inProcessPending;
    @Nullable
    private SubscriptionImpl subscription;
    @Nullable
    private Queue<Object> pending;
    @Nullable
    private Throwable fatalError;

    private final Channel channel;
    private final CloseHandler closeHandler;
    private final EventLoop eventLoop;
    private final Predicate<T> terminalSignalPredicate;

    NettyChannelPublisher(Channel channel, Predicate<T> terminalSignalPredicate, CloseHandler closeHandler) {
        this.eventLoop = channel.eventLoop();
        this.channel = channel;
        this.closeHandler = closeHandler;
        this.terminalSignalPredicate = requireNonNull(terminalSignalPredicate);
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

    private void channelReadReferenceCounted(ReferenceCounted data) {
        try {
            data.release();
        } finally {
            // We do not expect ref-counted objects here as ST does not support them and do not take care to clean them
            // in error conditions. Hence we fail-fast when we see such objects.
            pending = null;
            if (fatalError == null) {
                fatalError = new IllegalArgumentException("Reference counted leaked netty's pipeline. Object: " +
                        data.getClass().getSimpleName());
                exceptionCaught0(fatalError);
            }
            channel.close();
        }
    }

    void onReadComplete() {
        assertInEventloop();
        requested = false;
        if (requestCount > 0) {
            requestChannel();
        }
    }

    void exceptionCaught(Throwable throwable) {
        assertInEventloop();
        if (fatalError != null) {
            return;
        }
        exceptionCaught0(throwable);
    }

    private void exceptionCaught0(Throwable throwable) {
        TransportObserverUtils.assignConnectionError(channel, throwable);
        if (subscription == null || shouldBuffer()) {
            addPending(TerminalNotification.error(throwable));
            if (subscription != null) {
                processPending(subscription);
            }
        } else {
            sendErrorToTarget(subscription, throwable);
        }
    }

    void channelInboundClosed() {
        assertInEventloop();
        if (fatalError == null) {
            fatalError = StacklessClosedChannelException.newInstance(
                    NettyChannelPublisher.class, "channelInboundClosed");
            exceptionCaught0(fatalError);
        }
    }

    // All private methods MUST be invoked from the eventloop.

    private void requestN(long n, SubscriptionImpl forSubscription) {
        if (forSubscription != subscription) {
            // Subscriptions shares common state hence a requestN after termination/cancellation must be ignored
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
            forSubscription.associatedSub.onError(newExceptionForInvalidRequestN(n));
            // The specification has been violated. There is no way to know if more demand for data will come, so we
            // force close the connection to ensure we don't hang indefinitely.
            channel.close();
        }
    }

    private boolean processPending(SubscriptionImpl target) {
        // Should always be called from eventloop. (assert done before calling)
        if (inProcessPending || pending == null) {
            // Guard against re-entrance.
            return false;
        }

        inProcessPending = true;
        try {
            while (requestCount > 0) {
                Object p = pending.poll();
                if (p == null) {
                    break;
                }
                if (p instanceof TerminalNotification) {
                    Throwable throwable = ((TerminalNotification) p).cause();
                    assert throwable != null : "onComplete notification can not be enqueued.";
                    sendErrorToTarget(target, throwable);
                    return true;
                }
                if (emit(target, p)) {
                    if (subscription == target || subscription == null) {
                        // stop draining the pending events if the current Subscription is still the same for which we
                        // started draining, continue emitting the remaining data if there is a new Subscriber
                        return true;
                    }
                    target = subscription;
                }
            }
            if (pending.peek() instanceof TerminalNotification) {
                TerminalNotification terminal = (TerminalNotification) pending.poll();
                Throwable throwable = terminal.cause();
                assert throwable != null : "onComplete notification can not be enqueued.";
                sendErrorToTarget(target, throwable);
                return true;
            }
        } finally {
            inProcessPending = false;
        }
        return false;
    }

    private boolean emit(SubscriptionImpl target, Object next) {
        requestCount--;
        @SuppressWarnings("unchecked")
        T t = (T) next;
        /*
         * In case when this Publisher is converted to a Single (with isLast always returning true),
         * it should be possible for us to cancel the Subscription inside onNext.
         * Operators like first that pick a single item does exactly that.
         * If we do not resetSubscription() before onNext such a cancel will be illegal and close the connection.
         */
        final boolean isLast = terminalSignalPredicate.test(t);
        if (isLast) {
            resetSubscription();
        }
        try {
            target.associatedSub.onNext(t);
        } catch (Throwable cause) {
            // Ensure we call subscriber.onError(..)  and cancel the subscription
            sendErrorToTarget(target, cause);

            // Return true as we want to signal we had a terminal event.
            return true;
        }
        if (isLast) {
            target.associatedSub.onComplete();
            return true;
        }
        return false;
    }

    private void sendErrorToTarget(SubscriptionImpl target, Throwable throwable) {
        resetSubscription();
        try {
            target.associatedSub.onError(throwable);
        } finally {
            // We do not support resumption once we observe an error since we are not sure whether the channel is in a
            // state to be resumed. We may send data to the next Subscriber which is malformed.
            // Users are responsible to catch-ignore resumable exceptions in the pipeline or from the processing of a
            // message in onNext()
            closeChannelInbound();
        }
    }

    private void cancel(SubscriptionImpl forSubscription) {
        if (forSubscription != subscription) {
            // Subscriptions shares common state hence a requestN after termination/cancellation must be ignored
            return;
        }
        resetSubscription();

        // If a cancel occurs with a valid subscription we need to clear any pending data and set a fatalError so that
        // any future Subscribers don't get partial data delivered from the queue.
        pending = null;
        if (fatalError == null) {
            fatalError = StacklessClosedChannelException.newInstance(NettyChannelPublisher.class, "cancel");
        }

        // If an incomplete subscriber is cancelled then close channel. A subscriber can cancel after getting complete,
        // which should not close the channel.
        closeChannelInbound();
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
        return (pending != null && !pending.isEmpty()) || requestCount == 0;
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
            if (!processPending(subscription) && (fatalError != null && (pending == null || pending.isEmpty()))) {
                // We are already on the eventloop, so we are sure that nobody else is emitting to the Subscriber.
                sendErrorToTarget(subscription, fatalError);
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
                NettyChannelPublisher.this.cancel(this);
            } else {
                eventLoop.execute(() -> NettyChannelPublisher.this.cancel(SubscriptionImpl.this));
            }
        }
    }
}
