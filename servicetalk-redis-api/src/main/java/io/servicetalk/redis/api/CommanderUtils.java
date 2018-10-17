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
import io.servicetalk.concurrent.api.internal.SingleProcessor;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.redis.api.RedisClient.ReservedRedisConnection;

import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.internal.ThrowableUtil.unknownStackTrace;
import static io.servicetalk.redis.api.RedisData.QUEUED;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

final class CommanderUtils {

    static final int STATE_PENDING = 0;
    static final int STATE_EXECUTED = 1;
    static final int STATE_DISCARDED = 2;

    private static final String QUEUED_RESP = QUEUED.getValue().toString();

    private CommanderUtils() {
        // no instances
    }

    @Nonnull
    static <T> Future<T> enqueueForExecute(final int state, final List<SingleProcessor<?>> singles,
                                           final Single<String> queued) {
        // `state` is volatile in the transacted Redis commanders that call this, but we don't expect them to be used
        // to add commands concurrently with exec()/discard() so we don't need to access `state` atomically here.
        if (state != STATE_PENDING) {
            throw new IllegalTransactionStateException(
                    Single.class.getSimpleName() + " cannot be subscribed to after the transaction has completed.");
        }
        // Currently, we do not offload subscribes for `connection.request()` hence the order of subscribes is the
        // order of writes, so using `toFuture()` is sufficient. When we have the ability to override `Executor` per
        // request, we will use that to make sure subscribes are not offloaded.
        final SingleProcessor<T> single = new SingleProcessor<>();
        singles.add(single);
        return queued.flatMap(status -> {
            if (QUEUED_RESP.equals(status)) {
                return single;
            }
            return Single.error(new RedisClientException("Read '" + status + "' but expected 'QUEUED'"));
        }).toFuture();
    }

    static Single<String> abortSingles(final Single<String> result, final List<SingleProcessor<?>> singles) {
        return result.doBeforeSuccess(__ -> {
            for (SingleProcessor<?> single : singles) {
                single.onError(new TransactionAbortedException());
            }
        });
    }

