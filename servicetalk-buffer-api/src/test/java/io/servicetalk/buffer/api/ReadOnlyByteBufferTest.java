/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadOnlyByteBufferTest {
    @Test
    void directFromString() {
        String expectedString = "testing";
        ByteBuffer expectedBuffer = allocateDirect(expectedString.length());
        expectedBuffer.put(expectedString.getBytes(US_ASCII));
        expectedBuffer.flip();
        Buffer buffer1 = DEFAULT_RO_ALLOCATOR.wrap(expectedBuffer);
        Buffer buffer2 = DEFAULT_RO_ALLOCATOR.fromAscii("testing");
        assertEquals(buffer1, buffer2);
        assertEquals(expectedBuffer, buffer1.toNioBuffer());
        assertEquals(expectedBuffer, buffer2.toNioBuffer());
        assertEquals(expectedBuffer, buffer1.toNioBuffer(buffer1.readerIndex(), buffer1.writerIndex()));
        assertEquals(expectedBuffer, buffer2.toNioBuffer(buffer2.readerIndex(), buffer2.writerIndex()));
        assertEquals(expectedBuffer, buffer1.toNioBuffers()[0]);
        assertEquals(expectedBuffer, buffer2.toNioBuffers()[0]);
        assertEquals(expectedBuffer, buffer1.toNioBuffers(buffer1.readerIndex(), buffer1.writerIndex())[0]);
        assertEquals(expectedBuffer, buffer2.toNioBuffers(buffer2.readerIndex(), buffer2.writerIndex())[0]);
        assertEquals("testing", buffer1.toString(US_ASCII));
    }

    @Test
    void getLong() {
        ByteBuffer expectedBuffer = allocateDirect(8);
        expectedBuffer.putLong(Long.MAX_VALUE);
        expectedBuffer.flip();
        Buffer buffer1 = DEFAULT_RO_ALLOCATOR.wrap(expectedBuffer);
        assertEquals(Long.MAX_VALUE, buffer1.getLong(buffer1.readerIndex()));
    }

    @Test
    void copy() {
        Buffer buffer = DEFAULT_RO_ALLOCATOR.fromAscii("test");
        assertEquals(buffer, buffer.copy());
    }

    @Test
    void setNegativeReaderIndex() {
        Buffer buffer = DEFAULT_RO_ALLOCATOR.fromAscii("test");
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.readerIndex(-1));
    }

    @Test
    void setReaderIndexHigherThanWriterIndex() {
        Buffer buffer = DEFAULT_RO_ALLOCATOR.fromAscii("test");
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.readerIndex(buffer.writerIndex() + 1));
    }

    @Test
    void setWriterIndexLowerThanReaderIndex() {
        Buffer buffer = DEFAULT_RO_ALLOCATOR.fromAscii("test");
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.writerIndex(buffer.readerIndex() - 1));
    }

    @Test
    void setWriterIndexHigherThanCapacity() {
        Buffer buffer = DEFAULT_RO_ALLOCATOR.fromAscii("test");
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.writerIndex(buffer.capacity() + 1));
    }

    @Test
    void testIndexOf() {
        Buffer buffer = DEFAULT_RO_ALLOCATOR.fromAscii("test");

        assertEquals(-1, buffer.indexOf(0, 4, (byte) 'a'));

        assertEquals(0, buffer.indexOf(0, 4, (byte) 't'));
        assertEquals(3, buffer.indexOf(1, 4, (byte) 't'));

        assertEquals(3, buffer.indexOf(4, 0, (byte) 't'));
        assertEquals(0, buffer.indexOf(3, 0, (byte) 't'));
    }
}
