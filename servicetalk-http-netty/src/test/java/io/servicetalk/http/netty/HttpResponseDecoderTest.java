/*
 * Copyright © 2018, 2020 Apple Inc. and the ServiceTalk project authors
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
/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferUtils.getByteBufAllocator;
import static io.servicetalk.http.api.HeaderUtils.isTransferEncodingChunked;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpResponseStatus.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static java.lang.Integer.toHexString;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;

public class HttpResponseDecoderTest extends HttpObjectDecoderTest {
    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    private final Queue<HttpRequestMethod> methodQueue = new ArrayDeque<>();

    private final EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseDecoder(methodQueue,
            getByteBufAllocator(DEFAULT_ALLOCATOR), DefaultHttpHeadersFactory.INSTANCE, 8192, 8192));

    @Override
    protected EmbeddedChannel channel() {
        return channel;
    }

    @Override
    String startLine() {
        return "HTTP/1.1 204 No Content";
    }

    @Override
    HttpMetaData assertStartLine() {
        return assertResponseLine(HTTP_1_1, NO_CONTENT);
    }

    @Override
    String startLineForContent() {
        return "HTTP/1.1 200 OK";
    }

    @Override
    HttpMetaData assertStartLineForContent() {
        return assertResponseLine(HTTP_1_1, OK);
    }

    @Test
    public void illegalPrefaceCharacter() {
        assertDecoderExceptionWithCause(" HTTP/1.1 200 OK" + "\r\n", "Invalid preface character");
    }

    @Test
    public void noVersion() {
        assertDecoderException("200 OK" + "\r\n", "Invalid start-line");
    }

    @Test
    public void noStatusCode() {
        assertDecoderException("HTTP/1.1 OK" + "\r\n", "Invalid start-line");
    }

    @Test
    public void noSpAfterStatusCode() {
        assertDecoderException("HTTP/1.1 200" + "\r\n", "Invalid start-line");
    }

    @Test
    public void invalidStartLineOrder() {
        assertDecoderException("HTTP/1.1 OK 200" + "\r\n", "Invalid start-line");
    }

    @Test
    public void onlyVersion() {
        assertDecoderException("HTTP/1.1" + "\r\n", "Invalid start-line");
    }

    @Test
    public void invalidVersionPrefixOnly() {
        assertDecoderExceptionWithCause("HttP", "Invalid start-line");
    }

    @Test
    public void invalidVersionPrefix() {
        assertDecoderException("HttP/1.1 200 OK" + "\r\n", "Invalid HTTP version");
    }

    @Test
    public void invalidVersionSlash() {
        assertDecoderException("HTTP|1.1 200 OK" + "\r\n", "Invalid HTTP version");
    }

    @Test
    public void invalidVersionMajor() {
        assertDecoderException("HTTP/5.1 200 OK" + "\r\n", "Invalid HTTP version");
    }

    @Test
    public void invalidVersionDelimiter() {
        assertDecoderException("HTTP/1,1 200 OK" + "\r\n", "Invalid HTTP version");
    }

    @Test
    public void invalidVersionMinorNotNumber() {
        assertDecoderExceptionWithCause("HTTP/1.z 200 OK" + "\r\n", "Invalid HTTP version");
    }

    @Test
    public void twoWsBetweenVersionAndStatusCode() {
        assertDecoderException("HTTP/1.1  200 OK" + "\r\n", "Invalid start-line");
    }

    @Test
    public void invalidStatusCodeLessThan3digitInteger() {
        assertDecoderException("HTTP/1.1 20 OK" + "\r\n", "Invalid start-line");
    }

    @Test
    public void invalidStatusCodeMoreThan3digitInteger() {
        assertDecoderException("HTTP/1.1 2000 OK" + "\r\n", "Invalid start-line");
    }

    @Test
    public void invalidStatusCodeNonInteger() {
        assertDecoderExceptionWithCause("HTTP/1.1 20K OK" + "\r\n",
                "Invalid start-line: HTTP status-code must contain only 3 digits");
    }

    @Test
    public void invalidStatusCodeWithControlCharacter() {
        assertDecoderExceptionWithCause("HTTP/1.1 20\0 OK" + "\r\n",
                "Invalid start-line: HTTP status-code must contain only 3 digits");
    }

    @Test
    public void invalidReasonPhraseWithControlCharacter() {
        assertDecoderExceptionWithCause("HTTP/1.1 200 O\fK" + "\r\n", "Invalid start-line");
    }

    @Test
    public void validStartLineWithCustomHttpVersion() {
        writeMsg("HTTP/1.9 204 No Content" + "\r\n" + "\r\n");
        assertResponseLine(HttpProtocolVersion.of(1, 9), NO_CONTENT);
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void emptyReasonPhrase() {
        testReasonPhrase("");
    }

    @Test
    public void emptyReasonPhraseWith3Ws() {
        testReasonPhrase("   ");
    }

    @Test
    public void reasonPhraseWithLeadingWs() {
        testReasonPhrase("   No Content");
    }

    @Test
    public void reasonPhraseWithTrailingWs() {
        testReasonPhrase("No Content   ");
    }

    @Test
    public void reasonPhraseWithLeadingAndTrailingWs() {
        testReasonPhrase("   No Content   ");
    }

    @Test
    public void reasonPhraseWithHtab() {
        testReasonPhrase("No\tContent");
    }

    @Test
    public void reasonPhraseWithObsText() {
        testReasonPhrase("Ñó Cóñtêñt");
    }

    private void testReasonPhrase(String reasonPhrase) {
        writeMsg("HTTP/1.1 204 " + reasonPhrase + "\r\n" + "\r\n");
        assertResponseLine(HTTP_1_1, HttpResponseStatus.of(204, reasonPhrase));
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void chunkedWithTrailersSplitOnNetwork() {
        int chunkSize = 128;
        List<String> beforeContent = new ArrayList<>();
        beforeContent.add("HTTP/");
        beforeContent.add("1.1 ");
        beforeContent.add("200 OK\r");
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
    public void variableEmptyContent() {
        writeMsg("HTTP/1.1 200 OK" + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Connection: keep-alive" + "\r\n" + "\r\n");

        // For a response, the variable length content is considered "complete" when the channel is closed.
        channel.close();

        HttpMetaData metaData = assertStartLineForContent();
        assertStandardHeaders(metaData.headers());
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void variableWithContent() {
        int contentLength = 128;
        writeMsg("HTTP/1.1 200 OK" + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Connection: keep-alive" + "\r\n" + "\r\n");
        writeContent(contentLength);

        // For a response, the variable length content is considered "complete" when the channel is closed.
        channel.close();

        HttpMetaData metaData = assertStartLineForContent();
        assertStandardHeaders(metaData.headers());
        assertThat(metaData.headers().get(CONTENT_LENGTH), is(nullValue()));
        Buffer chunk = channel().readInbound();
        assertThat(chunk.readableBytes(), is(contentLength));
        assertEmptyTrailers(channel());
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void variableWithChunkedContentAndTrailers() {
        int chunkSize = 128;
        writeMsg("HTTP/1.1 200 OK" + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Connection: keep-alive" + "\r\n" + "\r\n");

        // Note that trailers are only allowed when chunked encoding is used. So the trailers in this case are
        // considered part of the payload (even the \r\n), and the response is terminated when the channel is closed.
        // https://tools.ietf.org/html/rfc7230.html#section-4.1
        writeChunk(chunkSize);
        writeChunk(0);
        String trailersPart = "TrailerStatus: good" + "\r\n";
        writeMsg(trailersPart);
        writeMsg("\r\n");

        // For a response, the variable length content is considered "complete" when the channel is closed.
        channel.close();

        // In this case, content is parsed without taking "chunked" encoding into account.
        int expectedContentLength = 2 /* chunk-size */ + 2 /* CRLF */ + chunkSize + 2 /* CRLF */ + 3 /* last-chunk */ +
                trailersPart.length() + 2 /* CRLF */;
        HttpMetaData metaData = assertStartLineForContent();
        assertStandardHeaders(metaData.headers());
        assertThat(isTransferEncodingChunked(metaData.headers()), is(false));
        HttpHeaders trailers = assertPayloadSize(expectedContentLength);
        assertThat("Trailers are not empty", trailers.isEmpty(), is(true));
        assertFalse(channel.finishAndReleaseAll());
    }

    private HttpResponseMetaData assertResponseLine(HttpProtocolVersion expectedVersion,
                                                    HttpResponseStatus expectedStatus) {
        HttpResponseMetaData response = channel.readInbound();
        assertThat(response.version(), equalTo(expectedVersion));
        assertThat(response.status().code(), is(expectedStatus.code()));
        assertThat(response.status().reasonPhrase(), equalTo(expectedStatus.reasonPhrase()));
        return response;
    }

    @Test
    public void smuggleBeforeNonZeroContentLengthHeader() {
        int contentLength = 128;
        writeMsg(startLineForContent() + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                // If a Transfer-Encoding header field is present in a response and
                // the chunked transfer coding is not the final encoding, the
                // message body length is determined by reading the connection until
                // it is closed by the server [1].
                // [1] https://tools.ietf.org/html/rfc7230#section-3.3.3
                "Smuggled: " + startLine() + "\r\n\r\n" +
                "Content-Length: " + contentLength + "\r\n" +
                "Connection: keep-alive" + "\r\n\r\n");

        HttpMetaData metaData = assertStartLineForContent();
        assertSingleHeaderValue(metaData.headers(), HOST, "servicetalk.io");
        assertSingleHeaderValue(metaData.headers(), "Smuggled", startLine());
        Buffer buffer = channel().readInbound();
        assertThat(buffer.toString(US_ASCII), is("Content-Length: " + contentLength +
                "\r\nConnection: keep-alive\r\n\r\n"));
        channel().close();
        assertEmptyTrailers(channel());
        assertFalse(channel().finishAndReleaseAll());
    }

    @Test
    public void smuggleBeforeTransferEncodingHeader() {
        writeMsg(startLineForContent() + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                // Otherwise, this is a response message without a declared message
                // body length, so the message body length is determined by the
                // number of octets received prior to the server closing the
                // connection [1].
                // [1] https://tools.ietf.org/html/rfc7230#section-3.3.3
                "Smuggled: " + startLine() + "\r\n\r\n" +
                TRANSFER_ENCODING + ": " + CHUNKED + "\r\n" +
                "Connection: keep-alive" + "\r\n\r\n");

        HttpMetaData metaData = assertStartLineForContent();
        assertSingleHeaderValue(metaData.headers(), HOST, "servicetalk.io");
        assertSingleHeaderValue(metaData.headers(), "Smuggled", startLine());
        Buffer buffer = channel().readInbound();
        assertThat(buffer.toString(US_ASCII), is(TRANSFER_ENCODING + ": " + CHUNKED +
                "\r\nConnection: keep-alive\r\n\r\n"));
        channel().close();
        assertEmptyTrailers(channel());
        assertFalse(channel().finishAndReleaseAll());
    }

    @Test
    public void successfulResponseToConnectRequest() {
        methodQueue.add(CONNECT);
        writeMsg(startLineForContent() + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Connection: keep-alive" + "\r\n\r\n");

        assertStartLineForContent();
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void successfulResponseToConnectRequestWithPayloadBodyFails() {
        methodQueue.add(CONNECT);
        String content = "content";
        writeMsg(startLineForContent() + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Content-Length: " + content.length() + "\r\n" +
                "Connection: keep-alive" + "\r\n\r\n");

        // Verify response meta-data received and parsed as a full response:
        assertStartLineForContent();
        assertEmptyTrailers(channel);

        // Try to write response content:
        assertDecoderException(content, "Invalid start-line");
    }

    @Test
    public void errorResponseToConnectRequestWithEmptyContent() {
        methodQueue.add(CONNECT);
        writeMsg("HTTP/1.1 400 Bad Request" + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Content-Length: 0" + "\r\n" +
                "Connection: keep-alive" + "\r\n\r\n");
        assertResponseLine(HTTP_1_1, BAD_REQUEST);
        assertEmptyTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void errorResponseToConnectRequestWithContent() {
        methodQueue.add(CONNECT);
        int contentLength = 128;
        writeMsg("HTTP/1.1 400 Bad Request" + "\r\n" +
                "Host: servicetalk.io" + "\r\n" +
                "Content-Length: " + contentLength + "\r\n" +
                "Connection: keep-alive" + "\r\n\r\n");
        writeContent(contentLength);
        assertResponseLine(HTTP_1_1, BAD_REQUEST);
        HttpHeaders trailers = assertPayloadSize(contentLength);
        assertThat("Trailers are not empty", trailers.isEmpty(), is(true));
        assertFalse(channel.finishAndReleaseAll());
    }
}
