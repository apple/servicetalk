/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.netty.H2PriorKnowledgeFeatureParityTest.EchoHttp2Handler;
import io.servicetalk.tcp.netty.internal.TcpServerConfig;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.ssl.SslContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.InetSocketAddress;
import javax.annotation.Nullable;

import static io.netty.handler.codec.http.HttpScheme.HTTP;
import static io.netty.handler.codec.http.HttpScheme.HTTPS;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_1;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_2;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.BuilderUtils.serverChannel;
import static io.servicetalk.transport.netty.internal.CloseUtils.safeSync;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class H2SchemeTest {

    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    private final EventLoopGroup serverEventLoopGroup = createIoExecutor(1, "server-io").eventLoopGroup();

    @Nullable
    private Channel serverAcceptorChannel;

    @AfterEach
    void tearDown() throws Exception {
        if (serverAcceptorChannel != null) {
            safeSync(serverAcceptorChannel.close());
        }
        safeSync(serverEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS));
    }

    private void setUp(Scenario scenario) throws Exception {
        SslContext sslContext;
        if (scenario == Scenario.H2_PLAINTEXT) {
            sslContext = null;
        } else {
            TcpServerConfig config = new TcpServerConfig();
            config.sslConfig(new ServerSslConfigBuilder(
                    DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                    .trustManager(DefaultTestCerts::loadClientCAPem)
                    .alpnProtocols(AlpnIds.HTTP_2)
                    .build());
            sslContext = config.asReadOnly().sslContext();
        }

        ServerBootstrap sb = new ServerBootstrap();
        sb.group(serverEventLoopGroup);
        sb.channel(serverChannel(serverEventLoopGroup, InetSocketAddress.class));
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(final Channel parentChannel) {
                if (sslContext != null) {
                    parentChannel.pipeline().addFirst(sslContext.newHandler(PooledByteBufAllocator.DEFAULT));
                }
                parentChannel.pipeline().addLast(Http2FrameCodecBuilder.forServer().build(),
                        new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
                            @Override
                            protected void initChannel(final Http2StreamChannel streamChannel) {
                                streamChannel.pipeline()
                                        .addLast(new SchemeValidatorHandler(sslContext != null ? HTTPS : HTTP));
                                streamChannel.pipeline().addLast(new EchoHttp2Handler());
                            }
                        }));
            }
        });
        serverAcceptorChannel = sb.bind(localAddress(0)).sync().channel();
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void test(Scenario scenario) throws Exception {
        setUp(scenario);
        assert serverAcceptorChannel != null;
        final SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder = newClientBuilder(
                serverHostAndPort(serverAcceptorChannel.localAddress()), CLIENT_CTX, HTTP_2);
        if (scenario != Scenario.H2_PLAINTEXT) {
            builder.sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                    .peerHost(serverPemHostname())
                    .build());
        }
        if (scenario == Scenario.ALPN) {
            builder.protocols(HTTP_2.config, HTTP_1.config);
        }
        try (BlockingHttpClient client = builder.buildBlocking()) {
            assertThat(client.request(client.get("/")).status(), is(OK));
        }
    }

    private enum Scenario {
        H2_PLAINTEXT,
        H2_OVER_SSL,
        ALPN
    }

    private static final class SchemeValidatorHandler extends ChannelInboundHandlerAdapter {

        private final HttpScheme expectedScheme;

        private SchemeValidatorHandler(final HttpScheme expectedScheme) {
            this.expectedScheme = expectedScheme;
        }

        @Override
        public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
            if (msg instanceof Http2HeadersFrame) {
                Http2Headers headers = ((Http2HeadersFrame) msg).headers();
                if (!expectedScheme.name().equals(headers.scheme())) {
                    throw new IllegalArgumentException("Unexpected :scheme received: " + headers.scheme() +
                            ", expected: " + expectedScheme);
                }
            }
            ctx.fireChannelRead(msg);
        }
    }
}
