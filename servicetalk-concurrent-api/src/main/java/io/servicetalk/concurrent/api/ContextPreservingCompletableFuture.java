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
package io.servicetalk.concurrent.api;

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

import static io.servicetalk.concurrent.api.DefaultAsyncContextProvider.INSTANCE;
import static java.util.Objects.requireNonNull;

final class ContextPreservingCompletableFuture<T> extends CompletableFuture<T> {
    final CompletableFuture<T> delegate;

    ContextPreservingCompletableFuture(CompletableFuture<T> delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public <U> CompletableFuture<U> thenApply(final Function<? super T, ? extends U> fn) {
        return new ContextPreservingCompletableFuture<>(delegate.thenApply(INSTANCE.wrap(fn)));
    }

    @Override
    public <U> CompletableFuture<U> thenApplyAsync(final Function<? super T, ? extends U> fn) {
        return new ContextPreservingCompletableFuture<>(delegate.thenApplyAsync(INSTANCE.wrap(fn)));
    }

    @Override
    public <U> CompletableFuture<U> thenApplyAsync(final Function<? super T, ? extends U> fn,
                                                   final java.util.concurrent.Executor executor) {
        return new ContextPreservingCompletableFuture<>(delegate.thenApplyAsync(INSTANCE.wrap(fn), executor));
    }

    @Override
    public CompletableFuture<Void> thenAccept(final Consumer<? super T> action) {
        return new ContextPreservingCompletableFuture<>(delegate.thenAccept(INSTANCE.wrap(action)));
    }

    @Override
    public CompletableFuture<Void> thenAcceptAsync(final Consumer<? super T> action) {
        return new ContextPreservingCompletableFuture<>(delegate.thenAcceptAsync(INSTANCE.wrap(action)));
    }

    @Override
    public CompletableFuture<Void> thenAcceptAsync(final Consumer<? super T> action,
                                                   final java.util.concurrent.Executor executor) {
        return new ContextPreservingCompletableFuture<>(delegate.thenAcceptAsync(INSTANCE.wrap(action), executor));
    }

    @Override
    public CompletableFuture<Void> thenRun(final Runnable action) {
        return new ContextPreservingCompletableFuture<>(delegate.thenRun(INSTANCE.wrap(action)));
    }

    @Override
    public CompletableFuture<Void> thenRunAsync(final Runnable action) {
        return new ContextPreservingCompletableFuture<>(delegate.thenRunAsync(INSTANCE.wrap(action)));
    }

    @Override
    public CompletableFuture<Void> thenRunAsync(final Runnable action, final java.util.concurrent.Executor executor) {
        return new ContextPreservingCompletableFuture<>(delegate.thenRunAsync(INSTANCE.wrap(action), executor));
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombine(final CompletionStage<? extends U> other,
                                                   final BiFunction<? super T, ? super U, ? extends V> fn) {
        return new ContextPreservingCompletableFuture<>(delegate.thenCombine(other, INSTANCE.wrap(fn)));
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombineAsync(final CompletionStage<? extends U> other,
                                                        final BiFunction<? super T, ? super U, ? extends V> fn) {
        return new ContextPreservingCompletableFuture<>(delegate.thenCombineAsync(other, INSTANCE.wrap(fn)));
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombineAsync(final CompletionStage<? extends U> other,
                                                        final BiFunction<? super T, ? super U, ? extends V> fn,
                                                        final java.util.concurrent.Executor executor) {
        return new ContextPreservingCompletableFuture<>(delegate.thenCombineAsync(other, INSTANCE.wrap(fn), executor));
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBoth(final CompletionStage<? extends U> other,
                                                      final BiConsumer<? super T, ? super U> action) {
        return new ContextPreservingCompletableFuture<>(delegate.thenAcceptBoth(other, INSTANCE.wrap(action)));
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBothAsync(final CompletionStage<? extends U> other,
                                                           final BiConsumer<? super T, ? super U> action) {
        return new ContextPreservingCompletableFuture<>(delegate.thenAcceptBothAsync(other, INSTANCE.wrap(action)));
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBothAsync(final CompletionStage<? extends U> other,
                                                           final BiConsumer<? super T, ? super U> action,
                                                           final java.util.concurrent.Executor executor) {
        return new ContextPreservingCompletableFuture<>(
                delegate.thenAcceptBothAsync(other, INSTANCE.wrap(action), executor));
    }

    @Override
    public CompletableFuture<Void> runAfterBoth(final CompletionStage<?> other, final Runnable action) {
        return new ContextPreservingCompletableFuture<>(delegate.runAfterBoth(other, INSTANCE.wrap(action)));
    }

    @Override
    public CompletableFuture<Void> runAfterBothAsync(final CompletionStage<?> other, final Runnable action) {
        return new ContextPreservingCompletableFuture<>(delegate.runAfterBothAsync(other, INSTANCE.wrap(action)));
    }

    @Override
    public CompletableFuture<Void> runAfterBothAsync(final CompletionStage<?> other, final Runnable action,
                                                     final java.util.concurrent.Executor executor) {
        return new ContextPreservingCompletableFuture<>(
                delegate.runAfterBothAsync(other, INSTANCE.wrap(action), executor));
    }

    @Override
    public <U> CompletableFuture<U> applyToEither(final CompletionStage<? extends T> other,
                                                  final Function<? super T, U> fn) {
        return new ContextPreservingCompletableFuture<>(delegate.applyToEither(other, INSTANCE.wrap(fn)));
    }

    @Override
    public <U> CompletableFuture<U> applyToEitherAsync(final CompletionStage<? extends T> other,
                                                       final Function<? super T, U> fn) {
        return new ContextPreservingCompletableFuture<>(delegate.applyToEitherAsync(other, INSTANCE.wrap(fn)));
    }

    @Override
    public <U> CompletableFuture<U> applyToEitherAsync(final CompletionStage<? extends T> other,
                                                       final Function<? super T, U> fn,
                                                       final java.util.concurrent.Executor executor) {
        return new ContextPreservingCompletableFuture<>(
                delegate.applyToEitherAsync(other, INSTANCE.wrap(fn), executor));
    }

    @Override
    public CompletableFuture<Void> acceptEither(final CompletionStage<? extends T> other,
                                                final Consumer<? super T> action) {
        return new ContextPreservingCompletableFuture<>(delegate.acceptEither(other, INSTANCE.wrap(action)));
    }

    @Override
    public CompletableFuture<Void> acceptEitherAsync(final CompletionStage<? extends T> other,
                                                     final Consumer<? super T> action) {
        return new ContextPreservingCompletableFuture<>(delegate.acceptEitherAsync(other, INSTANCE.wrap(action)));
    }

    @Override
    public CompletableFuture<Void> acceptEitherAsync(final CompletionStage<? extends T> other,
                                                     final Consumer<? super T> action,
                                                     final java.util.concurrent.Executor executor) {
        return new ContextPreservingCompletableFuture<>(
                delegate.acceptEitherAsync(other, INSTANCE.wrap(action), executor));
    }

    @Override
    public CompletableFuture<Void> runAfterEither(final CompletionStage<?> other, final Runnable action) {
        return new ContextPreservingCompletableFuture<>(delegate.runAfterEither(other, INSTANCE.wrap(action)));
    }

    @Override
    public CompletableFuture<Void> runAfterEitherAsync(final CompletionStage<?> other, final Runnable action) {
        return new ContextPreservingCompletableFuture<>(delegate.runAfterEitherAsync(other, INSTANCE.wrap(action)));
    }

    @Override
    public CompletableFuture<Void> runAfterEitherAsync(final CompletionStage<?> other, final Runnable action,
                                                       final java.util.concurrent.Executor executor) {
        return new ContextPreservingCompletableFuture<>(
                delegate.runAfterEitherAsync(other, INSTANCE.wrap(action), executor));
    }

    @Override
    public <U> CompletableFuture<U> thenCompose(final Function<? super T, ? extends CompletionStage<U>> fn) {
        return new ContextPreservingCompletableFuture<>(delegate.thenCompose(INSTANCE.wrap(fn)));
    }

    @Override
    public <U> CompletableFuture<U> thenComposeAsync(final Function<? super T, ? extends CompletionStage<U>> fn) {
        return new ContextPreservingCompletableFuture<>(delegate.thenComposeAsync(INSTANCE.wrap(fn)));
    }

    @Override
    public <U> CompletableFuture<U> thenComposeAsync(final Function<? super T, ? extends CompletionStage<U>> fn,
                                                     final java.util.concurrent.Executor executor) {
        return new ContextPreservingCompletableFuture<>(delegate.thenComposeAsync(INSTANCE.wrap(fn), executor));
    }

    @Override
    public CompletableFuture<T> exceptionally(final Function<Throwable, ? extends T> fn) {
        return new ContextPreservingCompletableFuture<>(delegate.exceptionally(INSTANCE.wrap(fn)));
    }

    @Override
    public CompletableFuture<T> whenComplete(final BiConsumer<? super T, ? super Throwable> action) {
        return new ContextPreservingCompletableFuture<>(delegate.whenComplete(INSTANCE.wrap(action)));
    }

    @Override
    public CompletableFuture<T> whenCompleteAsync(final BiConsumer<? super T, ? super Throwable> action) {
        return new ContextPreservingCompletableFuture<>(delegate.whenCompleteAsync(INSTANCE.wrap(action)));
    }

    @Override
    public CompletableFuture<T> whenCompleteAsync(final BiConsumer<? super T, ? super Throwable> action,
                                                  final java.util.concurrent.Executor executor) {
        return new ContextPreservingCompletableFuture<>(delegate.whenCompleteAsync(INSTANCE.wrap(action), executor));
    }

    @Override
    public <U> CompletableFuture<U> handle(final BiFunction<? super T, Throwable, ? extends U> fn) {
        return new ContextPreservingCompletableFuture<>(delegate.handle(INSTANCE.wrap(fn)));
    }

    @Override
    public <U> CompletableFuture<U> handleAsync(final BiFunction<? super T, Throwable, ? extends U> fn) {
        return new ContextPreservingCompletableFuture<>(delegate.handleAsync(INSTANCE.wrap(fn)));
    }

    @Override
    public <U> CompletableFuture<U> handleAsync(final BiFunction<? super T, Throwable, ? extends U> fn,
                                                final Executor executor) {
        return new ContextPreservingCompletableFuture<>(delegate.handleAsync(INSTANCE.wrap(fn), executor));
    }

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
    public boolean cancel(boolean mayInterruptIfRunning) {
        return delegate.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean complete(@Nullable T value) {
        return delegate.complete(value);
    }

    @Override
    public boolean completeExceptionally(Throwable ex) {
        return delegate.completeExceptionally(ex);
    }

    @Override
    public void obtrudeValue(@Nullable T value) {
        delegate.obtrudeValue(value);
    }

    @Override
    public void obtrudeException(Throwable ex) {
        delegate.obtrudeException(ex);
    }

    @Override
    public int getNumberOfDependents() {
        return delegate.getNumberOfDependents();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
