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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.Buffer;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.LastHttpPayloadChunk;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.util.AsciiString.contentEquals;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.USER_AGENT;
import static io.servicetalk.http.api.HttpHeaderValues.KEEP_ALIVE;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static java.lang.Integer.toHexString;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpRequestDecoderTest {
    @Test
    public void contentLengthNoTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = new String(
                "GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "User-Agent: unit-test" + "\r\n" +
                "Content-Length: " + content.length + "\r\n" + "\r\n").getBytes(US_ASCII);
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
        assertTrue(channel.writeInbound(wrappedBuffer(content)));

        validateHttpRequest(channel, content.length);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void contentLengthNoTrailersHeaderWhiteSpace() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = new String(
                "GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" +
                " Connection :  keep-alive " + "\r\n" +
                "  User-Agent  :        unit-test        " + "\r\n" +
                "   Content-Length  : " + content.length + "\r\n" + "\r\n").getBytes(US_ASCII);
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
        assertTrue(channel.writeInbound(wrappedBuffer(content)));

        validateHttpRequest(channel, content.length);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void chunkedNoTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = new String(
                "GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "User-Agent: unit-test" + "\r\n" +
                "Transfer-Encoding: chunked" + "\r\n" + "\r\n" +
                toHexString(content.length) + ";\r\n").getBytes(US_ASCII);
        byte[] afterContentBytes = new String("\r\n0\r\n\r\n").getBytes(US_ASCII);
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
        assertTrue(channel.writeInbound(wrappedBuffer(content)));
        assertTrue(channel.writeInbound(wrappedBuffer(afterContentBytes)));
        validateHttpRequest(channel, -content.length);
        channel.finishAndReleaseAll();
    }

    @Test
    public void chunkedWithTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = new String(
                "GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" +
                        "Connection: keep-alive" + "\r\n" +
                        "User-Agent: unit-test" + "\r\n" +
                        "Transfer-Encoding: chunked" + "\r\n" + "\r\n" +
                        toHexString(content.length) + ";\r\n").getBytes(US_ASCII);
        byte[] afterContentBytes = new String("\r\n0\r\n" +
                "TrailerStatus: good" + "\r\n" + "\r\n").getBytes(US_ASCII);
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
        assertTrue(channel.writeInbound(wrappedBuffer(content)));
        assertTrue(channel.writeInbound(wrappedBuffer(afterContentBytes)));
        validateHttpRequest(channel, -content.length, true);
        channel.finishAndReleaseAll();
    }

    @Test
    public void chunkedWithTrailersSplitOnNetwork() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        List<byte[]> beforeContentBytes = new ArrayList<>();
        beforeContentBytes.add(new String("GET /some/pa").getBytes(US_ASCII));
        beforeContentBytes.add(new String("th?foo=bar&baz=yy").getBytes(US_ASCII));
        beforeContentBytes.add(new String("y HTTP/1.1" + "\r").getBytes(US_ASCII));
        beforeContentBytes.add(new String("\n" + "C").getBytes(US_ASCII));
        beforeContentBytes.add(new String("onnection").getBytes(US_ASCII));
        beforeContentBytes.add(new String(":").getBytes(US_ASCII));
        beforeContentBytes.add(new String(" keep-alive" + "\r").getBytes(US_ASCII));
        beforeContentBytes.add(new String("\n").getBytes(US_ASCII));
        beforeContentBytes.add(new String("\n").getBytes(US_ASCII));
        beforeContentBytes.add(new String("User-Agent: ").getBytes(US_ASCII));
        beforeContentBytes.add(new String(" unit-test").getBytes(US_ASCII));
        beforeContentBytes.add(new String("\r\n").getBytes(US_ASCII));
        beforeContentBytes.add(new String("Transfer-Encoding: chunked\r\n").getBytes(US_ASCII));
        beforeContentBytes.add(new String("\r").getBytes(US_ASCII));
        beforeContentBytes.add(new String("\n").getBytes(US_ASCII));
        beforeContentBytes.add(toHexString(content.length).getBytes(US_ASCII));
        beforeContentBytes.add(";".getBytes(US_ASCII));
        beforeContentBytes.add("\r".getBytes(US_ASCII));
        beforeContentBytes.add("\n".getBytes(US_ASCII));
        List<byte[]> afterContentBytes = new ArrayList<>();
        afterContentBytes.add(new String("\r").getBytes(US_ASCII));
        afterContentBytes.add(new String("\n").getBytes(US_ASCII));
        afterContentBytes.add(new String("0").getBytes(US_ASCII));
        afterContentBytes.add(new String("\r").getBytes(US_ASCII));
        afterContentBytes.add(new String("\n").getBytes(US_ASCII));
        afterContentBytes.add(new String("TrailerStatus").getBytes(US_ASCII));
        afterContentBytes.add(new String(": good").getBytes(US_ASCII));
        afterContentBytes.add(new String("\r").getBytes(US_ASCII));
        afterContentBytes.add(new String("\n").getBytes(US_ASCII));
        afterContentBytes.add(new String("\r").getBytes(US_ASCII));
        afterContentBytes.add(new String("\n").getBytes(US_ASCII));
        for (int i = 0; i < beforeContentBytes.size(); ++i) {
            channel.writeInbound(wrappedBuffer(beforeContentBytes.get(i)));
        }
        for (int i = 0; i < content.length; ++i) {
            channel.writeInbound(Unpooled.wrappedBuffer(new byte[] {content[i]}));
        }
        for (int i = 0; i < afterContentBytes.size(); ++i) {
            channel.writeInbound(wrappedBuffer(afterContentBytes.get(i)));
        }
        validateHttpRequest(channel, -content.length, true);
        channel.finishAndReleaseAll();
    }

    @Test
    public void chunkedNoTrailersNoContent() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = new String(
                "GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "User-Agent: unit-test" + "\r\n" +
                "Transfer-Encoding: chunked" + "\r\n" + "\r\n").getBytes(US_ASCII);
        byte[] afterContentBytes = new String("0\r\n\r\n").getBytes(US_ASCII);
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
        assertTrue(channel.writeInbound(wrappedBuffer(afterContentBytes)));

        HttpRequestMetaData request = channel.readInbound();
        assertStandardHeaders(request.getHeaders());
        LastHttpPayloadChunk lastChunk = channel.readInbound();
        assertTrue(lastChunk.getTrailers().isEmpty());
        assertEquals(0, lastChunk.getContent().getReadableBytes());
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void variableNoTrailersNoContent() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] beforeContentBytes = new String(
                "GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" +
                        "Connection: keep-alive" + "\r\n" +
                        "User-Agent: unit-test" + "\r\n" + "\r\n").getBytes(US_ASCII);
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));

        HttpRequestMetaData request = channel.readInbound();
        assertStandardHeaders(request.getHeaders());
        LastHttpPayloadChunk lastChunk = channel.readInbound();
        assertTrue(lastChunk.getTrailers().isEmpty());
        assertEquals(0, lastChunk.getContent().getReadableBytes());
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void variableNoTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = new String(
                "GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" +
                        "Connection: keep-alive" + "\r\n" +
                        "User-Agent: unit-test" + "\r\n" + "\r\n").getBytes(US_ASCII);
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
        assertTrue(channel.writeInbound(wrappedBuffer(content)));

        HttpRequestMetaData request = channel.readInbound();
        assertStandardHeaders(request.getHeaders());
        LastHttpPayloadChunk lastChunk = channel.readInbound();
        assertTrue(lastChunk.getTrailers().isEmpty());
        assertEquals(0, lastChunk.getContent().getReadableBytes());
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test(expected = DecoderException.class)
    public void variableWithTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = new String(
                "GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" +
                        "Connection: keep-alive" + "\r\n" +
                        "User-Agent: unit-test" + "\r\n" + "\r\n").getBytes(US_ASCII);
        // Note that trailers are not allowed when content is specified
        // https://tools.ietf.org/html/rfc7230.html#section-4.1
        byte[] afterContentBytes = new String("TrailerStatus: good\r\n").getBytes(US_ASCII);
        try {
            assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
            assertTrue(channel.writeInbound(wrappedBuffer(content)));
            assertTrue(channel.writeInbound(wrappedBuffer(afterContentBytes)));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test(expected = DecoderException.class)
    public void contentLengthWithTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = new String(
                "GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" +
                " Connection :  keep-alive " + "\r\n" +
                "  User-Agent  :        unit-test        " + "\r\n" +
                "   Content-Length  : " + content.length + "\r\n" + "\r\n").getBytes(US_ASCII);
        // Note that trailers are not allowed when content is specified
        // https://tools.ietf.org/html/rfc7230.html#section-4.1
        byte[] afterContentBytes = "TrailerStatus: good\r\n".getBytes(US_ASCII);
        try {
            assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
            assertTrue(channel.writeInbound(wrappedBuffer(content)));
            assertTrue(channel.writeInbound(wrappedBuffer(afterContentBytes)));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test(expected = DecoderException.class)
    public void invalidHttpVersion() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = new String("GET /some/path?foo=bar&baz=yyy HTTP/1.z" + "\r\n").getBytes(US_ASCII);
        try {
            assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static EmbeddedChannel newEmbeddedChannel() {
        HttpRequestDecoder decoder = new HttpRequestDecoder(DefaultHttpHeadersFactory.INSTANCE, 8192, 8192, 8192, true);
        decoder.setDiscardAfterReads(1);
        return new EmbeddedChannel(decoder);
    }

    private static void validateHttpRequest(EmbeddedChannel channel, int expectedContentLength) {
        validateHttpRequest(channel, expectedContentLength, false);
    }

    private static void validateHttpRequest(EmbeddedChannel channel, int expectedContentLength,
                                            boolean containsTrailers) {
        HttpRequestMetaData request = channel.readInbound();
        assertEquals("/some/path?foo=bar&baz=yyy", request.getRequestTarget());
        assertEquals(GET, request.getMethod());
        assertEquals(HTTP_1_1, request.getVersion());
        assertStandardHeaders(request.getHeaders());
        if (expectedContentLength >= 0) {
            assertSingleHeaderValue(request.getHeaders(), CONTENT_LENGTH, String.valueOf(expectedContentLength));
            LastHttpPayloadChunk chunk = channel.readInbound();
            assertEquals(expectedContentLength, chunk.getContent().getReadableBytes());
            assertTrue(chunk.getTrailers().isEmpty());
        } else {
            Buffer actual = DEFAULT_ALLOCATOR.newBuffer(-expectedContentLength);
            Object chunk;
            for (;;) {
                chunk = channel.readInbound();
                if (chunk instanceof HttpPayloadChunk && !(chunk instanceof LastHttpPayloadChunk)) {
                    actual.writeBytes(((HttpPayloadChunk) chunk).getContent());
                } else {
                    break;
                }
            }
            assertEquals(-expectedContentLength, actual.getReadableBytes());
            LastHttpPayloadChunk lastChunk = (LastHttpPayloadChunk) chunk;
            assertEquals(0, lastChunk.getContent().getReadableBytes());
            if (containsTrailers) {
                assertSingleHeaderValue(lastChunk.getTrailers(), "TrailerStatus", "good");
            } else {
                assertTrue(lastChunk.getTrailers().isEmpty());
            }
        }
    }

    private static void assertStandardHeaders(HttpHeaders headers) {
        assertSingleHeaderValue(headers, "connecTion", KEEP_ALIVE);
        assertSingleHeaderValue(headers, USER_AGENT, "unit-test");
    }

    static void assertSingleHeaderValue(HttpHeaders headers, CharSequence name, CharSequence expectedValue) {
        Iterator<? extends CharSequence> itr = headers.getAll(name);
        assertTrue("unabled to find header name '" + name + "'", itr.hasNext());
        CharSequence value = itr.next();
        assertTrue(name + " expected value of '" + expectedValue + "' but got: '" + value + "'",
                contentEquals(expectedValue, value));
        assertFalse(itr.hasNext());
    }
}
