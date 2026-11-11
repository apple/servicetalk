/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.buffer.ByteBufUtil.writeAscii;
import static io.netty.util.ReferenceCountUtil.release;
import static io.servicetalk.buffer.api.Matchers.contentEqualTo;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.BuilderUtils.serverChannel;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static java.lang.Integer.MAX_VALUE;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end tests for {@link H1SpecExceptions#allowHeaderWithoutColon()} on the server and on the client.
 */
class H1HeaderWithoutColonTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor").setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor").setClassLevel(true);

    private static final String X_LEGACY = "x-legacy";

    @ParameterizedTest(name = "{displayName} [{index}] allowHeaderWithoutColon={0}")
    @ValueSource(booleans = {true, false})
    void serverReceivesRequestHeaderWithoutColon(boolean allowHeaderWithoutColon) throws Exception {
        final AtomicInteger requests = new AtomicInteger();
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .ioExecutor(SERVER_CTX.ioExecutor())
                .executor(SERVER_CTX.executor())
                .protocols(h1().specExceptions(specExceptions(allowHeaderWithoutColon)).build())
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    requests.incrementAndGet();
                    return responseFactory.ok().payloadBody(X_LEGACY + "=[" + request.headers().get(X_LEGACY) + ']',
                            textSerializerUtf8());
                })) {
            final InetSocketAddress serverAddress = (InetSocketAddress) serverContext.listenAddress();
            try (Socket socket = new Socket(serverAddress.getAddress(), serverAddress.getPort())) {
                socket.setSoTimeout((int) SECONDS.toMillis(DEFAULT_TIMEOUT_SECONDS));
                final OutputStream out = socket.getOutputStream();
                out.write(("GET / HTTP/1.1\r\n" +
                        "Host: servicetalk.io\r\n" +
                        "X-Legacy\r\n" +
                        "Connection: close\r\n" + "\r\n").getBytes(US_ASCII));
                out.flush();

                final InputStream in = socket.getInputStream();
                if (allowHeaderWithoutColon) {
                    final String response = readUntilClosed(in);
                    assertThat(response, startsWith("HTTP/1.1 200 OK\r\n"));
                    assertThat(response, endsWith("\r\n\r\n" + X_LEGACY + "=[]"));
                    assertThat(requests.get(), is(1));
                } else {
                    // The server can not decode the request, so it closes or resets the connection without a response.
                    assertThat(readUntilClosedOrReset(in), is(emptyString()));
                    assertThat(requests.get(), is(0));
                }
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] allowHeaderWithoutColon={0}")
    @ValueSource(booleans = {true, false})
    void clientReceivesResponseHeaderWithoutColon(boolean allowHeaderWithoutColon) throws Exception {
        final ServerSocketChannel server = nettyServer("HTTP/1.1 200 OK\r\n" +
                "X-Legacy\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n" + "\r\n");
        try (BlockingHttpClient client = HttpClients.forSingleAddress(HostAndPort.of(server.localAddress()))
                .ioExecutor(CLIENT_CTX.ioExecutor())
                .executor(CLIENT_CTX.executor())
                .protocols(h1().specExceptions(specExceptions(allowHeaderWithoutColon)).build())
                .buildBlocking()) {
            if (allowHeaderWithoutColon) {
                final HttpResponse response = client.request(client.get("/"));
                assertThat(response.status(), is(OK));
                assertThat(response.headers().get(X_LEGACY), contentEqualTo(""));
            } else {
                final Exception e = assertThrows(Exception.class, () -> client.request(client.get("/")));
                assertThat(decoderException(e).getMessage(), startsWith("Unable to find end of a header name"));
            }
        } finally {
            server.close().sync();
        }
    }

    private static H1SpecExceptions specExceptions(final boolean allowHeaderWithoutColon) {
        return new H1SpecExceptions.Builder().allowHeaderWithoutColon(allowHeaderWithoutColon).build();
    }

    private static String readUntilClosed(final InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), US_ASCII);
    }

    private static String readUntilClosedOrReset(final InputStream in) throws IOException {
        try {
            return readUntilClosed(in);
        } catch (SocketException e) {
            return "";
        }
    }

    private static DecoderException decoderException(final Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof DecoderException) {
                return (DecoderException) cause;
            }
        }
        throw new AssertionError("No DecoderException in the cause chain", t);
    }

    private static ServerSocketChannel nettyServer(final String response) throws Exception {
        final EventLoopGroup eventLoopGroup =
                toEventLoopAwareNettyIoExecutor(SERVER_CTX.ioExecutor()).eventLoopGroup();
        final ServerBootstrap bs = new ServerBootstrap();
        bs.group(eventLoopGroup);
        bs.channel(serverChannel(eventLoopGroup, InetSocketAddress.class));
        bs.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(final Channel ch) {
                ch.pipeline().addLast(new HttpRequestDecoder());
                ch.pipeline().addLast(new HttpObjectAggregator(MAX_VALUE));
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
                        if (msg instanceof FullHttpRequest) {
                            ctx.writeAndFlush(writeAscii(ctx.alloc(), response))
                                    .addListener(ChannelFutureListener.CLOSE);
                        }
                        release(msg);
                    }
                });
            }
        });
        return (ServerSocketChannel) bs.bind(localAddress(0)).sync().channel();
    }
}
