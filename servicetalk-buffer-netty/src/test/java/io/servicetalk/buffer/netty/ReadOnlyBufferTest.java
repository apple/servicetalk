/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.buffer.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.util.stream.Stream;

import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_DIRECT_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_HEAP_ALLOCATOR;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReadOnlyBufferTest {

    private static Stream<Named<BufferAllocator>> allocators() {
        return Stream.of(
                Named.of("heap", PREFER_HEAP_ALLOCATOR),
                Named.of("direct", PREFER_DIRECT_ALLOCATOR));
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void capacity(BufferAllocator allocator) {
        Buffer buffer = allocator.newBuffer(10).writeBytes("test".getBytes(US_ASCII));
        Buffer readOnly = buffer.asReadOnly();
        assertEquals(buffer.capacity(), readOnly.capacity());
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void maxCapacity(BufferAllocator allocator) {
        Buffer buffer = allocator.newBuffer(10).writeBytes("test".getBytes(US_ASCII));
        Buffer readOnly = buffer.asReadOnly();
        assertEquals(buffer.maxCapacity(), readOnly.maxCapacity());
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void changeReaderIndexViaReadOnlyView(BufferAllocator allocator) {
        Buffer buffer = allocator.fromAscii("test");
        Buffer readOnly = buffer.asReadOnly();
        assertEquals(buffer.readerIndex(), readOnly.readerIndex());
        readOnly.skipBytes(2);
        assertEquals(2, readOnly.readerIndex());
        assertEquals(buffer.readerIndex(), readOnly.readerIndex());
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void changeWriterIndexViaReadOnlyView(BufferAllocator allocator) {
        Buffer buffer = allocator.fromAscii("test");
        Buffer readOnly = buffer.asReadOnly();
        assertEquals(buffer.writerIndex(), readOnly.writerIndex());
        readOnly.writerIndex(2);
        assertEquals(2, readOnly.writerIndex());
        assertEquals(buffer.writerIndex(), readOnly.writerIndex());
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void sliceStartsAtSliceOffset(BufferAllocator allocator) {
        Buffer buffer = allocator.fromAscii("text42text").asReadOnly();
        Buffer slice = buffer.slice(4, 2);

        assertEquals(0, slice.readerIndex());
        assertEquals(2, slice.writerIndex());
        assertEquals(2, slice.capacity());
        assertEquals('4', (char) slice.getByte(0));
        assertEquals('2', (char) slice.getByte(1));
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void wrapPartialArrayStartsAtOffset(BufferAllocator allocator) {
        Buffer buffer = allocator.wrap("text42text".getBytes(US_ASCII), 4, 2).asReadOnly();

        assertEquals(0, buffer.readerIndex());
        assertEquals(2, buffer.writerIndex());
        assertEquals(2, buffer.capacity());
        assertEquals('4', (char) buffer.getByte(0));
        assertEquals('2', (char) buffer.getByte(1));
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void toNioBufferStartsAtSliceOffset(BufferAllocator allocator) {
        ByteBuffer nioBuffer = allocator.fromAscii("text42text").asReadOnly().toNioBuffer(4, 2);

        assertEquals(0, nioBuffer.position());
        assertEquals(2, nioBuffer.remaining());
        assertEquals('4', (char) nioBuffer.get(0));
        assertEquals('2', (char) nioBuffer.get(1));
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void sliceOfSliceComposesOffsets(BufferAllocator allocator) {
        Buffer buffer = allocator.fromAscii("text42text").asReadOnly();
        Buffer outer = buffer.slice(2, 6);    // "xt42te"
        Buffer inner = outer.slice(2, 2);     // "42"

        assertEquals(0, inner.readerIndex());
        assertEquals(2, inner.writerIndex());
        assertEquals(2, inner.capacity());
        assertEquals('4', (char) inner.getByte(0));
        assertEquals('2', (char) inner.getByte(1));
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void sliceAndWrapPartialArrayAreEquivalent(BufferAllocator allocator) {
        // slice(off, len) on a full wrap and wrap(bytes, off, len) should produce the same read-only buffer.
        byte[] bytes = "text42text".getBytes(US_ASCII);
        Buffer viaSlice = allocator.wrap(bytes).asReadOnly().slice(4, 2);
        Buffer viaWrap = allocator.wrap(bytes, 4, 2).asReadOnly();

        assertEquals(viaSlice, viaWrap);
        assertEquals(viaSlice.capacity(), viaWrap.capacity());
        assertEquals(viaSlice.readerIndex(), viaWrap.readerIndex());
        assertEquals(viaSlice.writerIndex(), viaWrap.writerIndex());
    }
}
