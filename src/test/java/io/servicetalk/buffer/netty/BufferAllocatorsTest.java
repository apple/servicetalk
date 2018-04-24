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
package io.servicetalk.buffer.netty;

import io.servicetalk.buffer.Buffer;
import io.servicetalk.buffer.BufferAllocator;

import io.netty.buffer.ByteBuf;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_DIRECT_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_HEAP_ALLOCATOR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        assertByteBufIsUnreleasable(allocator.newCompositeBuffer());
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
        assertByteBufIsUnreleasable(buffer);
    }

    private static void assertByteBufIsUnreleasable(Buffer buffer) {
        ByteBuf byteBuf = BufferUtil.toByteBuf(buffer);
        assertByteBufIsUnreleasable(byteBuf);
        assertByteBufIsUnreleasable(byteBuf.slice());
        assertByteBufIsUnreleasable(byteBuf.slice(0, 0));
        assertByteBufIsUnreleasable(byteBuf.retainedSlice());

        assertByteBufIsUnreleasable(byteBuf.readSlice(0));
        assertByteBufIsUnreleasable(byteBuf.duplicate());
        assertByteBufIsUnreleasable(byteBuf.retainedDuplicate());
        assertByteBufIsUnreleasable(byteBuf.order(ByteOrder.BIG_ENDIAN));
        assertByteBufIsUnreleasable(byteBuf.order(ByteOrder.LITTLE_ENDIAN));
    }

    private static void assertByteBufIsUnreleasable(ByteBuf byteBuf) {
        int refCnt = byteBuf.refCnt();

        assertTrue(refCnt > 0);

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
