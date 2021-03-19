/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.ConcurrentTerminalSubscriber;
import io.servicetalk.concurrent.internal.SignalOffloader;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.PublishAndSubscribeOnPublishers.deliverOnSubscribeAndOnError;
import static io.servicetalk.concurrent.internal.EmptySubscriptions.EMPTY_SUBSCRIPTION;
import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

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
                     final Executor publisherExecutor,
                     final long duration,
                     final TimeUnit unit,
                     final boolean restartAtOnNext,
                     final io.servicetalk.concurrent.Executor timeoutExecutor) {
        super(publisherExecutor);
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
    void handleSubscribe(Subscriber<? super T> subscriber, SignalOffloader signalOffloader,
                         AsyncContextMap contextMap, AsyncContextProvider contextProvider) {
        original.delegateSubscribe(
                TimeoutSubscriber.newInstance(this, subscriber, signalOffloader, contextMap, contextProvider),
                signalOffloader, contextMap, contextProvider);
    }

    private static final class TimeoutSubscriber<X> implements Subscriber<X>, Subscription {
        /**
         * Create a local instance because the instance is used as part of the local state machine.
         */
        private static final Cancellable LOCAL_IGNORE_CANCEL = () -> { };
        /**
         * {@code null} is only used during initialization to account for the following condition:
         * Thread A: new TimeoutSubscriber(), schedule new timer (1)
         * Thread B: call timerFires(), schedule new timer (2)
         * Thread A: set timerCancellable to (1)
         * Thread B: fails to set the timer to (2) ... at this point we don't have a reference to the active timer!
         */
        private static final Cancellable TIMER_PROCESSING = () -> { };
        private static final Cancellable TIMER_FIRED = () -> { };
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<TimeoutSubscriber, Cancellable> timerCancellableUpdater =
                AtomicReferenceFieldUpdater.newUpdater(TimeoutSubscriber.class, Cancellable.class, "timerCancellable");
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<TimeoutSubscriber, Subscription> subscriptionUpdater =
                AtomicReferenceFieldUpdater.newUpdater(TimeoutSubscriber.class, Subscription.class, "subscription");
        private final TimeoutPublisher<X> parent;
        private final ConcurrentTerminalSubscriber<? super X> target;
        private final SignalOffloader signalOffloader;
        private final AsyncContextProvider contextProvider;
        @Nullable
        private volatile Subscription subscription;
        /**
         * <ul>
         * <li>{@code null} - initialization only seen in the constructor and potentially on the first timer fire</li>
         * <li>{@link #TIMER_PROCESSING} - the {@link #timerFires()} is processing a timeout fire</li>
         * <li>{@link #TIMER_FIRED} - the next timeout fired before the current {@link #timerFires()} method exited</li>
         * <li>{@link #LOCAL_IGNORE_CANCEL} - a timeout occurred or normal termination. we don't need a timer.</li>
         * </ul>
         */
        @Nullable
        private volatile Cancellable timerCancellable;
        /**
         * Absolute monotonic time of the last timer (re)start.
         */
        private volatile long lastStartNS;

        private TimeoutSubscriber(TimeoutPublisher<X> parent,
                                  Subscriber<? super X> target,
                                  SignalOffloader signalOffloader,
                                  AsyncContextProvider contextProvider) {
            this.parent = parent;
            this.target = new ConcurrentTerminalSubscriber<>(target);
            this.signalOffloader = signalOffloader;
            this.contextProvider = contextProvider;
        }

        static <X> TimeoutSubscriber<X> newInstance(TimeoutPublisher<X> parent,
                                                    Subscriber<? super X> target,
                                                    SignalOffloader signalOffloader,
                                                    AsyncContextMap contextMap,
                                                    AsyncContextProvider contextProvider) {
            TimeoutSubscriber<X> s = new TimeoutSubscriber<>(parent, target, signalOffloader, contextProvider);
            try {
                s.lastStartNS = System.nanoTime();
                // CAS is just in case the timer fired, the run method schedule a new timer before this thread is able
                // to set the initial timer value. in this case we don't want to overwrite the active timer.
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
                handleConstructorException(s, signalOffloader, contextMap, contextProvider, cause);
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
            if (parent.restartAtOnNext) {
                lastStartNS = System.nanoTime();
            }
            target.onNext(x);
        }

        @Override
        public void onError(final Throwable t) {
            if (target.processOnError(t)) {
                stopTimer();
            }
        }

        @Override
        public void onComplete() {
            if (target.processOnComplete()) {
                stopTimer();
            }
        }

        @Override
        public void request(final long n) {
            final Subscription subscription = this.subscription;
            assert subscription != null;
            subscription.request(n);
        }

        @Override
        public void cancel() {
            final Subscription subscription = this.subscription;
            assert subscription != null;
            try {
                stopTimer();
            } finally {
                subscription.cancel();
            }
        }

        private void timerFires() {
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
                final long nextTimeoutNs = parent.durationNs - (System.nanoTime() - lastStartNS);
                if (nextTimeoutNs <= 0) { // Timeout!
                    offloadTimeout(new TimeoutException("timeout after " + NANOSECONDS.toMillis(parent.durationNs) +
                            "ms"));
                    return;
                } else {
                    final Cancellable nextTimerCancellable;
                    try {
                        nextTimerCancellable = requireNonNull(
                                parent.timeoutExecutor.schedule(this::timerFires, nextTimeoutNs, NANOSECONDS));
                    } catch (Throwable cause) {
                        offloadTimeout(cause);
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
                                // the run method executed before the constructor set the initial value.
                                return;
                            }
                        }
                    }
                }
            }
        }

        private void offloadTimeout(Throwable cause) {
            if (parent.executor() == parent.timeoutExecutor) {
                processTimeout(cause);
            } else {
                // We rely upon the timeout Executor to save/restore the context. so we just use
                // contextProvider.contextMap() here.
                signalOffloader.offloadSignal(cause, contextProvider.wrapConsumer(this::processTimeout,
                        contextProvider.contextMap()));
            }
        }

        private void processTimeout(Throwable cause) {
            final Subscription subscription = subscriptionUpdater.getAndSet(this, EMPTY_SUBSCRIPTION);
            // The timer is started before onSubscribe so the subscription may actually be null at this time.
            if (subscription != null) {
                subscription.cancel();
                // onErrorFromTimeout will protect against concurrent access on the Subscriber.
            } else {
                target.onSubscribe(EMPTY_SUBSCRIPTION);
            }
            target.processOnError(cause);
        }

        private void stopTimer() {
            // timerCancellable is known not to be null here based upon the current usage of this method.
            timerCancellableUpdater.getAndSet(this, LOCAL_IGNORE_CANCEL).cancel();
        }

        /**
         * This is unlikely to occur, so we extract the code into a private method.
         * @param cause The exception.
         */
        private static <X> void handleConstructorException(TimeoutSubscriber<X> s, SignalOffloader offloader,
                                                           AsyncContextMap contextMap,
                                                           AsyncContextProvider contextProvider, Throwable cause) {
            // We must set local state so there are no further interactions with Subscriber in the future.
            s.timerCancellable = LOCAL_IGNORE_CANCEL;
            s.subscription = EMPTY_SUBSCRIPTION;
            deliverOnSubscribeAndOnError(s.target, offloader, contextMap, contextProvider, cause);
        }
    }
}
