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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.servicetalk.concurrent.PublisherSource.Subscriber;

/**
 * Implementation that backs the {@link AsyncContext}.
 */
interface AsyncContextProvider {
    /**
     * Get the current context.
     *
     * @return The current context.
     */
    AsyncContextMap contextMap();

    /**
     * Set the current context.
     *
     * @param newContextMap The current context.
     */
    void contextMap(AsyncContextMap newContextMap);

    /**
     * Wrap the {@link Cancellable} to ensure it is able to track
     * {@link AsyncContext} correctly.
     * @param subscriber The {@link CompletableSource.Subscriber} for which to wrap the corresponding
     * {@link Cancellable}.
     * @param current The current {@link AsyncContextMap}.
     * @return The wrapped {@link CompletableSource.Subscriber}.
     */
    CompletableSource.Subscriber wrapCancellable(CompletableSource.Subscriber subscriber, AsyncContextMap current);

    /**
     * Wrap an {@link CompletableSource.Subscriber} to ensure it is able to track {@link AsyncContext} correctly.
     * @param subscriber The subscriber to wrap.
     * @param current The current {@link AsyncContextMap}.
     * @return The wrapped subscriber.
     */
    CompletableSource.Subscriber wrapCompletableSubscriber(CompletableSource.Subscriber subscriber,
                                                           AsyncContextMap current);

    /**
     * Wrap an {@link CompletableSource.Subscriber} and any {@link Cancellable}s from
     * {@link CompletableSource.Subscriber#onSubscribe(Cancellable)} to ensure it is able to track {@link AsyncContext}
     * correctly.
     * @param subscriber The subscriber to wrap.
     * @param current The current {@link AsyncContextMap}.
     * @return The wrapped subscriber.
     */
    CompletableSource.Subscriber wrapCompletableSubscriberAndCancellable(CompletableSource.Subscriber subscriber,
                                                                         AsyncContextMap current);

    /**
     * Wrap the {@link Cancellable} to ensure it is able to track
     * {@link AsyncContext} correctly.
     * @param subscriber The {@link SingleSource.Subscriber} for which to wrap the corresponding
     * {@link Cancellable}.
     * @param current The current {@link AsyncContextMap}.
     * @param <T> Type of the {@link Single}.
     * @return The wrapped {@link SingleSource.Subscriber}.
     */
    <T> SingleSource.Subscriber<T> wrapCancellable(SingleSource.Subscriber<T> subscriber, AsyncContextMap current);

    /**
     * Wrap an {@link SingleSource.Subscriber} to ensure it is able to track {@link AsyncContext} correctly.
     * @param subscriber subscriber to wrap.
     * @param current The current {@link AsyncContextMap}.
     * @param <T> Type of the {@link Single}.
     * @return The wrapped subscriber.
     */
    <T> SingleSource.Subscriber<T> wrapSingleSubscriber(SingleSource.Subscriber<T> subscriber, AsyncContextMap current);

    /**
     * Wrap an {@link SingleSource.Subscriber} and any {@link Cancellable}s from
     * {@link SingleSource.Subscriber#onSubscribe(Cancellable)} to ensure it is able to track {@link AsyncContext}
     * correctly.
     * @param subscriber subscriber to wrap.
     * @param current The current {@link AsyncContextMap}.
     * @param <T> Type of the {@link Single}.
     * @return The wrapped subscriber.
     */
    <T> SingleSource.Subscriber<T> wrapSingleSubscriberAndCancellable(SingleSource.Subscriber<T> subscriber,
                                                                      AsyncContextMap current);

    /**
     * Wrap an {@link Subscription} to ensure it is able to track {@link AsyncContext} correctly.
     * @param subscriber The {@link Subscriber} for which to wrap the corresponding
     * {@link Subscription}.
     * @param current The current {@link AsyncContextMap}.
     * @return The wrapped {@link Subscriber}.
     */
    <T> PublisherSource.Subscriber<T> wrapSubscription(PublisherSource.Subscriber<T> subscriber,
                                                       AsyncContextMap current);

    /**
     * Wrap an {@link Subscriber} to ensure it is able to track {@link AsyncContext} correctly.
     * @param subscriber The subscriber to wrap.
     * @param current The current {@link AsyncContextMap}.
     * @param <T> the type of element signaled to the {@link Subscriber}.
     * @return The wrapped subscriber.
     */
    <T> PublisherSource.Subscriber<T> wrapPublisherSubscriber(PublisherSource.Subscriber<T> subscriber,
                                                              AsyncContextMap current);

