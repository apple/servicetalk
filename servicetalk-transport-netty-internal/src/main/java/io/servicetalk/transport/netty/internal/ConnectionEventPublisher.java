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
import io.servicetalk.transport.netty.internal.NettyConnectionContext.ConnectionEvent;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.FlowControlUtil.addWithOverflowProtection;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static io.servicetalk.transport.netty.internal.NettyConnectionContext.ConnectionEvent.ReadComplete;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * A {@link Publisher} of {@link ConnectionEvent}s for a {@link Channel} as returned from
 * {@link Connection#connectionEvents()}.
 */
final class ConnectionEventPublisher extends Publisher<ConnectionEvent> implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionEventPublisher.class);
    private static final SubscriberHolder[] CLOSED = new SubscriberHolder[0];
    private static final AtomicReferenceFieldUpdater<ConnectionEventPublisher, SubscriberHolder[]> subscribersUpdater =
            newUpdater(ConnectionEventPublisher.class, SubscriberHolder[].class, "subscribers");

    private final EventLoop eventLoop;

    /**
     * If {@link Subscriber}s are {@link Subscription#cancel() cancelled} then it will be removed from the array
     * when a new {@link Subscriber} arrives.
     */
    @SuppressWarnings("unused")
    @Nullable
    private volatile SubscriberHolder[] subscribers;

    /**
     * For delayed Subscribers that arrive after a ReadComplete event is received, we should send one such event.
     * This state represents that event.
     */
    private volatile boolean oneReadCompleteReceived;

    /**
     * New instance.
     *
     * @param eventLoop {@link EventLoop} for which {@link ConnectionEvent}s will be emitted.
     */
    ConnectionEventPublisher(final EventLoop eventLoop) {
        this.eventLoop = eventLoop;
    }

    @Override
    protected void handleSubscribe(final Subscriber<? super ConnectionEvent> subscriber) {
        final SubscriberHolder holder = new SubscriberHolder(subscriber);
        for (;;) {
            final SubscriberHolder[] current = subscribers;
            if (current == CLOSED) {
                subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                subscriber.onComplete();
                return;
            }
            final SubscriberHolder[] next;
            if (current == null) {
                next = new SubscriberHolder[]{holder};
            } else {
                // Remove terminated (cancelled) subscribers while copying.
                next = Stream.of(current)
                        .filter(SubscriberHolder::isActive)
                        .toArray(length -> new SubscriberHolder[length + 1]);
                next[next.length - 1] = holder;
            }
            if (subscribersUpdater.compareAndSet(this, current, next)) {
                break;
            }
        }
        subscriber.onSubscribe(holder);
        if (oneReadCompleteReceived) {
            holder.emitReadComplete();
        }
    }

    /**
     * Publishes {@link ConnectionEvent#ReadComplete} to all active {@link Subscriber}s having sufficient demand.
     */
    void publishReadComplete() {
        assert eventLoop.inEventLoop() : "Must be called from the eventloop";
        if (!oneReadCompleteReceived) {
            oneReadCompleteReceived = true;
        }
        SubscriberHolder[] subscribers = this.subscribers;
        if (subscribers == null) {
            return;
        }
        for (SubscriberHolder subscriber : subscribers) {
            subscriber.emitReadComplete();
        }
    }

    @Override
    public void close() {
        assert eventLoop.inEventLoop() : "Must be called from the eventloop";
        SubscriberHolder[] subscribers = subscribersUpdater.getAndSet(this, CLOSED);
        if (subscribers == null || subscribers == CLOSED) {
            return;
        }
        for (SubscriberHolder subscriber : subscribers) {
            subscriber.close();
        }
    }

    private static final class SubscriberHolder implements Subscription, AutoCloseable {
        private static final int TERMINATED = -2;
        private static final int PENDING_READ_COMPLETE = -1;

        private static final AtomicLongFieldUpdater<SubscriberHolder> stateUpdater =
                AtomicLongFieldUpdater.newUpdater(SubscriberHolder.class, "state");

        private volatile long state;
        private final Subscriber<? super ConnectionEvent> subscriber;

        SubscriberHolder(final Subscriber<? super ConnectionEvent> subscriber) {
            this.subscriber = subscriber;
        }

        /**
         * Emits the passed {@link ConnectionEvent} if there is enough demand, else drop.
         */
        void emitReadComplete() {
            for (;;) {
                final long s = state;
                if (s == TERMINATED) {
                    return;
                }
                if (s == PENDING_READ_COMPLETE) {
                    // We only emit 1 ReadComplete as older values are not useful for ReadComplete w.r.t flushes
                    // and we do not commit to any buffering in the API.
                    return;
                }
                if (s > 0 && stateUpdater.compareAndSet(this, s, s - 1)) {
                    subscriber.onNext(ReadComplete);
                    return;
                }
                if (s == 0 && stateUpdater.compareAndSet(this, 0, PENDING_READ_COMPLETE)) {
                    return;
                }
            }
        }

        @Override
        public void request(final long n) {
            if (!isRequestNValid(n)) {
                subscriber.onError(newExceptionForInvalidRequestN(n));
                return;
            }
            for (;;) {
                final long s = state;
                if (s == TERMINATED) {
                    return;
                }
                if (s == PENDING_READ_COMPLETE && stateUpdater.compareAndSet(this, PENDING_READ_COMPLETE, n - 1)) {
                    subscriber.onNext(ReadComplete);
                    return;
                }
                if (s >= 0 && stateUpdater.compareAndSet(this, s, addWithOverflowProtection(s, n))) {
                    return;
                }
            }
        }

        @Override
        public void cancel() {
            state = TERMINATED;
        }

        @Override
        public void close() {
            if (stateUpdater.getAndSet(this, TERMINATED) == TERMINATED) {
                return;
            }
            try {
                subscriber.onComplete();
            } catch (Throwable t) {
                LOGGER.debug("Unexpected exception from onComplete of subscriber {}", subscriber, t);
            }
        }

        /**
         * Checks if the contained {@link Subscriber} is active.
         *
         * @return {@code true} if the contained {@link Subscriber} is active.
         */
        boolean isActive() {
            return state != TERMINATED;
        }
    }
}
