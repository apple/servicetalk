/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopStreamObserver;

import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2Headers;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.function.Consumer;

import static io.netty.buffer.ByteBufUtil.writeAscii;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpMethod.PUT;
import static io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.servicetalk.buffer.api.Matchers.contentEqualTo;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HeaderUtils.isTransferEncodingChunked;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequest;
import static io.servicetalk.transport.netty.internal.CloseHandler.forNonPipelined;
import static java.lang.String.valueOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class AbstractH2DuplexHandlerTest {

    private static final HttpHeadersFactory HEADERS_FACTORY = DefaultHttpHeadersFactory.INSTANCE;

    private enum Variant {

        CLIENT_HANDLER {
            @Override
            ChannelDuplexHandler handler(ChannelConfig config) {
                return new H2ToStH1ClientDuplexHandler(false, DEFAULT_ALLOCATOR,
                        HEADERS_FACTORY, forNonPipelined(true, config), NoopStreamObserver.INSTANCE);
            }

            @Override
            void writeOutbound(EmbeddedChannel channel) {
                channel.writeOutbound(newRequest(GET, "/", HTTP_2_0, HEADERS_FACTORY.newHeaders(),
                        DEFAULT_ALLOCATOR, HEADERS_FACTORY), true);
            }

            @Override
            Http2Headers setHeaders(Http2Headers headers) {
                return headers.status(OK.codeAsText());
            }
        },
        SERVER_HANDLER {
            @Override
            ChannelDuplexHandler handler(ChannelConfig config) {
                return new H2ToStH1ServerDuplexHandler(DEFAULT_ALLOCATOR, HEADERS_FACTORY,
                        forNonPipelined(false, config), NoopStreamObserver.INSTANCE);
            }

            @Override
            void writeOutbound(EmbeddedChannel channel) {
                // noop
            }

            @Override
            Http2Headers setHeaders(Http2Headers headers) {
                return headers.method(PUT.name()).path("/");
            }
        };

        abstract ChannelDuplexHandler handler(ChannelConfig config);

        abstract void writeOutbound(EmbeddedChannel channel);

        abstract Http2Headers setHeaders(Http2Headers headers);
    }

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final EmbeddedChannel channel = new EmbeddedChannel();
    private final Variant variant;

    public AbstractH2DuplexHandlerTest(Variant variant) {
        this.variant = variant;
        channel.pipeline().addLast(variant.handler(channel.config()));
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (channel.isOpen()) {
                channel.close().get();
            }
        } finally {
            channel.releaseInbound();
            channel.releaseOutbound();
        }
    }

    @Parameterized.Parameters(name = "variant = {0}")
    public static Variant[] data() {
        return Variant.values();
    }

    @Test
    public void multipleContentLengthHeaders() {
        final Consumer<Http2Headers> initHeaders = headers -> headers.add(CONTENT_LENGTH, "1", "2");
        multipleContentLength(initHeaders, false);
    }

    @Test
    public void multipleContentLengthHeadersEndStream() {
        final Consumer<Http2Headers> initHeaders = headers -> headers.add(CONTENT_LENGTH, "1", "2");
        multipleContentLength(initHeaders, true);
    }

    @Test
    public void multipleContentLengthHeaderValues() {
        final Consumer<Http2Headers> initHeaders = headers -> headers.add(CONTENT_LENGTH, "1, 2");
        multipleContentLength(initHeaders, false);
    }

    @Test
    public void multipleContentLengthHeaderValuesEndStream() {
        final Consumer<Http2Headers> initHeaders = headers -> headers.add(CONTENT_LENGTH, "1, 2");
        multipleContentLength(initHeaders, true);
    }

    private void multipleContentLength(Consumer<Http2Headers> initHeaders, boolean endStream) {
        variant.writeOutbound(channel);

        Http2Headers headers = variant.setHeaders(new DefaultHttp2Headers());
        initHeaders.accept(headers);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> channel.writeInbound(new DefaultHttp2HeadersFrame(headers, endStream)));
        assertThat(e.getMessage(), startsWith("Multiple content-length values found"));
    }

    @Test
    public void unexpectedContentLength() {
        unexpectedContentLength(false);
    }

    @Test
    public void unexpectedContentLengthEndStream() {
        unexpectedContentLength(true);
    }

    private void unexpectedContentLength(boolean endStream) {
        variant.writeOutbound(channel);

        Http2Headers headers = new DefaultHttp2Headers();
        switch (variant) {
            case CLIENT_HANDLER:
                headers.status(NO_CONTENT.codeAsText());
                break;
            case SERVER_HANDLER:
                headers.method(HttpMethod.TRACE.asciiName()).path("/");
                break;
            default:
                throw new Error();
        }
        headers.setInt(CONTENT_LENGTH, 1);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> channel.writeInbound(new DefaultHttp2HeadersFrame(headers, endStream)));
        assertThat(e.getMessage(), startsWith("content-length (1) header is not expected"));
    }

    @Test
    public void responseWithContentLengthToHeadRequest() {
        responseWithContentLengthToHeadRequest(false);
    }

    @Test
    public void responseWithContentLengthToHeadRequestEndStream() {
        responseWithContentLengthToHeadRequest(true);
    }

    private void responseWithContentLengthToHeadRequest(boolean endStream) {
        assumeTrue("Only relevant for the client-side", variant == Variant.CLIENT_HANDLER);
        int contentLength = 1;
        // Send HEAD request
        channel.writeOutbound(newRequest(HEAD, "/", HTTP_2_0, HEADERS_FACTORY.newHeaders(),
                DEFAULT_ALLOCATOR, HEADERS_FACTORY), true);
        // Prepare server response with content-length header:
        Http2Headers headers = new DefaultHttp2Headers();
        headers.status(OK.codeAsText());
        headers.setInt(CONTENT_LENGTH, contentLength);
        channel.writeInbound(new DefaultHttp2HeadersFrame(headers, endStream));

        HttpMetaData metaData = channel.readInbound();
        assertThat(metaData.headers().get(CONTENT_LENGTH), contentEqualTo(valueOf(contentLength)));
        if (endStream) {
            HttpHeaders trailers = channel.readInbound();
            assertThat(trailers.isEmpty(), is(true));
        } else {
            // No more items at this moment:
            assertThat(channel.inboundMessages(), is(empty()));
            channel.writeInbound(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers(), true));
            HttpHeaders trailers = channel.readInbound();
            assertThat(trailers.isEmpty(), is(true));
        }
        assertThat(channel.inboundMessages(), is(empty()));
    }

    @Test
    public void noContentLength() {
        noContentLength(false);
    }

    @Test
    public void noContentLengthEndStream() {
        noContentLength(true);
    }

    private void noContentLength(boolean endStream) {
        variant.writeOutbound(channel);

        Http2Headers headers = variant.setHeaders(new DefaultHttp2Headers());
        channel.writeInbound(new DefaultHttp2HeadersFrame(headers, endStream));

        HttpMetaData metaData = channel.readInbound();
        if (endStream) {
            assertThat(metaData.headers().contains(CONTENT_LENGTH), is(true));
        } else {
            assertThat(isTransferEncodingChunked(metaData.headers()), is(true));
        }
    }

    @Test
    public void withContentLength() {
        withContentLength(false);
    }

    @Test
    public void withContentLengthAndTrailers() {
        withContentLength(true);
    }

    private void withContentLength(boolean addTrailers) {
        variant.writeOutbound(channel);
        String content = "hello";

        Http2Headers headers = variant.setHeaders(new DefaultHttp2Headers());
        headers.setInt(CONTENT_LENGTH, content.length());
        channel.writeInbound(new DefaultHttp2HeadersFrame(headers));

        HttpMetaData metaData = channel.readInbound();
        assertThat(metaData.headers().get(CONTENT_LENGTH), contentEqualTo(valueOf(content.length())));

        channel.writeInbound(new DefaultHttp2DataFrame(writeAscii(UnpooledByteBufAllocator.DEFAULT, content),
                !addTrailers));
        Buffer buffer = channel.readInbound();
        assertThat(buffer, is(equalTo(DEFAULT_ALLOCATOR.fromAscii(content))));

        if (addTrailers) {
            channel.writeInbound(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers().set("trailer", "value"), true));
        }
        HttpHeaders trailers = channel.readInbound();
        assertThat(trailers.isEmpty(), is(!addTrailers));
        assertThat(channel.inboundMessages(), is(empty()));
    }

    @Test
    public void singleHeadersFrameWithContentLength() {
        variant.writeOutbound(channel);

        Http2Headers headers = variant.setHeaders(new DefaultHttp2Headers());
        headers.setInt(CONTENT_LENGTH, 1);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> channel.writeInbound(new DefaultHttp2HeadersFrame(headers, true)));
        assertThat(e.getMessage(), containsString("not equal to the actual length"));
    }

    @Test
    public void singleHeadersFrameWithZeroContentLength() {
        variant.writeOutbound(channel);

        Http2Headers headers = variant.setHeaders(new DefaultHttp2Headers());
        headers.setInt(CONTENT_LENGTH, 0);
        channel.writeInbound(new DefaultHttp2HeadersFrame(headers, true));

        HttpMetaData metaData = channel.readInbound();
        assertThat(metaData.headers().get(CONTENT_LENGTH), contentEqualTo(valueOf(0)));

        HttpHeaders trailers = channel.readInbound();
        assertThat(trailers.isEmpty(), is(true));
        assertThat(channel.inboundMessages(), is(empty()));
    }

    @Test
    public void lessThanActual() {
        invalidContentLength(3, "hello", false);
    }

    @Test
    public void lessThanActualWithTrailers() {
        invalidContentLength(3, "hello", true);
    }

    @Test
    public void notEqualToActualLength() {
        invalidContentLength(10, "hello", false);
    }

    @Test
    public void notEqualToActualLengthWithTrailers() {
        invalidContentLength(10, "hello", true);
    }

    private void invalidContentLength(int contentLength, String content, boolean addTrailers) {
        variant.writeOutbound(channel);

        Http2Headers headers = variant.setHeaders(new DefaultHttp2Headers());
        headers.setInt(CONTENT_LENGTH, contentLength);
        channel.writeInbound(new DefaultHttp2HeadersFrame(headers));

        HttpMetaData metaData = channel.readInbound();
        assertThat(metaData.headers().get(CONTENT_LENGTH), contentEqualTo(valueOf(contentLength)));

        final IllegalArgumentException e;
        if (addTrailers) {
            if (contentLength < content.length()) {
                e = assertThrows(IllegalArgumentException.class, () -> channel.writeInbound(new DefaultHttp2DataFrame(
                        writeAscii(UnpooledByteBufAllocator.DEFAULT, content))));
            } else {
                channel.writeInbound(new DefaultHttp2DataFrame(writeAscii(UnpooledByteBufAllocator.DEFAULT, content)));
                Buffer buffer = channel.readInbound();
                assertThat(buffer, is(equalTo(DEFAULT_ALLOCATOR.fromAscii(content))));

                e = assertThrows(IllegalArgumentException.class, () -> channel.writeInbound(
                        new DefaultHttp2HeadersFrame(new DefaultHttp2Headers().set("trailer", "value"), true)));
            }
        } else {
            e = assertThrows(IllegalArgumentException.class, () -> channel.writeInbound(new DefaultHttp2DataFrame(
                    writeAscii(UnpooledByteBufAllocator.DEFAULT, content), true)));
        }
        assertThat(e.getMessage(), containsString("not equal to the actual length"));
    }
}
