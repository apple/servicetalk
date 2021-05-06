/*
 * Copyright © 2018, 2020 Apple Inc. and the ServiceTalk project authors
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

import io.netty.buffer.ByteBuf;
import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;

import static io.servicetalk.buffer.netty.BufferAllocators.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class BufferAllocatorsTest {

    private final BufferAllocator allocator;

    public BufferAllocatorsTest(BufferAllocator allocator) {
        this.allocator = allocator;
    }

    @Parameterized.Parameters(name = "{index}: allocators = {0}")
    public static Collection<Object> data() {
        return Arrays.asList(DEFAULT_ALLOCATOR, PREFER_DIRECT_ALLOCATOR, PREFER_HEAP_ALLOCATOR);
    }

    @Test
    public void testNewBuffer() {
        assertBuffer(allocator.newBuffer());
    }

    @Test
    public void testNewBufferDirect() {
        assertBuffer(allocator.newBuffer(true), true);
    }

    @Test
    public void testNewBufferHeap() {
        assertBuffer(allocator.newBuffer(false), false);
    }

    @Test
    public void testNewCompositeBuffer() {
        assertBufferIsUnreleasable(allocator.newCompositeBuffer());
    }

    @Test
    public void testReadOnlyDirectBuffer() {
        assertBuffer(allocator.wrap(ByteBuffer.allocateDirect(16).asReadOnlyBuffer()), true);
    }

    @Test
    public void testNewCompositeBufferWithSingleComponent() {
        assertBufferIsUnreleasable(allocator.newCompositeBuffer()
                .addBuffer(allocator.fromAscii("test")));
    }

    @Test
    public void testNewCompositeBufferWithMultipleComponents() {
        assertBufferIsUnreleasable(allocator.newCompositeBuffer()
                .addBuffer(allocator.fromAscii("test1"))
                .addBuffer(allocator.fromAscii("test2"))
                .addBuffer(allocator.fromAscii("test3")));
    }

    @Test
    public void testNewConsolidatedCompositeBufferWithMultipleComponents() {
        assertBufferIsUnreleasable(allocator.newCompositeBuffer()
                .addBuffer(allocator.fromAscii("test1"))
                .addBuffer(allocator.fromAscii("test2"))
                .addBuffer(allocator.fromAscii("test3"))
                .consolidate());
    }

    @Test
    public void testFromAscii() {
        assertBuffer(allocator.fromAscii("test"));
    }

    @Test
    public void testFromAsciiDirect() {
        assertBuffer(allocator.fromAscii("test", true), true);
    }

    @Test
    public void testFromAsciiHeap() {
        assertBuffer(allocator.fromAscii("test", false), false);
    }

    @Test
    public void testFromUtf8() {
        assertBuffer(allocator.fromUtf8("test"));
    }

    @Test
    public void testFromUtf8Direct() {
        assertBuffer(allocator.fromUtf8("test", true), true);
    }

    @Test
    public void testFromUtf8Heap() {
        assertBuffer(allocator.fromUtf8("test", false), false);
    }

    @Test
    public void testFromSequence() {
        assertBuffer(allocator.fromSequence("test", StandardCharsets.US_ASCII));
    }

    @Test
    public void testFromSequenceDirect() {
        assertBuffer(allocator.fromSequence("test", StandardCharsets.US_ASCII, true), true);
    }

    @Test
    public void testFromSequenceHeap() {
        assertBuffer(allocator.fromSequence("test", StandardCharsets.US_ASCII, false), false);
    }

    private void assertBuffer(Buffer buffer) {
        assertBuffer(buffer, allocator != PREFER_HEAP_ALLOCATOR);
    }

    private static void assertBuffer(Buffer buffer, boolean direct) {
        if (direct) {
            assertFalse(buffer.hasArray());
            assertTrue(buffer.isDirect());
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
