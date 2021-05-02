/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.http.api.HttpDataSourceTransformations.HttpBufferFilterIterable;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpDataSourceTransformationsTest {

    private static final Buffer BUFFER_1 = DEFAULT_RO_ALLOCATOR.fromAscii("1");



    private final TestPublisher<Buffer> publisher = new TestPublisher<>();

    @Test
    void hasNextWithTimeout() throws Exception {
        final HttpBufferFilterIterable filterIterable = new HttpBufferFilterIterable(publisher.toIterable());
        final BlockingIterator<Buffer> iterator = filterIterable.iterator();
        publisher.onNext(BUFFER_1);
        iterator.hasNext(1, MILLISECONDS);
        assertThat(iterator.next(1, MILLISECONDS), sameInstance(BUFFER_1));
        publisher.onComplete();
        assertThat(iterator.hasNext(1, MILLISECONDS), is(false));
    }

    @Test
    void hasNextWithTimeoutTimesOut() {
        final HttpBufferFilterIterable filterIterable = new HttpBufferFilterIterable(publisher.toIterable());
        final BlockingIterator<Buffer> iterator = filterIterable.iterator();

        assertThrows(TimeoutException.class, () -> iterator.hasNext(1, MILLISECONDS));
    }

    @Test
    void hasNext() {
        final HttpBufferFilterIterable filterIterable = new HttpBufferFilterIterable(publisher.toIterable());
        final BlockingIterator<Buffer> iterator = filterIterable.iterator();
        publisher.onNext(BUFFER_1);
        assertThat(iterator.hasNext(), is(true));
        assertThat(iterator.next(), sameInstance(BUFFER_1));
        publisher.onComplete();
        assertThat(iterator.hasNext(), is(false));
    }

    @Test
    void hasNextBlocks() throws Exception {
        final HttpBufferFilterIterable filterIterable = new HttpBufferFilterIterable(publisher.toIterable());
        final BlockingIterator<Buffer> iterator = filterIterable.iterator();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final Future<Boolean> future = executor.submit((Callable<Boolean>) iterator::hasNext);
            assertThat(publisher.isSubscribed(), is(true));
            assertThat(future.isDone(), is(false));
            publisher.onComplete();
            assertThat(future.get(), is(false));
        } finally {
            executor.shutdownNow();
        }
    }
}
