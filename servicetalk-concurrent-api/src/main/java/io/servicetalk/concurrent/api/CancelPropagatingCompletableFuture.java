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

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

final class CancelPropagatingCompletableFuture<T> extends CompletableFuture<T> {
    private final Cancellable cancellable;
    private final CompletableFuture<T> delegate;

    private CancelPropagatingCompletableFuture(CompletableFuture<T> delegate, Cancellable cancellable) {
        this.delegate = delegate;
        this.cancellable = cancellable;
    }

    static <T> CompletableFuture<T> newInstance(CompletableFuture<T> delegate, Cancellable cancellable) {
        CancelPropagatingCompletableFuture<T> future = new CancelPropagatingCompletableFuture<>(delegate,
                cancellable);
        wrap(delegate, future);
        return future;
    }

    /**
     * Enables the {@code outer} to mimic the status of the {@code delegate}.
     * <p>
     * {@link CompletableFuture} may use internal APIs to complete itself. This doesn't work well when trying to
     * delegate. So we attach a listener to {@code delegate} in case there are any state changes and make sure this
     * state is reflected in {@code outer}.
     *
     * @param delegate The {@link CompletableFuture} that is being delegated to.
     * @param outer The {@link CompletableFuture} that is doing the delegating.
     * @param <T> The type of {@link CompletableFuture}.
     */
    static <T> void wrap(CompletableFuture<T> delegate, CompletableFuture<T> outer) {
        // This is necessary because CompletableFuture may use internal methods to set the result in the event of
        // an unexpected exception from user callbacks. In this case our methods won't be executed.
        delegate.whenComplete((value, throwable) -> {
            if (throwable == null) {
                outer.complete(value);
            } else if (throwable instanceof CancellationException) {
                outer.cancel(true);
            } else {
                outer.completeExceptionally(throwable);
            }
        });
    }

