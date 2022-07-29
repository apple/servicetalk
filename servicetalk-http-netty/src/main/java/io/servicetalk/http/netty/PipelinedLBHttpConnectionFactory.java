/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.ReservableRequestConcurrencyController;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.TransportObserver;

import javax.annotation.Nullable;

import static io.servicetalk.client.api.internal.ReservableRequestConcurrencyControllers.newController;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.netty.StreamingConnectionFactory.buildStreaming;

final class PipelinedLBHttpConnectionFactory<ResolvedAddress> extends AbstractLBHttpConnectionFactory<ResolvedAddress> {
    PipelinedLBHttpConnectionFactory(
            final ReadOnlyHttpClientConfig config, final HttpExecutionContext executionContext,
            @Nullable final StreamingHttpConnectionFilterFactory connectionFilterFunction,
            final StreamingHttpRequestResponseFactory reqRespFactory,
            final ExecutionStrategy connectStrategy,
            final ConnectionFactoryFilter<ResolvedAddress, FilterableStreamingHttpConnection> connectionFactoryFilter,
            final ProtocolBinding protocolBinding) {
        super(config, executionContext, version -> reqRespFactory, connectStrategy, connectionFactoryFilter,
                connectionFilterFunction, protocolBinding);
    }

    @Override
    Single<FilterableStreamingHttpConnection> newFilterableConnection(final ResolvedAddress resolvedAddress,
                                                                      final TransportObserver observer) {
        assert config.h1Config() != null;
        return buildStreaming(executionContext, resolvedAddress, config, observer)
                .map(conn -> new PipelinedStreamingHttpConnection(conn, config.h1Config(), executionContext,
                        reqRespFactoryFunc.apply(HTTP_1_1), config.allowDropTrailersReadFromTransport()));
    }

    @Override
    ReservableRequestConcurrencyController newConcurrencyController(
            final Publisher<? extends ConsumableEvent<Integer>> maxConcurrency, Completable onClosing) {
        assert config.h1Config() != null;
        return newController(maxConcurrency, onClosing, config.h1Config().maxPipelinedRequests());
    }
}
