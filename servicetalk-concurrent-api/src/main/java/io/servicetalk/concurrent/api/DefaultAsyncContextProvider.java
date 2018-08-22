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

import org.reactivestreams.Subscription;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

final class DefaultAsyncContextProvider implements AsyncContextProvider {
    public static final AsyncContextProvider INSTANCE = new DefaultAsyncContextProvider();

    private static final ThreadLocal<AsyncContextMap> contextLocal =
            ThreadLocal.withInitial(() -> CopyOnWriteAsyncContextMap.EMPTY_CONTEXT_MAP);
    private static final AsyncContextListenerSet listeners = new CopyOnWriteAsyncContextListenerSet();

    private DefaultAsyncContextProvider() {
        // singleton
    }

    @Override
    public boolean addListener(AsyncContext.Listener listener) {
        return listeners.add(listener);
    }

    @Override
    public boolean removeListener(AsyncContext.Listener listener) {
        return listeners.remove(listener);
    }

    @Override
    public void clearListeners() {
        listeners.clear();
    }

    @Override
    public AsyncContextMap getContextMap() {
        return contextLocal.get();
    }

    @Override
    public void setContextMap(AsyncContextMap newContextMap) {
        listeners.setContextMapAndNotifyListeners(newContextMap, contextLocal);
    }

    @Override
    public Completable.Subscriber wrap(Completable.Subscriber subscriber, AsyncContextMap current) {
        return new ContextPreservingCompletableSubscriber(subscriber, current);
    }

    @Override
    public <T> Single.Subscriber<T> wrap(Single.Subscriber<T> subscriber, AsyncContextMap current) {
        return new ContextPreservingSingleSubscriber<>(subscriber, current);
    }

    @Override
    public Subscription wrap(Subscription subscription, AsyncContextMap current) {
        return new ContextPreservingSubscription(subscription, current);
    }

    @Override
    public <T> org.reactivestreams.Subscriber<T> wrap(org.reactivestreams.Subscriber<T> subscriber,
                                                      AsyncContextMap current) {
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
    public io.servicetalk.concurrent.api.Executor unwrap(final io.servicetalk.concurrent.api.Executor executor) {
        return ContextPreservingStExecutor.unwrap(executor);
    }

    @Override
    public io.servicetalk.concurrent.Executor unwrap(final io.servicetalk.concurrent.Executor executor) {
        return ContextPreservingStExecutor.unwrap(executor);
    }

    @Override
    public <T> CompletionStage<T> wrap(final CompletionStage<T> stage) {
        return ContextPreservingCompletionStage.of(stage);
    }

    @Override
    public <T> CompletionStage<T> unwrap(final CompletionStage<T> stage) {
        return ContextPreservingCompletionStage.unwrap(stage);
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
    public Runnable wrap(Runnable runnable) {
        return new ContextPreservingRunnable(runnable);
    }

    @Override
    public <V> Callable<V> wrap(Callable<V> callable) {
        return new ContextPreservingCallable<>(callable);
    }

    @Override
    public <T> Consumer<T> wrap(Consumer<T> consumer) {
        return new ContextPreservingConsumer<>(consumer);
    }

    @Override
    public <T, U> Function<T, U> wrap(Function<T, U> func) {
        return new ContextPreservingFunction<>(func);
    }

    @Override
    public <T, U> BiConsumer<T, U> wrap(BiConsumer<T, U> consumer) {
        return new ContextPreservingBiConsumer<>(consumer);
    }

    @Override
    public <T, U, V> BiFunction<T, U, V> wrap(BiFunction<T, U, V> func) {
        return new ContextPreservingBiFunction<>(func);
    }
}
