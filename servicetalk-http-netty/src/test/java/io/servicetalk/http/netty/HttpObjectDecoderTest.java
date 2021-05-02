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
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static io.netty.buffer.ByteBufUtil.writeAscii;
import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.util.AsciiString.contentEquals;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferUtils.getByteBufAllocator;
import static io.servicetalk.http.api.HeaderUtils.isTransferEncodingChunked;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class HttpObjectDecoderTest {

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (channel().isOpen()) {
                channel().close().get();
            }
        } finally {
            try {
                if (channelSpecException().isOpen()) {
                    channelSpecException().close().get();
                }
            } finally {
                channel().releaseInbound();
                channel().releaseOutbound();
                channelSpecException().releaseInbound();
                channelSpecException().releaseOutbound();
            }
        }
    }

    abstract EmbeddedChannel channel();

    abstract EmbeddedChannel channelSpecException();

    abstract String startLine();

    abstract HttpMetaData assertStartLine(EmbeddedChannel channel);

    abstract String startLineForContent();

    abstract HttpMetaData assertStartLineForContent(EmbeddedChannel channel);

    final HttpMetaData assertStartLineForContent() {
        return assertStartLineForContent(channel());
    }

    final void writeMsg(String msg) {
        writeMsg(msg, channel());
    }

    final void writeMsg(String msg, EmbeddedChannel channel) {
        assertThat("writeInbound(msg) did not produce something for readInbound()",
                channel.writeInbound(fromAscii(msg)), is(true));
    }

    final void writeContent(int length) {
        writeContent(length, channel());
    }

    final void writeContent(int length, EmbeddedChannel channel) {
        assertThat("writeInbound(content) did not produce something for readInbound()",
                channel.writeInbound(content(length)), is(true));
    }

    final void writeChunkSize(int length, EmbeddedChannel channel) {
        writeMsg(toHexString(length) + "\r\n", channel);
    }

    final void writeChunk(int length) {
        writeChunk(length, channel());
    }

    final void writeChunk(int length, EmbeddedChannel channel) {
        if (length == 0) {
            writeMsg("0\r\n", channel);
            return;
        }
        writeChunkSize(length, channel);
        writeContent(length, channel);
        writeMsg("\r\n", channel);
    }

    final void writeLastChunk(EmbeddedChannel channel) {
        writeMsg("0\r\n\r\n", channel);
    }

    final void assertDecoderException(String msg, String expectedExceptionMsg) {
        assertDecoderException(msg, expectedExceptionMsg, channel());
    }

    final void assertDecoderException(String msg, String expectedExceptionMsg, EmbeddedChannel channel) {
        DecoderException e = assertThrows(DecoderException.class, () -> writeMsg(msg, channel));
        assertThat(e.getMessage(), startsWith(expectedExceptionMsg));
        assertThat(channel().inboundMessages(), is(empty()));
    }

    final void assertDecoderExceptionWithCause(String msg, String expectedExceptionMsg) {
        assertDecoderExceptionWithCause(msg, expectedExceptionMsg, channel());
    }

    final void assertDecoderExceptionWithCause(String msg, String expectedExceptionMsg,
                                               EmbeddedChannel channel) {
        DecoderException e = assertThrows(DecoderException.class, () -> writeMsg(msg, channel));
        assertThat(e.getMessage(), startsWith(expectedExceptionMsg));
        assertThat(e.getCause(), is(instanceOf(IllegalCharacterException.class)));
        assertThat(e.getCause().getMessage(), not(isEmptyString()));
        assertThat(channel().inboundMessages(), is(empty()));
    }

    final HttpMetaData validateWithContent(int expectedContentLength, boolean containsTrailers) {
        return validateWithContent(expectedContentLength, containsTrailers, channel());
    }

    final HttpMetaData validateWithContent(int expectedContentLength, boolean containsTrailers,
        EmbeddedChannel channel) {
        HttpMetaData metaData = assertStartLineForContent(channel);
        assertStandardHeaders(metaData.headers());
        if (expectedContentLength > 0) {
            assertSingleHeaderValue(metaData.headers(), CONTENT_LENGTH, String.valueOf(expectedContentLength));
            HttpHeaders trailers = assertPayloadSize(expectedContentLength, channel);
            assertThat("Trailers are not empty", trailers.isEmpty(), is(true));
        } else if (expectedContentLength == 0) {
            if (containsTrailers) {
                assertSingleHeaderValue(metaData.headers(), TRANSFER_ENCODING, CHUNKED);
                HttpHeaders trailers = channel.readInbound();
                assertSingleHeaderValue(trailers, "TrailerStatus", "good");
            } else {
                assertSingleHeaderValue(metaData.headers(), CONTENT_LENGTH, "0");
                assertEmptyTrailers(channel);
            }
        } else {
            assertThat("No 'transfer-encoding: chunked' header",
                    isTransferEncodingChunked(metaData.headers()), is(true));
            HttpHeaders trailers = assertPayloadSize(-expectedContentLength, channel);
            if (containsTrailers) {
                assertSingleHeaderValue(trailers, "TrailerStatus", "good");
            } else {
                assertThat("Trailers are not empty", trailers.isEmpty(), is(true));
            }
        }
        assertFalse(channel.finishAndReleaseAll());
        return metaData;
    }

    final HttpHeaders assertPayloadSize(int expectedPayloadSize) {
        return assertPayloadSize(expectedPayloadSize, channel());
    }

    final HttpHeaders assertPayloadSize(int expectedPayloadSize, EmbeddedChannel channel) {
        int actualPayloadSize = 0;
        Object item;
        for (;;) {
            item = channel.readInbound();
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
        assertTrue(itr.hasNext(), () -> "Unable to find header name '" + name + "'");
        CharSequence value = itr.next();
        assertTrue(
            contentEquals(expectedValue, value),
                () -> name + " expected value of '" + expectedValue + "' but got: '" + value + "'");
        assertFalse(itr.hasNext(), "Unexpected second value for header name '" + name + "'");
    }

    static void assertStandardHeaders(HttpHeaders headers) {
        assertSingleHeaderValue(headers, HOST, "servicetalk.io");
        assertSingleHeaderValue(headers, "connecTion", KEEP_ALIVE); // Make sure header-name is case-insensitive
    }

    static ByteBuf fromAscii(final String msg) {
        return writeAscii(getByteBufAllocator(DEFAULT_ALLOCATOR), msg);
    }

    private static ByteBuf content(int contentLength) {
        byte[] content = new byte[contentLength];
        ThreadLocalRandom.current().nextBytes(content);
        return wrappedBuffer(content);
    }

    private EmbeddedChannel channel(boolean crlf) {
        return crlf ? channel() : channelSpecException();
    }

    private static String br(boolean crlf) {
        return crlf ? "\r\n" : "\n";
    }

    @Test
    void startLineWithoutCR() {
        assertDecoderException(startLine() + '\n', "Found LF (0x0a) but no CR (0x0d) before");
    }

    @Test
    void startLineWithoutCRSpecException() {
        writeMsg(startLine() + "\n" + "\n", channelSpecException());
        assertStartLine(channelSpecException());
        assertEmptyTrailers(channelSpecException());
        assertFalse(channelSpecException().finishAndReleaseAll());
    }

    @Test
    void validStartLine() {
        validStartLine(true);
        validStartLine(false);
    }

    private void validStartLine(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        writeMsg(startLine() + br + br, channel);
        assertStartLine(channel);
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    void validStartLineInThreeFrames() {
        validStartLineInThreeFrames(true);
        validStartLineInThreeFrames(false);
    }

    private void validStartLineInThreeFrames(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertFalse(channel.writeInbound(fromAscii(startLine())));
        assertFalse(channel.writeInbound(fromAscii(br)));
        assertTrue(channel.writeInbound(fromAscii(br)));
        assertStartLine(channel);
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    void validStartLineInFourFrames() {
        validStartLineInFourFrames(true);
        validStartLineInFourFrames(false);
    }

    private void validStartLineInFourFrames(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertFalse(channel.writeInbound(fromAscii(startLine().substring(0, 3))));
        assertFalse(channel.writeInbound(fromAscii(startLine().substring(3))));
        assertFalse(channel.writeInbound(fromAscii(br)));
        assertTrue(channel.writeInbound(fromAscii(br)));
        assertStartLine(channel);
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    void validStartLineAfterPrefaceCRLF() {
        validStartLineAfterPrefaceCRLF(true);
        validStartLineAfterPrefaceCRLF(false);
    }

    private void validStartLineAfterPrefaceCRLF(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        writeMsg("\r\n" + startLine() + br + br, channel);
        assertStartLine(channel);
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    void validStartLineAfterPrefaceCRLFInSeparateFrame() {
        validStartLineAfterPrefaceCRLFInSeparateFrame(true);
        validStartLineAfterPrefaceCRLFInSeparateFrame(false);
    }

    private void validStartLineAfterPrefaceCRLFInSeparateFrame(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertFalse(channel.writeInbound(fromAscii("\r\n")));   // write control characters first
        writeMsg(startLine() + br + br, channel);
        assertStartLine(channel);
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    void tooManyPrefaceCharacters() {
        tooManyPrefaceCharacters(true);
        tooManyPrefaceCharacters(false);
    }

    private void tooManyPrefaceCharacters(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        DecoderException ex = assertThrows(DecoderException.class,
                () -> writeMsg("\r\n\r\n\r\n" + startLine() + br + br, channel));
        assertThat(ex.getMessage(), startsWith("Too many prefacing CRLF (0x0d0a) characters"));
        assertThat(channel.inboundMessages(), is(empty()));
    }

    @Test
    void whitespaceNotAllowedBeforeHeaderFieldName() {
        whitespaceNotAllowedBeforeHeaderFieldName(true);
        whitespaceNotAllowedBeforeHeaderFieldName(false);
    }

    private void whitespaceNotAllowedBeforeHeaderFieldName(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertDecoderExceptionWithCause(startLine() + br +
                " Host: servicetalk.io" + br + br, "Invalid header name", channel);
    }

    @Test
    void whitespaceNotAllowedBetweenHeaderFieldNameAndColon() {
        whitespaceNotAllowedBetweenHeaderFieldNameAndColon(true);
        whitespaceNotAllowedBetweenHeaderFieldNameAndColon(false);
    }

    private void whitespaceNotAllowedBetweenHeaderFieldNameAndColon(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertDecoderExceptionWithCause(startLine() + br +
                "Host : servicetalk.io" + br + br, "Invalid header name", channel);
    }

    @Test
    void controlCharNotAllowedBeforeHeaderFieldValue() {
        controlCharNotAllowedBeforeHeaderFieldValue(true);
        controlCharNotAllowedBeforeHeaderFieldValue(false);
    }

    private void controlCharNotAllowedBeforeHeaderFieldValue(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertDecoderExceptionWithCause(startLine() + br +
                "Host: \fservicetalk.io" + br + br, "Invalid value for the header", channel);
    }

    @Test
    void noEndOfHeaderName() {
        noEndOfHeaderName(true);
        noEndOfHeaderName(false);
    }

    private void noEndOfHeaderName(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertDecoderException(startLine() + br +
                "Host" + br + br, "Unable to find end of a header name", channel);
    }

    @Test
    void emptyHeaderName() {
        emptyHeaderName(true);
        emptyHeaderName(false);
    }

    private void emptyHeaderName(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertDecoderException(startLine() + br +
                ": some-value" + br + br, "Empty header name", channel);
    }

    @Test
    void whitespaceHeaderName() {
        testBadHeaderName(" ");
        testBadHeaderName("  ");
        testBadHeaderName("\t");
        testBadHeaderName("\t\t");
    }

    @Test
    void embededWhitespaceHeaderName() {
        testBadHeaderName("content length");
        testBadHeaderName("content\tlength");
    }

    @Test
    void trailingWhitespaceHeaderName() {
        testBadHeaderName("content-length ");
        testBadHeaderName("content-length\t");
    }

    private void testBadHeaderName(String badHeader) {
        testBadHeaderName(badHeader, true);
        testBadHeaderName(badHeader, false);
    }

    private void testBadHeaderName(String badHeader, boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertDecoderException(startLine() + br +
                badHeader + ": 3" + br + br, "Invalid header name", channel);
    }

    @Test
    void headerNameWithControlChar() {
        headerNameWithControlChar(true);
        headerNameWithControlChar(false);
    }

    private void headerNameWithControlChar(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertDecoderExceptionWithCause(startLine() + br +
                "H\0st: servicetalk.io" + br + br, "Invalid header name", channel);
    }

    @Test
    void headerNameWithObsText() {
        headerNameWithObsText(true);
        headerNameWithObsText(false);
    }

    private void headerNameWithObsText(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertDecoderExceptionWithCause(startLine() + br +
                "Hóst: servicetalk.io" + br + br, "Invalid header name", channel);
    }

    @Test
    void headerFiledValueEmpty() {
        testHeaderFiledValue("", "");
        testHeaderFiledValue(" ", "");
        testHeaderFiledValue("   ", "");
    }

    @Test
    void headerFiledValue() {
        testHeaderFiledValue("servicetalk.io", "servicetalk.io");
        testHeaderFiledValue(" servicetalk.io", "servicetalk.io");
        testHeaderFiledValue("servicetalk.io ", "servicetalk.io");
        testHeaderFiledValue(" servicetalk.io ", "servicetalk.io");
        testHeaderFiledValue("   servicetalk.io", "servicetalk.io");
        testHeaderFiledValue("servicetalk.io   ", "servicetalk.io");
        testHeaderFiledValue("   servicetalk.io   ", "servicetalk.io");
    }

    @Test
    void headerFiledValueSingleCharacter() {
        testHeaderFiledValue("s", "s");
        testHeaderFiledValue(" s", "s");
        testHeaderFiledValue("s ", "s");
        testHeaderFiledValue(" s ", "s");
        testHeaderFiledValue("   s", "s");
        testHeaderFiledValue("s   ", "s");
        testHeaderFiledValue("   s   ", "s");
    }

    @Test
    void headerFiledValueCommaSeparated() {
        testHeaderFiledValue("first, second, third", "first, second, third");
    }

    @Test
    void headerFiledValueAllowsHTab() {
        testHeaderFiledValue("service\talk.io", "service\talk.io");
    }

    @Test
    void headerFiledValueAllowsObsText() {
        testHeaderFiledValue("sêrvicêtalk.io", "sêrvicêtalk.io");
    }

    private void testHeaderFiledValue(String fieldValue, String expectedFieldValue) {
        testHeaderFiledValue(fieldValue, expectedFieldValue, true);
        testHeaderFiledValue(fieldValue, expectedFieldValue, false);
    }

    private void testHeaderFiledValue(String fieldValue, String expectedFieldValue, boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        writeMsg(startLine() + br +
                "Host:" + fieldValue + br + br, channel);
        HttpMetaData metaData = assertStartLine(channel);
        assertSingleHeaderValue(metaData.headers(), HOST, expectedFieldValue);
        assertEmptyTrailers(channel);
    }

    @Test
    void multipleHeaderFiledValues() {
        multipleHeaderFiledValues(true);
        multipleHeaderFiledValues(false);
    }

    private void multipleHeaderFiledValues(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        writeMsg(startLine() + br +
                "Accept-Encoding: gzip" + br +
                "Accept-Encoding: compress" + br +
                "Accept-Encoding: deflate" + br + br, channel);
        HttpMetaData metaData = assertStartLine(channel);
        List<String> headerValues = new ArrayList<>();
        Iterator<? extends CharSequence> itr = metaData.headers().valuesIterator(ACCEPT_ENCODING);
        while (itr.hasNext()) {
            headerValues.add(itr.next().toString());
        }
        assertThat("Unable to find header name 'Accept-Encoding'", headerValues, hasSize(3));
        assertThat(headerValues, containsInAnyOrder("gzip", "compress", "deflate"));
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    void zeroContentLength() {
        zeroContentLength(true);
        zeroContentLength(false);
    }

    private void zeroContentLength(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        writeMsg(startLineForContent() + br +
                "Host: servicetalk.io" + br +
                "Connection: keep-alive" + br +
                "Content-Length: 0" + br + br, channel);
        validateWithContent(0, false, channel);
    }

    @Test
    void contentLengthNoTrailers() {
        contentLengthNoTrailers(true);
        contentLengthNoTrailers(false);
    }

    private void contentLengthNoTrailers(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        int contentLength = 128;
        writeMsg(startLineForContent() + br +
                "Host: servicetalk.io" + br +
                "Connection: keep-alive" + br +
                "Content-Length: " + contentLength + br + br, channel);
        writeContent(contentLength, channel);
        validateWithContent(contentLength, false, channel);
    }

    @Test
    void chunkedNoTrailersChunkSizeWithoutSemicolon() {
        chunkedNoTrailers(false, true);
        chunkedNoTrailers(false, false);
    }

    @Test
    void chunkedNoTrailersChunkSizeWithSemicolon() {
        chunkedNoTrailers(true, true);
        chunkedNoTrailers(true, false);
    }

    private void chunkedNoTrailers(boolean addSemicolon, boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        int chunkSize = 128;
        writeMsg(startLineForContent() + br +
                "Host: servicetalk.io" + br +
                "Connection: keep-alive" + br +
                "Transfer-Encoding: chunked" + br + br, channel);
        writeMsg(toHexString(chunkSize) + (addSemicolon ? ";" : "") + "\r\n", channel);
        writeContent(chunkSize, channel);
        writeMsg("\r\n", channel);
        writeLastChunk(channel);
        validateWithContent(-chunkSize, false, channel);
    }

    @Test
    void chunkedWithContentLength() {
        chunkedWithContentLength(true);
        chunkedWithContentLength(false);
    }

    private void chunkedWithContentLength(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        int chunkSize = 128;
        int chunkedContentLength = 2 + 2 + chunkSize + 2 + 5;
        writeMsg(startLineForContent() + br +
                "Host: servicetalk.io" + br +
                "Connection: keep-alive" + br +
                "Content-Length: " + chunkedContentLength + br +
                "Transfer-Encoding: chunked" + br + br, channel);
        writeChunk(chunkSize, channel);
        writeLastChunk(channel);
        HttpMetaData metaData = validateWithContent(-chunkSize, false, channel);
        assertThat("Unexpected content-length header(s)",
                metaData.headers().valuesIterator(CONTENT_LENGTH).hasNext(), is(false));
    }

    @Test
    void chunkedNoTrailersMultipleLargeContent() {
        chunkedNoTrailersMultipleLargeContent(true);
        chunkedNoTrailersMultipleLargeContent(false);
    }

    private void chunkedNoTrailersMultipleLargeContent(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        int chunkSize = 4096;
        int numChunks = 5;
        writeMsg(startLineForContent() + br +
                "Host: servicetalk.io" + br +
                "Connection: keep-alive" + br +
                "Transfer-Encoding: chunked" + br + br, channel);
        for (int i = 0; i < numChunks; ++i) {
            writeChunk(chunkSize, channel);
        }
        writeLastChunk(channel);
        validateWithContent(-(chunkSize * numChunks), false, channel);
    }

    @Test
    void chunkedNoTrailersNoChunkSize() {
        chunkedNoTrailersNoChunkSize(true);
        chunkedNoTrailersNoChunkSize(false);
    }

    private void chunkedNoTrailersNoChunkSize(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        writeMsg(startLineForContent() + br +
                "Host: servicetalk.io" + br +
                "Connection: keep-alive" + br +
                "Transfer-Encoding: chunked" + br + br, channel);
        // we omit writing the chunk-size intentionally, write only \r\n
        DecoderException e = assertThrows(DecoderException.class, () -> writeMsg("\r\n", channel));
        assertThat(e.getMessage(), startsWith("Chunked encoding specified but chunk-size not found"));
        assertThat(channel.inboundMessages(), is(not(empty())));
    }

    @Test
    void chunkedNoTrailersInvalidChunkSize() {
        chunkedNoTrailersInvalidChunkSize(true);
        chunkedNoTrailersInvalidChunkSize(false);
    }

    private void chunkedNoTrailersInvalidChunkSize(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        writeMsg(startLineForContent() + br +
                "Host: servicetalk.io" + br +
                "Connection: keep-alive" + br +
                "Transfer-Encoding: chunked" + br + br, channel);
        // write illegal characters instead of chunk-size
        DecoderException e = assertThrows(DecoderException.class, () -> writeMsg("text\r\n", channel));
        assertThat(e.getCause(), is(instanceOf(NumberFormatException.class)));
        assertThat(channel.inboundMessages(), is(not(empty())));
    }

    @Test
    void chunkedNoTrailersNoChunkCRLF() {
        chunkedNoTrailersNoChunkCRLF(true);
        chunkedNoTrailersNoChunkCRLF(false);
    }

    private void chunkedNoTrailersNoChunkCRLF(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        int chunkSize = 128;
        writeMsg(startLineForContent() + br +
                "Host: servicetalk.io" + br +
                "Connection: keep-alive" + br +
                "Transfer-Encoding: chunked" + br + br, channel);
        writeChunkSize(chunkSize, channel);
        writeContent(chunkSize, channel);
        // we omit writing the "\r\n" after chunk-data intentionally
        DecoderException e = assertThrows(DecoderException.class, () -> writeLastChunk(channel));
        assertThat(e.getMessage(), startsWith("Could not find CRLF"));
        assertThat(channel.inboundMessages(), is(not(empty())));
    }

    @Test
    void chunkedNoContentWithTrailers() {
        chunkedNoContentWithTrailers(true);
        chunkedNoContentWithTrailers(false);
    }

    private void chunkedNoContentWithTrailers(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        writeMsg(startLineForContent() + br +
                "Host: servicetalk.io" + br +
                "Connection: keep-alive" + br +
                "Transfer-Encoding: chunked" + br + br +
                "0\r\n" +
                "TrailerStatus: good" + br + br, channel);
        validateWithContent(0, true, channel);
    }

    @Test
    void chunkedContentWithTrailers() {
        chunkedContentWithTrailers(true);
        chunkedContentWithTrailers(false);
    }

    private void chunkedContentWithTrailers(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        int chunkSize = 128;
        writeMsg(startLineForContent() + br +
                "Host: servicetalk.io" + br +
                "Connection: keep-alive" + br +
                "Transfer-Encoding: chunked" + br + br, channel);
        writeChunk(chunkSize, channel);
        writeMsg("0\r\n" + "TrailerStatus: good" + br + br, channel);
        validateWithContent(-chunkSize, true, channel);
    }

    @Test
    void chunkedNoContentNoTrailers() {
        chunkedNoContentNoTrailers(true);
        chunkedNoContentNoTrailers(false);
    }

    private void chunkedNoContentNoTrailers(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        writeMsg(startLineForContent() + br +
                "Host: servicetalk.io" + br +
                "Connection: keep-alive" + br +
                "Transfer-Encoding: chunked" + br + br, channel);
        writeLastChunk(channel);

        HttpMetaData metaData = assertStartLineForContent(channel);
        assertStandardHeaders(metaData.headers());
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    void unexpectedTrailersAfterContentLength() {
        unexpectedTrailersAfterContentLength(true);
        unexpectedTrailersAfterContentLength(false);
    }

    private void unexpectedTrailersAfterContentLength(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        int contentLength = 128;
        writeMsg(startLineForContent() + br +
                "Host: servicetalk.io" + br +
                "Connection: keep-alive" + br +
                "Content-Length:" + contentLength + br + br, channel);
        writeContent(contentLength, channel);
        // Note that trailers are not allowed when content-length is specified
        // https://tools.ietf.org/html/rfc7230#section-3.3
        DecoderException e = assertThrows(DecoderException.class,
                () -> writeMsg("TrailerStatus: good" + br + br, channel));
        assertThat(e.getMessage(), startsWith("Invalid start-line"));
        assertThat(channel.inboundMessages(), is(not(empty())));
    }

    @Test
    void smuggleBeforeZeroContentLengthHeader() {
        smuggleZeroContentLength(true, false);
        smuggleZeroContentLength(true, true);
    }

    @Test
    void smuggleAfterZeroContentLengthHeader() {
        smuggleZeroContentLength(false, false);
        smuggleZeroContentLength(false, true);
    }

    private void smuggleZeroContentLength(boolean smuggleBeforeContentLength, boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        DecoderException e = assertThrows(DecoderException.class, () -> writeMsg(startLine() + br +
                "Host: servicetalk.io" + br +
                // Smuggled requests injected into a header will terminate the current request due to valid \r\n\r\n
                // framing terminating the request with no content-length or transfer-encoding, or with known zero
                // content-length [1].
                // [1] https://tools.ietf.org/html/rfc7230#section-3.3.3
                (smuggleBeforeContentLength ?
                        "Smuggled: " + startLine() + br + br + "Content-Length: 0" + br :
                        "Content-Length: 0" + br + "Smuggled: " + startLine() + br + br) +
                "Connection: keep-alive" + br + br, channel));
        assertThat(e.getMessage(), startsWith("Invalid start-line"));

        HttpMetaData metaData = assertStartLine(channel);
        assertSingleHeaderValue(metaData.headers(), HOST, "servicetalk.io");
        assertSingleHeaderValue(metaData.headers(), "Smuggled", startLine());
        assertEmptyTrailers(channel);
    }

    @Test
    void smuggleAfterTransferEncodingHeader() {
        smuggleTransferEncoding(false, false);
        smuggleTransferEncoding(false, true);
    }

    void smuggleTransferEncoding(boolean smuggleBeforeTransferEncoding, boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertThrows(DecoderException.class, () -> writeMsg(startLineForContent() + br +
                "Host: servicetalk.io" + br +
                // Smuggled requests injected into a header will terminate the current request due to valid \r\n\r\n
                // framing terminating the request with no content-length or transfer-encoding, or with known zero
                // content-length [1].
                // [1] https://tools.ietf.org/html/rfc7230#section-3.3.3
                (smuggleBeforeTransferEncoding ?
                        "Smuggled: " + startLine() + br + br + TRANSFER_ENCODING + ":" + CHUNKED + br :
                        TRANSFER_ENCODING + ":" + CHUNKED + br + "Smuggled: " + startLine() + br + br) +
                "Connection: keep-alive" + br + br, channel));

        HttpMetaData metaData = assertStartLineForContent(channel);
        assertSingleHeaderValue(metaData.headers(), HOST, "servicetalk.io");
        assertSingleHeaderValue(metaData.headers(), "Smuggled", startLine());
    }

    @Test
    void smuggleNameBeforeNonZeroContentLengthHeader() {
        smuggleNameZeroContentLengthHeader(true, false);
        smuggleNameZeroContentLengthHeader(true, true);
    }

    @Test
    void smuggleNameAfterNonZeroContentLengthHeader() {
        smuggleNameZeroContentLengthHeader(false, false);
        smuggleNameZeroContentLengthHeader(false, true);
    }

    private void smuggleNameZeroContentLengthHeader(boolean smuggleBeforeContentLength, boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertThrows(DecoderException.class, () -> writeMsg(startLineForContent() + br +
                "Host: servicetalk.io" + br +
                        (smuggleBeforeContentLength ?
                                startLine() + br + br + "Content-Length: 0" + br :
                                "Content-Length: 0" + br + startLine() + br + br) +
                "Connection: keep-alive" + br + br, channel));
    }

    @Test
    void multipleContentLengthHeaders() {
        multipleContentLengthHeaders(true);
        multipleContentLengthHeaders(false);
    }

    private void multipleContentLengthHeaders(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        DecoderException e = assertThrows(DecoderException.class, () -> writeMsg(startLineForContent() + br +
                "Host: servicetalk.io" + br +
                "Content-Length: 1" + br +
                "Content-Length: 2" + br +
                "Connection: keep-alive" + br + br, channel));
        assertThat(e.getCause(), instanceOf(IllegalArgumentException.class));
        assertThat(e.getCause().getMessage(), startsWith("Multiple content-length values found"));
    }

    @Test
    void multipleContentLengthHeaderValues() {
        multipleContentLengthHeaderValues(true);
        multipleContentLengthHeaderValues(false);
    }

    private void multipleContentLengthHeaderValues(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        DecoderException e = assertThrows(DecoderException.class, () -> writeMsg(startLineForContent() + br +
                "Host: servicetalk.io" + br +
                "Content-Length: 1, 2" + br +
                "Connection: keep-alive" + br + br, channel));
        assertThat(e.getCause(), instanceOf(IllegalArgumentException.class));
        assertThat(e.getCause().getMessage(), startsWith("Multiple content-length values found"));
    }

    @Test
    void signedPositiveContentLengthHeaderValues() {
        malformedContentLengthHeaderValue("+1", true);
        malformedContentLengthHeaderValue("+1", false);
    }

    @Test
    void signedNegativeContentLengthHeaderValues() {
        malformedContentLengthHeaderValue("-1", true);
        malformedContentLengthHeaderValue("-1", false);
    }

    @Test
    void malformedContentLengthHeaderValueWithSP() {
        malformedContentLengthHeaderValue("1 2", true);
        malformedContentLengthHeaderValue("1 2", false);
    }

    @Test
    void malformedContentLengthHeaderValueWithLetter() {
        malformedContentLengthHeaderValue("1a2", true);
        malformedContentLengthHeaderValue("1a2", false);
    }

    @Test
    void malformedContentLengthHeaderValueWithSymbol() {
        malformedContentLengthHeaderValue("1-2", true);
        malformedContentLengthHeaderValue("1-2", false);
    }

    void malformedContentLengthHeaderValue(String value, boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        DecoderException e = assertThrows(DecoderException.class, () -> writeMsg(startLineForContent() + br +
                "Host: servicetalk.io" + br +
                "Content-Length: " + value + br +
                "Connection: keep-alive" + br + br, channel));
        assertThat(e.getCause(), instanceOf(IllegalArgumentException.class));
        assertThat(e.getCause().getMessage(), startsWith("Malformed 'content-length' value"));
    }
}
