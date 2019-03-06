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
package io.servicetalk.redis.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.redis.api.RedisData.RequestRedisData;
import io.servicetalk.redis.api.RedisProtocolSupport.FieldValue;

import org.junit.Test;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.redis.api.RedisRequests.writeNumber;
import static java.lang.Long.MAX_VALUE;
import static java.lang.Long.MIN_VALUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class RedisRequestsTest {
    @Test
    public void testWriteNumber() {
        Buffer buffer = DEFAULT_ALLOCATOR.newBuffer(9);
        writeNumber(buffer, 123456789);
        String result = buffer.toString(UTF_8);
        assertEquals("123456789", result);

        buffer = DEFAULT_ALLOCATOR.newBuffer(9);
        writeNumber(buffer, 0);
        result = buffer.toString(UTF_8);
        assertEquals("0", result);

        buffer = DEFAULT_ALLOCATOR.newBuffer(9);
        writeNumber(buffer, 9);
        result = buffer.toString(UTF_8);
        assertEquals("9", result);

        buffer = DEFAULT_ALLOCATOR.newBuffer(9);
        writeNumber(buffer, 10);
        result = buffer.toString(UTF_8);
        assertEquals("10", result);

        buffer = DEFAULT_ALLOCATOR.newBuffer(9);
        writeNumber(buffer, 20);
        result = buffer.toString(UTF_8);
        assertEquals("20", result);

        buffer = DEFAULT_ALLOCATOR.newBuffer(9);
        writeNumber(buffer, 31);
        result = buffer.toString(UTF_8);
        assertEquals("31", result);

        buffer = DEFAULT_ALLOCATOR.newBuffer(9);
        writeNumber(buffer, 32);
        result = buffer.toString(UTF_8);
        assertEquals("32", result);

        buffer = DEFAULT_ALLOCATOR.newBuffer(9);
        writeNumber(buffer, 33);
        result = buffer.toString(UTF_8);
        assertEquals("33", result);

        buffer = DEFAULT_ALLOCATOR.newBuffer(9);
        writeNumber(buffer, 100);
        result = buffer.toString(UTF_8);
        assertEquals("100", result);

        buffer = DEFAULT_ALLOCATOR.newBuffer(9);
        writeNumber(buffer, 101);
        result = buffer.toString(UTF_8);
        assertEquals("101", result);

        buffer = DEFAULT_ALLOCATOR.newBuffer(9);
        writeNumber(buffer, 102);
        result = buffer.toString(UTF_8);
        assertEquals("102", result);

        buffer = DEFAULT_ALLOCATOR.newBuffer(9);
        writeNumber(buffer, MAX_VALUE);
        result = buffer.toString(UTF_8);
        assertEquals("9223372036854775807", result);

        buffer = DEFAULT_ALLOCATOR.newBuffer(9);
        writeNumber(buffer, MIN_VALUE);
        result = buffer.toString(UTF_8);
        assertEquals("-9223372036854775808", result);
    }

    @Test
    public void testStringRequestArgument() {
        String arg = "abcde";
        assertWritten(
                () -> RedisRequests.calculateRequestArgumentSize(arg),
                buf -> RedisRequests.writeRequestArgument(buf, arg),
                "$5\r\nabcde\r\n");
    }

    @Test
    public void testBufferRequestArgument() {
        Buffer arg = DEFAULT_ALLOCATOR.fromUtf8("abcde");
        assertWritten(
                () -> RedisRequests.calculateRequestArgumentSize(arg),
                buf -> RedisRequests.writeRequestArgument(buf, arg),
                "$5\r\nabcde\r\n");
    }

    @Test
    public void testByteArrayRequestArgument() {
        byte[] arg = "abcde".getBytes(UTF_8);
        assertWritten(
                () -> RedisRequests.calculateRequestArgumentSize(arg),
                buf -> RedisRequests.writeRequestArgument(buf, arg),
                "$5\r\nabcde\r\n");
    }

    @Test
    public void testLongRequestArgument() {
        long arg = 12345L;
        assertWritten(
                () -> RedisRequests.calculateRequestArgumentSize(arg),
                buf -> RedisRequests.writeRequestArgument(buf, arg),
                "$5\r\n12345\r\n");
    }

    @Test
    public void testDoubleRequestArgument() {
        double arg = -123.45;
        assertWritten(
                () -> RedisRequests.calculateRequestArgumentSize(arg),
                buf -> RedisRequests.writeRequestArgument(buf, arg),
                "$7\r\n-123.45\r\n");
    }

    @Test
    public void testRequestRedisDataRequestArgument() {
        RequestRedisData arg = new FieldValue("abcde", "fghij");
        assertWritten(
                () -> arg.encodedByteCount(),
                buf -> arg.encodeTo(buf),
                "$5\r\nabcde\r\n$5\r\nfghij\r\n");
    }

    @Test
    public void testArraySizeRequestArgument() {
        long arg = 12345;
        assertWritten(
                () -> RedisRequests.calculateRequestArgumentArraySize(arg),
                buf -> RedisRequests.writeRequestArraySize(buf, arg),
                "*12345\r\n");
    }

    private void assertWritten(final IntSupplier lengthProvider, Consumer<Buffer> writer, final String expected) {
        Buffer buffer = DEFAULT_ALLOCATOR.newBuffer(lengthProvider.getAsInt());
        writer.accept(buffer);
        assertThat(buffer.toString(UTF_8), equalTo(expected));
        int expectedLength = expected.length();
        assertThat("lengthProvider did not calculate provide length", lengthProvider.getAsInt(),
                equalTo(expectedLength));
        assertThat("buffer.readableBytes() was not as expected", buffer.readableBytes(), equalTo(expectedLength));
        assertThat("buffer capacity was not as expected", buffer.capacity(), equalTo(expectedLength));
    }
}
