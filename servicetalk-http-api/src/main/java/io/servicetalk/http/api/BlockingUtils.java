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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static io.servicetalk.concurrent.api.Completable.fromRunnable;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;

final class BlockingUtils {

    private BlockingUtils() {
        // no instances
    }

    interface RunnableCheckedException {
        void run() throws Exception;

        default void runUnchecked() {
            try {
                run();
            } catch (Exception e) {
                throwException(e);
            }
        }
    }

    static Completable blockingToCompletable(RunnableCheckedException r) {
        return fromRunnable(r::runUnchecked);
    }

    static <T> T futureGetCancelOnInterrupt(Future<T> future) throws Exception {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(false);
            throw e;
        } catch (ExecutionException e) {
            return throwException(executionExceptionCause(e));
        }
    }

    static HttpResponse request(final StreamingHttpRequester requester, final HttpExecutionStrategy strategy,
                                final HttpRequest request) throws Exception {
        // It is assumed that users will always apply timeouts at the StreamingHttpService layer (e.g. via filter). So
        // we don't apply any explicit timeout here and just wait forever.
        return blockingInvocation(requester.request(strategy, request.toStreamingRequest())
                .flatMap(StreamingHttpResponse::toResponse));
    }

    static <T> T blockingInvocation(Single<T> source) throws Exception {
        // It is assumed that users will always apply timeouts at the StreamingHttpService layer (e.g. via filter). So
        // we don't apply any explicit timeout here and just wait forever.
        try {
            return source.toFuture().get();
        } catch (final ExecutionException e) {
            return throwException(executionExceptionCause(e));
        }
    }

    static void blockingInvocation(Completable source) throws Exception {
        // It is assumed that users will always apply timeouts at the StreamingHttpService layer (e.g. via filter). So
        // we don't apply any explicit timeout here and just wait forever.
        try {
            source.toFuture().get();
        } catch (final ExecutionException e) {
            throwException(executionExceptionCause(e));
        }
    }

    private static Throwable executionExceptionCause(ExecutionException original) {
        return (original.getCause() != null) ? original.getCause() : original;
    }
}
