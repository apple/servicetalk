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
final class IterableMergeCompletable extends AbstractMergeCompletableOperator {
    private final Iterable<? extends Completable> others;
    private final boolean delayError;

    IterableMergeCompletable(boolean delayError, Completable original, Iterable<? extends Completable> others,
                             Executor executor) {
        super(original, executor);
        this.delayError = delayError;
        this.others = requireNonNull(others);
    }

    @Override
    public MergeSubscriber apply(final Subscriber subscriber) {
        if (others instanceof Collection) {
            return new FixedCountMergeSubscriber(subscriber, 1 + ((Collection) others).size(), delayError);
        } else {
            return new DynamicCountSubscriber(subscriber, delayError);
        }
    }

    @Override
    void doMerge(final MergeSubscriber subscriber) {
        if (subscriber instanceof DynamicCountSubscriber) {
            int count = 1;
            for (Completable itr : others) {
                ++count;
                itr.subscribe(subscriber);
            }
            ((DynamicCountSubscriber) subscriber).setExpectedCount(count);
        } else {
            for (Completable itr : others) {
                itr.subscribe(subscriber);
            }
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
