/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.netty.BufferAllocators;

import org.junit.jupiter.api.Test;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_0;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpProtocolVersion.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpProtocolVersionTest {

    @Test
    void testHttp11Constant() {
        assertEquals(1, HTTP_1_1.major());
        assertEquals(1, HTTP_1_1.minor());
        assertEquals("HTTP/1.1", HTTP_1_1.toString());
        assertWriteToBuffer("HTTP/1.1", HTTP_1_1);
    }

    @Test
    void testHttp10Constant() {
        assertEquals(1, HTTP_1_0.major());
        assertEquals(0, HTTP_1_0.minor());
        assertEquals("HTTP/1.0", HTTP_1_0.toString());
        assertWriteToBuffer("HTTP/1.0", HTTP_1_0);
    }

    @Test
    void testFromMajorAndMinorReturnsConstants() {
        assertSame(HTTP_1_1, of(1, 1));
        assertSame(HTTP_1_0, of(1, 0));
    }

    @Test
    void testCreateNewProtocolVersionFromMajorAndMinor() {
        HttpProtocolVersion version98 = HttpProtocolVersion.of(9, 8);
        assertEquals(9, version98.major());
        assertEquals(8, version98.minor());
        assertEquals("HTTP/9.8", version98.toString());
        assertWriteToBuffer("HTTP/9.8", version98);
    }

    private static void assertWriteToBuffer(final String expected, final HttpProtocolVersion version) {
        final Buffer buffer = BufferAllocators.DEFAULT_ALLOCATOR.newBuffer();
        version.writeTo(buffer);
        assertEquals(DEFAULT_RO_ALLOCATOR.fromAscii(expected), buffer);
    }

    @Test
    void testIllegalMajorVersionLT0() {
        assertThrows(IllegalArgumentException.class, () -> HttpProtocolVersion.of(-1, 0));
    }

    @Test
    void testIllegalMajorVersionGT9() {
        assertThrows(IllegalArgumentException.class, () -> HttpProtocolVersion.of(10, 0));
    }

    @Test
    void testIllegalMinorVersionLT0() {
        assertThrows(IllegalArgumentException.class, () -> HttpProtocolVersion.of(1, -1));
    }

    @Test
    void testIllegalMinorVersionGT9() {
        assertThrows(IllegalArgumentException.class, () -> HttpProtocolVersion.of(1, 10));
    }
}
