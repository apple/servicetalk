/*
 * Copyright © 2018, 2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.internal.ReservableRequestConcurrencyController;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;
import io.servicetalk.transport.netty.internal.NoopTransportObserver;

import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.transport.api.TransportObservers.asSafeObserver;
import static java.util.Objects.requireNonNull;

abstract class AbstractLBHttpConnectionFactory<ResolvedAddress>
        implements ConnectionFactory<ResolvedAddress, LoadBalancedStreamingHttpConnection> {
    private final ListenableAsyncCloseable close = emptyAsyncCloseable();
    @Nullable
    final StreamingHttpConnectionFilterFactory connectionFilterFunction;
    final ReadOnlyHttpClientConfig config;
    final HttpExecutionContext executionContext;
    final StreamingHttpRequestResponseFactory reqRespFactory;
    final HttpExecutionStrategyInfluencer strategyInfluencer;
    final ConnectionFactory<ResolvedAddress, FilterableStreamingHttpConnection> filterableConnectionFactory;
    private final Function<FilterableStreamingHttpConnection,
            FilterableStreamingHttpLoadBalancedConnection> protocolBinding;

    AbstractLBHttpConnectionFactory(
            final ReadOnlyHttpClientConfig config, final HttpExecutionContext executionContext,
            @Nullable final StreamingHttpConnectionFilterFactory connectionFilterFunction,
            final StreamingHttpRequestResponseFactory reqRespFactory,
            final HttpExecutionStrategyInfluencer strategyInfluencer,
            final ConnectionFactoryFilter<ResolvedAddress, FilterableStreamingHttpConnection> connectionFactoryFilter,
            final Function<FilterableStreamingHttpConnection,
                    FilterableStreamingHttpLoadBalancedConnection> protocolBinding) {
        this.connectionFilterFunction = connectionFilterFunction;
        this.config = requireNonNull(config);
        this.executionContext = requireNonNull(executionContext);
        this.reqRespFactory = requireNonNull(reqRespFactory);
        this.strategyInfluencer = strategyInfluencer;
        filterableConnectionFactory = connectionFactoryFilter.create(
                new ConnectionFactory<ResolvedAddress, FilterableStreamingHttpConnection>() {
                    @Override
                    public Single<FilterableStreamingHttpConnection> newConnection(
                            final ResolvedAddress ra, @Nullable final TransportObserver observer) {
                        return newFilterableConnection(ra, observer == null ? NoopTransportObserver.INSTANCE :
                                asSafeObserver(observer));
                    }

                    @Override
                    public Completable onClose() {
                        return close.onClose();
                    }

                    @Override
                    public Completable closeAsync() {
                        return close.closeAsync();
                    }

                    @Override
                    public Completable closeAsyncGracefully() {
                        return close.closeAsyncGracefully();
                    }
        });
        this.protocolBinding = protocolBinding;
    }

    @Override
    public final Single<LoadBalancedStreamingHttpConnection> newConnection(
            final ResolvedAddress resolvedAddress, @Nullable final TransportObserver observer) {
        return filterableConnectionFactory.newConnection(resolvedAddress, observer)
                .map(conn -> {
                    FilterableStreamingHttpConnection filteredConnection = connectionFilterFunction != null ?
                            connectionFilterFunction.create(conn) : conn;
                    ConnectionContext ctx = filteredConnection.connectionContext();
                    Completable onClosing;
                    if (ctx instanceof NettyConnectionContext) {
                        onClosing = ((NettyConnectionContext) ctx).onClosing();
                    } else {
                        onClosing = filteredConnection.onClose();
                    }
                    return new LoadBalancedStreamingHttpConnection(protocolBinding.apply(filteredConnection),
                            newConcurrencyController(filteredConnection, onClosing),
                            executionContext.executionStrategy(), strategyInfluencer);
                });
    }

    abstract Single<FilterableStreamingHttpConnection> newFilterableConnection(
            ResolvedAddress resolvedAddress, TransportObserver observer);

    abstract ReservableRequestConcurrencyController newConcurrencyController(
            FilterableStreamingHttpConnection connection, Completable onClosing);

    @Override
    public final Completable onClose() {
        return filterableConnectionFactory.onClose();
    }

    @Override
    public final Completable closeAsync() {
        return filterableConnectionFactory.closeAsync();
    }

    @Override
    public final Completable closeAsyncGracefully() {
        return filterableConnectionFactory.closeAsyncGracefully();
    }
}
