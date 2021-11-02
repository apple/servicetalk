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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ConcurrentModificationException;
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

import static io.servicetalk.concurrent.api.AsyncContextExecutorPlugin.EXECUTOR_PLUGIN;
import static io.servicetalk.concurrent.api.Executors.EXECUTOR_PLUGINS;

/**
 * Presents a static interface to retain state in an asynchronous environment.
 * <p>
 * This should not be used as a "catch all" to avoid designing APIs which accommodate for your needs. This should be
 * used as a last resort (e.g. for low level framework or infrastructure like tasks) because there maybe non-trivial
 * overhead required to maintain this context.
 */
public final class AsyncContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncContext.class);

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
     * This is currently not volatile as we rely upon external synchronization for this to be made visible. The current
     * use case for this is a "once at start up" to {@link #disable()} this mechanism completely. This is currently a
     * best effort mechanism for performance reasons, and we can re-evaluate later if more strict behavior is required.
     */
    private static AsyncContextProvider provider = DefaultAsyncContextProvider.INSTANCE;

    private AsyncContext() {
        // no instances
    }

    static AsyncContextProvider provider() {
        return provider;
    }

    /**
     * Get the current {@link AsyncContextMap}.
     *
     * @return the current {@link AsyncContextMap}
     * @deprecated Use {@link #context()}.
     */
    @Deprecated
    public static AsyncContextMap current() {
        return provider().contextMap();
    }

    /**
     * Get the current {@link ContextMap}.
     *
     * @return the current {@link ContextMap}
     */
    public static ContextMap context() {
        return provider().context();
    }

    /**
     * Registers a new mapping between {@link AsyncContextMap.Key} and {@link ContextMap.Key} to allow smooth migration
     * to the new API.
     * <p>
     * If the mapping is registered using this method, it will be possible to use both keys for {@link AsyncContext}
     * operations at the same time, allowing step-by-step migration from deprecated to the new keys. For example, it
     * will be possible to use {@link #put(AsyncContextMap.Key, Object)} and {@link #get(ContextMap.Key)} to retrieve
     * the same value.
     *
     * @param acmKey {@link AsyncContextMap.Key} to map
     * @param cmKey {@link ContextMap.Key} to map
     * @param <T> type of the value for these keys
     * @deprecated This method can be used only as a temporary approach until the full codebase is migrated to
     * {@link ContextMap} API. This method will be removed in future releases.
     */
    @Deprecated
    public static <T> void newKeyMapping(final AsyncContextMap.Key<T> acmKey, final ContextMap.Key<T> cmKey) {
        AsyncContextMapToContextMapAdapter.newKeyMapping(acmKey, cmKey);
    }

    /**
     * Convenience method for adding a value to the current context.
     *
     * @param key   the key used to index {@code value}. Cannot be {@code null}.
     * @param value the value to put.
     * @param <T>   The type of object associated with {@code key}.
     * @return The previous value associated with the {@code key}, or {@code null} if there was none. {@code null} can
     * also indicate the value associated value with {@code key} is {@code null} (if {@code null} values are supported
     * by the implementation).
     * @throws NullPointerException if {@code key} or {@code value} is {@code null} and the underlying
     * {@link AsyncContextMap} implementation doesn't support {@code null} keys or values.
     * @throws UnsupportedOperationException if this method is not supported by the underlying {@link AsyncContextMap}
     * implementation.
     * @see AsyncContextMap#put(AsyncContextMap.Key, Object)
     * @deprecated Use {@link #put(ContextMap.Key, Object)}
     */
    @Deprecated
    @Nullable
    public static <T> T put(final AsyncContextMap.Key<T> key, @Nullable final T value) {
        return current().put(key, value);
    }

    /**
     * Convenience method for adding a new key-value pair to the current context.
     *
     * @param key The {@link ContextMap.Key} used to index the {@code value}.
     * @param value the value to put.
     * @param <T> The type of object associated with {@code key}.
     * @return The previous value associated with the {@code key}, or {@code null} if there was none. {@code null} can
     * also indicate the value associated value with {@code key} is {@code null} (if {@code null} values are supported
     * by the implementation).
     * @throws NullPointerException (optional behavior) if {@code key} or {@code value} is {@code null} and the
     * underlying {@link ContextMap} implementation doesn't support {@code null} keys or values.
     * @throws UnsupportedOperationException if this method is not supported by the underlying {@link ContextMap}
     * implementation.
     * @see ContextMap#put(ContextMap.Key, Object)
     */
    @Nullable
    public static <T> T put(final ContextMap.Key<T> key, @Nullable final T value) {
        return context().put(key, value);
    }

    /**
     * Convenience method for adding a new key-value pair to the current context if this map does not already contain
     * this {@code key} or is mapped to {@code null}.
     *
     * @param key The {@link ContextMap.Key} used to index the {@code value}.
     * @param value the value to put.
     * @param <T> The type of object associated with {@code key}.
     * @return The previous value associated with the {@code key}, or {@code null} if there was none. {@code null} can
     * also indicate the value associated value with {@code key} is {@code null} (if {@code null} values are supported
     * by the implementation).
     * @throws NullPointerException (optional behavior) if {@code key} or {@code value} is {@code null} and the
     * underlying {@link ContextMap} implementation doesn't support {@code null} keys or values.
     * @throws UnsupportedOperationException if this method is not supported by the underlying {@link ContextMap}
     * implementation.
     * @see ContextMap#putIfAbsent(ContextMap.Key, Object)
     */
    @Nullable
    public static <T> T putIfAbsent(final ContextMap.Key<T> key, @Nullable final T value) {
        return context().putIfAbsent(key, value);
    }

    /**
     * Convenience method for adding a new key-value pair to the current context if this map does not already contain
     * this {@code key} or is mapped to {@code null}.
     *
     * @param key The {@link ContextMap.Key} used to index the {@code value}.
     * @param computeFunction The function to compute a new value. Implementation may invoke this function multiple
     * times if concurrent threads attempt modifying this context map, result is expected to be idempotent.
     * @param <T> The type of object associated with {@code key}.
     * @return The previous value associated with the {@code key}, or {@code null} if there was none. {@code null} can
     * also indicate the value associated value with {@code key} is {@code null} (if {@code null} values are supported
     * by the implementation).
     * @throws NullPointerException (optional behavior) if {@code key} or computed {@code value} is {@code null} and the
     * underlying {@link ContextMap} implementation doesn't support {@code null} keys or values.
     * @throws UnsupportedOperationException if this method is not supported by the underlying {@link ContextMap}
     * implementation.
     * @see ContextMap#putIfAbsent(ContextMap.Key, Object)
     */
    @Nullable
    public static <T> T computeIfAbsent(final ContextMap.Key<T> key,
                                        final Function<ContextMap.Key<T>, T> computeFunction) {
        return context().computeIfAbsent(key, computeFunction);
    }

    /**
     * Convenience method for to put all the key-value pairs into the current context from another {@link ContextMap}.
     *
     * @param map contains the key-value pairs that will be added.
     * @throws ConcurrentModificationException done on a best effort basis if the passed {@code map} is detected to be
     * modified while attempting to put all entries.
     * @throws NullPointerException (optional behavior) if any of the {@code map} entries has a {@code null} {@code key}
     * or {@code value} and the underlying {@link ContextMap} implementation doesn't support {@code null} keys or
     * values.
     * @throws UnsupportedOperationException if this method is not supported by the underlying {@link ContextMap}
     * implementation.
     * @see ContextMap#putAll(ContextMap)
     */
    public static void putAll(final ContextMap map) {
        context().putAll(map);
    }

    /**
     * Convenience method for to put all the key/value pairs into the current context.
     *
     * @param map contains the key/value pairs that will be added.
     * @throws ConcurrentModificationException done on a best effort basis if the passed {@code map} is detected to be
     * modified while attempting to put all entries.
     * @throws NullPointerException if {@code key} or {@code value} is {@code null} and the underlying
     * {@link AsyncContextMap} implementation doesn't support {@code null} keys or values.
     * @throws UnsupportedOperationException if this method is not supported by the underlying {@link AsyncContextMap}
     * implementation.
     * @see AsyncContextMap#putAll(Map)
     * @deprecated Use {@link #putAllFromMap(Map)}
     */
    @Deprecated
    public static void putAll(final Map<AsyncContextMap.Key<?>, Object> map) {
        current().putAll(map);
    }

    /**
     * Convenience method for to put all the key-value pairs into the current context.
     *
     * @param map contains the key-value pairs that will be added.
     * @throws ConcurrentModificationException done on a best effort basis if the passed {@code map} is detected to be
     * modified while attempting to put all entries.
     * @throws NullPointerException (optional behavior) if any of the {@code map} entries has a {@code null} {@code key}
     * or {@code value} and the underlying {@link ContextMap} implementation doesn't support {@code null} keys or
     * values.
     * @throws UnsupportedOperationException if this method is not supported by the underlying {@link ContextMap}
     * implementation.
     * @see ContextMap#putAll(Map)
     */
    // FIXME: 0.42 - add putAll(Map) alias method and consider deprecating putAllFromMap(Map)
    public static void putAllFromMap(final Map<ContextMap.Key<?>, Object> map) {
        context().putAll(map);
    }

    /**
     * Convenience method to remove a key/value pair from the current context.
     *
     * @param <T> The type of object associated with {@code key}.
     * @param key The key to remove.
     * @return the previous value associated with {@code key}, or {@code null} if there was none. A {@code null}
     * value may also indicate there was a previous value which was {@code null}.
     * @throws UnsupportedOperationException if this method is not supported by the underlying {@link AsyncContextMap}
     * implementation.
     * @see AsyncContextMap#remove(AsyncContextMap.Key)
     * @deprecated Use {@link #remove(ContextMap.Key)}
     */
    @Deprecated
    @Nullable
    public static <T> T remove(final AsyncContextMap.Key<T> key) {
        return current().remove(key);
    }

    /**
     * Convenience method to remove a key-value pair from the current context.
     *
     * @param key The {@link ContextMap.Key} which identifies a key-value pair for removal.
     * @param <T> The type of object associated with {@code key}.
     * @return the previous value associated with {@code key}, or {@code null} if there was none. A {@code null}
     * value may also indicate there was a previous value which was {@code null}.
     * @throws NullPointerException (optional behavior) if {@code key} is {@code null} and the underlying
     * {@link ContextMap} implementation doesn't support {@code null} keys or values.
     * @throws UnsupportedOperationException if this method is not supported by the underlying {@link ContextMap}
     * implementation.
     * @see ContextMap#remove(ContextMap.Key)
     */
    @Nullable
    public static <T> T remove(final ContextMap.Key<T> key) {
        return context().remove(key);
    }

    /**
     * Convenience method to remove all the key/value pairs from the current context.
     *
     * @param entries A {@link Iterable} which contains all the keys to remove.
     * @return {@code true} if this map has changed as a result of this operation.
     * @throws UnsupportedOperationException if this method is not supported by the underlying {@link AsyncContextMap}
     * implementation.
     * @see AsyncContextMap#removeAll(Iterable)
     * @deprecated Use {@link #removeAllPairs(Iterable)}
     */
    @Deprecated
    public static boolean removeAll(final Iterable<AsyncContextMap.Key<?>> entries) {
        return current().removeAll(entries);
    }

    /**
     * Convenience method to remove all key-value pairs from the current context associated with the keys from the
     * passed {@link Iterable}.
     *
     * @param keys The {@link ContextMap.Key}s that identify key-value pairs for removal.
     * @return {@code true} if this map has changed as a result of this operation.
     * @throws NullPointerException (optional behavior) if any of the {@code keys} is {@code null} and the underlying
     * {@link ContextMap} implementation doesn't support {@code null} keys.
     * @throws UnsupportedOperationException if this method is not supported by the underlying {@link ContextMap}
     * implementation.
     * @see ContextMap#removeAll(Iterable)
     */
    // FIXME: 0.42 - add removeAll(Iterable) alias method and consider deprecating removeAllPairs(Iterable)
    public static boolean removeAllPairs(final Iterable<ContextMap.Key<?>> keys) {
        return context().removeAll(keys);
    }

    /**
     * Convenience method to clear all the key-value pairs from the current context.
     *
     * @throws UnsupportedOperationException if this method is not supported by the underlying {@link ContextMap}
     * implementation.
     * @see ContextMap#clear()
     */
    public static void clear() {
        context().clear();
    }

    /**
     * Convenience method to get the value associated with {@code key} from the current context.
     *
     * @param key the key to lookup.
     * @param <T> The anticipated type of object associated with {@code key}.
     * @return the value associated with {@code key}, or {@code null} if no value is associated. {@code null} can
     * also indicate the value associated with {@code key} is {@code null} (if {@code null} values are supported by the
     * underlying {@link AsyncContextMap} implementation).
     * @throws NullPointerException (optional behavior) if {@code key} is {@code null} and the underlying
     * {@link AsyncContextMap} implementation doesn't support {@code null} keys or values.
     * @see AsyncContextMap#get(AsyncContextMap.Key)
     * @deprecated Use {@link #get(ContextMap.Key)}
     */
    @Deprecated
    @Nullable
    public static <T> T get(final AsyncContextMap.Key<T> key) {
        return current().get(key);
    }

    /**
     * Convenience method to get the value associated with the {@code key} from the current context.
     *
     * @param key The {@link ContextMap.Key} to lookup.
     * @param <T> The anticipated type of object associated with {@code key}.
     * @return the value associated with the {@code key}, or {@code null} if no value is associated. {@code null} can
     * also indicate the value associated with {@code key} is {@code null} (if {@code null} values are supported by the
     * underlying {@link ContextMap} implementation).
     * @throws NullPointerException (optional behavior) if {@code key} is {@code null} and the underlying
     * {@link ContextMap} implementation doesn't support {@code null} keys or values.
     * @see ContextMap#get(ContextMap.Key)
     */
    @Nullable
    public static <T> T get(final ContextMap.Key<T> key) {
        return context().get(key);
    }

    /**
     * Convenience method to get the value associated with {@code key} from the current context, or {@code defaultValue}
     * if no value is associated.
     *
     * @param key The {@link ContextMap.Key} to lookup.
     * @param defaultValue The value to return if no value is associated with the {@code key}.
     * @param <T> The anticipated type of object associated with {@code key}.
     * @return The value associated with the {@code key} (can return {@code null} if {@code null} values are supported
     * by the underlying {@link ContextMap} implementation), or {@code defaultValue} if no value is associated.
     * @throws NullPointerException (optional behavior) if {@code key} is {@code null} and the underlying
     * {@link ContextMap} implementation doesn't support {@code null} keys or values.
     * @see ContextMap#getOrDefault(ContextMap.Key, Object)
     */
    @Nullable
    public static <T> T getOrDefault(final ContextMap.Key<T> key, T defaultValue) {
        return context().getOrDefault(key, defaultValue);
    }

    /**
     * Convenience method to determine if the current context contains a key/value entry corresponding to {@code key}.
     *
     * @param key the key to lookup.
     * @return {@code true} if the current context contains a key/value entry corresponding to {@code key}.
     * {@code false} otherwise.
     * @throws NullPointerException (optional behavior) if {@code key} is {@code null} and the underlying
     * {@link AsyncContextMap} implementation doesn't support {@code null} keys or values.
     * @see AsyncContextMap#containsKey(AsyncContextMap.Key)
     * @deprecated Use {@link #containsKey(ContextMap.Key)}
     */
    @Deprecated
    public static boolean containsKey(final AsyncContextMap.Key<?> key) {
        return current().containsKey(key);
    }

    /**
     * Convenience method to determine if the current context contains a key-value pair corresponding to the
     * {@code key}.
     *
     * @param key The {@link ContextMap.Key} to lookup.
     * @return {@code true} if the current context contains a key-value pair corresponding to the {@code key},
     * {@code false} otherwise.
     * @throws NullPointerException (optional behavior) if {@code key} is {@code null} and the underlying
     * {@link ContextMap} implementation doesn't support {@code null} keys.
     * @see ContextMap#containsKey(ContextMap.Key)
     */
    public static boolean containsKey(final ContextMap.Key<?> key) {
        return context().containsKey(key);
    }

    /**
     * Convenience method to determine if the current context contains a key-value pair with the specified
     * {@code value}.
     *
     * @param value the value to lookup.
     * @return {@code true} if this context contains one or more a key-value entries with the specified {@code value},
     * {@code false} otherwise.
     * @throws NullPointerException (optional behavior) if {@code value} is {@code null} and the underlying
     * {@link ContextMap} implementation doesn't support {@code null} values.
     * @see ContextMap#containsValue(Object)
     */
    public static boolean containsValue(final Object value) {
        return context().containsValue(value);
    }

    /**
     * Convenience method to determine if this context contains a key-value pair matching the passed {@code key} and
     * {@code value}.
     *
     * @param key The {@link ContextMap.Key} to lookup.
     * @param value The value to match.
     * @param <T> The anticipated type of object associated with the {@code key}.
     * @return {@code true} if this context contains a key-value pair matching the passed {@code key} and
     * {@code value}, {@code false} otherwise.
     * @throws NullPointerException (optional behavior) if {@code key} or {@code value} is {@code null} and the
     * underlying {@link ContextMap} implementation doesn't support {@code null} keys or values.
     */
    public static <T> boolean contains(final ContextMap.Key<T> key, @Nullable final T value) {
        return context().contains(key, value);
    }

    /**
     * Convenience method to determine the number of key/value pairs in the current context.
     *
     * @return the number of key/value pairs in the current context.
     * @see ContextMap#size() ()
     */
    public static int size() {
        return context().size();
    }

    /**
     * Convenience method to determine if there are no key/value pairs in the current context.
     *
     * @return {@code true} if there are no key/value pairs in the current context.
     * @see ContextMap#isEmpty()
     */
    public static boolean isEmpty() {
        return context().isEmpty();
    }

    /**
     * Convenience method to iterate over the key/value pairs contained in the current context.
     *
     * @param consumer Each key/value pair will be passed as arguments to this {@link BiPredicate}. Returns {@code true}
     * if the consumer wants to keep iterating or {@code false} to stop iteration at the current key/value pair.
     * @return {@code null} if {@code consumer} iterated through all key/value pairs or the {@link AsyncContextMap.Key}
     * at which the iteration stopped.
     * @throws NullPointerException if {@code consumer} is null.
     * @see AsyncContextMap#forEach(BiPredicate)
     * @deprecated Use {@link #forEachPair(BiPredicate)}
     */
    @Deprecated
    @Nullable
    public static AsyncContextMap.Key<?> forEach(final BiPredicate<AsyncContextMap.Key<?>, Object> consumer) {
        return current().forEach(consumer);
    }

    /**
     * Convenience method to iterate over the key-value pairs contained in the current context.
     *
     * @param consumer Each key/value pair will be passed as arguments to this {@link BiPredicate}. Returns {@code true}
     * if the consumer wants to keep iterating or {@code false} to stop iteration at the current key/value pair.
     * @return {@code null} if {@code consumer} iterated through all key/value pairs or the {@link ContextMap.Key}
     * at which the iteration stopped.
     * @throws NullPointerException if {@code consumer} is null.
     * @see ContextMap#forEach(BiPredicate)
     */
    // FIXME: 0.42 - add forEach(BiPredicate) alias method and consider deprecating forEachPair(BiPredicate)
    @Nullable
    public static ContextMap.Key<?> forEachPair(final BiPredicate<ContextMap.Key<?>, Object> consumer) {
        return context().forEach(consumer);
    }

    /**
     * Wrap an {@link java.util.concurrent.Executor} to ensure it is able to track {@link AsyncContext} correctly.
     * @param executor The executor to wrap.
     * @return The wrapped executor.
     */
    public static java.util.concurrent.Executor wrapJdkExecutor(final java.util.concurrent.Executor executor) {
        return provider().wrapJdkExecutor(executor);
    }

    /**
     * Wrap an {@link Executor} to ensure it is able to track {@link AsyncContext}
     * correctly.
     * @param executor The executor to wrap.
     * @return The wrapped executor.
     */
    public static Executor wrapExecutor(final Executor executor) {
        return provider().wrapExecutor(executor);
    }

    /**
     * Wrap an {@link ExecutorService} to ensure it is able to track {@link AsyncContext} correctly.
     * @param executor The executor to wrap.
     * @return The wrapped executor.
     */
    public static ExecutorService wrapJdkExecutorService(final ExecutorService executor) {
        return provider().wrapJdkExecutorService(executor);
    }

    /**
     * Wrap a {@link ScheduledExecutorService} to ensure it is able to track {@link AsyncContext} correctly.
     * @param executor The executor to wrap.
     * @return The wrapped executor.
     */
    public static ScheduledExecutorService wrapJdkScheduledExecutorService(final ScheduledExecutorService executor) {
        return provider().wrapJdkScheduledExecutorService(executor);
    }

    /**
     * Wrap a {@link Runnable} to ensure it is able to track {@link AsyncContext} correctly.
     * @param runnable The runnable to wrap.
     * @return The wrapped {@link Runnable}.
     */
    public static Runnable wrapRunnable(final Runnable runnable) {
        AsyncContextProvider provider = provider();
        return provider.wrapRunnable(runnable, provider.context());
    }

    /**
     * Wrap a {@link Callable} to ensure it is able to track {@link AsyncContext} correctly.
     * @param callable The callable to wrap.
     * @param <V> The type of data returned by {@code callable}.
     * @return The wrapped {@link Callable}.
     */
    public static <V> Callable<V> wrapCallable(final Callable<V> callable) {
        AsyncContextProvider provider = provider();
        return provider.wrapCallable(callable, provider.context());
    }

    /**
     * Wrap a {@link Consumer} to ensure it is able to track {@link AsyncContext} correctly.
     * @param consumer The consumer to wrap.
     * @param <T> The type of data consumed by {@code consumer}.
     * @return The wrapped {@link Consumer}.
     */
    public static <T> Consumer<T> wrapConsumer(final Consumer<T> consumer) {
        AsyncContextProvider provider = provider();
        return provider.wrapConsumer(consumer, provider.context());
    }

    /**
     * Wrap a {@link Function} to ensure it is able to track {@link AsyncContext} correctly.
     * @param func The function to wrap.
     * @param <T> The type of data consumed by {@code func}.
     * @param <U> The type of data returned by {@code func}.
     * @return The wrapped {@link Function}.
     */
    public static <T, U> Function<T, U> wrapFunction(final Function<T, U> func) {
        AsyncContextProvider provider = provider();
        return provider.wrapFunction(func, provider.context());
    }

    /**
     * Wrap a {@link BiFunction} to ensure it is able to track {@link AsyncContext} correctly.
     * @param consumer The consumer to wrap.
     * @param <T> The type of data consumed by {@code func}.
     * @param <U> The type of data consumed by {@code func}.
     * @return The wrapped {@link BiConsumer}.
     */
    public static <T, U> BiConsumer<T, U> wrapBiConsume(final BiConsumer<T, U> consumer) {
        AsyncContextProvider provider = provider();
        return provider.wrapBiConsumer(consumer, provider.context());
    }

    /**
     * Wrap a {@link BiFunction} to ensure it is able to track {@link AsyncContext} correctly.
     * @param func The function to wrap.
     * @param <T> The type of data consumed by {@code func}.
     * @param <U> The type of data consumed by {@code func}.
     * @param <V> The type of data returned by {@code func}.
     * @return The wrapped {@link BiFunction}.
     */
    public static <T, U, V> BiFunction<T, U, V> wrapBiFunction(BiFunction<T, U, V> func) {
        AsyncContextProvider provider = provider();
        return provider.wrapBiFunction(func, provider.context());
    }

    /**
     * Disable AsyncContext. It is assumed the application will call this in a well orchestrated fashion. For example
     * the behavior of in flight AsyncContext is undefined, objects that are already initialized with AsyncContext
     * enabled may continue to preserve AsyncContext in an unreliable fashion. and also how this behaves relative to
     * concurrent invocation is undefined. External synchronization should be used to ensure this change is visible to
     * other threads.
     */
    public static void disable() {
        if (ENABLED_STATE.getAndSet(STATE_DISABLED) != STATE_DISABLED) {
            disable0();
        }
    }

    /**
     * Determine if {@link #disable()} has been previously called.
     *
     * @return {@code true} if {@link #disable()} has been previously called.
     */
    public static boolean isDisabled() {
        return ENABLED_STATE.get() == STATE_DISABLED;
    }

    /**
     * This method is currently internal only! If it is exposed publicly, and {@link #STATE_DISABLED} is no longer a
     * terminal state the racy {@link #ENABLED_STATE} should be re-evaluated. We currently don't try to account for an
     * application calling this method and {@link #disable()} concurrently, and this may result in inconsistent
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
        if (ENABLED_STATE.compareAndSet(STATE_INIT, STATE_AUTO_ENABLED)) {
            enable0();
        }
    }

    private static void enable0() {
        provider = DefaultAsyncContextProvider.INSTANCE;
        EXECUTOR_PLUGINS.add(EXECUTOR_PLUGIN);
        LOGGER.debug("Enabled.");

        if (ENABLED_STATE.get() == STATE_DISABLED) {
            disable0();
        }
    }

    private static void disable0() {
        provider = NoopAsyncContextProvider.INSTANCE;
        EXECUTOR_PLUGINS.remove(EXECUTOR_PLUGIN);
        LOGGER.info("Disabled, features that depend on AsyncContext will stop working.");
    }
}
