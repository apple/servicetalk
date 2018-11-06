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

final class ExecutorCompletionStageToCompletableFuture<T> extends CompletableFuture<T> {
    private final ExecutorCompletionStage<T> stage;

    private ExecutorCompletionStageToCompletableFuture(ExecutorCompletionStage<T> stage) {
        this.stage = stage;
    }

    @Override
    public <U> CompletableFuture<U> thenApply(final Function<? super T, ? extends U> fn) {
        return toCompletableFuture(stage.thenApply(fn));
    }

    @Override
    public <U> CompletableFuture<U> thenApplyAsync(final Function<? super T, ? extends U> fn) {
        return toCompletableFuture(stage.thenApplyAsync(fn));
    }

    @Override
    public <U> CompletableFuture<U> thenApplyAsync(final Function<? super T, ? extends U> fn, final Executor executor) {
        return toCompletableFuture(stage.thenApplyAsync(fn, executor));
    }

    @Override
    public CompletableFuture<Void> thenAccept(final Consumer<? super T> action) {
        return toCompletableFuture(stage.thenAccept(action));
    }

    @Override
    public CompletableFuture<Void> thenAcceptAsync(final Consumer<? super T> action) {
        return toCompletableFuture(stage.thenAcceptAsync(action));
    }

    @Override
    public CompletableFuture<Void> thenAcceptAsync(final Consumer<? super T> action, final Executor executor) {
        return toCompletableFuture(stage.thenAcceptAsync(action, executor));
    }

    @Override
    public CompletableFuture<Void> thenRun(final Runnable action) {
        return toCompletableFuture(stage.thenRun(action));
    }

    @Override
    public CompletableFuture<Void> thenRunAsync(final Runnable action) {
        return toCompletableFuture(stage.thenRunAsync(action));
    }

