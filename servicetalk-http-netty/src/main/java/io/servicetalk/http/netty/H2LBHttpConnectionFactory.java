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
import io.servicetalk.client.api.ConsumableEvent;
import io.servicetalk.client.api.internal.ReservableRequestConcurrencyController;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpClientConfig;
import io.servicetalk.tcp.netty.internal.TcpClientChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpConnector;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.TransportObserver;

import java.util.function.Function;
import javax.annotation.Nullable;

import static io.netty.handler.codec.http2.Http2CodecUtil.SMALLEST_MAX_CONCURRENT_STREAMS;
import static io.servicetalk.client.api.internal.ReservableRequestConcurrencyControllers.newController;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.netty.StreamingConnectionFactory.withSslConfigPeerHost;

final class H2LBHttpConnectionFactory<ResolvedAddress> extends AbstractLBHttpConnectionFactory<ResolvedAddress> {
    H2LBHttpConnectionFactory(
            final ReadOnlyHttpClientConfig config, final HttpExecutionContext executionContext,
            @Nullable final StreamingHttpConnectionFilterFactory connectionFilterFunction,
            final StreamingHttpRequestResponseFactory reqRespFactory,
            final ExecutionStrategy connectStrategy,
            final ConnectionFactoryFilter<ResolvedAddress, FilterableStreamingHttpConnection> connectionFactoryFilter,
            final Function<FilterableStreamingHttpConnection,
                    FilterableStreamingHttpLoadBalancedConnection> protocolBinding) {
        super(config, executionContext, version -> reqRespFactory,
                connectStrategy, connectionFactoryFilter, connectionFilterFunction,
                protocolBinding);
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
                        executionContext,
                        config.h2Config(), reqRespFactoryFunc.apply(HTTP_2_0), tcpConfig.flushStrategy(),
                        tcpConfig.isAsyncCloseOffloaded(), tcpConfig.idleTimeoutMs(),
                        new TcpClientChannelInitializer(tcpConfig, connectionObserver).andThen(
                                new H2ClientParentChannelInitializer(config.h2Config())), connectionObserver,
                        config.allowDropTrailersReadFromTransport()), observer);
    }

    @Override
    ReservableRequestConcurrencyController newConcurrencyController(
            final Publisher<? extends ConsumableEvent<Integer>> maxConcurrency, final Completable onClosing) {
        return newController(maxConcurrency, onClosing, SMALLEST_MAX_CONCURRENT_STREAMS);
    }
}
