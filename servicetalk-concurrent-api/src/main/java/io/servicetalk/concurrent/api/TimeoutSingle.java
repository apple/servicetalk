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
import io.servicetalk.context.api.ContextMap;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.PublishAndSubscribeOnSingles.deliverOnSubscribeAndOnError;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

final class TimeoutSingle<T> extends AbstractNoHandleSubscribeSingle<T> {
    private final Single<T> original;
    private final io.servicetalk.concurrent.Executor timeoutExecutor;
    private final long durationNs;

    TimeoutSingle(final Single<T> original,
                  final Duration duration,
                  final io.servicetalk.concurrent.Executor timeoutExecutor) {
        this.original = original;
        this.durationNs = duration.toNanos();
        this.timeoutExecutor = requireNonNull(timeoutExecutor);
    }

    TimeoutSingle(final Single<T> original,
                  final long duration,
                  final TimeUnit unit,
                  final io.servicetalk.concurrent.Executor timeoutExecutor) {
        this.original = original;
        this.durationNs = unit.toNanos(duration);
        this.timeoutExecutor = requireNonNull(timeoutExecutor);
    }

    @Override
    protected void handleSubscribe(final Subscriber<? super T> subscriber,
                                   final ContextMap contextMap, final AsyncContextProvider contextProvider) {
        original.delegateSubscribe(
                TimeoutSubscriber.newInstance(this, subscriber, contextMap, contextProvider),
                contextMap, contextProvider);
    }

    private static final class TimeoutSubscriber<X> implements Subscriber<X>, Cancellable, Runnable {
        /**
         * Create a local instance because the instance is used as part of the local state machine.
         */
        private static final Cancellable LOCAL_IGNORE_CANCEL = () -> { };
        private static final int STATE_ON_WAITING_FOR_SUBSCRIBE = 0;
        private static final int STATE_ON_SUBSCRIBE_DONE = 1;
        private static final int STATE_TIMED_OUT_ERROR = 2;
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<TimeoutSubscriber, Cancellable>
                cancellableUpdater = newUpdater(TimeoutSubscriber.class, Cancellable.class, "cancellable");
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<TimeoutSubscriber> subscriberStateUpdater =
                AtomicIntegerFieldUpdater.newUpdater(TimeoutSubscriber.class, "subscriberState");
        @Nullable
        private volatile Cancellable cancellable;
        private volatile int subscriberState;
        private final TimeoutSingle<X> parent;
        private final Subscriber<? super X> target;
        private final AsyncContextProvider contextProvider;
        @Nullable
        private Cancellable timerCancellable;

        private TimeoutSubscriber(TimeoutSingle<X> parent, Subscriber<? super X> target,
                                  AsyncContextProvider contextProvider) {
            this.parent = parent;
            this.target = target;
            this.contextProvider = contextProvider;
        }

        static <X> TimeoutSubscriber<X> newInstance(TimeoutSingle<X> parent, Subscriber<? super X> target,
                                                    ContextMap contextMap, AsyncContextProvider contextProvider) {
            TimeoutSubscriber<X> s = new TimeoutSubscriber<>(parent, target, contextProvider);
            Cancellable localTimerCancellable;
            try {
                // We rely upon the timeoutExecutor to save/restore the current context when notifying when the timer
                // fires. An alternative would be to also wrap the Subscriber to preserve the AsyncContext but that
                // would result in duplicate wrapping.
                // The only time this may cause issues if someone disables AsyncContext for the Executor and wants
                // it enabled for the Subscriber, however the user explicitly specifies the Executor with this operator
                // so they can wrap the Executor in this case.
                localTimerCancellable = requireNonNull(
                        parent.timeoutExecutor.schedule(s, parent.durationNs, NANOSECONDS));
            } catch (Throwable cause) {
                localTimerCancellable = IGNORE_CANCEL;
                // We must set this to ignore so there are no further interactions with Subscriber in the future.
                s.cancellable = LOCAL_IGNORE_CANCEL;
                deliverOnSubscribeAndOnError(target, contextMap, contextProvider, cause);
            }
            s.timerCancellable = localTimerCancellable;
            return s;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            if (cancellableUpdater.compareAndSet(this, null, cancellable)) {
                target.onSubscribe(this);

                if (!subscriberStateUpdater.compareAndSet(this, STATE_ON_WAITING_FOR_SUBSCRIBE,
                        STATE_ON_SUBSCRIBE_DONE)) {
                    // subscriberState will be STATE_TIMED_OUT_ERROR, but the timeout occurred while we were invoking
                    // onSubscribe so terminating the target is handed off to this thread.
                    target.onError(newTimeoutException());
                }
            } else {
                cancellable.cancel();
            }
        }

        @Override
        public void onSuccess(@Nullable final X result) {
            if (cancellableUpdater.getAndSet(this, LOCAL_IGNORE_CANCEL) != LOCAL_IGNORE_CANCEL) {
                try {
                    stopTimer();
                } finally {
                    target.onSuccess(result);
                }
            }
        }

        @Override
        public void onError(final Throwable t) {
            if (cancellableUpdater.getAndSet(this, LOCAL_IGNORE_CANCEL) != LOCAL_IGNORE_CANCEL) {
                try {
                    stopTimer();
                } finally {
                    target.onError(t);
                }
            }
        }

        @Override
        public void cancel() {
            Cancellable oldCancellable = cancellableUpdater.getAndSet(this, LOCAL_IGNORE_CANCEL);
            if (oldCancellable != LOCAL_IGNORE_CANCEL) {
                try {
                    stopTimer();
                } finally {
                    // oldCancellable can't be null here, because we don't give out this object to onSubscribe unless
                    // the cancellable is set.
                    oldCancellable.cancel();
                }
            }
        }

        @Override
        public void run() {
            Cancellable oldCancellable = cancellableUpdater.getAndSet(this, LOCAL_IGNORE_CANCEL);
            if (oldCancellable != LOCAL_IGNORE_CANCEL) {
                // The timeout may be running on a different Executor than the original async source. If that is the
                // case we should offload to the correct Executor.
                // We rely upon the timeout Executor to save/restore the context. so we just use
                // contextProvider.contextMap() here.
                final Subscriber<? super X> offloadedTarget = parent.timeoutExecutor == immediate() ? target :
                        contextProvider.wrapSingleSubscriber(target, contextProvider.context());
                // The timer is started before onSubscribe so the oldCancellable may actually be null at this time.
                if (oldCancellable != null) {
                    oldCancellable.cancel();

                    // We already know that this.onSubscribe was called because we have a valid Cancellable. We need to
                    // know that the call to target.onSubscribe completed so we don't interact with the Subscriber
                    // concurrently.
                    if (subscriberStateUpdater.getAndSet(this, STATE_TIMED_OUT_ERROR) == STATE_ON_SUBSCRIBE_DONE) {
                        offloadedTarget.onError(newTimeoutException());
                    }
                } else {
                    // If there is no Cancellable, that means this.onSubscribe wasn't called before the timeout. In this
                    // case there is no risk of concurrent invocation on target because we won't invoke
                    // target.onSubscribe from this.onSubscribe.
                    deliverErrorFromSource(offloadedTarget, newTimeoutException());
                }
            }
        }

        private TimeoutException newTimeoutException() {
            return new TimeoutException("timeout after " + NANOSECONDS.toMillis(parent.durationNs) + "ms");
        }

        private void stopTimer() {
            assert timerCancellable != null;
            timerCancellable.cancel();
        }
    }
}