    @Override
    public CompletableFuture<Void> thenRunAsync(final Runnable action, final Executor executor) {
        return toCompletableFuture(stage.thenRunAsync(action, executor));
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombine(final CompletionStage<? extends U> other,
                                                   final BiFunction<? super T, ? super U, ? extends V> fn) {
        return toCompletableFuture(stage.thenCombine(other, fn));
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombineAsync(final CompletionStage<? extends U> other,
                                                        final BiFunction<? super T, ? super U, ? extends V> fn) {
        return toCompletableFuture(stage.thenCombineAsync(other, fn));
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombineAsync(final CompletionStage<? extends U> other,
                                                        final BiFunction<? super T, ? super U, ? extends V> fn,
                                                        final Executor executor) {
        return toCompletableFuture(stage.thenCombineAsync(other, fn, executor));
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBoth(final CompletionStage<? extends U> other,
                                                      final BiConsumer<? super T, ? super U> action) {
        return toCompletableFuture(stage.thenAcceptBoth(other, action));
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBothAsync(final CompletionStage<? extends U> other,
                                                           final BiConsumer<? super T, ? super U> action) {
        return toCompletableFuture(stage.thenAcceptBothAsync(other, action));
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBothAsync(final CompletionStage<? extends U> other,
                                                           final BiConsumer<? super T, ? super U> action,
                                                           final Executor executor) {
        return toCompletableFuture(stage.thenAcceptBothAsync(other, action, executor));
    }

    @Override
    public CompletableFuture<Void> runAfterBoth(final CompletionStage<?> other, final Runnable action) {
        return toCompletableFuture(stage.runAfterBoth(other, action));
    }

    @Override
    public CompletableFuture<Void> runAfterBothAsync(final CompletionStage<?> other, final Runnable action) {
        return toCompletableFuture(stage.runAfterBothAsync(other, action));
    }

    @Override
    public CompletableFuture<Void> runAfterBothAsync(final CompletionStage<?> other,
                                                     final Runnable action,
                                                     final Executor executor) {
        return toCompletableFuture(stage.runAfterBothAsync(other, action, executor));
    }

    @Override
    public <U> CompletableFuture<U> applyToEither(final CompletionStage<? extends T> other,
                                                  final Function<? super T, U> fn) {
        return toCompletableFuture(stage.applyToEither(other, fn));
    }

    @Override
    public <U> CompletableFuture<U> applyToEitherAsync(final CompletionStage<? extends T> other,
                                                       final Function<? super T, U> fn) {
        return toCompletableFuture(stage.applyToEitherAsync(other, fn));
    }

    @Override
    public <U> CompletableFuture<U> applyToEitherAsync(final CompletionStage<? extends T> other,
                                                       final Function<? super T, U> fn, final Executor executor) {
        return toCompletableFuture(stage.applyToEitherAsync(other, fn, executor));
    }

    @Override
    public CompletableFuture<Void> acceptEither(final CompletionStage<? extends T> other,
                                                final Consumer<? super T> action) {
        return toCompletableFuture(stage.acceptEither(other, action));
    }

    @Override
    public CompletableFuture<Void> acceptEitherAsync(final CompletionStage<? extends T> other,
                                                     final Consumer<? super T> action) {
        return toCompletableFuture(stage.acceptEitherAsync(other, action));
    }

    @Override
    public CompletableFuture<Void> acceptEitherAsync(final CompletionStage<? extends T> other,
                                                     final Consumer<? super T> action, final Executor executor) {
        return toCompletableFuture(stage.acceptEitherAsync(other, action, executor));
    }

    @Override
    public CompletableFuture<Void> runAfterEither(final CompletionStage<?> other, final Runnable action) {
        return toCompletableFuture(stage.runAfterEither(other, action));
    }

    @Override
    public CompletableFuture<Void> runAfterEitherAsync(final CompletionStage<?> other, final Runnable action) {
        return toCompletableFuture(stage.runAfterEitherAsync(other, action));
    }

    @Override
    public CompletableFuture<Void> runAfterEitherAsync(final CompletionStage<?> other,
                                                       final Runnable action, final Executor executor) {
        return toCompletableFuture(stage.runAfterEitherAsync(other, action, executor));
    }

    @Override
    public <U> CompletableFuture<U> thenCompose(final Function<? super T, ? extends CompletionStage<U>> fn) {
        return toCompletableFuture(stage.thenCompose(fn));
    }

    @Override
    public <U> CompletableFuture<U> thenComposeAsync(final Function<? super T, ? extends CompletionStage<U>> fn) {
        return toCompletableFuture(stage.thenComposeAsync(fn));
    }

    @Override
    public <U> CompletableFuture<U> thenComposeAsync(final Function<? super T, ? extends CompletionStage<U>> fn,
                                                     final Executor executor) {
        return toCompletableFuture(stage.thenComposeAsync(fn, executor));
    }

    @Override
    public CompletableFuture<T> exceptionally(final Function<Throwable, ? extends T> fn) {
        return toCompletableFuture(stage.exceptionally(fn));
    }

    @Override
    public CompletableFuture<T> whenComplete(final BiConsumer<? super T, ? super Throwable> action) {
        return toCompletableFuture(stage.whenComplete(action));
    }

    @Override
    public CompletableFuture<T> whenCompleteAsync(final BiConsumer<? super T, ? super Throwable> action) {
        return toCompletableFuture(stage.whenCompleteAsync(action));
    }

    @Override
    public CompletableFuture<T> whenCompleteAsync(final BiConsumer<? super T, ? super Throwable> action,
                                                  final Executor executor) {
        return toCompletableFuture(stage.whenCompleteAsync(action, executor));
    }

    @Override
    public <U> CompletableFuture<U> handle(final BiFunction<? super T, Throwable, ? extends U> fn) {
        return toCompletableFuture(stage.handle(fn));
    }

    @Override
    public <U> CompletableFuture<U> handleAsync(final BiFunction<? super T, Throwable, ? extends U> fn) {
        return toCompletableFuture(stage.handleAsync(fn));
    }

    @Override
    public <U> CompletableFuture<U> handleAsync(final BiFunction<? super T, Throwable, ? extends U> fn,
                                                final Executor executor) {
        return toCompletableFuture(stage.handleAsync(fn, executor));
    }

    @Override
    public CompletableFuture<T> toCompletableFuture() {
        return this;
    }

    //
    // Method not part of the CompletionStage interface
    //

    @Override
    public boolean isDone() {
        return stage.isDone();
    }

    @Override
    public boolean isCancelled() {
        return stage.isCancelled();
    }

    @Override
    public boolean isCompletedExceptionally() {
        return stage.isCompletedExceptionally();
    }

    @Nullable
    @Override
    public T get() throws InterruptedException, ExecutionException {
        return stage.get();
    }

    @Nullable
    @Override
    public T get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return stage.get(timeout, unit);
    }

    @Nullable
    @Override
    public T join() {
        return stage.join();
    }

    @Nullable
    @Override
    public T getNow(@Nullable T valueIfAbsent) {
        return stage.getNow(valueIfAbsent);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return stage.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean complete(@Nullable T value) {
        stage.complete(value);
        return super.complete(value);
    }

    @Override
    public boolean completeExceptionally(Throwable ex) {
        stage.completeExceptionally(ex);
        return super.completeExceptionally(ex);
    }

    @Override
    public void obtrudeValue(@Nullable T value) {
        stage.obtrudeValue(value);
        super.obtrudeValue(value);
    }

    @Override
    public void obtrudeException(Throwable ex) {
        stage.obtrudeException(ex);
        super.obtrudeException(ex);
    }

    @Override
    public int getNumberOfDependents() {
        return stage.getListenerCount();
    }

    @Override
    public String toString() {
        return "F(" + stage.toString() + ")";
    }

    /**
     * Creates a {@link CompletableFuture} for the passed {@link ExecutorCompletionStage}.
     *
     * @param stage {@link ExecutorCompletionStage} for which the {@link CompletableFuture} is to be created.
     * @param <T> Type of the result for the returned {@link CompletableFuture}.
     * @return {@link CompletableFuture} for the passed {@link ExecutorCompletionStage}.
     */
    static <T> CompletableFuture<T> forStage(ExecutorCompletionStage<T> stage) {
        ExecutorCompletionStageToCompletableFuture<T> f = new ExecutorCompletionStageToCompletableFuture<>(stage);
        stage.whenComplete((t, throwable) -> {
            if (throwable == null) {
                f.complete(t);
            } else {
                f.completeExceptionally(throwable);
            }
        });
        return f;
    }

    private static <U> CompletableFuture<U> toCompletableFuture(CompletionStage<U> stage) {
        return stage.toCompletableFuture();
    }
}
