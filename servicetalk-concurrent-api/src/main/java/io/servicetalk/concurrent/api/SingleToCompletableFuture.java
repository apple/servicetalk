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
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.internal.SequentialCancellable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.CancelPropagatingCompletableFuture.newCancelPropagatingFuture;

final class SingleToCompletableFuture<T> extends CompletableFuture<T> implements Subscriber<T> {
    private final SequentialCancellable cancellable;

    private SingleToCompletableFuture() {
        cancellable = new SequentialCancellable();
    }

    static <X> CompletableFuture<X> createAndSubscribe(Single<X> original) {
        SingleToCompletableFuture<X> future = new SingleToCompletableFuture<>();
        AsyncContextProvider provider = AsyncContext.provider();
        return provider.wrapCompletableFuture(future, original.subscribeAndReturnContext(future, provider));
    }

    private void disableCancellable() {
        // If the source terminates "normally", then we don't need to cancel the subscription. However if
        // CompletableFuture#complete(T) (and related methods) are used to complete the future we do want to cancel the
        // upstream subscription because we are no longer interested in its results. This is naturally racy due to the
        // CompletableFuture APIs but a best effort is made to avoid cancelling when possible.
        cancellable.nextCancellable(IGNORE_CANCEL);
    }

    // Subscriber begin
    @Override
    public void onSubscribe(final Cancellable cancellable) {
        this.cancellable.nextCancellable(cancellable);
    }

    @Override
    public void onSuccess(@Nullable final T result) {
        disableCancellable();
        super.complete(result);
    }

    @Override
    public void onError(final Throwable t) {
        disableCancellable();
        super.completeExceptionally(t);
    }
    // Subscriber end

