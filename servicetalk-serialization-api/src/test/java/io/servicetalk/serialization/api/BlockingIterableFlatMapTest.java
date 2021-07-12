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

import io.servicetalk.concurrent.BlockingIterator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.AdditionalMatchers.leq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class BlockingIterableFlatMapTest {

    private BlockingIterator<Integer> source;
    private Function<Integer, Iterable<String>> mapper;
    private BlockingIterableFlatMap<Integer, String> flatMap;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws TimeoutException {
        source = mock(BlockingIterator.class);
        when(source.hasNext(leq(0), any(TimeUnit.class))).thenThrow(new TimeoutException());
        mapper = mock(Function.class);
        flatMap = new BlockingIterableFlatMap<>(() -> source, mapper);
    }

    @Test
    void emptyIteratorTimeout() throws TimeoutException {
        when(source.hasNext(anyLong(), any(TimeUnit.class))).thenReturn(false);

        assertThat("Iterator not empty.", flatMap.iterator().hasNext(1, MILLISECONDS), is(false));
        verifyZeroInteractions(mapper);
        verify(source).hasNext(anyLong(), any(TimeUnit.class));
        verifyNoMoreInteractions(source);
    }

    @Test
    void emptyIterator() {
        when(source.hasNext()).thenReturn(false);

        assertThat("Iterator not empty.", flatMap.iterator().hasNext(), is(false));
        verifyZeroInteractions(mapper);
        verify(source).hasNext();
        verifyNoMoreInteractions(source);
    }

    @Test
    void sourceReturnsNullTimeout() throws TimeoutException {
        when(source.hasNext(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(source.next(anyLong(), any(TimeUnit.class))).thenReturn(null);
        when(mapper.apply(null)).thenReturn(singletonList(null));

        final BlockingIterator<String> iterator = flatMap.iterator();
        assertThat("Iterator not empty.", iterator.hasNext(1, MILLISECONDS), is(true));
        assertThat("Unexpected result.", iterator.next(1, MILLISECONDS), is(nullValue()));
        verify(source).hasNext(anyLong(), any(TimeUnit.class));
        verify(mapper).apply(null);
        verify(source).next(anyLong(), any(TimeUnit.class));
        verifyNoMoreInteractions(source);
    }

    @Test
    void sourceReturnsNull() {
        when(source.hasNext()).thenReturn(true);
        when(source.next()).thenReturn(null);
        when(mapper.apply(null)).thenReturn(singletonList(null));

        final BlockingIterator<String> iterator = flatMap.iterator();
        assertThat("Iterator not empty.", iterator.hasNext(), is(true));
        assertThat("Unexpected result.", iterator.next(), is(nullValue()));
        verify(source).hasNext();
        verify(mapper).apply(null);
        verify(source).next();
        verifyNoMoreInteractions(source);
    }

    @Test
    void mapperReturnsEmptyIteratorTimeout() throws TimeoutException {
        when(source.hasNext(anyLong(), any(TimeUnit.class))).thenAnswer(new DynamicHasNext(1));
        when(source.next(anyLong(), any(TimeUnit.class))).thenReturn(null);
        when(mapper.apply(null)).thenReturn(emptyList());

        final BlockingIterator<String> iterator = flatMap.iterator();
        assertThat("Iterator not empty.", iterator.hasNext(1, MILLISECONDS), is(false));
        verify(source, times(2)).hasNext(anyLong(), any(TimeUnit.class));
        verify(mapper).apply(null);
        verify(source).next(anyLong(), any(TimeUnit.class));
        verifyNoMoreInteractions(source);
    }

    @Test
    void mapperReturnsEmptyIterator() {
        when(source.hasNext()).thenAnswer(new DynamicHasNext(1));
        when(source.next()).thenReturn(null);
        when(mapper.apply(null)).thenReturn(emptyList());

        final BlockingIterator<String> iterator = flatMap.iterator();
        assertThat("Iterator not empty.", iterator.hasNext(), is(false));
        verify(source, times(2)).hasNext();
        verify(mapper).apply(null);
        verify(source).next();
        verifyNoMoreInteractions(source);
    }

    @Test
    void mapperReturnsEmptyIteratorAndThenDataTimeout() throws TimeoutException {
        when(source.hasNext(anyLong(), any(TimeUnit.class))).thenAnswer(new DynamicHasNext(2));
        when(source.next(anyLong(), any(TimeUnit.class))).thenAnswer(new CountingAnswer());
        when(mapper.apply(1)).thenReturn(emptyList());
        when(mapper.apply(2)).thenReturn(singletonList("1"));

        final BlockingIterator<String> iterator = flatMap.iterator();
        assertThat("Iterator is empty.", iterator.hasNext(1, MILLISECONDS), is(true));
        verify(source, times(2)).hasNext(anyLong(), any(TimeUnit.class));
        verify(mapper).apply(1);
        verify(mapper).apply(2);
        verify(source, times(2)).next(anyLong(), any(TimeUnit.class));
        verifyNoMoreInteractions(source);
    }

    @Test
    void mapperReturnsEmptyIteratorAndThenData() {
        when(source.hasNext()).thenAnswer(new DynamicHasNext(2));
        when(source.next()).thenAnswer(new CountingAnswer());
        when(mapper.apply(1)).thenReturn(emptyList());
        when(mapper.apply(2)).thenReturn(singletonList("1"));

        final BlockingIterator<String> iterator = flatMap.iterator();
        assertThat("Iterator not empty.", iterator.hasNext(), is(true));
        assertThat("Unexpected result.", iterator.next(), is("1"));
        verify(source, times(2)).hasNext();
        verify(mapper).apply(1);
        verify(mapper).apply(2);
        verify(source, times(2)).next();
        verifyNoMoreInteractions(source);
    }

    @Test
    void oneIntegerToOneStringTimeout() throws TimeoutException {
        when(source.hasNext(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(source.next(anyLong(), any(TimeUnit.class))).thenReturn(1);
        when(mapper.apply(1)).thenReturn(singletonList("1"));

        final BlockingIterator<String> iterator = flatMap.iterator();
        verifyIteratorHasItemTimeout(iterator, "1");
    }

    @Test
    void oneIntegerToOneString() {
        when(source.hasNext()).thenReturn(true);
        when(source.next()).thenReturn(1);
        when(mapper.apply(1)).thenReturn(singletonList("1"));

        final BlockingIterator<String> iterator = flatMap.iterator();
        verifyIteratorHasItem(iterator, "1");
    }

    @Test
    void oneIntegerToMultipleStringsTimeout() throws TimeoutException {
        when(source.hasNext(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(source.next(anyLong(), any(TimeUnit.class))).thenReturn(1);
        when(mapper.apply(1)).thenReturn(asList("1", "2"));

        final BlockingIterator<String> iterator = flatMap.iterator();
        verifyIteratorHasItemTimeout(iterator, "1");
        verifyIteratorHasItemTimeout(iterator, "2");
    }

    @Test
    void oneIntegerToMultipleStrings() {
        when(source.hasNext()).thenReturn(true);
        when(source.next()).thenReturn(1);
        when(mapper.apply(1)).thenReturn(asList("1", "2"));

        final BlockingIterator<String> iterator = flatMap.iterator();

        verifyIteratorHasItem(iterator, "1");
        verifyIteratorHasItem(iterator, "2");
    }

    @Test
    void closeClosesSource() throws Exception {
        final BlockingIterator<String> iterator = flatMap.iterator();
        iterator.close();
        verify(source).close();
    }

    private void verifyIteratorHasItem(final BlockingIterator<String> iterator, String expected) {
        assertThat("Iterator completed unexpectedly.", iterator.hasNext(), is(true));
        verify(source).hasNext();
        String result = iterator.next();
        assertThat("Unexpected result.", result, is(expected));
        verify(mapper).apply(1);
        verify(source).next();
    }

    private void verifyIteratorHasItemTimeout(final BlockingIterator<String> iterator, String expected)
            throws TimeoutException {
        assertThat("Iterator completed unexpectedly.", iterator.hasNext(1, MILLISECONDS), is(true));
        verify(source).hasNext(anyLong(), any(TimeUnit.class));
        String result = iterator.next(1, MILLISECONDS);
        assertThat("Unexpected result.", result, is(expected));
        verify(mapper).apply(1);
        verify(source).next(anyLong(), any(TimeUnit.class));
    }

    private static class DynamicHasNext implements Answer<Boolean> {
        private int count;
        private final int countTillFalse;

        DynamicHasNext(final int countTillFalse) {
            this.countTillFalse = countTillFalse;
        }

        @Override
        public Boolean answer(final InvocationOnMock invocation) {
            if (count++ == countTillFalse) {
                return FALSE;
            }
            return TRUE;
        }
    }

    private static class CountingAnswer implements Answer<Integer> {
        private int count;

        @Override
        public Integer answer(final InvocationOnMock invocation) {
            return ++count;
        }
    }
}
