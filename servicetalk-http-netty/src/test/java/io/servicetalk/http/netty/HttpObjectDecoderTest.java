/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.utils.internal.IllegalCharacterException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static io.netty.buffer.ByteBufUtil.writeAscii;
import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.util.AsciiString.contentEquals;
import static io.servicetalk.http.api.HttpHeaderNames.ACCEPT_ENCODING;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpHeaderValues.KEEP_ALIVE;
import static java.lang.Integer.toHexString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

abstract class HttpObjectDecoderTest {

    @After
    public void tearDown() throws Exception {
        try {
            if (channel().isOpen()) {
                channel().close().get();
            }
        } finally {
            channel().releaseInbound();
            channel().releaseOutbound();
        }
    }

    abstract EmbeddedChannel channel();

    abstract String startLine();

    abstract HttpMetaData assertStartLine();

    abstract String startLineForContent();

    abstract HttpMetaData assertStartLineForContent();

    final void writeMsg(String msg) {
        assertThat("writeInbound(msg) did not produce something for readInbound()",
                channel().writeInbound(fromAscii(msg)), is(true));
    }

    final void writeContent(int length) {
        assertThat("writeInbound(content) did not produce something for readInbound()",
                channel().writeInbound(content(length)), is(true));
    }

    final void writeChunkSize(int length) {
        writeMsg(toHexString(length) + "\r\n");
    }

    final void writeChunk(int length) {
        writeChunkSize(length);
        writeContent(length);
        writeMsg("\r\n");
    }

    final void writeLastChunk() {
        writeMsg("0\r\n\r\n");
    }

    final void assertDecoderException(String msg, String expectedExceptionMsg) {
        DecoderException e = assertThrows(DecoderException.class, () -> writeMsg(msg));
        assertThat(e.getMessage(), startsWith(expectedExceptionMsg));
        assertThat(channel().inboundMessages(), is(empty()));
    }

    final void assertDecoderExceptionWithCause(String msg, String expectedExceptionMsg) {
        DecoderException e = assertThrows(DecoderException.class, () -> writeMsg(msg));
        assertThat(e.getMessage(), startsWith(expectedExceptionMsg));
        assertThat(e.getCause(), is(instanceOf(IllegalCharacterException.class)));
        assertThat(e.getCause().getMessage(), not(isEmptyString()));
        assertThat(channel().inboundMessages(), is(empty()));
    }

    final HttpMetaData validateWithContent(int expectedContentLength, boolean containsTrailers) {
        HttpMetaData metaData = assertStartLineForContent();
        assertStandardHeaders(metaData.headers());
        if (expectedContentLength > 0) {
            assertSingleHeaderValue(metaData.headers(), CONTENT_LENGTH, String.valueOf(expectedContentLength));
            HttpHeaders trailers = assertPayloadSize(expectedContentLength);
            assertThat("Trailers are not empty", trailers.isEmpty(), is(true));
        } else if (expectedContentLength == 0) {
            if (containsTrailers) {
                assertSingleHeaderValue(metaData.headers(), TRANSFER_ENCODING, CHUNKED);
                HttpHeaders trailers = channel().readInbound();
                assertSingleHeaderValue(trailers, "TrailerStatus", "good");
            } else {
                assertSingleHeaderValue(metaData.headers(), CONTENT_LENGTH, "0");
                assertEmptyTrailers(channel());
            }
        } else {
            HttpHeaders trailers = assertPayloadSize(-expectedContentLength);
            if (containsTrailers) {
                assertSingleHeaderValue(trailers, "TrailerStatus", "good");
            } else {
                assertThat("Trailers are not empty", trailers.isEmpty(), is(true));
            }
        }
        assertFalse(channel().finishAndReleaseAll());
        return metaData;
    }

    final HttpHeaders assertPayloadSize(int expectedPayloadSize) {
        int actualPayloadSize = 0;
        Object item;
        for (;;) {
            item = channel().readInbound();
            if (item instanceof Buffer) {
                actualPayloadSize += ((Buffer) item).readableBytes();
            } else {
                assertThat(actualPayloadSize, is(expectedPayloadSize));
                assertThat(item, instanceOf(HttpHeaders.class));
                return (HttpHeaders) item;
            }
        }
    }

