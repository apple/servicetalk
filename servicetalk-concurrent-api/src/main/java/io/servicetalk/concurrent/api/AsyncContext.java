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

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncContextCompletablePlugin.COMPLETABLE_PLUGIN;
import static io.servicetalk.concurrent.api.AsyncContextExecutorPlugin.EXECUTOR_PLUGIN;
import static io.servicetalk.concurrent.api.AsyncContextPublisherPlugin.PUBLISHER_PLUGIN;
import static io.servicetalk.concurrent.api.AsyncContextSinglePlugin.SINGLE_PLUGIN;
import static io.servicetalk.concurrent.api.DefaultAsyncContextProvider.INSTANCE;
import static io.servicetalk.concurrent.api.Executors.EXECUTOR_PLUGINS;
import static io.servicetalk.concurrent.internal.ConcurrentPlugins.addPlugin;
import static io.servicetalk.concurrent.internal.ConcurrentPlugins.removePlugin;

/**
 * Presents a static interface to retain state in an asynchronous environment.
 * <p>
 * This should not be used as a "catch all" to avoid designing APIs which accommodate for your needs. This should be
 * used as a last resort (e.g. for low level framework or infrastructure like tasks) because there maybe non-trivial
 * overhead required to maintain this context.
 */
public final class AsyncContext {
    private static final int STATE_DISABLED = -1;
    private static final int STATE_INIT = 0;
    private static final int STATE_AUTO_ENABLED = 1;
    private static final int STATE_ENABLED = 2;
    /**
     * Note this mechanism is racy. Currently only the {@link #disable()} method is exposed publicly and
     * {@link #STATE_DISABLED} is a terminal state. Because we favor going to the disabled state we don't have to worry
     * about concurrent {@link #enable()} and {@link #disable()} calls.
     */
    private static final AtomicInteger ENABLED_STATE = new AtomicInteger(STATE_INIT);

    /**
     * A listener that is notified when ever the {@link AsyncContextProvider} changes.
     */
    @FunctionalInterface
    public interface Listener {
        /**
         * A listener which does nothing.
         */
        @SuppressWarnings("unused")
        Listener NOOP = (oldContext, newContext) -> {
        };

        /**
         * Called after the {@link AsyncContext} is changed.
         * <p>
         * This method must not throw.
         * @param oldContext the previous valud of the context.
         * @param newContext the new value of the context.
         */
        void contextMapChanged(AsyncContextMap oldContext, AsyncContextMap newContext);
    }

    private AsyncContext() {
        // no instances
    }

    /**
     * Subscribe {@code listener} for notification of {@link AsyncContextProvider} change events.
     *
     * @param listener A listener which will be notified of {@link AsyncContextProvider} change events.
     * @return {@code true} if the listener was subscribed.
     */
    public static boolean addListener(Listener listener) {
        return INSTANCE.addListener(listener);
    }

    /**
     * Unsubscribe {@code listener} for notification of {@link AsyncContextProvider} change events.
     *
     * @param listener A listener which will no longer be notified of {@link AsyncContextProvider} change events.
     * @return {@code true} if the listener was unsubscribed.
     */
    public static boolean removeListener(Listener listener) {
        return INSTANCE.removeListener(listener);
    }

    /**
     * Get the current {@link AsyncContextMap}.
     *
     * @return the current {@link AsyncContextMap}
     */
    public static AsyncContextMap current() {
        return INSTANCE.getContextMap();
    }

    /**
     * Restores a previously saved {@link AsyncContextMap}.
     *
     * @param contextMap the {@link AsyncContextMap} to use.
     */
    public static void replace(AsyncContextMap contextMap) {
        INSTANCE.setContextMap(contextMap);
    }

    /**
     * Convenience method for adding a value to the current context.
     *
     * @param key   the key used to index {@code value}. Cannot be {@code null}.
     * @param value the value to put.
     * @param <T>   The type of object associated with {@code key}.
     * @see AsyncContextMap#put(AsyncContextMap.Key, Object)
     */
    public static <T> void put(AsyncContextMap.Key<T> key, @Nullable T value) {
        replace(current().put(key, value));
    }

    /**
     * Convenience method to put all the key/value pairs into the current context.
     *
     * @param context contains the key/value pairs that will be added.
     * @see AsyncContextMap#putAll(AsyncContextMap)
     */
    public static void putAll(AsyncContextMap context) {
        replace(current().putAll(context));
    }

    /**
     * Convenience method for to put all the key/value pairs into the current context.
     *
     * @param map contains the key/value pairs that will be added.
     * @see AsyncContextMap#putAll(Map)
     */
    public static void putAll(Map<AsyncContextMap.Key<?>, Object> map) {
        replace(current().putAll(map));
    }

