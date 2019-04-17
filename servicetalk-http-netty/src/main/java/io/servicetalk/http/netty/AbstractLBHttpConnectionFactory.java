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

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static java.util.Objects.requireNonNull;

abstract class AbstractLBHttpConnectionFactory<ResolvedAddress>
        implements ConnectionFactory<ResolvedAddress, StreamingHttpConnection> {
    private final ListenableAsyncCloseable close = emptyAsyncCloseable();
    @Nullable
    final StreamingHttpConnectionFilterFactory connectionFilterFunction;
    final ReadOnlyHttpClientConfig config;
    final HttpExecutionContext executionContext;
    final StreamingHttpRequestResponseFactory reqRespFactory;
    final HttpExecutionStrategyInfluencer strategyInfluencer;

    AbstractLBHttpConnectionFactory(final ReadOnlyHttpClientConfig config,
                                    final HttpExecutionContext executionContext,
                                    @Nullable final StreamingHttpConnectionFilterFactory connectionFilterFunction,
                                    final StreamingHttpRequestResponseFactory reqRespFactory,
                                    final HttpExecutionStrategyInfluencer strategyInfluencer) {
        this.connectionFilterFunction = connectionFilterFunction;
        this.config = requireNonNull(config);
        this.executionContext = requireNonNull(executionContext);
        this.reqRespFactory = requireNonNull(reqRespFactory);
        this.strategyInfluencer = strategyInfluencer;
    }

    @Override
    public final Completable onClose() {
        return close.onClose();
    }

    @Override
    public final Completable closeAsync() {
        return close.closeAsync();
    }

    @Override
    public final Completable closeAsyncGracefully() {
        return close.closeAsyncGracefully();
    }
}
