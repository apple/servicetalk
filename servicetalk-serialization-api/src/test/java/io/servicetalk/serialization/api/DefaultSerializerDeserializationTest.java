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
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.CloseableIterable;
import io.servicetalk.concurrent.CloseableIterator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitely;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultSerializerDeserializationTest {

    private static final TypeHolder<List<String>> TYPE_FOR_LIST = new TypeHolder<List<String>>() {
    };

    private StreamingDeserializer<String> deSerializer;
    private StreamingDeserializer<List<String>> listDeSerializer;
    private SerializationProvider provider;
    private DefaultSerializer factory;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        deSerializer = mock(StreamingDeserializer.class);
        listDeSerializer = mock(StreamingDeserializer.class);
        provider = mock(SerializationProvider.class);
        when(provider.getDeserializer(String.class)).thenReturn(deSerializer);
        when(provider.getDeserializer(TYPE_FOR_LIST)).thenReturn(listDeSerializer);
        factory = new DefaultSerializer(provider);
    }

    @Test
    void applyDeserializerForPublisherWithType() throws Exception {
        Buffer first = mock(Buffer.class);
        Buffer second = mock(Buffer.class);
        when(deSerializer.deserialize(first)).thenReturn(singletonList("Hello1"));
        when(deSerializer.deserialize(second)).thenReturn(singletonList("Hello2"));

        final List<String> deserialized = awaitIndefinitely(factory.deserialize(from(first, second),
                String.class));
        verify(provider).getDeserializer(String.class);
        verify(deSerializer).deserialize(first);
        verify(deSerializer).deserialize(second);
        assertThat("Unexpected deserialized result.", deserialized, contains("Hello1", "Hello2"));
    }

    @Test
    void applyDeserializerForPublisherWithTypeHolder() throws Exception {
        final List<String> firstList = singletonList("Hello1");
        final List<String> secondList = singletonList("Hello2");
        List<List<String>> expected = asList(firstList, secondList);

        Buffer firstBuf = mock(Buffer.class);
        Buffer secondBuf = mock(Buffer.class);
        when(listDeSerializer.deserialize(firstBuf)).thenReturn(singletonList(firstList));
        when(listDeSerializer.deserialize(secondBuf)).thenReturn(singletonList(secondList));

        final List<List<String>> deserialized = awaitIndefinitely(factory.deserialize(from(firstBuf, secondBuf),
                TYPE_FOR_LIST));
        verify(provider).getDeserializer(TYPE_FOR_LIST);
        verify(listDeSerializer).deserialize(firstBuf);
        verify(listDeSerializer).deserialize(secondBuf);
        assertThat("Unexpected deserialized result.", deserialized, equalTo(expected));
    }

    @Test
    void applyDeserializerForIterableWithType() {
        Buffer first = mock(Buffer.class);
        Buffer second = mock(Buffer.class);
        final List<Buffer> source = asList(first, second);

        when(deSerializer.deserialize(source)).thenReturn(asList("Hello1", "Hello2"));

        final Iterable<String> deserialized = factory.deserialize(source, String.class);
        verify(provider).getDeserializer(String.class);
        verify(deSerializer).deserialize(source);
        verify(deSerializer, times(0)).close();
        assertThat("Unexpected deserialized result.", deserialized, contains("Hello1", "Hello2"));
        verify(deSerializer).close();
    }

    @Test
    void applyDeserializerForIterableWithTypeHolder() {
        final List<String> firstList = singletonList("Hello1");
        final List<String> secondList = singletonList("Hello2");
        List<List<String>> expected = asList(firstList, secondList);

        Buffer firstBuf = mock(Buffer.class);
        Buffer secondBuf = mock(Buffer.class);
        final List<Buffer> source = asList(firstBuf, secondBuf);
        when(listDeSerializer.deserialize(source)).thenReturn(asList(firstList, secondList));
        final CloseableIterable<List<String>> deserialized = factory.deserialize(source, TYPE_FOR_LIST);
        verify(provider).getDeserializer(TYPE_FOR_LIST);
        verify(listDeSerializer).deserialize(source);
        verify(listDeSerializer, times(0)).close();
        assertThat("Unexpected deserialized result.", stream(deserialized.spliterator(), false).collect(toList()),
                equalTo(expected));
        verify(listDeSerializer).close();
    }

    @Test
    void applyDeserializerForBlockingIterableWithType() throws Exception {
        final Buffer first = mock(Buffer.class);
        final Buffer second = mock(Buffer.class);
        // Since deserialize(BlockingIterable) calls deserialize(Buffer), we set up the mock for these granular calls.
        when(deSerializer.deserialize(first)).thenReturn(singletonList("Hello1"));
        when(deSerializer.deserialize(second)).thenReturn(singletonList("Hello2"));

        final List<Buffer> data = asList(first, second);
        BlockingIterableMock<Buffer> source = new BlockingIterableMock<>(data);
        when(deSerializer.deserialize(source.iterable())).thenCallRealMethod();
        final BlockingIterable<String> expected = new BlockingIterableFromIterable<>(asList("Hello1", "Hello2"));

        final BlockingIterable<String> deserialized = factory.deserialize(source.iterable(), String.class);
        verify(provider).getDeserializer(String.class);
        verify(deSerializer).deserialize(source.iterable());

        drainBlockingIteratorAndVerify(deserialized, source.iterator(), expected);
    }

    @Test
    void applyDeserializerForBlockingIterableWithTypeHolder() throws Exception {
        final Buffer first = mock(Buffer.class);
        final Buffer second = mock(Buffer.class);
        // Since deserialize(BlockingIterable) calls deserialize(Buffer), we set up the mock for these granular calls.
        when(listDeSerializer.deserialize(first)).thenReturn(singletonList(singletonList("Hello1")));
        when(listDeSerializer.deserialize(second)).thenReturn(singletonList(singletonList("Hello2")));

        final List<Buffer> data = asList(first, second);
        BlockingIterableMock<Buffer> source = new BlockingIterableMock<>(data);
        when(listDeSerializer.deserialize(source.iterable())).thenCallRealMethod();
        BlockingIterable<List<String>> expected =
                new BlockingIterableFromIterable<>(asList(singletonList("Hello1"), singletonList("Hello2")));

        final BlockingIterable<List<String>> deserialized = factory.deserialize(source.iterable(), TYPE_FOR_LIST);
        verify(provider).getDeserializer(TYPE_FOR_LIST);
        verify(listDeSerializer).deserialize(source.iterable());

        drainBlockingIteratorAndVerify(deserialized, source.iterator(), expected);
    }

    private <T> void drainBlockingIteratorAndVerify(final BlockingIterable<T> deserialized,
                                                    final BlockingIterator<Buffer> sourceIterator,
                                                    final Iterable<T> expected) throws Exception {
        final BlockingIterator<T> deserializedIter = deserialized.iterator();
        int index = 0;
        for (T item : expected) {
            assertThat("Incomplete data at index: " + index,
                    deserializedIter.hasNext(1, TimeUnit.MILLISECONDS), is(true));
            final T next = deserializedIter.next(1, TimeUnit.MILLISECONDS);
            assertThat("Unexpected data at index: " + index, next, is(item));
            index++;
        }

        deserializedIter.close();
        verify(sourceIterator).close();
    }

    @Test
    void publisherCompletesWithLeftOverData() {
        Buffer first = mock(Buffer.class);
        Buffer second = mock(Buffer.class);
        when(deSerializer.deserialize(first)).thenReturn(singletonList("Hello1"));
        when(deSerializer.deserialize(second)).thenReturn(emptyList());

        when(deSerializer.hasData()).thenReturn(true);
        final IllegalStateException e = new IllegalStateException();
        doThrow(e).when(deSerializer).close();

        final Publisher<String> deserialized = factory.deserialize(from(first, second), String.class);
        TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
        toSource(deserialized).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);

        verify(provider).getDeserializer(String.class);
        verify(deSerializer).deserialize(first);
        verify(deSerializer).deserialize(second);

        assertThat(subscriber.takeOnNext(), is("Hello1"));
        assertThat(subscriber.awaitOnError(), sameInstance(e));
    }

    @Test
    void iterableCompletesWithLeftOverData() {
        Buffer first = mock(Buffer.class);
        Buffer second = mock(Buffer.class);
        final List<Buffer> source = asList(first, second);
        when(deSerializer.deserialize(source)).thenReturn(singletonList("Hello1"));

        when(deSerializer.hasData()).thenReturn(true);
        final SerializationException e = new SerializationException(DELIBERATE_EXCEPTION);
        doThrow(e).when(deSerializer).close();

        final CloseableIterable<String> deserialized = factory.deserialize(source, String.class);

        verify(provider).getDeserializer(String.class);
        verify(deSerializer).deserialize(source);
        verify(deSerializer, times(0)).close();

        final CloseableIterator<String> iterator = deserialized.iterator();
        assertThat("Iterator does not contain data.", iterator.hasNext(), is(true));
        assertThat("Unexpected data received from iterator.", iterator.next(), is("Hello1"));

        try {
            iterator.hasNext();
            Assertions.fail();
        } catch (RuntimeException re) {
            assertThat("Unexpected exception.", re.getCause(), sameInstance(e));
        }
        verify(deSerializer).close();
    }

    @Test
    void deserializeAggregatedWithType() {
        Buffer buffer = mock(Buffer.class);
        when(deSerializer.deserialize(buffer)).thenReturn(singletonList("Hello1"));

        final CloseableIterable<String> deserialized = factory.deserializeAggregated(buffer, String.class);
        verify(provider).getDeserializer(String.class);
        verify(deSerializer).deserialize(buffer);
        verify(deSerializer, times(0)).close();

        assertThat("Unexpected deserialized data.", deserialized, contains("Hello1"));
        verify(deSerializer).close();
    }

    @Test
    void deserializeAggregatedWithTypeHolder() {
        final List<List<String>> expected = singletonList(singletonList("Hello1"));
        Buffer buffer = mock(Buffer.class);
        when(listDeSerializer.deserialize(buffer)).thenReturn(expected);

        final CloseableIterable<List<String>> deserialized = factory.deserializeAggregated(buffer, TYPE_FOR_LIST);
        verify(provider).getDeserializer(TYPE_FOR_LIST);
        verify(listDeSerializer).deserialize(buffer);
        verify(listDeSerializer, times(0)).close();
        assertThat("Unexpected deserialized data.", stream(deserialized.spliterator(), false).collect(toList()),
                equalTo(expected));
        verify(listDeSerializer).close();
    }

    @Test
    void deserializeIncompleteAggregatedWithType() {
        Buffer buffer = mock(Buffer.class);
        when(deSerializer.deserialize(buffer)).thenReturn(emptyList());

        when(deSerializer.hasData()).thenReturn(true);
        final SerializationException e = new SerializationException(DELIBERATE_EXCEPTION);
        doThrow(e).when(deSerializer).close();

        final CloseableIterable<String> deserialized = factory.deserializeAggregated(buffer, String.class);
        verify(provider).getDeserializer(String.class);
        verify(deSerializer).deserialize(buffer);
        verify(deSerializer, times(0)).close();

        try {
            deserialized.iterator().hasNext();
            Assertions.fail();
        } catch (RuntimeException re) {
            assertThat("Unexpected exception.", re.getCause(), sameInstance(e));
        }
        verify(deSerializer).close();
    }

    @Test
    void deserializeIncompleteAggregatedWithTypeHolder() {
        Buffer buffer = mock(Buffer.class);
        when(listDeSerializer.deserialize(buffer)).thenReturn(emptyList());

        when(listDeSerializer.hasData()).thenReturn(true);
        final SerializationException e = new SerializationException(DELIBERATE_EXCEPTION);
        doThrow(e).when(listDeSerializer).close();

        final Iterable<List<String>> deserialized = factory.deserializeAggregated(buffer, TYPE_FOR_LIST);
        verify(provider).getDeserializer(TYPE_FOR_LIST);
        verify(listDeSerializer).deserialize(buffer);
        verify(listDeSerializer, times(0)).close();

        try {
            deserialized.iterator().hasNext();
            Assertions.fail();
        } catch (RuntimeException re) {
            assertThat("Unexpected exception.", re.getCause(), sameInstance(e));
        }
        verify(listDeSerializer).close();
    }
}
