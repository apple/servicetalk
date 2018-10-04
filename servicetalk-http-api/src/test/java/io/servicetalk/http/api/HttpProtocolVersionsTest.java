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
package io.servicetalk.http.api;

import org.junit.Test;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpProtocolVersions.newProtocolVersion;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class HttpProtocolVersionsTest {

    @Test
    public void testCreateNewProtocolVersionFromMajorAndMinor() {
        HttpProtocolVersion version11 = newProtocolVersion(1, 1);
        assertEquals(HTTP_1_1.majorVersion(), version11.majorVersion());
        assertEquals(HTTP_1_1.minorVersion(), version11.minorVersion());
        assertEquals(HTTP_1_1.toString(), version11.toString());
        assertNotEquals(HTTP_1_1, version11);   // FIXME: enum does not equal to a new HttpProtocolVersion object

        HttpProtocolVersion version98 = newProtocolVersion(9, 8);
        assertEquals(9, version98.majorVersion());
        assertEquals(8, version98.minorVersion());
        assertEquals("HTTP/9.8", version98.toString());
    }

    @Test
    public void testCreateNewProtocolVersionFromBuffer() {
        HttpProtocolVersion version11 = newProtocolVersion(DEFAULT_ALLOCATOR.fromAscii("HTTP/1.1"));
        assertEquals(HTTP_1_1.majorVersion(), version11.majorVersion());
        assertEquals(HTTP_1_1.minorVersion(), version11.minorVersion());
        assertEquals(HTTP_1_1.toString(), version11.toString());
        assertNotEquals(HTTP_1_1, version11);   // FIXME: enum does not equal to a new HttpProtocolVersion object

        HttpProtocolVersion version98 = newProtocolVersion(DEFAULT_ALLOCATOR.fromAscii("HTTP/9.8"));
        assertEquals(9, version98.majorVersion());
        assertEquals(8, version98.minorVersion());
        assertEquals("HTTP/9.8", version98.toString());
    }

    @Test
    public void testEqualsAndHashCode() {
        HttpProtocolVersion fromMajorAndMinor = newProtocolVersion(1, 1);
        HttpProtocolVersion fromBuffer = newProtocolVersion(DEFAULT_ALLOCATOR.fromAscii("HTTP/1.1"));
        assertEquals(fromMajorAndMinor, fromBuffer);
        assertEquals(fromMajorAndMinor.hashCode(), fromBuffer.hashCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVersionIsTooSmall() {
        newProtocolVersion(DEFAULT_ALLOCATOR.fromAscii("1.0"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidCharacterFound() {
        newProtocolVersion(DEFAULT_ALLOCATOR.fromAscii("HTTP/11.0"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalMajorVersion() {
        newProtocolVersion(DEFAULT_ALLOCATOR.fromAscii("HTTP/X.0"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalMinorVersion() {
        newProtocolVersion(DEFAULT_ALLOCATOR.fromAscii("HTTP/1.X"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalMajorVersionLT0() {
        newProtocolVersion(-1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalMajorVersionGT9() {
        newProtocolVersion(10, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalMinorVersionLT0() {
        newProtocolVersion(1, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalMinorVersionGT9() {
        newProtocolVersion(1, 10);
    }
}