    /**
     * Wrap an {@link Subscriber} and any {@link Subscription}s from {@link Subscriber#onSubscribe(Subscription)} to
     * ensure it is able to track {@link AsyncContext} correctly.
     * @param subscriber The subscriber to wrap.
     * @param current The current {@link AsyncContextMap}.
     * @param <T> the type of element signaled to the {@link Subscriber}.
     * @return The wrapped subscriber.
     */
    <T> PublisherSource.Subscriber<T> wrapPublisherSubscriberAndSubscription(PublisherSource.Subscriber<T> subscriber,
                                                                             AsyncContextMap current);

    /**
     * Wrap an {@link Executor} to ensure it is able to track {@link AsyncContext} correctly.
     * @param executor The executor to wrap.
     * @return The wrapped executor.
     */
    java.util.concurrent.Executor wrapJdkExecutor(java.util.concurrent.Executor executor);

    /**
     * Wrap an {@link ExecutorService} to ensure it is able to track {@link AsyncContext} correctly.
     * @param executor The executor to wrap.
     * @return The wrapped executor.
     */
    ExecutorService wrapJdkExecutorService(ExecutorService executor);

    /**
     * Wrap an {@link Executor} to ensure it is able to track {@link AsyncContext}
     * correctly.
     * @param executor The executor to wrap.
     * @return The wrapped executor.
     */
    Executor wrapExecutor(Executor executor);

    /**
     * Wrap a {@link CompletableFuture} so that {@link AsyncContext} is preserved from listener methods.
     * @param future The future to wrap.
     * @param <T> The type of data for {@link CompletableFuture}.
     * @return the wrapped {@link CompletableFuture}.
     */
    <T> CompletableFuture<T> wrapCompletableFuture(CompletableFuture<T> future, AsyncContextMap current);

    /**
     * Wrap a {@link ScheduledExecutorService} to ensure it is able to track {@link AsyncContext} correctly.
     * @param executor The executor to wrap.
     * @return The wrapped executor.
     */
    ScheduledExecutorService wrapJdkScheduledExecutorService(ScheduledExecutorService executor);

    /**
     * Wrap a {@link Runnable} to ensure it is able to track {@link AsyncContext} correctly.
     * @param runnable The runnable to wrap.
     * @param contextMap The {@link AsyncContext}.
     * @return The wrapped {@link Runnable}.
     */
    Runnable wrapRunnable(Runnable runnable, AsyncContextMap contextMap);

    /**
     * Wrap a {@link Callable} to ensure it is able to track {@link AsyncContext} correctly.
     * @param callable The callable to wrap.
     * @param contextMap The {@link AsyncContext}.
     * @return The wrapped {@link Callable}.
     */
    Callable wrapCallable(Callable callable, AsyncContextMap contextMap);

    /**
     * Wrap a {@link Consumer} to ensure it is able to track {@link AsyncContext} correctly.
     * @param consumer The consumer to wrap.
     * @param contextMap The {@link AsyncContext}.
     * @param <T> The type of data consumed by {@code consumer}.
     * @return The wrapped {@link Consumer}.
     */
    <T> Consumer<T> wrapConsumer(Consumer<T> consumer, AsyncContextMap contextMap);

    /**
     * Wrap a {@link Function} to ensure it is able to track {@link AsyncContext} correctly.
     * @param func The function to wrap.
     * @param contextMap The {@link AsyncContext}.
     * @param <T> The type of data consumed by {@code func}.
     * @param <U> The type of data returned by {@code func}.
     * @return The wrapped {@link Function}.
     */
    <T, U> Function<T, U> wrapFunction(Function<T, U> func, AsyncContextMap contextMap);

    /**
     * Wrap a {@link BiFunction} to ensure it is able to track {@link AsyncContext} correctly.
     * @param consumer The consumer to wrap.
     * @param contextMap The {@link AsyncContext}.
     * @param <T> The type of data consumed by {@code func}.
     * @param <U> The type of data consumed by {@code func}.
     * @return The wrapped {@link BiConsumer}.
     */
    <T, U> BiConsumer<T, U> wrapBiConsumer(BiConsumer<T, U> consumer, AsyncContextMap contextMap);

    /**
     * Wrap a {@link BiFunction} to ensure it is able to track {@link AsyncContext} correctly.
     * @param func The function to wrap.
     * @param contextMap The {@link AsyncContext}.
     * @param <T> The type of data consumed by {@code func}.
     * @param <U> The type of data consumed by {@code func}.
     * @param <V> The type of data returned by {@code func}.
     * @return The wrapped {@link BiFunction}.
     */
    <T, U, V> BiFunction<T, U, V> wrapBiFunction(BiFunction<T, U, V> func, AsyncContextMap contextMap);
}