    static Completable completeSingles(final Single<List<Object>> results, final List<SingleProcessor<?>> singles) {
        return results.doBeforeSuccess(resultList -> {
            if (singles.size() != resultList.size()) {
                throw new IllegalStateException("Result resultList size (" + resultList.size()
                        + ") and SingleProcessor resultList size (" + singles.size() + ") did not match");
            }
            final int size = resultList.size();
            for (int i = 0; i < size; ++i) {
                Object obj = resultList.get(i);
                SingleProcessor<?> single = singles.get(i);
                if (obj instanceof Throwable) {
                    // If a Redis command returns an error from the server (eg. if the stored type is invalid for the
                    // attempted command) this is translated into an `Exception`.
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

        private static final ClosedChannelException CANCELLATION_EXCEPTION =
                unknownStackTrace(new ClosedChannelException(),
                        DiscardSingle.CloseOnCancelCancellable.class, "cancel");
        private static final AtomicIntegerFieldUpdater<DiscardSingle> terminatedUpdater =
                newUpdater(DiscardSingle.class, "terminated");

        private final T commander;
        private final Single<String> queued;
        private final List<SingleProcessor<?>> singles;
        @SuppressWarnings("AtomicFieldUpdaterNotStaticFinal")
        private final AtomicIntegerFieldUpdater<T> stateUpdater;
        private final ReservedRedisConnection reservedCnx;
        private final boolean releaseAfterDone;

        @SuppressWarnings("unused")
        private volatile int terminated;

        DiscardSingle(final T commander, final Single<String> queued, final List<SingleProcessor<?>> singles,
                      final AtomicIntegerFieldUpdater<T> stateUpdater, final ReservedRedisConnection reservedCnx,
                      final boolean releaseAfterDone) {
            this.commander = commander;
            this.queued = queued;
            this.singles = singles;
            this.stateUpdater = stateUpdater;
            this.reservedCnx = reservedCnx;
            this.releaseAfterDone = releaseAfterDone;
        }

        @Override
        protected void handleSubscribe(final Subscriber<? super String> subscriber) {
            if (!stateUpdater.compareAndSet(commander, STATE_PENDING, STATE_DISCARDED)) {
                subscriber.onSubscribe(IGNORE_CANCEL);
                subscriber.onError(new DuplicateSubscribeException(stateUpdater.get(commander), subscriber));
                return;
            }
            abortSingles(queued, singles).subscribe(new Subscriber<String>() {
                @Override
                public void onSubscribe(final Cancellable cancellable) {
                    subscriber.onSubscribe(new CloseOnCancelCancellable(cancellable));
                }

                @Override
                public void onSuccess(@Nullable final String result) {
                    if (terminatedUpdater.compareAndSet(DiscardSingle.this, 0, 1)) {
                        if (releaseAfterDone) {
                            reservedCnx.releaseAsync().subscribe();
                        }
                        subscriber.onSuccess(result);
                    }
                }

                @Override
                public void onError(final Throwable t) {
                    if (terminatedUpdater.compareAndSet(DiscardSingle.this, 0, 1)) {
                        if (releaseAfterDone) {
                            reservedCnx.releaseAsync().subscribe();
                        }
                        subscriber.onError(t);
                    }
                }
            });
        }

        private final class CloseOnCancelCancellable implements Cancellable {
            private final Cancellable cancellable;

            private CloseOnCancelCancellable(final Cancellable cancellable) {
                this.cancellable = cancellable;
            }

            @Override
            public void cancel() {
                if (terminatedUpdater.compareAndSet(DiscardSingle.this, 0, 1)) {
                    cancellable.cancel();
                    reservedCnx.closeAsync().doAfterComplete(() -> {
                        for (SingleProcessor<?> single : singles) {
                            single.onError(CANCELLATION_EXCEPTION);
                        }
                    }).subscribe();
                }
            }
        }
    }

    static final class ExecCompletable<T> extends Completable {

        private static final ClosedChannelException CANCELLATION_EXCEPTION =
                unknownStackTrace(new ClosedChannelException(),
                        ExecCompletable.CloseOnCancelCancellable.class, "cancel");
        private static final AtomicIntegerFieldUpdater<ExecCompletable> terminatedUpdater =
                newUpdater(ExecCompletable.class, "terminated");

        private final T commander;
        private final Single<List<Object>> results;
        private final List<SingleProcessor<?>> singles;
        @SuppressWarnings("AtomicFieldUpdaterNotStaticFinal")
        private final AtomicIntegerFieldUpdater<T> stateUpdater;
        private final ReservedRedisConnection reservedCnx;
        private final boolean releaseAfterDone;

        @SuppressWarnings("unused")
        private volatile int terminated;

        ExecCompletable(final T commander, final Single<List<Object>> results, final List<SingleProcessor<?>> singles,
                        final AtomicIntegerFieldUpdater<T> stateUpdater, final ReservedRedisConnection reservedCnx,
                        final boolean releaseAfterDone) {
            this.commander = commander;
            this.results = results;
            this.singles = singles;
            this.stateUpdater = stateUpdater;
            this.reservedCnx = reservedCnx;
            this.releaseAfterDone = releaseAfterDone;
        }

        @Override
        protected void handleSubscribe(final Subscriber subscriber) {
            if (!stateUpdater.compareAndSet(commander, STATE_PENDING, STATE_EXECUTED)) {
                subscriber.onSubscribe(IGNORE_CANCEL);
                subscriber.onError(new DuplicateSubscribeException(stateUpdater.get(commander), subscriber));
                return;
            }

            completeSingles(results, singles).subscribe(new Subscriber() {
                @Override
                public void onSubscribe(final Cancellable cancellable) {
                    subscriber.onSubscribe(new CloseOnCancelCancellable(cancellable));
                }

                @Override
                public void onComplete() {
                    if (terminatedUpdater.compareAndSet(ExecCompletable.this, 0, 1)) {
                        if (releaseAfterDone) {
                            reservedCnx.releaseAsync().subscribe();
                        }
                        subscriber.onComplete();
                    }
                }

                @Override
                public void onError(final Throwable t) {
                    if (terminatedUpdater.compareAndSet(ExecCompletable.this, 0, 1)) {
                        if (releaseAfterDone) {
                            reservedCnx.releaseAsync().subscribe();
                        }
                        subscriber.onError(t);
                    }
                }
            });
        }

        private final class CloseOnCancelCancellable implements Cancellable {
            private final Cancellable cancellable;

            private CloseOnCancelCancellable(final Cancellable cancellable) {
                this.cancellable = cancellable;
            }

            @Override
            public void cancel() {
                if (terminatedUpdater.compareAndSet(ExecCompletable.this, 0, 1)) {
                    cancellable.cancel();
                    reservedCnx.closeAsync().doAfterComplete(() -> {
                        for (SingleProcessor<?> single : singles) {
                            single.onError(CANCELLATION_EXCEPTION);
                        }
                    }).subscribe();
                }
            }
        }
    }
}
