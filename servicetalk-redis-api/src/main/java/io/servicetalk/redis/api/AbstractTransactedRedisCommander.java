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
package io.servicetalk.redis.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SingleProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nonnull;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.redis.api.CommanderUtils.abortSingles;
import static io.servicetalk.redis.api.CommanderUtils.completeSingles;

abstract class AbstractTransactedRedisCommander extends TransactedRedisCommander {

    private boolean transactionCompleted;

    private final List<SingleProcessor> singles = new ArrayList<>();

    @Nonnull
    <T> Future<T> enqueueForExecute(final Single<String> queued) {
        return CommanderUtils.enqueueForExecute(this.transactionCompleted, this.singles, queued);
    }

    static class DiscardSingle extends Single<String> {
        private static final AtomicIntegerFieldUpdater<DiscardSingle> subscribedUpdater =
                AtomicIntegerFieldUpdater.newUpdater(DiscardSingle.class, "subscribed");
        @SuppressWarnings("unused")
        private volatile int subscribed;

        private final AbstractTransactedRedisCommander commander;
        final Single<String> queued;

        DiscardSingle(final AbstractTransactedRedisCommander commander, final Single<String> queued) {
            this.commander = commander;
            this.queued = queued;
        }

        @Override
        protected void handleSubscribe(final Subscriber<? super String> subscriber) {
            if (!subscribedUpdater.compareAndSet(this, 0, 1)) {
                subscriber.onSubscribe(IGNORE_CANCEL);
                subscriber.onError(new IllegalStateException("Only one subscriber allowed."));
                return;
            }
            abortSingles(queued.doAfterSubscribe(__ -> commander.transactionCompleted = true),
                    commander.singles).subscribe(subscriber);
        }
    }

    static class ExecCompletable extends Completable {

        private static final AtomicIntegerFieldUpdater<ExecCompletable> subscribedUpdater =
                AtomicIntegerFieldUpdater.newUpdater(ExecCompletable.class, "subscribed");
        @SuppressWarnings("unused")
        private volatile int subscribed;

        private final AbstractTransactedRedisCommander commander;
        private final Single<List<Object>> queued;

        ExecCompletable(final AbstractTransactedRedisCommander commander, final Single<List<Object>> queued) {
            this.commander = commander;
            this.queued = queued;
        }

        @Override
        protected void handleSubscribe(final Subscriber subscriber) {
            if (!subscribedUpdater.compareAndSet(this, 0, 1)) {
                subscriber.onSubscribe(IGNORE_CANCEL);
                subscriber.onError(new IllegalStateException("Only one subscriber allowed."));
                return;
            }
            completeSingles(queued.doAfterSubscribe(__ -> commander.transactionCompleted = true), commander.singles)
                    .subscribe(subscriber);
        }
    }
}
