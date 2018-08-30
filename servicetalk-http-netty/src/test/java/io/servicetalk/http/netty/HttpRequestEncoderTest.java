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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DefaultThreadFactory;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.EmptyHttpHeaders;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.LastHttpPayloadChunk;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpServerConfig;
import io.servicetalk.tcp.netty.internal.TcpClientChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.tcp.netty.internal.TcpConnector;
import io.servicetalk.tcp.netty.internal.TcpServerChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpServerConfig;
import io.servicetalk.tcp.netty.internal.TcpServerInitializer;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.Connection;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.SocketChannel;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpHeaderNames.CONNECTION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderNames.USER_AGENT;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpHeaderValues.KEEP_ALIVE;
import static io.servicetalk.http.api.HttpPayloadChunks.newLastPayloadChunk;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMetaDataFactory.newRequestMetaData;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpRequestMethods.POST;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequest;
import static io.servicetalk.transport.api.ContextFilter.ACCEPT_ALL;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor;
import static java.lang.Boolean.TRUE;
import static java.lang.Integer.toHexString;
import static java.lang.String.valueOf;
import static java.lang.Thread.NORM_PRIORITY;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class HttpRequestEncoderTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @ClassRule
    public static final ExecutionContextRule SEC = new ExecutionContextRule(() -> DEFAULT_ALLOCATOR,
            () -> createIoExecutor(0, new DefaultThreadFactory("server-io", false, NORM_PRIORITY)),
            Executors::immediate);
    @ClassRule
    public static final ExecutionContextRule CEC = new ExecutionContextRule(() -> DEFAULT_ALLOCATOR,
            () -> createIoExecutor(0, new DefaultThreadFactory("client-io", false, NORM_PRIORITY)),
            Executors::newCachedThreadExecutor);

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
        LastHttpPayloadChunk lastChunk = newLastPayloadChunk(buffer, EmptyHttpHeaders.INSTANCE);
        HttpRequestMetaData request = newRequestMetaData(HTTP_1_1,
                GET, "/some/path?foo=bar&baz=yyy", INSTANCE.newHeaders());
        request.getHeaders()
                .add(CONNECTION, KEEP_ALIVE)
                .add(USER_AGENT, "unit-test")
                .add(CONTENT_LENGTH, valueOf(content.length));
        channel.writeOutbound(request);
        channel.writeOutbound(lastChunk.duplicate());
        verifyHttpRequest(channel, buffer, TransferEncoding.ContentLength, false);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test(expected = IllegalArgumentException.class)
    public void contentLengthNoTrailersHeaderWhiteSpaceThrowByDefault() {
        EmbeddedChannel channel = newEmbeddedChannel();
        HttpRequestMetaData request = newRequestMetaData(HTTP_1_1,
                GET, "/some/path?foo=bar&baz=yyy", INSTANCE.newHeaders());
        try {
            request.getHeaders().add(" " + CONNECTION, KEEP_ALIVE);
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

        LastHttpPayloadChunk lastChunk = newLastPayloadChunk(buffer, EmptyHttpHeaders.INSTANCE);
        HttpRequestMetaData request = newRequestMetaData(HTTP_1_1, GET, "/some/path?foo=bar&baz=yyy",
                new DefaultHttpHeadersFactory(false, false).newHeaders());
        request.getHeaders()
                .add(" " + CONNECTION + " ", " " + KEEP_ALIVE)
                .add("  " + USER_AGENT + "   ", "    unit-test   ")
                .add(CONTENT_LENGTH, valueOf(content.length));
        channel.writeOutbound(request);
        channel.writeOutbound(lastChunk.duplicate());

        ByteBuf byteBuf = channel.readOutbound();
        String actualMetaData = byteBuf.toString(US_ASCII);
        byteBuf.release();
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.contains("GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n"));
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.contains(" " + CONNECTION + " :  " + KEEP_ALIVE + "\r\n"));
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.contains("  " + USER_AGENT + "   :     unit-test   " + "\r\n"));
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.contains(CONTENT_LENGTH + ": " + valueOf(buffer.getReadableBytes()) + "\r\n"));
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.endsWith("\r\n" + "\r\n"));
        byteBuf = channel.readOutbound();
        assertEquals(buffer.toNioBuffer(), byteBuf.nioBuffer());
        byteBuf.release();

        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void chunkedNoTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = DEFAULT_ALLOCATOR.wrap(content);
        LastHttpPayloadChunk lastChunk = newLastPayloadChunk(buffer, EmptyHttpHeaders.INSTANCE);
        HttpRequestMetaData request = newRequestMetaData(HTTP_1_1,
                GET, "/some/path?foo=bar&baz=yyy", INSTANCE.newHeaders());
        request.getHeaders()
                .add(CONNECTION, KEEP_ALIVE)
                .add(USER_AGENT, "unit-test")
                .add(TRANSFER_ENCODING, CHUNKED);
        channel.writeOutbound(request);
        channel.writeOutbound(lastChunk.duplicate());
        verifyHttpRequest(channel, buffer, TransferEncoding.Chunked, false);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void chunkedWithTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = DEFAULT_ALLOCATOR.wrap(content);
        HttpHeaders trailers = INSTANCE.newTrailers();
        trailers.add("TrailerStatus", "good");
        LastHttpPayloadChunk lastChunk = newLastPayloadChunk(buffer, trailers);
        HttpRequestMetaData request = newRequestMetaData(HTTP_1_1,
                GET, "/some/path?foo=bar&baz=yyy", INSTANCE.newHeaders());
        request.getHeaders()
                .add(CONNECTION, KEEP_ALIVE)
                .add(USER_AGENT, "unit-test")
                .add(TRANSFER_ENCODING, CHUNKED);
        channel.writeOutbound(request);
        channel.writeOutbound(lastChunk.duplicate());
        verifyHttpRequest(channel, buffer, TransferEncoding.Chunked, true);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void chunkedNoTrailersNoContent() {
        EmbeddedChannel channel = newEmbeddedChannel();
        LastHttpPayloadChunk lastChunk = newLastPayloadChunk(EMPTY_BUFFER, EmptyHttpHeaders.INSTANCE);
        HttpRequestMetaData request = newRequestMetaData(HTTP_1_1,
                GET, "/some/path?foo=bar&baz=yyy", INSTANCE.newHeaders());
        request.getHeaders()
                .add(CONNECTION, KEEP_ALIVE)
                .add(USER_AGENT, "unit-test")
                .add(TRANSFER_ENCODING, CHUNKED);
        channel.writeOutbound(request);
        channel.writeOutbound(lastChunk.duplicate());
        verifyHttpRequest(channel, EMPTY_BUFFER, TransferEncoding.Chunked, false);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void variableNoTrailersNoContent() {
        EmbeddedChannel channel = newEmbeddedChannel();
        LastHttpPayloadChunk lastChunk = newLastPayloadChunk(EMPTY_BUFFER, EmptyHttpHeaders.INSTANCE);
        HttpRequestMetaData request = newRequestMetaData(HTTP_1_1,
                GET, "/some/path?foo=bar&baz=yyy", INSTANCE.newHeaders());
        request.getHeaders()
                .add(CONNECTION, KEEP_ALIVE)
                .add(USER_AGENT, "unit-test");
        channel.writeOutbound(request);
        channel.writeOutbound(lastChunk.duplicate());
        verifyHttpRequest(channel, EMPTY_BUFFER, TransferEncoding.Variable, false);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void variableNoTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = DEFAULT_ALLOCATOR.wrap(content);
        LastHttpPayloadChunk lastChunk = newLastPayloadChunk(buffer, EmptyHttpHeaders.INSTANCE);
        HttpRequestMetaData request = newRequestMetaData(HTTP_1_1,
                GET, "/some/path?foo=bar&baz=yyy", INSTANCE.newHeaders());
        request.getHeaders()
                .add(CONNECTION, KEEP_ALIVE)
                .add(USER_AGENT, "unit-test");
        channel.writeOutbound(request);
        channel.writeOutbound(lastChunk.duplicate());
        verifyHttpRequest(channel, buffer, TransferEncoding.Variable, false);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void variableWithTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = DEFAULT_ALLOCATOR.wrap(content);
        HttpHeaders trailers = INSTANCE.newTrailers();
        trailers.add("TrailerStatus", "good");
        LastHttpPayloadChunk lastChunk = newLastPayloadChunk(buffer, trailers);
        HttpRequestMetaData request = newRequestMetaData(HTTP_1_1,
                GET, "/some/path?foo=bar&baz=yyy", INSTANCE.newHeaders());
        request.getHeaders()
                .add(CONNECTION, KEEP_ALIVE)
                .add(USER_AGENT, "unit-test");
        channel.writeOutbound(request);
        channel.writeOutbound(lastChunk.duplicate());
        verifyHttpRequest(channel, buffer, TransferEncoding.Variable, false);

        // The trailers will just not be encoded if the transfer encoding is not set correctly.
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void contentLengthWithTrailers() {
        EmbeddedChannel channel = newEmbeddedChannel();
        byte[] content = new byte[128];
        ThreadLocalRandom.current().nextBytes(content);
        Buffer buffer = DEFAULT_ALLOCATOR.wrap(content);
        HttpHeaders trailers = INSTANCE.newTrailers();
        trailers.add("TrailerStatus", "good");
        LastHttpPayloadChunk lastChunk = newLastPayloadChunk(buffer, trailers);
        HttpRequestMetaData request = newRequestMetaData(HTTP_1_1,
                GET, "/some/path?foo=bar&baz=yyy", INSTANCE.newHeaders());
        request.getHeaders()
                .add(CONNECTION, KEEP_ALIVE)
                .add(USER_AGENT, "unit-test")
                .add(CONTENT_LENGTH, valueOf(content.length));
        channel.writeOutbound(request);
        channel.writeOutbound(lastChunk.duplicate());
        verifyHttpRequest(channel, buffer, TransferEncoding.ContentLength, false);

        // The trailers will just not be encoded if the transfer encoding is not set correctly.
        assertFalse(channel.finishAndReleaseAll());
    }

    private static void verifyHttpRequest(EmbeddedChannel channel, Buffer buffer, TransferEncoding encoding,
                                          boolean trailers) {
        ByteBuf byteBuf = channel.readOutbound();
        String actualMetaData = byteBuf.toString(US_ASCII);
        byteBuf.release();
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.contains("GET /some/path?foo=bar&baz=yyy HTTP/1.1" + "\r\n"));
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.contains(CONNECTION + ": " + KEEP_ALIVE + "\r\n"));
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.contains(USER_AGENT + ": unit-test" + "\r\n"));
        assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.endsWith("\r\n" + "\r\n"));
        switch (encoding) {
            case Chunked:
                assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.contains(TRANSFER_ENCODING + ": " + CHUNKED + "\r\n"));
                if (buffer.getReadableBytes() != 0) {
                    byteBuf = channel.readOutbound();
                    assertEquals(toHexString(buffer.getReadableBytes()) + "\r\n", byteBuf.toString(US_ASCII));
                    byteBuf.release();

                    byteBuf = channel.readOutbound();
                    assertEquals(buffer.toNioBuffer(), byteBuf.nioBuffer());
                    byteBuf.release();

                    byteBuf = channel.readOutbound();
                    assertEquals("\r\n", byteBuf.toString(US_ASCII));
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
                assertTrue("unexpected metadata: " + actualMetaData, actualMetaData.contains(CONTENT_LENGTH + ": " + valueOf(buffer.getReadableBytes()) + "\r\n"));
                byteBuf = channel.readOutbound();
                assertEquals(buffer.toNioBuffer(), byteBuf.nioBuffer());
                break;
            case Variable:
                byteBuf = channel.readOutbound();
                assertEquals(buffer.toNioBuffer(), byteBuf.nioBuffer());
                byteBuf.release();
                break;
            default:
                throw new Error();
        }
    }

    private static EmbeddedChannel newEmbeddedChannel() {
        return new EmbeddedChannel(new HttpRequestEncoder(new ArrayDeque<>(), 256, 256));
    }

    @Test
    public void protocolPayloadEndOutboundShouldNotTriggerOnFailedFlush() throws Exception {

        CloseHandler closeHandler = mock(CloseHandler.class);

        try (CompositeCloseable resources = newCompositeCloseable()) {

            CompletableProcessor serverCloseTrigger = new CompletableProcessor();
            CountDownLatch serverChannelLatch = new CountDownLatch(1);
            AtomicReference<Channel> serverChannelRef = new AtomicReference<>();

            ReadOnlyTcpServerConfig sConfig = new TcpServerConfig(true)
                    .enableWireLogging("servicetalk-tests-server-wire-logger").asReadOnly();
            ServerContext serverContext = resources.prepend(awaitIndefinitelyNonNull(
                    new TcpServerInitializer(SEC, sConfig)
                            .start(new InetSocketAddress(0),
                                    context -> Single.success(TRUE),
                                    new TcpServerChannelInitializer(sConfig, ACCEPT_ALL).andThen(
                                            (c, cc) -> {
                                                serverChannelRef.compareAndSet(null, c);
                                                serverChannelLatch.countDown();
                                                return cc;
                                            }), false, true)));

            HttpClientConfig cConfig = new HttpClientConfig(new TcpClientConfig(true)
                    .enableWireLogging("servicetalk-tests-client-wire-logger"));

            final ChannelInitializer initializer = new TcpClientChannelInitializer(cConfig.getTcpClientConfig())
                    .andThen(new HttpClientChannelInitializer(cConfig.asReadOnly(), closeHandler))
                    .andThen((channel, context) -> {
                        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                // Propagate the user event in the pipeline before triggering the test condition.
                                ctx.fireUserEventTriggered(evt);
                                if (evt instanceof ChannelInputShutdownReadComplete) {
                                    serverCloseTrigger.onComplete();
                                }
                            }
                        });
                        return context;
                    });

            Predicate<Object> predicate = (Object h) -> h instanceof LastHttpPayloadChunk;

            Connection<Object, Object> conn = resources.prepend(awaitIndefinitelyNonNull(
                    new TcpConnector<>(cConfig.getTcpClientConfig().asReadOnly(), initializer, () -> predicate, null,
                            closeHandler).connect(CEC, serverContext.getListenAddress(), false)));

            // The server needs to wait to close the conneciton until after the client has established the connection.
            serverChannelLatch.await();
            Channel serverChannel = serverChannelRef.get();
            assertNotNull(serverChannel);
            ((SocketChannel) serverChannel).config().setSoLinger(0);
            serverChannel.close(); // Close and send RST concurrently with client write

            StreamingHttpRequest<?> request = newRequest(POST, "/closeme", empty());
            HttpPayloadChunk lastChunk = newLastPayloadChunk(DEFAULT_ALLOCATOR.fromAscii("Bye"),
                    INSTANCE.newEmptyTrailers());

            awaitIndefinitely(serverCloseTrigger);
            Completable write = conn.write(from(request, lastChunk));

            try {
                awaitIndefinitely(write);
                fail("Should not complete normally");
            } catch (ExecutionException e) {
                verify(closeHandler, never()).protocolPayloadEndOutbound(any());
            }
        }
    }
}
