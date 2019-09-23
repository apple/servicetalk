/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.netty.channel.Channel;

import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.client.api.internal.ReservableRequestConcurrencyControllers.newController;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.http.api.HttpEventKey.MAX_CONCURRENCY;
import static io.servicetalk.http.netty.ApplicationProtocolNames.HTTP_1_1;
import static io.servicetalk.http.netty.ApplicationProtocolNames.HTTP_2;
import static io.servicetalk.http.netty.DefaultSingleAddressHttpClientBuilder.reservedConnectionsPipelineEnabled;

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
    }

    @Override
    Single<FilterableStreamingHttpConnection> newFilterableConnection(final ResolvedAddress resolvedAddress) {
        // This state is read only, so safe to keep a copy across Subscribers
        final ReadOnlyTcpClientConfig roTcpClientConfig = config.tcpClientConfig();
        return TcpConnector.connect(null, resolvedAddress, roTcpClientConfig, executionContext)
                .flatMap(this::createConnection);
    }

    private Single<FilterableStreamingHttpConnection> createConnection(final Channel channel) {
        return new AlpnChannelSingle(channel, executionContext,
                new TcpClientChannelInitializer(config.tcpClientConfig()), false).flatMap(alpnContext -> {
            final ReadOnlyTcpClientConfig tcpConfig = config.tcpClientConfig();
            final String protocol = alpnContext.protocol();
            assert protocol != null;
            switch (protocol) {
                case HTTP_1_1:
                    return StreamingConnectionFactory.createConnection(channel, executionContext, config,
                            NoopChannelInitializer.INSTANCE)
                            .map(conn -> reservedConnectionsPipelineEnabled(config) ?
                                    new PipelinedStreamingHttpConnection(conn, config, executionContext,
                                            reqRespFactory) :
                                    new NonPipelinedStreamingHttpConnection(conn, executionContext,
                                            reqRespFactory, config.headersFactory()));
                case HTTP_2:
                    return H2ClientParentConnectionContext.initChannel(channel,
                            executionContext.bufferAllocator(), executionContext.executor(),
                            config.h2ClientConfig(), reqRespFactory, tcpConfig.flushStrategy(),
                            executionContext.executionStrategy(),
                            new H2ClientParentChannelInitializer(config.h2ClientConfig()));
                default:
                    return failed(new IllegalStateException("Unknown ALPN protocol negotiated: " + protocol));
            }
        });
    }

    @Override
    ReservableRequestConcurrencyController newConcurrencyController(final FilterableStreamingHttpConnection connection,
                                                                    final Completable onClosing) {
        // We set initialMaxConcurrency to 1 here because we don't know what type of connection will be created when
        // ALPN completes. The actual maxConcurrency value will be will be updated by the MAX_CONCURRENCY stream,
        // when we create a connection.
        return newController(connection.transportEventStream(MAX_CONCURRENCY), onClosing, 1);
    }
}
