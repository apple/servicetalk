/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.buffer.api;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.util.stream.Stream;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.PREFER_DIRECT_RO_ALLOCATOR;
import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.PREFER_HEAP_RO_ALLOCATOR;
import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadOnlyByteBufferTest {

    private static Stream<Named<BufferAllocator>> allocators() {
        return Stream.of(
                Named.of("heap", PREFER_HEAP_RO_ALLOCATOR),
                Named.of("direct", PREFER_DIRECT_RO_ALLOCATOR));
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void directFromString(BufferAllocator allocator) {
        String expectedString = "testing";
        ByteBuffer expectedBuffer = allocateDirect(expectedString.length());
        expectedBuffer.put(expectedString.getBytes(US_ASCII));
        expectedBuffer.flip();
        Buffer buffer1 = allocator.wrap(expectedBuffer);
        Buffer buffer2 = allocator.fromAscii("testing");
        assertEquals(buffer1, buffer2);
        assertEquals(expectedBuffer, buffer1.toNioBuffer());
        assertEquals(expectedBuffer, buffer2.toNioBuffer());
        assertEquals(expectedBuffer, buffer1.toNioBuffer(buffer1.readerIndex(), buffer1.writerIndex()));
        assertEquals(expectedBuffer, buffer2.toNioBuffer(buffer2.readerIndex(), buffer2.writerIndex()));
        assertEquals(expectedBuffer, buffer1.toNioBuffers()[0]);
        assertEquals(expectedBuffer, buffer2.toNioBuffers()[0]);
        assertEquals(expectedBuffer, buffer1.toNioBuffers(buffer1.readerIndex(), buffer1.writerIndex())[0]);
        assertEquals(expectedBuffer, buffer2.toNioBuffers(buffer2.readerIndex(), buffer2.writerIndex())[0]);
        assertEquals("testing", buffer1.toString(US_ASCII));
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void getLong(BufferAllocator allocator) {
        ByteBuffer expectedBuffer = allocateDirect(8);
        expectedBuffer.putLong(Long.MAX_VALUE);
        expectedBuffer.flip();
        Buffer buffer1 = allocator.wrap(expectedBuffer);
        assertEquals(Long.MAX_VALUE, buffer1.getLong(buffer1.readerIndex()));
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void getUnsignedShort(BufferAllocator allocator) {
        ByteBuffer expectedBuffer = allocateDirect(4);
        expectedBuffer.putShort((short) 0xffff);
        expectedBuffer.putShort((short) 0xf234);
        expectedBuffer.flip();
        Buffer buffer1 = allocator.wrap(expectedBuffer);
        assertEquals(0xffff, buffer1.getUnsignedShort(buffer1.readerIndex()));
        assertEquals(0xf234, buffer1.getUnsignedShort(buffer1.readerIndex() + 2));
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void sliceOfLittleEndianBufferPreservesOrder(BufferAllocator allocator) {
        ByteBuffer src = ByteBuffer.allocate(4).order(LITTLE_ENDIAN)
                .putShort((short) 0x1234).putShort((short) 0x5678);
        src.flip();
        Buffer buffer = allocator.wrap(src).slice(2, 2);
        // With LE preserved: 0x5678 stored as bytes 0x78 0x56, so getShortLE() reads 0x5678.
        // If order were dropped to BE, we'd read 0x7856 instead.
        assertEquals((short) 0x5678, buffer.getShortLE(0));
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void sliceStartsAtSliceOffset(BufferAllocator allocator) {
        Buffer buffer = allocator.fromAscii("text42text");
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
        Buffer buffer = allocator.wrap("text42text".getBytes(US_ASCII), 4, 2);

        assertEquals(0, buffer.readerIndex());
        assertEquals(2, buffer.writerIndex());
        assertEquals(2, buffer.capacity());
        assertEquals('4', (char) buffer.getByte(0));
        assertEquals('2', (char) buffer.getByte(1));
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void toNioBufferStartsAtSliceOffset(BufferAllocator allocator) {
        ByteBuffer nioBuffer = allocator.fromAscii("text42text").toNioBuffer(4, 2);

        assertEquals(0, nioBuffer.position());
        assertEquals(2, nioBuffer.remaining());
        assertEquals('4', (char) nioBuffer.get(0));
        assertEquals('2', (char) nioBuffer.get(1));
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void sliceOfSliceComposesOffsets(BufferAllocator allocator) {
        Buffer buffer = allocator.fromAscii("text42text");
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
        // slice(off, len) on a full wrap and wrap(bytes, off, len) should produce the same buffer under the fixed
        // coordinate system.
        byte[] bytes = "text42text".getBytes(US_ASCII);
        Buffer viaSlice = allocator.wrap(bytes).slice(4, 2);
        Buffer viaWrap = allocator.wrap(bytes, 4, 2);

        assertEquals(viaSlice, viaWrap);
        assertEquals(viaSlice.capacity(), viaWrap.capacity());
        assertEquals(viaSlice.readerIndex(), viaWrap.readerIndex());
        assertEquals(viaSlice.writerIndex(), viaWrap.writerIndex());
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copy(BufferAllocator allocator) {
        Buffer buffer = allocator.fromAscii("test");
        assertEquals(buffer, buffer.copy());
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void setNegativeReaderIndex(BufferAllocator allocator) {
        Buffer buffer = allocator.fromAscii("test");
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.readerIndex(-1));
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void setReaderIndexHigherThanWriterIndex(BufferAllocator allocator) {
        Buffer buffer = allocator.fromAscii("test");
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.readerIndex(buffer.writerIndex() + 1));
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void setWriterIndexLowerThanReaderIndex(BufferAllocator allocator) {
        Buffer buffer = allocator.fromAscii("test");
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.writerIndex(buffer.readerIndex() - 1));
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void setWriterIndexHigherThanCapacity(BufferAllocator allocator) {
        Buffer buffer = allocator.fromAscii("test");
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.writerIndex(buffer.capacity() + 1));
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void skipNegativeLength(BufferAllocator allocator) {
        Buffer buffer = allocator.fromAscii("test");
        int readerIndex = buffer.readerIndex();
        assertThrows(IllegalArgumentException.class, () -> buffer.skipBytes(-1));
        assertEquals(readerIndex, buffer.readerIndex());
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void testIndexOf(BufferAllocator allocator) {
        Buffer buffer = allocator.fromAscii("test");

        assertEquals(-1, buffer.indexOf(0, 4, (byte) 'a'));

        assertEquals(0, buffer.indexOf(0, 4, (byte) 't'));
        assertEquals(3, buffer.indexOf(1, 4, (byte) 't'));

        assertEquals(3, buffer.indexOf(4, 0, (byte) 't'));
        assertEquals(0, buffer.indexOf(3, 0, (byte) 't'));
    }
}
