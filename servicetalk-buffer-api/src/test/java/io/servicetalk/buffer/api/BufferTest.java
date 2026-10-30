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
package io.servicetalk.buffer.api;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.PREFER_HEAP_RO_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_DIRECT_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_HEAP_ALLOCATOR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BufferTest {
    private static final byte[] BYTES = {1, 2, 3};

    private static Stream<Named<Buffer>> buffers() {
        return Stream.of(
                Named.of("empty", EMPTY_BUFFER),
                Named.of("empty heap", PREFER_HEAP_ALLOCATOR.newBuffer(8)),
                Named.of("empty direct", PREFER_DIRECT_ALLOCATOR.newBuffer(8)),
                Named.of("consumed heap", PREFER_HEAP_ALLOCATOR.wrap(BYTES.clone()).skipBytes(BYTES.length)),
                Named.of("heap", PREFER_HEAP_ALLOCATOR.wrap(BYTES.clone())),
                Named.of("heap with spare capacity", PREFER_HEAP_ALLOCATOR.newBuffer(8).writeBytes(BYTES)),
                Named.of("heap with reader index", PREFER_HEAP_ALLOCATOR.wrap(new byte[] {9, 1, 2, 3}).skipBytes(1)),
                Named.of("heap slice", PREFER_HEAP_ALLOCATOR.wrap(new byte[] {9, 1, 2, 3, 8}).slice(1, 3)),
                Named.of("heap slice with reader index",
                        PREFER_HEAP_ALLOCATOR.wrap(new byte[] {9, 8, 1, 2, 3, 7}).slice(1, 4).skipBytes(1)),
                Named.of("direct", PREFER_DIRECT_ALLOCATOR.newBuffer(8).writeBytes(BYTES)),
                Named.of("direct slice with reader index", PREFER_DIRECT_ALLOCATOR.newBuffer(8)
                        .writeBytes(new byte[] {9, 8, 1, 2, 3, 7}).slice(1, 4).skipBytes(1)),
                Named.of("read-only heap", PREFER_HEAP_ALLOCATOR.wrap(BYTES.clone()).asReadOnly()),
                Named.of("read-only direct", PREFER_DIRECT_ALLOCATOR.newBuffer(3).writeBytes(BYTES).asReadOnly()),
                Named.of("read-only ByteBuffer", PREFER_HEAP_RO_ALLOCATOR.wrap(BYTES.clone())),
                Named.of("composite", PREFER_HEAP_ALLOCATOR.newCompositeBuffer()
                        .addBuffer(PREFER_HEAP_ALLOCATOR.wrap(new byte[] {9, 1}))
                        .addBuffer(PREFER_DIRECT_ALLOCATOR.newBuffer(2).writeBytes(new byte[] {2, 3})).skipBytes(1)));
    }

    private static Stream<Arguments> copyParams() {
        return buffers().flatMap(buffer -> Stream.of(Arguments.of(buffer, false), Arguments.of(buffer, true)));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @MethodSource("buffers")
    void toByteArrayCopiesReadableBytesWithoutMovingIndices(Buffer buffer) {
        int readerIndex = buffer.readerIndex();
        int writerIndex = buffer.writerIndex();
        byte[] result = buffer.toByteArray();
        assertReadableBytesAndIndices(buffer, result, readerIndex, writerIndex);
        if (result.length != 0 && buffer.hasArray()) {
            assertThat(result, not(sameInstance(buffer.array())));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}, forceCopy={1}")
    @MethodSource("copyParams")
    void toByteArrayHonorsCopyPreferenceWithoutMovingIndices(Buffer buffer, boolean forceCopy) {
        int readerIndex = buffer.readerIndex();
        int writerIndex = buffer.writerIndex();
        byte[] result = buffer.toByteArray(forceCopy);
        assertReadableBytesAndIndices(buffer, result, readerIndex, writerIndex);
        assertArraySharing(buffer, result, forceCopy);
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}, forceCopy={1}")
    @MethodSource("copyParams")
    void toByteArrayIndexedHonorsCopyPreferenceWithoutMovingIndices(Buffer buffer, boolean forceCopy) {
        int readerIndex = buffer.readerIndex();
        int writerIndex = buffer.writerIndex();
        byte[] result = buffer.toByteArray(readerIndex, buffer.readableBytes(), forceCopy);
        assertReadableBytesAndIndices(buffer, result, readerIndex, writerIndex);
        assertArraySharing(buffer, result, forceCopy);
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @MethodSource("buffers")
    void toByteArrayIndexedCopiesSelectedRegionWithoutMovingIndices(Buffer buffer) {
        int readerIndex = buffer.readerIndex();
        int writerIndex = buffer.writerIndex();
        boolean empty = buffer.readableBytes() == 0;
        byte[] result = buffer.toByteArray(empty ? buffer.capacity() : readerIndex + 1, empty ? 0 : 1);
        assertThat(result, equalTo(empty ? new byte[0] : new byte[] {2}));
        assertThat(buffer.readerIndex(), is(readerIndex));
        assertThat(buffer.writerIndex(), is(writerIndex));
        if (!empty && buffer.hasArray()) {
            assertThat(result, not(sameInstance(buffer.array())));
        }
    }

    @Test
    void toByteArrayCopyIsIndependentOfBufferModifications() {
        Buffer buffer = PREFER_HEAP_ALLOCATOR.wrap(BYTES.clone());
        byte[] result = buffer.toByteArray();
        result[0] = 9;
        assertThat(buffer.getByte(0), is(BYTES[0]));
        buffer.setByte(1, 9);
        assertThat(result[1], is(BYTES[1]));
    }

    @Test
    void toByteArrayWithoutForceCopySharesBufferModifications() {
        byte[] bytes = BYTES.clone();
        Buffer buffer = PREFER_HEAP_ALLOCATOR.wrap(bytes);
        byte[] result = buffer.toByteArray(false);
        assertThat(result, sameInstance(bytes));
        result[0] = 9;
        assertThat(buffer.getByte(0), is((byte) 9));
        buffer.setByte(1, 8);
        assertThat(result[1], is((byte) 8));
        assertThat(buffer.readerIndex(), is(0));
        assertThat(buffer.writerIndex(), is(BYTES.length));
    }

    @Test
    void toByteArrayIndexedUsesCapacityBoundsAndPreservesIndices() {
        byte[] bytes = BYTES.clone();
        Buffer buffer = PREFER_HEAP_ALLOCATOR.wrap(bytes).readerIndex(1).writerIndex(2);
        assertThat(buffer.toByteArray(0, bytes.length), equalTo(BYTES));
        assertThat(buffer.toByteArray(0, bytes.length), not(sameInstance(bytes)));
        assertThat(buffer.toByteArray(0, bytes.length, false), sameInstance(bytes));
        assertThat(buffer.toByteArray(1, 2, false), equalTo(new byte[] {2, 3}));
        assertThat(buffer.readerIndex(), is(1));
        assertThat(buffer.writerIndex(), is(2));
    }

    @Test
    void toByteArrayEmptyRegionsReturnSharedEmptyArray() {
        byte[] empty = EMPTY_BUFFER.toByteArray();
        assertThat(empty.length, is(0));
        buffers().forEach(named -> {
            Buffer buffer = named.getPayload();
            int readerIndex = buffer.readerIndex();
            int writerIndex = buffer.writerIndex();
            assertThat(buffer.toByteArray(buffer.capacity(), 0), sameInstance(empty));
            assertThat(buffer.toByteArray(buffer.capacity(), 0, false), sameInstance(empty));
            assertThat(buffer.toByteArray(buffer.capacity(), 0, true), sameInstance(empty));
            if (buffer.readableBytes() == 0) {
                assertThat(buffer.toByteArray(), sameInstance(empty));
                assertThat(buffer.toByteArray(false), sameInstance(empty));
                assertThat(buffer.toByteArray(true), sameInstance(empty));
            }
            assertThat(buffer.readerIndex(), is(readerIndex));
            assertThat(buffer.writerIndex(), is(writerIndex));
        });
    }

    @ParameterizedTest(name = "{displayName} [{index}]: index={0}, length={1}")
    @CsvSource({"-1,0", "0,-1", "-1,-1", "4,0", "3,1", "1,3", "2,2147483647",
            "2147483647,0", "2147483647,1", "2147483647,2147483647"})
    void toByteArrayInvalidRegionThrowsWithoutMovingIndices(int index, int length) {
        Buffer buffer = PREFER_HEAP_ALLOCATOR.wrap(BYTES.clone());
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.toByteArray(index, length));
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.toByteArray(index, length, false));
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.toByteArray(index, length, true));
        assertThat(buffer.readerIndex(), is(0));
        assertThat(buffer.writerIndex(), is(BYTES.length));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: index={0}, length={1}")
    @CsvSource({"-1,0", "0,-1", "1,0", "0,1", "2147483647,2147483647"})
    void toByteArrayInvalidEmptyRegionThrows(int index, int length) {
        assertThrows(IndexOutOfBoundsException.class, () -> EMPTY_BUFFER.toByteArray(index, length));
        assertThrows(IndexOutOfBoundsException.class, () -> EMPTY_BUFFER.toByteArray(index, length, false));
        assertThrows(IndexOutOfBoundsException.class, () -> EMPTY_BUFFER.toByteArray(index, length, true));
    }

    private static void assertReadableBytesAndIndices(Buffer buffer, byte[] result, int readerIndex, int writerIndex) {
        assertThat(result, equalTo(writerIndex == readerIndex ? new byte[0] : BYTES));
        assertThat(buffer.readerIndex(), is(readerIndex));
        assertThat(buffer.writerIndex(), is(writerIndex));
    }

    private static void assertArraySharing(Buffer buffer, byte[] result, boolean forceCopy) {
        if (result.length != 0 && buffer.hasArray()) {
            boolean shared = !forceCopy && buffer.arrayOffset() == 0 && buffer.readerIndex() == 0 &&
                    buffer.array().length == result.length;
            assertThat(result, shared ? sameInstance(buffer.array()) : not(sameInstance(buffer.array())));
        }
    }
}
