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

import io.servicetalk.concurrent.Single;

import org.reactivestreams.Subscriber;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
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

    @Override
    public void contextMap(final AsyncContextMap newContextMap) {
    }

    @Override
    public AsyncContextMap newContextMap() {
        return NoopAsyncContextMap.INSTANCE;
    }

    @Override
    public Completable.Subscriber wrapCancellable(final Completable.Subscriber subscriber,
                                                  final AsyncContextMap current) {
        return subscriber;
    }

    @Override
    public Completable.Subscriber wrap(final Completable.Subscriber subscriber, final AsyncContextMap current) {
        return subscriber;
    }

    @Override
    public <T> Single.Subscriber<T> wrapCancellable(final Single.Subscriber<T> subscriber,
                                                    final AsyncContextMap current) {
        return subscriber;
    }

    @Override
    public <T> Single.Subscriber<T> wrap(final Single.Subscriber<T> subscriber, final AsyncContextMap current) {
        return subscriber;
    }

    @Override
    public <T> Subscriber<T> wrapSubscription(final Subscriber<T> subscriber, final AsyncContextMap current) {
        return subscriber;
    }

    @Override
    public <T> Subscriber<T> wrap(final Subscriber<T> subscriber, final AsyncContextMap current) {
        return subscriber;
    }

    @Override
    public Executor wrap(final Executor executor) {
        return executor;
    }

    @Override
    public Executor unwrap(final Executor executor) {
        return executor;
    }

    @Override
    public ExecutorService wrap(final ExecutorService executor) {
        return executor;
    }

    @Override
    public ExecutorService unwrap(final ExecutorService executor) {
        return executor;
    }

    @Override
    public io.servicetalk.concurrent.api.Executor wrap(final io.servicetalk.concurrent.api.Executor executor) {
        return executor;
    }

    @Override
    public io.servicetalk.concurrent.api.Executor unwrap(final io.servicetalk.concurrent.api.Executor executor) {
        return executor;
    }

    @Override
    public io.servicetalk.concurrent.Executor unwrap(final io.servicetalk.concurrent.Executor executor) {
        return executor;
    }

    @Override
    public <T> CompletionStage<T> wrap(final CompletionStage<T> stage, final AsyncContextMap contextMap) {
        return stage;
    }

    @Override
    public ScheduledExecutorService wrap(final ScheduledExecutorService executor) {
        return executor;
    }

    @Override
    public ScheduledExecutorService unwrap(final ScheduledExecutorService executor) {
        return executor;
    }

    @Override
    public Runnable wrap(final Runnable runnable, final AsyncContextMap contextMap) {
        return runnable;
    }

    @Override
    public <T> Consumer<T> wrap(final Consumer<T> consumer, final AsyncContextMap contextMap) {
        return consumer;
    }

    @Override
    public <T, U> Function<T, U> wrap(final Function<T, U> func, final AsyncContextMap contextMap) {
        return func;
    }

    @Override
    public <T, U> BiConsumer<T, U> wrap(final BiConsumer<T, U> consumer, final AsyncContextMap contextMap) {
        return consumer;
    }

    @Override
    public <T, U, V> BiFunction<T, U, V> wrap(final BiFunction<T, U, V> func, final AsyncContextMap contextMap) {
        return func;
    }

    private static final class NoopAsyncContextMap implements AsyncContextMap {
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
            throw new UnsupportedOperationException();
        }

        @Override
        public void putAll(final Map<Key<?>, Object> map) {
            throw new UnsupportedOperationException();
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
}
