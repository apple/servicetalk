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

import io.servicetalk.concurrent.CompletableSource.Subscriber;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Completable} implementation for merging {@link Completable}s.
 */
final class MergeCompletable extends AbstractMergeCompletableOperator {

    @Nullable
    private final Completable[] others;
    @Nullable
    private final Completable onlyOther;
    private final boolean delayError;

    /**
     * New instance.
     * @param delayError {@code true} to wait until all {@code others} complete before propagating an error.
     *                   {@code false} to fail fast and propagate an error on the first
     *                   {@link Subscriber#onError(Throwable)} observed.
     * @param original {@link Completable} to merge with {@code others}.
     * @param others {@link Completable}s to merge with {@code original}.
     */
    MergeCompletable(boolean delayError, Completable original, Executor executor, Completable... others) {
        super(original, executor);
        this.delayError = delayError;
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
    public MergeSubscriber apply(final Subscriber subscriber) {
        assert onlyOther != null || others != null;
        return new FixedCountMergeSubscriber(subscriber, 1 + (onlyOther == null ? others.length : 1),
                delayError);
    }

    @Override
    void doMerge(final MergeSubscriber subscriber) {
        if (onlyOther == null) {
            assert others != null;
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
}
