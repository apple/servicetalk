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
import static java.util.Objects.requireNonNull;

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

    private static final AsyncContextProvider DEFAULT_ENABLED_PROVIDER;
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
    private static AsyncContextProvider provider;

    static {
        CapturedContextProvider capturedContextProvider = null;
        for (CapturedContextProvider provider : CapturedContextProviders.providers()) {
            capturedContextProvider = capturedContextProvider == null ?
                    provider : new CapturedContextProviderUnion(capturedContextProvider, provider);
        }
        if (capturedContextProvider == null) {
            DEFAULT_ENABLED_PROVIDER = DefaultAsyncContextProvider.INSTANCE;
        } else {
            DEFAULT_ENABLED_PROVIDER = new CustomCaptureAsyncContextProvider(capturedContextProvider);
        }
        provider = DEFAULT_ENABLED_PROVIDER;
    }

    private AsyncContext() {
        // no instances
    }

    static AsyncContextProvider provider() {
        return provider;
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
     * Grab a reference of the current context.
     * @return the current context.
     */
    public static CapturedContext captureContext() {
        return provider().captureContext();
    }

    /**
     * Convenience method to put a new entry to the current context.
     *
     * @param key The {@link ContextMap.Key} used to index the {@code value}.
     * @param value the value to put.
     * @param <T> The type of object associated with {@code key}.
     * @return The previous value associated with the {@code key}, or {@code null} if there was none. {@code null} can
     * also indicate the value associated with {@code key} is {@code null} (if {@code null} values are supported by the
     * implementation).
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
     * Convenience method to put a new entry to the current context if this map does not already contain this
     * {@code key} or is mapped to {@code null}.
     *
     * @param key The {@link ContextMap.Key} used to index the {@code value}.
     * @param value the value to put.
     * @param <T> The type of object associated with {@code key}.
     * @return The previous value associated with the {@code key}, or {@code null} if there was none. {@code null} can
     * also indicate the value associated with {@code key} is {@code null} (if {@code null} values are supported by the
     * implementation).
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
     * Convenience method to compute a new entry for the current context if this map does not already contain this
     * {@code key} or is mapped to {@code null}.
     *
     * @param key The {@link ContextMap.Key} used to index the {@code value}.
     * @param computeFunction The function to compute a new value. Implementation may invoke this function multiple
     * times if concurrent threads attempt modifying this context map, result is expected to be idempotent.
     * @param <T> The type of object associated with {@code key}.
     * @return The previous value associated with the {@code key}, or {@code null} if there was none. {@code null} can
     * also indicate the value associated with {@code key} is {@code null} (if {@code null} values are supported by the
     * implementation).
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
     * Convenience method to put all the entries into the current context from another {@link ContextMap}.
     *
     * @param map contains the entries that will be added.
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
     * Convenience method to put all the entries into the current context.
     *
     * @param map contains the entries that will be added.
     * @throws ConcurrentModificationException done on a best effort basis if the passed {@code map} is detected to be
     * modified while attempting to put all entries.
     * @throws NullPointerException (optional behavior) if any of the {@code map} entries has a {@code null} {@code key}
     * or {@code value} and the underlying {@link ContextMap} implementation doesn't support {@code null} keys or
     * values.
     * @throws UnsupportedOperationException if this method is not supported by the underlying {@link ContextMap}
     * implementation.
     * @see ContextMap#putAll(Map)
     */
    public static void putAll(final Map<ContextMap.Key<?>, Object> map) {
        context().putAll(map);
    }

    /**
     * Convenience method to put all the entries into the current context.
     *
     * @param map contains the entries that will be added.
     * @throws ConcurrentModificationException done on a best effort basis if the passed {@code map} is detected to be
     * modified while attempting to put all entries.
     * @throws NullPointerException (optional behavior) if any of the {@code map} entries has a {@code null} {@code key}
     * or {@code value} and the underlying {@link ContextMap} implementation doesn't support {@code null} keys or
     * values.
     * @throws UnsupportedOperationException if this method is not supported by the underlying {@link ContextMap}
     * implementation.
     * @see ContextMap#putAll(Map)
     * @deprecated Use {@link #putAll(Map)}
     */
    @Deprecated // 0.43
    public static void putAllFromMap(final Map<ContextMap.Key<?>, Object> map) {
        putAll(map);
    }

    /**
     * Convenience method to remove an entry from the current context.
     *
     * @param key The {@link ContextMap.Key} which identifies an entry for removal.
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
     * Convenience method to remove all entries from the current context associated with the keys from the
     * passed {@link Iterable}.
     *
     * @param keys The {@link ContextMap.Key}s that identify entries for removal.
     * @return {@code true} if this map has changed as a result of this operation.
     * @throws NullPointerException (optional behavior) if any of the {@code keys} is {@code null} and the underlying
     * {@link ContextMap} implementation doesn't support {@code null} keys.
     * @throws UnsupportedOperationException if this method is not supported by the underlying {@link ContextMap}
     * implementation.
     * @see ContextMap#removeAll(Iterable)
     */
    public static boolean removeAll(final Iterable<ContextMap.Key<?>> keys) {
        return context().removeAll(keys);
    }

    /**
     * Convenience method to remove all entries from the current context associated with the keys from the
     * passed {@link Iterable}.
     *
     * @param keys The {@link ContextMap.Key}s that identify entries for removal.
     * @return {@code true} if this map has changed as a result of this operation.
     * @throws NullPointerException (optional behavior) if any of the {@code keys} is {@code null} and the underlying
     * {@link ContextMap} implementation doesn't support {@code null} keys.
     * @throws UnsupportedOperationException if this method is not supported by the underlying {@link ContextMap}
     * implementation.
     * @see ContextMap#removeAll(Iterable)
     * @deprecated Use {@link #removeAll(Iterable)}
     */
    @Deprecated // 0.43
    public static boolean removeAllEntries(final Iterable<ContextMap.Key<?>> keys) {
        return removeAll(keys);
    }

    /**
     * Convenience method to clear all the entries from the current context.
     *
     * @throws UnsupportedOperationException if this method is not supported by the underlying {@link ContextMap}
     * implementation.
     * @see ContextMap#clear()
     */
    public static void clear() {
        context().clear();
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
     * Convenience method to determine if the current context contains an entry corresponding to the {@code key}.
     *
     * @param key The {@link ContextMap.Key} to lookup.
     * @return {@code true} if the current context contains an entry corresponding to the {@code key}, {@code false}
     * otherwise.
     * @throws NullPointerException (optional behavior) if {@code key} is {@code null} and the underlying
     * {@link ContextMap} implementation doesn't support {@code null} keys.
     * @see ContextMap#containsKey(ContextMap.Key)
     */
    public static boolean containsKey(final ContextMap.Key<?> key) {
        return context().containsKey(key);
    }

    /**
     * Convenience method to determine if the current context contains an entry with the specified {@code value}.
     *
     * @param value the value to lookup.
     * @return {@code true} if this context contains one or more entries with the specified {@code value}, {@code false}
     * otherwise.
     * @throws NullPointerException (optional behavior) if {@code value} is {@code null} and the underlying
     * {@link ContextMap} implementation doesn't support {@code null} values.
     * @see ContextMap#containsValue(Object)
     */
    public static boolean containsValue(final Object value) {
        return context().containsValue(value);
    }

    /**
     * Convenience method to determine if this context contains an entry matching the passed {@code key} and
     * {@code value}.
     *
     * @param key The {@link ContextMap.Key} to lookup.
     * @param value The value to match.
     * @param <T> The anticipated type of object associated with the {@code key}.
     * @return {@code true} if this context contains an entry matching the passed {@code key} and {@code value},
     * {@code false} otherwise.
     * @throws NullPointerException (optional behavior) if {@code key} or {@code value} is {@code null} and the
     * underlying {@link ContextMap} implementation doesn't support {@code null} keys or values.
     */
    public static <T> boolean contains(final ContextMap.Key<T> key, @Nullable final T value) {
        return context().contains(key, value);
    }

    /**
     * Convenience method to determine the number of entries in the current context.
     *
     * @return the number of entries in the current context.
     * @see ContextMap#size() ()
     */
    public static int size() {
        return context().size();
    }

    /**
     * Convenience method to determine if there are no entries in the current context.
     *
     * @return {@code true} if there are no entries in the current context.
     * @see ContextMap#isEmpty()
     */
    public static boolean isEmpty() {
        return context().isEmpty();
    }

    /**
     * Convenience method to iterate over the entries contained in the current context.
     *
     * @param consumer Each entry will be passed as key and value arguments to this {@link BiPredicate}. A consumer
     * predicate should return {@code true} if it wants to keep iterating or {@code false} to stop iteration at the
     * current entry.
     * @return {@code null} if {@code consumer} iterated through all entries or the {@link ContextMap.Key} at which the
     * iteration stopped.
     * @throws NullPointerException if {@code consumer} is null.
     * @see ContextMap#forEach(BiPredicate)
     */
    @Nullable
    public static ContextMap.Key<?> forEach(final BiPredicate<ContextMap.Key<?>, Object> consumer) {
        return context().forEach(consumer);
    }

    /**
     * Convenience method to iterate over the entries contained in the current context.
     *
     * @param consumer Each entry will be passed as key and value arguments to this {@link BiPredicate}. A consumer
     * predicate should return {@code true} if it wants to keep iterating or {@code false} to stop iteration at the
     * current entry.
     * @return {@code null} if {@code consumer} iterated through all entries or the {@link ContextMap.Key} at which the
     * iteration stopped.
     * @throws NullPointerException if {@code consumer} is null.
     * @see ContextMap#forEach(BiPredicate)
     * @deprecated Use {@link #forEach(BiPredicate)}
     */
    @Deprecated // 0.43
    @Nullable
    public static ContextMap.Key<?> forEachEntry(final BiPredicate<ContextMap.Key<?>, Object> consumer) {
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
        return provider.wrapRunnable(runnable, provider.captureContext());
    }

    /**
     * Wrap a {@link Callable} to ensure it is able to track {@link AsyncContext} correctly.
     * @param callable The callable to wrap.
     * @param <V> The type of data returned by {@code callable}.
     * @return The wrapped {@link Callable}.
     */
    public static <V> Callable<V> wrapCallable(final Callable<V> callable) {
        AsyncContextProvider provider = provider();
        return provider.wrapCallable(callable, provider.captureContext());
    }

    /**
     * Wrap a {@link Consumer} to ensure it is able to track {@link AsyncContext} correctly.
     * @param consumer The consumer to wrap.
     * @param <T> The type of data consumed by {@code consumer}.
     * @return The wrapped {@link Consumer}.
     */
    public static <T> Consumer<T> wrapConsumer(final Consumer<T> consumer) {
        AsyncContextProvider provider = provider();
        return provider.wrapConsumer(consumer, provider.captureContext());
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
        return provider.wrapFunction(func, provider.captureContext());
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
        return provider.wrapBiConsumer(consumer, provider.captureContext());
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
        return provider.wrapBiFunction(func, provider.captureContext());
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
        provider = DEFAULT_ENABLED_PROVIDER;
        EXECUTOR_PLUGINS.add(EXECUTOR_PLUGIN);
        LOGGER.debug("Enabled.");

        if (ENABLED_STATE.get() == STATE_DISABLED) {
            disable0();
        }
    }

    private static void disable0() {
        provider = NoopAsyncContextProvider.INSTANCE;
        EXECUTOR_PLUGINS.remove(EXECUTOR_PLUGIN);
        LOGGER.info("Disabled. Features that depend on AsyncContext will stop working.");
    }

    private static final class CapturedContextProviderUnion implements CapturedContextProvider {

        private final CapturedContextProvider first;
        private final CapturedContextProvider second;

        CapturedContextProviderUnion(CapturedContextProvider first, CapturedContextProvider second) {
            this.first = requireNonNull(first, "first");
            this.second = requireNonNull(second, "second");
        }

        @Override
        public CapturedContext captureContext(CapturedContext underlying) {
            return second.captureContext(first.captureContext(underlying));
        }

        @Override
        public CapturedContext captureContextCopy(CapturedContext underlying) {
            return second.captureContextCopy(first.captureContextCopy(underlying));
        }
    }
}
