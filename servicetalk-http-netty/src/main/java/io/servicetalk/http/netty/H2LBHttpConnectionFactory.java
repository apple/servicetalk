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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpClientConfig;
import io.servicetalk.tcp.netty.internal.TcpClientChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpConnector;

import javax.annotation.Nullable;

import static io.netty.handler.codec.http2.Http2CodecUtil.SMALLEST_MAX_CONCURRENT_STREAMS;
import static io.servicetalk.client.api.internal.ReservableRequestConcurrencyControllers.newController;
import static io.servicetalk.http.api.HttpEventKey.MAX_CONCURRENCY;

final class H2LBHttpConnectionFactory<ResolvedAddress> extends AbstractLBHttpConnectionFactory<ResolvedAddress> {
    H2LBHttpConnectionFactory(final ReadOnlyHttpClientConfig config,
                              final HttpExecutionContext executionContext,
                              @Nullable final StreamingHttpConnectionFilterFactory connectionFilterFunction,
                              final StreamingHttpRequestResponseFactory reqRespFactory,
                              final HttpExecutionStrategyInfluencer strategyInfluencer) {
        super(config, executionContext, connectionFilterFunction, reqRespFactory, strategyInfluencer);
    }

    @Override
    public Single<StreamingHttpConnection> newConnection(final ResolvedAddress resolvedAddress) {
        final ReadOnlyTcpClientConfig roTcpClientConfig = config.tcpClientConfig();
        // This state is read only, so safe to keep a copy across Subscribers
        return TcpConnector.connect(null, resolvedAddress, roTcpClientConfig, executionContext)
                .flatMap(channel -> H2ClientParentConnectionContext.initChannel(channel,
                        executionContext.bufferAllocator(), executionContext.executor(),
                        config.h2ClientConfig(), reqRespFactory, roTcpClientConfig.flushStrategy(),
                        executionContext.executionStrategy(),
                        new TcpClientChannelInitializer(roTcpClientConfig)))
                .map(filterableConnection -> {
                    FilterableStreamingHttpConnection filteredConnection = connectionFilterFunction != null ?
                            connectionFilterFunction.create(filterableConnection) : filterableConnection;
                    return new LoadBalancedStreamingHttpConnection(filteredConnection,
                            newController(filteredConnection.transportEventStream(MAX_CONCURRENCY),
                                    filterableConnection.onClosing(), SMALLEST_MAX_CONCURRENT_STREAMS),
                            executionContext.executionStrategy(), strategyInfluencer);
                });
    }
}
