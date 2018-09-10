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
import javax.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

final class CommanderUtils {

    private CommanderUtils() {
        // no instances
    }

    @Nonnull
    static <T> Future<T> enqueueForExecute(final boolean transactionCompleted, final List<SingleProcessor> singles,
                                           final Single<String> queued) {
        if (transactionCompleted) {
            throw new IllegalTransactionStateException(
                    Single.class.getSimpleName() + " cannot be subscribed to after the transaction has completed.");
        }
        return queued.onErrorResume(t -> Single.error(new RedisClientException("Exception enqueuing command",
                t.getCause() != null ? t.getCause() : t)))
                .flatMap(status -> {
                    if ("QUEUED".equals(status)) {
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
    static void onSuccessUnchecked(final Object obj, final SingleProcessor single) {
        single.onSuccess(obj);
    }
}
