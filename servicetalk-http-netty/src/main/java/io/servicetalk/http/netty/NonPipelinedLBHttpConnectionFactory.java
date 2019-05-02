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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;

import javax.annotation.Nullable;

import static io.servicetalk.client.api.internal.ReservableRequestConcurrencyControllers.newSingleController;
import static io.servicetalk.http.api.HttpEventKey.MAX_CONCURRENCY;
import static io.servicetalk.http.netty.DefaultHttpConnectionBuilder.buildStreaming;

final class NonPipelinedLBHttpConnectionFactory<ResolvedAddress>
        extends AbstractLBHttpConnectionFactory<ResolvedAddress> {
    NonPipelinedLBHttpConnectionFactory(final ReadOnlyHttpClientConfig config,
                                        final HttpExecutionContext executionContext,
                                        @Nullable final StreamingHttpConnectionFilterFactory connectionFilterFunction,
                                        final StreamingHttpRequestResponseFactory reqRespFactory,
                                        final HttpExecutionStrategyInfluencer strategyInfluencer) {
        super(config, executionContext, connectionFilterFunction, reqRespFactory, strategyInfluencer);
    }

    @Override
    public Single<StreamingHttpConnection> newConnection(final ResolvedAddress resolvedAddress) {
        return buildStreaming(executionContext, resolvedAddress, config).map(conn -> {
            FilterableStreamingHttpConnection mappedConnection = new NonPipelinedStreamingHttpConnection(conn,
                    executionContext, reqRespFactory);

            FilterableStreamingHttpConnection filteredConnection = connectionFilterFunction != null ?
                    connectionFilterFunction.create(mappedConnection) : mappedConnection;
            return new LoadBalancedStreamingHttpConnection(filteredConnection, newSingleController(
                    filteredConnection.transportEventStream(MAX_CONCURRENCY), conn.onClosing()),
                    executionContext.executionStrategy(), strategyInfluencer);
        });
    }
}
