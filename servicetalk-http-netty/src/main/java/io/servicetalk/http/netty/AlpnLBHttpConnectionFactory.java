/*
 * Copyright © 2019-2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.internal.ReservableRequestConcurrencyController;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.netty.AlpnChannelSingle.NoopChannelInitializer;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpClientConfig;
import io.servicetalk.tcp.netty.internal.TcpClientChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpConnector;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.TransportObserver;

import io.netty.channel.Channel;

import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.client.api.internal.ReservableRequestConcurrencyControllers.newController;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.http.api.HttpEventKey.MAX_CONCURRENCY;
import static io.servicetalk.http.netty.AlpnIds.HTTP_1_1;
import static io.servicetalk.http.netty.AlpnIds.HTTP_2;

final class AlpnLBHttpConnectionFactory<ResolvedAddress> extends AbstractLBHttpConnectionFactory<ResolvedAddress> {

    AlpnLBHttpConnectionFactory(
            final ReadOnlyHttpClientConfig config, final HttpExecutionContext executionContext,
            @Nullable final StreamingHttpConnectionFilterFactory connectionFilterFunction,
            final StreamingHttpRequestResponseFactory reqRespFactory,
            final HttpExecutionStrategyInfluencer strategyInfluencer,
            final ConnectionFactoryFilter<ResolvedAddress, FilterableStreamingHttpConnection> connectionFactoryFilter,
            final Function<FilterableStreamingHttpConnection,
                    FilterableStreamingHttpLoadBalancedConnection> protocolBinding) {
        super(config, executionContext, connectionFilterFunction, reqRespFactory, strategyInfluencer,
                connectionFactoryFilter, protocolBinding);
        assert config.h1Config() != null && config.h2Config() != null;
    }

    @Override
    Single<FilterableStreamingHttpConnection> newFilterableConnection(
            final ResolvedAddress resolvedAddress, @Nullable final TransportObserver observer) {
        // This state is read only, so safe to keep a copy across Subscribers
        final ReadOnlyTcpClientConfig roTcpClientConfig = config.tcpConfig();
        // We disable auto read by default so we can handle stuff in the ConnectionFilter before we accept any content.
        // In case ALPN negotiates h2, h2 connection MUST enable auto read for its Channel.
        return TcpConnector.connect(null, resolvedAddress, roTcpClientConfig, false,
                executionContext, channel -> createConnection(channel, observer));
    }

    private Single<FilterableStreamingHttpConnection> createConnection(
            final Channel channel, @Nullable final TransportObserver observer) {
        final ReadOnlyTcpClientConfig tcpConfig = this.config.tcpConfig();
        final ConnectionObserver connectionObserver = observer == null ? null : observer.onNewConnection();
        return new AlpnChannelSingle(channel,
                new TcpClientChannelInitializer(tcpConfig, connectionObserver), false).flatMap(protocol -> {
            switch (protocol) {
                case HTTP_1_1:
                    final H1ProtocolConfig h1Config = this.config.h1Config();
                    assert h1Config != null;
                    return StreamingConnectionFactory.createConnection(channel, executionContext, this.config,
                            NoopChannelInitializer.INSTANCE, connectionObserver)
                            .map(conn -> new PipelinedStreamingHttpConnection(conn, h1Config, executionContext,
                                    reqRespFactory));
                case HTTP_2:
                    final H2ProtocolConfig h2Config = this.config.h2Config();
                    assert h2Config != null;
                    return H2ClientParentConnectionContext.initChannel(channel,
                            executionContext.bufferAllocator(), executionContext.executor(),
                            h2Config, reqRespFactory, tcpConfig.flushStrategy(), tcpConfig.idleTimeoutMs(),
                            executionContext.executionStrategy(),
                            new H2ClientParentChannelInitializer(h2Config), connectionObserver);
                default:
                    return failed(new IllegalStateException("Unknown ALPN protocol negotiated: " + protocol));
            }
        });
    }

    @Override
    ReservableRequestConcurrencyController newConcurrencyController(final FilterableStreamingHttpConnection connection,
                                                                    final Completable onClosing) {
        // We set initialMaxConcurrency to 1 here because we don't know what type of connection will be created when
        // ALPN completes. The actual maxConcurrency value will be updated by the MAX_CONCURRENCY stream,
        // when we create a connection.
        return newController(connection.transportEventStream(MAX_CONCURRENCY), onClosing, 1);
    }
}
