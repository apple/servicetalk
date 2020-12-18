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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

final class MergeCompletable extends AbstractMergeCompletableOperator {
    private final Completable[] others;
    private final boolean delayError;

    private MergeCompletable(boolean delayError, Completable original, Executor executor, Completable... others) {
        super(original, executor);
        this.delayError = delayError;
        this.others = requireNonNull(others);
    }

    static AbstractMergeCompletableOperator newInstance(boolean delayError, Completable original, Executor executor,
                                                        Completable... others) {
        return others.length == 1 ?
                new MergeOneCompletable(delayError, original, executor, others[0]) :
                new MergeCompletable(delayError, original, executor, others);
    }

    @Override
    public MergeSubscriber apply(final Subscriber subscriber) {
        return new FixedCountMergeSubscriber(subscriber, 1 + others.length, delayError);
    }

    @Override
    void doMerge(final MergeSubscriber subscriber) {
        for (Completable itr : others) {
            itr.subscribeInternal(subscriber);
        }
    }

    static final class FixedCountMergeSubscriber extends MergeSubscriber {
        private static final AtomicIntegerFieldUpdater<FixedCountMergeSubscriber> remainingCountUpdater =
                newUpdater(FixedCountMergeSubscriber.class, "remainingCount");
        private volatile int remainingCount;

        FixedCountMergeSubscriber(Subscriber subscriber, int expectedCount, boolean delayError) {
            super(subscriber, delayError);
            // Subscribe operation must happen before the Subscriber is terminated, so initialization will be visible
            // in onTerminate.
            this.remainingCount = expectedCount;
        }

        @Override
        boolean onTerminate() {
            return remainingCountUpdater.decrementAndGet(this) == 0;
        }
    }
}
