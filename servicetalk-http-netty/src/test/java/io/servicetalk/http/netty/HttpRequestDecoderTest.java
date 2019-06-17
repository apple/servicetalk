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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequestMetaData;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayDeque;
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
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static java.lang.Integer.toHexString;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpRequestDecoderTest {
    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    @Test
    public void noVersion() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = ("GET /some/path?foo=bar&baz=yyy " + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "User-Agent: unit-test" + "\r\n" +
                "Content-Length: " + content.length + "\r\n" + "\r\n").getBytes(US_ASCII);
        expectedException.expect(DecoderException.class);
        expectedException.expectCause(instanceOf(IllegalArgumentException.class));
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
    }

    @Test
    public void contentLengthNoTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = ("GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" +
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
        byte[] beforeContentBytes = ("GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" +
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
        byte[] beforeContentBytes = ("GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "User-Agent: unit-test" + "\r\n" +
                "Transfer-Encoding: chunked" + "\r\n" + "\r\n" +
                toHexString(content.length) + ";\r\n").getBytes(US_ASCII);
        byte[] afterContentBytes = "\r\n0\r\n\r\n".getBytes(US_ASCII);
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
        assertTrue(channel.writeInbound(wrappedBuffer(content)));
        assertTrue(channel.writeInbound(wrappedBuffer(afterContentBytes)));
        validateHttpRequest(channel, -content.length);
        channel.finishAndReleaseAll();
    }

    @Test
    public void chunkedNoTrailersMultipleLargeContent() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[4096];
        final int numChunks = 5;
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = ("GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "User-Agent: unit-test" + "\r\n" +
                "Transfer-Encoding: chunked" + "\r\n" + "\r\n").getBytes(US_ASCII);
        byte[] chunkHeaderBytes = (toHexString(content.length) + "\r\n").getBytes(US_ASCII);
        byte[] afterContentBytes = "0\r\n\r\n".getBytes(US_ASCII);
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
        for (int i = 0; i < numChunks; ++i) {
            assertTrue(channel.writeInbound(wrappedBuffer(chunkHeaderBytes)));
            assertTrue(channel.writeInbound(wrappedBuffer(content)));
            assertTrue(channel.writeInbound(wrappedBuffer("\r\n".getBytes(US_ASCII))));
        }
        assertTrue(channel.writeInbound(wrappedBuffer(afterContentBytes)));
        validateHttpRequest(channel, -(content.length * numChunks));
        channel.finishAndReleaseAll();
    }

    @Test
    public void chunkedNoTrailersMultipleLargeContentNoChunkCRLF() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = ("GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "User-Agent: unit-test" + "\r\n" +
                "Transfer-Encoding: chunked" + "\r\n" + "\r\n").getBytes(US_ASCII);
        byte[] chunkHeaderBytes = (toHexString(content.length) + "\r\n").getBytes(US_ASCII);
        byte[] afterContentBytes = "0\r\n\r\n".getBytes(US_ASCII);
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
        assertTrue(channel.writeInbound(wrappedBuffer(chunkHeaderBytes)));
        assertTrue(channel.writeInbound(wrappedBuffer(content)));
        // we omit writing the CRLF here intentionally
        expectedException.expect(DecoderException.class);
        expectedException.expectCause(instanceOf(IllegalStateException.class));
        try {
            assertTrue(channel.writeInbound(wrappedBuffer(afterContentBytes)));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void chunkedWithTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = ("GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "User-Agent: unit-test" + "\r\n" +
                "Transfer-Encoding: chunked" + "\r\n" + "\r\n" +
                toHexString(content.length) + ";\r\n").getBytes(US_ASCII);
        byte[] afterContentBytes = ("\r\n0\r\n" +
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
        beforeContentBytes.add("GET /some/pa".getBytes(US_ASCII));
        beforeContentBytes.add("th?foo=bar&baz=yy".getBytes(US_ASCII));
        beforeContentBytes.add(("y HTTP/1.1" + "\r").getBytes(US_ASCII));
        beforeContentBytes.add(("\n" + "C").getBytes(US_ASCII));
        beforeContentBytes.add("onnection".getBytes(US_ASCII));
        beforeContentBytes.add(":".getBytes(US_ASCII));
        beforeContentBytes.add((" keep-alive" + "\r").getBytes(US_ASCII));
        beforeContentBytes.add("\n".getBytes(US_ASCII));
        beforeContentBytes.add("\n".getBytes(US_ASCII));
        beforeContentBytes.add("User-Agent: ".getBytes(US_ASCII));
        beforeContentBytes.add(" unit-test".getBytes(US_ASCII));
        beforeContentBytes.add("\r\n".getBytes(US_ASCII));
        beforeContentBytes.add("Transfer-Encoding: chunked\r\n".getBytes(US_ASCII));
        beforeContentBytes.add("\r".getBytes(US_ASCII));
        beforeContentBytes.add("\n".getBytes(US_ASCII));
        beforeContentBytes.add(toHexString(content.length).getBytes(US_ASCII));
        beforeContentBytes.add(";".getBytes(US_ASCII));
        beforeContentBytes.add("\r".getBytes(US_ASCII));
        beforeContentBytes.add("\n".getBytes(US_ASCII));
        List<byte[]> afterContentBytes = new ArrayList<>();
        afterContentBytes.add("\r".getBytes(US_ASCII));
        afterContentBytes.add("\n".getBytes(US_ASCII));
        afterContentBytes.add("0".getBytes(US_ASCII));
        afterContentBytes.add("\r".getBytes(US_ASCII));
        afterContentBytes.add("\n".getBytes(US_ASCII));
        afterContentBytes.add("TrailerStatus".getBytes(US_ASCII));
        afterContentBytes.add(": good".getBytes(US_ASCII));
        afterContentBytes.add("\r".getBytes(US_ASCII));
        afterContentBytes.add("\n".getBytes(US_ASCII));
        afterContentBytes.add("\r".getBytes(US_ASCII));
        afterContentBytes.add("\n".getBytes(US_ASCII));
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
        byte[] beforeContentBytes = ("GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "User-Agent: unit-test" + "\r\n" +
                "Transfer-Encoding: chunked" + "\r\n" + "\r\n").getBytes(US_ASCII);
        byte[] afterContentBytes = "0\r\n\r\n".getBytes(US_ASCII);
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
        assertTrue(channel.writeInbound(wrappedBuffer(afterContentBytes)));

        HttpRequestMetaData request = channel.readInbound();
        assertStandardHeaders(request.headers());
        HttpHeaders lastChunk = channel.readInbound();
        assertTrue(lastChunk.isEmpty());
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void variableNoTrailersNoContent() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] beforeContentBytes = ("GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "User-Agent: unit-test" + "\r\n" + "\r\n").getBytes(US_ASCII);
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));

        HttpRequestMetaData request = channel.readInbound();
        assertStandardHeaders(request.headers());
        HttpHeaders lastChunk = channel.readInbound();
        assertTrue(lastChunk.isEmpty());
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void variableNoTrailersWithInvalidContent() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[] {'h', 'e', 'l', 'l', 'o', '\r', '\n'};
        byte[] beforeContentBytes = ("GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "User-Agent: unit-test" + "\r\n" + "\r\n").getBytes(US_ASCII);
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
        expectedException.expect(instanceOf(DecoderException.class));
        expectedException.expectCause(instanceOf(IllegalArgumentException.class));
        channel.writeInbound(wrappedBuffer(content));
    }

    @Test(expected = DecoderException.class)
    public void variableWithTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = ("GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "User-Agent: unit-test" + "\r\n" + "\r\n").getBytes(US_ASCII);
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
    public void contentLengthWithTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = ("GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" +
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
        byte[] beforeContentBytes = ("GET /some/path?foo=bar&baz=yyy HTTP/1.z" + "\r\n").getBytes(US_ASCII);
        try {
            assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static EmbeddedChannel newEmbeddedChannel() {
        HttpRequestDecoder decoder = new HttpRequestDecoder(new ArrayDeque<>(),
                DefaultHttpHeadersFactory.INSTANCE, 8192, 8192);
        decoder.setDiscardAfterReads(1);
        return new EmbeddedChannel(decoder);
    }

    private static void validateHttpRequest(EmbeddedChannel channel, int expectedContentLength) {
        validateHttpRequest(channel, expectedContentLength, false);
    }

    private static void validateHttpRequest(EmbeddedChannel channel, int expectedContentLength,
                                            boolean containsTrailers) {
        HttpRequestMetaData request = channel.readInbound();
        assertEquals("/some/path?foo=bar&baz=yyy", request.requestTarget());
        assertEquals(GET, request.method());
        assertEquals(HTTP_1_1, request.version());
        assertStandardHeaders(request.headers());
        if (expectedContentLength >= 0) {
            assertSingleHeaderValue(request.headers(), CONTENT_LENGTH, String.valueOf(expectedContentLength));
            Buffer chunk = channel.readInbound();
            assertEquals(expectedContentLength, chunk.readableBytes());
            HttpHeaders trailers = channel.readInbound();
            assertTrue(trailers.isEmpty());
        } else {
            Buffer actual = DEFAULT_ALLOCATOR.newBuffer(-expectedContentLength);
            Object chunk;
            for (;;) {
                chunk = channel.readInbound();
                if (chunk instanceof Buffer) {
                    actual.writeBytes(((Buffer) chunk));
                } else {
                    break;
                }
            }
            assertEquals(-expectedContentLength, actual.readableBytes());
            HttpHeaders lastChunk = (HttpHeaders) chunk;
            if (containsTrailers) {
                assertSingleHeaderValue(lastChunk, "TrailerStatus", "good");
            } else {
                assertTrue(lastChunk.isEmpty());
            }
        }
    }

    private static void assertStandardHeaders(HttpHeaders headers) {
        assertSingleHeaderValue(headers, "connecTion", KEEP_ALIVE);
        assertSingleHeaderValue(headers, USER_AGENT, "unit-test");
    }

    static void assertSingleHeaderValue(HttpHeaders headers, CharSequence name, CharSequence expectedValue) {
        Iterator<? extends CharSequence> itr = headers.valuesIterator(name);
        assertTrue("unabled to find header name '" + name + "'", itr.hasNext());
        CharSequence value = itr.next();
        assertTrue(name + " expected value of '" + expectedValue + "' but got: '" + value + "'",
                contentEquals(expectedValue, value));
        assertFalse(itr.hasNext());
    }
}
