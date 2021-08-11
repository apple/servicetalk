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

import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.ContentEncodingHttpRequesterFilter;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatus;
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

import java.net.InetSocketAddress;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_ENCODING;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.BuilderUtils.serverChannel;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createEventLoopGroup;
import static java.lang.Thread.NORM_PRIORITY;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ServiceTalkContentEncodingCompatibilityTest extends BaseContentEncodingTest {
    @Override
    protected void runTest(final HttpProtocol protocol, final Encoder clientEncoding, final Decoders clientDecoder,
                           final Encoders serverEncoder, final Decoders serverDecoder, final boolean isValid)
            throws Throwable {
        assumeFalse(protocol.version.equals(HTTP_2_0), "Only testing H1 scenarios yet.");
        assumeTrue(isValid, "Only testing successful configurations; Netty doesn't have knowledge " +
                "about unsupported compression types.");

        EventLoopGroup serverEventLoopGroup = createEventLoopGroup(2,
                new DefaultThreadFactory("server-io", true, NORM_PRIORITY));
        Channel serverAcceptorChannel = null;
        try {
            ServerBootstrap sb = new ServerBootstrap();
            sb.group(serverEventLoopGroup);
            sb.channel(serverChannel(serverEventLoopGroup, InetSocketAddress.class));

            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(final Channel ch) {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast(new HttpServerCodec());
                    if (!serverDecoder.group.decoders().isEmpty()) {
                        p.addLast(new HttpContentDecompressor());
                    }
                    if (!serverEncoder.list.isEmpty()) {
                        p.addLast(new HttpContentCompressor());
                    }
                    p.addLast(EchoServerHandler.INSTANCE);
                }
            });
            serverAcceptorChannel = sb.bind(localAddress(0)).syncUninterruptibly().channel();

            try (BlockingHttpClient client = HttpClients.forSingleAddress(
                    HostAndPort.of((InetSocketAddress) serverAcceptorChannel.localAddress()))
                    .protocols(protocol.config)
                    .appendClientFilter(new ContentEncodingHttpRequesterFilter(clientDecoder.group))
                    .buildBlocking()) {
                HttpResponse response = client.request(client.get("/").contentEncoding(clientEncoding.encoder)
                        .payloadBody(payloadAsString((byte) 'a'), textSerializerUtf8()));

                assertThat(response.status(), is(HttpResponseStatus.OK));

                // content encoding should be stripped by the time the decoding is done.
                assertThat(response.headers().get(CONTENT_ENCODING), nullValue());

                assertEquals(payloadAsString((byte) 'b'), response.payloadBody(textSerializerUtf8()));
            }
        } finally {
            if (serverAcceptorChannel != null) {
                serverAcceptorChannel.close().syncUninterruptibly();
            }
            serverEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
        }
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
