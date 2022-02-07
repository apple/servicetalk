/*
 * Copyright © 2018-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.CompletableSource.Processor;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.EmptyHttpHeaders;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpServerConfig;
import io.servicetalk.tcp.netty.internal.TcpClientChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpConnector;
import io.servicetalk.tcp.netty.internal.TcpServerBinder;
import io.servicetalk.tcp.netty.internal.TcpServerChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpServerConfig;
import io.servicetalk.transport.api.ConnectionInfo.Protocol;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;
import io.servicetalk.transport.netty.internal.NettyConnection;
import io.servicetalk.transport.netty.internal.NoopTransportObserver;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferUtils.getByteBufAllocator;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONNECTION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderNames.USER_AGENT;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpHeaderValues.KEEP_ALIVE;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMetaDataFactory.newRequestMetaData;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.CloseHandler.forPipelinedRequestResponse;
import static io.servicetalk.transport.netty.internal.FlushStrategies.defaultFlushStrategy;
import static java.lang.Integer.toHexString;
import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class HttpRequestEncoderTest extends HttpEncoderTest<HttpRequestMetaData> {

    private static final BufferAllocator allocator = DEFAULT_ALLOCATOR;
    private static final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(allocator, DefaultHttpHeadersFactory.INSTANCE, HTTP_1_1);

    @RegisterExtension
    static final ExecutionContextExtension SEC =
            new ExecutionContextExtension(() -> allocator,
                    () -> createIoExecutor("server-io"),
                    Executors::immediate).setClassLevel(true);;
    @RegisterExtension
    static final ExecutionContextExtension CEC = new ExecutionContextExtension(() -> allocator,
            () -> createIoExecutor("client-io"),
            Executors::newCachedThreadExecutor).setClassLevel(true);;

    @Override
    EmbeddedChannel newEmbeddedChannel() {
        return new EmbeddedChannel(new HttpRequestEncoder(new ArrayDeque<>(), 256, 256));
    }

    @Override
    HttpRequestMetaData newMetaData(final HttpHeaders headers) {
        return newRequestMetaData(HTTP_1_1, GET, "/some/path?foo=bar&baz=yyy", headers);
    }

    @Test
    void contentLengthNoTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = allocator.wrap(content);
        HttpRequestMetaData request = newRequestMetaData(HTTP_1_1,
                GET, "/some/path?foo=bar&baz=yyy", INSTANCE.newHeaders());
        request.headers()
                .add(CONNECTION, KEEP_ALIVE)
                .add(USER_AGENT, "unit-test")
                .add(CONTENT_LENGTH, valueOf(content.length));
        channel.writeOutbound(request);
        channel.writeOutbound(buffer.duplicate());
        channel.writeOutbound(EmptyHttpHeaders.INSTANCE);
        verifyHttpRequest(channel, buffer, TransferEncoding.ContentLength, false);
        consumeEmptyBufferFromTrailers(channel);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    void contentLengthNoTrailersHeaderWhiteSpaceThrowByDefault() {
        EmbeddedChannel channel = newEmbeddedChannel();
        HttpRequestMetaData request = newRequestMetaData(HTTP_1_1,
                                                         GET, "/some/path?foo=bar&baz=yyy", INSTANCE.newHeaders());

        assertThrows(IllegalArgumentException.class, () -> request.addHeader(" " + CONNECTION, KEEP_ALIVE));
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    void contentLengthNoTrailersHeaderWhiteSpaceEncodedWithValidationOff() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = allocator.wrap(content);

        HttpRequestMetaData request = newRequestMetaData(HTTP_1_1, GET, "/some/path?foo=bar&baz=yyy",
                new DefaultHttpHeadersFactory(false, false, false).newHeaders());
        request.headers()
                .add(" " + CONNECTION + " ", " " + KEEP_ALIVE)
                .add("  " + USER_AGENT + "   ", "    unit-test   ")
                .add(CONTENT_LENGTH, valueOf(content.length));
        channel.writeOutbound(request);
        channel.writeOutbound(buffer.duplicate());

        ByteBuf byteBuf = channel.readOutbound();
        String actualMetaData = byteBuf.toString(US_ASCII);
        byteBuf.release();
        assertTrue(actualMetaData.contains(
                "GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n"), () -> "unexpected metadata: " + actualMetaData);
        assertTrue(actualMetaData.contains(
                " " + CONNECTION + " :  " + KEEP_ALIVE + "\r\n"), () -> "unexpected metadata: " + actualMetaData);
        assertTrue(actualMetaData.contains(
                "  " + USER_AGENT + "   :     unit-test   " + "\r\n"), () -> "unexpected metadata: " + actualMetaData);
        assertTrue(actualMetaData.contains(
                CONTENT_LENGTH + ": " + buffer.readableBytes() + "\r\n"),
                () -> "unexpected metadata: " + actualMetaData);
        assertTrue(actualMetaData.endsWith("\r\n" + "\r\n"), () -> "unexpected metadata: " + actualMetaData);
        byteBuf = channel.readOutbound();
        assertEquals(buffer.toNioBuffer(), byteBuf.nioBuffer());
        byteBuf.release();

        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    void chunkedNoTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = allocator.wrap(content);
        HttpRequestMetaData request = newRequestMetaData(HTTP_1_1,
                GET, "/some/path?foo=bar&baz=yyy", INSTANCE.newHeaders());
        request.headers()
                .add(CONNECTION, KEEP_ALIVE)
                .add(USER_AGENT, "unit-test")
                .add(TRANSFER_ENCODING, CHUNKED);
        channel.writeOutbound(request);
        channel.writeOutbound(buffer.duplicate());
        channel.writeOutbound(EmptyHttpHeaders.INSTANCE);
        verifyHttpRequest(channel, buffer, TransferEncoding.Chunked, false);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    void chunkedWithTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = allocator.wrap(content);
        HttpHeaders trailers = INSTANCE.newTrailers();
        trailers.add("TrailerStatus", "good");

        HttpRequestMetaData request = newRequestMetaData(HTTP_1_1,
                GET, "/some/path?foo=bar&baz=yyy", INSTANCE.newHeaders());
        request.headers()
                .add(CONNECTION, KEEP_ALIVE)
                .add(USER_AGENT, "unit-test")
                .add(TRANSFER_ENCODING, CHUNKED);
        channel.writeOutbound(request);
        channel.writeOutbound(buffer.duplicate());
        channel.writeOutbound(trailers);
        verifyHttpRequest(channel, buffer, TransferEncoding.Chunked, true);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    void chunkedNoTrailersNoContent() {
        EmbeddedChannel channel = newEmbeddedChannel();
        HttpRequestMetaData request = newRequestMetaData(HTTP_1_1,
                GET, "/some/path?foo=bar&baz=yyy", INSTANCE.newHeaders());
        request.headers()
                .add(CONNECTION, KEEP_ALIVE)
                .add(USER_AGENT, "unit-test")
                .add(TRANSFER_ENCODING, CHUNKED);
        channel.writeOutbound(request);
        channel.writeOutbound(EMPTY_BUFFER.duplicate());
        channel.writeOutbound(EmptyHttpHeaders.INSTANCE);
        verifyHttpRequest(channel, EMPTY_BUFFER, TransferEncoding.Chunked, false);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    void variableNoTrailersNoContent() {
        EmbeddedChannel channel = newEmbeddedChannel();
        HttpRequestMetaData request = newRequestMetaData(HTTP_1_1,
                GET, "/some/path?foo=bar&baz=yyy", INSTANCE.newHeaders());
        request.headers()
                .add(CONNECTION, KEEP_ALIVE)
                .add(USER_AGENT, "unit-test");
        channel.writeOutbound(request);
        channel.writeOutbound(EMPTY_BUFFER);
        channel.writeOutbound(EmptyHttpHeaders.INSTANCE);
        verifyHttpRequest(channel, EMPTY_BUFFER, TransferEncoding.Variable, false);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    void variableNoTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = allocator.wrap(content);
        HttpRequestMetaData request = newRequestMetaData(HTTP_1_1,
                GET, "/some/path?foo=bar&baz=yyy", INSTANCE.newHeaders());
        request.headers()
                .add(CONNECTION, KEEP_ALIVE)
                .add(USER_AGENT, "unit-test");
        channel.writeOutbound(request);
        channel.writeOutbound(buffer.duplicate());
        channel.writeOutbound(EmptyHttpHeaders.INSTANCE);
        verifyHttpRequest(channel, buffer, TransferEncoding.Variable, false);
        assertFalse(channel.finishAndReleaseAll());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void variableWithTrailers(boolean emptyTrailers) {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = allocator.wrap(content);
        HttpHeaders trailers = INSTANCE.newTrailers();
        if (!emptyTrailers) {
            trailers.add("TrailerStatus", "good");
        }
        HttpRequestMetaData request = newRequestMetaData(HTTP_1_1,
                GET, "/some/path?foo=bar&baz=yyy", INSTANCE.newHeaders());
        request.headers()
                .add(CONNECTION, KEEP_ALIVE)
                .add(USER_AGENT, "unit-test");
        channel.writeOutbound(request);
        channel.writeOutbound(buffer.duplicate());
        if (!emptyTrailers) {
            assertThrows(IOException.class, () -> channel.writeOutbound(trailers));
        } else {
            channel.writeOutbound(trailers);
            verifyHttpRequest(channel, buffer, TransferEncoding.Variable, false);
        }

        // The trailers will just not be encoded if the transfer encoding is not set correctly.
        assertNotEquals(emptyTrailers, channel.finishAndReleaseAll());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void contentLengthWithTrailers(boolean emptyTrailers) {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = allocator.wrap(content);
        HttpHeaders trailers = INSTANCE.newTrailers();
        if (!emptyTrailers) {
            trailers.add("TrailerStatus", "good");
        }
        HttpRequestMetaData request = newRequestMetaData(HTTP_1_1,
                GET, "/some/path?foo=bar&baz=yyy", INSTANCE.newHeaders());
        request.headers()
                .add(CONNECTION, KEEP_ALIVE)
                .add(USER_AGENT, "unit-test")
                .add(CONTENT_LENGTH, valueOf(content.length));
        channel.writeOutbound(request);
        channel.writeOutbound(buffer.duplicate());
        if (!emptyTrailers) {
            assertThrows(IOException.class, () -> channel.writeOutbound(trailers));
        } else {
            channel.writeOutbound(trailers);
            verifyHttpRequest(channel, buffer, TransferEncoding.ContentLength, false);
            consumeEmptyBufferFromTrailers(channel);
        }

        // The trailers will just not be encoded if the transfer encoding is not set correctly.
        assertNotEquals(emptyTrailers, channel.finishAndReleaseAll());
    }

    private static String verifyHttpRequest(EmbeddedChannel channel, Buffer buffer, TransferEncoding encoding,
                                            boolean trailers) {
        ByteBuf byteBuf = channel.readOutbound();
        String actualMetaData = byteBuf.toString(US_ASCII);
        byteBuf.release();
        assertTrue(actualMetaData.contains(
                "GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n"), () -> "unexpected metadata: " + actualMetaData);
        assertTrue(actualMetaData.contains(
                CONNECTION + ": " + KEEP_ALIVE + "\r\n"), () -> "unexpected metadata: " + actualMetaData);
        assertTrue(actualMetaData.contains(
                USER_AGENT + ": unit-test" + "\r\n"), () -> "unexpected metadata: " + actualMetaData);
        assertTrue(actualMetaData.endsWith("\r\n" + "\r\n"), () -> "unexpected metadata: " + actualMetaData);
        switch (encoding) {
            case Chunked:
                assertTrue(actualMetaData.contains(
                        TRANSFER_ENCODING + ": " + CHUNKED + "\r\n"), () -> "unexpected metadata: " + actualMetaData);
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
                assertTrue(actualMetaData.contains(
                        CONTENT_LENGTH + ": " + buffer.readableBytes() + "\r\n"),
                        () -> "unexpected metadata: " + actualMetaData);
                byteBuf = channel.readOutbound();
                assertEquals(buffer.toNioBuffer(), byteBuf.nioBuffer());
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
        return actualMetaData;
    }

    @Test
    void protocolPayloadEndOutboundShouldNotTriggerOnFailedFlush() throws Exception {
        AtomicReference<CloseHandler> closeHandlerRef = new AtomicReference<>();
        try (CompositeCloseable resources = newCompositeCloseable()) {
            Processor serverCloseTrigger = newCompletableProcessor();
            CountDownLatch serverChannelLatch = new CountDownLatch(1);
            AtomicReference<Channel> serverChannelRef = new AtomicReference<>();

            ReadOnlyTcpServerConfig sConfig = new TcpServerConfig().asReadOnly();
            ServerContext serverContext = resources.prepend(
                    TcpServerBinder.bind(localAddress(0), sConfig, false,
                            SEC, null,
                            (channel, observer) -> DefaultNettyConnection.initChannel(channel, SEC.bufferAllocator(),
                                    SEC.executor(), SEC.ioExecutor(),
                                    forPipelinedRequestResponse(false, channel.config()), defaultFlushStrategy(), null,
                                    new TcpServerChannelInitializer(sConfig, observer).andThen(
                                            channel2 -> {
                                                serverChannelRef.compareAndSet(null, channel2);
                                                serverChannelLatch.countDown();
                                            }), defaultStrategy(), mock(Protocol.class), observer, false, __ -> false),
                            connection -> { }).toFuture().get());
            ReadOnlyHttpClientConfig cConfig = new HttpClientConfig().asReadOnly();
            assert cConfig.h1Config() != null;

            NettyConnection<Object, Object> conn = resources.prepend(
                    TcpConnector.connect(null, serverHostAndPort(serverContext), cConfig.tcpConfig(), false,
                            CEC, (channel, connectionObserver) -> {
                                CloseHandler closeHandler = spy(forPipelinedRequestResponse(true, channel.config()));
                                closeHandlerRef.compareAndSet(null, closeHandler);
                                return DefaultNettyConnection.initChannel(channel, CEC.bufferAllocator(),
                                        CEC.executor(), CEC.ioExecutor(),
                                        closeHandler, defaultFlushStrategy(),
                                        null, new TcpClientChannelInitializer(cConfig.tcpConfig(),
                                                connectionObserver)
                                                .andThen(new HttpClientChannelInitializer(
                                                        getByteBufAllocator(CEC.bufferAllocator()),
                                                        cConfig.h1Config(), closeHandler))
                                                .andThen(channel2 -> channel2.pipeline()
                                                        .addLast(new ChannelInboundHandlerAdapter() {
                                                            @Override
                                                            public void userEventTriggered(ChannelHandlerContext ctx,
                                                                                           Object evt) {
                                                                // Propagate the user event in the pipeline before
                                                                // triggering the test condition.
                                                                ctx.fireUserEventTriggered(evt);
                                                                if (evt instanceof ChannelInputShutdownReadComplete) {
                                                                    serverCloseTrigger.onComplete();
                                                                }
                                                            }
                                                        })), defaultStrategy(), HTTP_1_1, connectionObserver, true,
                                        __ -> false);
                            },
                            NoopTransportObserver.INSTANCE).toFuture().get());

            // The server needs to wait to close the conneciton until after the client has established the connection.
            serverChannelLatch.await();
            Channel serverChannel = serverChannelRef.get();
            assertNotNull(serverChannel);
            assumeFalse(serverChannel instanceof NioSocketChannel,
                    "Windows doesn't emit ChannelInputShutdownReadComplete. Investigation Required.");
            ((SocketChannel) serverChannel).config().setSoLinger(0);
            serverChannel.close(); // Close and send RST concurrently with client write

            StreamingHttpRequest request = reqRespFactory.post("/closeme");

            fromSource(serverCloseTrigger).toFuture().get();
            Completable write = conn.write(from(request, allocator.fromAscii("Bye"), EmptyHttpHeaders.INSTANCE));

            assertThrows(ExecutionException.class, () -> write.toFuture().get());
            CloseHandler closeHandler = closeHandlerRef.get();
            assertNotNull(closeHandler);
            verify(closeHandler, never()).protocolPayloadEndOutbound(any(), any());
        }
    }

    @Test
    void withContentLengthAndChunked() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = allocator.wrap(content);
        HttpRequestMetaData request = newRequestMetaData(HTTP_1_1,
                GET, "/some/path?foo=bar&baz=yyy", INSTANCE.newHeaders());
        request.headers()
                .add(CONNECTION, KEEP_ALIVE)
                .add(USER_AGENT, "unit-test")
                .add(CONTENT_LENGTH, valueOf(content.length))
                .add(TRANSFER_ENCODING, CHUNKED);
        channel.writeOutbound(request);
        channel.writeOutbound(buffer.duplicate());
        channel.writeOutbound(EmptyHttpHeaders.INSTANCE);
        String metaData = verifyHttpRequest(channel, buffer, TransferEncoding.Chunked, false);
        assertFalse(
            metaData.contains(CONTENT_LENGTH),
            "Unexpected content-length header in meta-data while chunked encoding is used: " + metaData);
        assertFalse(channel.finishAndReleaseAll());
    }
}
