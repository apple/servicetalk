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
import io.servicetalk.concurrent.api.TimeoutPublisher.AbstractTimeoutSubscriber;
import io.servicetalk.concurrent.internal.FlowControlUtils;
import io.servicetalk.context.api.ContextMap;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

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

    private static final class TimeoutDemandSubscriber<X> extends AbstractTimeoutSubscriber<X> {
        /**
         * Use {@code -1} because {@link #onNext(Object)} unconditionally decrements and doesn't check for underflow.
         */
        private static final long DEMAND_TIMER_FIRED = -1;

        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<TimeoutDemandSubscriber>
                demandUpdater = AtomicLongFieldUpdater.newUpdater(TimeoutDemandSubscriber.class, "demand");

        private final TimeoutDemandPublisher<X> parent;
        private volatile long demand;

        private TimeoutDemandSubscriber(TimeoutDemandPublisher<X> parent,
                                        Subscriber<? super X> target,
                                        AsyncContextProvider contextProvider) {
            super(target, contextProvider);
            this.parent = parent;
        }

        static <X> TimeoutDemandSubscriber<X> newInstance(TimeoutDemandPublisher<X> parent,
                                                          Subscriber<? super X> target,
                                                          ContextMap contextMap,
                                                          AsyncContextProvider contextProvider) {
            TimeoutDemandSubscriber<X> s = new TimeoutDemandSubscriber<>(parent, target, contextProvider);
            s.initTimer(parent.durationNs, parent.timeoutExecutor, contextMap);
            return s;
        }

        @Override
        public void onNext(final X x) {
            // Deliver before starting the timer in case processing takes time, and also the delivery of data may
            // increase demand which saves starting/stopping the timer.
            target.onNext(x);
            if (demandUpdater.decrementAndGet(this) == 0) {
                startTimer();
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
        void timerFires() {
            for (;;) {
                final long currDemand = demand;
                if (currDemand != 0) {
                    // demand and timer state are set independent, so it is possible the timer may fire while there is
                    // demand due to race conditions. If this is the case just bail as it isn't a "real" timeout.
                    break;
                } else if (demandUpdater.compareAndSet(this, currDemand, DEMAND_TIMER_FIRED)) {
                    try {
                        stopTimer(true); // clear the reference and prevent future timers.
                    } finally {
                        // Concurrent/multiple termination is protected by ConcurrentTerminalSubscriber.
                        offloadTimeout(new TimeoutException("no demand timeout after " +
                                NANOSECONDS.toMillis(parent.durationNs) + "ms"), parent.timeoutExecutor);
                    }
                    break;
                }
            }
        }

        private void startTimer() {
            for (;;) {
                final Cancellable cancellable = timerCancellable;
                if (cancellable == LOCAL_IGNORE_CANCEL) {
                    break;
                }
                final Cancellable nextTimer = parent.timeoutExecutor.schedule(this::timerFires, parent.durationNs,
                        NANOSECONDS);
                if (timerCancellableUpdater.compareAndSet(this, cancellable, nextTimer)) {
                    assert cancellable == null;
                    // We don't atomically manipulate the `demand` and set the timer so we can get the following race:
                    // Thread1: `.onNext(..)` call decrements demand to 0 and enters the top of `startTimer()`, pauses.
                    // Thread2: `.request(n > 0)`, increments demand from 0 -> n, enters `stopTimer(false)` and replaces
                    // `null` with `null`.
                    // Thread1: wakes and proceeds to successfully `startTimer()` despite demand being n > 0.
                    // If we see demand > 0 here we know it is safe to stop the timer because this method is only called
                    // on the Subscriber thread (no concurrency allowed), otherwise we let the timer stand.
                    for (;;) {
                        final long currDemand = demand;
                        if (currDemand > 0) {
                            nextTimer.cancel();
                            // Try to reset the timerCancellableUpdater to the original state. If the CAS fails, no need
                            // to loop because the only other state is LOCAL_IGNORE_CANCEL which is a terminal state.
                            timerCancellableUpdater.compareAndSet(this, nextTimer, null);
                            break;
                        } else if (demandUpdater.compareAndSet(this, currDemand, currDemand)) {
                            break;
                        }
                    }
                    break;
                } else {
                    nextTimer.cancel();
                }
            }
        }

        @Override
        void stopTimer(boolean terminal) {
            for (;;) {
                final Cancellable cancellable = timerCancellable;
                if (cancellable == LOCAL_IGNORE_CANCEL) {
                    break;
                } else if (timerCancellableUpdater.compareAndSet(this, cancellable,
                        terminal ? LOCAL_IGNORE_CANCEL : null)) {
                    if (cancellable != null) {
                        cancellable.cancel();
                    }
                    break;
                }
            }
        }
    }
}
