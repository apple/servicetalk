/**
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

/**
 * Implementation that backs the {@link AsyncContext}.
 */
interface AsyncContextProvider {
    /**
     * Subscribe {@code listener} for notification of {@link AsyncContextProvider} change events.
     *
     * @param listener A listener which will be notified of {@link AsyncContextProvider} change events.
     * @return {@code true} if the listener was subscribed.
     */
    boolean addListener(AsyncContext.Listener listener);

    /**
     * Unsubscribe {@code listener} for notification of {@link AsyncContextProvider} change events.
     *
     * @param listener A listener which will no longer be notified of {@link AsyncContextProvider} change events.
     * @return {@code true} if the listener was unsubscribed.
     */
    boolean removeListener(AsyncContext.Listener listener);

    /**
     * Remove all listeners.
     */
    void clearListeners();

    /**
     * Get the current context.
     *
     * @return The current context.
     */
    AsyncContextMap getContextMap();

    /**
     * Set the current context.
     *
     * @param newContextMap The current context.
     */
    void setContextMap(AsyncContextMap newContextMap);

    /**
     * Wrap an {@link Completable.Subscriber} to ensure it is able to track {@link AsyncContext} correctly.
     * @param subscriber The subscriber to wrap.
     * @param current The current {@link AsyncContextMap}.
     * @return The wrapped subscriber.
     */
    Completable.Subscriber wrap(Completable.Subscriber subscriber, AsyncContextMap current);

    /**
     * Wrap an {@link Single.Subscriber} to ensure it is able to track {@link AsyncContext} correctly.
     * @param subscriber subscriber to wrap.
     * @param current The current {@link AsyncContextMap}.
     * @param <T> Type of the {@link Single}.
     * @return The wrapped subscriber.
     */
    <T> Single.Subscriber<T> wrap(Single.Subscriber<T> subscriber, AsyncContextMap current);

    /**
     * Wrap an {@link Subscription} to ensure it is able to track {@link AsyncContext} correctly.
     * @param subscription The subscription to wrap.
     * @param current The current {@link AsyncContextMap}.
     * @return The wrapped subscription.
     */
    Subscription wrap(Subscription subscription, AsyncContextMap current);

    /**
     * Wrap an {@link org.reactivestreams.Subscriber} to ensure it is able to track {@link AsyncContext} correctly.
     * @param subscriber The subscriber to wrap.
     * @param current The current {@link AsyncContextMap}.
     * @param <T> the type of element signaled to the {@link org.reactivestreams.Subscriber}.
     * @return The wrapped subscriber.
     */
    <T> org.reactivestreams.Subscriber<T> wrap(org.reactivestreams.Subscriber<T> subscriber, AsyncContextMap current);

    /**
     * Wrap an {@link Executor} to ensure it is able to track {@link AsyncContext} correctly.
     * @param executor The executor to wrap.
     * @return The wrapped executor.
     */
    Executor wrap(Executor executor);

    /**
     * Wrap an {@link ExecutorService} to ensure it is able to track {@link AsyncContext} correctly.
     * @param executor The executor to wrap.
     * @return The wrapped executor.
     */
    ExecutorService wrap(ExecutorService executor);

    /**
     * Wrap a {@link ScheduledExecutorService} to ensure it is able to track {@link AsyncContext} correctly.
     * @param executor The executor to wrap.
     * @return The wrapped executor.
     */
    ScheduledExecutorService wrap(ScheduledExecutorService executor);

    /**
     * Wrap a {@link Runnable} to ensure it is able to track {@link AsyncContext} correctly.
     * @param runnable The runnable to wrap.
     * @return The wrapped {@link Runnable}.
     */
    Runnable wrap(Runnable runnable);

    /**
     * Wrap a {@link Callable} to ensure it is able to track {@link AsyncContext} correctly.
     * @param callable The callable to wrap.
     * @param <V> The type of data returned by {@code callable}.
     * @return The wrapped {@link Callable}.
     */
    <V> Callable<V> wrap(Callable<V> callable);

    /**
     * Wrap a {@link Consumer} to ensure it is able to track {@link AsyncContext} correctly.
     * @param consumer The consumer to wrap.
     * @param <T> The type of data consumed by {@code consumer}.
     * @return The wrapped {@link Consumer}.
     */
    <T> Consumer<T> wrap(Consumer<T> consumer);

    /**
     * Wrap a {@link Function} to ensure it is able to track {@link AsyncContext} correctly.
     * @param func The function to wrap.
     * @param <T> The type of data consumed by {@code func}.
     * @param <U> The type of data returned by {@code func}.
     * @return The wrapped {@link Function}.
     */
    <T, U> Function<T, U> wrap(Function<T, U> func);

    /**
     * Wrap a {@link BiFunction} to ensure it is able to track {@link AsyncContext} correctly.
     * @param consumer The consumer to wrap.
     * @param <T> The type of data consumed by {@code func}.
     * @param <U> The type of data consumed by {@code func}.
     * @return The wrapped {@link BiConsumer}.
     */
    <T, U> BiConsumer<T, U> wrap(BiConsumer<T, U> consumer);

    /**
     * Wrap a {@link BiFunction} to ensure it is able to track {@link AsyncContext} correctly.
     * @param func The function to wrap.
     * @param <T> The type of data consumed by {@code func}.
     * @param <U> The type of data consumed by {@code func}.
     * @param <V> The type of data returned by {@code func}.
     * @return The wrapped {@link BiFunction}.
     */
    <T, U, V> BiFunction<T, U, V> wrap(BiFunction<T, U, V> func);
}