    // CompletionStage begin
    @Override
    public <U> CompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
        return newCancelPropagatingFuture(super.thenApply(fn), cancellable);
    }

    @Override
    public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return newCancelPropagatingFuture(super.thenApplyAsync(fn), cancellable);
    }

    @Override
    public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return newCancelPropagatingFuture(super.thenApplyAsync(fn, executor), cancellable);
    }

    @Override
    public CompletableFuture<Void> thenAccept(Consumer<? super T> action) {
        return newCancelPropagatingFuture(super.thenAccept(action), cancellable);
    }

    @Override
    public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action) {
        return newCancelPropagatingFuture(super.thenAcceptAsync(action), cancellable);
    }

    @Override
    public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return newCancelPropagatingFuture(super.thenAcceptAsync(action, executor), cancellable);
    }

    @Override
    public CompletableFuture<Void> thenRun(Runnable action) {
        return newCancelPropagatingFuture(super.thenRun(action), cancellable);
    }

    @Override
    public CompletableFuture<Void> thenRunAsync(Runnable action) {
        return newCancelPropagatingFuture(super.thenRunAsync(action), cancellable);
    }

    @Override
    public CompletableFuture<Void> thenRunAsync(Runnable action, Executor executor) {
        return newCancelPropagatingFuture(super.thenRunAsync(action, executor), cancellable);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombine(CompletionStage<? extends U> other,
                                                  BiFunction<? super T, ? super U, ? extends V> fn) {
        return newCancelPropagatingFuture(super.thenCombine(other, fn), cancellable);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                        BiFunction<? super T, ? super U, ? extends V> fn) {
        return newCancelPropagatingFuture(super.thenCombineAsync(other, fn), cancellable);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                        BiFunction<? super T, ? super U, ? extends V> fn,
                                                        Executor executor) {
        return newCancelPropagatingFuture(super.thenCombineAsync(other, fn, executor), cancellable);
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBoth(CompletionStage<? extends U> other,
                                                      BiConsumer<? super T, ? super U> action) {
        return newCancelPropagatingFuture(super.thenAcceptBoth(other, action), cancellable);
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                           BiConsumer<? super T, ? super U> action) {
        return newCancelPropagatingFuture(super.thenAcceptBothAsync(other, action), cancellable);
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                           BiConsumer<? super T, ? super U> action,
                                                           Executor executor) {
        return newCancelPropagatingFuture(super.thenAcceptBothAsync(other, action, executor),
                cancellable);
    }

    @Override
    public CompletableFuture<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return newCancelPropagatingFuture(super.runAfterBoth(other, action), cancellable);
    }

    @Override
    public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return newCancelPropagatingFuture(super.runAfterBothAsync(other, action), cancellable);
    }

    @Override
    public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return newCancelPropagatingFuture(super.runAfterBothAsync(other, action, executor),
                cancellable);
    }

    @Override
    public <U> CompletableFuture<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return newCancelPropagatingFuture(super.applyToEither(other, fn), cancellable);
    }

    @Override
    public <U> CompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return newCancelPropagatingFuture(super.applyToEitherAsync(other, fn), cancellable);
    }

    @Override
    public <U> CompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn,
                                                       Executor executor) {
        return newCancelPropagatingFuture(super.applyToEitherAsync(other, fn, executor),
                cancellable);
    }

    @Override
    public CompletableFuture<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return newCancelPropagatingFuture(super.acceptEither(other, action), cancellable);
    }

    @Override
    public CompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return newCancelPropagatingFuture(super.acceptEitherAsync(other, action), cancellable);
    }

    @Override
    public CompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action,
                                                     Executor executor) {
        return newCancelPropagatingFuture(super.acceptEitherAsync(other, action, executor),
                cancellable);
    }

    @Override
    public CompletableFuture<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return newCancelPropagatingFuture(super.runAfterEither(other, action), cancellable);
    }

    @Override
    public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return newCancelPropagatingFuture(super.runAfterEitherAsync(other, action), cancellable);
    }

    @Override
    public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return newCancelPropagatingFuture(super.runAfterEitherAsync(other, action, executor),
                cancellable);
    }

    @Override
    public <U> CompletableFuture<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return newCancelPropagatingFuture(super.thenCompose(fn), cancellable);
    }

    @Override
    public <U> CompletableFuture<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return newCancelPropagatingFuture(super.thenComposeAsync(fn), cancellable);
    }

    @Override
    public <U> CompletableFuture<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn,
                                                     Executor executor) {
        return newCancelPropagatingFuture(super.thenComposeAsync(fn, executor), cancellable);
    }

    @Override
    public CompletableFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return newCancelPropagatingFuture(super.exceptionally(fn), cancellable);
    }

    @Override
    public CompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return newCancelPropagatingFuture(super.whenComplete(action), cancellable);
    }

    @Override
    public CompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return newCancelPropagatingFuture(super.whenCompleteAsync(action), cancellable);
    }

    @Override
    public CompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action,
                                                  Executor executor) {
        return newCancelPropagatingFuture(super.whenCompleteAsync(action, executor), cancellable);
    }

    @Override
    public <U> CompletableFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return newCancelPropagatingFuture(super.handle(fn), cancellable);
    }

    @Override
    public <U> CompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return newCancelPropagatingFuture(super.handleAsync(fn), cancellable);
    }

    @Override
    public <U> CompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn,
                                                Executor executor) {
        return newCancelPropagatingFuture(super.handleAsync(fn, executor), cancellable);
    }
    // CompletionStage end

    // CompletableFuture begin
    @Override
    public boolean complete(T value) {
        try {
            return super.complete(value);
        } finally {
            cancellable.cancel();
        }
    }

    @Override
    public boolean completeExceptionally(Throwable ex) {
        try {
            return super.completeExceptionally(ex);
        } finally {
            cancellable.cancel();
        }
    }

    @Override
    public void obtrudeValue(T value) {
        try {
            super.obtrudeValue(value);
        } finally {
            cancellable.cancel();
        }
    }

    @Override
    public void obtrudeException(Throwable ex) {
        try {
            super.obtrudeException(ex);
        } finally {
            cancellable.cancel();
        }
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        try {
            return super.cancel(mayInterruptIfRunning);
        } finally {
            cancellable.cancel();
        }
    }
    // CompletableFuture end
}