    static void assertEmptyTrailers(EmbeddedChannel channel) {
        HttpHeaders trailers = channel.readInbound();
        assertThat("Trailers are not empty", trailers.isEmpty(), is(true));
    }

    static void assertSingleHeaderValue(HttpHeaders headers, CharSequence name, CharSequence expectedValue) {
        Iterator<? extends CharSequence> itr = headers.valuesIterator(name);
        assertTrue("Unable to find header name '" + name + "'", itr.hasNext());
        CharSequence value = itr.next();
        assertTrue(name + " expected value of '" + expectedValue + "' but got: '" + value + "'",
                contentEquals(expectedValue, value));
        assertFalse("Unexpected second value for header name '" + name + "'", itr.hasNext());
    }

    static void assertStandardHeaders(HttpHeaders headers) {
        assertSingleHeaderValue(headers, HOST, "servicetalk.io");
        assertSingleHeaderValue(headers, "connecTion", KEEP_ALIVE); // Make sure header-name is case-insensitive
    }

    static ByteBuf fromAscii(final String msg) {
        return writeAscii(UnpooledByteBufAllocator.DEFAULT, msg);
    }

    private static ByteBuf content(int contentLength) {
        byte[] content = new byte[contentLength];
        ThreadLocalRandom.current().nextBytes(content);
        return wrappedBuffer(content);
    }

    @Test
    public void startLineWithoutCR() {
        assertDecoderException(startLine() + '\n', "Found LF (0x0a) but no CR (0x0d) before");
    }

    @Test
    public void validStartLine() {
        writeMsg(startLine() + "\r\n" + "\r\n");
        assertStartLine();
        assertEmptyTrailers(channel());
        assertFalse(channel().finishAndReleaseAll());
    }

    @Test
    public void validStartLineInThreeFrames() {
        assertFalse(channel().writeInbound(fromAscii(startLine())));
        assertFalse(channel().writeInbound(fromAscii("\r\n")));
        assertTrue(channel().writeInbound(fromAscii("\r\n")));
        assertStartLine();
        assertEmptyTrailers(channel());
        assertFalse(channel().finishAndReleaseAll());
    }

    @Test
    public void validStartLineInFourFrames() {
        assertFalse(channel().writeInbound(fromAscii(startLine().substring(0, 3))));
        assertFalse(channel().writeInbound(fromAscii(startLine().substring(3))));
        assertFalse(channel().writeInbound(fromAscii("\r\n")));
        assertTrue(channel().writeInbound(fromAscii("\r\n")));
        assertStartLine();
        assertEmptyTrailers(channel());
        assertFalse(channel().finishAndReleaseAll());
    }

    @Test
    public void validStartLineAfterPrefaceCRLF() {
        writeMsg("\r\n" + startLine() + "\r\n" + "\r\n");
        assertStartLine();
        assertEmptyTrailers(channel());
        assertFalse(channel().finishAndReleaseAll());
    }

    @Test
    public void validStartLineAfterPrefaceCRLFInSeparateFrame() {
        assertFalse(channel().writeInbound(fromAscii("\r\n")));   // write control characters first
        writeMsg(startLine() + "\r\n" + "\r\n");
        assertStartLine();
        assertEmptyTrailers(channel());
        assertFalse(channel().finishAndReleaseAll());
    }

    @Test
    public void tooManyPrefaceCharacters() {
        DecoderException ex = assertThrows(DecoderException.class,
                () -> writeMsg("\r\n\r\n\r\n" + startLine() + "\r\n" + "\r\n"));
        assertThat(ex.getMessage(), startsWith("Too many prefacing CRLF (0x0d0a) characters"));
        assertThat(channel().inboundMessages(), is(empty()));
    }

    @Test
    public void whitespaceNotAllowedBeforeHeaderFieldName() {
        assertDecoderExceptionWithCause(startLine() + "\r\n" +
                " Host: servicetalk.io" + "\r\n" + "\r\n", "Invalid header name");
    }

    @Test
    public void whitespaceNotAllowedBetweenHeaderFieldNameAndColon() {
        assertDecoderExceptionWithCause(startLine() + "\r\n" +
                "Host : servicetalk.io" + "\r\n" + "\r\n", "Invalid header name");
    }

