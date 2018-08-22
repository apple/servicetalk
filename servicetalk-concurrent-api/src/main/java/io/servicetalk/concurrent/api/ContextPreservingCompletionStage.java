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
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.DefaultAsyncContextProvider.INSTANCE;
import static java.util.Objects.requireNonNull;

final class ContextPreservingCompletionStage<T> implements CompletionStage<T> {
    private final CompletionStage<T> delegate;

    private ContextPreservingCompletionStage(CompletionStage<T> delegate) {
        this.delegate = requireNonNull(delegate);
    }

    static <X> CompletionStage<X> of(CompletionStage<X> stage) {
        return stage instanceof ContextPreservingCompletionStage ||
                stage instanceof ContextPreservingCompletableFuture ? stage :
                new ContextPreservingCompletionStage<>(stage);
    }

    static <X> CompletionStage<X> unwrap(CompletionStage<X> stage) {
        return stage instanceof ContextPreservingCompletionStage ? ((ContextPreservingCompletionStage) stage).delegate :
                stage instanceof ContextPreservingCompletableFuture ?
                        ((ContextPreservingCompletableFuture) stage).delegate : stage;
    }

    @Override
    public <U> CompletionStage<U> thenApply(final Function<? super T, ? extends U> fn) {
        return new ContextPreservingCompletionStage<>(delegate.thenApply(INSTANCE.wrap(fn)));
    }

    @Override
    public <U> CompletionStage<U> thenApplyAsync(final Function<? super T, ? extends U> fn) {
        return new ContextPreservingCompletionStage<>(delegate.thenApplyAsync(INSTANCE.wrap(fn)));
    }

    @Override
    public <U> CompletionStage<U> thenApplyAsync(final Function<? super T, ? extends U> fn,
                                                 final Executor executor) {
        return new ContextPreservingCompletionStage<>(delegate.thenApplyAsync(INSTANCE.wrap(fn), executor));
    }

    @Override
    public CompletionStage<Void> thenAccept(final Consumer<? super T> action) {
        return new ContextPreservingCompletionStage<>(delegate.thenAccept(INSTANCE.wrap(action)));
    }

    @Override
    public CompletionStage<Void> thenAcceptAsync(final Consumer<? super T> action) {
        return new ContextPreservingCompletionStage<>(delegate.thenAcceptAsync(INSTANCE.wrap(action)));
    }

    @Override
    public CompletionStage<Void> thenAcceptAsync(final Consumer<? super T> action, final Executor executor) {
        return new ContextPreservingCompletionStage<>(delegate.thenAcceptAsync(INSTANCE.wrap(action), executor));
    }

    @Override
    public CompletionStage<Void> thenRun(final Runnable action) {
        return new ContextPreservingCompletionStage<>(delegate.thenRun(INSTANCE.wrap(action)));
    }

    @Override
    public CompletionStage<Void> thenRunAsync(final Runnable action) {
        return new ContextPreservingCompletionStage<>(delegate.thenRunAsync(INSTANCE.wrap(action)));
    }

    @Override
    public CompletionStage<Void> thenRunAsync(final Runnable action, final Executor executor) {
        return new ContextPreservingCompletionStage<>(delegate.thenRunAsync(INSTANCE.wrap(action), executor));
    }

    @Override
    public <U, V> CompletionStage<V> thenCombine(final CompletionStage<? extends U> other,
                                                 final BiFunction<? super T, ? super U, ? extends V> fn) {
        return new ContextPreservingCompletionStage<>(delegate.thenCombine(other, INSTANCE.wrap(fn)));
    }

    @Override
    public <U, V> CompletionStage<V> thenCombineAsync(final CompletionStage<? extends U> other,
                                                      final BiFunction<? super T, ? super U, ? extends V> fn) {
        return new ContextPreservingCompletionStage<>(delegate.thenCombineAsync(other, INSTANCE.wrap(fn)));
    }

