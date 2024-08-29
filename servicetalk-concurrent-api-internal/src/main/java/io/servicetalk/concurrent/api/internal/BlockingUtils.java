/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.internal;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static io.servicetalk.utils.internal.ThrowableUtils.throwException;

/**
 * Common utility functions to unwrap {@link ExecutionException} from async operations.
 */
public final class BlockingUtils {

    private BlockingUtils() {
        // no instances
    }

    /**
     * Completes a {@link Future} by invoking {@link Future#get()}.
     * Any occurred {@link Exception} will be converted to unchecked, and {@link ExecutionException}s will be unwrapped.
     * Upon interruption, the {@link Future} is cancelled.
     *
     * @param future The future to operate on.
     * @param <T> The type of the result.
     * @return The result of the future.
     * @throws Exception InterrupedException upon interruption or unchecked exceptions for any other exception.
     */
    public static <T> T futureGetCancelOnInterrupt(Future<T> future) throws Exception {
        try {
            return trackBlockingGet(future);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(false);
            throw e;
        } catch (ExecutionException e) {
            return throwException(executionExceptionCause(e));
        }
    }

    /**
     * Subscribes a {@link Single} immediately and awaits result.
     * Any occurred {@link Exception} will be converted to unchecked, and {@link ExecutionException}s will be unwrapped.
     *
     * @param source The {@link Single} to operate on.
     * @param <T> The type of the result.
     * @return The result of the single.
     * @throws Exception InterrupedException upon interruption or unchecked exceptions for any other exception.
     */
    public static <T> T blockingInvocation(Single<T> source) throws Exception {
        // It is assumed that users will always apply timeouts at the StreamingHttpService layer (e.g. via filter). So
        // we don't apply any explicit timeout here and just wait forever.
        try {
            return trackBlockingGet(source.toFuture());
        } catch (final ExecutionException e) {
            return throwException(executionExceptionCause(e));
        }
    }

    /**
     * Subscribes a {@link Completable} immediately and awaits result.
     * Any occurred {@link Exception} will be converted to unchecked, and {@link ExecutionException}s will be unwrapped.
     *
     * @param source The {@link Completable} to operate on.
     * @throws Exception unchecked exceptions for any exception that occurs.
     */
    public static void blockingInvocation(Completable source) throws Exception {
        // It is assumed that users will always apply timeouts at the StreamingHttpService layer (e.g. via filter). So
        // we don't apply any explicit timeout here and just wait forever.
        try {
            trackBlockingGet(source.toFuture());
        } catch (final ExecutionException e) {
            throwException(executionExceptionCause(e));
        }
    }

    private static Throwable executionExceptionCause(ExecutionException original) {
        return (original.getCause() != null) ? original.getCause() : original;
    }

    private static <T> T trackBlockingGet(Future<T> future) throws InterruptedException, ExecutionException {
        return new TimestampedFutureGetter<>(future).doGet();
    }

    // A helper to track the blocking time of futures.
    private static final class TimestampedFutureGetter<T> {
        private final Future<T> future;
        private long getTimestampMs;

        TimestampedFutureGetter(Future<T> future) {
            this.future = future;
        }

        T doGet() throws InterruptedException, ExecutionException {
            getTimestampMs = System.currentTimeMillis();
            try {
                return future.get();
            } finally {
                getTimestampMs = 0;
            }
        }

        @Override
        public String toString() {
            return "FutureGetter{" +
                    "future=" + future +
                    ", getTimestampMs=" + getTimestampMs +
                    '}';
        }
    }
}
