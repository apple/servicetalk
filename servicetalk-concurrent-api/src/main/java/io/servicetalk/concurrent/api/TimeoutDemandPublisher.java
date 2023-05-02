/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.Executor;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.ConcurrentTerminalSubscriber;
import io.servicetalk.concurrent.internal.FlowControlUtils;
import io.servicetalk.context.api.ContextMap;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.PublishAndSubscribeOnPublishers.deliverOnSubscribeAndOnError;
import static io.servicetalk.concurrent.internal.EmptySubscriptions.EMPTY_SUBSCRIPTION;
import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

final class TimeoutDemandPublisher<T> extends AbstractNoHandleSubscribePublisher<T> {
    private final Publisher<T> original;
    private final io.servicetalk.concurrent.Executor timeoutExecutor;
    /**
     * non-negative nanoseconds until the timeout
     */
    private final long durationNs;

    TimeoutDemandPublisher(final Publisher<T> original,
                           final long duration,
                           final TimeUnit unit,
                           final Executor timeoutExecutor) {
        this.original = requireNonNull(original);
        this.timeoutExecutor = requireNonNull(timeoutExecutor);
        // We use the duration in arithmetic below to determine the expiration time for the "next timer" below. So
        // lets cap this at 0 to simplify overflow at that time. Negative duration is allowed as input as this
        // simplifies cases where the duration is calculated and an "already timed out" result is found. The caller
        // would otherwise have to generate the timeout exception themselves.
        this.durationNs = max(0, unit.toNanos(duration));
    }

    @Override
    void handleSubscribe(Subscriber<? super T> subscriber,
                         ContextMap contextMap, AsyncContextProvider contextProvider) {
        original.delegateSubscribe(
                TimeoutDemandSubscriber.newInstance(this, subscriber, contextMap, contextProvider),
                contextMap, contextProvider);
    }

    private static final class TimeoutDemandSubscriber<X> implements Subscriber<X>, Subscription {
        /**
         * Create a local instance because the instance is used as part of the local state machine.
         */
        private static final Cancellable LOCAL_IGNORE_CANCEL = () -> { };
        /**
         * Use {@code -1} because {@link #onNext(Object)} unconditionally decrements and doesn't check for underflow.
         */
        private static final long DEMAND_TIMER_FIRED = -1;

        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<TimeoutDemandSubscriber, Cancellable>
                timerCancellableUpdater = newUpdater(TimeoutDemandSubscriber.class, Cancellable.class,
                "timerCancellable");
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<TimeoutDemandSubscriber, Subscription> subscriptionUpdater =
                newUpdater(TimeoutDemandSubscriber.class, Subscription.class, "subscription");
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<TimeoutDemandSubscriber>
                demandUpdater = AtomicLongFieldUpdater.newUpdater(TimeoutDemandSubscriber.class, "demand");

        private final TimeoutDemandPublisher<X> parent;
        private final ConcurrentTerminalSubscriber<? super X> target;
        private final AsyncContextProvider contextProvider;
        private volatile long demand;
        @Nullable
        private volatile Subscription subscription;
        /**
         * <ul>
         * <li>{@code null} - initialization only seen in the constructor and potentially on the first timer fire</li>
         * <li>{@link #LOCAL_IGNORE_CANCEL} - a timeout occurred or normal termination. we don't need a timer.</li>
         * </ul>
         */
        @Nullable
        private volatile Cancellable timerCancellable;

        private TimeoutDemandSubscriber(TimeoutDemandPublisher<X> parent,
                                        Subscriber<? super X> target,
                                        AsyncContextProvider contextProvider) {
            this.parent = parent;
            // Concurrent onSubscribe is protected by subscriptionUpdater, no need to double protect.
            this.target = new ConcurrentTerminalSubscriber<>(target, false);
            this.contextProvider = contextProvider;
        }

        static <X> TimeoutDemandSubscriber<X> newInstance(TimeoutDemandPublisher<X> parent,
                                                          Subscriber<? super X> target,
                                                          ContextMap contextMap,
                                                          AsyncContextProvider contextProvider) {
            TimeoutDemandSubscriber<X> s = new TimeoutDemandSubscriber<>(parent, target, contextProvider);
            try {
                // CAS is just in case the timer fired, the timerFires method schedule a new timer before this thread is
                // able to set the initial timer value. In this case we don't want to overwrite the active timer.
                //
                // We rely upon the timeoutExecutor to save/restore the current context when notifying when the timer
                // fires. An alternative would be to also wrap the Subscriber to preserve the AsyncContext but that
                // would result in duplicate wrapping.
                // The only time this may cause issues if someone disables AsyncContext for the Executor and wants
                // it enabled for the Subscriber, however the user explicitly specifies the Executor with this operator
                // so they can wrap the Executor in this case.
                timerCancellableUpdater.compareAndSet(s, null, requireNonNull(
                        parent.timeoutExecutor.schedule(s::timerFires, parent.durationNs, NANOSECONDS)));
            } catch (Throwable cause) {
                handleConstructorException(s, contextMap, contextProvider, cause);
            }
            return s;
        }

