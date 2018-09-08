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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.TerminalNotification;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.FlowControlUtil.addWithOverflowProtection;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static io.servicetalk.concurrent.internal.ThrowableUtil.unknownStackTrace;
import static java.util.Objects.requireNonNull;

final class NettyChannelPublisher<T> extends Publisher<T> {

    private static final ClosedChannelException CLOSED_CHANNEL_EXCEPTION = unknownStackTrace(new ClosedChannelException(), NettyChannelPublisher.class, "channelInactive");

    // All state is only touched from eventloop.
    private long requestCount;
    private boolean requested;
    private boolean inProcessPending;
    @Nullable
    private Subscriber<? super T> subscriber;
    @Nullable
    private Queue<Object> pending;
    @Nullable
    private Throwable fatalError;

    private final Channel channel;
    private final EventLoop eventLoop;
    private final Predicate<T> isLastElement;

    NettyChannelPublisher(Channel channel, Predicate<T> isLastElement) {
        this.eventLoop = channel.eventLoop();
        this.isLastElement = requireNonNull(isLastElement);
        this.channel = channel;
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
            /*
             * We do not expect ref-counted objects here as ST does not support them and do not take care to clean them in error conditions.
             * Hence we fail-fast when we see such objects.
             */
            ReferenceCountUtil.release(data);
            exceptionCaught(new IllegalStateException("Reference counted leaked netty's pipeline. Object: " + data.getClass().getSimpleName()));
            channel.close();
            return;
        }

        if (subscriber == null || shouldBuffer()) {
            addPending(data);
            if (subscriber != null) {
                processPending(subscriber);
            }
        } else {
            emit(subscriber, data);
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
        if (subscriber == null || shouldBuffer()) {
            addPending(TerminalNotification.error(throwable));
            if (subscriber != null) {
                processPending(subscriber);
            }
        } else {
            sendErrorToTarget(subscriber, throwable);
        }
    }

    void channelInactive() {
        assertInEventloop();
        fatalError = CLOSED_CHANNEL_EXCEPTION;
        exceptionCaught(CLOSED_CHANNEL_EXCEPTION);
    }

    // All private methods MUST be invoked from the eventloop.

    private void requestN(long n, Subscriber<? super T> forSubscriber) {
        if (forSubscriber != subscriber) {
            // Subscriptions shares common state hence a requestN after termination/cancellation must be ignored
            return;
        }
        if (isRequestNValid(n)) {
            requestCount = addWithOverflowProtection(requestCount, n);
            if (!processPending(forSubscriber) && !requested && requestCount > 0) {
                // If subscriber wasn't terminated from the queue, then request more.
                requestChannel();
            }
        } else {
            resetSubscriber();
            forSubscriber.onError(newExceptionForInvalidRequestN(n));
            // The specification has been violated. There is no way to know if more demand for data will come, so we force close the connection
            // to ensure we don't hang indefinitely.
            channel.close();
        }
    }

    private boolean processPending(Subscriber<? super T> target) {
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
                    Throwable throwable = ((TerminalNotification) p).getCause();
                    assert throwable != null : "onComplete notification can not be enqueued.";
                    sendErrorToTarget(target, throwable);
                    return true;
                }

                if (emit(target, p)) {
                    return true;
                }
            }
            if (pending.peek() instanceof TerminalNotification) {
                TerminalNotification terminal = (TerminalNotification) pending.poll();
                Throwable throwable = terminal.getCause();
                assert throwable != null : "onComplete notification can not be enqueued.";
                sendErrorToTarget(target, throwable);
                return true;
            }
        } finally {
            inProcessPending = false;
        }
        return false;
    }

    private boolean emit(Subscriber<? super T> target, Object next) {
        requestCount--;
        @SuppressWarnings("unchecked")
        T t = (T) next;
        /*
         * In case when this Publisher is converted to a Single (with isLast always returning true),
         * it should be possible for us to cancel the Subscription inside onNext.
         * Operators like first that pick a single item does exactly that.
         * If we do not resetSubscriber() before onNext such a cancel will be illegal and close the connection.
         */
        boolean isLast = isLastElement.test(t);
        if (isLast) {
            resetSubscriber();
        }
        try {
            target.onNext(t);
        } catch (Throwable cause) {
            // Ensure we call subscriber.onError(..)  and cancel the subscription
            sendErrorToTarget(target, cause);

            // Return true as we want to signal we had a terminal event.
            return true;
        }
        if (isLast) {
            target.onComplete();
        }
        return isLast;
    }

    private void sendErrorToTarget(Subscriber<? super T> target, Throwable throwable) {
        resetSubscriber();
        target.onError(throwable);
    }

    private void cancel(Subscriber<? super T> forSubscriber) {
        if (forSubscriber != subscriber) {
            // Subscriptions shares common state hence a requestN after termination/cancellation must be ignored
            return;
        }
        resetSubscriber();
        // If an incomplete subscriber is cancelled then close channel. A subscriber can cancel after getting complete, which should not close the channel.
        channel.close();
    }

    private void resetSubscriber() {
        subscriber = null;
        requestCount = 0;
    }

    private void requestChannel() {
        requested = true;
        channel.read();
    }

    private void addPending(Object p) {
        if (pending == null) {
            pending = new ArrayDeque<>();
        }
        pending.add(p);
    }

    private boolean shouldBuffer() {
        return (pending != null && !pending.isEmpty()) || requestCount == 0;
    }

    private void subscribe0(Subscriber<? super T> subscriber) {
        if (this.subscriber != null) {
            subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
            subscriber.onError(new DuplicateSubscribeException(this.subscriber, subscriber));
        } else {
            this.subscriber = subscriber;
            requestCount = 0; /*Don't pollute requested count between subscribers */
            subscriber.onSubscribe(new SubscriptionImpl(subscriber));
            // Fatal error is removed from the queue once it is drained for a Subscriber.
            // In absence of the below, any subsequent Subscriber will not get any fatal error.
            if (!processPending(subscriber) && fatalError != null && pending != null && pending.isEmpty()) {
                // We are already on the eventloop, so we are sure that nobody else is emitting to the Subscriber.
                sendErrorToTarget(subscriber, fatalError);
            }
        }
    }

    private void assertInEventloop() {
        assert eventLoop.inEventLoop() : "Must be called from the associated eventloop.";
    }

    private final class SubscriptionImpl implements Subscription {

        private final Subscriber<? super T> associatedSub;

        private SubscriptionImpl(Subscriber<? super T> associatedSub) {
            this.associatedSub = associatedSub;
        }

        @Override
        public void request(long n) {
            if (eventLoop.inEventLoop()) {
                NettyChannelPublisher.this.requestN(n, associatedSub);
            } else {
                eventLoop.execute(() -> NettyChannelPublisher.this.requestN(n, associatedSub));
            }
        }

        @Override
        public void cancel() {
            if (eventLoop.inEventLoop()) {
                NettyChannelPublisher.this.cancel(associatedSub);
            } else {
                eventLoop.execute(() -> NettyChannelPublisher.this.cancel(associatedSub));
            }
        }
    }
}
