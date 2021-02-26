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

import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.transport.api.HostAndPort;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpServerCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.BuilderUtils.serverChannel;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createEventLoopGroup;
import static java.lang.Thread.NORM_PRIORITY;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Deprecated
class ServiceTalkToNettyContentCodingCompatibilityTest extends ServiceTalkContentCodingTest {

    private EventLoopGroup serverEventLoopGroup;
    private Channel serverAcceptorChannel;
    private BlockingHttpClient client;

    @Override
    void start() {
        serverEventLoopGroup = createEventLoopGroup(2, new DefaultThreadFactory("server-io", true, NORM_PRIORITY));
        serverAcceptorChannel = newNettyServer();
        InetSocketAddress serverAddress = (InetSocketAddress) serverAcceptorChannel.localAddress();
        client = newServiceTalkClient(HostAndPort.of(serverAddress), scenario, errors);
    }

    @Override
    @AfterEach
    void finish() throws Exception {
        if (serverAcceptorChannel != null) {
            serverAcceptorChannel.close().syncUninterruptibly();
        }
        if (serverEventLoopGroup != null) {
            serverEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
        }
        if (client != null) {
            client.close();
        }
    }

    private Channel newNettyServer() {
        ServerBootstrap sb = new ServerBootstrap();
        sb.group(serverEventLoopGroup);
        sb.channel(serverChannel(serverEventLoopGroup, InetSocketAddress.class));

        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(final Channel ch) {
                ChannelPipeline p = ch.pipeline();
                p.addLast(new HttpServerCodec());
                if (!scenario.serverSupported.isEmpty()) {
                    p.addLast(new HttpContentDecompressor());
                    p.addLast(new HttpContentCompressor());
                }
                p.addLast(EchoServerHandler.INSTANCE);
            }
        });
        return sb.bind(localAddress(0)).syncUninterruptibly().channel();
    }

    @Override
    @ParameterizedTest(name = "{index}, protocol={0}, server=[{1}], client=[{2}], request={3}, pass={4}")
    @MethodSource("params")
    void testCompatibility(final HttpProtocol protocol, final Codings serverCodings,
                           final Codings clientCodings, final Compression compression,
                           final boolean valid) throws Throwable {
        setUp(protocol, serverCodings, clientCodings, compression, valid);
        assumeFalse(scenario.protocol.version.equals(HTTP_2_0), "Only testing H1 scenarios yet.");
        assumeTrue(scenario.valid, "Only testing successful configurations; Netty doesn't have knowledge " +
                "about unsupported compression types.");
        start();

        if (scenario.valid) {
            assertSuccessful(scenario.requestEncoding);
        } else {
            assertNotSupported(scenario.requestEncoding);
        }

        verifyNoErrors();
    }

    @Override
    BlockingHttpClient client() {
        return client;
    }

    @ChannelHandler.Sharable
    static class EchoServerHandler extends SimpleChannelInboundHandler<HttpObject> {
        static final EchoServerHandler INSTANCE = new EchoServerHandler();

        private static final byte[] CONTENT = payload((byte) 'b');

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
            if (msg instanceof io.netty.handler.codec.http.HttpRequest) {
                io.netty.handler.codec.http.HttpRequest req = (io.netty.handler.codec.http.HttpRequest) msg;
                FullHttpResponse response = new DefaultFullHttpResponse(req.protocolVersion(),
                        OK, wrappedBuffer(CONTENT));

                response.headers()
                        .set(CONTENT_TYPE, TEXT_PLAIN)
                        .setInt(CONTENT_LENGTH, response.content().readableBytes());

                ctx.write(response);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
