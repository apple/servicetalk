/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.transport.api.HostAndPort;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
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
import io.netty.handler.codec.http.HttpUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.net.InetSocketAddress;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.BuilderUtils.serverChannel;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createEventLoopGroup;
import static java.lang.Thread.NORM_PRIORITY;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class ServiceTalkToNettyContentCodingCompatibilityTest extends ServiceTalkContentCodingTest {

    private EventLoopGroup serverEventLoopGroup;
    private Channel serverAcceptorChannel;
    private HttpClient client;

    public ServiceTalkToNettyContentCodingCompatibilityTest(Scenario scenario) {
        super(scenario);
    }

    @Before
    public void start() {
        serverEventLoopGroup = createEventLoopGroup(2, new DefaultThreadFactory("server-io", true, NORM_PRIORITY));
        serverAcceptorChannel = newNettyServer();
        InetSocketAddress serverAddress = (InetSocketAddress) serverAcceptorChannel.localAddress();
        client = newServiceTalkClient(HostAndPort.of(serverAddress), scenario);
    }

    @After
    public void finish() throws Exception {
        serverAcceptorChannel.close().syncUninterruptibly();
        serverEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
        client.close();
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
    public void testCompatibility() throws Exception {
        assumeFalse("Only testing H1 scenarios yet.", scenario.isH2);
        assumeTrue("Only testing successful configurations; Netty doesn't have knowledge " +
                "about unsupported compression types.", scenario.valid);

        super.testCompatibility();
    }

    @Override
    protected HttpClient client() {
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

                boolean keepAlive = HttpUtil.isKeepAlive(req);
                FullHttpResponse response = new DefaultFullHttpResponse(req.protocolVersion(),
                        OK, wrappedBuffer(CONTENT));

                response.headers()
                        .set(CONTENT_TYPE, TEXT_PLAIN)
                        .setInt(CONTENT_LENGTH, response.content().readableBytes());

                if (keepAlive) {
                    if (!req.protocolVersion().isKeepAliveDefault()) {
                        response.headers().set(CONNECTION, KEEP_ALIVE);
                    }
                } else {
                    response.headers().set(CONNECTION, CLOSE);
                }

                ChannelFuture f = ctx.write(response);

                if (!keepAlive) {
                    f.addListener(ChannelFutureListener.CLOSE);
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
