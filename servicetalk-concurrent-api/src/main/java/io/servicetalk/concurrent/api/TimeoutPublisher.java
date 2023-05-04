/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.context.api.ContextMap;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.PublishAndSubscribeOnPublishers.deliverOnSubscribeAndOnError;
import static io.servicetalk.concurrent.internal.EmptySubscriptions.EMPTY_SUBSCRIPTION;
import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * Publisher which cancels the subscription after the specified duration if not completed. Two timer modes are offered:
 * <ul>
 *     <li>An absolute timeout which requires that the publisher receive all items and complete (or error)</li>
 *     <li>A mode which restarts the timer for each {@link Subscriber#onNext(Object) onNext} signal providing an "idle"
 *     timeout.</li>
 * </ul>
 *
 * <p/>For either timer mode the timer begins with the subscription.
 *
 * @param <T> Type of items
 */
final class TimeoutPublisher<T> extends AbstractNoHandleSubscribePublisher<T> {
    private final Publisher<T> original;
    private final io.servicetalk.concurrent.Executor timeoutExecutor;
    /**
     * non-negative nanoseconds until the timeout
     */
    private final long durationNs;
    /**
     * If true then restart the timeout at every {@link TimeoutSubscriber#onNext(Object)} otherwise start only at
     * subscribe
     */
    private final boolean restartAtOnNext;

    TimeoutPublisher(final Publisher<T> original,
                     final long duration,
                     final TimeUnit unit,
                     final boolean restartAtOnNext,
                     final Executor timeoutExecutor) {
        this.original = requireNonNull(original);
        this.timeoutExecutor = requireNonNull(timeoutExecutor);
        // We use the duration in arithmetic below to determine the expiration time for the "next timer" below. So
        // lets cap this at 0 to simplify overflow at that time. Negative duration is allowed as input as this
        // simplifies cases where the duration is calculated and an "already timed out" result is found. The caller
        // would otherwise have to generate the timeout exception themselves.
        this.durationNs = max(0, unit.toNanos(duration));
        this.restartAtOnNext = restartAtOnNext;
    }

    @Override
    void handleSubscribe(Subscriber<? super T> subscriber,
                         ContextMap contextMap, AsyncContextProvider contextProvider) {
        original.delegateSubscribe(
                TimeoutSubscriber.newInstance(this, subscriber, contextMap, contextProvider),
                contextMap, contextProvider);
    }

    abstract static class AbstractTimeoutSubscriber<X> implements Subscriber<X>, Subscription {
        /**
         * Create a local instance because the instance is used as part of the local state machine.
         */
        static final Cancellable LOCAL_IGNORE_CANCEL = () -> { };
        @SuppressWarnings("rawtypes")
        static final AtomicReferenceFieldUpdater<AbstractTimeoutSubscriber, Cancellable> timerCancellableUpdater =
                newUpdater(AbstractTimeoutSubscriber.class, Cancellable.class, "timerCancellable");
        @SuppressWarnings("rawtypes")
        static final AtomicReferenceFieldUpdater<AbstractTimeoutSubscriber, Subscription> subscriptionUpdater =
                newUpdater(AbstractTimeoutSubscriber.class, Subscription.class, "subscription");

        final ConcurrentTerminalSubscriber<? super X> target;
        final AsyncContextProvider contextProvider;
        @Nullable
        volatile Subscription subscription;
        @Nullable
        volatile Cancellable timerCancellable;

        AbstractTimeoutSubscriber(Subscriber<? super X> target, AsyncContextProvider contextProvider) {
            // Concurrent onSubscribe is protected by subscriptionUpdater, no need to double protect.
            this.target = new ConcurrentTerminalSubscriber<>(target, false);
            this.contextProvider = contextProvider;
        }

        final void initTimer(long durationNs, io.servicetalk.concurrent.Executor timeoutExecutor,
                             ContextMap contextMap) {
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
                timerCancellableUpdater.compareAndSet(this, null, requireNonNull(
                        timeoutExecutor.schedule(this::timerFires, durationNs, NANOSECONDS)));
            } catch (Throwable cause) {
                handleConstructorException(this, contextMap, contextProvider, cause);
            }
        }

        @Override
        public final void onSubscribe(final Subscription s) {
            if (subscriptionUpdater.compareAndSet(this, null, ConcurrentSubscription.wrap(s))) {
                target.onSubscribe(this);
            } else {
                s.cancel();
            }
        }

        @Override
        public final void onError(final Throwable t) {
            if (target.processOnError(t)) {
                stopTimer(true);
            }
        }

        @Override
        public final void onComplete() {
            if (target.processOnComplete()) {
                stopTimer(true);
            }
        }

        @Override
        public final void cancel() {
            final Subscription subscription = this.subscription;
            assert subscription != null;
            try {
                stopTimer(true);
            } finally {
                subscription.cancel();
            }
        }

        abstract void stopTimer(boolean terminal);

        abstract void timerFires();

        final void offloadTimeout(Throwable cause, io.servicetalk.concurrent.Executor executor) {
            if (immediate() == executor) {
                processTimeout(cause);
            } else {
                // We rely upon the timeout Executor to save/restore the context. so we just use
                // contextProvider.contextMap() here.
                contextProvider.wrapConsumer(this::processTimeout, contextProvider.context()).accept(cause);
            }
        }

        final void processTimeout(Throwable cause) {
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

        /**
         * This is unlikely to occur, so we extract the code into a private method.
         * @param cause The exception.
         */
        private static <X> void handleConstructorException(
                AbstractTimeoutSubscriber<X> s, ContextMap contextMap, AsyncContextProvider contextProvider,
                Throwable cause) {
            // We must set local state so there are no further interactions with Subscriber in the future.
            s.timerCancellable = LOCAL_IGNORE_CANCEL;
            s.subscription = EMPTY_SUBSCRIPTION;
            deliverOnSubscribeAndOnError(s.target, contextMap, contextProvider, cause);
        }
    }

    /**
     * States for {@link #timerCancellable}:
     * <ul>
     * <li>{@code null} - initialization only seen in the constructor and potentially on the first timer fire</li>
     * <li>{@link #TIMER_PROCESSING} - the {@link #timerFires()} is processing a timeout fire</li>
     * <li>{@link #TIMER_FIRED} - the next timeout fired before the current {@link #timerFires()} method exited</li>
     * <li>{@link #LOCAL_IGNORE_CANCEL} - a timeout occurred or normal termination. we don't need a timer.</li>
     * </ul>
     * @param <X> Type of subscriber.
     */
    private static final class TimeoutSubscriber<X> extends AbstractTimeoutSubscriber<X> {
        /**
         * {@code null} is only used during initialization to account for the following condition:
         * Thread A: new TimeoutSubscriber(), schedule new timer (1)
         * Thread B: call timerFires(), schedule new timer (2)
         * Thread A: set timerCancellable to (1)
         * Thread B: fails to set the timer to (2) ... at this point we don't have a reference to the active timer!
         */
        private static final Cancellable TIMER_PROCESSING = () -> { };
        private static final Cancellable TIMER_FIRED = () -> { };
        private final TimeoutPublisher<X> parent;
        /**
         * Absolute monotonic time of the last timer (re)start.
         */
        private volatile long lastStartNS;

        private TimeoutSubscriber(TimeoutPublisher<X> parent,
                                  Subscriber<? super X> target,
                                  AsyncContextProvider contextProvider) {
            super(target, contextProvider);
            this.parent = parent;
            lastStartNS = parent.timeoutExecutor.currentTime(NANOSECONDS);
        }

        static <X> TimeoutSubscriber<X> newInstance(TimeoutPublisher<X> parent,
                                                    Subscriber<? super X> target,
                                                    ContextMap contextMap,
                                                    AsyncContextProvider contextProvider) {
            TimeoutSubscriber<X> s = new TimeoutSubscriber<>(parent, target, contextProvider);
            s.initTimer(parent.durationNs, parent.timeoutExecutor, contextMap);
            return s;
        }

        @Override
        public void onNext(final X x) {
            if (parent.restartAtOnNext) {
                lastStartNS = parent.timeoutExecutor.currentTime(NANOSECONDS);
            }
            target.onNext(x);
        }

        @Override
        public void request(final long n) {
            final Subscription subscription = this.subscription;
            assert subscription != null;
            subscription.request(n);
        }

        @Override
        void timerFires() {
            // Reserve the timer processing for a single thread. There is only expected to be a single timer outstanding
            // at any give time, but because we reschedule the timer from within this method it is possible that another
            // timer will fire, and invoke this timerFires() method "concurrently" before the first invocation of
            // timerFires() has updated state as a result of the rescheduled timer.
            Cancellable previousTimerCancellable;
            for (;;) {
                previousTimerCancellable = timerCancellable;
                if (previousTimerCancellable == LOCAL_IGNORE_CANCEL || previousTimerCancellable == TIMER_FIRED) {
                    return;
                } else if (previousTimerCancellable == TIMER_PROCESSING) {
                    if (timerCancellableUpdater.compareAndSet(this, TIMER_PROCESSING, TIMER_FIRED)) {
                        return;
                    }
                } else if (timerCancellableUpdater.compareAndSet(this, previousTimerCancellable, TIMER_PROCESSING)) {
                    break;
                }
            }

            // Instead of recursion we use a 2 level for loop structure.
            for (;;) {
                final long currentTimeNs = parent.timeoutExecutor.currentTime(NANOSECONDS);
                final long nextTimeoutNs = parent.durationNs - (currentTimeNs - lastStartNS);
                if (nextTimeoutNs <= 0) { // Timeout!
                    offloadTimeout(new TimeoutException((parent.restartAtOnNext ?
                            "between onNext" : "until terminal") + " timeout after " +
                            NANOSECONDS.toMillis(parent.durationNs) + "ms"), parent.timeoutExecutor);
                    return;
                } else {
                    final Cancellable nextTimerCancellable;
                    try {
                        nextTimerCancellable = requireNonNull(
                                parent.timeoutExecutor.schedule(this::timerFires, nextTimeoutNs, NANOSECONDS),
                                () -> "Executor.schedule " + parent.timeoutExecutor + " returned null");
                    } catch (Throwable cause) {
                        offloadTimeout(cause, parent.timeoutExecutor);
                        return;
                    }

                    if (timerCancellableUpdater.compareAndSet(this, previousTimerCancellable, nextTimerCancellable)) {
                        return;
                    } else {
                        for (;;) {
                            previousTimerCancellable = timerCancellable;
                            if (previousTimerCancellable == LOCAL_IGNORE_CANCEL) {
                                nextTimerCancellable.cancel();
                                return;
                            } else if (previousTimerCancellable == TIMER_FIRED) {
                                if (timerCancellableUpdater.compareAndSet(this, TIMER_FIRED, TIMER_PROCESSING)) {
                                    // reset state and get ready for the next iteration of the outer loop.
                                    previousTimerCancellable = TIMER_PROCESSING;
                                    break;
                                }
                            } else if (timerCancellableUpdater.compareAndSet(this, previousTimerCancellable,
                                    nextTimerCancellable)) {
                                // This means that initialization sequence was such that the timer fired, and
                                // the timerFires method executed before the constructor set the initial value.
                                return;
                            }
                        }
                    }
                }
            }
        }

        @Override
        void stopTimer(boolean terminal) {
            // timerCancellable is known not to be null here based upon the current usage of this method.
            timerCancellableUpdater.getAndSet(this, LOCAL_IGNORE_CANCEL).cancel();
        }
    }
}