    @Test
    public void controlCharNotAllowedBeforeHeaderFieldValue() {
        assertDecoderExceptionWithCause(startLine() + "\r\n" +
                "Host: \fservicetalk.io" + "\r\n" + "\r\n", "Invalid value for the header");
    }

    @Test
    public void noEndOfHeaderName() {
        assertDecoderException(startLine() + "\r\n" +
                "Host" + "\r\n" + "\r\n", "Unable to find end of a header name");
    }

    @Test
    public void emptyHeaderName() {
        assertDecoderException(startLine() + "\r\n" +
                ": some-value" + "\r\n" + "\r\n", "Empty header name");
    }

    @Test
    public void headerNameWithControlChar() {
        assertDecoderExceptionWithCause(startLine() + "\r\n" +
                "H\0st: servicetalk.io" + "\r\n" + "\r\n", "Invalid header name");
    }

    @Test
    public void headerNameWithObsText() {
        assertDecoderExceptionWithCause(startLine() + "\r\n" +
                "Hóst: servicetalk.io" + "\r\n" + "\r\n", "Invalid header name");
    }

    @Test
    public void headerFiledValueEmpty() {
        testHeaderFiledValue("", "");
        testHeaderFiledValue(" ", "");
        testHeaderFiledValue("   ", "");
    }

    @Test
    public void headerFiledValue() {
        testHeaderFiledValue("servicetalk.io", "servicetalk.io");
        testHeaderFiledValue(" servicetalk.io", "servicetalk.io");
        testHeaderFiledValue("servicetalk.io ", "servicetalk.io");
        testHeaderFiledValue(" servicetalk.io ", "servicetalk.io");
        testHeaderFiledValue("   servicetalk.io", "servicetalk.io");
        testHeaderFiledValue("servicetalk.io   ", "servicetalk.io");
        testHeaderFiledValue("   servicetalk.io   ", "servicetalk.io");
    }

    @Test
    public void headerFiledValueSingleCharacter() {
        testHeaderFiledValue("s", "s");
        testHeaderFiledValue(" s", "s");
        testHeaderFiledValue("s ", "s");
        testHeaderFiledValue(" s ", "s");
        testHeaderFiledValue("   s", "s");
        testHeaderFiledValue("s   ", "s");
        testHeaderFiledValue("   s   ", "s");
    }

    @Test
    public void headerFiledValueCommaSeparated() {
        testHeaderFiledValue("first, second, third", "first, second, third");
    }

    @Test
    public void headerFiledValueAllowsHTab() {
        testHeaderFiledValue("service\talk.io", "service\talk.io");
    }

    @Test
    public void headerFiledValueAllowsObsText() {
        testHeaderFiledValue("sêrvicêtalk.io", "sêrvicêtalk.io");
    }

    private void testHeaderFiledValue(String fieldValue, String expectedFieldValue) {
        writeMsg(startLine() + "\r\n" +
                "Host:" + fieldValue + "\r\n" + "\r\n");
        HttpMetaData metaData = assertStartLine();
        assertSingleHeaderValue(metaData.headers(), HOST, expectedFieldValue);
        assertEmptyTrailers(channel());
    }

    @Test
    public void multipleHeaderFiledValues() {
        writeMsg(startLine() + "\r\n" +
                "Accept-Encoding: gzip" + "\r\n" +
                "Accept-Encoding: compress" + "\r\n" +
                "Accept-Encoding: deflate" + "\r\n" + "\r\n");
        HttpMetaData metaData = assertStartLine();
        List<String> headerValues = new ArrayList<>();
        Iterator<? extends CharSequence> itr = metaData.headers().valuesIterator(ACCEPT_ENCODING);
        while (itr.hasNext()) {
            headerValues.add(itr.next().toString());
        }
        assertThat("Unable to find header name 'Accept-Encoding'", headerValues, hasSize(3));
        assertThat(headerValues, containsInAnyOrder("gzip", "compress", "deflate"));
        assertEmptyTrailers(channel());
        assertFalse(channel().finishAndReleaseAll());
    }

