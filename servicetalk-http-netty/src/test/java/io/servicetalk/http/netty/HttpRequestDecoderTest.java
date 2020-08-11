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

import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import org.junit.Test;

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
import static java.lang.Integer.toHexString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class HttpRequestDecoderTest extends HttpObjectDecoderTest {

    private final EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder(new ArrayDeque<>(),
            getByteBufAllocator(DEFAULT_ALLOCATOR), DefaultHttpHeadersFactory.INSTANCE, 8192, 8192));

    @Override
    protected EmbeddedChannel channel() {
        return channel;
    }

    @Override
    String startLine() {
        return "GET / HTTP/1.1";
    }

    @Override
    HttpMetaData assertStartLine() {
        return assertRequestLine(GET, "/", HTTP_1_1);
    }

    @Override
    String startLineForContent() {
        return "POST /some/path HTTP/1.1";
    }

    @Override
    HttpMetaData assertStartLineForContent() {
        return assertRequestLine(POST, "/some/path", HTTP_1_1);
    }

    @Test
    public void illegalPrefaceCharacter() {
        assertDecoderException(' ' + startLine() + "\r\n", "Illegal character");
    }

    @Test
    public void noMethod() {
        assertDecoderException("/ HTTP/1.1" + "\r\n", "Invalid initial line");
    }

    @Test
    public void noRequestTarget() {
        assertDecoderException("GET HTTP/1.1" + "\r\n", "Invalid initial line");
    }

    @Test
    public void noVersion() {
        assertDecoderException("GET / " + "\r\n", "Invalid http version");
    }

    @Test
    public void invalidStartLineOrder() {
        assertDecoderException("GET HTTP/1.1 /" + "\r\n", "Invalid http version");
    }

    @Test
    public void illegalEndSpCharacter() {
        assertDecoderException("GET / HTTP/1.1 " + "\r\n", "Invalid http version");
    }

    @Test
    public void invalidMethodName() {
        assertDecoderException("GeT / HTTP/1.1" + "\r\n", "HTTP request method MUST contain only upper case letters");
    }

    @Test
    public void invalidMethodNameNothingElse() {
        assertDecoderException("GeT ", "HTTP request method MUST contain only upper case letters");
    }

    @Test
    public void onlyMethodName() {
        assertDecoderException("GET" + "\r\n", "Invalid initial line");
    }

    @Test
    public void invalidRequestTargetWithControlCharacter() {
        assertDecoderException("GET /\f/ HTTP/1.1" + "\r\n", "Illegal character");
    }

    @Test
    public void invalidVersionPrefix() {
        assertDecoderException("GET / HttP/1.1" + "\r\n", "Invalid http version");
    }

    @Test
    public void invalidVersionSlash() {
        assertDecoderException("GET / HTTP|1.1" + "\r\n", "Invalid http version");
    }

    @Test
    public void invalidVersionMajor() {
        assertDecoderException("GET / HTTP/5.1" + "\r\n", "Invalid http version");
    }

    @Test
    public void invalidVersionDelimiter() {
        assertDecoderException("GET / HTTP/1,1" + "\r\n", "Invalid http version");
    }

    @Test
    public void invalidVersionMinorNotNumber() {
        assertDecoderException("GET / HTTP/1.z" + "\r\n", "Illegal character");
    }

    @Test
    public void twoWsBetweenMethodAndRequestTarget() {
        assertDecoderException("GET  / HTTP/1.1" + "\r\n", "Invalid initial line");
    }

    @Test
    public void twoWsBetweenRequestTargetAndVersion() {
        assertDecoderException("GET /  HTTP/1.1" + "\r\n", "Invalid http version");
    }

    @Test
    public void validStartLineWithCustomMethod() {
        writeMsg("CUSTOM / HTTP/1.1" + "\r\n" + "\r\n");
        assertRequestLine(HttpRequestMethod.of("CUSTOM", NONE), "/", HTTP_1_1);
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void validStartLineWithShortMethod() {
        writeMsg("A / HTTP/1.1" + "\r\n" + "\r\n");
        assertRequestLine(HttpRequestMethod.of("A", NONE), "/", HTTP_1_1);
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void validStartLineWithLongerPath() {
        writeMsg("GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n" + "\r\n");
        assertRequestLine(GET, "/some/path?foo=bar&baz=yyy", HTTP_1_1);
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void validStartLineWithCustomHttpVersion() {
        writeMsg("GET / HTTP/1.9" + "\r\n" + "\r\n");
        assertRequestLine(GET, "/", HttpProtocolVersion.of(1, 9));
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void chunkedWithTrailersSplitOnNetwork() {
        int chunkLength = 128;
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
        beforeContent.add(toHexString(chunkLength));
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
        writeContent(chunkLength / 2);
        writeContent(chunkLength / 2);
        for (String msg : afterContent) {
            channel.writeInbound(fromAscii(msg));
        }
        validateWithContent(-chunkLength, true);
    }

    @Test
    public void noContentHeadersNoContent() {
        writeMsg("POST /some/path HTTP/1.1" + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Connection: keep-alive" + "\r\n" + "\r\n");

        HttpMetaData metaData = assertStartLineForContent();
        assertStandardHeaders(metaData.headers());
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void unexpectedContentAfterNoContentHeaders() {
        writeMsg("POST /some/path HTTP/1.1" + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Connection: keep-alive" + "\r\n" + "\r\n");
        // Content is not expected for requests if no "content-length" nor "transfer-encoding: chunked" is present
        DecoderException e = assertThrows(DecoderException.class, () -> writeContent(128));
        assertThat(e.getCause(), is(instanceOf(IllegalArgumentException.class)));
        assertThat(channel.inboundMessages(), is(not(empty())));
    }

    @Test
    public void unexpectedTrailersAfterNoContentHeaders() {
        writeMsg("POST /some/path HTTP/1.1" + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Connection: keep-alive" + "\r\n" + "\r\n");
        // Note that trailers are not allowed for requests without "transfer-encoding: chunked"
        // https://tools.ietf.org/html/rfc7230#section-3.3
        DecoderException e = assertThrows(DecoderException.class, () -> writeMsg("TrailerStatus: good\r\n\r\n"));
        assertThat(e.getCause(), is(instanceOf(IllegalArgumentException.class)));
        assertThat(channel.inboundMessages(), is(not(empty())));
    }

    private HttpRequestMetaData assertRequestLine(HttpRequestMethod expectedMethod, String expectedRequestTarget,
                                                  HttpProtocolVersion expectedVersion) {
        HttpRequestMetaData request = channel.readInbound();
        assertThat(request.method(), equalTo(expectedMethod));
        assertThat(request.requestTarget(), equalTo(expectedRequestTarget));
        assertThat(request.version(), equalTo(expectedVersion));
        return request;
    }

    @Test
    public void smuggleBeforeNonZeroContentLengthHeader() {
        int contentLength = 128;
        DecoderException e = assertThrows(DecoderException.class, () -> writeMsg(startLineForContent() + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                // Smuggled requests injected into a header will terminate the current request due to valid \r\n\r\n
                // framing terminating the request with no content-length or transfer-encoding, or with known zero
                // content-length [1].
                // [1] https://tools.ietf.org/html/rfc7230#section-3.3.3
                "Smuggled: " + startLine() + "\r\n\r\n" +
                "Content-Length: " + contentLength + "\r\n" +
                "Connection: keep-alive" + "\r\n\r\n"));
        assertThat(e.getCause(), is(instanceOf(IllegalArgumentException.class)));

        HttpMetaData metaData = assertStartLineForContent();
        assertSingleHeaderValue(metaData.headers(), HOST, "servicetalk.io");
        assertSingleHeaderValue(metaData.headers(), "Smuggled", startLine());
        assertEmptyTrailers(channel());
    }

    @Test
    public void smuggleBeforeTransferEncodingHeader() {
        smuggleTransferEncoding(true);
    }
}
