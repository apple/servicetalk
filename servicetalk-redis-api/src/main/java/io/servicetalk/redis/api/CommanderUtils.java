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

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nonnull;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.redis.api.RedisData.QUEUED;
import static java.util.Objects.requireNonNull;

final class CommanderUtils {

    static final int STATE_PENDING = 0;
    static final int STATE_EXECED = 1;
    static final int STATE_DISCARDED = 2;

    public static final CharSequence QUEUED_RESP = QUEUED.getValue();

    private CommanderUtils() {
        // no instances
    }

    @Nonnull
    static <T> Future<T> enqueueForExecute(final int state, final List<SingleProcessor> singles,
                                           final Single<String> queued) {
        if (state != STATE_PENDING) {
            throw new IllegalTransactionStateException(
                    Single.class.getSimpleName() + " cannot be subscribed to after the transaction has completed.");
        }
        return queued.flatMap(status -> {
            if (QUEUED_RESP.equals(status)) {
                        final SingleProcessor<T> single = new SingleProcessor<>();
                        singles.add(single);
                        return single;
                    }
                    return Single.error(new RedisClientException("Read '" + status + "' but expected 'QUEUED'"));
                }).toFuture();
    }

    static Single<String> abortSingles(final Single<String> result, final List<SingleProcessor> singles) {
        return result.doBeforeSuccess(list -> {
            for (int i = 0; i < singles.size(); ++i) {
                singles.get(i).onError(new TransactionAbortedException());
            }
        });
    }

    static Completable completeSingles(final Single<List<Object>> result, final List<SingleProcessor> singles) {
        return result.doBeforeSuccess(list -> {
            int index = 0;
            for (Object obj : requireNonNull(list)) {
                SingleProcessor single = singles.get(index);
                if (obj instanceof Throwable) {
                    single.onError((Throwable) obj);
                } else {
                    onSuccessUnchecked(obj, single);
                }
                index++;
            }
        }).ignoreResult();
    }

    @SuppressWarnings("unchecked")
    private static void onSuccessUnchecked(final Object obj, final SingleProcessor single) {
        single.onSuccess(obj);
    }

    static class DiscardSingle<T> extends Single<String> {

        private final T commander;
        private final Single<String> queued;
        private final List<SingleProcessor> singles;
        @SuppressWarnings("AtomicFieldUpdaterNotStaticFinal")
        private final AtomicIntegerFieldUpdater<T> stateUpdater;

        DiscardSingle(final T commander, final Single<String> queued, final List<SingleProcessor> singles, final AtomicIntegerFieldUpdater<T> stateUpdater) {
            this.commander = commander;
            this.queued = queued;
            this.singles = singles;
            this.stateUpdater = stateUpdater;
        }

        @Override
        protected void handleSubscribe(final Subscriber<? super String> subscriber) {
            if (!stateUpdater.compareAndSet(commander, STATE_PENDING, STATE_DISCARDED)) {
                subscriber.onSubscribe(IGNORE_CANCEL);
                subscriber.onError(new IllegalStateException("Only one subscriber allowed."));
                return;
            }
            abortSingles(queued, singles).subscribe(subscriber);
        }
    }

    static class ExecCompletable<T> extends Completable {

        private final T commander;
        private final Single<List<Object>> queued;
        private final List<SingleProcessor> singles;
        @SuppressWarnings("AtomicFieldUpdaterNotStaticFinal")
        private final AtomicIntegerFieldUpdater<T> stateUpdater;

        ExecCompletable(final T commander, final Single<List<Object>> queued, final List<SingleProcessor> singles, final AtomicIntegerFieldUpdater<T> stateUpdater) {
            this.commander = commander;
            this.queued = queued;
            this.singles = singles;
            this.stateUpdater = stateUpdater;
        }

        @Override
        protected void handleSubscribe(final Subscriber subscriber) {
            if (!stateUpdater.compareAndSet(commander, STATE_PENDING, STATE_EXECED)) {
                subscriber.onSubscribe(IGNORE_CANCEL);
                subscriber.onError(new IllegalStateException("Only one subscriber allowed."));
                return;
            }
            completeSingles(queued, singles).subscribe(subscriber);
        }
    }
}
