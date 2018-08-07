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
package io.servicetalk.buffer.api;

import org.junit.Test;

import java.nio.ByteBuffer;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_ALLOCATOR;
import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.assertEquals;

public class ReadOnlyByteBufferTest {
    @Test
    public void directFromString() {
        String expectedString = "testing";
        ByteBuffer expectedBuffer = allocateDirect(expectedString.length());
        expectedBuffer.put(expectedString.getBytes(US_ASCII));
        expectedBuffer.flip();
        Buffer buffer1 = DEFAULT_ALLOCATOR.wrap(expectedBuffer);
        Buffer buffer2 = DEFAULT_ALLOCATOR.fromAscii("testing");
        assertEquals(buffer1, buffer2);
        assertEquals(expectedBuffer, buffer1.toNioBuffer());
        assertEquals(expectedBuffer, buffer2.toNioBuffer());
        assertEquals(expectedBuffer, buffer1.toNioBuffer(buffer1.getReaderIndex(), buffer1.getWriterIndex()));
        assertEquals(expectedBuffer, buffer2.toNioBuffer(buffer2.getReaderIndex(), buffer2.getWriterIndex()));
        assertEquals(expectedBuffer, buffer1.toNioBuffers()[0]);
        assertEquals(expectedBuffer, buffer2.toNioBuffers()[0]);
        assertEquals(expectedBuffer, buffer1.toNioBuffers(buffer1.getReaderIndex(), buffer1.getWriterIndex())[0]);
        assertEquals(expectedBuffer, buffer2.toNioBuffers(buffer2.getReaderIndex(), buffer2.getWriterIndex())[0]);
        assertEquals("testing", buffer1.toString(US_ASCII));
    }

    @Test
    public void getLong() {
        ByteBuffer expectedBuffer = allocateDirect(8);
        expectedBuffer.putLong(Long.MAX_VALUE);
        expectedBuffer.flip();
        Buffer buffer1 = DEFAULT_ALLOCATOR.wrap(expectedBuffer);
        assertEquals(Long.MAX_VALUE, buffer1.getLong(buffer1.getReaderIndex()));
    }
}
