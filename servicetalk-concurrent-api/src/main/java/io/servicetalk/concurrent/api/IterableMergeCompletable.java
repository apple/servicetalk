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

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

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
        return new DynamicCountSubscriber(subscriber, delayError);
    }

    @Override
    void doMerge(final MergeSubscriber subscriber) {
        long count = 1;
        for (Completable itr : others) {
            ++count;
            itr.subscribeInternal(subscriber);
        }
        ((DynamicCountSubscriber) subscriber).setExpectedCount(count);
    }

    static final class DynamicCountSubscriber extends MergeSubscriber {
        private static final AtomicLongFieldUpdater<DynamicCountSubscriber> completedCountUpdater =
                AtomicLongFieldUpdater.newUpdater(DynamicCountSubscriber.class, "completedCount");
        private volatile long completedCount;

        DynamicCountSubscriber(Subscriber subscriber, boolean delayError) {
            super(subscriber, delayError);
        }

        @Override
        boolean onTerminate() {
            // we will go negative until setExpectedCount is called, but then we will offset by the expected amount.
            return completedCountUpdater.decrementAndGet(this) == 0;
        }

        void setExpectedCount(final long count) {
            // add the expected amount back, if we come to 0 that means all sources have completed.
            if (completedCountUpdater.addAndGet(this, count) == 0) {
                tryToCompleteSubscriber();
            }
        }
    }
}
