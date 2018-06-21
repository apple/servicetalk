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

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static java.util.Objects.requireNonNull;

public class TestIterableToBlockingIterable<T> implements BlockingIterable<T> {
    private final Iterable<T> iterable;
    private final BiConsumer<Long, TimeUnit> hashNextConsumer;
    private final BiConsumer<Long, TimeUnit> nextConsumer;
    private final AutoCloseable closeable;

    public TestIterableToBlockingIterable(Iterable<T> iterable,
                                          BiConsumer<Long, TimeUnit> hashNextConsumer,
                                          BiConsumer<Long, TimeUnit> nextConsumer,
                                          AutoCloseable closeable) {
        this.iterable = requireNonNull(iterable);
        this.hashNextConsumer = hashNextConsumer;
        this.nextConsumer = nextConsumer;
        this.closeable = closeable;
    }

    @Override
    public BlockingIterator<T> iterator() {
        return new TestIteratorToBlockingIterator<>(this, iterable.iterator());
    }

    private static final class TestIteratorToBlockingIterator<T> implements BlockingIterator<T> {
        private final Iterator<T> iterator;
        private final TestIterableToBlockingIterable<T> iterable;

        TestIteratorToBlockingIterator(TestIterableToBlockingIterable<T> iterable,
                                       Iterator<T> iterator) {
            this.iterable = iterable;
            this.iterator = requireNonNull(iterator);
        }

        @Override
        public boolean hasNext(final long timeout, final TimeUnit unit) {
            iterable.hashNextConsumer.accept(timeout, unit);
            return iterator.hasNext();
        }

        @Override
        public T next(final long timeout, final TimeUnit unit) {
            iterable.nextConsumer.accept(timeout, unit);
            return iterator.next();
        }

        @Override
        public void close() throws Exception {
            iterable.closeable.close();
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public T next() {
            return iterator.next();
        }
    }
}
