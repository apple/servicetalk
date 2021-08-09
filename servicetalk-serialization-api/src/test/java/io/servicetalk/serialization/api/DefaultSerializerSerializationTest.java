/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.IntUnaryOperator;

import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitelyNonNull;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@Deprecated
class DefaultSerializerSerializationTest {
    private static final TypeHolder<List<String>> TYPE_FOR_LIST = new TypeHolder<List<String>>() { };
    private IntUnaryOperator sizeEstimator;
    private List<Buffer> createdBuffers;
    private StreamingSerializer serializer;
    private BufferAllocator allocator;
    private SerializationProvider provider;
    private DefaultSerializer factory;

    @BeforeEach
    void setUp() {
        createdBuffers = new ArrayList<>();
        allocator = mock(BufferAllocator.class);
        when(allocator.newBuffer(anyInt())).then(invocation -> {
            Buffer b = mock(Buffer.class);
            createdBuffers.add(b);
            return b;
        });
        sizeEstimator = mock(IntUnaryOperator.class);
        when(sizeEstimator.applyAsInt(anyInt())).then(invocation -> ((Integer) invocation.getArgument(0)) + 1);
        serializer = mock(StreamingSerializer.class);
        provider = mock(SerializationProvider.class);
        when(provider.getSerializer(String.class)).thenReturn(serializer);
        when(provider.getSerializer(TYPE_FOR_LIST)).thenReturn(serializer);
        factory = new DefaultSerializer(provider);
    }

    @Test
    void applySerializationForPublisherWithType() throws Exception {
        final Publisher<Buffer> serialized = factory.serialize(from("Hello1", "Hello2"), allocator, String.class);
        final List<Buffer> buffers = awaitIndefinitelyNonNull(serialized);
        verify(provider).getSerializer(String.class);
        assertThat("Unexpected created buffers.", createdBuffers, hasSize(2));
        verify(serializer).serialize("Hello1", createdBuffers.get(0));
        verify(serializer).serialize("Hello2", createdBuffers.get(1));
        assertThat("Unexpected serialized buffers.", buffers, equalTo(createdBuffers));
    }

    @Test
    void applySerializationForPublisherWithTypeHolder() throws Exception {
        final List<String> first = singletonList("Hello1");
        final List<String> second = singletonList("Hello2");
        final Publisher<Buffer> serialized = factory.serialize(from(first, second), allocator, TYPE_FOR_LIST);
        final List<Buffer> buffers = awaitIndefinitelyNonNull(serialized);
        verify(provider).getSerializer(TYPE_FOR_LIST);
        assertThat("Unexpected created buffers.", createdBuffers, hasSize(2));
        verify(serializer).serialize(first, createdBuffers.get(0));
        verify(serializer).serialize(second, createdBuffers.get(1));
        assertThat("Unexpected serialized buffers.", buffers, equalTo(createdBuffers));
    }

    @Test
    void applySerializationForPublisherWithTypeAndEstimator() {
        TestPublisher<String> source = new TestPublisher<>();

        final Publisher<Buffer> serialized = factory.serialize(source, allocator, String.class, sizeEstimator);
        TestPublisherSubscriber<Buffer> subscriber = new TestPublisherSubscriber<>();
        toSource(serialized).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);

        verify(provider).getSerializer(String.class);

        Buffer expected1 = verifySerializedBufferWithSizes(source, "Hello", 1);
        assertThat(subscriber.takeOnNext(), is(expected1));
        Buffer expected2 = verifySerializedBufferWithSizes(source, "Hello", 2);
        assertThat(subscriber.takeOnNext(), is(expected2));

        source.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    void applySerializationForPublisherWithTypeHolderAndEstimator() {
        TestPublisher<List<String>> source = new TestPublisher<>();

        final Publisher<Buffer> serialized = factory.serialize(source, allocator, TYPE_FOR_LIST, sizeEstimator);
        TestPublisherSubscriber<Buffer> subscriber = new TestPublisherSubscriber<>();
        toSource(serialized).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);

        verify(provider).getSerializer(TYPE_FOR_LIST);

        Buffer expected1 = verifySerializedBufferWithSizes(source, singletonList("Hello"), 1);
        assertThat(subscriber.takeOnNext(), is(expected1));
        Buffer expected2 = verifySerializedBufferWithSizes(source, singletonList("Hello"), 2);
        assertThat(subscriber.takeOnNext(), is(expected2));

        source.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    void applySerializationForIterableWithType() {
        final Iterable<Buffer> buffers = factory.serialize(asList("Hello1", "Hello2"), allocator, String.class);
        verify(provider).getSerializer(String.class);
        assertThat("Unexpected created buffers.", createdBuffers, hasSize(2));
        verify(serializer).serialize("Hello1", createdBuffers.get(0));
        verify(serializer).serialize("Hello2", createdBuffers.get(1));
        assertThat("Unexpected serialized buffers.", buffers, equalTo(createdBuffers));
    }

    @Test
    void applySerializationForIterableWithTypeHolder() {
        final List<String> first = singletonList("Hello1");
        final List<String> second = singletonList("Hello2");
        final Iterable<Buffer> buffers = factory.serialize(asList(first, second), allocator, TYPE_FOR_LIST);
        verify(provider).getSerializer(TYPE_FOR_LIST);
        assertThat("Unexpected created buffers.", createdBuffers, hasSize(2));
        verify(serializer).serialize(first, createdBuffers.get(0));
        verify(serializer).serialize(second, createdBuffers.get(1));
        assertThat("Unexpected serialized buffers.", buffers, equalTo(createdBuffers));
    }

