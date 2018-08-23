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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the correct functionality of the {@link RedisDecoder}.
 */
public class RedisDecoderTest {

    private EmbeddedChannel channel;

    @Before
    public void setup() {
        channel = new EmbeddedChannel(
                new RedisDecoder());
    }

    @After
    public void teardown() {
        assertFalse(channel.finish());
    }

    @Test
    public void splitEOLDoesNotInfiniteLoop() {
        assertTrue(channel.writeInbound(byteBufOf("$6\r\nfoobar\r")));
        assertTrue(channel.writeInbound(byteBufOf("\n")));

        RedisData.BulkStringSize stringSize = channel.readInbound();
        assertEquals(6, stringSize.getIntValue());
        RedisData.BulkStringChunk stringChunk = channel.readInbound();
        assertEquals(asciiBuffer("foobar"), stringChunk.getBufferValue());
        RedisData.LastBulkStringChunk lastStringChunk = channel.readInbound();
        assertEquals(emptyBuffer(), lastStringChunk.getBufferValue());
    }

    @Test
    public void shouldDecodeSimpleString() {
        assertFalse(channel.writeInbound(byteBufOf("+")));
        assertFalse(channel.writeInbound(byteBufOf("O")));
        assertFalse(channel.writeInbound(byteBufOf("K")));
        assertTrue(channel.writeInbound(byteBufOf("\r\n")));

        RedisData.SimpleString msg = channel.readInbound();

        assertEquals("OK", msg.getCharSequenceValue());
    }

    @Test
    public void shouldDecodeTwoSimpleStrings() {
        assertFalse(channel.writeInbound(byteBufOf("+")));
        assertFalse(channel.writeInbound(byteBufOf("O")));
        assertFalse(channel.writeInbound(byteBufOf("K")));
        assertTrue(channel.writeInbound(byteBufOf("\r\n+SEC")));
        assertTrue(channel.writeInbound(byteBufOf("OND\r\n")));

        RedisData.SimpleString msg1 = channel.readInbound();
        assertEquals("OK", msg1.getCharSequenceValue());

        RedisData.SimpleString msg2 = channel.readInbound();
        assertEquals("SECOND", msg2.getCharSequenceValue());
    }

    @Test
    public void shouldDecodeError() {
        String content = "ERROR sample message";
        assertFalse(channel.writeInbound(byteBufOf("-")));
        assertFalse(channel.writeInbound(byteBufOf(content)));
        assertFalse(channel.writeInbound(byteBufOf("\r")));
        assertTrue(channel.writeInbound(byteBufOf("\n")));

        RedisData.Error msg = channel.readInbound();

        assertEquals(content, msg.getCharSequenceValue());
    }

    @Test
    public void shouldDecodeInteger() {
        long value = 1234L;
        assertFalse(channel.writeInbound(byteBufOf(":")));
        assertFalse(channel.writeInbound(byteBufOf(Long.toString(value))));
        assertTrue(channel.writeInbound(byteBufOf("\r\n")));

        RedisData.Integer msg = channel.readInbound();

        assertEquals(value, msg.getLongValue());
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

        RedisData.BulkStringSize stringSize = channel.readInbound();
        assertEquals(content.length, stringSize.getIntValue());
        RedisData.BulkStringChunk stringChunk = channel.readInbound();
        assertEquals(asciiBuffer(buf1), stringChunk.getBufferValue());
        stringChunk = channel.readInbound();
        assertEquals(asciiBuffer(buf2), stringChunk.getBufferValue());

        RedisData.LastBulkStringChunk lastStringChunk = channel.readInbound();
        assertEquals(emptyBuffer(), lastStringChunk.getBufferValue());
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

            RedisData.BulkStringSize stringSize = channel.readInbound();
            assertEquals(len, stringSize.getIntValue());

            RedisData.BulkStringChunk stringChunk = channel.readInbound();
            assertEquals(asciiBuffer(input), stringChunk.getBufferValue());

            RedisData.LastBulkStringChunk lastStringChunk = channel.readInbound();
            assertEquals(emptyBuffer(), lastStringChunk.getBufferValue());
        }
    }

    @Test
    public void shouldDecodeEmptyBulkString() {
        byte[] content = bytesOf("");
        assertFalse(channel.writeInbound(byteBufOf("$")));
        assertFalse(channel.writeInbound(byteBufOf(Integer.toString(content.length))));
        assertFalse(channel.writeInbound(byteBufOf("\r\n")));
        assertFalse(channel.writeInbound(byteBufOf(content)));
        assertTrue(channel.writeInbound(byteBufOf("\r\n")));

        RedisData.CompleteBulkString completeBulkString = channel.readInbound();
        assertEquals(emptyBuffer(), completeBulkString.getBufferValue());
    }

    @Test
    public void shouldDecodeNullBulkString() {
        assertFalse(channel.writeInbound(byteBufOf("$")));
        assertFalse(channel.writeInbound(byteBufOf(Integer.toString(-1))));
        assertTrue(channel.writeInbound(byteBufOf("\r\n")));

        assertTrue(channel.writeInbound(byteBufOf("$")));
        assertTrue(channel.writeInbound(byteBufOf(Integer.toString(-1))));
        assertTrue(channel.writeInbound(byteBufOf("\r\n")));

        assertThat(channel.readInbound(), is(instanceOf(RedisData.Null.class)));
        assertThat(channel.readInbound(), is(instanceOf(RedisData.Null.class)));
    }

    @Test
    public void shouldDecodeSimpleArray() {
        assertTrue(channel.writeInbound(byteBufOf("*3\r\n")));
        assertTrue(channel.writeInbound(byteBufOf(":1234\r\n")));
        assertTrue(channel.writeInbound(byteBufOf("+sim")));
        assertTrue(channel.writeInbound(byteBufOf("ple\r\n-err")));
        assertTrue(channel.writeInbound(byteBufOf("or\r\n")));

        RedisData.ArraySize arraySize = channel.readInbound();
        assertEquals(3, arraySize.getLongValue());
        RedisData.Integer integer = channel.readInbound();
        assertEquals(1234, integer.getLongValue());

        RedisData.SimpleString msg = channel.readInbound();
        assertEquals("simple", msg.getCharSequenceValue());
        RedisData.Error error = channel.readInbound();
        assertEquals("error", error.getCharSequenceValue());
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
