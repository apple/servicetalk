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
import io.servicetalk.http.api.HttpResponseMetaData;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpHeaderNames.CONNECTION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.KEEP_ALIVE;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.netty.HttpRequestDecoderTest.assertSingleHeaderValue;
import static java.lang.Integer.toHexString;
import static java.lang.System.arraycopy;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpResponseDecoderTest {
    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    @Test
    public void noReasonPhrase() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = ("HTTP/1.1 200 " + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "Server: unit-test" + "\r\n" +
                "Content-Length: " + content.length + "\r\n" + "\r\n").getBytes(US_ASCII);
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
        assertTrue(channel.writeInbound(wrappedBuffer(content)));

        validateHttpResponse(channel, content.length);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void contentLengthNoTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = ("HTTP/1.1 200 OK" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "Server: unit-test" + "\r\n" +
                "Content-Length: " + content.length + "\r\n" + "\r\n").getBytes(US_ASCII);
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
        assertTrue(channel.writeInbound(wrappedBuffer(content)));

        validateHttpResponse(channel, content.length);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void contentLengthNoTrailersHeaderWhiteSpace() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = ("HTTP/1.1 200 OK" + "\r\n" +
                " Connection :  keep-alive " + "\r\n" +
                "  Server  :        unit-test        " + "\r\n" +
                "   Content-Length  : " + content.length + "\r\n" + "\r\n").getBytes(US_ASCII);
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
        assertTrue(channel.writeInbound(wrappedBuffer(content)));

        validateHttpResponse(channel, content.length);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void chunkedNoTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = ("HTTP/1.1 200 OK" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "Server: unit-test" + "\r\n" +
                "Transfer-Encoding: chunked" + "\r\n" + "\r\n" +
                toHexString(content.length) + ";\r\n").getBytes(US_ASCII);
        byte[] afterContentBytes = "\r\n0\r\n\r\n".getBytes(US_ASCII);
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
        assertTrue(channel.writeInbound(wrappedBuffer(content)));
        assertTrue(channel.writeInbound(wrappedBuffer(afterContentBytes)));
        validateHttpResponse(channel, -content.length);
        channel.finishAndReleaseAll();
    }

    @Test
    public void chunkedWithTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = ("HTTP/1.1 200 OK" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "Server: unit-test" + "\r\n" +
                "Transfer-Encoding: chunked" + "\r\n" + "\r\n" +
                toHexString(content.length) + ";\r\n").getBytes(US_ASCII);
        byte[] afterContentBytes = ("\r\n0\r\n" +
                "TrailerStatus: good" + "\r\n" + "\r\n").getBytes(US_ASCII);
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
        assertTrue(channel.writeInbound(wrappedBuffer(content)));
        assertTrue(channel.writeInbound(wrappedBuffer(afterContentBytes)));
        validateHttpResponse(channel, -content.length, true);
        channel.finishAndReleaseAll();
    }

    @Test
    public void chunkedNoTrailersMultipleLargeContent() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[4096];
        final int numChunks = 5;
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = ("HTTP/1.1 200 OK" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "Server: unit-test" + "\r\n" +
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
        validateHttpResponse(channel, -(content.length * numChunks));
        channel.finishAndReleaseAll();
    }

    @Test
    public void chunkedNoTrailersMultipleLargeContentNoChunkCRLF() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = ("HTTP/1.1 200 OK" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "Server: unit-test" + "\r\n" +
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
    public void chunkedWithTrailersSplitOnNetwork() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        List<byte[]> beforeContentBytes = new ArrayList<>();
        beforeContentBytes.add("HTTP/".getBytes(US_ASCII));
        beforeContentBytes.add("1.1 ".getBytes(US_ASCII));
        beforeContentBytes.add(("200 OK" + "\r").getBytes(US_ASCII));
        beforeContentBytes.add(("\n" + "C").getBytes(US_ASCII));
        beforeContentBytes.add("onnection".getBytes(US_ASCII));
        beforeContentBytes.add(":".getBytes(US_ASCII));
        beforeContentBytes.add((" keep-alive" + "\r").getBytes(US_ASCII));
        beforeContentBytes.add("\n".getBytes(US_ASCII));
        beforeContentBytes.add("\n".getBytes(US_ASCII));
        beforeContentBytes.add("Server: ".getBytes(US_ASCII));
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
        validateHttpResponse(channel, -content.length, true);
        channel.finishAndReleaseAll();
    }

    @Test
    public void chunkedNoTrailersNoContent() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = ("HTTP/1.1 200 OK" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "Server: unit-test" + "\r\n" +
                "Transfer-Encoding: chunked" + "\r\n" + "\r\n").getBytes(US_ASCII);
        byte[] afterContentBytes = "0\r\n\r\n".getBytes(US_ASCII);
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
        assertTrue(channel.writeInbound(wrappedBuffer(afterContentBytes)));

        HttpResponseMetaData response = channel.readInbound();
        assertStandardHeaders(response.headers());
        HttpHeaders lastChunk = channel.readInbound();
        assertTrue(lastChunk.isEmpty());
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void variableNoTrailersNoContent() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] beforeContentBytes = ("HTTP/1.1 200 OK" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "Server: unit-test" + "\r\n" + "\r\n").getBytes(US_ASCII);
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));

        // For a response, the variable length content is considered "complete" when the channel is closed.
        channel.close();

        HttpResponseMetaData response = channel.readInbound();
        assertStandardHeaders(response.headers());
        HttpHeaders lastChunk = channel.readInbound();
        assertTrue(lastChunk.isEmpty());
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void variableWithTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = ("HTTP/1.1 200 OK" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "Server: unit-test" + "\r\n" + "\r\n").getBytes(US_ASCII);
        // Note that trailers are only allowed when chunked encoding is used. So the trailers in this case are
        // considered part of the payload (even the \r\n), and the response is terminated when the channel is closed.
        // https://tools.ietf.org/html/rfc7230.html#section-4.1
        byte[] afterContentBytes = "TrailerStatus: good\r\n".getBytes(US_ASCII);
        byte[] uberContent = new byte[content.length + afterContentBytes.length];
        arraycopy(content, 0, uberContent, 0, content.length);
        arraycopy(afterContentBytes, 0, uberContent, content.length, afterContentBytes.length);
        assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
        assertTrue(channel.writeInbound(wrappedBuffer(content)));
        assertTrue(channel.writeInbound(wrappedBuffer(afterContentBytes)));
        channel.close();
        validateHttpResponse(channel, -uberContent.length);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test(expected = DecoderException.class)
    public void contentLengthWithTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        byte[] beforeContentBytes = ("HTTP/1.1 200 OK" + "\r\n" +
                " Connection :  keep-alive " + "\r\n" +
                "  Server  :        unit-test        " + "\r\n" +
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
        byte[] beforeContentBytes = ("HTTP/1.z 200 OK" + "\r\n").getBytes(US_ASCII);
        try {
            assertTrue(channel.writeInbound(wrappedBuffer(beforeContentBytes)));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static void validateHttpResponse(EmbeddedChannel channel, int expectedContentLength) {
        validateHttpResponse(channel, expectedContentLength, false);
    }

    private static void validateHttpResponse(EmbeddedChannel channel, int expectedContentLength,
                                             boolean containsTrailers) {
        HttpResponseMetaData response = channel.readInbound();
        assertEquals(OK, response.status());
        assertEquals(HTTP_1_1, response.version());
        assertStandardHeaders(response.headers());
        if (expectedContentLength >= 0) {
            assertSingleHeaderValue(response.headers(), CONTENT_LENGTH, String.valueOf(expectedContentLength));
            Buffer chunk = channel.readInbound();
            assertEquals(expectedContentLength, chunk.getReadableBytes());
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
            assertEquals(-expectedContentLength, actual.getReadableBytes());
            HttpHeaders lastChunk = (HttpHeaders) chunk;
            if (containsTrailers) {
                assertSingleHeaderValue(lastChunk, "TrailerStatus", "good");
            } else {
                assertTrue(lastChunk.isEmpty());
            }
        }
    }

    private static void assertStandardHeaders(HttpHeaders headers) {
        assertSingleHeaderValue(headers, CONNECTION, KEEP_ALIVE);
        assertSingleHeaderValue(headers, "seRver", "unit-test");
    }

    private static EmbeddedChannel newEmbeddedChannel() {
        HttpResponseDecoder decoder = new HttpResponseDecoder(new ArrayDeque<>(), DefaultHttpHeadersFactory.INSTANCE,
                8192, 8192);
        decoder.setDiscardAfterReads(1);
        return new EmbeddedChannel(decoder);
    }
}
