/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.Test;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReadOnlyBufferTest {

    @Test
    void capacity() {
        Buffer buffer = DEFAULT_ALLOCATOR.newBuffer(10).writeBytes("test".getBytes(US_ASCII));
        Buffer readOnly = buffer.asReadOnly();
        assertEquals(buffer.capacity(), readOnly.capacity());
    }

    @Test
    void maxCapacity() {
        Buffer buffer = DEFAULT_ALLOCATOR.newBuffer(10).writeBytes("test".getBytes(US_ASCII));
        Buffer readOnly = buffer.asReadOnly();
        assertEquals(buffer.maxCapacity(), readOnly.maxCapacity());
    }

    @Test
    void changeReaderIndexViaReadOnlyView() {
        Buffer buffer = DEFAULT_ALLOCATOR.fromAscii("test");
        Buffer readOnly = buffer.asReadOnly();
        assertEquals(buffer.readerIndex(), readOnly.readerIndex());
        readOnly.skipBytes(2);
        assertEquals(2, readOnly.readerIndex());
        assertEquals(buffer.readerIndex(), readOnly.readerIndex());
    }

    @Test
    void changeWriterIndexViaReadOnlyView() {
        Buffer buffer = DEFAULT_ALLOCATOR.fromAscii("test");
        Buffer readOnly = buffer.asReadOnly();
        assertEquals(buffer.writerIndex(), readOnly.writerIndex());
        readOnly.writerIndex(2);
        assertEquals(2, readOnly.writerIndex());
        assertEquals(buffer.writerIndex(), readOnly.writerIndex());
    }
}
