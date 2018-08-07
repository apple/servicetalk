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
package io.servicetalk.serialization.api;

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

final class BlockingIterableFromIterable<T> implements BlockingIterable<T> {

    private final Iterable<T> actual;

    BlockingIterableFromIterable(final Iterable<T> actual) {
        this.actual = actual;
    }

    @Override
    public BlockingIterator<T> iterator() {
        final Iterator<T> iterator = actual.iterator();
        return new BlockingIterator<T>() {
            @Override
            public boolean hasNext(final long timeout, final TimeUnit unit) {
                return hasNext();
            }

            @Override
            public T next(final long timeout, final TimeUnit unit) {
                return next();
            }

            @Override
            public void close() {
                // noop
            }

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public T next() {
                return iterator.next();
            }
        };
    }
}
