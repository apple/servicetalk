/*
 * Copyright © 2019-2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.ProxyConnectException;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.netty.ProxyConnectChannelSingle.RetryableProxyConnectException;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpClientConfig;
import io.servicetalk.tcp.netty.internal.TcpClientChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpConnector;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ChannelCloseUtils;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.transport.netty.internal.DeferSslHandler;
import io.servicetalk.transport.netty.internal.StacklessClosedChannelException;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;

import java.nio.channels.ClosedChannelException;
import javax.annotation.Nullable;
import javax.net.ssl.SSLException;

import static io.netty.channel.ChannelOption.ALLOW_HALF_CLOSURE;
import static io.servicetalk.buffer.netty.BufferUtils.getByteBufAllocator;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.netty.AlpnLBHttpConnectionFactory.unknownAlpnProtocol;
import static io.servicetalk.http.netty.HeaderUtils.OBJ_EXPECT_CONTINUE;
import static io.servicetalk.http.netty.HttpDebugUtils.showPipeline;
import static io.servicetalk.http.netty.HttpExecutionContextUtils.channelExecutionContext;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.transport.netty.internal.CloseHandler.forPipelinedRequestResponse;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

/**
 * {@link AbstractLBHttpConnectionFactory} implementation that handles HTTP/1.1 CONNECT when a client is configured to
 * talk over HTTPS Proxy Tunnel.
 *
 * @param <ResolvedAddress> The type of resolved address.
 */