    @Test
    public void zeroContentLength() {
        writeMsg(startLineForContent() + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "Content-Length: 0" + "\r\n" + "\r\n");
        validateWithContent(0, false);
    }

    @Test
    public void contentLengthNoTrailers() {
        int contentLength = 128;
        writeMsg(startLineForContent() + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "Content-Length: " + contentLength + "\r\n" + "\r\n");
        writeContent(contentLength);
        validateWithContent(contentLength, false);
    }

    @Test
    public void chunkedNoTrailersChunkSizeWithoutSemicolon() {
        chunkedNoTrailers(false);
    }

    @Test
    public void chunkedNoTrailersChunkSizeWithSemicolon() {
        chunkedNoTrailers(true);
    }

    private void chunkedNoTrailers(boolean addSemicolon) {
        int chunkSize = 128;
        writeMsg(startLineForContent() + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "Transfer-Encoding: chunked" + "\r\n" + "\r\n");
        writeMsg(toHexString(chunkSize) + (addSemicolon ? ";" : "") + "\r\n");
        writeContent(chunkSize);
        writeMsg("\r\n");
        writeLastChunk();
        validateWithContent(-chunkSize, false);
    }

    @Test
    public void chunkedNoTrailersMultipleLargeContent() {
        int chunkSize = 4096;
        int numChunks = 5;
        writeMsg(startLineForContent() + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "Transfer-Encoding: chunked" + "\r\n" + "\r\n");
        for (int i = 0; i < numChunks; ++i) {
            writeChunk(chunkSize);
        }
        writeLastChunk();
        validateWithContent(-(chunkSize * numChunks), false);
    }

    @Test
    public void chunkedNoTrailersNoChunkSize() {
        writeMsg(startLineForContent() + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "Transfer-Encoding: chunked" + "\r\n" + "\r\n");
        // we omit writing the chunk-size intentionally, write only \r\n
        DecoderException e = assertThrows(DecoderException.class, () -> writeMsg("\r\n"));
        assertThat(e.getMessage(), startsWith("Chunked encoding specified but chunk-size not found"));
        assertThat(channel().inboundMessages(), is(not(empty())));
    }

    @Test
    public void chunkedNoTrailersInvalidChunkSize() {
        writeMsg(startLineForContent() + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "Transfer-Encoding: chunked" + "\r\n" + "\r\n");
        // write illegal characters instead of chunk-size
        DecoderException e = assertThrows(DecoderException.class, () -> writeMsg("text\r\n"));
        assertThat(e.getCause(), is(instanceOf(NumberFormatException.class)));
        assertThat(channel().inboundMessages(), is(not(empty())));
    }

    @Test
    public void chunkedNoTrailersNoChunkCRLF() {
        int chunkSize = 128;
        writeMsg(startLineForContent() + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "Transfer-Encoding: chunked" + "\r\n" + "\r\n");
        writeChunkSize(chunkSize);
        writeContent(chunkSize);
        // we omit writing the "\r\n" after chunk-data intentionally
        DecoderException e = assertThrows(DecoderException.class, this::writeLastChunk);
        assertThat(e.getMessage(), startsWith("Could not find CRLF"));
        assertThat(channel().inboundMessages(), is(not(empty())));
    }

    @Test
    public void chunkedNoContentWithTrailers() {
        writeMsg(startLineForContent() + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "Transfer-Encoding: chunked" + "\r\n" + "\r\n" +
                "0\r\n" +
                "TrailerStatus: good" + "\r\n" + "\r\n");
        validateWithContent(0, true);
    }

    @Test
    public void chunkedContentWithTrailers() {
        int chunkSize = 128;
        writeMsg(startLineForContent() + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "Transfer-Encoding: chunked" + "\r\n" + "\r\n");
        writeChunk(chunkSize);
        writeMsg("0\r\n" + "TrailerStatus: good" + "\r\n" + "\r\n");
        validateWithContent(-chunkSize, true);
    }

    @Test
    public void chunkedNoContentNoTrailers() {
        writeMsg(startLineForContent() + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "Transfer-Encoding: chunked" + "\r\n" + "\r\n");
        writeLastChunk();

        HttpMetaData metaData = assertStartLineForContent();
        assertStandardHeaders(metaData.headers());
        assertEmptyTrailers(channel());
        assertFalse(channel().finishAndReleaseAll());
    }

