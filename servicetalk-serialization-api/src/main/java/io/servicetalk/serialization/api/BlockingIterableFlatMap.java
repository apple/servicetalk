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
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.lang.System.nanoTime;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class BlockingIterableFlatMap<T, R> implements BlockingIterable<R> {

    private static final Object NULL_PLACEHOLDER = new Object();

    private final BlockingIterable<T> original;
    private final Function<T, Iterable<R>> mapper;

    BlockingIterableFlatMap(final BlockingIterable<T> original, final Function<T, Iterable<R>> mapper) {
        this.original = requireNonNull(original);
        this.mapper = requireNonNull(mapper);
    }

    @Override
    public BlockingIterator<R> iterator() {
        final BlockingIterator<T> originalIterator = original.iterator();
        return new BlockingIterator<R>() {
            @Nullable
            private Iterator<R> intermediate;
            @Nullable
            private Object prefetched;

            @Override
            public boolean hasNext(final long timeout, final TimeUnit unit) throws TimeoutException {
                if (prefetched != null) {
                    return true;
                }
                long remainingTimeoutNanos = unit.toNanos(timeout);
                long timeStampANanos = nanoTime();
                // A Buffer may not get deserialized into a T, so we need to make sure here that we can return
                // a T from next, when called. So, we fetch a T beforehand and return from next() when called.
                for (;;) {
                    if (tryFetchFromIntermediate()) {
                        return true;
                    }
                    if (!originalIterator.hasNext(remainingTimeoutNanos, NANOSECONDS)) {
                        return false;
                    }

                    final long timeStampBNanos = nanoTime();
                    remainingTimeoutNanos -= timeStampBNanos - timeStampANanos;
                    // We do not check for timeout expiry here and instead let hasNext(), next() determine what a
                    // timeout of <= 0 means. It may be that those methods decide to throw a TimeoutException or provide
                    // a fallback value.
                    intermediate = mapper.apply(originalIterator.next(remainingTimeoutNanos, NANOSECONDS)).iterator();
                    timeStampANanos = nanoTime();
                    remainingTimeoutNanos -= timeStampANanos - timeStampBNanos;
                }
            }

            @Nullable
            @Override
            public R next(final long timeout, final TimeUnit unit) throws TimeoutException {
                if (!hasNext(timeout, unit)) {
                    throw new NoSuchElementException();
                }
                return resetPrefetchAndReturn();
            }

            @Override
            public void close() throws Exception {
                originalIterator.close();
            }

            @Override
            public boolean hasNext() {
                if (prefetched != null) {
                    return true;
                }
                // A Buffer may not get deserialized into a T, so we need to make sure here that we can return
                // a T from next, when called. So, we fetch a T beforehand and return from next() when called.
                for (;;) {
                    if (tryFetchFromIntermediate()) {
                        return true;
                    }
                    if (!originalIterator.hasNext()) {
                        return false;
                    }
                    intermediate = mapper.apply(originalIterator.next()).iterator();
                }
            }

            @Nullable
            @Override
            public R next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return resetPrefetchAndReturn();
            }

            private boolean tryFetchFromIntermediate() {
                if (intermediate != null) {
                    if (intermediate.hasNext()) {
                        prefetched = intermediate.next();
                        if (prefetched == null) {
                            prefetched = NULL_PLACEHOLDER;
                        }
                        return true;
                    }
                    intermediate = null;
                }
                return false;
            }

            @Nullable
            private R resetPrefetchAndReturn() {
                assert prefetched != null;
                Object next = prefetched;
                prefetched = null;
                return unwrapNullPlaceHolder(next);
            }

            @Nullable
            @SuppressWarnings("unchecked")
            private R unwrapNullPlaceHolder(final Object next) {
                return next == NULL_PLACEHOLDER ? null : (R) next;
            }
        };
    }
}
