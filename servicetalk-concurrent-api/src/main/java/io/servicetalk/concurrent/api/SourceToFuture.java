/*
 * Copyright © 2019, 2022-2023 Apple Inc. and the ServiceTalk project authors
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.ThrowableWrapper.isThrowableWrapper;
import static java.util.Objects.requireNonNull;

abstract class SourceToFuture<T> implements Future<T> {
    static final Object NULL = new Object();

    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<SourceToFuture, Object> valueUpdater =
            AtomicReferenceFieldUpdater.newUpdater(SourceToFuture.class, Object.class, "value");

    private final DelayedCancellable cancellable = new DelayedCancellable();
    private final CountDownLatch latch = new CountDownLatch(1);

    @Nullable
    private volatile Object value;

    // The timestamp of the last `.get()` call. This is intended to help with debugging stuck threads via heap dumps.
    private long lastGetTimestampMs;

    private SourceToFuture() {
    }

    public final void onSubscribe(final Cancellable cancellable) {
        this.cancellable.delayedCancellable(cancellable);
    }

    final void setValue(@Nullable final Object value) {
        if (valueUpdater.compareAndSet(this, null, value)) {
            latch.countDown();
        }
    }

    public final void onError(final Throwable t) {
        setValue(requireNonNull(t));
    }

    @Override
    public final boolean cancel(final boolean mayInterruptIfRunning) {
        if (value == null && valueUpdater.compareAndSet(this, null,
                new CancellationWrapper(new CancellationException("Stacktrace from thread calling cancel()")))) {
            try {
                cancellable.cancel();
            } finally {
                latch.countDown();
            }
            return true;
        }
        return false;
    }

    @Override
    public final boolean isCancelled() {
        return CancellationWrapper.isCancellationWrapper(value);
    }

    @Override
    public final boolean isDone() {
        return value != null;
    }

    @Nullable
    @Override
    public final T get() throws InterruptedException, ExecutionException {
        final Object value = this.value;
        if (value == null) {
            lastGetTimestampMs = System.currentTimeMillis();
            try {
                latch.await();
            } finally {
                lastGetTimestampMs = 0;
            }
            return reportGet(this.value);
        } else {
            return reportGet(value);
        }
    }

    @Nullable
    @Override
    public final T get(final long timeout, final TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        final Object value = this.value;
        if (value == null) {
            if (latch.await(timeout, unit)) {
                return reportGet(this.value);
            } else {
                // We do not cancel the source on timeout or interrupt because users can reattempt getting the value
                // after processing an exception.
                throw new TimeoutException("Timed out after " + timeout + ' ' + unit + " waiting for the result");
            }
        } else {
            return reportGet(value);
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private T reportGet(@Nullable final Object value) throws ExecutionException {
        if (value == NULL) {
            return null;
        }
        if (value instanceof Throwable) {
            throw new ExecutionException((Throwable) value);
        }
        if (CancellationWrapper.isCancellationWrapper(value)) {
            CancellationException exception = new CancellationException("Stacktrace from thread calling get()");
            exception.initCause(((CancellationWrapper) value).exception);
            throw exception;
        }
        if (isThrowableWrapper(value)) {
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
            if (result == null) {
                setValue(NULL);
            } else if (result instanceof Throwable) {
                setValue(new ThrowableWrapper((Throwable) result));
            } else {
                setValue(result);
            }
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
            setValue(NULL);
        }
    }

    private static final class CancellationWrapper {
        private final CancellationException exception;

        private CancellationWrapper(final CancellationException exception) {
            this.exception = exception;
        }

        static boolean isCancellationWrapper(@Nullable Object o) {
            return o != null && o.getClass().equals(CancellationWrapper.class);
        }
    }
}
