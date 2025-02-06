/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.context.api.ContextMap;

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

import static io.servicetalk.concurrent.api.CancelPropagatingCompletableFuture.cascadeTermination;
import static java.util.Objects.requireNonNull;

final class ContextPreservingCompletableFuture<T> extends CompletableFuture<T> {
    // TODO: remove after 0.42.55
    private final ContextMap saved;
    private final CompletableFuture<T> delegate;
    private final CapturedContext capturedContext;

    private ContextPreservingCompletableFuture(CompletableFuture<T> delegate, CapturedContext capturedContext) {
        this.delegate = requireNonNull(delegate);
        this.capturedContext = requireNonNull(capturedContext);
        this.saved = capturedContext.captured();
    }

    static <T> ContextPreservingCompletableFuture<T> newContextPreservingFuture(CompletableFuture<T> original,
                                                                                CapturedContext capturedContext) {
        ContextPreservingCompletableFuture<T> future =
                new ContextPreservingCompletableFuture<>(original, capturedContext);
        cascadeTermination(original, future);
        return future;
    }

    // CompletionStage begin
    @Override
    public <U> CompletableFuture<U> thenApply(final Function<? super T, ? extends U> fn) {
        return newContextPreservingFuture(delegate.thenApply(
                AsyncContext.provider().wrapFunction(fn, capturedContext)), capturedContext);
    }

    @Override
    public <U> CompletableFuture<U> thenApplyAsync(final Function<? super T, ? extends U> fn) {
        return newContextPreservingFuture(delegate.thenApplyAsync(
                AsyncContext.provider().wrapFunction(fn, capturedContext)),
                capturedContext);
    }

    @Override
    public <U> CompletableFuture<U> thenApplyAsync(final Function<? super T, ? extends U> fn,
                                                   final java.util.concurrent.Executor executor) {
        return newContextPreservingFuture(delegate.thenApplyAsync(
                AsyncContext.provider().wrapFunction(fn, capturedContext), executor), capturedContext);
    }

    @Override
    public CompletableFuture<Void> thenAccept(final Consumer<? super T> action) {
        return newContextPreservingFuture(delegate.thenAccept(
                AsyncContext.provider().wrapConsumer(action, capturedContext)), capturedContext);
    }

    @Override
    public CompletableFuture<Void> thenAcceptAsync(final Consumer<? super T> action) {
        return newContextPreservingFuture(delegate.thenAcceptAsync(
                AsyncContext.provider().wrapConsumer(action, capturedContext)), capturedContext);
    }

    @Override
    public CompletableFuture<Void> thenAcceptAsync(final Consumer<? super T> action,
                                                   final java.util.concurrent.Executor executor) {
        return newContextPreservingFuture(delegate.thenAcceptAsync(
                AsyncContext.provider().wrapConsumer(action, capturedContext), executor), capturedContext);
    }

    @Override
    public CompletableFuture<Void> thenRun(final Runnable action) {
        return newContextPreservingFuture(delegate.thenRun(
                AsyncContext.provider().wrapRunnable(action, capturedContext)), capturedContext);
    }

    @Override
    public CompletableFuture<Void> thenRunAsync(final Runnable action) {
        return newContextPreservingFuture(delegate.thenRunAsync(
                AsyncContext.provider().wrapRunnable(action, capturedContext)),
                capturedContext);
    }

