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

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import static java.util.concurrent.atomic.AtomicLongFieldUpdater.newUpdater;

final class CompletableDynamicCountSubscriber extends CompletableMergeSubscriber {
    private static final AtomicLongFieldUpdater<CompletableDynamicCountSubscriber> completedCountUpdater =
            newUpdater(CompletableDynamicCountSubscriber.class, "completedCount");
    private volatile long completedCount;

    CompletableDynamicCountSubscriber(Subscriber subscriber, boolean delayError) {
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
