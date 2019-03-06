/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.internal.PlatformDependent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.ThrowableUtil.unknownStackTrace;

/**
 * Utilities to await results of an asynchronous computation either by blocking the calling thread.
 */
public final class BlockingTestUtils {
    private static final NullPointerException AWAIT_PUBLISHER_NPE =
            unknownStackTrace(new NullPointerException(), BlockingTestUtils.class,
                    "awaitIndefinitelyNonNull(" + Publisher.class.getSimpleName() + ")");
    private static final NullPointerException AWAIT_PUBLISHER_TIMEOUT_NPE =
            unknownStackTrace(new NullPointerException(), BlockingTestUtils.class,
                    "awaitNonNull(" + Publisher.class.getSimpleName() + ", ..)");
    private static final NullPointerException AWAIT_SINGLE_NPE =
            unknownStackTrace(new NullPointerException(), BlockingTestUtils.class,
                    "awaitIndefinitelyNonNull(" + Single.class.getSimpleName() + ")");
    private static final NullPointerException AWAIT_SINGLE_TIMEOUT_NPE =
            unknownStackTrace(new NullPointerException(), BlockingTestUtils.class,
                    "awaitNonNull(" + Single.class.getSimpleName() + ", ..)");

    private BlockingTestUtils() {
        // No instances.
    }

    /**
     * Awaits termination of the passed {@link Publisher} by blocking the calling thread.
     *
     * @param source to await for.
     * @param <T> Type of items produced by the {@link Publisher}.
     *
     * @return {@link List} of all elements emitted by the {@link Publisher}, iff it terminated successfully.
     * @throws ExecutionException if the passed {@link Publisher} terminates with a failure.
     * @throws InterruptedException if the thread was interrupted while waiting for termination.
     */
    @Nullable
    public static <T> List<T> awaitIndefinitely(Publisher<T> source) throws ExecutionException, InterruptedException {
        return toList(source.toFuture().get());
    }

    /**
     * Awaits termination of the passed {@link Publisher} by blocking the calling thread.
     *
     * @param source to await for.
     * @param <T> Type of items produced by the {@link Publisher}.
     *
     * @return {@link List} of all elements emitted by the {@link Publisher}, iff it terminated successfully.
     * @throws ExecutionException if the passed {@link Publisher} terminates with a failure, or if the result of
     * {@link #awaitIndefinitely(Publisher)} is {@code null}.
     * @throws InterruptedException if the thread was interrupted while waiting for termination.
     * @see #awaitIndefinitely(Publisher)
     */
    public static <T> List<T> awaitIndefinitelyNonNull(Publisher<T> source)
            throws ExecutionException, InterruptedException {
        return enforceNonNull(awaitIndefinitely(source), AWAIT_PUBLISHER_NPE);
    }

    /**
     * Awaits termination of the passed {@link Publisher} by blocking the calling thread.
     *
     * @param source to await for.
     * @param timeout maximum time to wait for termination.
     * @param timeoutUnit {@link TimeUnit} for timeout.
     * @param <T> Type of items produced by the {@link Publisher}.
     *
     * @return {@link List} of all elements emitted by the {@link Publisher}, iff it terminated successfully.
     * @throws ExecutionException if the passed {@link Publisher} terminates with a failure.
     * @throws InterruptedException if the thread was interrupted while waiting for termination.
     * @throws TimeoutException If the {@link Publisher} isn't terminated after waiting for the passed {@code timeout}
     * duration.
     */
    @Nullable
    public static <T> List<T> await(Publisher<T> source, long timeout, TimeUnit timeoutUnit)
            throws ExecutionException, InterruptedException, TimeoutException {
        return toList(source.toFuture().get(timeout, timeoutUnit));
    }

    /**
     * Awaits termination of the passed {@link Publisher} by blocking the calling thread.
     *
     * @param source to await for.
     * @param timeout maximum time to wait for termination.
     * @param timeoutUnit {@link TimeUnit} for timeout.
     * @param <T> Type of items produced by the {@link Publisher}.
     *
     * @return {@link List} of all elements emitted by the {@link Publisher}, iff it terminated successfully.
     * @throws ExecutionException if the passed {@link Publisher} terminates with a failure, or if the result of
     * {@link #await(Publisher, long, TimeUnit)} is {@code null}.
     * @throws InterruptedException if the thread was interrupted while waiting for termination.
     * @throws TimeoutException If the {@link Publisher} isn't terminated after waiting for the passed {@code timeout}
     * duration.
     * @see #await(Publisher, long, TimeUnit)
     */
    public static <T> List<T> awaitNonNull(Publisher<T> source, long timeout, TimeUnit timeoutUnit)
            throws ExecutionException, InterruptedException, TimeoutException {
        return enforceNonNull(await(source, timeout, timeoutUnit), AWAIT_PUBLISHER_TIMEOUT_NPE);
    }

