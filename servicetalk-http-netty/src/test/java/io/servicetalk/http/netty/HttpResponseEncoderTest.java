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

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import java.util.ArrayDeque;
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
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseMetaDataFactory.newResponseMetaData;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
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
        HttpResponseMetaData response = newResponseMetaData(HTTP_1_1, OK, INSTANCE.newHeaders());
        response.headers()
                .add(CONNECTION, KEEP_ALIVE)
                .add(SERVER, "unit-test")
                .add(CONTENT_LENGTH, valueOf(content.length));
        channel.writeOutbound(response);
        channel.writeOutbound(buffer.duplicate());
        channel.writeOutbound(EmptyHttpHeaders.INSTANCE);
        verifyHttpResponse(channel, buffer, TransferEncoding.ContentLength, false);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test(expected = IllegalArgumentException.class)
    public void contentLengthNoTrailersHeaderWhiteSpaceThrowByDefault() {
        EmbeddedChannel channel = newEmbeddedChannel();
        HttpResponseMetaData response = newResponseMetaData(HTTP_1_1, OK, INSTANCE.newHeaders());
        try {
            response.addHeader(" " + CONNECTION, KEEP_ALIVE);
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

        HttpResponseMetaData response = newResponseMetaData(HTTP_1_1, OK,
                new DefaultHttpHeadersFactory(false, false).newHeaders());
        response.headers()
                .add(" " + CONNECTION + " ", " " + KEEP_ALIVE)
                .add("  " + SERVER + "   ", "    unit-test   ")
                .add(CONTENT_LENGTH, valueOf(content.length));
        channel.writeOutbound(response);
        channel.writeOutbound(buffer.duplicate());
        channel.writeOutbound(EmptyHttpHeaders.INSTANCE);

        ByteBuf byteBuf = channel.readOutbound();
        String actualMetaData = byteBuf.toString(US_ASCII);
        byteBuf.release();
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.contains("HTTP/1.1 200 OK" + "\r\n"));
        assertTrue("unexpected metadata: " + actualMetaData,
                actualMetaData.contains(" " + CONNECTION + " :  " + KEEP_ALIVE + "\r\n"));
        assertTrue("unexpected metadata: " + actualMetaData,
                actualMetaData.contains("  " + SERVER + "   :     unit-test   " + "\r\n"));
        assertTrue("unexpected metadata: " + actualMetaData,
                actualMetaData.contains(CONTENT_LENGTH + ": " + valueOf(buffer.readableBytes()) + "\r\n"));
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.endsWith("\r\n" + "\r\n"));
        byteBuf = channel.readOutbound();
        assertEquals(buffer.toNioBuffer(), byteBuf.nioBuffer());
        byteBuf.release();
        consumeEmptyBufferFromTrailers(channel);

        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void chunkedNoTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = DEFAULT_ALLOCATOR.wrap(content);
        HttpResponseMetaData response = newResponseMetaData(HTTP_1_1, OK, INSTANCE.newHeaders());
        response.headers()
                .add(CONNECTION, KEEP_ALIVE)
                .add(SERVER, "unit-test")
                .add(TRANSFER_ENCODING, CHUNKED);
        channel.writeOutbound(response);
        channel.writeOutbound(buffer.duplicate());
        channel.writeOutbound(EmptyHttpHeaders.INSTANCE);
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
        HttpResponseMetaData response = newResponseMetaData(HTTP_1_1, OK, INSTANCE.newHeaders());
        response.headers()
                .add(CONNECTION, KEEP_ALIVE)
                .add(SERVER, "unit-test")
                .add(TRANSFER_ENCODING, CHUNKED);
        channel.writeOutbound(response);
        channel.writeOutbound(buffer.duplicate());
        channel.writeOutbound(trailers);
        verifyHttpResponse(channel, buffer, TransferEncoding.Chunked, true);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void chunkedNoTrailersNoContent() {
        EmbeddedChannel channel = newEmbeddedChannel();
        HttpResponseMetaData response = newResponseMetaData(HTTP_1_1, OK, INSTANCE.newHeaders());
        response.headers()
                .add(CONNECTION, KEEP_ALIVE)
                .add(SERVER, "unit-test")
                .add(TRANSFER_ENCODING, CHUNKED);
        channel.writeOutbound(response);
        channel.writeOutbound(EMPTY_BUFFER.duplicate());
        channel.writeOutbound(EmptyHttpHeaders.INSTANCE);
        verifyHttpResponse(channel, EMPTY_BUFFER, TransferEncoding.Chunked, false);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void variableNoTrailersNoContent() {
        EmbeddedChannel channel = newEmbeddedChannel();
        HttpResponseMetaData response = newResponseMetaData(HTTP_1_1, OK, INSTANCE.newHeaders());
        response.headers()
                .add(CONNECTION, KEEP_ALIVE)
                .add(SERVER, "unit-test");
        channel.writeOutbound(response);
        channel.writeOutbound(EMPTY_BUFFER.duplicate());
        channel.writeOutbound(EmptyHttpHeaders.INSTANCE);
        verifyHttpResponse(channel, EMPTY_BUFFER, TransferEncoding.Variable, false);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void variableNoTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = DEFAULT_ALLOCATOR.wrap(content);
        HttpResponseMetaData response = newResponseMetaData(HTTP_1_1, OK, INSTANCE.newHeaders());
        response.headers()
                .add(CONNECTION, KEEP_ALIVE)
                .add(SERVER, "unit-test");
        channel.writeOutbound(response);
        channel.writeOutbound(buffer.duplicate());
        channel.writeOutbound(EmptyHttpHeaders.INSTANCE);
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
        HttpResponseMetaData response = newResponseMetaData(HTTP_1_1, OK, INSTANCE.newHeaders());
        response.headers()
                .add(CONNECTION, KEEP_ALIVE)
                .add(SERVER, "unit-test");
        channel.writeOutbound(response);
        channel.writeOutbound(buffer.duplicate());
        channel.writeOutbound(trailers);
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
        HttpResponseMetaData response = newResponseMetaData(HTTP_1_1, OK, INSTANCE.newHeaders());
        response.headers()
                .add(CONNECTION, KEEP_ALIVE)
                .add(SERVER, "unit-test")
                .add(CONTENT_LENGTH, valueOf(content.length));
        channel.writeOutbound(response);
        channel.writeOutbound(buffer.duplicate());
        channel.writeOutbound(trailers);
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
        assertTrue("unexpected metadata: " + actualMetaData,
                actualMetaData.contains(CONNECTION + ": " + KEEP_ALIVE + "\r\n"));
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.contains(SERVER + ": unit-test" + "\r\n"));
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.endsWith("\r\n" + "\r\n"));
        switch (encoding) {
            case Chunked:
                assertTrue("unexpected metadata: " + actualMetaData,
                        actualMetaData.contains(TRANSFER_ENCODING + ": " + CHUNKED + "\r\n"));
                if (buffer.readableBytes() != 0) {
                    byteBuf = channel.readOutbound();
                    assertEquals(toHexString(buffer.readableBytes()) + "\r\n", byteBuf.toString(US_ASCII));
                    byteBuf.release();

                    byteBuf = channel.readOutbound();
                    assertEquals(buffer.toNioBuffer(), byteBuf.nioBuffer());
                    byteBuf.release();

                    byteBuf = channel.readOutbound();
                    assertEquals("\r\n", byteBuf.toString(US_ASCII));
                    byteBuf.release();
                } else {
                    byteBuf = channel.readOutbound();
                    assertFalse(byteBuf.isReadable());
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
                assertTrue("unexpected metadata: " + actualMetaData,
                        actualMetaData.contains(CONTENT_LENGTH + ": " + valueOf(buffer.readableBytes()) + "\r\n"));
                byteBuf = channel.readOutbound();
                assertEquals(buffer.toNioBuffer(), byteBuf.nioBuffer());
                consumeEmptyBufferFromTrailers(channel);
                break;
            case Variable:
                byteBuf = channel.readOutbound();
                assertEquals(buffer.toNioBuffer(), byteBuf.nioBuffer());
                byteBuf.release();
                consumeEmptyBufferFromTrailers(channel);
                break;
            default:
                throw new Error();
        }
    }

    private static void consumeEmptyBufferFromTrailers(EmbeddedChannel channel) {
        // Empty buffer is written when trailers are seen to indicate the end of the request
        ByteBuf byteBuf = channel.readOutbound();
        assertFalse(byteBuf.isReadable());
        byteBuf.release();
    }

    private static EmbeddedChannel newEmbeddedChannel() {
        return new EmbeddedChannel(new HttpResponseEncoder(new ArrayDeque<>(), 256, 256));
    }
}
