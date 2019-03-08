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
package io.servicetalk.redis.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisData.ArraySize;
import io.servicetalk.redis.api.RedisData.BulkStringChunk;
import io.servicetalk.redis.api.RedisData.FirstBulkStringChunk;
import io.servicetalk.redis.api.RedisData.Null;
import io.servicetalk.redis.api.RedisData.SimpleString;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the correct functionality of the {@link RedisDecoder}.
 */
public class RedisDecoderTest {

    private EmbeddedChannel channel;

    @Before
    public void setup() {
        channel = new EmbeddedChannel(new RedisDecoder());
    }

    @After
    public void teardown() {
        assertFalse(channel.finish());
    }

    @Test
    public void splitEOLDoesNotInfiniteLoop() {
        assertTrue(channel.writeInbound(byteBufOf("$6\r\nfoobar\r")));
        assertTrue(channel.writeInbound(byteBufOf("\n")));

        FirstBulkStringChunk firstStringChunk = channel.readInbound();
        assertEquals(6, firstStringChunk.bulkStringLength());
        assertEquals(asciiBuffer("foobar"), firstStringChunk.bufferValue());
        assertNull(channel.readInbound());
    }

    @Test
    public void splitFirstEOLDoesNotInfiniteLoop() {
        assertFalse(channel.writeInbound(byteBufOf("$6\r")));
        assertTrue(channel.writeInbound(byteBufOf("\nfoobar\r\n")));

        FirstBulkStringChunk firstBulkStringChunk = channel.readInbound();
        assertEquals(asciiBuffer("foobar"), firstBulkStringChunk.bufferValue());
    }

    @Test
    public void shouldDecodeSimpleString() {
        assertFalse(channel.writeInbound(byteBufOf("+")));
        assertFalse(channel.writeInbound(byteBufOf("O")));
        assertFalse(channel.writeInbound(byteBufOf("K")));
        assertTrue(channel.writeInbound(byteBufOf("\r\n")));

        SimpleString msg = channel.readInbound();

        assertEquals("OK", msg.charSequenceValue());
    }

    @Test
    public void shouldDecodeTwoSimpleStrings() {
        assertFalse(channel.writeInbound(byteBufOf("+")));
        assertFalse(channel.writeInbound(byteBufOf("O")));
        assertFalse(channel.writeInbound(byteBufOf("K")));
        assertTrue(channel.writeInbound(byteBufOf("\r\n+SEC")));
        assertTrue(channel.writeInbound(byteBufOf("OND\r\n")));

        SimpleString msg1 = channel.readInbound();
        assertEquals("OK", msg1.charSequenceValue());

        SimpleString msg2 = channel.readInbound();
        assertEquals("SECOND", msg2.charSequenceValue());
    }

    @Test
    public void shouldDecodeError() {
        String content = "ERROR sample message";
        assertFalse(channel.writeInbound(byteBufOf("-")));
        assertFalse(channel.writeInbound(byteBufOf(content)));
        assertFalse(channel.writeInbound(byteBufOf("\r")));
        assertTrue(channel.writeInbound(byteBufOf("\n")));

        RedisData.Error msg = channel.readInbound();

        assertEquals(content, msg.charSequenceValue());
    }

    @Test
    public void shouldDecodeInteger() {
        long value = 1234L;
        assertFalse(channel.writeInbound(byteBufOf(":")));
        assertFalse(channel.writeInbound(byteBufOf(Long.toString(value))));
        assertTrue(channel.writeInbound(byteBufOf("\r\n")));

        RedisData.Integer msg = channel.readInbound();

        assertEquals(value, msg.longValue());
    }

    @Test
    public void shouldDecodeBulkString() {
        String buf1 = "bulk\nst";
        String buf2 = "ring\ntest\n1234";
        byte[] content = bytesOf(buf1 + buf2);
        assertFalse(channel.writeInbound(byteBufOf("$")));
        assertFalse(channel.writeInbound(byteBufOf(Integer.toString(content.length))));
        assertTrue(channel.writeInbound(byteBufOf("\r\n")));
        assertTrue(channel.writeInbound(byteBufOf(buf1)));
        assertTrue(channel.writeInbound(byteBufOf(buf2)));
        assertTrue(channel.writeInbound(byteBufOf("\r\n")));

        BulkStringChunk stringChunk = channel.readInbound();
        assertEquals("", stringChunk.bufferValue().toString(UTF_8));
        assertEquals(21, ((FirstBulkStringChunk) stringChunk).bulkStringLength());

        stringChunk = channel.readInbound();
        assertEquals(buf1, stringChunk.bufferValue().toString(UTF_8));

        stringChunk = channel.readInbound();
        assertEquals(buf2, stringChunk.bufferValue().toString(UTF_8));

        assertNull(channel.readInbound());
    }

