/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.internal.ContextMapUtils;
import io.servicetalk.context.api.ContextMap;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class NoopAsyncContextProvider implements AsyncContextProvider {
    static final AsyncContextProvider INSTANCE = new NoopAsyncContextProvider();

    private NoopAsyncContextProvider() {
        // singleton
    }

    @Override
    public AsyncContextMap contextMap() {
        return NoopAsyncContextMap.INSTANCE;
    }

    @Nonnull
    @Override
    public ContextMap context() {
        return NoopContextMap.INSTANCE;
    }

    @Override
    public CompletableSource.Subscriber wrapCancellable(final CompletableSource.Subscriber subscriber,
                                                        final ContextMap current) {
        return subscriber;
    }

    @Override
    public CompletableSource.Subscriber wrapCompletableSubscriber(final CompletableSource.Subscriber subscriber,
                                                                  final ContextMap current) {
        return subscriber;
    }

    @Override
    public CompletableSource.Subscriber wrapCompletableSubscriberAndCancellable(
            final CompletableSource.Subscriber subscriber, final ContextMap current) {
        return subscriber;
    }

    @Override
    public <T> SingleSource.Subscriber<T> wrapCancellable(final SingleSource.Subscriber<T> subscriber,
                                                          final ContextMap current) {
        return subscriber;
    }

    @Override
    public <T> SingleSource.Subscriber<T> wrapSingleSubscriber(final SingleSource.Subscriber<T> subscriber,
                                                               final ContextMap current) {
        return subscriber;
    }

    @Override
    public <T> SingleSource.Subscriber<T> wrapSingleSubscriberAndCancellable(
            final SingleSource.Subscriber<T> subscriber, final ContextMap current) {
        return subscriber;
    }

    @Override
    public <T> Subscriber<T> wrapSubscription(final Subscriber<T> subscriber, final ContextMap current) {
        return subscriber;
    }

    @Override
    public <T> Subscriber<T> wrapPublisherSubscriber(final Subscriber<T> subscriber, final ContextMap current) {
        return subscriber;
    }

    @Override
    public <T> Subscriber<T> wrapPublisherSubscriberAndSubscription(final Subscriber<T> subscriber,
                                                                    final ContextMap current) {
        return subscriber;
    }

    @Override
    public Executor wrapJdkExecutor(final Executor executor) {
        return executor;
    }

    @Override
    public ExecutorService wrapJdkExecutorService(final ExecutorService executor) {
        return executor;
    }

    @Override
    public io.servicetalk.concurrent.api.Executor wrapExecutor(final io.servicetalk.concurrent.api.Executor executor) {
        return executor;
    }

    @Override
    public ScheduledExecutorService wrapJdkScheduledExecutorService(final ScheduledExecutorService executor) {
        return executor;
    }

    @Override
    public <T> CompletableFuture<T> wrapCompletableFuture(final CompletableFuture<T> future,
                                                          final ContextMap current) {
        return future;
    }

    @Override
    public Runnable wrapRunnable(final Runnable runnable, final ContextMap current) {
        return runnable;
    }

    @Override
    public <V> Callable<V> wrapCallable(final Callable<V> callable, final ContextMap current) {
        return callable;
    }

    @Override
    public <T> Consumer<T> wrapConsumer(final Consumer<T> consumer, final ContextMap current) {
        return consumer;
    }

    @Override
    public <T, U> Function<T, U> wrapFunction(final Function<T, U> func, final ContextMap current) {
        return func;
    }

    @Override
    public <T, U> BiConsumer<T, U> wrapBiConsumer(final BiConsumer<T, U> consumer, final ContextMap current) {
        return consumer;
    }

    @Override
    public <T, U, V> BiFunction<T, U, V> wrapBiFunction(final BiFunction<T, U, V> func,
                                                        final ContextMap current) {
        return func;
    }

    @Deprecated
    private static final class NoopAsyncContextMap implements AsyncContextMap {
        @Deprecated
        static final AsyncContextMap INSTANCE = new NoopAsyncContextMap();

        private NoopAsyncContextMap() {
            // singleton
        }

        @Nullable
        @Override
        public <T> T get(final Key<T> key) {
            return null;
        }

        @Override
        public boolean containsKey(final Key<?> key) {
            return false;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public int size() {
            return 0;
        }

        @Nullable
        @Override
        public <T> T put(final Key<T> key, @Nullable final T value) {
            return null;
        }

        @Override
        public void putAll(final Map<Key<?>, Object> map) {
        }

        @Override
        public <T> T remove(final Key<T> key) {
            return null;
        }

        @Override
        public boolean removeAll(final Iterable<Key<?>> entries) {
            return false;
        }

        @Override
        public void clear() {
        }

        @Nullable
        @Override
        public Key<?> forEach(final BiPredicate<Key<?>, Object> consumer) {
            return null;
        }

        @Override
        public AsyncContextMap copy() {
            return this;
        }
    }

    private static final class NoopContextMap implements ContextMap {
        static final ContextMap INSTANCE = new NoopContextMap();

        private NoopContextMap() {
            // Singleton
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public boolean containsKey(final Key<?> key) {
            return false;
        }

        @Override
        public boolean containsValue(@Nullable final Object value) {
            return false;
        }

        @Override
        public <T> boolean contains(final Key<T> key, @Nullable final T value) {
            return false;
        }

        @Nullable
        @Override
        public <T> T get(final Key<T> key) {
            return null;
        }

        @Override
        public <T> T getOrDefault(final Key<T> key, final T defaultValue) {
            return defaultValue;
        }

        @Nullable
        @Override
        public <T> T put(final Key<T> key, @Nullable final T value) {
            return null;
        }

        @Nullable
        @Override
        public <T> T putIfAbsent(final Key<T> key, @Nullable final T value) {
            return null;
        }

        @Nullable
        @Override
        public <T> T computeIfAbsent(final Key<T> key, final Function<Key<T>, T> computeFunction) {
            return null;
        }

        @Override
        public void putAll(final ContextMap map) {
        }

        @Override
        public void putAll(final Map<Key<?>, Object> map) {
        }

        @Nullable
        @Override
        public <T> T remove(final Key<T> key) {
            return null;
        }

        @Override
        public boolean removeAll(final Iterable<Key<?>> keys) {
            return false;
        }

        @Override
        public void clear() {
        }

        @Nullable
        @Override
        public Key<?> forEach(final BiPredicate<Key<?>, Object> consumer) {
            return null;
        }

        @Override
        public ContextMap copy() {
            return this;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ContextMap)) {
                return false;
            }
            return ((ContextMap) o).isEmpty();
        }

        @Override
        public String toString() {
            return ContextMapUtils.toString(this);
        }
    }
}
