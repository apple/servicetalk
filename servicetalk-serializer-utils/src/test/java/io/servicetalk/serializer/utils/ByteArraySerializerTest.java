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
package io.servicetalk.serializer.utils;

import io.servicetalk.buffer.api.Buffer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_DIRECT_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_HEAP_ALLOCATOR;
import static io.servicetalk.serializer.utils.ByteArraySerializer.byteArraySerializer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

class ByteArraySerializerTest {
    private static final byte[] BYTES = {1, 2, 3};

    @ParameterizedTest(name = "{displayName} [{index}]: forceCopy={0}")
    @ValueSource(booleans = {false, true})
    void deserializeEmptyBufferReturnsEmptyArray(boolean forceCopy) {
        Buffer[] buffers = {PREFER_HEAP_ALLOCATOR.newBuffer(8), PREFER_DIRECT_ALLOCATOR.newBuffer(8)};
        for (Buffer buffer : buffers) {
            assertThat(byteArraySerializer(forceCopy).deserialize(buffer, PREFER_HEAP_ALLOCATOR),
                    equalTo(new byte[0]));
            assertThat(buffer.readerIndex(), is(0));
            assertThat(buffer.writerIndex(), is(0));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}]: forceCopy={0}")
    @ValueSource(booleans = {false, true})
    void deserializeFullHeapBufferHonorsCopyPreferenceAndConsumes(boolean forceCopy) {
        byte[] bytes = BYTES.clone();
        Buffer buffer = PREFER_HEAP_ALLOCATOR.wrap(bytes);
        byte[] result = byteArraySerializer(forceCopy).deserialize(buffer, PREFER_HEAP_ALLOCATOR);
        assertThat(result, equalTo(BYTES));
        assertThat(result, forceCopy ? not(sameInstance(bytes)) : sameInstance(bytes));
        assertConsumed(buffer, BYTES.length);
        result[0] = 9;
        assertThat(bytes[0], is(forceCopy ? BYTES[0] : (byte) 9));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: forceCopy={0}")
    @ValueSource(booleans = {false, true})
    void deserializeDirectBufferCopiesReadableBytesAndConsumes(boolean forceCopy) {
        Buffer buffer = PREFER_DIRECT_ALLOCATOR.newBuffer(8).writeBytes(new byte[] {9, 1, 2, 3}).skipBytes(1);
        assertThat(byteArraySerializer(forceCopy).deserialize(buffer, PREFER_DIRECT_ALLOCATOR), equalTo(BYTES));
        assertConsumed(buffer, 4);
    }

    @ParameterizedTest(name = "{displayName} [{index}]: forceCopy={0}")
    @ValueSource(booleans = {false, true})
    void deserializeHeapSliceCopiesReadableBytesAndConsumes(boolean forceCopy) {
        Buffer buffer = PREFER_HEAP_ALLOCATOR.wrap(new byte[] {9, 8, 1, 2, 3, 7}).slice(1, 4).skipBytes(1);
        byte[] result = byteArraySerializer(forceCopy).deserialize(buffer, PREFER_HEAP_ALLOCATOR);
        assertThat(result, equalTo(BYTES));
        assertThat(result, not(sameInstance(buffer.array())));
        assertConsumed(buffer, 4);
    }

    private static void assertConsumed(Buffer buffer, int writerIndex) {
        assertThat(buffer.readerIndex(), is(writerIndex));
        assertThat(buffer.writerIndex(), is(writerIndex));
        assertThat(buffer.readableBytes(), is(0));
    }
}
