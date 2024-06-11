/*
 * Copyright © 2018-2021, 2024 Apple Inc. and the ServiceTalk project authors
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

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_DIRECT_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_HEAP_ALLOCATOR;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class BufferAllocatorsTest {

    private static final String TEST_NAME_FORMAT = "{index}: allocator = {0}";

    @SuppressWarnings("unused")
    private static List<Arguments> allocators() {
        return asList(arguments(named("DEFAULT_ALLOCATOR", DEFAULT_ALLOCATOR)),
                arguments(named("PREFER_DIRECT_ALLOCATOR", PREFER_DIRECT_ALLOCATOR)),
                arguments(named("PREFER_HEAP_ALLOCATOR", PREFER_HEAP_ALLOCATOR)));
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testNewBuffer(BufferAllocator allocator) {
        assertBuffer(allocator, allocator.newBuffer());
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testNewBufferDirect(BufferAllocator allocator) {
        assertBuffer(allocator.newBuffer(true), true);
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testNewBufferHeap(BufferAllocator allocator) {
        assertBuffer(allocator.newBuffer(false), false);
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testNewCompositeBuffer(BufferAllocator allocator) {
        assertBufferIsUnreleasable(allocator.newCompositeBuffer());
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testReadOnlyDirectBuffer(BufferAllocator allocator) {
        Buffer wrapped = allocator.wrap(ByteBuffer.allocateDirect(16).asReadOnlyBuffer());
        assertBuffer(wrapped, true);
        assertThat(wrapped.isReadOnly(), is(true));
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testWrapDirectByteBufferThenMakeItReadOnly(BufferAllocator allocator) {
        Buffer wrapped = allocator.wrap(ByteBuffer.allocateDirect(16)).asReadOnly();
        assertBuffer(wrapped, true);
        assertThat(wrapped.isReadOnly(), is(true));
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testReadOnlyHeapBuffer(BufferAllocator allocator) {
        Buffer wrapped = allocator.wrap(ByteBuffer.allocate(16).asReadOnlyBuffer());
        assertBuffer(wrapped, false);
        assertThat(wrapped.isReadOnly(), is(true));
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testWrapHeapByteBufferThenMakeItReadOnly(BufferAllocator allocator) {
        Buffer wrapped = allocator.wrap(ByteBuffer.allocate(16)).asReadOnly();
        assertBuffer(wrapped, false);
        assertThat(wrapped.isReadOnly(), is(true));
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testWrapByteArray(BufferAllocator allocator) {
        byte[] array = new byte[16];
        ThreadLocalRandom.current().nextBytes(array);
        Buffer wrapped = allocator.wrap(array);
        assertBuffer(wrapped, false);
        assertThat(wrapped.isReadOnly(), is(false));
        assertThat(wrapped.readableBytes(), is(equalTo(array.length)));
        byte[] dst = new byte[array.length];
        wrapped.readBytes(dst);
        assertThat(wrapped.readableBytes(), is(0));
        assertThat(dst, is(equalTo(array)));
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testWrapPartialByteArray(BufferAllocator allocator) {
        byte[] array = new byte[16];
        ThreadLocalRandom.current().nextBytes(array);
        int newLength = array.length - 4;
        Buffer wrapped = allocator.wrap(array, 2, newLength);
        assertBuffer(wrapped, false);
        assertThat(wrapped.isReadOnly(), is(false));
        assertThat(wrapped.readableBytes(), is(equalTo(newLength)));
        assertThat(wrapped.readerIndex(), is(0));
        assertThat(wrapped.writerIndex(), is(equalTo(newLength)));
        byte[] dst = new byte[array.length];
        wrapped.readBytes(dst, 0, newLength);
        assertThat(wrapped.readableBytes(), is(0));
        for (int i = 0, j = 2; i < newLength; i++, j++) {
            assertThat("Mismatch at index: " + i, dst[i], is(equalTo(array[j])));
        }
        for (int i = newLength; i < array.length; i++) {
            assertThat("Mismatch at index: " + i, dst[i], is((byte) 0));
        }
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testWrapPartialByteArrayWithIllegalArguments(BufferAllocator allocator) {
        byte[] array = new byte[16];
        assertThrows(IndexOutOfBoundsException.class, () -> allocator.wrap(array, -1, array.length));
        assertThrows(IndexOutOfBoundsException.class, () -> allocator.wrap(array, array.length + 1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> allocator.wrap(array, 2, array.length + 1));
        assertThrows(IllegalArgumentException.class, () -> allocator.wrap(array, 2, -1));
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testNewCompositeBufferWithSingleComponent(BufferAllocator allocator) {
        assertBufferIsUnreleasable(allocator.newCompositeBuffer()
                .addBuffer(allocator.fromAscii("test")));
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testNewCompositeBufferWithMultipleComponents(BufferAllocator allocator) {
        assertBufferIsUnreleasable(allocator.newCompositeBuffer()
                .addBuffer(allocator.fromAscii("test1"))
                .addBuffer(allocator.fromAscii("test2"))
                .addBuffer(allocator.fromAscii("test3")));
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testNewConsolidatedCompositeBufferWithMultipleComponents(BufferAllocator allocator) {
        assertBufferIsUnreleasable(allocator.newCompositeBuffer()
                .addBuffer(allocator.fromAscii("test1"))
                .addBuffer(allocator.fromAscii("test2"))
                .addBuffer(allocator.fromAscii("test3"))
                .consolidate());
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testFromAscii(BufferAllocator allocator) {
        assertBuffer(allocator, allocator.fromAscii("test"));
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testFromAsciiDirect(BufferAllocator allocator) {
        assertBuffer(allocator.fromAscii("test", true), true);
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testFromAsciiHeap(BufferAllocator allocator) {
        assertBuffer(allocator.fromAscii("test", false), false);
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testFromUtf8(BufferAllocator allocator) {
        assertBuffer(allocator, allocator.fromUtf8("test"));
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testFromUtf8Direct(BufferAllocator allocator) {
        assertBuffer(allocator.fromUtf8("test", true), true);
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testFromUtf8Heap(BufferAllocator allocator) {
        assertBuffer(allocator.fromUtf8("test", false), false);
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testFromSequence(BufferAllocator allocator) {
        assertBuffer(allocator, allocator.fromSequence("test", StandardCharsets.US_ASCII));
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testFromSequenceDirect(BufferAllocator allocator) {
        assertBuffer(allocator.fromSequence("test", StandardCharsets.US_ASCII, true), true);
    }

    @ParameterizedTest(name = TEST_NAME_FORMAT)
    @MethodSource("allocators")
    void testFromSequenceHeap(BufferAllocator allocator) {
        assertBuffer(allocator.fromSequence("test", StandardCharsets.US_ASCII, false), false);
    }

    private void assertBuffer(BufferAllocator allocator, Buffer buffer) {
        assertBuffer(buffer, allocator != PREFER_HEAP_ALLOCATOR);
    }

    private static void assertBuffer(Buffer buffer, boolean direct) {
        if (direct || buffer.isReadOnly()) {
            assertFalse(buffer.hasArray());
            assertThat(buffer.isDirect(), is(direct));
        } else {
            assertTrue(buffer.hasArray());
            assertFalse(buffer.isDirect());
        }
        assertBufferIsUnreleasable(buffer);
    }

    private static void assertBufferIsUnreleasable(Buffer buffer) {
        ByteBuf byteBuf = BufferUtils.toByteBuf(buffer);
        byteBuf.markReaderIndex();

        // ServiceTalk buffers are unreleasable. There are some optimizations in Netty which use `refCnt() > 1` to
        // judge if a ByteBuf maybe shared, and if not shared Netty may assume is is safe to make changes to the
        // underlying storage (e.g. write reallocation, compact data in place) of the ByteBuf which may lead to
        // visibility issues across threads and data corruption. We want to make sure `refCnt() > 1` here to imply the
        // ByteBuf maybe shared and these optimizations are not safe.
        assertThat(byteBuf.refCnt(), greaterThan(1));
        assertByteBufIsUnreleasable(byteBuf);

        assertByteBufIsUnreleasable(byteBuf.asReadOnly());
        assertByteBufIsUnreleasable(byteBuf.slice());
        assertByteBufIsUnreleasable(byteBuf.slice(0, 0));
        assertByteBufIsUnreleasable(byteBuf.retainedSlice());
        assertByteBufIsUnreleasable(byteBuf.retainedSlice(0, 0));

        assertByteBufIsUnreleasable(byteBuf.duplicate());
        assertByteBufIsUnreleasable(byteBuf.retainedDuplicate());
        assertByteBufIsUnreleasable(byteBuf.order(ByteOrder.BIG_ENDIAN));
        assertByteBufIsUnreleasable(byteBuf.order(ByteOrder.LITTLE_ENDIAN));

        assertByteBufIsUnreleasable(byteBuf.readSlice(0));
        assertByteBufIsUnreleasable(byteBuf.readSlice(byteBuf.readableBytes()));
        byteBuf.resetReaderIndex();

        assertByteBufIsUnreleasable(byteBuf.readRetainedSlice(0));
        assertByteBufIsUnreleasable(byteBuf.readRetainedSlice(byteBuf.readableBytes()));
        byteBuf.resetReaderIndex();

        assertByteBufIsUnreleasable(byteBuf.readBytes(0));
        assertByteBufIsUnreleasable(byteBuf.readBytes(byteBuf.readableBytes()));
        byteBuf.resetReaderIndex();

        assertByteBufIsUnreleasable(byteBuf.copy());
        assertByteBufIsUnreleasable(byteBuf.copy(0, 0));
    }

    private static void assertByteBufIsUnreleasable(ByteBuf byteBuf) {
        int refCnt = byteBuf.refCnt();

        // Expect greater than 0 as some operations may return Unpooled.EMPTY_BUFFER with refCnt == 1
        assertThat(refCnt, greaterThan(0));

        byteBuf.release();
        assertEquals(refCnt, byteBuf.refCnt());

        byteBuf.release(1);
        assertEquals(refCnt, byteBuf.refCnt());

        byteBuf.retain();
        assertEquals(refCnt, byteBuf.refCnt());

        byteBuf.retain(1);
        assertEquals(refCnt, byteBuf.refCnt());
    }
}
