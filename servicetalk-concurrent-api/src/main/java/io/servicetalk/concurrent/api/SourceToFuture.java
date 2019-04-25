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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

abstract class SourceToFuture<T> implements Future<T> {

    private static final Object NULL = new Object();
    private static final Object CANCELLED = new Object();

    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<SourceToFuture, Object> valueUpdater =
            AtomicReferenceFieldUpdater.newUpdater(SourceToFuture.class, Object.class, "value");

    private final DelayedCancellable cancellable = new DelayedCancellable();
    private final CountDownLatch latch = new CountDownLatch(1);

    @Nullable
    private volatile Object value;

    private SourceToFuture() {
    }

    public final void onSubscribe(final Cancellable cancellable) {
        this.cancellable.delayedCancellable(cancellable);
    }

    final void onSuccessInternal(@Nullable final T result) {
        if (isDone()) {
            return;
        }
        final Object tmp;
        if (result == null) {
            tmp = NULL;
        } else if (result instanceof Throwable) {
            tmp = new ThrowableWrapper(result);
        } else {
            tmp = result;
        }
        if (valueUpdater.compareAndSet(this, null, tmp)) {
            latch.countDown();
        }
    }

    final void onCompleteInternal() {
        if (valueUpdater.compareAndSet(this, null, NULL)) {
            latch.countDown();
        }
    }

    public final void onError(final Throwable t) {
        if (valueUpdater.compareAndSet(this, null, requireNonNull(t))) {
            latch.countDown();
        }
    }

    @Override
    public final boolean cancel(final boolean mayInterruptIfRunning) {
        if (valueUpdater.compareAndSet(this, null, CANCELLED)) {
            cancellable.cancel();
            latch.countDown();
            return true;
        }
        return false;
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
            latch.await();
        }
        return reportGet();
    }

    @Nullable
    @Override
    public final T get(final long timeout, final TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (!isDone()) {
            if (!latch.await(timeout, unit)) {
                throw new TimeoutException("Timed out waiting for the result");
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
