/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.SingleSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

final class DefaultAsyncContextProvider implements AsyncContextProvider {
    static final AsyncContextProvider INSTANCE = new DefaultAsyncContextProvider();

    private static final AsyncContextMapThreadLocal contextLocal = new AsyncContextMapThreadLocal();

    private DefaultAsyncContextProvider() {
        // singleton
    }

    @Override
    public AsyncContextMap contextMap() {
        return contextLocal.get();
    }

    @Override
    public void contextMap(AsyncContextMap newContextMap) {
        contextLocal.set(newContextMap);
    }

    @Override
    public CompletableSource.Subscriber wrapCancellable(final CompletableSource.Subscriber subscriber,
                                                        final AsyncContextMap current) {
        return new ContextPreservingCancellableCompletableSubscriber(subscriber, current);
    }

    @Override
    public CompletableSource.Subscriber wrap(CompletableSource.Subscriber subscriber, AsyncContextMap current) {
        return new ContextPreservingCompletableSubscriber(subscriber, current);
    }

    @Override
    public <T> SingleSource.Subscriber<T> wrapCancellable(final SingleSource.Subscriber<T> subscriber,
                                                          final AsyncContextMap current) {
        return new ContextPreservingCancellableSingleSubscriber<>(subscriber, current);
    }

    @Override
    public <T> SingleSource.Subscriber<T> wrap(SingleSource.Subscriber<T> subscriber, AsyncContextMap current) {
        return new ContextPreservingSingleSubscriber<>(subscriber, current);
    }

    @Override
    public <T> Subscriber<T> wrapSubscription(final Subscriber<T> subscriber, final AsyncContextMap current) {
        return ContextPreservingSubscriptionSubscriber.wrap(subscriber, current);
    }

    @Override
    public <T> Subscriber<T> wrap(Subscriber<T> subscriber, AsyncContextMap current) {
        return new ContextPreservingSubscriber<>(subscriber, current);
    }

    @Override
    public Executor wrap(Executor executor) {
        return ContextPreservingExecutor.of(executor);
    }

    @Override
    public Executor unwrap(final Executor executor) {
        return ContextPreservingExecutor.unwrap(executor);
    }

    @Override
    public ExecutorService wrap(ExecutorService executor) {
        return ContextPreservingExecutorService.of(executor);
    }

    @Override
    public ExecutorService unwrap(final ExecutorService executor) {
        return ContextPreservingExecutorService.unwrap(executor);
    }

    @Override
    public io.servicetalk.concurrent.api.Executor wrap(final io.servicetalk.concurrent.api.Executor executor) {
        return ContextPreservingStExecutor.of(executor);
    }

    @Override
    public io.servicetalk.concurrent.Executor unwrap(final io.servicetalk.concurrent.Executor executor) {
        return ContextPreservingStExecutor.unwrap(executor);
    }

    @Override
    public <T> CompletableFuture<T> wrap(final CompletableFuture<T> future, AsyncContextMap current) {
        return ContextPreservingCompletableFuture.newInstance(future, current);
    }

    @Override
    public ScheduledExecutorService wrap(ScheduledExecutorService executor) {
        return ContextPreservingScheduledExecutorService.of(executor);
    }

    @Override
    public ScheduledExecutorService unwrap(final ScheduledExecutorService executor) {
        return ContextPreservingScheduledExecutorService.unwrap(executor);
    }

    @Override
    public Runnable wrap(final Runnable runnable, final AsyncContextMap contextMap) {
        return new ContextPreservingRunnable(runnable, contextMap);
    }

    @Override
    public <T> Consumer<T> wrap(final Consumer<T> consumer, final AsyncContextMap contextMap) {
        return new ContextPreservingConsumer<>(consumer, contextMap);
    }

    @Override
    public <T, U> Function<T, U> wrap(Function<T, U> func, AsyncContextMap contextMap) {
        return new ContextPreservingFunction<>(func, contextMap);
    }

    @Override
    public <T, U> BiConsumer<T, U> wrap(BiConsumer<T, U> consumer, AsyncContextMap contextMap) {
        return new ContextPreservingBiConsumer<>(consumer, contextMap);
    }

    @Override
    public <T, U, V> BiFunction<T, U, V> wrap(BiFunction<T, U, V> func, AsyncContextMap contextMap) {
        return new ContextPreservingBiFunction<>(func, contextMap);
    }
}
