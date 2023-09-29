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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.netty.AlpnChannelSingle.NoopChannelInitializer;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpClientConfig;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.transport.netty.internal.DeferSslHandler;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopConnectionObserver;
import io.servicetalk.transport.netty.internal.StacklessClosedChannelException;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;

import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpContextKeys.HTTP_EXECUTION_STRATEGY_KEY;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SUCCESSFUL_2XX;
import static io.servicetalk.http.netty.AlpnLBHttpConnectionFactory.unknownAlpnProtocol;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.StreamingConnectionFactory.buildStreaming;
import static io.servicetalk.utils.internal.ThrowableUtils.addSuppressed;

/**
 * {@link AbstractLBHttpConnectionFactory} implementation that handles HTTP/1.1 CONNECT when a client is configured to
 * talk over HTTPS Proxy Tunnel.
 *
 * @param <ResolvedAddress> The type of resolved address.
 */
final class ProxyConnectLBHttpConnectionFactory<ResolvedAddress>
        extends AbstractLBHttpConnectionFactory<ResolvedAddress> {

    private final String connectAddress;

    ProxyConnectLBHttpConnectionFactory(
            final ReadOnlyHttpClientConfig config, final HttpExecutionContext executionContext,
            @Nullable final StreamingHttpConnectionFilterFactory connectionFilterFunction,
            final StreamingHttpRequestResponseFactory reqRespFactory,
            final ExecutionStrategy connectStrategy,
            final ConnectionFactoryFilter<ResolvedAddress, FilterableStreamingHttpConnection> connectionFactoryFilter,
            final ProtocolBinding protocolBinding) {
        super(config, executionContext, version -> reqRespFactory, connectStrategy, connectionFactoryFilter,
                connectionFilterFunction, protocolBinding);
        assert config.tcpConfig().sslContext() != null : "Proxy CONNECT works only for TLS connections";
        assert config.connectAddress() != null : "Address (authority) for CONNECT request is required";
        connectAddress = config.connectAddress().toString();
    }

    @Override
    Single<FilterableStreamingHttpConnection> newFilterableConnection(final ResolvedAddress resolvedAddress,
                                                                      final TransportObserver observer) {
        final H1ProtocolConfig h1Config = config.h1Config() != null ? config.h1Config() : h1Default();
        return buildStreaming(executionContext, resolvedAddress, config.tcpConfig(), h1Config, config.hasProxy(),
                observer)
                // Always create PipelinedStreamingHttpConnection because:
                // 1. buildStreaming creates a CloseHandler for pipelined request-response
                // 2. in case ALPN negotiates HTTP/1.x we won't need to change the connection
                .map(c -> new PipelinedStreamingHttpConnection(c, h1Config,
                        reqRespFactoryFunc.apply(HTTP_1_1), config.allowDropTrailersReadFromTransport()))
                .flatMap(this::processConnect);
    }

    // Visible for testing
    Single<FilterableStreamingHttpConnection> processConnect(final NettyFilterableStreamingHttpConnection c) {
        try {
            // Send CONNECT request: https://datatracker.ietf.org/doc/html/rfc9110#section-9.3.6
            // Host header value must be equal to CONNECT request target, see
            // https://github.com/haproxy/haproxy/issues/1159
            // https://datatracker.ietf.org/doc/html/rfc7230#section-5.4:
            //   If the target URI includes an authority component, then a client MUST send a field-value
            //   for Host that is identical to that authority component
            final StreamingHttpRequest request = c.connect(connectAddress).setHeader(HOST, connectAddress);
            // No need to offload because there is no user code involved
            request.context().put(HTTP_EXECUTION_STRATEGY_KEY, offloadNone());
            return c.request(request)
                    .flatMap(response -> {
                        // Successful response to CONNECT never has a message body, and we are not interested in payload
                        // body for any non-200 status code. Drain it asap to free connection and RS resources before
                        // starting TLS handshake or propagating an error. We do this after verifying the status to
                        // preserve ProxyResponseException even if draining fails with an exception.
                        if (response.status().statusClass() != SUCCESSFUL_2XX) {
                            return drainPropagateError(response, new ProxyResponseException(c +
                                    " Non-successful response from proxy CONNECT " + connectAddress, response.status()))
                                    .shareContextOnSubscribe();
                        }
                        return response.messageBody().ignoreElements()
                                .concat(handshake(c))
                                .shareContextOnSubscribe();
                    })
                    // Close recently created connection in case of any error while it connects to the proxy:
                    .onErrorResume(t -> closePropagateError(c, t));
            // We do not apply shareContextOnSubscribe() here to isolate a context for `CONNECT` request.
        } catch (Throwable t) {
            return closePropagateError(c, t);
        }
    }

    private Single<FilterableStreamingHttpConnection> handshake(
            final NettyFilterableStreamingHttpConnection connection) {
        return Single.defer(() -> {
            final Channel channel = connection.nettyChannel();
            assert channel.eventLoop().inEventLoop();

            final Single<String> result;
            final DeferSslHandler deferSslHandler = channel.pipeline().get(DeferSslHandler.class);
            if (deferSslHandler == null) {
                if (!channel.isActive()) {
                    result = Single.failed(StacklessClosedChannelException.newInstance(connection +
                                    " Connection is closed, either received a 'Connection: closed' header or" +
                                    " closed by the proxy. Investigate logs on a proxy side to identify the cause.",
                            ProxyConnectLBHttpConnectionFactory.class, "handshake"));
                } else {
                    result = Single.failed(new IllegalStateException(connection +
                            " Unexpected connection state: failed to find a handler of type " +
                            DeferSslHandler.class + " in the channel pipeline."));
                }
            } else {
                result = new AlpnChannelSingle(channel, NoopChannelInitializer.INSTANCE, __ -> deferSslHandler.ready());
            }
            return result.shareContextOnSubscribe();
        }).flatMap(protocol -> {
            final Single<? extends FilterableStreamingHttpConnection> result;
            switch (protocol) {
                case AlpnIds.HTTP_1_1:
                    // Nothing to do, HTTP/1.1 pipeline is already initialized
                    result = Single.succeeded(connection);
                    break;
                case AlpnIds.HTTP_2:
                    final Channel channel = connection.nettyChannel();
                    assert channel.eventLoop().inEventLoop();
                    removeH1Handlers(channel);
                    result = initializeH2Connection(channel);
                    break;
                default:
                    result = unknownAlpnProtocol(protocol);
                    break;
            }
            return result.shareContextOnSubscribe();
        });
    }

    private static void removeH1Handlers(final Channel channel) {
        final ChannelPipeline pipeline = channel.pipeline();
        pipeline.remove(DefaultNettyConnection.handlerClass());
        for (Class<? extends ChannelHandler> handlerClass : HttpClientChannelInitializer.handlers()) {
            pipeline.remove(handlerClass);
        }
    }

    private Single<? extends FilterableStreamingHttpConnection> initializeH2Connection(final Channel channel) {
        final H2ProtocolConfig h2Config = config.h2Config();
        assert h2Config != null;
        final ReadOnlyTcpClientConfig tcpConfig = config.tcpConfig();
        return H2ClientParentConnectionContext.initChannel(channel, executionContext, h2Config,
                reqRespFactoryFunc.apply(HTTP_2_0), tcpConfig.flushStrategy(), tcpConfig.idleTimeoutMs(),
                tcpConfig.sslConfig(), new H2ClientParentChannelInitializer(h2Config),
                // FIXME: propagate real observer
                NoopConnectionObserver.INSTANCE, config.allowDropTrailersReadFromTransport());
    }

    private static Single<FilterableStreamingHttpConnection> drainPropagateError(
            final StreamingHttpResponse response, final Throwable error) {
        return safeCompletePropagateError(response.messageBody().ignoreElements(), error);
    }

    private static Single<FilterableStreamingHttpConnection> closePropagateError(
            final FilterableStreamingHttpConnection connection, final Throwable error) {
        return safeCompletePropagateError(connection.closeAsync(), error);
    }

    private static Single<FilterableStreamingHttpConnection> safeCompletePropagateError(
            final Completable completable, final Throwable error) {
        return completable
                .onErrorResume(completableError -> Completable.failed(addSuppressed(error, completableError)))
                .concat(Single.failed(error));
    }
}
