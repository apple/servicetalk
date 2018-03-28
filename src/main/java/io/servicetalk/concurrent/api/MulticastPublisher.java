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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.api.MulticastUtils.IndividualMulticastSubscriber;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceArray;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.PlatformDependent.throwException;
import static io.servicetalk.concurrent.internal.SubscriberUtils.NULL_TOKEN;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static java.lang.Math.min;

final class MulticastPublisher<T> extends Publisher<T> implements Subscriber<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MulticastPublisher.class);

    private static final AtomicIntegerFieldUpdater<MulticastPublisher> notCancelledCountUpdater =
            AtomicIntegerFieldUpdater.newUpdater(MulticastPublisher.class, "notCancelledCount");
    private static final AtomicIntegerFieldUpdater<MulticastPublisher> subscriberCountUpdater =
            AtomicIntegerFieldUpdater.newUpdater(MulticastPublisher.class, "subscriberCount");
    private static final AtomicLongFieldUpdater<MulticastPublisher> sourceRequestedUpdater =
            AtomicLongFieldUpdater.newUpdater(MulticastPublisher.class, "sourceRequested");

    /**
     * Used internally to distinguish between an external {@link Subscriber} and also ensures no side-effects.
     */
    private static final Subscriber NOOP_SUBSCRIBER = new Subscriber() {
        @Override
        public void onSubscribe(Subscription s) {
            s.cancel();
        }

        @Override
        public void onNext(Object o) {
        }

        @Override
        public void onError(Throwable ignore) {
        }

        @Override
        public void onComplete() {
        }
    };

    private boolean terminatedPrematurely;
    private boolean inOnNext;
    @Nullable
    private Queue<Object> reentryQueue;
    @Nullable
    private volatile Subscription subscription;
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private volatile int notCancelledCount;
    @SuppressWarnings("unused")
    private volatile int subscriberCount;
    @SuppressWarnings("unused")
    private volatile long pendingSourceRequested;
    @SuppressWarnings("unused")
    private volatile long sourceRequested;
    private final int maxQueueSize;
    private final AtomicReferenceArray<Subscriber<? super T>> subscribers;
    private final Publisher<T> original;

    MulticastPublisher(Publisher<T> original, int expectedSubscribers) {
        this(original, expectedSubscribers, 10);
    }

    MulticastPublisher(Publisher<T> original, int expectedSubscribers, int maxQueueSize) {
        if (expectedSubscribers < 2) {
            throw new IllegalArgumentException("expectedSubscribers: " + expectedSubscribers + " (expected >=2)");
        }
        if (maxQueueSize < 1) {
            throw new IllegalArgumentException("maxQueueSize: " + maxQueueSize + " (expected >=1)");
        }
        this.original = original;
        notCancelledCount = expectedSubscribers;
        this.maxQueueSize = maxQueueSize;
        subscribers = new AtomicReferenceArray<>(expectedSubscribers);
    }

    @Override
    protected void handleSubscribe(Subscriber<? super T> subscriber) {
        for (;;) {
            final int subscriberCount = this.subscriberCount;
            if (subscriberCount == subscribers.length() || subscriberCount < 0) {
                subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                subscriber.onError(new IllegalStateException("Only " + subscribers.length() + " subscribers are allowed!"));
                break;
            }

            if (subscriberCountUpdater.compareAndSet(this, subscriberCount, subscriberCount + 1)) {
                subscribers.set(subscriberCount, new MulticastSubscriber<>(this, subscriber, subscriberCount));
                if (subscriberCount == subscribers.length() - 1) {
                    original.subscribe(this);
                }
                break;
            }
        }
    }

    @Override
    public void onSubscribe(Subscription s) {
        if (!checkDuplicateSubscription(subscription, s)) {
            return;
        }
        subscription = s = ConcurrentSubscription.wrap(s);
        // We always call onSubscribe for all Subscribers and if an exception is thrown by any Subscriber we throw it
        // after we are done. We rely upon the original Publisher of data to then call onError, and therefore we need
        // to make sure we call onSubscribe for all Subscribers so we don't call onError without calling onSubscribe.
        Throwable cause = null;
        for (int i = 0; i < subscribers.length(); ++i) {
            try {
                subscribers.get(i).onSubscribe(s);
            } catch (Throwable t) {
                if (cause == null) {
                    cause = t;
                } else {
                    cause.addSuppressed(t);
                }
            }
        }
        if (cause != null) {
            throwException(cause);
        }
    }

    private boolean offerNext(@Nullable Object o) {
        assert reentryQueue != null;
        return reentryQueue.size() < maxQueueSize && reentryQueue.offer(o == null ? NULL_TOKEN : o);
    }

    private void offerTerminal(TerminalNotification notification) {
        assert reentryQueue != null;
        reentryQueue.offer(notification);
    }

    @Override
    public void onNext(T t) {
        if (terminatedPrematurely) {
            return;
        }
        if (inOnNext) {
            // If we are already in onNext that means there is re-entry, and in order to preserve the request count
            // we should queue this object and process it after the stack unwinds.
            if (reentryQueue == null) {
                // There will only ever be a single thread generating data. It is safe to lazy-create the groupQueue
                // without using atomic operations.
                reentryQueue = new ArrayDeque<>(min(4, maxQueueSize));
            }
            if (!offerNext(t)) {
                Subscription s = subscription;
                assert s != null : "Subscription can not be null in onNext()";
                terminatedPrematurely = true;

                s.cancel(); // Cancel subscription but not mark state as cancelled since we still need to drain the groups.
                offerTerminal(TerminalNotification.error(new QueueFullException("global", maxQueueSize)));
            }
        } else {
            inOnNext = true;
            try {
                onNext0(t);
                if (reentryQueue != null && !reentryQueue.isEmpty()) {
                    do {
                        Object next;
                        while ((next = reentryQueue.poll()) != null) {
                            if (next instanceof TerminalNotification) {
                                TerminalNotification terminalNotification = (TerminalNotification) next;
                                try {
                                    terminateFromQueuedEvent(terminalNotification);
                                } catch (Throwable throwable) {
                                    LOGGER.error("Error from terminal callbacks to subscriber {}", this, throwable);
                                }
                                return;
                            }

                            @SuppressWarnings("unchecked")
                            final T nextT = (T) (next == NULL_TOKEN ? null : next);
                            onNext0(nextT);
                        }

                        if (reentryQueue.peek() instanceof TerminalNotification) {
                            TerminalNotification terminalNotification = (TerminalNotification) reentryQueue.poll();
                            try {
                                terminateFromQueuedEvent(terminalNotification);
                            } catch (Throwable throwable) {
                                LOGGER.error("Error from terminal callbacks to subscriber {}", this, throwable);
                            }
                            return;
                        }
                    } while (!reentryQueue.isEmpty());
                }
            } finally {
                inOnNext = false;
            }
        }
    }

    private void terminateFromQueuedEvent(TerminalNotification terminalNotification) {
        if (terminalNotification.getCause() == null) {
            onComplete0();
        } else {
            onError0(terminalNotification.getCause());
        }
    }

    @Override
    public void onError(Throwable t) {
        if (terminatedPrematurely) {
            return;
        }
        if (inOnNext && reentryQueue != null && !reentryQueue.isEmpty()) {
            offerTerminal(TerminalNotification.error(t));
        } else {
            onError0(t);
        }
    }

    private void onError0(Throwable t) {
        Throwable overallCause = null;
        for (int i = 0; i < subscribers.length(); ++i) {
            Subscriber<? super T> subscriber = subscribers.getAndSet(i, noopSubscriber());
            try {
                subscriber.onError(t);
            } catch (Throwable cause) {
                if (overallCause == null) {
                    overallCause = cause;
                } else {
                    overallCause.addSuppressed(cause);
                }
            }
        }
        if (overallCause != null) {
            throwException(overallCause);
        }
    }

    @Override
    public void onComplete() {
        if (terminatedPrematurely) {
            return;
        }
        if (inOnNext && reentryQueue != null && !reentryQueue.isEmpty()) {
            offerTerminal(TerminalNotification.complete());
        } else {
            onComplete0();
        }
    }

    private void onComplete0() {
        Throwable overallCause = null;
        for (int i = 0; i < subscribers.length(); ++i) {
            Subscriber<? super T> subscriber = subscribers.getAndSet(i, noopSubscriber());
            try {
                subscriber.onComplete();
            } catch (Throwable cause) {
                if (overallCause == null) {
                    overallCause = cause;
                } else {
                    overallCause.addSuppressed(cause);
                }
            }
        }
        if (overallCause != null) {
            throwException(overallCause);
        }
    }

    private void onNext0(@Nullable T t) {
        for (int i = 0; i < subscribers.length(); ++i) {
            // Ignore exceptions, let the Producer of data above this operator catch it and propagate an onError
            subscribers.get(i).onNext(t);
        }
    }

    void requestIndividualSubscriber(MulticastSubscriber<T> subscriber) {
        // This is the cumulative amount requested from this Subscriber.
        // If we compare this with the cumulative amount we have requested upstream, we can deduce the delta to request.
        final long individualSourceRequested = subscriber.getSourceRequested();
        for (;;) {
            final long sourceRequested = this.sourceRequested;
            if (sourceRequested >= individualSourceRequested) {
                break;
            }
            if (sourceRequestedUpdater.compareAndSet(this, sourceRequested, individualSourceRequested)) {
                Subscription subscription = this.subscription;
                assert subscription != null;
                subscription.request(individualSourceRequested - sourceRequested);
                break;
            }
        }
    }

    void cancelIndividualSubscriber(int subscriberIndex) {
        Subscriber<? super T> subscriber = subscribers.getAndSet(subscriberIndex, noopSubscriber());
        if (subscriber != NOOP_SUBSCRIBER && notCancelledCountUpdater.decrementAndGet(this) == 0) {
            Subscription subscription = this.subscription;
            assert subscription != null;
            subscription.cancel();
        }
    }

    private static final class MulticastSubscriber<T> extends IndividualMulticastSubscriber<T> implements Subscriber<T> {
        private static final Logger LOGGER = LoggerFactory.getLogger(MulticastSubscriber.class);
        private final MulticastPublisher<T> source;
        final int subscriberIndex;

        MulticastSubscriber(MulticastPublisher<T> source, Subscriber<? super T> target, int subscriberIndex) {
            super(source.maxQueueSize, target);
            this.source = source;
            this.subscriberIndex = subscriberIndex;
        }

        @Override
        public void onSubscribe(Subscription s) {
            Subscriber<? super T> target = super.target;
            assert target != null;
            target.onSubscribe(this);
        }

        @Override
        String queueIdentifier() {
            return source + " " + subscriberIndex;
        }

        @Override
        void requestFromSource(int requestN) {
            source.requestIndividualSubscriber(this);
        }

        @Override
        void handleInvalidRequestN(long n) {
            Subscription subscription = source.subscription;
            assert subscription != null;
            subscription.request(n);
        }

        @Override
        void cancelSourceFromExternal(Throwable cause) {
            LOGGER.error("Unexpected exception thrown from {} subscriber", queueIdentifier(), cause);
            source.cancelIndividualSubscriber(subscriberIndex);
        }

        @Override
        void cancelSourceFromSource(boolean subscriberLockAcquired, Throwable cause) {
            LOGGER.error("Unexpected exception thrown from {} subscriber", queueIdentifier(), cause);
            source.cancelIndividualSubscriber(subscriberIndex);
        }

        @Override
        public void cancel() {
            source.cancelIndividualSubscriber(subscriberIndex);
        }
    }

    @SuppressWarnings("unchecked")
    private static <X> Subscriber<X> noopSubscriber() {
        return (Subscriber<X>) NOOP_SUBSCRIBER;
    }
}