        @Override
        public void onSubscribe(final Subscription s) {
            if (subscriptionUpdater.compareAndSet(this, null, ConcurrentSubscription.wrap(s))) {
                target.onSubscribe(this);
            } else {
                s.cancel();
            }
        }

        @Override
        public void onNext(final X x) {
            // Deliver before starting the timer in case processing takes time.
            target.onNext(x);
            if (demandUpdater.decrementAndGet(this) == 0) {
                startTimer();
            }
        }

        @Override
        public void onError(final Throwable t) {
            if (target.processOnError(t)) {
                stopTimer(true);
            }
        }

        @Override
        public void onComplete() {
            if (target.processOnComplete()) {
                stopTimer(true);
            }
        }

        @Override
        public void request(final long n) {
            final Subscription subscription = this.subscription;
            assert subscription != null;
            if (n > 0 && demandUpdater.getAndAccumulate(this, n,
                    FlowControlUtils::addWithOverflowProtectionIfNotNegative) == 0) {
                stopTimer(false);
            }
            subscription.request(n);
        }

        @Override
        public void cancel() {
            final Subscription subscription = this.subscription;
            assert subscription != null;
            try {
                stopTimer(true);
            } finally {
                subscription.cancel();
            }
        }

        private void timerFires() {
            for (;;) {
                final long currDemand = demand;
                if (currDemand > 0) {
                    // The value we read maybe stale, force write to ensure demand is positive before break.
                    // This also avoids ABA like scenarios where multiple timers are "set" and "cancelled" before
                    // one actually fires.
                    if (demandUpdater.compareAndSet(this, currDemand, currDemand)) {
                        break;
                    }
                } else if (demandUpdater.compareAndSet(this, currDemand, DEMAND_TIMER_FIRED)) {
                    try {
                        stopTimer(true); // clear the reference and prevent future timers.
                    } finally {
                        // Concurrent/multiple termination is protected by ConcurrentTerminalSubscriber.
                        offloadTimeout(new TimeoutException("timeout after " + NANOSECONDS.toMillis(parent.durationNs) +
                                "ms"));
                    }
                    break;
                }
            }
        }

        private void offloadTimeout(Throwable cause) {
            if (immediate() == parent.timeoutExecutor) {
                processTimeout(cause);
            } else {
                // We rely upon the timeout Executor to save/restore the context. so we just use
                // contextProvider.contextMap() here.
                contextProvider.wrapConsumer(this::processTimeout, contextProvider.context()).accept(cause);
            }
        }

        private void processTimeout(Throwable cause) {
            final Subscription subscription = subscriptionUpdater.getAndSet(this, EMPTY_SUBSCRIPTION);
            // We need to deliver cancel upstream first (clear state for Publishers that
            // allow sequential resubscribe) but we always want to force a TimeoutException downstream (because this is
            // the source of the error, despite what any upstream operators/publishers may deliver).
            if (target.deferredOnError(cause)) {
                try {
                    // The timer is started before onSubscribe so the subscription may actually be null at this time.
                    if (subscription != null) {
                        subscription.cancel();
                    } else {
                        target.onSubscribe(EMPTY_SUBSCRIPTION);
                    }
                } finally {
                    target.deliverDeferredTerminal();
                }
            }
        }

        private void startTimer() {
            for (;;) {
                final Cancellable currTimer = timerCancellable;
                if (currTimer == LOCAL_IGNORE_CANCEL) {
                    break;
                }
                final Cancellable nextTimer = parent.timeoutExecutor.schedule(this::timerFires, parent.durationNs,
                        NANOSECONDS);
                if (timerCancellableUpdater.compareAndSet(this, currTimer, nextTimer)) {
                    break;
                } else {
                    nextTimer.cancel();
                }
            }
        }

        private void stopTimer(boolean terminal) {
            // timerCancellable is known not to be null here based upon the current usage of this method.
            final Cancellable cancelable = timerCancellableUpdater.getAndSet(this,
                    terminal ? LOCAL_IGNORE_CANCEL : null);
            if (cancelable != null) {
                cancelable.cancel();
            }
        }

        /**
         * This is unlikely to occur, so we extract the code into a private method.
         * @param cause The exception.
         */
        private static <X> void handleConstructorException(TimeoutDemandSubscriber<X> s,
                                                           ContextMap contextMap,
                                                           AsyncContextProvider contextProvider, Throwable cause) {
            // We must set local state so there are no further interactions with Subscriber in the future.
            s.timerCancellable = LOCAL_IGNORE_CANCEL;
            s.subscription = EMPTY_SUBSCRIPTION;
            deliverOnSubscribeAndOnError(s.target, contextMap, contextProvider, cause);
        }
    }
}