final class ProxyConnectLBHttpConnectionFactory<ResolvedAddress>
        extends AbstractLBHttpConnectionFactory<ResolvedAddress> {

    ProxyConnectLBHttpConnectionFactory(
            final ReadOnlyHttpClientConfig config, final HttpExecutionContext executionContext,
            @Nullable final StreamingHttpConnectionFilterFactory connectionFilterFunction,
            final StreamingHttpRequestResponseFactory reqRespFactory,
            final ExecutionStrategy connectStrategy,
            final ConnectionFactoryFilter<ResolvedAddress, FilterableStreamingHttpConnection> connectionFactoryFilter,
            final ProtocolBinding protocolBinding) {
        super(config, executionContext, version -> reqRespFactory, connectStrategy, connectionFactoryFilter,
                connectionFilterFunction, protocolBinding);
        assert config.hasProxy() : "Unexpected hasProxy flag";
        assert config.tcpConfig().sslContext() != null : "Proxy CONNECT works only for TLS connections";
        assert config.proxyConfig() != null : "ProxyConfig is required";
    }

    @Override
    Single<FilterableStreamingHttpConnection> newFilterableConnection(final ResolvedAddress resolvedAddress,
                                                                      final TransportObserver observer) {
        final H1ProtocolConfig h1Config = config.h1Config() != null ? config.h1Config() : h1Default();
        // Because we initialize HTTP/1.1 connection, autoRead must be set to false.
        return TcpConnector.connect(null, resolvedAddress, config.tcpConfig(), false, executionContext,
                (channel, connectionObserver) -> createConnection(channel, connectionObserver, h1Config), observer);
    }

    private Single<? extends FilterableStreamingHttpConnection> createConnection(
            final Channel channel, final ConnectionObserver observer, final H1ProtocolConfig h1Config) {
        final ChannelConfig channelConfig = channel.config();
        final CloseHandler closeHandler = forPipelinedRequestResponse(true, channelConfig);
        // Disable half-closure to simplify ProxyConnectHandler implementation
        channelConfig.setOption(ALLOW_HALF_CLOSURE, FALSE);
        return new ProxyConnectChannelSingle(channel,
                new TcpClientChannelInitializer(config.tcpConfig(), observer, executionContext, true)
                        .andThen(new HttpClientChannelInitializer(
                                getByteBufAllocator(executionContext.bufferAllocator()), h1Config, closeHandler)),
                observer, h1Config.headersFactory(), config.proxyConfig())
                .flatMap(ProxyConnectLBHttpConnectionFactory::handshake)
                .flatMap(protocol -> finishConnectionInitialization(protocol, channel, closeHandler, observer))
                .onErrorMap(cause -> handleException(cause, channel));
    }

    private static Single<String> handshake(final Channel channel) {
        assert channel.eventLoop().inEventLoop();

        final Single<String> result;
        final DeferSslHandler deferSslHandler = channel.pipeline().get(DeferSslHandler.class);
        if (deferSslHandler == null) {
            if (!channel.isActive()) {
                result = Single.failed(new ProxyConnectException(channel +
                        " Connection is closed, either received a 'Connection: closed' header or closed by the proxy",
                        StacklessClosedChannelException.newInstance(
                                ProxyConnectLBHttpConnectionFactory.class, "handshake")));
            } else {
                result = Single.failed(new ProxyConnectException(channel +
                        " Unexpected connection state: failed to find a handler of type " + DeferSslHandler.class +
                        " in the channel pipeline."));
            }
        } else {
            result = new AlpnChannelSingle(channel, NoopChannelInitializer.INSTANCE, ctx -> deferSslHandler.ready());
        }
        return result.shareContextOnSubscribe();
    }

    private Single<? extends FilterableStreamingHttpConnection> finishConnectionInitialization(
            final String protocol, final Channel channel, final CloseHandler closeHandler,
            final ConnectionObserver connectionObserver) {
        assert channel.eventLoop().inEventLoop();

        final ReadOnlyTcpClientConfig tcpConfig = config.tcpConfig();
        final Single<? extends FilterableStreamingHttpConnection> result;
        switch (protocol) {
            case AlpnIds.HTTP_1_1:
                assert config.h1Config() != null;
                // Re-enable half-closure to let CloseHandler work with DuplexChannel
                channel.config().setOption(ALLOW_HALF_CLOSURE, TRUE);
                result = showPipeline(DefaultNettyConnection.initChannel(channel,
                            channelExecutionContext(channel, executionContext), closeHandler,
                            tcpConfig.flushStrategy(), tcpConfig.idleTimeoutMs(), tcpConfig.sslConfig(),
                            NoopChannelInitializer.INSTANCE, HTTP_1_1, connectionObserver, true, OBJ_EXPECT_CONTINUE),
                        HTTP_1_1, channel)
                        .whenOnSuccess(conn -> conn.notifyConnectionEstablished(connectionObserver))
                        .map(conn -> new PipelinedStreamingHttpConnection(conn, config.h1Config(),
                                reqRespFactoryFunc.apply(HTTP_1_1), config.allowDropTrailersReadFromTransport()));
                break;
            case AlpnIds.HTTP_2:
                removeH1Handlers(channel);
                final H2ProtocolConfig h2Config = config.h2Config();
                assert h2Config != null;
                result = H2ClientParentConnectionContext.initChannel(channel, executionContext, h2Config,
                        reqRespFactoryFunc.apply(HTTP_2_0), tcpConfig.flushStrategy(), tcpConfig.idleTimeoutMs(),
                        tcpConfig.sslConfig(), new H2ClientParentChannelInitializer(h2Config), connectionObserver,
                        config.allowDropTrailersReadFromTransport())
                        .whenOnSuccess(conn -> conn.notifyConnectionEstablished(connectionObserver));
                break;
            default:
                result = unknownAlpnProtocol(protocol);
                break;
        }
        return result.shareContextOnSubscribe();
    }

    private static void removeH1Handlers(final Channel channel) {
        final ChannelPipeline pipeline = channel.pipeline();
        for (Class<? extends ChannelHandler> handlerClass : HttpClientChannelInitializer.handlers()) {
            pipeline.remove(handlerClass);
        }
    }

    private static Throwable handleException(final Throwable cause, final Channel channel) {
        // If the Channel is still active, close it in case of any error to free resources.
        if (channel.isActive()) {
            ChannelCloseUtils.close(channel, cause);
        }
        if (cause instanceof SSLException) {
            return cause;
        }
        if (cause instanceof ClosedChannelException) {
            return new RetryableProxyConnectException(channel + " Connection is closed, either received a " +
                    "'Connection: closed' header or closed by the proxy", cause);
        }
        if (!(cause instanceof ProxyConnectException)) {
            return new RetryableProxyConnectException(channel +
                    " Unexpected exception during an attempt to connect to a proxy", cause);
        }
        return cause;
    }
}
