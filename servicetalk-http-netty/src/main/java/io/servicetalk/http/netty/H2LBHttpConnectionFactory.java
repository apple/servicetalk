/*
 * Copyright © 2019-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.ReservableRequestConcurrencyController;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpClientConfig;
import io.servicetalk.tcp.netty.internal.TcpClientChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpConnector;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.TransportObserver;

import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.netty.H2ClientParentConnectionContext.DEFAULT_H2_MAX_CONCURRENCY_EVENT;
import static io.servicetalk.http.netty.ReservableRequestConcurrencyControllers.newController;
import static io.servicetalk.http.netty.StreamingConnectionFactory.withSslConfigPeerHost;

final class H2LBHttpConnectionFactory<ResolvedAddress> extends AbstractLBHttpConnectionFactory<ResolvedAddress> {
    H2LBHttpConnectionFactory(
            final ReadOnlyHttpClientConfig config, final HttpExecutionContext executionContext,
            @Nullable final StreamingHttpConnectionFilterFactory connectionFilterFunction,
            final StreamingHttpRequestResponseFactory reqRespFactory,
            final ExecutionStrategy connectStrategy,
            final ConnectionFactoryFilter<ResolvedAddress, FilterableStreamingHttpConnection> connectionFactoryFilter,
            final ProtocolBinding protocolBinding) {
        super(config, executionContext, version -> reqRespFactory,
                connectStrategy, connectionFactoryFilter, connectionFilterFunction, protocolBinding);
    }

    @Override
    Single<FilterableStreamingHttpConnection> newFilterableConnection(
            final ResolvedAddress resolvedAddress, final TransportObserver observer) {
        assert config.h2Config() != null;
        // This state is read only, so safe to keep a copy across Subscribers
        final ReadOnlyTcpClientConfig tcpConfig = withSslConfigPeerHost(resolvedAddress, config.tcpConfig());
        // Auto read is required for h2
        return TcpConnector.connect(null, resolvedAddress, tcpConfig, true, executionContext,
                (channel, connectionObserver) -> H2ClientParentConnectionContext.initChannel(channel,
                        executionContext, config.h2Config(), reqRespFactoryFunc.apply(HTTP_2_0),
                        tcpConfig.flushStrategy(), tcpConfig.idleTimeoutMs(), tcpConfig.sslConfig(),
                        new TcpClientChannelInitializer(tcpConfig, connectionObserver).andThen(
                                new H2ClientParentChannelInitializer(config.h2Config())), connectionObserver,
                        config.allowDropTrailersReadFromTransport()), observer);
    }

    @Override
    ReservableRequestConcurrencyController newConcurrencyController(
            final FilterableStreamingHttpConnection connection) {
        return newController(connection, DEFAULT_H2_MAX_CONCURRENCY_EVENT.event());
    }
}