    @Test
    public void shouldDecodeBulkStringsOfVariousLengths() {
        String baseString = "abcdefghij";
        for (int len = 1; len <= baseString.length(); ++len) {
            String input = baseString.substring(0, len);
            assertFalse(channel.writeInbound(byteBufOf("$")));
            assertFalse(channel.writeInbound(byteBufOf(Integer.toString(input.length()))));
            assertTrue(channel.writeInbound(byteBufOf("\r\n")));
            assertTrue(channel.writeInbound(byteBufOf(input)));
            assertTrue(channel.writeInbound(byteBufOf("\r\n")));

            FirstBulkStringChunk firstStringChunk = channel.readInbound();
            assertEquals(len, firstStringChunk.bulkStringLength());
            assertEquals("", firstStringChunk.bufferValue().toString(UTF_8));

            BulkStringChunk stringChunk = channel.readInbound();
            assertEquals(input, stringChunk.bufferValue().toString(UTF_8));

            assertNull(channel.readInbound());
        }
    }

    @Test
    public void shouldDecodeBulkStringWithTrailingPayload() {
        assertTrue(channel.writeInbound(byteBufOf("$5\r\nabcde\r\n$7\r\ntrailer\r\n")));
        FirstBulkStringChunk firstBulkStringChunk = channel.readInbound();
        assertEquals("abcde", firstBulkStringChunk.bufferValue().toString(UTF_8));
        firstBulkStringChunk = channel.readInbound();
        assertEquals("trailer", firstBulkStringChunk.bufferValue().toString(UTF_8));
    }

    @Test
    public void shouldDecodePartialBulkStringWithTrailingPayload() {
        assertTrue(channel.writeInbound(byteBufOf("$5\r\nab")));
        assertTrue(channel.writeInbound(byteBufOf("cde\r\n$7\r\ntrailer\r\n")));
        FirstBulkStringChunk firstBulkStringChunk = channel.readInbound();
        assertEquals(5, firstBulkStringChunk.bulkStringLength());
        assertEquals("ab", firstBulkStringChunk.bufferValue().toString(UTF_8));
        BulkStringChunk bulkStringChunk = channel.readInbound();
        assertEquals("cde", bulkStringChunk.bufferValue().toString(UTF_8));
        firstBulkStringChunk = channel.readInbound();
        assertEquals("trailer", firstBulkStringChunk.bufferValue().toString(UTF_8));
    }

    @Test
    public void shouldDecodeEmptyBulkString() {
        assertTrue(channel.writeInbound(byteBufOf("$0\r\n\r\n")));
        FirstBulkStringChunk firstBulkStringChunk = channel.readInbound();
        assertEquals(emptyBuffer(), firstBulkStringChunk.bufferValue());
    }

    @Test
    public void shouldDecodeNullBulkString() {
        assertFalse(channel.writeInbound(byteBufOf("$")));
        assertFalse(channel.writeInbound(byteBufOf(Integer.toString(-1))));
        assertTrue(channel.writeInbound(byteBufOf("\r\n")));

        assertTrue(channel.writeInbound(byteBufOf("$")));
        assertTrue(channel.writeInbound(byteBufOf(Integer.toString(-1))));
        assertTrue(channel.writeInbound(byteBufOf("\r\n")));

        assertThat(channel.readInbound(), is(instanceOf(Null.class)));
        assertThat(channel.readInbound(), is(instanceOf(Null.class)));
    }

    @Test
    public void shouldDecodeSimpleArray() {
        assertTrue(channel.writeInbound(byteBufOf("*3\r\n")));
        assertTrue(channel.writeInbound(byteBufOf(":1234\r\n")));
        assertTrue(channel.writeInbound(byteBufOf("+sim")));
        assertTrue(channel.writeInbound(byteBufOf("ple\r\n-err")));
        assertTrue(channel.writeInbound(byteBufOf("or\r\n")));

        ArraySize arraySize = channel.readInbound();
        assertEquals(3, arraySize.longValue());
        RedisData.Integer integer = channel.readInbound();
        assertEquals(1234, integer.longValue());

        SimpleString msg = channel.readInbound();
        assertEquals("simple", msg.charSequenceValue());
        RedisData.Error error = channel.readInbound();
        assertEquals("error", error.charSequenceValue());
    }

    private static byte[] bytesOf(String s) {
        return s.getBytes(CharsetUtil.UTF_8);
    }

    private static ByteBuf byteBufOf(String s) {
        return byteBufOf(bytesOf(s));
    }

    private static ByteBuf byteBufOf(byte[] data) {
        return Unpooled.wrappedBuffer(data);
    }

    private static Buffer emptyBuffer() {
        return DEFAULT_ALLOCATOR.newBuffer();
    }

    private static Buffer asciiBuffer(CharSequence sequence) {
        return DEFAULT_ALLOCATOR.fromAscii(sequence);
    }
}
