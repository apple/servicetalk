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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;

import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Utility methods for {@link BlockingIterable}.
 */
public final class Iterables {
    private Iterables() {
        // no instances
    }

    /**
     * Get a {@link BlockingIterable} that generates {@link BlockingIterator}s where
     * {@link BlockingIterator#hasNext()} returns {@code true}.
     * @param <T> The type of items for the {@link BlockingIterable}.
     * @return a {@link BlockingIterable} that generates {@link BlockingIterator}s where
     * {@link BlockingIterator#hasNext()} returns {@code true}.
     */
    @SuppressWarnings("unchecked")
    public static <T> BlockingIterable<T> emptyBlockingIterable() {
        return (BlockingIterable<T>) EmptyBlockingIterable.INSTANCE;
    }

    /**
     * Create a new {@link BlockingIterable} generates {@link BlockingIterator}s that only return a single {@code item}.
     * @param item The item returned by {@link BlockingIterator}s.
     * @param <T> The type of items for the {@link BlockingIterable}.
     * @return a new {@link BlockingIterable} generates {@link BlockingIterator}s that only return a single
     * {@code item}.
     */
    public static <T> BlockingIterable<T> singletonBlockingIterable(T item) {
        return new SingletonBlockingIterable<>(item);
    }

    /**
     * A {@link BlockingIterable} which returns {@link BlockingIterator}s that are empty.
     * @param <T> The type of data.
     */
    private static final class EmptyBlockingIterable<T> implements BlockingIterable<T> {
        static final BlockingIterable<?> INSTANCE = new EmptyBlockingIterable<>();

        private EmptyBlockingIterable() {
            // singleton
        }

        @Override
        public BlockingIterator<T> iterator() {
            return EmptyBlockingIterator.instance();
        }

        private static final class EmptyBlockingIterator<T> implements BlockingIterator<T> {
            private static final BlockingIterator<?> INSTANCE = new EmptyBlockingIterator<>();

            private EmptyBlockingIterator() {
                // singleton
            }

            @SuppressWarnings("unchecked")
            public static <T> BlockingIterator<T> instance() {
                return (BlockingIterator<T>) INSTANCE;
            }

            @Override
            public boolean hasNext(final long timeout, final TimeUnit unit) {
                return false;
            }

            @Nullable
            @Override
            public T next(final long timeout, final TimeUnit unit) {
                throw new NoSuchElementException();
            }

            @Override
            public void close() {
            }

            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public T next() {
                throw new NoSuchElementException();
            }
        }
    }

    private static final class SingletonBlockingIterable<T> implements BlockingIterable<T> {
        @Nullable
        private final T item;

        SingletonBlockingIterable(T item) {
            this.item = item;
        }

        @Override
        public BlockingIterator<T> iterator() {
            return new SingletonBlockingIterator<>(item);
        }

        private static final class SingletonBlockingIterator<T> implements BlockingIterator<T> {
            @Nullable
            private final T item;
            private boolean hasNext = true;

            SingletonBlockingIterator(@Nullable T item) {
                this.item = item;
            }

            @Override
            public boolean hasNext() {
                return hasNext;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                hasNext = false;
                return item;
            }

            @Override
            public boolean hasNext(final long timeout, final TimeUnit unit) {
                return hasNext;
            }

            @Nullable
            @Override
            public T next(final long timeout, final TimeUnit unit) {
                return next();
            }

            @Override
            public void close() {
            }
        }
    }
}