    @Test
    public void unexpectedTrailersAfterContentLength() {
        int contentLength = 128;
        writeMsg(startLineForContent() + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Connection: keep-alive" + "\r\n" +
                "Content-Length:" + contentLength + "\r\n" + "\r\n");
        writeContent(contentLength);
        // Note that trailers are not allowed when content-length is specified
        // https://tools.ietf.org/html/rfc7230#section-3.3
        DecoderException e = assertThrows(DecoderException.class,
                () -> writeMsg("TrailerStatus: good" + "\r\n" + "\r\n"));
        assertThat(e.getMessage(), startsWith("Invalid start-line"));
        assertThat(channel().inboundMessages(), is(not(empty())));
    }

    @Test
    public void smuggleBeforeZeroContentLengthHeader() {
        smuggleZeroContentLength(true);
    }

    @Test
    public void smuggleAfterZeroContentLengthHeader() {
        smuggleZeroContentLength(false);
    }

    private void smuggleZeroContentLength(boolean smuggleBeforeContentLength) {
        DecoderException e = assertThrows(DecoderException.class, () -> writeMsg(startLine() + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                // Smuggled requests injected into a header will terminate the current request due to valid \r\n\r\n
                // framing terminating the request with no content-length or transfer-encoding, or with known zero
                // content-length [1].
                // [1] https://tools.ietf.org/html/rfc7230#section-3.3.3
                (smuggleBeforeContentLength ?
                        "Smuggled: " + startLine() + "\r\n\r\n" + "Content-Length: 0" + "\r\n" :
                        "Content-Length: 0" + "\r\n" + "Smuggled: " + startLine() + "\r\n\r\n") +
                "Connection: keep-alive" + "\r\n\r\n"));
        assertThat(e.getMessage(), startsWith("Invalid start-line"));

        HttpMetaData metaData = assertStartLine();
        assertSingleHeaderValue(metaData.headers(), HOST, "servicetalk.io");
        assertSingleHeaderValue(metaData.headers(), "Smuggled", startLine());
        assertEmptyTrailers(channel());
    }

    @Test
    public void smuggleAfterTransferEncodingHeader() {
        smuggleTransferEncoding(false);
    }

    protected void smuggleTransferEncoding(boolean smuggleBeforeTransferEncoding) {
        assertThrows(DecoderException.class, () -> writeMsg(startLineForContent() + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                // Smuggled requests injected into a header will terminate the current request due to valid \r\n\r\n
                // framing terminating the request with no content-length or transfer-encoding, or with known zero
                // content-length [1].
                // [1] https://tools.ietf.org/html/rfc7230#section-3.3.3
                (smuggleBeforeTransferEncoding ?
                        "Smuggled: " + startLine() + "\r\n\r\n" + TRANSFER_ENCODING + ":" + CHUNKED + "\r\n" :
                        TRANSFER_ENCODING + ":" + CHUNKED + "\r\n" + "Smuggled: " + startLine() + "\r\n\r\n") +
                "Connection: keep-alive" + "\r\n\r\n"));

        HttpMetaData metaData = assertStartLineForContent();
        assertSingleHeaderValue(metaData.headers(), HOST, "servicetalk.io");
        assertSingleHeaderValue(metaData.headers(), "Smuggled", startLine());
    }

    @Test
    public void smuggleNameBeforeNonZeroContentLengthHeader() {
        smuggleNameZeroContentLengthHeader(true);
    }

    @Test
    public void smuggleNameAfterNonZeroContentLengthHeader() {
        smuggleNameZeroContentLengthHeader(false);
    }

    private void smuggleNameZeroContentLengthHeader(boolean smuggleBeforeContentLength) {
        assertThrows(DecoderException.class, () -> writeMsg(startLineForContent() + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                        (smuggleBeforeContentLength ?
                                startLine() + "\r\n\r\n" + "Content-Length: 0" + "\r\n" :
                                "Content-Length: 0" + "\r\n" + startLine() + "\r\n\r\n") +
                "Connection: keep-alive" + "\r\n\r\n"));
    }
}
