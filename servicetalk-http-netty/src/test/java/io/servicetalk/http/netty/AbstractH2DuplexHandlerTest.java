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
import io.servicetalk.http.api.EmptyHttpHeaders;
import io.servicetalk.http.api.Http2Exception;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopStreamObserver;

import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static io.netty.buffer.ByteBufUtil.writeAscii;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpMethod.PUT;
import static io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.buffer.api.Matchers.contentEqualTo;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.HeaderUtils.isTransferEncodingChunked;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequest;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponse;
import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractH2DuplexHandlerTest {

    private static final HttpHeadersFactory HEADERS_FACTORY = H2HeadersFactory.INSTANCE;

    private enum Variant {

        CLIENT_HANDLER {
            @Override
            ChannelDuplexHandler handler(CloseHandler closeHandler) {
                return new H2ToStH1ClientDuplexHandler(false, DEFAULT_ALLOCATOR, HEADERS_FACTORY,
                        closeHandler, NoopStreamObserver.INSTANCE);
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
            ChannelDuplexHandler handler(CloseHandler closeHandler) {
                return new H2ToStH1ServerDuplexHandler(DEFAULT_ALLOCATOR, HEADERS_FACTORY, closeHandler,
                        NoopStreamObserver.INSTANCE);
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

        abstract ChannelDuplexHandler handler(CloseHandler closeHandler);

        abstract void writeOutbound(EmbeddedChannel channel);

        abstract Http2Headers setHeaders(Http2Headers headers);
    }

    private final EmbeddedChannel channel = new EmbeddedHttp2StreamChannel();
    private final CloseHandler closeHandler = mock(CloseHandler.class);

    void setUp(Variant variant) {
        channel.pipeline().addLast(variant.handler(closeHandler));
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (channel.isOpen()) {
                channel.close().get();
            }
        } finally {
            channel.releaseInbound();
            channel.releaseOutbound();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] variant={0}")
    @EnumSource(Variant.class)
    void unexpectedContentLength(Variant variant) {
        setUp(variant);
        unexpectedContentLength(variant, false);
    }

    @ParameterizedTest(name = "{displayName} [{index}] variant={0}")
    @EnumSource(Variant.class)
    void unexpectedContentLengthEndStream(Variant variant) {
        setUp(variant);
        unexpectedContentLength(variant, true);
    }

    private void unexpectedContentLength(Variant variant, boolean endStream) {
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

        Http2Exception e = assertThrows(Http2Exception.class,
                () -> channel.writeInbound(headersFrame(headers, endStream)));
        assertThat(e.getMessage(), startsWith("content-length (1) header is not expected"));
    }

    @ParameterizedTest(name = "{displayName} [{index}] variant={0}")
    @EnumSource(Variant.class)
    void nullContentLengthWhenContentIsNotExpected(Variant variant) {
        setUp(variant);
        nullContentLengthWhenContentIsNotExpected(variant, false);
    }

    @ParameterizedTest(name = "{displayName} [{index}] variant={0}")
    @EnumSource(Variant.class)
    void nullContentLengthWhenContentIsNotExpectedEndStream(Variant variant) {
        setUp(variant);
        nullContentLengthWhenContentIsNotExpected(variant, true);
    }

    private void nullContentLengthWhenContentIsNotExpected(Variant variant, boolean endStream) {
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

        channel.writeInbound(headersFrame(headers, endStream));
        assertThat(channel.readInbound(), instanceOf(HttpMetaData.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}] variant={0}")
    @EnumSource(Variant.class)
    void responseWithContentLengthToHeadRequest(Variant variant) {
        setUp(variant);
        responseWithContentLengthToHeadRequest(variant, false);
    }

    @ParameterizedTest(name = "{displayName} [{index}] variant={0}")
    @EnumSource(Variant.class)
    void responseWithContentLengthToHeadRequestEndStream(Variant variant) {
        setUp(variant);
        responseWithContentLengthToHeadRequest(variant, true);
    }

    private void responseWithContentLengthToHeadRequest(Variant variant, boolean endStream) {
        Assumptions.assumeTrue(variant == Variant.CLIENT_HANDLER, "Only relevant for the client-side");
        int contentLength = 1;
        // Send HEAD request
        channel.writeOutbound(newRequest(HEAD, "/", HTTP_2_0, HEADERS_FACTORY.newHeaders(),
                DEFAULT_ALLOCATOR, HEADERS_FACTORY), true);
        // Prepare server response with content-length header:
        Http2Headers headers = new DefaultHttp2Headers();
        headers.status(OK.codeAsText());
        headers.setInt(CONTENT_LENGTH, contentLength);
        channel.writeInbound(headersFrame(headers, endStream));

        HttpMetaData metaData = channel.readInbound();
        assertThat(metaData.headers().get(CONTENT_LENGTH), contentEqualTo(valueOf(contentLength)));
        if (endStream) {
            HttpHeaders trailers = channel.readInbound();
            if (trailers != null) {
                assertThat(trailers.isEmpty(), is(true));
            }
        } else {
            // No more items at this moment:
            assertThat(channel.inboundMessages(), is(empty()));
            channel.writeInbound(headersFrame(new DefaultHttp2Headers(), true));
            HttpHeaders trailers = channel.readInbound();
            assertThat(trailers.isEmpty(), is(true));
        }
        assertThat(channel.inboundMessages(), is(empty()));
    }

    @ParameterizedTest(name = "{displayName} [{index}] variant={0}")
    @EnumSource(Variant.class)
    void noContentLength(Variant variant) {
        setUp(variant);
        noContentLength(variant, false);
    }

    @ParameterizedTest(name = "{displayName} [{index}] variant={0}")
    @EnumSource(Variant.class)
    void noContentLengthEndStream(Variant variant) {
        setUp(variant);
        noContentLength(variant, true);
    }

    private void noContentLength(Variant variant, boolean endStream) {
        variant.writeOutbound(channel);

        Http2Headers headers = variant.setHeaders(new DefaultHttp2Headers());
        channel.writeInbound(headersFrame(headers, endStream));

        HttpMetaData metaData = channel.readInbound();
        if (endStream) {
            assertThat(metaData.headers().get(CONTENT_LENGTH), contentEqualTo(ZERO));
        } else {
            assertThat("Unexpected content-length header", metaData.headers().contains(CONTENT_LENGTH), is(false));
        }
        assertThat("Unexpected chunked encoding", isTransferEncodingChunked(metaData.headers()), is(false));
    }

    @ParameterizedTest(name = "{displayName} [{index}] variant={0}")
    @EnumSource(Variant.class)
    void withContentLength(Variant variant) {
        setUp(variant);
        withContentLength(variant, false);
    }

    @ParameterizedTest(name = "{displayName} [{index}] variant={0}")
    @EnumSource(Variant.class)
    void withContentLengthAndTrailers(Variant variant) {
        setUp(variant);
        withContentLength(variant, true);
    }

    private void withContentLength(Variant variant, boolean addTrailers) {
        variant.writeOutbound(channel);
        String content = "hello";

        Http2Headers headers = variant.setHeaders(new DefaultHttp2Headers());
        headers.setInt(CONTENT_LENGTH, content.length());
        channel.writeInbound(headersFrame(headers, false));

        HttpMetaData metaData = channel.readInbound();
        assertThat(metaData.headers().get(CONTENT_LENGTH), contentEqualTo(valueOf(content.length())));

        channel.writeInbound(new DefaultHttp2DataFrame(writeAscii(UnpooledByteBufAllocator.DEFAULT, content),
                !addTrailers));
        Buffer buffer = channel.readInbound();
        assertThat(buffer, is(equalTo(DEFAULT_ALLOCATOR.fromAscii(content))));

        if (addTrailers) {
            channel.writeInbound(headersFrame(new DefaultHttp2Headers().set("trailer", "value"), true));
        }
        HttpHeaders trailers = channel.readInbound();
        if (trailers != null) {
            assertThat(trailers.isEmpty(), is(!addTrailers));
        }
        assertThat(channel.inboundMessages(), is(empty()));
    }

    @ParameterizedTest(name = "{displayName} [{index}] variant={0}")
    @EnumSource(Variant.class)
    void singleHeadersFrameWithZeroContentLength(Variant variant) {
        setUp(variant);
        variant.writeOutbound(channel);

        Http2Headers headers = variant.setHeaders(new DefaultHttp2Headers());
        headers.setInt(CONTENT_LENGTH, 0);
        channel.writeInbound(headersFrame(headers, true));

        HttpMetaData metaData = channel.readInbound();
        assertThat(metaData.headers().get(CONTENT_LENGTH), contentEqualTo(valueOf(0)));

        HttpHeaders trailers = channel.readInbound();
        if (trailers != null) {
            assertThat(trailers.isEmpty(), is(true));
        }
        assertThat(channel.inboundMessages(), is(empty()));
    }

    @ParameterizedTest(name = "{displayName} [{index}] variant={0}")
    @EnumSource(Variant.class)
    void emptyMessageWrittenAsSingleFrame(Variant variant) {
        setUp(variant);
        HttpMetaData msg;
        switch (variant) {
            case CLIENT_HANDLER:
                msg = newRequest(GET, "/", HTTP_2_0, HEADERS_FACTORY.newHeaders(), DEFAULT_ALLOCATOR,
                        HEADERS_FACTORY);
                break;
            case SERVER_HANDLER:
                msg = newResponse(HttpResponseStatus.OK, HTTP_2_0, HEADERS_FACTORY.newHeaders(), DEFAULT_ALLOCATOR,
                        HEADERS_FACTORY);
                break;
            default:
                throw new IllegalStateException("Unexpected variant: " + variant);
        }
        channel.writeOutbound(msg);
        verify(closeHandler).protocolPayloadBeginOutbound(any());
        verify(closeHandler).protocolPayloadEndOutbound(any(), any());

        Http2HeadersFrame frame = channel.readOutbound();
        assertThat("Unexpected endStream flag value", frame.isEndStream(), is(true));
        assertThat("Unexpected outbound messages", channel.outboundMessages(), empty());
    }

    @ParameterizedTest(name = "{displayName} [{index}] variant={0}")
    @EnumSource(Variant.class)
    void noDataFramesForEmptyBuffers(Variant variant) {
        setUp(variant);
        Buffer[] payload = {EMPTY_BUFFER, DEFAULT_ALLOCATOR.fromAscii("data"), EMPTY_BUFFER};

        HttpMetaData msg;
        switch (variant) {
            case CLIENT_HANDLER:
                StreamingHttpRequest request = newRequest(GET, "/", HTTP_2_0,
                        HEADERS_FACTORY.newHeaders(), DEFAULT_ALLOCATOR, HEADERS_FACTORY);
                request.payloadBody(from(payload));
                msg = request;
                break;
            case SERVER_HANDLER:
                StreamingHttpResponse response = newResponse(HttpResponseStatus.OK, HTTP_2_0,
                        HEADERS_FACTORY.newHeaders(), DEFAULT_ALLOCATOR, HEADERS_FACTORY);
                response.payloadBody(from(payload));
                msg = response;
                break;
            default:
                throw new IllegalStateException("Unexpected variant: " + variant);
        }
        channel.writeOutbound(msg);
        verify(closeHandler).protocolPayloadBeginOutbound(any());
        verify(closeHandler, never()).protocolPayloadEndOutbound(any(), any());
        for (Buffer buffer : payload) {
            channel.writeOutbound(buffer);
        }
        verify(closeHandler, never()).protocolPayloadEndOutbound(any(), any());
        channel.writeOutbound(EmptyHttpHeaders.INSTANCE);
        verify(closeHandler).protocolPayloadEndOutbound(any(), any());

        Http2HeadersFrame headersFrame = channel.readOutbound();
        assertThat("Unexpected endStream flag value at headers frame", headersFrame.isEndStream(), is(false));
        assertEmptyDataFrame();
        Http2DataFrame dataFrame = channel.readOutbound();
        assertThat("Unexpected data", dataFrame.content().toString(US_ASCII), is("data"));
        assertThat("Unexpected endStream flag value at data frame", dataFrame.isEndStream(), is(false));
        assertEmptyDataFrame();
        dataFrame = channel.readOutbound();
        assertThat("Unexpected endStream flag value at last frame", dataFrame.isEndStream(), is(true));
        assertThat("Unexpected outbound messages", channel.outboundMessages(), empty());
    }

    private void assertEmptyDataFrame() {
        Http2DataFrame dataFrame = channel.readOutbound();
        assertThat(dataFrame.content().readableBytes(), is(0));
        dataFrame.release();
    }

    private static Http2HeadersFrame headersFrame(Http2Headers headers, boolean endStream) {
        Http2FrameStream stream = mock(Http2FrameStream.class);
        when(stream.id()).thenReturn(3);
        Http2HeadersFrame frame = new DefaultHttp2HeadersFrame(headers, endStream);
        frame.stream(stream);
        return frame;
    }

    private static final class EmbeddedHttp2StreamChannel extends EmbeddedChannel implements Http2StreamChannel {

        @Override
        public Http2FrameStream stream() {
            Http2FrameStream streamMock = mock(Http2FrameStream.class);
            when(streamMock.id()).thenReturn(3);
            return streamMock;
        }
    }
}
