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
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.SingleSource.Processor;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Processors;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableCompletable;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.redis.api.RedisClient.ReservedRedisConnection;

import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.redis.api.RedisData.QUEUED;

final class CommanderUtils {

    static final int STATE_PENDING = 0;
    static final int STATE_EXECUTED = 1;
    static final int STATE_DISCARDED = 2;

    private static final String QUEUED_RESP = QUEUED.value().toString();

    private CommanderUtils() {
        // no instances
    }

    @Nonnull
    static <T> Future<T> enqueueForExecute(final int state, final List<Processor<?, ?>> singles,
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
        final Processor<T, T> single = Processors.newSingleProcessor();
        singles.add(single);
        return queued.flatMap(status -> {
            if (QUEUED_RESP.equals(status)) {
                return fromSource(single);
            }
            return Single.error(new RedisClientException("Read '" + status + "' but expected 'QUEUED'"));
        }).<T>liftSynchronous(sub -> new Subscriber<T>() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                // Allowing cancellation of commands within a transaction would require needing to know whether the
                // server has accepted the command, and disallowing the cancel if it had, since it can't truly
                // be cancelled then. This would be unpredictable, so instead we disallow cancel entirely.
                sub.onSubscribe(IGNORE_CANCEL);
            }

            @Override
            public void onSuccess(@Nullable final T result) {
                sub.onSuccess(result);
            }

            @Override
            public void onError(final Throwable t) {
                sub.onError(t);
            }
        }).toFuture();
    }

    static Single<String> abortSingles(final Single<String> result, final List<Processor<?, ?>> singles) {
        return result.doBeforeSuccess(__ -> completeSinglesWithException(new TransactionAbortedException(), singles));
    }

    static Completable completeSingles(final Single<List<Object>> results,
                                       final List<Processor<?, ?>> singles) {
        return results.doBeforeSuccess(resultList -> {
            if (singles.size() != resultList.size()) {
                throw new IllegalStateException("Result resultList size (" + resultList.size()
                        + ") and SingleProcessor resultList size (" + singles.size() + ") did not match");
            }
            final int size = resultList.size();
            for (int i = 0; i < size; ++i) {
                Object obj = resultList.get(i);
                Processor<?, ?> single = singles.get(i);
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

    private static void completeSinglesWithException(final Exception e,
                                                     final List<Processor<?, ?>> singles) {
        for (Processor<?, ?> single : singles) {
            single.onError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void onSuccessUnchecked(final Object obj, final Processor single) {
        single.onSuccess(obj);
    }

    private static void handleCancel(final ReservedRedisConnection reservedCnx,
                                     final List<Processor<?, ?>> singles,
                                     final String exceptionMessage) {
        toSource(reservedCnx.closeAsync()).subscribe(new CompletableSource.Subscriber() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                // no-op
            }

            @Override
            public void onComplete() {
                completeSinglesWithException(new ConnectionClosedException(exceptionMessage), singles);
            }

            @Override
            public void onError(final Throwable releaseThrowable) {
                final Exception e = new ConnectionClosedException(exceptionMessage);
                e.addSuppressed(releaseThrowable);
                completeSinglesWithException(e, singles);
            }
        });
    }

    static final class DiscardSingle<T> extends SubscribableSingle<String> {

        private final T commander;
        private final Single<String> queued;
        private final List<Processor<?, ?>> singles;
        @SuppressWarnings("AtomicFieldUpdaterNotStaticFinal")
        private final AtomicIntegerFieldUpdater<T> stateUpdater;
        private final ReservedRedisConnection reservedCnx;
        private final boolean releaseAfterDone;

        DiscardSingle(final T commander, final Single<String> queued, final List<Processor<?, ?>> singles,
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
            Single<String> discardSingle = abortSingles(queued, singles);
            if (releaseAfterDone) {
                discardSingle = discardSingle.recoverWith(discardThrowable -> reservedCnx.releaseAsync()
                        // If releaseAsync() fails then add as a suppressed exception.
                        .onErrorResume(releaseThrowable -> {
                            discardThrowable.addSuppressed(releaseThrowable);
                            return Completable.error(discardThrowable);
                        })
                        // If releaseAsync() completes successfully, emit the original error.
                        .concat(error(discardThrowable))
                ).concat(reservedCnx.releaseAsync()); // If discard succeeds, release the connection.
            }
            toSource(discardSingle.doAfterCancel(() -> handleCancel(reservedCnx, singles,
                    "Connection closed due to discard() cancellation.")))
                    .subscribe(subscriber);
        }
    }

    static final class ExecCompletable<T> extends SubscribableCompletable {

        private final T commander;
        private final Single<List<Object>> results;
        private final List<SingleSource.Processor<?, ?>> singles;
        @SuppressWarnings("AtomicFieldUpdaterNotStaticFinal")
        private final AtomicIntegerFieldUpdater<T> stateUpdater;
        private final ReservedRedisConnection reservedCnx;
        private final boolean releaseAfterDone;

        ExecCompletable(final T commander, final Single<List<Object>> results,
                        final List<SingleSource.Processor<?, ?>> singles,
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
        protected void handleSubscribe(final CompletableSource.Subscriber subscriber) {
            if (!stateUpdater.compareAndSet(commander, STATE_PENDING, STATE_EXECUTED)) {
                subscriber.onSubscribe(IGNORE_CANCEL);
                subscriber.onError(new DuplicateSubscribeException(stateUpdater.get(commander), subscriber));
                return;
            }

            Completable execCompletable = completeSingles(results, singles);
            if (releaseAfterDone) {
                execCompletable = execCompletable.onErrorResume(discardThrowable -> reservedCnx.releaseAsync()
                        // If releaseAsync() fails then add as a suppressed exception.
                        .onErrorResume(releaseThrowable -> {
                            discardThrowable.addSuppressed(releaseThrowable);
                            return error(discardThrowable);
                        })
                        // If releaseAsync() completes successfully, emit the original error.
                        .concat(error(discardThrowable))
                ).concat(reservedCnx.releaseAsync()); // If exec succeeds, release the connection.
            }
            toSource(execCompletable.doAfterCancel(() -> handleCancel(reservedCnx, singles,
                    "Connection closed due to exec() cancellation.")))
                    .subscribe(subscriber);
        }
    }

    private static final class ConnectionClosedException extends ClosedChannelException {
        private static final long serialVersionUID = 1620818457019576699L;

        private final String message;

        private ConnectionClosedException(final String message) {
            this.message = message;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }
}
