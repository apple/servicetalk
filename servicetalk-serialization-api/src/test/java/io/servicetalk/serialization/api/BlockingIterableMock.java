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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class BlockingIterableMock<T> {

    private final BlockingIterable<T> iterable;
    private final BlockingIterator<T> iterator;

    @SuppressWarnings("unchecked")
    BlockingIterableMock(Iterable<T> source) {
        iterable = mock(BlockingIterable.class);
        iterator = mock(BlockingIterator.class);
        when(iterable.iterator()).then(__ -> {
            final Iterator<T> srcIterator = source.iterator();
            when(iterator.hasNext()).then(___ -> srcIterator.hasNext());
            when(iterator.hasNext(anyLong(), any(TimeUnit.class))).then(___ -> srcIterator.hasNext());
            when(iterator.next()).then(___ -> srcIterator.next());
            when(iterator.next(anyLong(), any(TimeUnit.class))).then(___ -> srcIterator.next());
            return iterator;
        });
    }

    BlockingIterable<T> iterable() {
        return iterable;
    }

    BlockingIterator<T> iterator() {
        return iterator;
    }
}