    @Override
    public <U, V> CompletionStage<V> thenCombineAsync(final CompletionStage<? extends U> other,
                                                      final BiFunction<? super T, ? super U, ? extends V> fn,
                                                      final Executor executor) {
        return new ContextPreservingCompletionStage<>(delegate.thenCombineAsync(other, INSTANCE.wrap(fn), executor));
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBoth(final CompletionStage<? extends U> other,
                                                    final BiConsumer<? super T, ? super U> action) {
        return new ContextPreservingCompletionStage<>(delegate.thenAcceptBoth(other, INSTANCE.wrap(action)));
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBothAsync(final CompletionStage<? extends U> other,
                                                         final BiConsumer<? super T, ? super U> action) {
        return new ContextPreservingCompletionStage<>(delegate.thenAcceptBothAsync(other, INSTANCE.wrap(action)));
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBothAsync(final CompletionStage<? extends U> other,
                                                         final BiConsumer<? super T, ? super U> action,
                                                         final Executor executor) {
        return new ContextPreservingCompletionStage<>(
                delegate.thenAcceptBothAsync(other, INSTANCE.wrap(action), executor));
    }

    @Override
    public CompletionStage<Void> runAfterBoth(final CompletionStage<?> other, final Runnable action) {
        return new ContextPreservingCompletionStage<>(delegate.runAfterBoth(other, INSTANCE.wrap(action)));
    }

    @Override
    public CompletionStage<Void> runAfterBothAsync(final CompletionStage<?> other, final Runnable action) {
        return new ContextPreservingCompletionStage<>(delegate.runAfterBothAsync(other, INSTANCE.wrap(action)));
    }

    @Override
    public CompletionStage<Void> runAfterBothAsync(final CompletionStage<?> other, final Runnable action,
                                                   final Executor executor) {
        return new ContextPreservingCompletionStage<>(delegate.runAfterBothAsync(other, INSTANCE.wrap(action), executor));
    }

    @Override
    public <U> CompletionStage<U> applyToEither(final CompletionStage<? extends T> other,
                                                final Function<? super T, U> fn) {
        return new ContextPreservingCompletionStage<>(delegate.applyToEither(other, INSTANCE.wrap(fn)));
    }

    @Override
    public <U> CompletionStage<U> applyToEitherAsync(final CompletionStage<? extends T> other,
                                                     final Function<? super T, U> fn) {
        return new ContextPreservingCompletionStage<>(delegate.applyToEitherAsync(other, INSTANCE.wrap(fn)));
    }

    @Override
    public <U> CompletionStage<U> applyToEitherAsync(final CompletionStage<? extends T> other,
                                                     final Function<? super T, U> fn, final Executor executor) {
        return new ContextPreservingCompletionStage<>(delegate.applyToEitherAsync(other, INSTANCE.wrap(fn), executor));
    }

    @Override
    public CompletionStage<Void> acceptEither(final CompletionStage<? extends T> other,
                                              final Consumer<? super T> action) {
        return new ContextPreservingCompletionStage<>(delegate.acceptEither(other, INSTANCE.wrap(action)));
    }

    @Override
    public CompletionStage<Void> acceptEitherAsync(final CompletionStage<? extends T> other,
                                                   final Consumer<? super T> action) {
        return new ContextPreservingCompletionStage<>(delegate.acceptEitherAsync(other, INSTANCE.wrap(action)));
    }

    @Override
    public CompletionStage<Void> acceptEitherAsync(final CompletionStage<? extends T> other,
                                                   final Consumer<? super T> action, final Executor executor) {
        return new ContextPreservingCompletionStage<>(delegate.acceptEitherAsync(other, INSTANCE.wrap(action), executor));
    }

    @Override
    public CompletionStage<Void> runAfterEither(final CompletionStage<?> other, final Runnable action) {
        return new ContextPreservingCompletionStage<>(delegate.runAfterEither(other, INSTANCE.wrap(action)));
    }

    @Override
    public CompletionStage<Void> runAfterEitherAsync(final CompletionStage<?> other, final Runnable action) {
        return new ContextPreservingCompletionStage<>(delegate.runAfterEitherAsync(other, INSTANCE.wrap(action)));
    }

    @Override
    public CompletionStage<Void> runAfterEitherAsync(final CompletionStage<?> other, final Runnable action,
                                                     final Executor executor) {
        return new ContextPreservingCompletionStage<>(
                delegate.runAfterEitherAsync(other, INSTANCE.wrap(action), executor));
    }

    @Override
    public <U> CompletionStage<U> thenCompose(final Function<? super T, ? extends CompletionStage<U>> fn) {
        return new ContextPreservingCompletionStage<>(delegate.thenCompose(INSTANCE.wrap(fn)));
    }

    @Override
    public <U> CompletionStage<U> thenComposeAsync(final Function<? super T, ? extends CompletionStage<U>> fn) {
        return new ContextPreservingCompletionStage<>(delegate.thenComposeAsync(INSTANCE.wrap(fn)));
    }

    @Override
    public <U> CompletionStage<U> thenComposeAsync(final Function<? super T, ? extends CompletionStage<U>> fn,
                                                   final Executor executor) {
        return new ContextPreservingCompletionStage<>(delegate.thenComposeAsync(INSTANCE.wrap(fn), executor));
    }

    @Override
    public CompletionStage<T> exceptionally(final Function<Throwable, ? extends T> fn) {
        return new ContextPreservingCompletionStage<>(delegate.exceptionally(INSTANCE.wrap(fn)));
    }

    @Override
    public CompletionStage<T> whenComplete(final BiConsumer<? super T, ? super Throwable> action) {
        return new ContextPreservingCompletionStage<>(delegate.whenComplete(INSTANCE.wrap(action)));
    }

    @Override
    public CompletionStage<T> whenCompleteAsync(final BiConsumer<? super T, ? super Throwable> action) {
        return new ContextPreservingCompletionStage<>(delegate.whenCompleteAsync(INSTANCE.wrap(action)));
    }

    @Override
    public CompletionStage<T> whenCompleteAsync(final BiConsumer<? super T, ? super Throwable> action,
                                                final Executor executor) {
        return new ContextPreservingCompletionStage<>(delegate.whenCompleteAsync(INSTANCE.wrap(action), executor));
    }

    @Override
    public <U> CompletionStage<U> handle(final BiFunction<? super T, Throwable, ? extends U> fn) {
        return new ContextPreservingCompletionStage<>(delegate.handle(INSTANCE.wrap(fn)));
    }

    @Override
    public <U> CompletionStage<U> handleAsync(final BiFunction<? super T, Throwable, ? extends U> fn) {
        return new ContextPreservingCompletionStage<>(delegate.handleAsync(INSTANCE.wrap(fn)));
    }

    @Override
    public <U> CompletionStage<U> handleAsync(final BiFunction<? super T, Throwable, ? extends U> fn,
                                              final Executor executor) {
        return new ContextPreservingCompletionStage<>(delegate.handleAsync(INSTANCE.wrap(fn), executor));
    }

    @Override
    public CompletableFuture<T> toCompletableFuture() {
        return new ContextPreservingCompletableFuture<>(delegate.toCompletableFuture());
    }
}