    @Override
    public CompletableFuture<Void> thenRunAsync(final Runnable action, final java.util.concurrent.Executor executor) {
        return newContextPreservingFuture(delegate.thenRunAsync(
                AsyncContext.provider().wrapRunnable(action, capturedContext), executor), capturedContext);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombine(final CompletionStage<? extends U> other,
                                                   final BiFunction<? super T, ? super U, ? extends V> fn) {
        return newContextPreservingFuture(delegate.thenCombine(other,
                AsyncContext.provider().wrapBiFunction(fn, capturedContext)), capturedContext);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombineAsync(final CompletionStage<? extends U> other,
                                                        final BiFunction<? super T, ? super U, ? extends V> fn) {
        return newContextPreservingFuture(delegate.thenCombineAsync(other,
                AsyncContext.provider().wrapBiFunction(fn, capturedContext)), capturedContext);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombineAsync(final CompletionStage<? extends U> other,
                                                        final BiFunction<? super T, ? super U, ? extends V> fn,
                                                        final java.util.concurrent.Executor executor) {
        return newContextPreservingFuture(delegate.thenCombineAsync(other,
                AsyncContext.provider().wrapBiFunction(fn, capturedContext), executor), capturedContext);
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBoth(final CompletionStage<? extends U> other,
                                                      final BiConsumer<? super T, ? super U> action) {
        return newContextPreservingFuture(delegate.thenAcceptBoth(other,
                        AsyncContext.provider().wrapBiConsumer(action, capturedContext)), capturedContext);
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBothAsync(final CompletionStage<? extends U> other,
                                                           final BiConsumer<? super T, ? super U> action) {
        return newContextPreservingFuture(delegate.thenAcceptBothAsync(other,
                        AsyncContext.provider().wrapBiConsumer(action, capturedContext)), capturedContext);
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBothAsync(final CompletionStage<? extends U> other,
                                                           final BiConsumer<? super T, ? super U> action,
                                                           final java.util.concurrent.Executor executor) {
        return newContextPreservingFuture(delegate.thenAcceptBothAsync(other,
                AsyncContext.provider().wrapBiConsumer(action, capturedContext), executor), capturedContext);
    }

    @Override
    public CompletableFuture<Void> runAfterBoth(final CompletionStage<?> other, final Runnable action) {
        return newContextPreservingFuture(delegate.runAfterBoth(other,
                AsyncContext.provider().wrapRunnable(action, capturedContext)), capturedContext);
    }

    @Override
    public CompletableFuture<Void> runAfterBothAsync(final CompletionStage<?> other, final Runnable action) {
        return newContextPreservingFuture(delegate.runAfterBothAsync(other,
                        AsyncContext.provider().wrapRunnable(action, capturedContext)), capturedContext);
    }

    @Override
    public CompletableFuture<Void> runAfterBothAsync(final CompletionStage<?> other, final Runnable action,
                                                     final java.util.concurrent.Executor executor) {
        return newContextPreservingFuture(delegate.runAfterBothAsync(other,
                AsyncContext.provider().wrapRunnable(action, capturedContext), executor), capturedContext);
    }

    @Override
    public <U> CompletableFuture<U> applyToEither(final CompletionStage<? extends T> other,
                                                  final Function<? super T, U> fn) {
        return newContextPreservingFuture(delegate.applyToEither(other,
                AsyncContext.provider().wrapFunction(fn, capturedContext)), capturedContext);
    }

    @Override
    public <U> CompletableFuture<U> applyToEitherAsync(final CompletionStage<? extends T> other,
                                                       final Function<? super T, U> fn) {
        return newContextPreservingFuture(delegate.applyToEitherAsync(other,
                AsyncContext.provider().wrapFunction(fn, capturedContext)), capturedContext);
    }

    @Override
    public <U> CompletableFuture<U> applyToEitherAsync(final CompletionStage<? extends T> other,
                                                       final Function<? super T, U> fn,
                                                       final java.util.concurrent.Executor executor) {
        return newContextPreservingFuture(delegate.applyToEitherAsync(other,
                AsyncContext.provider().wrapFunction(fn, capturedContext), executor), capturedContext);
    }

    @Override
    public CompletableFuture<Void> acceptEither(final CompletionStage<? extends T> other,
                                                final Consumer<? super T> action) {
        return newContextPreservingFuture(delegate.acceptEither(other,
                AsyncContext.provider().wrapConsumer(action, capturedContext)), capturedContext);
    }

    @Override
    public CompletableFuture<Void> acceptEitherAsync(final CompletionStage<? extends T> other,
                                                     final Consumer<? super T> action) {
        return newContextPreservingFuture(delegate.acceptEitherAsync(other,
                        AsyncContext.provider().wrapConsumer(action, capturedContext)), capturedContext);
    }

    @Override
    public CompletableFuture<Void> acceptEitherAsync(final CompletionStage<? extends T> other,
                                                     final Consumer<? super T> action,
                                                     final java.util.concurrent.Executor executor) {
        return newContextPreservingFuture(delegate.acceptEitherAsync(other,
                AsyncContext.provider().wrapConsumer(action, capturedContext), executor), capturedContext);
    }

    @Override
    public CompletableFuture<Void> runAfterEither(final CompletionStage<?> other, final Runnable action) {
        return newContextPreservingFuture(delegate.runAfterEither(other,
                AsyncContext.provider().wrapRunnable(action, capturedContext)), capturedContext);
    }

    @Override
    public CompletableFuture<Void> runAfterEitherAsync(final CompletionStage<?> other, final Runnable action) {
        return newContextPreservingFuture(delegate.runAfterEitherAsync(other,
                        AsyncContext.provider().wrapRunnable(action, capturedContext)), capturedContext);
    }

    @Override
    public CompletableFuture<Void> runAfterEitherAsync(final CompletionStage<?> other, final Runnable action,
                                                       final java.util.concurrent.Executor executor) {
        return newContextPreservingFuture(delegate.runAfterEitherAsync(other,
                AsyncContext.provider().wrapRunnable(action, capturedContext), executor), capturedContext);
    }

    @Override
    public <U> CompletableFuture<U> thenCompose(final Function<? super T, ? extends CompletionStage<U>> fn) {
        return newContextPreservingFuture(delegate.thenCompose(
                AsyncContext.provider().wrapFunction(fn, capturedContext)), capturedContext);
    }

    @Override
    public <U> CompletableFuture<U> thenComposeAsync(final Function<? super T, ? extends CompletionStage<U>> fn) {
        return newContextPreservingFuture(delegate.thenComposeAsync(
                AsyncContext.provider().wrapFunction(fn, capturedContext)), capturedContext);
    }

    @Override
    public <U> CompletableFuture<U> thenComposeAsync(final Function<? super T, ? extends CompletionStage<U>> fn,
                                                     final java.util.concurrent.Executor executor) {
        return newContextPreservingFuture(delegate.thenComposeAsync(
                AsyncContext.provider().wrapFunction(fn, capturedContext), executor), capturedContext);
    }

    @Override
    public CompletableFuture<T> exceptionally(final Function<Throwable, ? extends T> fn) {
        return newContextPreservingFuture(delegate.exceptionally(
                AsyncContext.provider().wrapFunction(fn, capturedContext)), capturedContext);
    }

    @Override
    public CompletableFuture<T> whenComplete(final BiConsumer<? super T, ? super Throwable> action) {
        return newContextPreservingFuture(delegate.whenComplete(
                AsyncContext.provider().wrapBiConsumer(action, capturedContext)), capturedContext);
    }

    @Override
    public CompletableFuture<T> whenCompleteAsync(final BiConsumer<? super T, ? super Throwable> action) {
        return newContextPreservingFuture(delegate.whenCompleteAsync(
                AsyncContext.provider().wrapBiConsumer(action, capturedContext)), capturedContext);
    }

    @Override
    public CompletableFuture<T> whenCompleteAsync(final BiConsumer<? super T, ? super Throwable> action,
                                                  final java.util.concurrent.Executor executor) {
        return newContextPreservingFuture(delegate.whenCompleteAsync(
                AsyncContext.provider().wrapBiConsumer(action, capturedContext), executor), capturedContext);
    }

    @Override
    public <U> CompletableFuture<U> handle(final BiFunction<? super T, Throwable, ? extends U> fn) {
        return newContextPreservingFuture(delegate.handle(
                AsyncContext.provider().wrapBiFunction(fn, capturedContext)), capturedContext);
    }

    @Override
    public <U> CompletableFuture<U> handleAsync(final BiFunction<? super T, Throwable, ? extends U> fn) {
        return newContextPreservingFuture(delegate.handleAsync(
                AsyncContext.provider().wrapBiFunction(fn, capturedContext)), capturedContext);
    }

    @Override
    public <U> CompletableFuture<U> handleAsync(final BiFunction<? super T, Throwable, ? extends U> fn,
                                                final Executor executor) {
        return newContextPreservingFuture(delegate.handleAsync(
                AsyncContext.provider().wrapBiFunction(fn, capturedContext), executor), capturedContext);
    }
    // CompletionStage end

    // CompletableFuture begin
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
        super.cancel(mayInterruptIfRunning);
        return delegate.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean complete(@Nullable T value) {
        super.complete(value);
        return delegate.complete(value);
    }

    @Override
    public boolean completeExceptionally(Throwable ex) {
        super.completeExceptionally(ex);
        return delegate.completeExceptionally(ex);
    }

    @Override
    public void obtrudeValue(@Nullable T value) {
        super.obtrudeValue(value);
        delegate.obtrudeValue(value);
    }

    @Override
    public void obtrudeException(Throwable ex) {
        super.obtrudeException(ex);
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
    // CompletableFuture end
}
