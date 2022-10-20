/*
 * Copyright © 2018-2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.ConsumableEvent;
import io.servicetalk.client.api.ReservableRequestConcurrencyController;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.transport.api.ConnectExecutionStrategy;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.IoThreadFactory;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.NoopTransportObserver;

import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.http.api.HttpEventKey.MAX_CONCURRENCY;
import static io.servicetalk.transport.api.TransportObservers.asSafeObserver;
import static java.util.Objects.requireNonNull;

abstract class AbstractLBHttpConnectionFactory<ResolvedAddress>
        implements ConnectionFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> {
    @Nullable
    private final StreamingHttpConnectionFilterFactory connectionFilterFunction;
    final ReadOnlyHttpClientConfig config;
    final HttpExecutionContext executionContext;
    final Function<HttpProtocolVersion, StreamingHttpRequestResponseFactory> reqRespFactoryFunc;
    private final ConnectionFactory<ResolvedAddress, FilterableStreamingHttpConnection> filterableConnectionFactory;
    private final ProtocolBinding protocolBinding;

    AbstractLBHttpConnectionFactory(
            final ReadOnlyHttpClientConfig config, final HttpExecutionContext executionContext,
            final Function<HttpProtocolVersion, StreamingHttpRequestResponseFactory> reqRespFactoryFunc,
            final ExecutionStrategy connectStrategy,
            final ConnectionFactoryFilter<ResolvedAddress, FilterableStreamingHttpConnection> connectionFactoryFilter,
            @Nullable final StreamingHttpConnectionFilterFactory connectionFilterFunction,
            final ProtocolBinding protocolBinding) {
        this.connectionFilterFunction = connectionFilterFunction;
        this.config = requireNonNull(config);
        this.executionContext = requireNonNull(executionContext);
        this.reqRespFactoryFunc = requireNonNull(reqRespFactoryFunc);
        requireNonNull(connectStrategy);
        this.protocolBinding = requireNonNull(protocolBinding);
        filterableConnectionFactory = connectionFactoryFilter.create(
                // provide the supplier of connections.
                new ConnectionFactory<ResolvedAddress, FilterableStreamingHttpConnection>() {
                    private final ListenableAsyncCloseable close = emptyAsyncCloseable();

                    @Override
                    public Single<FilterableStreamingHttpConnection> newConnection(
                            final ResolvedAddress ra, @Nullable final ContextMap context,
                            @Nullable final TransportObserver observer) {
                        Single<FilterableStreamingHttpConnection> connection =
                                newFilterableConnection(ra, observer == null ? NoopTransportObserver.INSTANCE :
                                asSafeObserver(observer));
                        return connectStrategy instanceof ConnectExecutionStrategy &&
                                ((ConnectExecutionStrategy) connectStrategy).isConnectOffloaded() ?
                                connection.publishOn(executionContext.executor(),
                                        IoThreadFactory.IoThread::currentThreadIsIoThread) :
                                connection;
                    }

                    @Override
                    public Completable onClose() {
                        return close.onClose();
                    }

                    @Override
                    public Completable onClosing() {
                        return close.onClosing();
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
    }

    @Override
    public final Single<FilterableStreamingHttpLoadBalancedConnection> newConnection(
            final ResolvedAddress resolvedAddress, @Nullable final ContextMap context,
            @Nullable final TransportObserver observer) {
        return filterableConnectionFactory.newConnection(resolvedAddress, context, observer)
                .map(conn -> {
                    // Apply connection filters:
                    FilterableStreamingHttpConnection filteredConnection =
                            connectionFilterFunction != null ? connectionFilterFunction.create(conn) : conn;
                    return protocolBinding.bind(filteredConnection,
                            newConcurrencyController(filteredConnection.transportEventStream(MAX_CONCURRENCY),
                                    filteredConnection.onClosing()), context);
                });
    }

    /**
     * The ultimate source of connections before filtering.
     *
     * @param resolvedAddress address of the connection
     * @param observer Observer on the connection state.
     * @return the connection instance.
     */
    abstract Single<FilterableStreamingHttpConnection> newFilterableConnection(
            ResolvedAddress resolvedAddress, TransportObserver observer);

    abstract ReservableRequestConcurrencyController newConcurrencyController(
            Publisher<? extends ConsumableEvent<Integer>> maxConcurrency, Completable onClosing);

    @Override
    public final Completable onClose() {
        return filterableConnectionFactory.onClose();
    }

    @Override
    public final Completable onClosing() {
        return filterableConnectionFactory.onClosing();
    }

    @Override
    public final Completable closeAsync() {
        return filterableConnectionFactory.closeAsync();
    }

    @Override
    public final Completable closeAsyncGracefully() {
        return filterableConnectionFactory.closeAsyncGracefully();
    }

    @FunctionalInterface
    interface ProtocolBinding {
        FilterableStreamingHttpLoadBalancedConnection bind(
                FilterableStreamingHttpConnection connection,
                ReservableRequestConcurrencyController concurrencyController,
                @Nullable ContextMap context);
    }
}
