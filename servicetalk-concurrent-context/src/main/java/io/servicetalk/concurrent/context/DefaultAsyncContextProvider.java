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
package io.servicetalk.concurrent.context;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;

import org.reactivestreams.Subscription;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

final class DefaultAsyncContextProvider implements AsyncContextProvider {
    public static final AsyncContextProvider INSTANCE = new DefaultAsyncContextProvider();

    private static final ThreadLocal<AsyncContextMap> contextLocal = ThreadLocal.withInitial(() -> CopyOnWriteContext.EMPTY_CONTEXT);
    private static final SimpleCopyOnWriteSet<AsyncContext.Listener> listeners = new SimpleCopyOnWriteSet<>(AsyncContext.Listener.class);

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
        final AsyncContext.Listener[] listenerArray = listeners.array();
        if (listenerArray.length == 0) {
            contextLocal.set(newContextMap);
            return;
        }
        final AsyncContextMap oldContextMap = contextLocal.get();
        if (oldContextMap != newContextMap) {
            contextLocal.set(newContextMap);
            int i = 0;
            // Use a do-while loop because we have already checked the first condition of the loop, so we can avoid
            // an extra conditional check on the first iteration of the loop.
            do {
                listenerArray[i].contextMapChanged(oldContextMap, newContextMap);
            } while (++i < listenerArray.length);
        }
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
    public <T> org.reactivestreams.Subscriber<T> wrap(org.reactivestreams.Subscriber<T> subscriber, AsyncContextMap current) {
        return new ContextPreservingSubscriber<>(subscriber, current);
    }

    @Override
    public Executor wrap(Executor executor) {
        return ContextPreservingExecutor.of(executor);
    }

    @Override
    public ExecutorService wrap(ExecutorService executor) {
        return ContextPreservingExecutorService.of(executor);
    }

    @Override
    public ScheduledExecutorService wrap(ScheduledExecutorService executor) {
        return ContextPreservingScheduledExecutorService.of(executor);
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
