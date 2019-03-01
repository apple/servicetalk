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

import org.junit.Test;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_0;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpProtocolVersions.getProtocolVersion;
import static io.servicetalk.http.api.HttpProtocolVersions.newProtocolVersion;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class HttpProtocolVersionsTest {

    @Test
    public void testHttp11Constant() {
        assertEquals(1, HTTP_1_1.majorVersion());
        assertEquals(1, HTTP_1_1.minorVersion());
        assertEquals("HTTP/1.1", HTTP_1_1.toString());
        assertWriteToBuffer("HTTP/1.1", HTTP_1_1);
    }

    @Test
    public void testHttp10Constant() {
        assertEquals(1, HTTP_1_0.majorVersion());
        assertEquals(0, HTTP_1_0.minorVersion());
        assertEquals("HTTP/1.0", HTTP_1_0.toString());
        assertWriteToBuffer("HTTP/1.0", HTTP_1_0);
    }

    @Test
    public void testFromMajorAndMinorReturnsConstants() {
        assertSame(HTTP_1_1, getProtocolVersion(1, 1));
        assertSame(HTTP_1_0, getProtocolVersion(1, 0));
    }

    @Test
    public void testFromBufferAlwaysCreateNewObject() {
        final HttpProtocolVersion new11 = newProtocolVersion(DEFAULT_RO_ALLOCATOR.fromAscii("HTTP/1.1"));
        assertNotSame(HTTP_1_1, new11);
        assertEquals(HTTP_1_1, new11);

        final HttpProtocolVersion new10 = newProtocolVersion(DEFAULT_RO_ALLOCATOR.fromAscii("HTTP/1.0"));
        assertNotSame(HTTP_1_0, new10);
        assertEquals(HTTP_1_0, new10);
    }

    @Test
    public void testCreateNewProtocolVersionFromMajorAndMinor() {
        HttpProtocolVersion version98 = getProtocolVersion(9, 8);
        assertEquals(9, version98.majorVersion());
        assertEquals(8, version98.minorVersion());
        assertEquals("HTTP/9.8", version98.toString());
        assertWriteToBuffer("HTTP/9.8", version98);
    }

    @Test
    public void testCreateNewProtocolVersionFromBuffer() {
        HttpProtocolVersion version98 = newProtocolVersion(DEFAULT_RO_ALLOCATOR.fromAscii("HTTP/9.8"));
        assertEquals(9, version98.majorVersion());
        assertEquals(8, version98.minorVersion());
        assertEquals("HTTP/9.8", version98.toString());
        assertWriteToBuffer("HTTP/9.8", version98);
    }

    @Test
    public void testFromMajorAndMinorAndFromBufferReturnEquals() {
        HttpProtocolVersion fromMajorAndMinor = getProtocolVersion(7, 6);
        HttpProtocolVersion fromBuffer = newProtocolVersion(DEFAULT_RO_ALLOCATOR.fromAscii("HTTP/7.6"));

        assertEquals(fromMajorAndMinor, fromBuffer);
        assertEquals(fromMajorAndMinor.hashCode(), fromBuffer.hashCode());

        assertEquals(fromMajorAndMinor.majorVersion(), fromBuffer.majorVersion());
        assertEquals(fromMajorAndMinor.minorVersion(), fromBuffer.minorVersion());

        assertEquals(fromMajorAndMinor.toString(), fromBuffer.toString());
        assertWriteToBuffer(fromMajorAndMinor.toString(), fromBuffer);
        assertWriteToBuffer(fromBuffer.toString(), fromMajorAndMinor);
    }

    private static void assertWriteToBuffer(final String expected, final HttpProtocolVersion version) {
        final Buffer buffer = BufferAllocators.DEFAULT_ALLOCATOR.newBuffer(expected.length());
        version.writeVersionTo(buffer);
        assertEquals(DEFAULT_RO_ALLOCATOR.fromAscii(expected), buffer);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVersionIsTooSmall() {
        newProtocolVersion(DEFAULT_RO_ALLOCATOR.fromAscii("1.0"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidCharacterFound() {
        newProtocolVersion(DEFAULT_RO_ALLOCATOR.fromAscii("HTTP/11.0"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalMajorVersion() {
        newProtocolVersion(DEFAULT_RO_ALLOCATOR.fromAscii("HTTP/X.0"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalMinorVersion() {
        newProtocolVersion(DEFAULT_RO_ALLOCATOR.fromAscii("HTTP/1.X"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalMajorVersionLT0() {
        getProtocolVersion(-1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalMajorVersionGT9() {
        getProtocolVersion(10, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalMinorVersionLT0() {
        getProtocolVersion(1, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalMinorVersionGT9() {
        getProtocolVersion(1, 10);
    }
}
