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
package io.servicetalk.http.router.jersey.internal;

import io.servicetalk.buffer.api.Buffer;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_DIRECT_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_HEAP_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class BufferPublisherInputStreamTest {
    private static final byte[] BYTES = {1, 2, 3};

    @Test
    void readEmptyBuffersReturnsEndOfStream() throws IOException {
        Buffer heap = PREFER_HEAP_ALLOCATOR.newBuffer(8);
        Buffer direct = PREFER_DIRECT_ALLOCATOR.newBuffer(8);
        try (BufferPublisherInputStream stream = new BufferPublisherInputStream(from(EMPTY_BUFFER, heap, direct), 3)) {
            assertThat(stream.read(), is(-1));
        }
        assertThat(heap.readerIndex(), is(0));
        assertThat(direct.readerIndex(), is(0));
    }

    @Test
    void readFullHeapBufferPreservesReaderIndex() throws IOException {
        Buffer buffer = PREFER_HEAP_ALLOCATOR.wrap(BYTES.clone());
        assertStreamBytes(buffer);
        assertThat(buffer.readerIndex(), is(0));
        assertThat(buffer.writerIndex(), is(BYTES.length));
    }

    @Test
    void readHeapBufferWithSpareCapacityConsumesReadableBytes() throws IOException {
        Buffer buffer = PREFER_HEAP_ALLOCATOR.newBuffer(8).writeBytes(BYTES);
        assertStreamBytes(buffer);
        assertThat(buffer.readerIndex(), is(BYTES.length));
        assertThat(buffer.writerIndex(), is(BYTES.length));
    }

    @Test
    void readDirectBufferConsumesReadableBytes() throws IOException {
        Buffer buffer = PREFER_DIRECT_ALLOCATOR.newBuffer(8).writeBytes(new byte[] {9, 1, 2, 3}).skipBytes(1);
        assertStreamBytes(buffer);
        assertThat(buffer.readerIndex(), is(4));
        assertThat(buffer.writerIndex(), is(4));
    }

    @Test
    void readHeapSliceConsumesReadableBytes() throws IOException {
        Buffer buffer = PREFER_HEAP_ALLOCATOR.wrap(new byte[] {9, 8, 1, 2, 3, 7}).slice(1, 4).skipBytes(1);
        assertStreamBytes(buffer);
        assertThat(buffer.readerIndex(), is(4));
        assertThat(buffer.writerIndex(), is(4));
    }

    private static void assertStreamBytes(Buffer buffer) throws IOException {
        try (BufferPublisherInputStream stream = new BufferPublisherInputStream(from(buffer), 1)) {
            byte[] result = new byte[BYTES.length];
            assertThat(stream.read(result), is(BYTES.length));
            assertThat(result, equalTo(BYTES));
            assertThat(stream.read(), is(-1));
        }
    }
}
