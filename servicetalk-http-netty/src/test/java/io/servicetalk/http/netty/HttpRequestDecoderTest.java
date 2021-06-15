/*
 * Copyright © 2018, 2020, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferUtils.getByteBufAllocator;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpRequestMethod.Properties.NONE;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static java.lang.Integer.toHexString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpRequestDecoderTest extends HttpObjectDecoderTest {

    private final EmbeddedChannel channel = newChannel(false);
    private final EmbeddedChannel channelSpecException = newChannel(true);

    private static EmbeddedChannel newChannel(boolean allowLFWithoutCR) {
        return new EmbeddedChannel(new HttpRequestDecoder(new ArrayDeque<>(), getByteBufAllocator(DEFAULT_ALLOCATOR),
                DefaultHttpHeadersFactory.INSTANCE, 8192, 8192, false, allowLFWithoutCR,
                UNSUPPORTED_PROTOCOL_CLOSE_HANDLER));
    }

    @Override
    EmbeddedChannel channel() {
        return channel;
    }

    @Override
    EmbeddedChannel channelSpecException() {
        return channelSpecException;
    }

    @Override
    String startLine() {
        return "GET / HTTP/1.1";
    }

    @Override
    HttpMetaData assertStartLine(EmbeddedChannel channel) {
        return assertRequestLine(GET, "/", HTTP_1_1, channel);
    }

    @Override
    String startLineForContent() {
        return "POST /some/path HTTP/1.1";
    }

    @Override
    HttpMetaData assertStartLineForContent(final EmbeddedChannel channel) {
        return assertRequestLine(POST, "/some/path", HTTP_1_1, channel);
    }

    @Test
    void illegalPrefaceCharacter() {
        assertDecoderExceptionWithCause(' ' + startLine() + "\r\n", "Invalid preface character");
    }

    @Test
    void noMethod() {
        assertDecoderException("/ HTTP/1.1" + "\r\n", "Invalid start-line");
    }

    @Test
    void noRequestTarget() {
        assertDecoderException("GET HTTP/1.1" + "\r\n", "Invalid start-line");
    }

    @Test
    void noVersion() {
        assertDecoderException("GET / " + "\r\n", "Invalid HTTP version");
    }

    @Test
    void invalidStartLineOrder() {
        assertDecoderException("GET HTTP/1.1 /" + "\r\n", "Invalid HTTP version");
    }

    @Test
    void illegalEndSpCharacter() {
        assertDecoderException("GET / HTTP/1.1 " + "\r\n", "Invalid HTTP version");
    }

    @Test
    void invalidMethodName() {
        assertDecoderExceptionWithCause("GeT / HTTP/1.1" + "\r\n",
                "Invalid start-line: HTTP request method must contain only upper case letters");
    }

    @Test
    void invalidMethodNameNothingElse() {
        assertDecoderExceptionWithCause("GeT ",
                "Invalid start-line: HTTP request method must contain only upper case letters");
    }

    @Test
    void onlyMethodName() {
        assertDecoderException("GET" + "\r\n", "Invalid start-line");
    }

    @Test
    void invalidRequestTargetWithControlCharacter() {
        assertDecoderExceptionWithCause("GET /\f/ HTTP/1.1" + "\r\n", "Invalid start-line: HTTP request-target");
    }

    @Test
    void invalidVersionPrefix() {
        assertDecoderException("GET / HttP/1.1" + "\r\n", "Invalid HTTP version");
    }

    @Test
    void invalidVersionSlash() {
        assertDecoderException("GET / HTTP|1.1" + "\r\n", "Invalid HTTP version");
    }

    @Test
    void invalidVersionMajor() {
        assertDecoderException("GET / HTTP/5.1" + "\r\n", "Invalid HTTP version");
    }

    @Test
    void invalidVersionDelimiter() {
        assertDecoderException("GET / HTTP/1,1" + "\r\n", "Invalid HTTP version");
    }

    @Test
    void invalidVersionMinorNotNumber() {
        assertDecoderExceptionWithCause("GET / HTTP/1.z" + "\r\n", "Invalid HTTP version");
    }

    @Test
    void twoWsBetweenMethodAndRequestTarget() {
        assertDecoderException("GET  / HTTP/1.1" + "\r\n", "Invalid start-line");
    }

    @Test
    void twoWsBetweenRequestTargetAndVersion() {
        assertDecoderException("GET /  HTTP/1.1" + "\r\n", "Invalid HTTP version");
    }

    @Test
    void validStartLineWithCustomMethod() {
        writeMsg("CUSTOM / HTTP/1.1" + "\r\n" + "\r\n");
        assertRequestLine(HttpRequestMethod.of("CUSTOM", NONE), "/", HTTP_1_1);
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    void validStartLineWithShortMethod() {
        writeMsg("A / HTTP/1.1" + "\r\n" + "\r\n");
        assertRequestLine(HttpRequestMethod.of("A", NONE), "/", HTTP_1_1);
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    void validStartLineWithLongerPath() {
        writeMsg("GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" + "\r\n");
        assertRequestLine(GET, "/some/path?foo=bar&baz=yyy", HTTP_1_1);
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    void validStartLineWithCustomHttpVersion() {
        writeMsg("GET / HTTP/1.9" + "\r\n" + "\r\n");
        assertRequestLine(GET, "/", HttpProtocolVersion.of(1, 9));
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    void chunkedWithTrailersSplitOnNetwork() {
        int chunkSize = 128;
        List<String> beforeContent = new ArrayList<>();
        beforeContent.add("POST /so");
        beforeContent.add("me/path");
        beforeContent.add(" HTTP/1.1\r");
        beforeContent.add("\nC");
        beforeContent.add("onnection");
        beforeContent.add(":");
        beforeContent.add(" keep-alive\r");
        beforeContent.add("\n");
        beforeContent.add("\n");
        beforeContent.add("Host: ");
        beforeContent.add("service");
        beforeContent.add("talk.io");
        beforeContent.add("\r\n");
        beforeContent.add("Transfer-Encoding: chunked\r\n");
        beforeContent.add("\r");
        beforeContent.add("\n");
        beforeContent.add(toHexString(chunkSize));
        beforeContent.add(";");
        beforeContent.add("\r");
        beforeContent.add("\n");
        List<String> afterContent = new ArrayList<>();
        afterContent.add("\r");
        afterContent.add("\n");
        afterContent.add("0");
        afterContent.add("\r");
        afterContent.add("\n");
        afterContent.add("TrailerStatus");
        afterContent.add(": good");
        afterContent.add("\r");
        afterContent.add("\n");
        afterContent.add("\r");
        afterContent.add("\n");
        for (String msg : beforeContent) {
            channel.writeInbound(fromAscii(msg));
        }
        // Write single chunk on two writes
        writeContent(chunkSize / 2);
        writeContent(chunkSize / 2);
        for (String msg : afterContent) {
            channel.writeInbound(fromAscii(msg));
        }
        validateWithContent(-chunkSize, true);
    }

    @Test
    void noContentHeadersNoContent() {
        writeMsg("POST /some/path HTTP/1.1" + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Connection: keep-alive" + "\r\n" + "\r\n");

        HttpMetaData metaData = assertStartLineForContent();
        assertStandardHeaders(metaData.headers());
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    void unexpectedContentAfterNoContentHeaders() {
        writeMsg("POST /some/path HTTP/1.1" + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Connection: keep-alive" + "\r\n" + "\r\n");
        // Content is not expected for requests if no "content-length" nor "transfer-encoding: chunked" is present
        assertThrows(DecoderException.class, () -> writeContent(128));
        assertThat(channel.inboundMessages(), is(not(empty())));
    }

    @Test
    void unexpectedTrailersAfterNoContentHeaders() {
        writeMsg("POST /some/path HTTP/1.1" + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Connection: keep-alive" + "\r\n" + "\r\n");
        // Note that trailers are not allowed for requests without "transfer-encoding: chunked"
        // https://tools.ietf.org/html/rfc7230#section-3.3
        DecoderException e = assertThrows(DecoderException.class, () -> writeMsg("TrailerStatus: good\r\n\r\n"));
        assertThat(e.getMessage(), startsWith("Invalid start-line"));
        assertThat(channel.inboundMessages(), is(not(empty())));
    }

    private HttpRequestMetaData assertRequestLine(HttpRequestMethod expectedMethod, String expectedRequestTarget,
                                                  HttpProtocolVersion expectedVersion) {
        return assertRequestLine(expectedMethod, expectedRequestTarget, expectedVersion, channel());
    }

    private static HttpRequestMetaData assertRequestLine(HttpRequestMethod expectedMethod, String expectedRequestTarget,
                                                         HttpProtocolVersion expectedVersion, EmbeddedChannel channel) {
        HttpRequestMetaData request = channel.readInbound();
        assertThat(request.method(), equalTo(expectedMethod));
        assertThat(request.requestTarget(), equalTo(expectedRequestTarget));
        assertThat(request.version(), equalTo(expectedVersion));
        return request;
    }

    @Test
    void smuggleBeforeNonZeroContentLengthHeader() {
        int contentLength = 128;
        assertThrows(DecoderException.class, () -> writeMsg(startLineForContent() + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                // Smuggled requests injected into a header will terminate the current request due to valid \r\n\r\n
                // framing terminating the request with no content-length or transfer-encoding, or with known zero
                // content-length [1].
                // [1] https://tools.ietf.org/html/rfc7230#section-3.3.3
                "Smuggled: " + startLine() + "\r\n\r\n" +
                "Content-Length: " + contentLength + "\r\n" +
                "Connection: keep-alive" + "\r\n\r\n"));

        HttpMetaData metaData = assertStartLineForContent();
        assertSingleHeaderValue(metaData.headers(), HOST, "servicetalk.io");
        assertSingleHeaderValue(metaData.headers(), "Smuggled", startLine());
        assertEmptyTrailers(channel());
    }

    @Test
    void smuggleBeforeTransferEncodingHeader() {
        smuggleTransferEncoding(true, false);
        smuggleTransferEncoding(true, true);
    }
}
