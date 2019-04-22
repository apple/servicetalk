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
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SERVER_ERROR_5XX;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SUCCESSFUL_2XX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class HttpResponseStatusTest {

    @Test
    public void testGetResponseStatusReturnsConstant() {
        assertSame(OK, HttpResponseStatus.of(200, "OK"));
        assertSame(OK, HttpResponseStatus.of(200, ""));
    }

    @Test
    public void testGetResponseStatusReturnsNewInstance() {
        final HttpResponseStatus newStatusObject =
                HttpResponseStatus.of(200, "YES");
        assertNotSame(OK, newStatusObject);
        assertEquals(OK, newStatusObject);    // reasonPhrase is ignored according to RFC
    }

    @Test
    public void testConstant() {
        final HttpResponseStatus status = NO_CONTENT;

        assertEquals(204, status.code());
        assertWriteCodeToBuffer(204, status);
        assertWriteReasonPhraseToBuffer("No Content", status);
        assertEquals(SUCCESSFUL_2XX, status.statusClass());
        assertEquals("204 No Content", status.toString());
    }

    @Test
    public void testNewObject() {
        final HttpResponseStatus status = HttpResponseStatus.of(590, "My Own Status Code");

        assertEquals(590, status.code());
        assertWriteCodeToBuffer(590, status);
        assertWriteReasonPhraseToBuffer("My Own Status Code", status);
        assertEquals(SERVER_ERROR_5XX, status.statusClass());
        assertEquals("590 My Own Status Code", status.toString());
    }

    private static void assertWriteCodeToBuffer(final int expected, final HttpResponseStatus status) {
        final Buffer buffer = BufferAllocators.DEFAULT_ALLOCATOR.newBuffer(3);
        status.writeCodeTo(buffer);
        assertEquals(DEFAULT_RO_ALLOCATOR.fromAscii(String.valueOf(expected)), buffer);
    }

    private static void assertWriteReasonPhraseToBuffer(final String expected, final HttpResponseStatus status) {
        final Buffer buffer = BufferAllocators.DEFAULT_ALLOCATOR.newBuffer(expected.length());
        status.writeReasonPhraseTo(buffer);
        assertEquals(DEFAULT_RO_ALLOCATOR.fromAscii(expected), buffer);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test2DigitStatusCodeIsNotAllowed() {
        HttpResponseStatus.of(99, "My Own Status Code");
    }

    @Test(expected = IllegalArgumentException.class)
    public void test4DigitStatusCodeIsNotAllowed() {
        HttpResponseStatus.of(1000, "My Own Status Code");
    }
}
