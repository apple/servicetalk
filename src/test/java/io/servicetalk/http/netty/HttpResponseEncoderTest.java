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
import io.servicetalk.http.api.EmptyHttpHeaders;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.LastHttpPayloadChunk;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import java.util.concurrent.ThreadLocalRandom;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpHeaderNames.CONNECTION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.SERVER;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpHeaderValues.KEEP_ALIVE;
import static io.servicetalk.http.api.HttpPayloadChunks.newLastPayloadChunk;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseMetaDataFactory.newResponseMetaData;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static java.lang.Integer.toHexString;
import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpResponseEncoderTest {
    private enum TransferEncoding {
        ContentLength,
        Chunked,
        Variable
    }

    @Test
    public void contentLengthNoTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = DEFAULT_ALLOCATOR.wrap(content);
        LastHttpPayloadChunk lastChunk = newLastPayloadChunk(buffer, EmptyHttpHeaders.INSTANCE);
        HttpResponseMetaData response = newResponseMetaData(HTTP_1_1, OK, INSTANCE.newHeaders());
        response.getHeaders()
                .add(CONNECTION, KEEP_ALIVE)
                .add(SERVER, "unit-test")
                .add(CONTENT_LENGTH, valueOf(content.length));
        channel.writeOutbound(response);
        channel.writeOutbound(lastChunk.duplicate());
        verifyHttpResponse(channel, buffer, TransferEncoding.ContentLength, false);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test(expected = IllegalArgumentException.class)
    public void contentLengthNoTrailersHeaderWhiteSpaceThrowByDefault() {
        EmbeddedChannel channel = newEmbeddedChannel();
        HttpResponseMetaData response = newResponseMetaData(HTTP_1_1, OK, INSTANCE.newHeaders());
        try {
            response.getHeaders().add(" " + CONNECTION, KEEP_ALIVE);
        } finally {
            assertFalse(channel.finishAndReleaseAll());
        }
    }

    @Test
    public void contentLengthNoTrailersHeaderWhiteSpaceEncodedWithValidationOff() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = DEFAULT_ALLOCATOR.wrap(content);

        LastHttpPayloadChunk lastChunk = newLastPayloadChunk(buffer, EmptyHttpHeaders.INSTANCE);
        HttpResponseMetaData response = newResponseMetaData(HTTP_1_1, OK,
                new DefaultHttpHeadersFactory(false).newHeaders());
        response.getHeaders()
                .add(" " + CONNECTION + " ", " " + KEEP_ALIVE)
                .add("  " + SERVER + "   ", "    unit-test   ")
                .add(CONTENT_LENGTH, valueOf(content.length));
        channel.writeOutbound(response);
        channel.writeOutbound(lastChunk.duplicate());

        ByteBuf byteBuf = channel.readOutbound();
        String actualMetaData = byteBuf.toString(US_ASCII);
        byteBuf.release();
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.contains("HTTP/1.1 200 OK" + "\r\n"));
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.contains(" " + CONNECTION + " :  " + KEEP_ALIVE + "\r\n"));
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.contains("  " + SERVER + "   :     unit-test   " + "\r\n"));
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.contains(CONTENT_LENGTH + ": " + valueOf(buffer.getReadableBytes()) + "\r\n"));
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.endsWith("\r\n" + "\r\n"));
        byteBuf = channel.readOutbound();
        assertEquals(buffer.toNioBuffer(), byteBuf.nioBuffer());
        byteBuf.release();

        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void chunkedNoTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = DEFAULT_ALLOCATOR.wrap(content);
        LastHttpPayloadChunk lastChunk = newLastPayloadChunk(buffer, EmptyHttpHeaders.INSTANCE);
        HttpResponseMetaData response = newResponseMetaData(HTTP_1_1, OK, INSTANCE.newHeaders());
        response.getHeaders()
                .add(CONNECTION, KEEP_ALIVE)
                .add(SERVER, "unit-test")
                .add(TRANSFER_ENCODING, CHUNKED);
        channel.writeOutbound(response);
        channel.writeOutbound(lastChunk.duplicate());
        verifyHttpResponse(channel, buffer, TransferEncoding.Chunked, false);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void chunkedWithTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = DEFAULT_ALLOCATOR.wrap(content);
        HttpHeaders trailers = DefaultHttpHeadersFactory.INSTANCE.newTrailers();
        trailers.add("TrailerStatus", "good");
        LastHttpPayloadChunk lastChunk = newLastPayloadChunk(buffer, trailers);
        HttpResponseMetaData response = newResponseMetaData(HTTP_1_1, OK, INSTANCE.newHeaders());
        response.getHeaders()
                .add(CONNECTION, KEEP_ALIVE)
                .add(SERVER, "unit-test")
                .add(TRANSFER_ENCODING, CHUNKED);
        channel.writeOutbound(response);
        channel.writeOutbound(lastChunk.duplicate());
        verifyHttpResponse(channel, buffer, TransferEncoding.Chunked, true);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void chunkedNoTrailersNoContent() {
        EmbeddedChannel channel = newEmbeddedChannel();
        LastHttpPayloadChunk lastChunk = newLastPayloadChunk(EMPTY_BUFFER, EmptyHttpHeaders.INSTANCE);
        HttpResponseMetaData response = newResponseMetaData(HTTP_1_1, OK, INSTANCE.newHeaders());
        response.getHeaders()
                .add(CONNECTION, KEEP_ALIVE)
                .add(SERVER, "unit-test")
                .add(TRANSFER_ENCODING, CHUNKED);
        channel.writeOutbound(response);
        channel.writeOutbound(lastChunk.duplicate());
        verifyHttpResponse(channel, EMPTY_BUFFER, TransferEncoding.Chunked, false);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void variableNoTrailersNoContent() {
        EmbeddedChannel channel = newEmbeddedChannel();
        LastHttpPayloadChunk lastChunk = newLastPayloadChunk(EMPTY_BUFFER, EmptyHttpHeaders.INSTANCE);
        HttpResponseMetaData response = newResponseMetaData(HTTP_1_1, OK, INSTANCE.newHeaders());
        response.getHeaders()
                .add(CONNECTION, KEEP_ALIVE)
                .add(SERVER, "unit-test");
        channel.writeOutbound(response);
        channel.writeOutbound(lastChunk.duplicate());
        verifyHttpResponse(channel, EMPTY_BUFFER, TransferEncoding.Variable, false);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void variableNoTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = DEFAULT_ALLOCATOR.wrap(content);
        LastHttpPayloadChunk lastChunk = newLastPayloadChunk(buffer, EmptyHttpHeaders.INSTANCE);
        HttpResponseMetaData response = newResponseMetaData(HTTP_1_1, OK, INSTANCE.newHeaders());
        response.getHeaders()
                .add(CONNECTION, KEEP_ALIVE)
                .add(SERVER, "unit-test");
        channel.writeOutbound(response);
        channel.writeOutbound(lastChunk.duplicate());
        verifyHttpResponse(channel, buffer, TransferEncoding.Variable, false);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void variableWithTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = DEFAULT_ALLOCATOR.wrap(content);
        HttpHeaders trailers = DefaultHttpHeadersFactory.INSTANCE.newTrailers();
        trailers.add("TrailerStatus", "good");
        LastHttpPayloadChunk lastChunk = newLastPayloadChunk(buffer, trailers);
        HttpResponseMetaData response = newResponseMetaData(HTTP_1_1, OK, INSTANCE.newHeaders());
        response.getHeaders()
                .add(CONNECTION, KEEP_ALIVE)
                .add(SERVER, "unit-test");
        channel.writeOutbound(response);
        channel.writeOutbound(lastChunk.duplicate());
        verifyHttpResponse(channel, buffer, TransferEncoding.Variable, false);

        // The trailers will just not be encoded if the transfer encoding is not set correctly.
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void contentLengthWithTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = DEFAULT_ALLOCATOR.wrap(content);
        HttpHeaders trailers = DefaultHttpHeadersFactory.INSTANCE.newTrailers();
        trailers.add("TrailerStatus", "good");
        LastHttpPayloadChunk lastChunk = newLastPayloadChunk(buffer, trailers);
        HttpResponseMetaData response = newResponseMetaData(HTTP_1_1, OK, INSTANCE.newHeaders());
        response.getHeaders()
                .add(CONNECTION, KEEP_ALIVE)
                .add(SERVER, "unit-test")
                .add(CONTENT_LENGTH, valueOf(content.length));
        channel.writeOutbound(response);
        channel.writeOutbound(lastChunk.duplicate());
        verifyHttpResponse(channel, buffer, TransferEncoding.ContentLength, false);

        // The trailers will just not be encoded if the transfer encoding is not set correctly.
        assertFalse(channel.finishAndReleaseAll());
    }

    private static void verifyHttpResponse(EmbeddedChannel channel, Buffer buffer, TransferEncoding encoding,
                                          boolean trailers) {
        ByteBuf byteBuf = channel.readOutbound();
        String actualMetaData = byteBuf.toString(US_ASCII);
        byteBuf.release();
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.contains("HTTP/1.1 200 OK" + "\r\n"));
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.contains(CONNECTION + ": " + KEEP_ALIVE + "\r\n"));
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.contains(SERVER + ": unit-test" + "\r\n"));
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.endsWith("\r\n" + "\r\n"));
        switch (encoding) {
            case Chunked:
                assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.contains(TRANSFER_ENCODING + ": " + CHUNKED + "\r\n"));
                if (buffer.getReadableBytes() != 0) {
                    byteBuf = channel.readOutbound();
                    assertEquals(toHexString(buffer.getReadableBytes()) + "\r\n", byteBuf.toString(US_ASCII));
                    byteBuf.release();

                    byteBuf = channel.readOutbound();
                    assertEquals(buffer.toNioBuffer(), byteBuf.nioBuffer());
                    byteBuf.release();

                    byteBuf = channel.readOutbound();
                    assertEquals("\r\n", byteBuf.toString(US_ASCII));
                    byteBuf.release();
                }

                if (trailers) {
                    byteBuf = channel.readOutbound();
                    assertEquals("0\r\nTrailerStatus: good\r\n\r\n", byteBuf.toString(US_ASCII));
                    byteBuf.release();
                } else {
                    byteBuf = channel.readOutbound();
                    assertEquals("0\r\n\r\n", byteBuf.toString(US_ASCII));
                    byteBuf.release();
                }
                break;
            case ContentLength:
                assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.contains(CONTENT_LENGTH + ": " + valueOf(buffer.getReadableBytes()) + "\r\n"));
                byteBuf = channel.readOutbound();
                assertEquals(buffer.toNioBuffer(), byteBuf.nioBuffer());
                break;
            case Variable:
                byteBuf = channel.readOutbound();
                assertEquals(buffer.toNioBuffer(), byteBuf.nioBuffer());
                byteBuf.release();
                break;
            default:
                throw new Error();
        }
    }

    private static EmbeddedChannel newEmbeddedChannel() {
        return new EmbeddedChannel(new HttpResponseEncoder(256, 256));
    }
}
