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

import static java.util.Objects.requireNonNull;

final class MergeCompletable extends AbstractMergeCompletableOperator<CompletableFixedCountMergeSubscriber> {
    private final Completable[] others;
    private final boolean delayError;

    private MergeCompletable(boolean delayError, Completable original, Completable... others) {
        super(original);
        this.delayError = delayError;
        this.others = requireNonNull(others);
    }

    static Completable newInstance(boolean delayError, Completable original, Completable... others) {
        return others.length == 0 ? original : others.length == 1 ?
                new MergeOneCompletable(delayError, original, others[0]) :
                new MergeCompletable(delayError, original, others);
    }

    @Override
    public CompletableFixedCountMergeSubscriber apply(final Subscriber subscriber) {
        return new CompletableFixedCountMergeSubscriber(subscriber, 1 + others.length, delayError);
    }

    @Override
    void doMerge(final CompletableFixedCountMergeSubscriber subscriber) {
        for (Completable itr : others) {
            itr.subscribeInternal(subscriber);
        }
    }
}
