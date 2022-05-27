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

import io.servicetalk.concurrent.CloseableIterable;
import io.servicetalk.concurrent.CloseableIterator;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static io.servicetalk.utils.internal.PlatformDependent.throwException;

/**
 * An abstract implementation of {@link CloseableIterable} that wraps an {@link Iterable}.
 *
 * @param <T> the type of elements returned by the {@link CloseableIterator}.
 */
public abstract class AbstractCloseableIterable<T> implements CloseableIterable<T> {

    private final Iterable<T> original;

    /**
     * New instance.
     *
     * @param original {@link Iterable} that is wrapped by this {@link AbstractCloseableIterable}.
     */
    public AbstractCloseableIterable(Iterable<T> original) {
        this.original = original;
    }

    @Override
    public CloseableIterator<T> iterator() {
        final Iterator<T> iterator = original.iterator();
        return new CloseableIterator<T>() {
            private boolean closed;

            @Override
            public void close() throws Exception {
                closeIterator(iterator);
            }

            @Override
            public boolean hasNext() {
                if (!iterator.hasNext()) {
                    if (!closed) {
                        closed = true;
                        try {
                            close();
                        } catch (Exception e) {
                            throwException(e);
                        }
                    }
                    return false;
                }
                return true;
            }

            @Override
            public T next() {
                if (hasNext()) {
                    return iterator.next();
                }
                throw new NoSuchElementException();
            }
        };
    }

    /**
     * Closes an {@link Iterator} as returned by {@link Iterable#iterator()} of the {@link Iterable} that is wrapped by
     * this {@link AbstractCloseableIterable}.
     *
     * @param iterator {@link Iterator} to close.
     * @throws Exception if close failed.
     */
    protected abstract void closeIterator(Iterator<T> iterator) throws Exception;
}
