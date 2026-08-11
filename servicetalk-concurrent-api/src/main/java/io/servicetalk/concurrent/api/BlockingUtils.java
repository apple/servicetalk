/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static io.servicetalk.utils.internal.ThrowableUtils.throwException;

/**
 * Utilities to await the result of an asynchronous source from a blocking context, unwrapping
 * {@link ExecutionException} and preserving interrupt semantics.
 */
public final class BlockingUtils {

    private BlockingUtils() {
        // no instances
    }

    /**
     * Subscribes to a {@link Single} immediately and awaits the result.
     * Any occurred {@link Exception} will be converted to unchecked, and {@link ExecutionException}s will be unwrapped.
     * Upon interruption, the operation is cancelled.
     *
     * @param source The {@link Single} to operate on.
     * @param <T> The type of the result.
     * @return The result of the single.
     * @throws Exception {@link InterruptedException} upon interruption or unchecked exceptions for any other exception.
     */
    public static <T> T blockingInvocation(Single<T> source) throws Exception {
        return futureGetCancelOnInterrupt(source.toFuture());
    }

    /**
     * Subscribes to a {@link Completable} immediately and awaits completion.
     * Any occurred {@link Exception} will be converted to unchecked, and {@link ExecutionException}s will be unwrapped.
     * Upon interruption, the operation is cancelled.
     *
     * @param source The {@link Completable} to operate on.
     * @throws Exception {@link InterruptedException} upon interruption or unchecked exceptions for any other exception.
     */
    public static void blockingInvocation(Completable source) throws Exception {
        futureGetCancelOnInterrupt(source.toFuture());
    }

    private static <T> T futureGetCancelOnInterrupt(Future<T> future) throws Exception {
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

    private static Throwable executionExceptionCause(ExecutionException original) {
        return (original.getCause() != null) ? original.getCause() : original;
    }
}
