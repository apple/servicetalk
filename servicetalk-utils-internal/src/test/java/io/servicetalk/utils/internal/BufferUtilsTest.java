/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.utils.internal;

import io.servicetalk.buffer.api.Buffer;

import org.junit.jupiter.api.Test;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_DIRECT_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_HEAP_ALLOCATOR;
import static io.servicetalk.utils.internal.BufferUtils.toByteArray;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

class BufferUtilsTest {
    private static final byte[] BYTES = {1, 2, 3};

    @Test
    void toByteArrayEmptyBuffersReturnSharedEmptyArray() {
        byte[] empty = toByteArray(EMPTY_BUFFER);
        Buffer[] buffers = {EMPTY_BUFFER, PREFER_HEAP_ALLOCATOR.newBuffer(8), PREFER_DIRECT_ALLOCATOR.newBuffer(8),
                PREFER_HEAP_ALLOCATOR.wrap(BYTES.clone()).skipBytes(BYTES.length)};
        for (Buffer buffer : buffers) {
            int readerIndex = buffer.readerIndex();
            int writerIndex = buffer.writerIndex();
            assertThat(toByteArray(buffer), sameInstance(empty));
            assertThat(empty.length, is(0));
            assertThat(buffer.readerIndex(), is(readerIndex));
            assertThat(buffer.writerIndex(), is(writerIndex));
        }
    }

    @Test
    void toByteArrayForceCopyEmptyBufferReturnsNewArray() {
        byte[] first = toByteArray(EMPTY_BUFFER, true);
        byte[] second = toByteArray(EMPTY_BUFFER, true);
        assertThat(first.length, is(0));
        assertThat(second.length, is(0));
        assertThat(second, not(sameInstance(first)));
        assertThat(second, not(sameInstance(toByteArray(EMPTY_BUFFER))));
    }

    @Test
    void toByteArrayFullHeapBufferReusesArrayWithoutAdvancingIndex() {
        byte[] bytes = BYTES.clone();
        Buffer buffer = PREFER_HEAP_ALLOCATOR.wrap(bytes);
        byte[] result = toByteArray(buffer);
        assertThat(result, sameInstance(bytes));
        assertThat(result, equalTo(BYTES));
        assertThat(buffer.readerIndex(), is(0));
        assertThat(buffer.writerIndex(), is(BYTES.length));
        result[0] = 9;
        assertThat(buffer.getByte(0), is((byte) 9));
    }

    @Test
    void toByteArrayForceCopyFullHeapBufferCopiesAndConsumes() {
        Buffer buffer = PREFER_HEAP_ALLOCATOR.wrap(BYTES.clone());
        byte[] result = assertCopied(buffer, true);
        result[0] = 9;
        assertThat(buffer.getByte(0), is(BYTES[0]));
    }

    @Test
    void toByteArrayHeapBufferWithSpareCapacityCopiesAndConsumes() {
        assertCopied(PREFER_HEAP_ALLOCATOR.newBuffer(8).writeBytes(BYTES), false);
    }

    @Test
    void toByteArrayHeapBufferWithAdvancedReaderIndexCopiesReadableBytes() {
        assertCopied(PREFER_HEAP_ALLOCATOR.wrap(new byte[] {9, 1, 2, 3}).skipBytes(1), false);
    }

    @Test
    void toByteArrayHeapSliceCopiesReadableBytes() {
        assertCopied(PREFER_HEAP_ALLOCATOR.wrap(new byte[] {9, 1, 2, 3, 8}).slice(1, 3), false);
    }

    @Test
    void toByteArrayHeapSliceWithAdvancedReaderIndexCopiesReadableBytes() {
        assertCopied(PREFER_HEAP_ALLOCATOR.wrap(new byte[] {9, 8, 1, 2, 3, 7}).slice(1, 4).skipBytes(1), false);
    }

    @Test
    void toByteArrayDirectBufferCopiesAndConsumes() {
        Buffer buffer = PREFER_DIRECT_ALLOCATOR.newBuffer(8).writeBytes(BYTES);
        assertThat(buffer.hasArray(), is(false));
        assertCopied(buffer, false);
    }

    @Test
    void toByteArrayDirectSliceWithAdvancedReaderIndexCopiesReadableBytes() {
        Buffer buffer = PREFER_DIRECT_ALLOCATOR.newBuffer(8).writeBytes(new byte[] {9, 8, 1, 2, 3, 7});
        assertCopied(buffer.slice(1, 4).skipBytes(1), false);
    }

    @Test
    void toByteArrayReadOnlyBufferCopiesReadableBytes() {
        assertCopied(PREFER_HEAP_ALLOCATOR.wrap(new byte[] {9, 1, 2, 3}).asReadOnly().skipBytes(1), false);
    }

    @Test
    void toByteArrayCompositeBufferCopiesReadableBytes() {
        Buffer buffer = PREFER_HEAP_ALLOCATOR.newCompositeBuffer()
                .addBuffer(PREFER_HEAP_ALLOCATOR.wrap(new byte[] {9, 1}))
                .addBuffer(PREFER_DIRECT_ALLOCATOR.newBuffer(2).writeBytes(new byte[] {2, 3})).skipBytes(1);
        assertCopied(buffer, false);
    }

    private static byte[] assertCopied(Buffer buffer, boolean forceCopy) {
        int writerIndex = buffer.writerIndex();
        byte[] result = toByteArray(buffer, forceCopy);
        assertThat(result, equalTo(BYTES));
        assertThat(buffer.readerIndex(), is(writerIndex));
        assertThat(buffer.writerIndex(), is(writerIndex));
        if (buffer.hasArray()) {
            assertThat(result, not(sameInstance(buffer.array())));
        }
        return result;
    }
}
