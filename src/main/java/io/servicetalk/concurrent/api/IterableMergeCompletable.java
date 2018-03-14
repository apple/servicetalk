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

import io.servicetalk.concurrent.api.MergeCompletable.FixedCountMergeSubscriber;

import java.util.Collection;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Completable} implementation for merging {@link Iterable}s of {@link Completable}s.
 */
final class IterableMergeCompletable extends Completable {
    private final Completable original;
    private final Iterable<? extends Completable> others;
    private final boolean delayError;

    /**
     * New instance.
     * @param delayError {@code true} to wait until all {@code others} complete before propagating an error.
     *                   {@code false} to fail fast and propagate an error on the first {@link Subscriber#onError(Throwable)} observed.
     * @param original {@link Completable} to merge with {@code others}.
     * @param others   {@link Completable}s to merge with {@code original}.
     */
    IterableMergeCompletable(boolean delayError, Completable original, Iterable<? extends Completable> others) {
        this.delayError = delayError;
        this.original = requireNonNull(original);
        this.others = requireNonNull(others);
    }

    @Override
    public void handleSubscribe(Subscriber completableSubscriber) {
        if (others instanceof Collection) {
            FixedCountMergeSubscriber subscriber = new MergeCompletable.FixedCountMergeSubscriber(completableSubscriber, 1 + ((Collection) others).size(), delayError);
            original.subscribe(subscriber);
            for (Completable itr : others) {
                itr.subscribe(subscriber);
            }
        } else {
            DynamicCountSubscriber subscriber = new DynamicCountSubscriber(completableSubscriber, delayError);
            original.subscribe(subscriber);
            int count = 1;
            for (Completable itr : others) {
                ++count;
                itr.subscribe(subscriber);
            }
            subscriber.setExpectedCount(count);
        }
    }

    private static final class DynamicCountSubscriber extends MergeCompletable.MergeSubscriber {
        private volatile int expectedCount;

        DynamicCountSubscriber(Subscriber subscriber, boolean delayError) {
            super(subscriber, delayError);
        }

        @Override
        boolean onTerminate() {
            return completedCountUpdater.incrementAndGet(this) == expectedCount;
        }

        @Override
        boolean isDone() {
            return completedCount == expectedCount;
        }

        void setExpectedCount(int count) {
            expectedCount = count;
            if (completedCount == count) {
                tryToCompleteSubscriber();
            }
        }
    }
}
