/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.internal.ReservableRequestConcurrencyController;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;

import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.client.api.internal.ReservableRequestConcurrencyControllers.newController;
import static io.servicetalk.http.api.HttpEventKey.MAX_CONCURRENCY;
import static io.servicetalk.http.netty.StreamingConnectionFactory.buildStreaming;

final class PipelinedLBHttpConnectionFactory<ResolvedAddress,
        FLC extends FilterableStreamingHttpConnection & LoadBalancedConnection>
        extends AbstractLBHttpConnectionFactory<ResolvedAddress, FLC> {
    PipelinedLBHttpConnectionFactory(
            final ReadOnlyHttpClientConfig config, final HttpExecutionContext executionContext,
            @Nullable final StreamingHttpConnectionFilterFactory connectionFilterFunction,
            final StreamingHttpRequestResponseFactory reqRespFactory,
            final HttpExecutionStrategyInfluencer strategyInfluencer,
            final ConnectionFactoryFilter<ResolvedAddress, FilterableStreamingHttpConnection> connectionFactoryFilter,
            final Function<FilterableStreamingHttpConnection, FLC> protocolBinding) {
        super(config, executionContext, connectionFilterFunction, reqRespFactory, strategyInfluencer,
                connectionFactoryFilter, protocolBinding);
    }

    @Override
    Single<FilterableStreamingHttpConnection> newFilterableConnection(final ResolvedAddress resolvedAddress) {
        return buildStreaming(executionContext, resolvedAddress, config)
                .map(conn -> new PipelinedStreamingHttpConnection(conn, config, executionContext, reqRespFactory));
    }

    @Override
    ReservableRequestConcurrencyController newConcurrencyController(final FilterableStreamingHttpConnection connection,
                                                                    Completable onClosing) {
        return newController(connection.transportEventStream(MAX_CONCURRENCY), onClosing,
                config.maxPipelinedRequests());
    }
}
