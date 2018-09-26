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
package io.servicetalk.redis.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.api.PublisherRule;
import io.servicetalk.redis.api.RedisData.ArraySize;
import io.servicetalk.redis.api.RedisData.BulkStringChunk;
import io.servicetalk.redis.api.RedisData.BulkStringSize;
import io.servicetalk.redis.api.RedisData.CompleteBulkString;
import io.servicetalk.redis.api.RedisData.LastBulkStringChunk;
import io.servicetalk.redis.api.RedisData.SimpleString;
import io.servicetalk.redis.api.RedisRequesterUtils.ToBufferSingle;
import io.servicetalk.redis.api.RedisRequesterUtils.ToListSingle;
import io.servicetalk.transport.api.ExecutionContext;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static java.lang.String.join;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Theories.class)
public class RedisRequesterUtilsTest {

    @DataPoints("COERCE_BUFFERS_TO_CHAR_SEQUENCES_VALUES")
    public static final boolean[] COERCE_BUFFERS_TO_CHAR_SEQUENCES_VALUES = {false, true};
    @DataPoints("RESIZABLE_BUFFER_VALUES")
    public static final boolean[] RESIZABLE_BUFFER_VALUES = {false, true};

    @Rule
    public MockedSingleListenerRule<Buffer> bufferSubscriber = new MockedSingleListenerRule<>();
    @Rule
    public MockedSingleListenerRule<List<Object>> listSubscriber = new MockedSingleListenerRule<>();
    @Rule
    public PublisherRule<RedisData> publisher = new PublisherRule<>();

    private RedisRequester requestor;
    private RedisRequest request;
    private BufferAllocator allocator;

    @Before
    public void setup() {
        ExecutionContext executionContext = mock(ExecutionContext.class);
        requestor = mock(RedisRequester.class);
        request = mock(RedisRequest.class);
        allocator = DEFAULT_ALLOCATOR;
        when(requestor.executionContext()).thenReturn(executionContext);
        when(requestor.executionContext().bufferAllocator()).thenReturn(allocator);
        when(requestor.request(any())).thenReturn(publisher.getPublisher());
    }

    @Test
    public void bufferAggregationDoesResize() {
        ToBufferSingle<Buffer> aggregator = new ToBufferSingle<>(requestor, request);
        bufferSubscriber.listen(aggregator);

        Buffer buffer = allocator.newBuffer(1).writeByte(1).asReadOnly();
        BulkStringChunk bufferChunk = new BulkStringChunk(buffer);
        publisher.sendItems(bufferChunk);

        buffer = allocator.newBuffer(1).writeByte(2).asReadOnly();
        bufferChunk = new BulkStringChunk(buffer);
        publisher.sendItems(bufferChunk);

        publisher.complete();

        bufferSubscriber.verifySuccess(allocator.newBuffer(2).writeByte(1).writeByte(2));
    }

    @Test
    public void charAggregationDoesResize() {
        ToBufferSingle<Buffer> aggregator = new ToBufferSingle<>(requestor, request);
        bufferSubscriber.listen(aggregator);

        SimpleString stringChunk = new SimpleString("1");
        publisher.sendItems(stringChunk);

        stringChunk = new SimpleString("2");
        publisher.sendItems(stringChunk);

        publisher.complete();

        // Note the comparison is in terms of ascii characters because there is a string conversion.
        bufferSubscriber.verifySuccess(allocator.newBuffer(2).writeByte(49).writeByte(50));
    }

    @Theory
    public void toListParsesCompleteBulkStringChunkAsSingleElement(
            @FromDataPoints("COERCE_BUFFERS_TO_CHAR_SEQUENCES_VALUES") final boolean coerceBuffersToCharSequences,
            @FromDataPoints("RESIZABLE_BUFFER_VALUES") final boolean resizableBuffer) {

        final String chunk = "complete-bulk-string";
        final Object expected = coerceBuffersToCharSequences ? chunk : allocator.fromUtf8(chunk).asReadOnly();
        testToListForCompleteBulkStrings(expected, chunk, coerceBuffersToCharSequences, resizableBuffer);
    }

