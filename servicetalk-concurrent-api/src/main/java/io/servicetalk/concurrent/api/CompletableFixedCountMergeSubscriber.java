/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

final class CompletableFixedCountMergeSubscriber extends CompletableMergeSubscriber {
    private static final AtomicIntegerFieldUpdater<CompletableFixedCountMergeSubscriber> terminatedCountUpdater =
            newUpdater(CompletableFixedCountMergeSubscriber.class, "terminatedCount");
    private final int expectedCount;
    private volatile int terminatedCount;

    CompletableFixedCountMergeSubscriber(Subscriber subscriber, int expectedCount, boolean delayError) {
        super(subscriber, delayError);
        this.expectedCount = expectedCount;
    }

    @Override
    boolean onTerminate() {
        return terminatedCountUpdater.incrementAndGet(this) == expectedCount;
    }
}
