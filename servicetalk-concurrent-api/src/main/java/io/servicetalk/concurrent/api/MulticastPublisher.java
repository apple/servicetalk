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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.api.MulticastUtils.IndividualMulticastSubscriber;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.DelayedSubscription;
import io.servicetalk.concurrent.internal.QueueFullException;
import io.servicetalk.concurrent.internal.RejectedSubscribeException;
import io.servicetalk.concurrent.internal.SignalOffloader;
import io.servicetalk.concurrent.internal.TerminalNotification;
import io.servicetalk.context.api.ContextMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceArray;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.PublishAndSubscribeOnPublishers.deliverOnSubscribeAndOnError;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.unwrapNullUnchecked;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.wrapNull;
import static io.servicetalk.concurrent.internal.ConcurrentSubscription.wrap;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.lang.Math.min;

final class MulticastPublisher<T> extends AbstractNoHandleSubscribePublisher<T> implements Subscriber<T> {

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
    private final DelayedSubscription delayedSubscription = new DelayedSubscription();
    private final ConcurrentSubscription subscription = wrap(delayedSubscription);
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

    MulticastPublisher(Publisher<T> original, int expectedSubscribers, Executor executor) {
        this(original, expectedSubscribers, 10, executor);
    }

    MulticastPublisher(Publisher<T> original, int expectedSubscribers, int maxQueueSize, Executor executor) {
        super(executor);
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
    void handleSubscribe(Subscriber<? super T> subscriber, SignalOffloader signalOffloader,
                         ContextMap contextMap, AsyncContextProvider contextProvider) {
        for (;;) {
            final int subscriberCount = this.subscriberCount;
            if (subscriberCount == subscribers.length() || subscriberCount < 0) {
                deliverOnSubscribeAndOnError(subscriber, signalOffloader, contextMap, contextProvider,
                        new RejectedSubscribeException("Only " + subscribers.length() + " subscribers are allowed!"));
                break;
            }

            if (subscriberCountUpdater.compareAndSet(this, subscriberCount, subscriberCount + 1)) {
                MulticastSubscriber<T> multicastSubscriber = new MulticastSubscriber<>(this, subscriber,
                        subscriberCount);
                subscribers.set(subscriberCount, multicastSubscriber);
                multicastSubscriber.onSubscribe(subscription);
                if (subscriberCount == subscribers.length() - 1) {
                    // This operator has special behavior where it chooses to use the AsyncContext and signal offloader
                    // from the last subscribe operation.
                    original.delegateSubscribe(this, signalOffloader, contextMap, contextProvider);
                }
                break;
            }
        }
    }

    @Override
    public void onSubscribe(Subscription s) {
        delayedSubscription.delayedSubscription(s);
    }

    private boolean offerNext(@Nullable Object o) {
        assert reentryQueue != null;
        return reentryQueue.size() < maxQueueSize && reentryQueue.offer(wrapNull(o));
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
                terminatedPrematurely = true;
                // Cancel subscription but not mark state as cancelled since we still need to drain the groups.
                subscription.cancel();
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

                            onNext0(unwrapNullUnchecked(next));
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
        if (terminalNotification.cause() == null) {
            onComplete0();
        } else {
            onError0(terminalNotification.cause());
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
        final long individualSourceRequested = subscriber.sourceRequested();
        for (;;) {
            final long sourceRequested = this.sourceRequested;
            if (sourceRequested >= individualSourceRequested) {
                break;
            }
            if (sourceRequestedUpdater.compareAndSet(this, sourceRequested, individualSourceRequested)) {
                subscription.request(individualSourceRequested - sourceRequested);
                break;
            }
        }
    }

    void cancelIndividualSubscriber(int subscriberIndex) {
        Subscriber<? super T> subscriber = subscribers.getAndSet(subscriberIndex, noopSubscriber());
        if (subscriber != NOOP_SUBSCRIBER && notCancelledCountUpdater.decrementAndGet(this) == 0) {
            subscription.cancel();
        }
    }

    private static final class MulticastSubscriber<T> extends IndividualMulticastSubscriber<T>
            implements Subscriber<T> {
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
            source.subscription.request(n);
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
