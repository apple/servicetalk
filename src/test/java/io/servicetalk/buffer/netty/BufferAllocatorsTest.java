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

import io.netty.buffer.ByteBuf;
import io.servicetalk.buffer.Buffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class BufferAllocatorsTest {

    private final BufferAllocators allocators;

    public BufferAllocatorsTest(BufferAllocators allocators) {
        this.allocators = allocators;
    }

    @Parameterized.Parameters(name = "{index}: allocators = {0}")
    public static Collection<Object> data() {
        List<Object> params = new ArrayList<>();
        Collections.addAll(params, BufferAllocators.values());
        return params;
    }

    @Test
    public void testNewBuffer() {
        assertBuffer(allocators.getAllocator().newBuffer());
    }

    @Test
    public void testNewBufferDirect() {
        assertBuffer(allocators.getAllocator().newBuffer(true), true);
    }

    @Test
    public void testNewBufferHeap() {
        assertBuffer(allocators.getAllocator().newBuffer(false), false);
    }

    @Test
    public void testNewCompositeBuffer() {
        assertByteBufIsUnreleasable(allocators.getAllocator().newCompositeBuffer());
    }

    @Test
    public void testFromAscii() {
        assertBuffer(allocators.getAllocator().fromAscii("test"));
    }

    @Test
    public void testFromAsciiDirect() {
        assertBuffer(allocators.getAllocator().fromAscii("test", true), true);
    }

    @Test
    public void testFromAsciiHeap() {
        assertBuffer(allocators.getAllocator().fromAscii("test", false), false);
    }

    @Test
    public void testFromUtf8() {
        assertBuffer(allocators.getAllocator().fromUtf8("test"));
    }

    @Test
    public void testFromUtf8Direct() {
        assertBuffer(allocators.getAllocator().fromUtf8("test", true), true);
    }

    @Test
    public void testFromUtf8Heap() {
        assertBuffer(allocators.getAllocator().fromUtf8("test", false), false);
    }

    @Test
    public void testFromSequence() {
        assertBuffer(allocators.getAllocator().fromSequence("test", StandardCharsets.US_ASCII));
    }

    @Test
    public void testFromSequenceDirect() {
        assertBuffer(allocators.getAllocator().fromSequence("test", StandardCharsets.US_ASCII, true), true);
    }

    @Test
    public void testFromSequenceHeap() {
        assertBuffer(allocators.getAllocator().fromSequence("test", StandardCharsets.US_ASCII, false), false);
    }

    private void assertBuffer(Buffer buffer) {
        assertBuffer(buffer, allocators != BufferAllocators.PREFER_HEAP);
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
