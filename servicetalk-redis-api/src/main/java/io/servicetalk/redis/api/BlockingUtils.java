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

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ThreadInterruptingCancellable;

import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.Publisher.error;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.PlatformDependent.throwException;
import static java.lang.Thread.currentThread;

final class BlockingUtils {

    private static final Object DUMMY = new Object();

    private BlockingUtils() {
        // No instances
    }

    interface RunnableCheckedException {
        void run() throws Exception;
    }

    interface SupplierCheckedException<T> {
        T get() throws Exception;
    }

    static void blockingInvocation(final Completable source) throws Exception {
        // It is assumed that users will always apply timeouts at another layer. So we don't
        // apply any explicit timeout here and just wait forever.
        try {
            awaitIndefinitely(source);
        } catch (final ExecutionException e) {
            throwException(e.getCause());
        }
    }

    @Nullable
    static <T> T blockingInvocation(final Single<T> source) throws Exception {
        // It is assumed that users will always apply timeouts at another layer. So we don't
        // apply any explicit timeout here and just wait forever.
        try {
            return awaitIndefinitely(source);
        } catch (final ExecutionException e) {
            throwException(e.getCause());
            return uncheckedCast(); // Used to fool the compiler, but actually should never be invoked at runtime.
        }
    }

    static <T> BlockingIterable<T> blockingInvocation(final Publisher<T> source) throws Exception {
        return source.toIterable();
    }

    static <T> Deferred singleToDeferredValue(Single<T> single) {
        final Deferred<T> deferred = new Deferred<>();

        single.subscribe(new io.servicetalk.concurrent.Single.Subscriber<T>() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                // nothing to do
            }

            @Override
            public void onSuccess(@Nullable final T result) {
                deferred.onSuccess(result);
            }

            @Override
            public void onError(final Throwable t) {
                deferred.onError(t);
            }
        });

        return deferred;
    }

    static Completable blockingToCompletable(RunnableCheckedException r) {
        return new Completable() {
            @Override
            protected void handleSubscribe(final Subscriber subscriber) {
                ThreadInterruptingCancellable cancellable = new ThreadInterruptingCancellable(currentThread());
                subscriber.onSubscribe(cancellable);
                try {
                    r.run();
                } catch (Throwable cause) {
                    cancellable.setDone(cause);
                    subscriber.onError(cause);
                    return;
                }
                // It is safe to set this outside the scope of the try/catch above because we don't do any blocking
                // operations which may be interrupted between the completion of the blockingHttpService call and
                // here.
                cancellable.setDone();
                subscriber.onComplete();
            }
        };
    }

    static <T> Single<T> blockingToSingle(SupplierCheckedException<T> supplier) {
        return new Single<T>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super T> subscriber) {
                ThreadInterruptingCancellable cancellable = new ThreadInterruptingCancellable(currentThread());
                subscriber.onSubscribe(cancellable);
                final T response;
                try {
                    response = supplier.get();
                } catch (Throwable cause) {
                    cancellable.setDone(cause);
                    subscriber.onError(cause);
                    return;
                }
                // It is safe to set this outside the scope of the try/catch above because we don't do any blocking
                // operations which may be interrupted between the completion of the blockingHttpService call and here.
                cancellable.setDone();

                // The from(..) operator will take care of propagating cancel.
                subscriber.onSuccess(response);
            }
        };
    }

    static <T> Publisher<T> blockingToPublisher(SupplierCheckedException<BlockingIterable<T>> supplier) {
        return defer(() -> {
            BlockingIterable<T> result;
            try {
                result = supplier.get();
            } catch (Exception e) {
                return error(e);
            }
            return from(result);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T uncheckedCast() {
        return (T) DUMMY;
    }
}
