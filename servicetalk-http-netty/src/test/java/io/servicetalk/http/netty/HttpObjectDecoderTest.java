/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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
import io.netty.handler.codec.TooLongFrameException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;

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
import static io.servicetalk.http.netty.H1ProtocolConfigBuilder.DEFAULT_MAX_HEADER_FIELD_LENGTH;
import static io.servicetalk.http.netty.H1ProtocolConfigBuilder.DEFAULT_MAX_START_LINE_LENGTH;
import static java.lang.Integer.toHexString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
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

    abstract boolean isDecodingRequest();

    abstract String startLine();

    abstract HttpMetaData assertStartLine(EmbeddedChannel channel);

    abstract String startLineForContent();

    abstract HttpMetaData assertStartLineForContent(EmbeddedChannel channel);

    abstract String startLine(String extra);

    /**
     * Creates a new channel with a custom maxTotalHeaderLength for testing header size limits.
     *
     * @param maxTotalHeaderFieldsLength the maximum total header length to enforce
     * @return a new EmbeddedChannel configured with the specified limit
     */
    abstract EmbeddedChannel channelWithMaxTotalHeaderFieldsLength(int maxTotalHeaderFieldsLength);

    final HttpMetaData assertStartLineForContent() {
        return assertStartLineForContent(channel());
    }

    final void writeMsg(String msg) {
        writeMsg(msg, channel());
    }

    static void writeMsg(String msg, EmbeddedChannel channel) {
        assertThat("writeInbound(msg) did not produce something for readInbound()",
                channel.writeInbound(fromAscii(msg)), is(true));
    }

    final void writeContent(int length) {
        writeContent(length, channel());
    }

    static void writeContent(int length, EmbeddedChannel channel) {
        assertThat("writeInbound(content) did not produce something for readInbound()",
                channel.writeInbound(content(length)), is(true));
    }

    static void writeChunkSize(int length, EmbeddedChannel channel) {
        writeMsg(toHexString(length) + "\r\n", channel);
    }

    final void writeChunk(int length) {
        writeChunk(length, channel());
    }

    static void writeChunk(int length, EmbeddedChannel channel) {
        if (length == 0) {
            writeMsg("0\r\n", channel);
            return;
        }
        writeChunkSize(length, channel);
        writeContent(length, channel);
        writeMsg("\r\n", channel);
    }

    static void writeLastChunk(EmbeddedChannel channel) {
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
        assertDecoderExceptionWithCause(msg, expectedExceptionMsg, IllegalCharacterException.class, channel);
    }

    final <T extends Throwable> void assertDecoderExceptionWithCause(String msg, String expectedExceptionMsg,
                                                                     Class<T> causeType, EmbeddedChannel channel) {
        DecoderException e = assertThrows(DecoderException.class, () -> writeMsg(msg, channel));
        assertThat(e.getMessage(), startsWith(expectedExceptionMsg));
        assertThat(e.getCause(), is(instanceOf(causeType)));
        assertThat(e.getCause().getMessage(), not(is(emptyString())));
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
            assertThat("Trailers are not empty", trailers, nullValue());
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
                assertThat(trailers, not(nullValue()));
                assertSingleHeaderValue(trailers, "TrailerStatus", "good");
            } else if (trailers != null) {
                assertThat("Trailers are not empty", trailers.isEmpty(), is(true));
            }
        }
        assertFalse(channel.finishAndReleaseAll());
        return metaData;
    }

    @Nullable
    final HttpHeaders assertPayloadSize(int expectedPayloadSize) {
        return assertPayloadSize(expectedPayloadSize, channel());
    }

    @Nullable
    static HttpHeaders assertPayloadSize(int expectedPayloadSize, EmbeddedChannel channel) {
        int actualPayloadSize = 0;
        Object item;
        for (;;) {
            item = channel.readInbound();
            if (item instanceof Buffer) {
                actualPayloadSize += ((Buffer) item).readableBytes();
            } else {
                assertThat(actualPayloadSize, is(expectedPayloadSize));
                assertThat(item, anyOf(nullValue(), instanceOf(HttpHeaders.class)));
                return (HttpHeaders) item;
            }
        }
    }

    static void assertEmptyTrailers(EmbeddedChannel channel) {
        HttpHeaders trailers = channel.readInbound();
        if (trailers != null) {
            assertThat("Trailers are not empty", trailers.isEmpty(), is(true));
        }
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

    EmbeddedChannel channel(boolean crlf) {
        return crlf ? channel() : channelSpecException();
    }

    static String br(boolean crlf) {
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
    void startLineLimitExceeded() {
        String msg = startLine(repeatChar('x', DEFAULT_MAX_START_LINE_LENGTH));
        TooLongFrameException e = assertThrows(TooLongFrameException.class, () -> writeMsg(msg));
        assertThat(e.getMessage(),
                is(equalTo("Could not find CRLF (0x0d0a) within 4096 bytes, while parsing line #0")));
        assertThat(channel().inboundMessages(), is(empty()));
    }

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void validStartLine(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        writeMsg(startLine() + br + br, channel);
        assertStartLine(channel);
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void validStartLineInThreeFrames(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertFalse(channel.writeInbound(fromAscii(startLine())));
        assertFalse(channel.writeInbound(fromAscii(br)));
        assertTrue(channel.writeInbound(fromAscii(br)));
        assertStartLine(channel);
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void validStartLineInFourFrames(boolean crlf) {
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

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void validStartLineAfterPrefaceCRLF(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        writeMsg("\r\n" + startLine() + br + br, channel);
        assertStartLine(channel);
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void validStartLineAfterPrefaceCRLFInSeparateFrame(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertFalse(channel.writeInbound(fromAscii("\r\n")));   // write control characters first
        writeMsg(startLine() + br + br, channel);
        assertStartLine(channel);
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void tooManyPrefaceCharacters(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        DecoderException ex = assertThrows(DecoderException.class,
                () -> writeMsg("\r\n\r\n\r\n" + startLine() + br + br, channel));
        assertThat(ex.getMessage(), startsWith("Too many prefacing CRLF (0x0d0a) characters"));
        assertThat(channel.inboundMessages(), is(empty()));
    }

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void whitespaceNotAllowedBeforeHeaderFieldName(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertDecoderExceptionWithCause(startLine() + br +
                " Host: servicetalk.io" + br + br, "Invalid header name", channel);
    }

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void whitespaceNotAllowedBetweenHeaderFieldNameAndColon(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertDecoderExceptionWithCause(startLine() + br +
                "Host : servicetalk.io" + br + br, "Invalid header name", channel);
    }

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void controlCharNotAllowedBeforeHeaderFieldValue(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertDecoderExceptionWithCause(startLine() + br +
                "Host: \fservicetalk.io" + br + br, "Invalid value for the header", channel);
    }

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void noEndOfHeaderName(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertDecoderException(startLine() + br +
                "Host" + br + br, "Unable to find end of a header name", channel);
    }

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void emptyHeaderName(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertDecoderException(startLine() + br +
                ": some-value" + br + br, "Empty header name", channel);
    }

    @ParameterizedTest(name = "{displayName} [{index}] badHeader={0} crlf={1}")
    @MethodSource("badHeaderNameArgs")
    void testBadHeaderName(String badHeader, boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertDecoderException(startLine() + br +
                badHeader + ": 3" + br + br, "Invalid header name", channel);
    }

    private static Collection<Arguments> badHeaderNameArgs() {
        final List<Arguments> arguments = new ArrayList<>();
        for (boolean crlf : new boolean[] {true, false}) {
            arguments.add(Arguments.of(" ", crlf)); // just whitespace
            arguments.add(Arguments.of("  ", crlf)); // just whitespace
            arguments.add(Arguments.of("\t", crlf)); // just whitespace
            arguments.add(Arguments.of("\t\t", crlf)); // just whitespace
            arguments.add(Arguments.of("content length", crlf)); // embedded whitespace
            arguments.add(Arguments.of("content\tlength", crlf)); // embedded whitespace
            arguments.add(Arguments.of("content-length ", crlf)); // trailing whitespace
            arguments.add(Arguments.of("content-length\t", crlf)); // trailing whitespace
        }
        return arguments;
    }

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void headerNameWithControlChar(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertDecoderExceptionWithCause(startLine() + br +
                "H\0st: servicetalk.io" + br + br, "Invalid header name", channel);
    }

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void headerNameWithObsText(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertDecoderExceptionWithCause(startLine() + br +
                "Hóst: servicetalk.io" + br + br, "Invalid header name", channel);
    }

    @ParameterizedTest(name = "{displayName} [{index}] fieldValue={0} expectedFieldValue={1} crlf={2}")
    @MethodSource("headerFieldValueSource")
    void testHeaderFiledValue(String fieldValue, String expectedFieldValue, boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        writeMsg(startLine() + br +
                "Host:" + fieldValue + br + br, channel);
        HttpMetaData metaData = assertStartLine(channel);
        assertSingleHeaderValue(metaData.headers(), HOST, expectedFieldValue);
        assertEmptyTrailers(channel);
    }

    private static Collection<Arguments> headerFieldValueSource() {
        final List<Arguments> arguments = new ArrayList<>();
        for (boolean crlf : new boolean[] {true, false}) {
            arguments.add(Arguments.of("", "", crlf));
            arguments.add(Arguments.of(" ", "", crlf));
            arguments.add(Arguments.of("   ", "", crlf));

            arguments.add(Arguments.of("servicetalk.io", "servicetalk.io", crlf));
            arguments.add(Arguments.of(" servicetalk.io", "servicetalk.io", crlf));
            arguments.add(Arguments.of("servicetalk.io ", "servicetalk.io", crlf));
            arguments.add(Arguments.of(" servicetalk.io ", "servicetalk.io", crlf));
            arguments.add(Arguments.of("   servicetalk.io", "servicetalk.io", crlf));
            arguments.add(Arguments.of("servicetalk.io   ", "servicetalk.io", crlf));
            arguments.add(Arguments.of("   servicetalk.io   ", "servicetalk.io", crlf));

            arguments.add(Arguments.of("s", "s", crlf));
            arguments.add(Arguments.of(" s", "s", crlf));
            arguments.add(Arguments.of("s ", "s", crlf));
            arguments.add(Arguments.of(" s ", "s", crlf));
            arguments.add(Arguments.of("   s", "s", crlf));
            arguments.add(Arguments.of("s   ", "s", crlf));
            arguments.add(Arguments.of("   s   ", "s", crlf));

            arguments.add(Arguments.of("first, second, third", "first, second, third", crlf)); // comma separated
            arguments.add(Arguments.of("service\talk.io", "service\talk.io", crlf)); // allows h tab
            arguments.add(Arguments.of("sêrvicêtalk.io", "sêrvicêtalk.io", crlf)); // allows obs text
        }
        return arguments;
    }

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void multipleHeaderFiledValues(boolean crlf) {
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

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void zeroContentLength(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        writeMsg(startLineForContent() + br +
                "Host: servicetalk.io" + br +
                "Connection: keep-alive" + br +
                "Content-Length: 0" + br + br, channel);
        validateWithContent(0, false, channel);
    }

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void contentLengthNoTrailers(boolean crlf) {
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

    @ParameterizedTest(name = "{displayName} [{index}] addSemicolon={0} crlf={1}")
    @MethodSource("biBooleanPermutationSource")
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

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void chunkedWithContentLength(boolean crlf) {
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

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void chunkedNoTrailersMultipleLargeContent(boolean crlf) {
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

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void chunkedNoTrailersNoChunkSize(boolean crlf) {
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

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void chunkedNoTrailersInvalidChunkSize(boolean crlf) {
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

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void chunkedNoTrailersNoChunkCRLF(boolean crlf) {
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

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void chunkedNoContentWithTrailers(boolean crlf) {
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

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void chunkedContentWithTrailers(boolean crlf) {
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

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void chunkedNoContentNoTrailers(boolean crlf) {
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

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void unexpectedTrailersAfterContentLength(boolean crlf) {
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
        assertThat(e.getMessage(), startsWith(isDecodingRequest() ? "Invalid start-line" : "Invalid HTTP version"));
        assertThat(channel.inboundMessages(), is(not(empty())));
    }

    @ParameterizedTest(name = "{displayName} [{index}] smuggleBeforeContentLength={0} crlf={1}")
    @MethodSource("biBooleanPermutationSource")
    void smuggleZeroContentLength(boolean smuggleBeforeContentLength, boolean crlf) {
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
        assertThat(e.getMessage(), startsWith(isDecodingRequest() ? "Invalid start-line" : "Invalid HTTP version"));

        HttpMetaData metaData = assertStartLine(channel);
        assertSingleHeaderValue(metaData.headers(), HOST, "servicetalk.io");
        assertSingleHeaderValue(metaData.headers(), "Smuggled", startLine());
        assertEmptyTrailers(channel);
    }

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void smuggleAfterTransferEncodingHeader(boolean crlf) {
        smuggleTransferEncoding(false, crlf);
    }

    protected void smuggleTransferEncoding(boolean smuggleBeforeTransferEncoding, boolean crlf) {
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

    @ParameterizedTest(name = "{displayName} [{index}] smuggleBeforeContentLength={0} crlf={1}")
    @MethodSource("biBooleanPermutationSource")
    void smuggleNameZeroContentLengthHeader(boolean smuggleBeforeContentLength, boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        assertThrows(DecoderException.class, () -> writeMsg(startLineForContent() + br +
                "Host: servicetalk.io" + br +
                        (smuggleBeforeContentLength ?
                                startLine() + br + br + "Content-Length: 0" + br :
                                "Content-Length: 0" + br + startLine() + br + br) +
                "Connection: keep-alive" + br + br, channel));
    }

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void multipleContentLengthHeaders(boolean crlf) {
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

    @ParameterizedTest(name = "{displayName} [{index}] crlf={0}")
    @ValueSource(booleans = {true, false})
    void multipleContentLengthHeaderValues(boolean crlf) {
        EmbeddedChannel channel = channel(crlf);
        String br = br(crlf);
        DecoderException e = assertThrows(DecoderException.class, () -> writeMsg(startLineForContent() + br +
                "Host: servicetalk.io" + br +
                "Content-Length: 1, 2" + br +
                "Connection: keep-alive" + br + br, channel));
        assertThat(e.getCause(), instanceOf(IllegalArgumentException.class));
        assertThat(e.getCause().getMessage(), startsWith("Multiple content-length values found"));
    }

    @ParameterizedTest(name = "{displayName} [{index}] value={0} crlf={1}")
    @MethodSource("malformedContentLengthHeaderValueSource")
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

    private static Collection<Arguments> malformedContentLengthHeaderValueSource() {
        final List<Arguments> arguments = new ArrayList<>();
        for (boolean crlf : new boolean[] {true, false}) {
            arguments.add(Arguments.of("+1", crlf)); // signed positive
            arguments.add(Arguments.of("-1", crlf)); // signed negative
            arguments.add(Arguments.of("1 2", crlf)); // malformed content length with SP
            arguments.add(Arguments.of("1a2", crlf)); // malformed content length with letter
            arguments.add(Arguments.of("1-2", crlf)); // malformed content length with symbol
        }
        return arguments;
    }

    @ParameterizedTest(name = "{displayName} [{index}] inHeader={0}")
    @ValueSource(booleans = {false, true})
    void singleHeaderLineLimit(boolean inHeader) {
        String msg = startLineForContent() + "\r\n" +
                "Host: servicetalk.io\r\n" + // 22 bytes
                (inHeader ? ("X-Header: " + repeatChar('x', DEFAULT_MAX_HEADER_FIELD_LENGTH) + "\r\n") : "") +
                "Transfer-Encoding: chunked\r\n" + // 28 bytes
                "\r\n" +
                "0\r\n" +   // empty chunk
                (inHeader ? "" : ("X-Trailer: " + repeatChar('x', DEFAULT_MAX_HEADER_FIELD_LENGTH) + "\r\n")) +
                "\r\n";

        TooLongFrameException e = assertThrows(TooLongFrameException.class, () -> writeMsg(msg));
        assertThat(e.getMessage(), is(equalTo("Could not find CRLF (0x0d0a) within 8192 bytes, while parsing line #" +
                (inHeader ? "2" : "5"))));
    }

    @ParameterizedTest(name = "{displayName} [{index}] exceed={0}")
    @ValueSource(booleans = {false, true})
    void totalHeadersLimitForHeaders(boolean exceed) {
        final String startLine = startLine();
        final int startLineLength = startLine.length() + 2;  // startLine + CRLF
        final int limit = startLineLength + 50;  // Increase limit to accommodate start line + headers
        final EmbeddedChannel testChannel = channelWithMaxTotalHeaderFieldsLength(limit);

        final int n = limit - startLineLength - 22 - 10 + (exceed ? 1 : 0); // Length of X-Test value
        String msg = startLine + "\r\n" +
                "Host: servicetalk.io\r\n" + // 22 bytes
                "X-Test: " + repeatChar('x', n) + "\r\n" + // 10 + n bytes
                "\r\n";

        if (exceed) {
            TooLongFrameException e = assertThrows(TooLongFrameException.class, () -> writeMsg(msg, testChannel));
            assertThat(e.getMessage(), startsWith("HTTP headers exceeded the total limit of " + limit +
                    " bytes after parsing line #3"));
        } else {
            writeMsg(msg, testChannel);
            HttpMetaData meta = assertStartLine(testChannel);
            CharSequence value = meta.headers().get("X-Test");
            assertThat(value, is(notNullValue()));
            assertThat(value.length(), is(n));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] exceed={0}")
    @ValueSource(booleans = {false, true})
    void totalHeadersLimitForTrailers(boolean exceed) {
        final int limit = 100;
        final EmbeddedChannel testChannel = channelWithMaxTotalHeaderFieldsLength(limit);

        final int n = limit - 13 + (exceed ? 1 : 0);
        String msg = startLineForContent() + "\r\n" +
                "Host: servicetalk.io\r\n" + // 22 bytes
                "Transfer-Encoding: chunked\r\n" + // 28 bytes
                "\r\n" +
                "0\r\n" +   // empty chunk
                "X-Trailer: " + repeatChar('x', n) + "\r\n" + // 13 + n bytes
                "\r\n";

        if (exceed) {
            TooLongFrameException e = assertThrows(TooLongFrameException.class, () -> writeMsg(msg, testChannel));
            assertThat(e.getMessage(), startsWith("HTTP trailers exceeded the total limit of " + limit +
                    " bytes after parsing line #6 of " + (limit + 1) + " bytes"));
        } else {
            writeMsg(msg, testChannel);
            HttpMetaData metaData = assertStartLineForContent(testChannel);
            assertSingleHeaderValue(metaData.headers(), TRANSFER_ENCODING, CHUNKED);
            HttpHeaders trailers = testChannel.readInbound();
            CharSequence value = trailers.get("X-Trailer");
            assertThat(value, is(notNullValue()));
            assertThat(value.length(), is(n));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] exceed={0}")
    @ValueSource(booleans = {false, true})
    void totalHeadersLimitForHeadersWithManyShortLines(boolean exceed) {
        final String startLine = startLine();
        final int startLineLength = startLine.length() + 2;  // startLine + CRLF
        final int limit = startLineLength + 100;
        EmbeddedChannel testChannel = channelWithMaxTotalHeaderFieldsLength(limit);

        // Start line + many short header lines that together may exceed the limit
        StringBuilder msg = new StringBuilder(startLine).append("\r\n");
        final int n = (limit - startLineLength) / 10 + (exceed ? 1 : 0);
        for (int i = 0; i < n; i++) {
            msg.append("X-").append(String.format("%02d", i)).append(": aa\r\n");   // 10 bytes
        }
        msg.append("\r\n");

        if (exceed) {
            TooLongFrameException e = assertThrows(TooLongFrameException.class,
                    () -> writeMsg(msg.toString(), testChannel));
            assertThat(e.getMessage(), startsWith("HTTP headers exceeded the total limit of " + limit +
                    " bytes after parsing line #12"));
        } else {
            writeMsg(msg.toString(), testChannel);
            HttpMetaData meta = assertStartLine(testChannel);
            assertThat(meta.headers().size(), is(n));
        }
    }

    @SuppressWarnings("PMD.ConsecutiveLiteralAppends")
    @ParameterizedTest(name = "{displayName} [{index}] exceed={0}")
    @ValueSource(booleans = {false, true})
    void totalHeadersLimitForTrailersWithManyShortLines(boolean exceed) {
        final int limit = 100;
        EmbeddedChannel testChannel = channelWithMaxTotalHeaderFieldsLength(limit);

        // Start line + headers + empty chunk + many short trailer lines that together may exceed the limit
        StringBuilder msg = new StringBuilder(startLineForContent()).append("\r\n")
                .append("Host: servicetalk.io\r\n") // 22 bytes
                .append("Transfer-Encoding: chunked\r\n") // 28 bytes
                .append("\r\n")
                .append("0\r\n"); // empty chunk
        final int n = limit / 10 + (exceed ? 1 : 0);
        for (int i = 0; i < n; i++) {
            msg.append("X-").append(String.format("%02d", i)).append(": aa\r\n");   // 10 bytes
        }
        msg.append("\r\n");

        if (exceed) {
            TooLongFrameException e = assertThrows(TooLongFrameException.class,
                    () -> writeMsg(msg.toString(), testChannel));
            assertThat(e.getMessage(), startsWith("HTTP trailers exceeded the total limit of " + limit +
                    " bytes after parsing line #16"));
        } else {
            writeMsg(msg.toString(), testChannel);
            HttpMetaData metaData = assertStartLineForContent(testChannel);
            assertSingleHeaderValue(metaData.headers(), TRANSFER_ENCODING, CHUNKED);
            HttpHeaders trailers = testChannel.readInbound();
            assertThat(trailers.size(), is(n));
        }
    }

    @Test
    void totalHeadersLimitResetsAfterCompleteMessage() {
        final String startLine = startLine();
        final int startLineLength = startLine.length() + 2;  // startLine + CRLF
        final int limit = startLineLength + 11;  // Just enough for start line + one header
        EmbeddedChannel testChannel = channelWithMaxTotalHeaderFieldsLength(limit);

        // First message: should be under the limit (start line + headers)
        String msg1 = startLine + "\r\n" +
                "Host: foo\r\n" + // 11 bytes
                "\r\n";

        writeMsg(msg1, testChannel);
        HttpMetaData meta1 = assertStartLine(testChannel);
        assertSingleHeaderValue(meta1.headers(), "Host", "foo");

        // Drain any trailers or other items
        while (testChannel.readInbound() != null) {
            // drain
        }

        // Second message: should succeed only if counter was reset
        String msg2 = startLine + "\r\n" +
                "Host: bar\r\n" + // 11 bytes
                "\r\n";

        writeMsg(msg2, testChannel);
        HttpMetaData meta2 = assertStartLine(testChannel);
        assertSingleHeaderValue(meta2.headers(), "Host", "bar");
    }

    @Test
    void totalHeadersLimitForHeadersIsSeparateFromTrailers() {  // checks both fit within exact limit
        final String startLine = startLineForContent();
        final int startLineLength = startLine.length() + 2;  // startLine + CRLF
        final int limit = startLineLength + 50;  // start line + headers
        EmbeddedChannel testChannel = channelWithMaxTotalHeaderFieldsLength(limit);

        // Headers length is 50 bytes
        String msg = startLine + "\r\n" +
                "Host: servicetalk.io\r\n" + // 22 bytes
                "Transfer-Encoding: chunked\r\n" + // 28 bytes
                "\r\n";

        writeMsg(msg, testChannel);
        HttpMetaData metaData = assertStartLineForContent(testChannel);
        assertSingleHeaderValue(metaData.headers(), TRANSFER_ENCODING, CHUNKED);

        // Send payload
        writeMsg("5\r\nhello\r\n0\r\n", testChannel);
        // Drain the chunk content
        while (testChannel.readInbound() != null) {
            // drain buffers
        }

        // Send trailers that fit under the same limit:
        final int n = limit - 11 - 2;
        String trailersMsg =
                "X-Trailer: " + repeatChar('t', n) + "\r\n" +  // 11 + n + 2
                "\r\n";
        writeMsg(trailersMsg, testChannel);
        HttpHeaders trailers = testChannel.readInbound();
        CharSequence value = trailers.get("X-Trailer");
        assertThat(value, is(notNullValue()));
        assertThat(value.length(), is(n));
    }

    @Test
    void totalHeadersLimitWithVerySmallValue() {
        final String startLine = startLine();
        final int startLineLength = startLine.length() + 2;  // startLine + CRLF
        int maxTotalHeaderLength = startLineLength + 5;  // Just enough for start line + minimal header
        EmbeddedChannel testChannel = channelWithMaxTotalHeaderFieldsLength(maxTotalHeaderLength);

        String msg = startLine + "\r\nHost: x\r\n\r\n";
        TooLongFrameException e = assertThrows(TooLongFrameException.class, () -> writeMsg(msg, testChannel));
        assertThat(e.getMessage(), startsWith("HTTP headers exceeded the total limit of " + maxTotalHeaderLength +
                " bytes after parsing line #2"));
    }

    @Test
    void totalHeadersLimitIncludesStartLine() {
        final String startLine = startLine();
        int maxTotalHeaderLength = startLine.length(); // Smaller than the startLine with CRLF and its default limit

        EmbeddedChannel testChannel = channelWithMaxTotalHeaderFieldsLength(maxTotalHeaderLength);

        String msg = startLine + "\r\nHost: x\r\n\r\n";
        TooLongFrameException e = assertThrows(TooLongFrameException.class, () -> writeMsg(msg, testChannel));
        assertThat(e.getMessage(), startsWith("HTTP headers exceeded the total limit of " + maxTotalHeaderLength +
                " bytes after parsing line #1"));
    }

    /**
     * Returns all possible permutations for two different booleans.
     */
    private static Collection<Arguments> biBooleanPermutationSource() {
        final List<Arguments> arguments = new ArrayList<>();
        for (boolean arg1 : new boolean[] {true, false}) {
            for (boolean arg2 : new boolean[] {true, false}) {
                arguments.add(Arguments.of(arg1, arg2));
            }
        }
        return arguments;
    }

    private static String repeatChar(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
