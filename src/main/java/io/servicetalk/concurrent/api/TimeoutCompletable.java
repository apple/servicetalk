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

import io.servicetalk.concurrent.Cancellable;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

final class TimeoutCompletable extends Completable {
    private final Completable original;
    private final Executor timeoutExecutor;
    private final long durationNs;

    TimeoutCompletable(final Completable original,
                       final Duration duration,
                       final Executor timeoutExecutor) {
        this.original = original;
        this.durationNs = duration.toNanos();
        this.timeoutExecutor = requireNonNull(timeoutExecutor);
    }

    TimeoutCompletable(final Completable original,
                       final long duration,
                       final TimeUnit unit,
                       final Executor timeoutExecutor) {
        this.original = original;
        this.durationNs = unit.toNanos(duration);
        this.timeoutExecutor = requireNonNull(timeoutExecutor);
    }

    @Override
    protected void handleSubscribe(final Subscriber subscriber) {
        original.subscribe(new TimeoutSubscriber(this, subscriber));
    }

    private static final class TimeoutSubscriber implements Subscriber, Cancellable, Runnable {
        /**
         * Create a local instance because the instance is used as part of the local state machine.
         */
        private static final Cancellable LOCAL_IGNORE_CANCEL = () -> { };
        private static final AtomicReferenceFieldUpdater<TimeoutSubscriber, Cancellable>
                cancellableUpdater = newUpdater(TimeoutSubscriber.class, Cancellable.class, "cancellable");
        private final Cancellable timerCancellable;
        @Nullable
        private volatile Cancellable cancellable;
        private final TimeoutCompletable parent;
        private final Subscriber target;

        TimeoutSubscriber(TimeoutCompletable parent, Subscriber target) {
            this.parent = parent;
            this.target = target;
            Cancellable localTimerCancellable;
            try {
                // Note that we let "this" escape the constructor. This is a trade-off done for the following reasons:
                // - it doesn't make semantic sense to use `timerCancellable` in `run()`
                // - the exposure of what can be used by the `Schedule` thread is controlled internally by this class
                // - timeouts maybe applied commonly and we can cut down on allocations which may be promoted into old
                // GC generations
                localTimerCancellable = requireNonNull(
                        parent.timeoutExecutor.schedule(this, parent.durationNs, NANOSECONDS));
            } catch (Throwable cause) {
                localTimerCancellable = IGNORE_CANCEL;
                // We must set this to ignore so there are no further interactions with Subscriber in the future.
                cancellable = LOCAL_IGNORE_CANCEL;
                target.onSubscribe(IGNORE_CANCEL);
                target.onError(cause);
            }
            timerCancellable = localTimerCancellable;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            if (cancellableUpdater.compareAndSet(this, null, cancellable)) {
                target.onSubscribe(this);
            } else {
                cancellable.cancel();
            }
        }

        @Override
        public void onComplete() {
            if (cancellableUpdater.getAndSet(this, LOCAL_IGNORE_CANCEL) != LOCAL_IGNORE_CANCEL) {
                timerCancellable.cancel();
                target.onComplete();
            }
        }

        @Override
        public void onError(final Throwable t) {
            if (cancellableUpdater.getAndSet(this, LOCAL_IGNORE_CANCEL) != LOCAL_IGNORE_CANCEL) {
                timerCancellable.cancel();
                target.onError(t);
            }
        }

        @Override
        public void cancel() {
            Cancellable oldCancellable = cancellableUpdater.getAndSet(this, LOCAL_IGNORE_CANCEL);
            if (oldCancellable != LOCAL_IGNORE_CANCEL) {
                // oldCancellable can't be null here, because we don't give out this object to onSubscribe unless the
                // cancellable is set.
                oldCancellable.cancel();
                timerCancellable.cancel();
            }
        }

        @Override
        public void run() {
            Cancellable oldCancellable = cancellableUpdater.getAndSet(this, LOCAL_IGNORE_CANCEL);
            if (oldCancellable != LOCAL_IGNORE_CANCEL) {
                // The timer is started before onSubscribe so the oldCancellable may actually be null at this time.
                if (oldCancellable != null) {
                    oldCancellable.cancel();
                } else {
                    target.onSubscribe(IGNORE_CANCEL);
                }
                target.onError(new TimeoutException("timeout after " + NANOSECONDS.toMillis(parent.durationNs) + "ms"));
            }
        }
    }
}