    @Test
    void applySerializationForIterableWithTypeAndEstimator() {
        final Iterable<Buffer> buffers = factory.serialize(asList("Hello1", "Hello2"), allocator, String.class,
                sizeEstimator);
        verify(provider).getSerializer(String.class);
        assertThat("Unexpected created buffers.", createdBuffers, hasSize(2));
        verify(serializer).serialize("Hello1", createdBuffers.get(0));
        verify(serializer).serialize("Hello2", createdBuffers.get(1));
        assertThat("Unexpected serialized buffers.", buffers, equalTo(createdBuffers));
    }

    @Test
    void applySerializationForIterableWithTypeHolderAndEstimator() {
        final List<String> first = singletonList("Hello1");
        final List<String> second = singletonList("Hello2");
        final Iterable<Buffer> buffers = factory.serialize(asList(first, second), allocator, TYPE_FOR_LIST,
                sizeEstimator);
        verify(provider).getSerializer(TYPE_FOR_LIST);
        assertThat("Unexpected created buffers.", createdBuffers, hasSize(2));
        verify(serializer).serialize(first, createdBuffers.get(0));
        verify(serializer).serialize(second, createdBuffers.get(1));
        assertThat("Unexpected serialized buffers.", buffers, equalTo(createdBuffers));
    }

    @Test
    void applySerializationForBlockingIterableWithType() throws Exception {
        final List<String> data = asList("Hello1", "Hello2");
        BlockingIterableMock<String> source = new BlockingIterableMock<>(data);
        final BlockingIterable<Buffer> buffers = factory.serialize(source.iterable(), allocator, String.class);
        verify(provider).getSerializer(String.class);

        drainBlockingIteratorAndVerify(data, source.iterator(), buffers);
    }

    @Test
    void applySerializationForBlockingIterableWithTypeHolder() throws Exception {
        final List<List<String>> data = asList(singletonList("Hello1"), singletonList("Hello2"));
        BlockingIterableMock<List<String>> source = new BlockingIterableMock<>(data);
        final BlockingIterable<Buffer> buffers = factory.serialize(source.iterable(), allocator, TYPE_FOR_LIST);
        verify(provider).getSerializer(TYPE_FOR_LIST);

        drainBlockingIteratorAndVerify(data, source.iterator(), buffers);
    }

    @Test
    void applySerializationForBlockingIterableWithTypeAndEstimator() throws Exception {
        final List<String> data = asList("Hello1", "Hello2");
        BlockingIterableMock<String> source = new BlockingIterableMock<>(data);
        final BlockingIterable<Buffer> buffers = factory.serialize(source.iterable(), allocator, String.class,
                sizeEstimator);
        verify(provider).getSerializer(String.class);

        drainBlockingIteratorAndVerify(data, source.iterator(), buffers);
    }

    @Test
    void applySerializationForBlockingIterableWithTypeHolderAndEstimator() throws Exception {
        final List<List<String>> data = asList(singletonList("Hello1"), singletonList("Hello2"));
        BlockingIterableMock<List<String>> source = new BlockingIterableMock<>(data);
        final BlockingIterable<Buffer> buffers = factory.serialize(source.iterable(), allocator, TYPE_FOR_LIST,
                sizeEstimator);
        verify(provider).getSerializer(TYPE_FOR_LIST);

        drainBlockingIteratorAndVerify(data, source.iterator(), buffers);
    }

    @Test
    void serializeSingle() {
        final Buffer buffer = factory.serialize("Hello", allocator);
        assertThat("Unexpected created buffers.", createdBuffers, hasSize(1));
        verify(provider).serialize("Hello", createdBuffers.get(0));
        assertThat("Unexpected serialized buffers.", buffer, equalTo(createdBuffers.get(0)));
    }

    @Test
    void serializeSingleWithSize() {
        final Buffer buffer = factory.serialize("Hello", allocator, 1);
        verify(allocator).newBuffer(1);
        assertThat("Unexpected created buffers.", createdBuffers, hasSize(1));
        verify(provider).serialize("Hello", createdBuffers.get(0));
        assertThat("Unexpected serialized buffers.", buffer, equalTo(createdBuffers.get(0)));
    }

    @Test
    void serializeSingleWithBuffer() {
        final Buffer buffer = mock(Buffer.class);
        factory.serialize("Hello", buffer);
        verify(provider).serialize("Hello", buffer);
        verifyNoMoreInteractions(provider);
    }

    private <T> void drainBlockingIteratorAndVerify(final Iterable<T> data, final BlockingIterator<T> mockIterator,
                                                    final BlockingIterable<Buffer> buffers) throws Exception {
        final BlockingIterator<Buffer> iterator = buffers.iterator();

        int index = 0;
        for (T datum : data) {
            assertThat("Incomplete data at index: " + index, iterator.hasNext(1, TimeUnit.MILLISECONDS),
                    is(true));
            final Buffer next = iterator.next(1, TimeUnit.MILLISECONDS);
            assertThat("Unexpected created buffers.", createdBuffers, hasSize(index + 1));
            verify(serializer).serialize(datum, createdBuffers.get(index));
            assertThat("Unexpected data at index: " + index, next, is(createdBuffers.get(index)));
            index++;
        }

        iterator.close();
        verify(mockIterator).close();
    }

    private <T> Buffer verifySerializedBufferWithSizes(final TestPublisher<T> source, T item, final int sizeEstimate) {
        when(sizeEstimator.applyAsInt(anyInt())).thenReturn(sizeEstimate);
        source.onNext(item);
        verify(allocator).newBuffer(sizeEstimate);
        assertThat("Unexpected created buffers.", createdBuffers, hasSize(1));
        final Buffer serialized = createdBuffers.remove(0);
        verify(sizeEstimator).applyAsInt(sizeEstimate - 1);
        verify(serializer).serialize(item, serialized);
        return serialized;
    }
}
