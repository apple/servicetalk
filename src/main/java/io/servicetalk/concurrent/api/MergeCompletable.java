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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Completable} implementation for merging {@link Completable}s.
 */
final class MergeCompletable extends Completable {
    private final Completable original;
    @Nullable private final Completable[] others;
    @Nullable private final Completable onlyOther;
    private final boolean delayError;

    /**
     * New instance.
     * @param delayError {@code true} to wait until all {@code others} complete before propagating an error.
     *                   {@code false} to fail fast and propagate an error on the first {@link Subscriber#onError(Throwable)} observed.
     * @param original {@link Completable} to merge with {@code others}.
     * @param others {@link Completable}s to merge with {@code original}.
     */
    MergeCompletable(boolean delayError, Completable original, Completable... others) {
        this.delayError = delayError;
        this.original = requireNonNull(original);
        switch (others.length) {
            case 0:
                throw new IllegalArgumentException("At least one Completable required to merge");
            case 1:
                onlyOther = requireNonNull(others[0]);
                this.others = null;
                break;
            default:
                this.others = others;
                onlyOther = null;
        }
    }

    @Override
    public void handleSubscribe(Subscriber completableSubscriber) {
        assert onlyOther != null || others != null;
        FixedCountMergeSubscriber subscriber = new FixedCountMergeSubscriber(completableSubscriber, 1 + (onlyOther == null ? others.length : 1), delayError);
        original.subscribe(subscriber);
        if (onlyOther == null) {
            for (Completable itr : others) {
                itr.subscribe(subscriber);
            }
        } else {
            onlyOther.subscribe(subscriber);
        }
    }

    static final class FixedCountMergeSubscriber extends MergeSubscriber {
        FixedCountMergeSubscriber(Subscriber subscriber, int completedCount, boolean delayError) {
            super(subscriber, completedCount, delayError);
        }

        @Override
        boolean onTerminate() {
            return completedCountUpdater.decrementAndGet(this) == 0;
        }

        @Override
        boolean isDone() {
            return completedCount == 0;
        }
    }

    abstract static class MergeSubscriber implements Subscriber {
        static final AtomicIntegerFieldUpdater<MergeSubscriber> completedCountUpdater =
                AtomicIntegerFieldUpdater.newUpdater(MergeSubscriber.class, "completedCount");
        private static final AtomicReferenceFieldUpdater<MergeSubscriber, Object> terminalNotificationUpdater =
                AtomicReferenceFieldUpdater.newUpdater(MergeSubscriber.class, Object.class, "terminalNotification");
        private static final Object ON_COMPLETE = new Object();

        @SuppressWarnings("unused")
        volatile int completedCount;
        @SuppressWarnings("unused")
        @Nullable
        private volatile Object terminalNotification;
        private final Subscriber subscriber;
        private final DynamicCompositeCancellable dynamicCancellable;
        private final boolean delayError;

        MergeSubscriber(Subscriber subscriber, boolean delayError) {
            this.subscriber = subscriber;
            this.delayError = delayError;
            dynamicCancellable = new QueueDynamicCompositeCancellable();
            subscriber.onSubscribe(dynamicCancellable);
        }

        MergeSubscriber(Subscriber subscriber, int completedCount, boolean delayError) {
            this.subscriber = subscriber;
            this.delayError = delayError;
            this.completedCount = completedCount;
            dynamicCancellable = new QueueDynamicCompositeCancellable();
            subscriber.onSubscribe(dynamicCancellable);
        }

        @Override
        public final void onSubscribe(Cancellable cancellable) {
            dynamicCancellable.add(cancellable);
        }

        @Override
        public final void onComplete() {
            if (onTerminate()) {
                tryToCompleteSubscriber();
            }
        }

        @Override
        public final void onError(Throwable t) {
            for (;;) {
                Object terminalNotification = this.terminalNotification;
                if (terminalNotification == null) {
                    if (terminalNotificationUpdater.compareAndSet(this, null, t)) {
                        break;
                    } else if (!delayError) {
                        // If we are not delaying error notification, and we fail to set the terminal event then that means we
                        // have already notified the subscriber and should return to avoid duplicate terminal notifications.
                        return;
                    }
                } else {
                    Throwable tmpT = (Throwable) terminalNotification;
                    tmpT.addSuppressed(t);
                    t = tmpT;
                    break;
                }
            }
            if (!delayError || onTerminate()) {
                onError0(t);
            }
        }

        final void tryToCompleteSubscriber() {
            if (terminalNotificationUpdater.compareAndSet(this, null, ON_COMPLETE)) {
                subscriber.onComplete();
            } else if (delayError) {
                Throwable t = (Throwable) terminalNotification;
                assert t != null;
                onError0(t);
            }
        }

        private void onError0(Throwable t) {
            // If we are delaying error, then everything must have terminated by now, and there is no need to cancel anything.
            // However if we are not delaying error then we should cancel all outstanding work.
            if (!delayError) {
                dynamicCancellable.cancel();
            }
            subscriber.onError(t);
        }

        /**
         * Called when a terminal event is received.
         * @return {@code true} if we are done processing after changing state due to receiving a terminal event.
         */
        abstract boolean onTerminate();

        /**
         * Determine if we are currently done.
         * @return {@code true} if we are currently done.
         */
        abstract boolean isDone();
    }
}