    /**
     * Convenience method to remove a key/value pair from the current context.
     *
     * @param key The key to remove.
     * @see AsyncContextMap#remove(AsyncContextMap.Key)
     */
    public static void remove(AsyncContextMap.Key<?> key) {
        replace(current().remove(key));
    }

    /**
     * Convenience method to remove all the key/value pairs from the current context.
     *
     * @param context The context which contains all the keys to remove.
     * @see AsyncContextMap#removeAll(AsyncContextMap)
     */
    public static void removeAll(AsyncContextMap context) {
        replace(current().removeAll(context));
    }

    /**
     * Convenience method to remove all the key/value pairs from the current context.
     *
     * @param entries A {@link Iterable} which contains all the keys to remove.
     * @see AsyncContextMap#removeAll(Iterable)
     */
    public static void removeAll(Iterable<AsyncContextMap.Key<?>> entries) {
        replace(current().removeAll(entries));
    }

    /**
     * Convenience method to clear all the key/value pairs from the current context.
     *
     * @see AsyncContextMap#clear()
     */
    public static void clear() {
        replace(current().clear());
    }

    /**
     * Convenience method to get the value associated with {@code key} from the current context.
     *
     * @param key the key to lookup.
     * @param <T> The anticipated type of object associated with {@code key}.
     * @return the value associated with {@code key}, or {@code null} if no value is associated.
     * @see AsyncContextMap#get(AsyncContextMap.Key)
     */
    @Nullable
    public static <T> T get(AsyncContextMap.Key<T> key) {
        return current().get(key);
    }

    /**
     * Convenience method to determine if the current context contains a key/value entry corresponding to {@code key}.
     *
     * @param key the key to lookup.
     * @return {@code true} if the current context contains a key/value entry corresponding to {@code key}.
     * {@code false} otherwise.
     * @see AsyncContextMap#contains(AsyncContextMap.Key)
     */
    public static boolean contains(AsyncContextMap.Key<?> key) {
        return current().contains(key);
    }

    /**
     * Convenience method to determine if there are no key/value pairs in the current context.
     *
     * @return {@code true} if there are no key/value pairs in the current context.
     * @see AsyncContextMap#isEmpty()
     */
    public static boolean isEmpty() {
        return current().isEmpty();
    }

    /**
     * Convenience method to iterate over the key/value pairs contained in the current context.
     *
     * @param consumer Each key/value pair will be passed as arguments to this {@link BiPredicate}. Returns {@code true}
     * if the consumer wants to keep iterating or {@code false} to stop iteration at the current key/value pair.
     * @return {@code null} if {@code consumer} iterated through all key/value pairs or the {@link AsyncContextMap.Key}
     * at which the iteration stopped.
     * @see AsyncContextMap#forEach(BiPredicate)
     */
    @Nullable
    public static AsyncContextMap.Key<?> forEach(BiPredicate<AsyncContextMap.Key<?>, Object> consumer) {
        return current().forEach(consumer);
    }

    /**
     * Wrap an {@link java.util.concurrent.Executor} to ensure it is able to track {@link AsyncContext} correctly.
     * @param executor The executor to wrap.
     * @return The wrapped executor.
     */
    public static java.util.concurrent.Executor wrap(java.util.concurrent.Executor executor) {
        return INSTANCE.wrap(executor);
    }

    /**
     * Wrap an {@link Executor} to ensure it is able to track {@link AsyncContext}
     * correctly.
     * @param executor The executor to wrap.
     * @return The wrapped executor.
     */
    public static Executor wrap(Executor executor) {
        return INSTANCE.wrap(executor);
    }

    /**
     * Make a best effort to unwrap a {@link Executor} so that it no longer tracks
     * {@link AsyncContext}.
     * @param executor The executor to unwrap.
     * @return The result of the unwrap attempt.
     */
    public static Executor unwrap(Executor executor) {
        return INSTANCE.unwrap(executor);
    }

    /**
     * Wrap an {@link ExecutorService} to ensure it is able to track {@link AsyncContext} correctly.
     * @param executor The executor to wrap.
     * @return The wrapped executor.
     */
    public static ExecutorService wrap(ExecutorService executor) {
        return INSTANCE.wrap(executor);
    }

    /**
     * Wrap a {@link ScheduledExecutorService} to ensure it is able to track {@link AsyncContext} correctly.
     * @param executor The executor to wrap.
     * @return The wrapped executor.
     */
    public static ScheduledExecutorService wrap(ScheduledExecutorService executor) {
        return INSTANCE.wrap(executor);
    }