    private void testToListForCompleteBulkStrings(final Object expectedObject, final CharSequence chunk,
                                                  final boolean coerceBuffersToCharSequences, final boolean resizableBuffer) {

        ToListSingle<List<Object>> aggregator = new ToListSingle<>(requestor, request, coerceBuffersToCharSequences);
        listSubscriber.listen(aggregator);

        ArraySize arraySize = new ArraySize(1);
        publisher.sendItems(arraySize);

        Buffer buffer = allocator.fromUtf8(chunk);
        if (!resizableBuffer) {
            buffer = buffer.asReadOnly();
        }
        BulkStringChunk redisData = new CompleteBulkString(buffer);
        publisher.sendItems(redisData);

        publisher.complete();

        listSubscriber.verifySuccess(singletonList(expectedObject));
    }

    @Theory
    public void toListParsesLastBulkStringChunkAsSingleElement(
            @FromDataPoints("COERCE_BUFFERS_TO_CHAR_SEQUENCES_VALUES") final boolean coerceBuffersToCharSequences,
            @FromDataPoints("RESIZABLE_BUFFER_VALUES") final boolean resizableBuffer) {

        testToListForChunkedBulkStrings(singletonList(singletonList("last-chunk")), coerceBuffersToCharSequences, resizableBuffer);
    }

    @Theory
    public void toListParsesTwoBulkStringChunksAsSingleElement(
            @FromDataPoints("COERCE_BUFFERS_TO_CHAR_SEQUENCES_VALUES") final boolean coerceBuffersToCharSequences,
            @FromDataPoints("RESIZABLE_BUFFER_VALUES") final boolean resizableBuffer) {

        final List<String> chunks = asList("first-chunk|", "last-chunk");
        testToListForChunkedBulkStrings(singletonList(chunks), coerceBuffersToCharSequences, resizableBuffer);
    }

    @Theory
    public void toListParsesThreeBulkStringChunksAsSingleElement(
            @FromDataPoints("COERCE_BUFFERS_TO_CHAR_SEQUENCES_VALUES") final boolean coerceBuffersToCharSequences,
            @FromDataPoints("RESIZABLE_BUFFER_VALUES") final boolean resizableBuffer) {

        final List<String> chunks = asList("first-chunk|", "second-chunk|", "last-chunk");
        testToListForChunkedBulkStrings(singletonList(chunks), coerceBuffersToCharSequences, resizableBuffer);
    }

    @Theory
    public void toListParsesTwoChunkedBulkStrings(
            @FromDataPoints("COERCE_BUFFERS_TO_CHAR_SEQUENCES_VALUES") final boolean coerceBuffersToCharSequences,
            @FromDataPoints("RESIZABLE_BUFFER_VALUES") final boolean resizableBuffer) {

        final List<List<? extends CharSequence>> chunkedItems = asList(asList("1-chunk|", "1-last"), asList("2-chunk|", "2-last"));
        testToListForChunkedBulkStrings(chunkedItems, coerceBuffersToCharSequences, resizableBuffer);
    }

    private void testToListForChunkedBulkStrings(final List<List<? extends CharSequence>> chunkedItems,
                                                 final boolean coerceBuffersToCharSequences, final boolean resizableBuffer) {

        final List<Object> expectedResult = new ArrayList<>(chunkedItems.size());

        final ToListSingle<List<Object>> aggregator = new ToListSingle<>(requestor, request, coerceBuffersToCharSequences);
        listSubscriber.listen(aggregator);

        publisher.sendItems(new ArraySize(chunkedItems.size()));
        for (final List<? extends CharSequence> chunks : chunkedItems) {
            publishChunkedBulkString(chunks, resizableBuffer);

            final String expectedChunk = join("", chunks);
            if (coerceBuffersToCharSequences) {
                expectedResult.add(expectedChunk);
            } else {
                expectedResult.add(allocator.fromAscii(expectedChunk).asReadOnly());
            }
        }
        publisher.complete();

        listSubscriber.verifySuccess(expectedResult);
    }

    private void publishChunkedBulkString(final List<? extends CharSequence> chunks, final boolean resizableBuffer) {
        final int lengthOfAllChunks = chunks.stream()
                .mapToInt(CharSequence::length)
                .sum();

        final int lastIdx = chunks.size() - 1;

        publisher.sendItems(new BulkStringSize(lengthOfAllChunks));
        for (int i = 0; i < chunks.size(); i++) {
            final CharSequence chunk = chunks.get(i);
            Buffer buffer = allocator.fromAscii(chunk);
            if (!resizableBuffer) {
                buffer = buffer.asReadOnly();
            }
            publisher.sendItems(i != lastIdx ? new BulkStringChunk(buffer) : new LastBulkStringChunk(buffer));
        }
    }
}