    /**
     * Awaits termination of the passed {@link Single} by blocking the calling thread.
     *
     * @param source to await for.
     * @param <T> Type of the result of {@link Single}.
     *
     * @return Result of the {@link Single}. {@code null} if the {@code source} is of type {@link Void}.
     * @throws ExecutionException if the passed {@link Publisher} terminates with a failure.
     * @throws InterruptedException if the thread was interrupted while waiting for termination.
     */
    @Nullable
    public static <T> T awaitIndefinitely(Single<T> source) throws ExecutionException, InterruptedException {
        return source.toFuture().get();
    }

    /**
     * Awaits termination of the passed {@link Single} by blocking the calling thread.
     *
     * @param source to await for.
     * @param <T> Type of the result of {@link Single}.
     *
     * @return Result of the {@link Single}. {@code null} if the {@code source} is of type {@link Void}.
     * @throws ExecutionException if the passed {@link Publisher} terminates with a failure, or if the result of
     * {@link #awaitIndefinitely(Single)} is {@code null}.
     * @throws InterruptedException if the thread was interrupted while waiting for termination.
     * @see #awaitIndefinitely(Single)
     */
    public static <T> T awaitIndefinitelyNonNull(Single<T> source) throws ExecutionException, InterruptedException {
        return enforceNonNull(awaitIndefinitely(source), AWAIT_SINGLE_NPE);
    }

    /**
     * Awaits termination of the passed {@link Single} by blocking the calling thread.
     *
     * @param source to await for.
     * @param timeout maximum time to wait for termination.
     * @param timeoutUnit {@link TimeUnit} for timeout.
     * @param <T> Type of the result of {@link Single}.
     *
     * @return Result of the {@link Single}. {@code null} if the {@code source} is of type {@link Void}.
     * @throws ExecutionException if the passed {@link Publisher} terminates with a failure.
     * @throws InterruptedException if the thread was interrupted while waiting for termination.
     * @throws TimeoutException If the result isn't available after waiting for the passed {@code timeout} duration.
     */
    @Nullable
    public static <T> T await(Single<T> source, long timeout, TimeUnit timeoutUnit)
            throws ExecutionException, InterruptedException, TimeoutException {
        return source.toFuture().get(timeout, timeoutUnit);
    }

    /**
     * Awaits termination of the passed {@link Single} by blocking the calling thread.
     *
     * @param source to await for.
     * @param timeout maximum time to wait for termination.
     * @param timeoutUnit {@link TimeUnit} for timeout.
     * @param <T> Type of the result of {@link Single}.
     *
     * @return Result of the {@link Single}. {@code null} if the {@code source} is of type {@link Void}.
     * @throws ExecutionException if the passed {@link Publisher} terminates with a failure, or if the result of
     * {@link #await(Single, long, TimeUnit)} is {@code null}.
     * @throws InterruptedException if the thread was interrupted while waiting for termination.
     * @throws TimeoutException If the result isn't available after waiting for the passed {@code timeout} duration.
     * @see #await(Single, long, TimeUnit)
     */
    public static <T> T awaitNonNull(Single<T> source, long timeout, TimeUnit timeoutUnit)
            throws ExecutionException, InterruptedException, TimeoutException {
        return enforceNonNull(await(source, timeout, timeoutUnit), AWAIT_SINGLE_TIMEOUT_NPE);
    }

    /**
     * Awaits termination of the passed {@link Completable} by blocking the calling thread.
     *
     * @param source to await for.
     *
     * @throws ExecutionException if the passed {@link Publisher} terminates with a failure.
     * @throws InterruptedException if the thread was interrupted while waiting for termination.
     */
    public static void awaitIndefinitely(Completable source) throws ExecutionException, InterruptedException {
        source.toVoidFuture().get();
    }

    /**
     * Awaits termination of the passed {@link Completable} by blocking the calling thread.
     * <p>
     * This method will re-throw exceptions from {@link #awaitIndefinitely(Completable)}.
     * @param source to await for.
     */
    public static void awaitIndefinitelyUnchecked(Completable source) {
        try {
            awaitIndefinitely(source);
        } catch (ExecutionException | InterruptedException e) {
            PlatformDependent.throwException(e);
        }
    }

    /**
     * Awaits termination of the passed {@link Completable} by blocking the calling thread.
     *
     * @param source to await for.
     * @param timeout maximum time to wait for termination.
     * @param timeoutUnit {@link TimeUnit} for timeout.
     *
     * @throws ExecutionException if the passed {@link Publisher} terminates with a failure.
     * @throws InterruptedException if the thread was interrupted while waiting for termination.
     * @throws TimeoutException If the {@link Completable} isn't terminated after waiting for the passed
     * {@code timeout} duration.
     */
    public static void await(Completable source, long timeout, TimeUnit timeoutUnit)
            throws ExecutionException, InterruptedException, TimeoutException {
        source.toVoidFuture().get(timeout, timeoutUnit);
    }

    private static <T> T enforceNonNull(@Nullable T result, NullPointerException npe) throws ExecutionException {
        if (result == null) {
            throw new ExecutionException("null return value not supported", npe);
        }
        return result;
    }

    @Nullable
    private static <T> List<T> toList(@Nullable Collection<T> collection) {
        return collection instanceof List ? (List<T>) collection :
                collection == null ? null : new ArrayList<>(collection);
    }
}
