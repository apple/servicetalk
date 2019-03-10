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
import io.servicetalk.concurrent.internal.SignalOffloader;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.PublishAndSubscribeOnPublishers.deliverOnSubscribeAndOnError;
import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.SubscriberUtils.SUBSCRIBER_STATE_TERMINATED;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkTerminationValidWithConcurrentOnNextCheck;
import static io.servicetalk.concurrent.internal.SubscriberUtils.sendOnNextWithConcurrentTerminationCheck;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static java.lang.Math.max;
import static java.lang.System.nanoTime;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class TimeoutPublisher<T> extends AbstractNoHandleSubscribePublisher<T> {
    private final Publisher<T> original;
    private final Executor timeoutExecutor;
    private final long durationNs;

    TimeoutPublisher(final Publisher<T> original,
                     final Executor executor,
                     final Duration duration) {
        this(original, executor, duration, executor);
    }

    TimeoutPublisher(final Publisher<T> original,
                     final Executor executor,
                     final long duration,
                     final TimeUnit unit) {
        this(original, executor, duration, unit, executor);
    }

    TimeoutPublisher(final Publisher<T> original,
                     final Executor publisherExecutor,
                     final Duration duration,
                     final Executor timeoutExecutor) {
        this(original, publisherExecutor, duration.toNanos(), timeoutExecutor);
    }

    TimeoutPublisher(final Publisher<T> original,
                     final Executor publisherExecutor,
                     final long duration,
                     final TimeUnit unit,
                     final Executor timeoutExecutor) {
        this(original, publisherExecutor, unit.toNanos(duration), timeoutExecutor);
    }

    private TimeoutPublisher(final Publisher<T> original,
                             final Executor publisherExecutor,
                             final long durationNs,
                             final Executor timeoutExecutor) {
        super(publisherExecutor);
        this.original = requireNonNull(original);
        this.timeoutExecutor = requireNonNull(timeoutExecutor);
        // We use the duration in arithmetic below to determine the expiration time for the "next timer" below. So
        // lets cap this at 0 to simplify overflow at that time.
        this.durationNs = max(0, durationNs);
    }

    @Override
    void handleSubscribe(Subscriber<? super T> subscriber, SignalOffloader signalOffloader,
                         AsyncContextMap contextMap, AsyncContextProvider contextProvider) {
        original.delegateSubscribe(
                TimeoutSubscriber.newInstance(this, subscriber, signalOffloader, contextMap, contextProvider),
                signalOffloader, contextMap, contextProvider);
    }

    private static final class TimeoutSubscriber<X> implements Subscriber<X>, Subscription, Runnable {
        /**
         * Create a local instance because the instance is used as part of the local state machine.
         */
        private static final Cancellable LOCAL_IGNORE_CANCEL = () -> { };
        /**
         * {@code null} is only used during initialization to account for the following condition:
         * Thread A: new TimeoutSubscriber(), schedule new timer (1)
         * Thread B: call run(), schedule new timer (2)
         * Thread A: set timerCancellable to (1)
         * Thread B: fails to set the timer to (2) ... at this point we don't have a reference to the active timer!
         */
        private static final Cancellable TIMER_PROCESSING = () -> { };
        private static final Cancellable TIMER_FIRED = () -> { };
        private static final AtomicReferenceFieldUpdater<TimeoutSubscriber, Cancellable> timerCancellableUpdater =
                AtomicReferenceFieldUpdater.newUpdater(TimeoutSubscriber.class, Cancellable.class, "timerCancellable");
        private static final AtomicReferenceFieldUpdater<TimeoutSubscriber, Subscription> subscriptionUpdater =
                AtomicReferenceFieldUpdater.newUpdater(TimeoutSubscriber.class, Subscription.class, "subscription");
        private static final AtomicIntegerFieldUpdater<TimeoutSubscriber> subscriberStateUpdater =
                AtomicIntegerFieldUpdater.newUpdater(TimeoutSubscriber.class, "subscriberState");
        private static final AtomicReferenceFieldUpdater<TimeoutSubscriber, Object> terminalNotificationUpdater =
                AtomicReferenceFieldUpdater.newUpdater(TimeoutSubscriber.class, Object.class, "terminalNotification");
        private final TimeoutPublisher<X> parent;
        private final Subscriber<? super X> target;
        private final SignalOffloader signalOffloader;
        private final AsyncContextProvider contextProvider;
        @Nullable
        private volatile Subscription subscription;
        private volatile int subscriberState;
        @SuppressWarnings("unused")
        @Nullable
        private volatile Object terminalNotification;
        /**
         * <ul>
         * <li>{@code null} - initialization only seen in the constructor and potentially on the first timer fire</li>
         * <li>{@link #TIMER_PROCESSING} - the {@link #run()} is processing a timeout fire</li>
         * <li>{@link #TIMER_FIRED} - the next timeout fired before the current {@link #run()} method exited</li>
         * <li>{@link #LOCAL_IGNORE_CANCEL} - a timeout occurred or normal termination. we don't need a timer.</li>
         * </ul>
         */
        @Nullable
        private volatile Cancellable timerCancellable;
        private volatile long lastOnNextNs;

        private TimeoutSubscriber(TimeoutPublisher<X> parent,
                                  Subscriber<? super X> target,
                                  SignalOffloader signalOffloader,
                                  AsyncContextProvider contextProvider) {
            this.parent = parent;
            this.target = target;
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
                s.lastOnNextNs = nanoTime();
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
                        parent.timeoutExecutor.schedule(s, parent.durationNs, NANOSECONDS)));
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
            lastOnNextNs = nanoTime();
            sendOnNextWithConcurrentTerminationCheck(target, x, this::terminate,
                    subscriberStateUpdater, terminalNotificationUpdater, this);
        }

        @Override
        public void onError(final Throwable t) {
            if (checkTerminationValidWithConcurrentOnNextCheck(null, t,
                    subscriberStateUpdater, terminalNotificationUpdater, this)) {
                try {
                    stopTimer();
                } finally {
                    target.onError(t);
                }
            }
        }

        @Override
        public void onComplete() {
            if (checkTerminationValidWithConcurrentOnNextCheck(null, complete(),
                    subscriberStateUpdater, terminalNotificationUpdater, this)) {
                try {
                    stopTimer();
                } finally {
                    target.onComplete();
                }
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

        @Override
        public void run() {
            // Reserve the timer processing for a single thread. There is only expected to be a single timer outstanding
            // at any give time, but because we reschedule the timer from within this method it is possible that another
            // timer will fire, and invoke this run() method "concurrently" before the first invocation of run() has
            // updated state as a result of the rescheduled timer.
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
                final long nextTimeoutNs = parent.durationNs - (nanoTime() - lastOnNextNs);
                if (nextTimeoutNs <= 0) { // Timeout!
                    offloadTimeout(new TimeoutException("timeout after " + NANOSECONDS.toMillis(parent.durationNs) +
                            "ms"));
                    return;
                } else {
                    final Cancellable nextTimerCancellable;
                    try {
                        nextTimerCancellable = requireNonNull(
                                parent.timeoutExecutor.schedule(this, nextTimeoutNs, NANOSECONDS));
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
            signalOffloader.offloadSignal(cause, contextProvider.wrapConsumer(this::processTimeout));
        }

        private void processTimeout(Throwable cause) {
            final Subscription subscription = subscriptionUpdater.getAndSet(this, EMPTY_SUBSCRIPTION);
            // The timer is started before onSubscribe so the subscription may actually be null at this time.
            if (subscription != null) {
                subscription.cancel();
            } else {
                target.onSubscribe(EMPTY_SUBSCRIPTION);
            }
            onError(cause); // call onError so we don't deliver any more elements to the Subscriber.
        }

        private void stopTimer() {
            // timerCancellable is known not to be null here based upon the current usage of this method.
            timerCancellableUpdater.getAndSet(this, LOCAL_IGNORE_CANCEL).cancel();
        }

        private void terminate(Object terminalNotification) {
            try {
                stopTimer();
            } finally {
                if (terminalNotification instanceof Throwable) {
                    target.onError((Throwable) terminalNotification);
                } else {
                    target.onComplete();
                }
            }
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
            s.subscriberState = SUBSCRIBER_STATE_TERMINATED;
            s.subscription = EMPTY_SUBSCRIPTION;
            deliverOnSubscribeAndOnError(s.target, offloader, contextMap, contextProvider, cause);
        }
    }
}