    /**
     * Wrap a {@link Runnable} to ensure it is able to track {@link AsyncContext} correctly.
     * @param runnable The runnable to wrap.
     * @return The wrapped {@link Runnable}.
     */
    public static Runnable wrap(Runnable runnable) {
        return INSTANCE.wrap(runnable);
    }

    /**
     * Wrap a {@link Callable} to ensure it is able to track {@link AsyncContext} correctly.
     * @param callable The callable to wrap.
     * @param <V> The type of data returned by {@code callable}.
     * @return The wrapped {@link Callable}.
     */
    public static <V> Callable<V> wrap(Callable<V> callable) {
        return INSTANCE.wrap(callable);
    }

    /**
     * Wrap a {@link Consumer} to ensure it is able to track {@link AsyncContext} correctly.
     * @param consumer The consumer to wrap.
     * @param <T> The type of data consumed by {@code consumer}.
     * @return The wrapped {@link Consumer}.
     */
    public static <T> Consumer<T> wrap(Consumer<T> consumer) {
        return INSTANCE.wrap(consumer);
    }

    /**
     * Wrap a {@link Function} to ensure it is able to track {@link AsyncContext} correctly.
     * @param func The function to wrap.
     * @param <T> The type of data consumed by {@code func}.
     * @param <U> The type of data returned by {@code func}.
     * @return The wrapped {@link Function}.
     */
    public static <T, U> Function<T, U> wrap(Function<T, U> func) {
        return INSTANCE.wrap(func);
    }

    /**
     * Wrap a {@link BiFunction} to ensure it is able to track {@link AsyncContext} correctly.
     * @param consumer The consumer to wrap.
     * @param <T> The type of data consumed by {@code func}.
     * @param <U> The type of data consumed by {@code func}.
     * @return The wrapped {@link BiConsumer}.
     */
    public static <T, U> BiConsumer<T, U> wrap(BiConsumer<T, U> consumer) {
        return INSTANCE.wrap(consumer);
    }

    /**
     * Wrap a {@link BiFunction} to ensure it is able to track {@link AsyncContext} correctly.
     * @param func The function to wrap.
     * @param <T> The type of data consumed by {@code func}.
     * @param <U> The type of data consumed by {@code func}.
     * @param <V> The type of data returned by {@code func}.
     * @return The wrapped {@link BiFunction}.
     */
    public static <T, U, V> BiFunction<T, U, V> wrap(BiFunction<T, U, V> func) {
        return INSTANCE.wrap(func);
    }

    /**
     * Disable AsyncContext. It is assumed the application will call this in a well orchestrated fashion. For example
     * the behavior of in flight AsyncContext is undefined, objects that are already initialized with AsyncContext
     * enabled may continue to preserve AsyncContext in an unreliable fashion. and also how this behaves relative to
     * concurrent invocation is undefined.
     */
    public static void disable() {
        if (ENABLED_STATE.getAndSet(STATE_DISABLED) != STATE_DISABLED) {
            disable0();
        }
    }

    /**
     * This method is currently internal only! If it is exposed publicly, and {@link #STATE_DISABLED} is no longer a
     * terminal state the racy {@link #ENABLED_STATE} should be re-evaluated. We currently don't try to account for an
     * application calling {@link #enable()} and {@link #disable()} concurrently, and this may result in inconsistent
     * plugin enabled/disable state.
     */
    static void enable() {
        for (;;) {
            final int enabledState = ENABLED_STATE.get();
            if (ENABLED_STATE.compareAndSet(enabledState, STATE_ENABLED)) {
                if (enabledState != STATE_ENABLED && enabledState != STATE_AUTO_ENABLED) {
                    enable0();
                }
                break;
            }
        }
    }

    static void autoEnable() {
        for (;;) {
            final int enabledState = ENABLED_STATE.get();
            if (enabledState == STATE_AUTO_ENABLED) {
                break;
            } else if (ENABLED_STATE.compareAndSet(enabledState, STATE_AUTO_ENABLED)) {
                if (enabledState != STATE_ENABLED) {
                    enable0();
                }
                break;
            }
        }
    }

    private static void enable0() {
        addPlugin(COMPLETABLE_PLUGIN);
        addPlugin(SINGLE_PLUGIN);
        addPlugin(PUBLISHER_PLUGIN);
        EXECUTOR_PLUGINS.add(EXECUTOR_PLUGIN);

        if (ENABLED_STATE.get() == STATE_DISABLED) {
            disable0();
        }
    }

    private static void disable0() {
        removePlugin(COMPLETABLE_PLUGIN);
        removePlugin(SINGLE_PLUGIN);
        removePlugin(PUBLISHER_PLUGIN);
        EXECUTOR_PLUGINS.remove(EXECUTOR_PLUGIN);
    }
}