    // CompletionStage begin
    @Override
    public <U> CompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
        return newInstance(delegate.thenApply(fn), cancellable);
    }

    @Override
    public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return newInstance(delegate.thenApplyAsync(fn), cancellable);
    }

    @Override
    public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn,
                                                   java.util.concurrent.Executor executor) {
        return newInstance(delegate.thenApplyAsync(fn, executor), cancellable);
    }

    @Override
    public CompletableFuture<Void> thenAccept(Consumer<? super T> action) {
        return newInstance(delegate.thenAccept(action), cancellable);
    }

    @Override
    public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action) {
        return newInstance(delegate.thenAcceptAsync(action), cancellable);
    }

    @Override
    public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action, java.util.concurrent.Executor executor) {
        return newInstance(delegate.thenAcceptAsync(action, executor),
                cancellable);
    }

    @Override
    public CompletableFuture<Void> thenRun(Runnable action) {
        return newInstance(delegate.thenRun(action), cancellable);
    }

    @Override
    public CompletableFuture<Void> thenRunAsync(Runnable action) {
        return newInstance(delegate.thenRunAsync(action), cancellable);
    }

    @Override
    public CompletableFuture<Void> thenRunAsync(Runnable action, java.util.concurrent.Executor executor) {
        return newInstance(delegate.thenRunAsync(action, executor), cancellable);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombine(CompletionStage<? extends U> other,
                                                   BiFunction<? super T, ? super U, ? extends V> fn) {
        return newInstance(delegate.thenCombine(other, fn), cancellable);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                        BiFunction<? super T, ? super U, ? extends V> fn) {
        return newInstance(delegate.thenCombineAsync(other, fn), cancellable);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                        BiFunction<? super T, ? super U, ? extends V> fn,
                                                        java.util.concurrent.Executor executor) {
        return newInstance(delegate.thenCombineAsync(other, fn, executor),
                cancellable);
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBoth(CompletionStage<? extends U> other,
                                                      BiConsumer<? super T, ? super U> action) {
        return newInstance(delegate.thenAcceptBoth(other, action), cancellable);
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                           BiConsumer<? super T, ? super U> action) {
        return newInstance(delegate.thenAcceptBothAsync(other, action),
                cancellable);
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                           BiConsumer<? super T, ? super U> action,
                                                           java.util.concurrent.Executor executor) {
        return newInstance(delegate.thenAcceptBothAsync(other, action, executor),
                cancellable);
    }

    @Override
    public CompletableFuture<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return newInstance(delegate.runAfterBoth(other, action), cancellable);
    }

    @Override
    public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return newInstance(delegate.runAfterBothAsync(other, action),
                cancellable);
    }

    @Override
    public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action,
                                                     java.util.concurrent.Executor executor) {
        return newInstance(delegate.runAfterBothAsync(other, action, executor),
                cancellable);
    }

    @Override
    public <U> CompletableFuture<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return newInstance(delegate.applyToEither(other, fn), cancellable);
    }

    @Override
    public <U> CompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other,
                                                       Function<? super T, U> fn) {
        return newInstance(delegate.applyToEitherAsync(other, fn), cancellable);
    }

    @Override
    public <U> CompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other,
                                                       Function<? super T, U> fn,
                                                       java.util.concurrent.Executor executor) {
        return newInstance(delegate.applyToEitherAsync(other, fn, executor),
                cancellable);
    }

    @Override
    public CompletableFuture<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return newInstance(delegate.acceptEither(other, action), cancellable);
    }

    @Override
    public CompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other,
                                                     Consumer<? super T> action) {
        return newInstance(delegate.acceptEitherAsync(other, action),
                cancellable);
    }

    @Override
    public CompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action,
                                                     java.util.concurrent.Executor executor) {
        return newInstance(delegate.acceptEitherAsync(other, action, executor),
                cancellable);
    }

    @Override
    public CompletableFuture<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return newInstance(delegate.runAfterEither(other, action), cancellable);
    }

    @Override
    public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return newInstance(delegate.runAfterEitherAsync(other, action),
                cancellable);
    }

    @Override
    public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action,
                                                       java.util.concurrent.Executor executor) {
        return newInstance(delegate.runAfterEitherAsync(other, action, executor),
                cancellable);
    }

    @Override
    public <U> CompletableFuture<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return newInstance(delegate.thenCompose(fn), cancellable);
    }

    @Override
    public <U> CompletableFuture<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return newInstance(delegate.thenComposeAsync(fn), cancellable);
    }

    @Override
    public <U> CompletableFuture<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn,
                                                     java.util.concurrent.Executor executor) {
        return newInstance(delegate.thenComposeAsync(fn, executor), cancellable);
    }

    @Override
    public CompletableFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return newInstance(delegate.exceptionally(fn), cancellable);
    }

    @Override
    public CompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return newInstance(delegate.whenComplete(action), cancellable);
    }

    @Override
    public CompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return newInstance(delegate.whenCompleteAsync(action), cancellable);
    }

    @Override
    public CompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action,
                                                  java.util.concurrent.Executor executor) {
        return newInstance(delegate.whenCompleteAsync(action, executor),
                cancellable);
    }

    @Override
    public <U> CompletableFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return newInstance(delegate.handle(fn), cancellable);
    }

    @Override
    public <U> CompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return newInstance(delegate.handleAsync(fn), cancellable);
    }

    @Override
    public <U> CompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn,
                                                Executor executor) {
        return newInstance(delegate.handleAsync(fn, executor), cancellable);
    }
    // CompletionStage end

    // CompletableFuture begin
    // We have to propagate status to super because this class extends from CompletableFuture, which keeps
    // internal state and may call internal methods to change the state and notify listeners if folks use
    // toCompletionStage()
    @Override
    public boolean isDone() {
        return delegate.isDone();
    }

    @Override
    public boolean isCancelled() {
        return delegate.isCancelled();
    }

    @Override
    public boolean isCompletedExceptionally() {
        return delegate.isCompletedExceptionally();
    }

    @Nullable
    @Override
    public T get() throws InterruptedException, ExecutionException {
        return delegate.get();
    }

    @Nullable
    @Override
    public T get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.get(timeout, unit);
    }

    @Nullable
    @Override
    public T join() {
        return delegate.join();
    }

    @Nullable
    @Override
    public T getNow(@Nullable T valueIfAbsent) {
        return delegate.getNow(valueIfAbsent);
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        try {
            super.cancel(mayInterruptIfRunning);
            return delegate.cancel(mayInterruptIfRunning);
        } finally {
            cancellable.cancel();
        }
    }

    @Override
    public boolean complete(T value) {
        try {
            super.complete(value);
            return delegate.complete(value);
        } finally {
            cancellable.cancel();
        }
    }

    @Override
    public boolean completeExceptionally(Throwable ex) {
        try {
            super.completeExceptionally(ex);
            return delegate.completeExceptionally(ex);
        } finally {
            cancellable.cancel();
        }
    }

    @Override
    public void obtrudeValue(T value) {
        try {
            super.obtrudeValue(value);
            delegate.obtrudeValue(value);
        } finally {
            cancellable.cancel();
        }
    }

    @Override
    public void obtrudeException(Throwable ex) {
        try {
            super.obtrudeException(ex);
            delegate.obtrudeException(ex);
        } finally {
            cancellable.cancel();
        }
    }

    @Override
    public int getNumberOfDependents() {
        return delegate.getNumberOfDependents();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
    // CompletableFuture end
}
