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

import java.nio.ByteBuffer;
import java.util.stream.Stream;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.PREFER_DIRECT_RO_ALLOCATOR;
import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.PREFER_HEAP_RO_ALLOCATOR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BufferTest {
    private static final byte[] BYTES = {1, 2, 3};

    private static Stream<Named<Buffer>> buffers() {
        ByteBuffer direct = ByteBuffer.allocateDirect(BYTES.length);
        direct.put(BYTES).flip();
        return Stream.of(
                Named.of("empty", EMPTY_BUFFER),
                Named.of("read-only ByteBuffer with reader index",
                        new ReadOnlyByteBuffer(ByteBuffer.wrap(new byte[] {9, 1, 2, 3})).readerIndex(1)),
                Named.of("read-only heap", PREFER_HEAP_RO_ALLOCATOR.wrap(BYTES.clone())),
                Named.of("read-only direct", PREFER_DIRECT_RO_ALLOCATOR.wrap(direct)));
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
        assertIndependentContents(buffer, result, readerIndex);
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}, forceCopy={1}")
    @MethodSource("copyParams")
    void toByteArrayHonorsCopyPreferenceWithoutMovingIndices(Buffer buffer, boolean forceCopy) {
        int readerIndex = buffer.readerIndex();
        int writerIndex = buffer.writerIndex();
        byte[] result = buffer.toByteArray(forceCopy);
        assertReadableBytesAndIndices(buffer, result, readerIndex, writerIndex);
        assertIndependentContents(buffer, result, readerIndex);
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @MethodSource("buffers")
    void toByteArrayIndexedCopiesReadableRegionWithoutMovingIndices(Buffer buffer) {
        int readerIndex = buffer.readerIndex();
        int writerIndex = buffer.writerIndex();
        byte[] result = buffer.toByteArray(readerIndex, buffer.readableBytes());
        assertReadableBytesAndIndices(buffer, result, readerIndex, writerIndex);
        assertIndependentContents(buffer, result, readerIndex);
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @MethodSource("buffers")
    void toByteArrayIndexedCopiesSelectedRegionWithoutMovingIndices(Buffer buffer) {
        int readerIndex = buffer.readerIndex();
        int writerIndex = buffer.writerIndex();
        boolean empty = buffer.readableBytes() == 0;
        int index = empty ? buffer.capacity() : readerIndex + 1;
        byte[] result = buffer.toByteArray(index, empty ? 0 : 1);
        assertThat(result, equalTo(empty ? new byte[0] : new byte[] {2}));
        assertThat(buffer.readerIndex(), is(readerIndex));
        assertThat(buffer.writerIndex(), is(writerIndex));
        assertIndependentContents(buffer, result, index);
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
    @CsvSource({"-1,0", "0,-1", "1,0", "0,1", "2147483647,2147483647"})
    void toByteArrayInvalidEmptyRegionThrows(int index, int length) {
        assertThrows(IndexOutOfBoundsException.class, () -> EMPTY_BUFFER.toByteArray(index, length));
    }

    private static void assertReadableBytesAndIndices(Buffer buffer, byte[] result, int readerIndex, int writerIndex) {
        assertThat(result, equalTo(writerIndex == readerIndex ? new byte[0] : BYTES));
        assertThat(buffer.readerIndex(), is(readerIndex));
        assertThat(buffer.writerIndex(), is(writerIndex));
    }

    private static void assertIndependentContents(Buffer buffer, byte[] result, int index) {
        if (result.length != 0) {
            byte expected = buffer.getByte(index);
            result[0] = 9;
            assertThat(buffer.getByte(index), is(expected));
        }
    }
}
