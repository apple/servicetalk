/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.internal.DelayedCancellable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

abstract class SourceToFuture<T> implements Future<T> {

    private static final Object NULL = new Object();
    private static final Object CANCELLED = new Object();

    private final DelayedCancellable cancellable = new DelayedCancellable();

    @Nullable
    private Object value;

    private SourceToFuture() {
    }

    public final void onSubscribe(final Cancellable cancellable) {
        this.cancellable.delayedCancellable(cancellable);
    }

    final void onSuccessInternal(@Nullable final T result) {
        if (isDone()) {
            return;
        }
        synchronized (cancellable) {
            if (!isDone()) {
                if (result == null) {
                    value = NULL;
                } else if (result instanceof Throwable) {
                    value = new ThrowableWrapper(result);
                } else {
                    value = result;
                }
                cancellable.notifyAll();
            }
        }
    }

    final void onCompleteInternal() {
        if (isDone()) {
            return;
        }
        synchronized (cancellable) {
            if (!isDone()) {
                value = NULL;
                cancellable.notifyAll();
            }
        }
    }

    public final void onError(final Throwable t) {
        if (isDone()) {
            return;
        }
        synchronized (cancellable) {
            if (!isDone()) {
                value = t;
                cancellable.notifyAll();
            }
        }
    }

    @Override
    public final boolean cancel(final boolean mayInterruptIfRunning) {
        if (isDone()) {
            return false;
        }
        cancellable.cancel();
        synchronized (cancellable) {
            if (isDone()) {
                return false;
            }
            value = CANCELLED;
            cancellable.notifyAll();
        }
        return true;
    }

    @Override
    public final boolean isCancelled() {
        return value == CANCELLED;
    }

    @Override
    public final boolean isDone() {
        return value != null;
    }

    @Nullable
    @Override
    public final T get() throws InterruptedException, ExecutionException {
        if (!isDone()) {
            synchronized (cancellable) {
                while (!isDone()) {
                    cancellable.wait();
                }
            }
        }
        return reportGet();
    }

    @Nullable
    @Override
    public final T get(final long timeout, final TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {

        if (!isDone()) {
            long timeoutNanos = unit.toNanos(timeout);
            if (timeoutNanos <= 0L) {
                throw new TimeoutException();
            }
            if (timeoutNanos < 1_000_000L) {
                timeoutNanos = 1_000_000L;  // round up to 1 milli
            }
            long timeStampA = System.nanoTime();
            long timeStampB;
            synchronized (cancellable) {
                while (!isDone()) {
                    long timeoutMillis = timeoutNanos / 1_000_000L;
                    if (timeoutMillis <= 0L) {  // avoid 0, otherwise it will wait until notified
                        throw new TimeoutException("Timed out waiting for the result");
                    }
                    cancellable.wait(timeoutMillis);

                    timeStampB = System.nanoTime();
                    timeoutNanos -= timeStampB - timeStampA; // time to lock for the next iteration
                    timeStampA = timeStampB;
                }
            }
        }
        return reportGet();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private T reportGet() throws ExecutionException {
        if (value == NULL) {
            return null;
        }
        if (value instanceof Throwable) {
            throw new ExecutionException((Throwable) value);
        }
        if (value == CANCELLED) {
            throw new CancellationException();
        }
        if (value instanceof ThrowableWrapper) {
            return (T) ((ThrowableWrapper) value).unwrap();
        }
        return (T) value;
    }

    static final class SingleToFuture<T> extends SourceToFuture<T> implements SingleSource.Subscriber<T> {

        private SingleToFuture() {
        }

        static <T> Future<T> createAndSubscribe(final Single<T> original) {
            SingleToFuture<T> future = new SingleToFuture<>();
            original.subscribeInternal(future);
            return future;
        }

        @Override
        public void onSuccess(@Nullable final T result) {
            onSuccessInternal(result);
        }
    }

    static final class CompletableToFuture extends SourceToFuture<Void> implements CompletableSource.Subscriber {

        private CompletableToFuture() {
        }

        static Future<Void> createAndSubscribe(final Completable original) {
            CompletableToFuture future = new CompletableToFuture();
            original.subscribeInternal(future);
            return future;
        }

        @Override
        public void onComplete() {
            onCompleteInternal();
        }
    }

    private static final class ThrowableWrapper {

        private final Object throwable;

        ThrowableWrapper(final Object throwable) {
            this.throwable = throwable;
        }

        Object unwrap() {
            return throwable;
        }
    }
}
