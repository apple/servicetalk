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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.Completable;
import io.servicetalk.concurrent.Single;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;

/**
 * Utilities to await results of an asynchronous computation either by blocking the calling thread.
 */
public final class Await {

    private Await() {
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
        return subscribe(source).blockingGet();
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
     * @throws TimeoutException If the {@link Publisher} isn't terminated after waiting for the passed {@code timeout} duration.
     */
    @Nullable
    public static <T> List<T> await(Publisher<T> source, long timeout, TimeUnit timeoutUnit) throws ExecutionException, InterruptedException, TimeoutException {
        return subscribe(source).blockingGet(timeout, timeoutUnit);
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
        return subscribe(source).blockingGet();
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
    public static <T> T await(Single<T> source, long timeout, TimeUnit timeoutUnit) throws ExecutionException, InterruptedException, TimeoutException {
        return subscribe(source).blockingGet(timeout, timeoutUnit);
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
        subscribe(source).blockAndThrowIfFailed();
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
     * @throws TimeoutException If the {@link Completable} isn't terminated after waiting for the passed {@code timeout} duration.
     */
    public static void await(Completable source, long timeout, TimeUnit timeoutUnit) throws ExecutionException, InterruptedException, TimeoutException {
        subscribe(source).blockAndThrowIfFailed(timeout, timeoutUnit);
    }

    private static <T> ResultProvider<List<T>> subscribe(Publisher<T> source) {
        assertNotInEventloop();
        SubscriberImpl<T> subscriber = new SubscriberImpl<>();
        source.subscribe(subscriber);
        return subscriber;
    }

    private static <T> ResultProvider<T> subscribe(Single<T> source) {
        assertNotInEventloop();
        SingleSubscriberImpl<T> subscriber = new SingleSubscriberImpl<>();
        source.subscribe(subscriber);
        return subscriber;
    }

    private static ResultProvider<Void> subscribe(Completable source) {
        assertNotInEventloop();
        SingleSubscriberImpl<Void> subscriber = new SingleSubscriberImpl<>();
        source.subscribe(subscriber);
        return subscriber;
    }

    private static void assertNotInEventloop() {
        //TODO: Check if the current thread is an eventloop, if so, throw.
    }

    private abstract static class ResultProvider<T> {

        @Nullable
        private T result;
        @Nullable
        private Throwable cause;

        private final AtomicReference<Cancellable> cancellable = new AtomicReference<>();
        private final AtomicBoolean terminated = new AtomicBoolean();
        private final CountDownLatch latch = new CountDownLatch(1);

        protected void setSuccess(@Nullable T result) {
            if (terminated.compareAndSet(false, true)) {
                this.result = result;
                latch.countDown();
            }
        }

        protected void setFailure(Throwable cause) {
            if (terminated.compareAndSet(false, true)) {
                this.cause = cause;
                latch.countDown();
            }
        }

        @Nullable
        T blockingGet() throws ExecutionException, InterruptedException {
            latch.await();
            return get();
        }

        @Nullable
        T blockingGet(long timeout, TimeUnit timeoutUnit) throws ExecutionException, InterruptedException, TimeoutException {
            if (latch.await(timeout, timeoutUnit)) {
                return get();
            } else {
                return timeout(timeout, timeoutUnit);
            }
        }

        void blockAndThrowIfFailed() throws ExecutionException, InterruptedException {
            latch.await();
            throwIfFailed();
        }

        void blockAndThrowIfFailed(long timeout, TimeUnit timeoutUnit) throws ExecutionException, InterruptedException, TimeoutException {
            if (latch.await(timeout, timeoutUnit)) {
                throwIfFailed();
            } else {
                timeout(timeout, timeoutUnit);
            }
        }

        void setCancellable(Cancellable cancellable) {
            if (!this.cancellable.compareAndSet(null, cancellable)) {
                cancellable.cancel();
            }
        }

        private T timeout(long timeout, TimeUnit timeoutUnit) throws TimeoutException {
            Cancellable oldVal = cancellable.getAndSet(IGNORE_CANCEL);
            if (oldVal != null) {
                oldVal.cancel();
            }
            setFailure(new CancellationException("Source is cancelled."));
            throw new TimeoutException(String.format("No terminal signal received after waiting for %s %s.", timeout, timeoutUnit));
        }

        @Nullable
        private T get() throws ExecutionException {
            throwIfFailed();
            return result;
        }

        private void throwIfFailed() throws ExecutionException {
            if (cause != null) {
                throw cause instanceof ExecutionException ? (ExecutionException) cause : new ExecutionException(cause);
            }
        }
    }

    private static final class SingleSubscriberImpl<T> extends ResultProvider<T> implements Single.Subscriber<T>, Completable.Subscriber {
        @Override
        public void onSubscribe(Cancellable cancellable) {
            setCancellable(cancellable);
        }

        @Override
        public void onSuccess(@Nullable T result) {
            setSuccess(result);
        }

        @Override
        public void onComplete() {
            setSuccess(null);
        }

        @Override
        public void onError(Throwable t) {
            setFailure(t);
        }
    }

    private static final class SubscriberImpl<T> extends ResultProvider<List<T>> implements org.reactivestreams.Subscriber<T> {

        private final List<T> data = new ArrayList<>();

        @Override
        public void onSubscribe(Subscription s) {
            setCancellable(s::cancel);
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T t) {
            data.add(t);
        }

        @Override
        public void onError(Throwable t) {
            if (data.isEmpty()) {
                setFailure(t);
            } else {
                setFailure(new ExecutionException("Source failed after emitting some items: " + data, t));
            }
        }

        @Override
        public void onComplete() {
            setSuccess(data);
        }
    }
}
