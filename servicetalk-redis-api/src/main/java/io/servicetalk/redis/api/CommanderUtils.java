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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SingleProcessor;
import io.servicetalk.concurrent.internal.SequentialCancellable;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.redis.api.RedisData.QUEUED;

final class CommanderUtils {

    static final int STATE_PENDING = 0;
    static final int STATE_EXECED = 1;
    static final int STATE_DISCARDED = 2;

    private static final String QUEUED_RESP = QUEUED.getValue().toString();

    private CommanderUtils() {
        // no instances
    }

    @Nonnull
    static <T> Future<T> enqueueForExecute(final int state, final List<SingleProcessor<?>> singles,
                                           final Single<String> queued) {
        if (state != STATE_PENDING) {
            throw new IllegalTransactionStateException(
                    Single.class.getSimpleName() + " cannot be subscribed to after the transaction has completed.");
        }
        // singles is always a field from a Commander, so this is safe.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (singles) {
            final SingleProcessor<T> single = new SingleProcessor<>();
            singles.add(single);
            return queued.flatMap(status -> {
                if (QUEUED_RESP.equals(status)) {
                    return single;
                }
                return Single.error(new RedisClientException("Read '" + status + "' but expected 'QUEUED'"));
            }).toFuture();
        }
    }

    static Single<String> abortSingles(final Single<String> result, final List<SingleProcessor<?>> singles) {
        return result.doBeforeSuccess(list -> {
            final int size = singles.size();
            for (int i = 0; i < size; ++i) {
                singles.get(i).onError(new TransactionAbortedException());
            }
        });
    }

    static Completable completeSingles(final Single<List<Object>> result, final List<SingleProcessor<?>> singles) {
        return result.doBeforeSuccess(list -> {
            if (singles.size() != list.size()) {
                throw new IllegalStateException("Result list size (" + list.size()
                        + ") and SingleProcessor list size (" + singles.size() + ") did not match");
            }
            final int size = list.size();
            for (int i = 0; i < size; ++i) {
                Object obj = list.get(i);
                SingleProcessor<?> single = singles.get(i);
                if (obj instanceof Throwable) {
                    single.onError((Throwable) obj);
                } else {
                    onSuccessUnchecked(obj, single);
                }
            }
        }).ignoreResult();
    }

    @SuppressWarnings("unchecked")
    private static void onSuccessUnchecked(final Object obj, final SingleProcessor single) {
        single.onSuccess(obj);
    }

    static final class DiscardSingle<T> extends Single<String> {

        private final T commander;
        private final Single<String> queued;
        private final List<SingleProcessor<?>> singles;
        @SuppressWarnings("AtomicFieldUpdaterNotStaticFinal")
        private final AtomicIntegerFieldUpdater<T> stateUpdater;
        private final SequentialCancellable sequentialCancellable = new SequentialCancellable();

        DiscardSingle(final T commander, final Single<String> queued, final List<SingleProcessor<?>> singles,
                      final AtomicIntegerFieldUpdater<T> stateUpdater) {
            this.commander = commander;
            this.queued = queued;
            this.singles = singles;
            this.stateUpdater = stateUpdater;
        }

        @Override
        protected void handleSubscribe(final Subscriber<? super String> subscriber) {
            if (!stateUpdater.compareAndSet(commander, STATE_PENDING, STATE_DISCARDED)) {
                subscriber.onSubscribe(sequentialCancellable);
                subscriber.onError(new IllegalStateException("Only one subscriber allowed."));
                return;
            }
            abortSingles(queued, singles).subscribe(new Subscriber<String>() {
                @Override
                public void onSubscribe(final Cancellable cancellable) {
                    subscriber.onSubscribe(cancellable);
                    sequentialCancellable.setNextCancellable(cancellable);
                }

                @Override
                public void onSuccess(@Nullable final String result) {
                    subscriber.onSuccess(result);
                }

                @Override
                public void onError(final Throwable t) {
                    subscriber.onError(t);
                }
            });
        }
    }

    static final class ExecCompletable<T> extends Completable {

        private final T commander;
        private final Single<List<Object>> queued;
        private final List<SingleProcessor<?>> singles;
        @SuppressWarnings("AtomicFieldUpdaterNotStaticFinal")
        private final AtomicIntegerFieldUpdater<T> stateUpdater;
        private final SequentialCancellable sequentialCancellable = new SequentialCancellable();

        ExecCompletable(final T commander, final Single<List<Object>> queued, final List<SingleProcessor<?>> singles,
                        final AtomicIntegerFieldUpdater<T> stateUpdater) {
            this.commander = commander;
            this.queued = queued;
            this.singles = singles;
            this.stateUpdater = stateUpdater;
        }

        @Override
        protected void handleSubscribe(final Subscriber subscriber) {
            if (!stateUpdater.compareAndSet(commander, STATE_PENDING, STATE_EXECED)) {
                subscriber.onSubscribe(sequentialCancellable);
                subscriber.onError(new IllegalStateException("Only one subscriber allowed."));
                return;
            }
            completeSingles(queued, singles).subscribe(new Subscriber() {
                @Override
                public void onSubscribe(final Cancellable cancellable) {
                    subscriber.onSubscribe(cancellable);
                    sequentialCancellable.setNextCancellable(cancellable);
                }

                @Override
                public void onComplete() {
                    subscriber.onComplete();
                }

                @Override
                public void onError(final Throwable t) {
                    subscriber.onError(t);
                }
            });
        }
    }
}
