/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.api;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.client.api.AutoRetryStrategyProvider;
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.BiIntPredicate;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;
import io.servicetalk.transport.api.TransportObserver;

import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.grpc.api.GrpcStatus.fromThrowable;

/**
 * A builder for building a <a href="https://www.grpc.io">gRPC</a> client.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
public abstract class GrpcClientBuilder<U, R> {

    private boolean appendedCatchAllFilter;

    /**
     * Sets the {@link Executor} for all clients created from this builder.
     *
     * @param executor {@link Executor} to use.
     * @return {@code this}.
     */
    public abstract GrpcClientBuilder<U, R> executor(Executor executor);

    /**
     * Sets the {@link IoExecutor} for all clients created from this builder.
     *
     * @param ioExecutor {@link IoExecutor} to use.
     * @return {@code this}.
     */
    public abstract GrpcClientBuilder<U, R> ioExecutor(IoExecutor ioExecutor);

    /**
     * Sets the {@link BufferAllocator} for all clients created from this builder.
     *
     * @param allocator {@link BufferAllocator} to use.
     * @return {@code this}.
     */
    public abstract GrpcClientBuilder<U, R> bufferAllocator(BufferAllocator allocator);

    /**
     * Sets the {@link GrpcExecutionStrategy} for all clients created from this builder.
     *
     * @param strategy {@link GrpcExecutionStrategy} to use.
     * @return {@code this}.
     * @see GrpcExecutionStrategies
     */
    public abstract GrpcClientBuilder<U, R> executionStrategy(GrpcExecutionStrategy strategy);

    /**
     * Add a {@link SocketOption} for all clients created by this builder.
     *
     * @param option the option to apply.
     * @param value the value.
     * @param <T> the type of the value.
     * @return {@code this}.
     * @see StandardSocketOptions
     * @see ServiceTalkSocketOptions
     */
    public abstract <T> GrpcClientBuilder<U, R> socketOption(SocketOption<T> option, T value);

    /**
     * Enables wire-logging for connections created by this builder.
     *
     * @param loggerName The name of the logger to log wire events.
     * @param logLevel The level to log at.
     * @param logUserData {@code true} to include user data (e.g. data, headers, etc.). {@code false} to exclude user
     * data and log only network events.
     * @return {@code this}.
     */
    public abstract GrpcClientBuilder<U, R> enableWireLogging(String loggerName, LogLevel logLevel,
                                                              BooleanSupplier logUserData);

    /**
     * Configurations of various underlying protocol versions.
     * <p>
     * <b>Note:</b> the order of specified protocols will reflect on priorities for ALPN in case the connections are
     * secured.
     *
     * @param protocols {@link HttpProtocolConfig} for each protocol that should be supported.
     * @return {@code this}.
     */
    public abstract GrpcClientBuilder<U, R> protocols(HttpProtocolConfig... protocols);

    /**
     * Set default timeout during which gRPC calls are expected to complete. This default will be used only if the
     * request metadata includes no timeout; any value specified in client request will supersede this default.
     *
     * @param defaultTimeout {@link Duration} of default timeout which must be positive non-zero.
     * @return {@code this}.
     */
    public abstract GrpcClientBuilder<U, R> defaultTimeout(Duration defaultTimeout);

    /**
     * Append the filter to the chain of filters used to decorate the {@link ConnectionFactory} used by this
     * builder.
     * <p>
     * Filtering allows you to wrap a {@link ConnectionFactory} and modify behavior of
     * {@link ConnectionFactory#newConnection(Object, TransportObserver)}.
     * Some potential candidates for filtering include logging and metrics.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.appendConnectionFactoryFilter(filter1)
     *            .appendConnectionFactoryFilter(filter2)
     *            .appendConnectionFactoryFilter(filter3)
     * </pre>
     * Calling {@link ConnectionFactory} wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ original connection factory
     * </pre>
     * @param factory {@link ConnectionFactoryFilter} to use.
     * @return {@code this}.
     */
    public abstract GrpcClientBuilder<U, R> appendConnectionFactoryFilter(
            ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> factory);

    /**
     * Append the filter to the chain of filters used to decorate the {@link StreamingHttpConnection} created by this
     * builder.
     * <p>
     * Filtering allows you to wrap a {@link StreamingHttpConnection} and modify behavior during request/response
     * processing
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.appendConnectionFilter(filter1).appendConnectionFilter(filter2).appendConnectionFilter(filter3)
     * </pre>
     * making a request to a connection wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ connection
     * </pre>
     *
     * @param factory {@link StreamingHttpConnectionFilterFactory} to decorate a {@link StreamingHttpConnection} for the
     * purpose of filtering.
     * @return {@code this}.
     */
    public abstract GrpcClientBuilder<U, R> appendConnectionFilter(StreamingHttpConnectionFilterFactory factory);

    /**
     * Append the filter to the chain of filters used to decorate the {@link StreamingHttpConnection} created by this
     * builder, for every request that passes the provided {@link Predicate}.
     * <p>
     * Filtering allows you to wrap a {@link StreamingHttpConnection} and modify behavior during request/response
     * processing
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.appendConnectionFilter(filter1).appendConnectionFilter(filter2).appendConnectionFilter(filter3)
     * </pre>
     * making a request to a connection wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ connection
     * </pre>
     * <p>
     * When overriding this method, delegate to {@code super} as it uses internal utilities to provide a consistent
     * execution flow.
     * @param predicate the {@link Predicate} to test if the filter must be applied.
     * @param factory {@link StreamingHttpConnectionFilterFactory} to decorate a {@link StreamingHttpConnection} for the
     * purpose of filtering.
     * @return {@code this}.
     */
    public abstract GrpcClientBuilder<U, R> appendConnectionFilter(Predicate<StreamingHttpRequest> predicate,
                                                                   StreamingHttpConnectionFilterFactory factory);

    /**
     * Set the SSL/TLS configuration.
     * @param sslConfig The configuration to use.
     * @return {@code this}.
     * @see io.servicetalk.transport.api.ClientSslConfigBuilder
     */
    public abstract GrpcClientBuilder<U, R> sslConfig(ClientSslConfig sslConfig);

    /**
     * Toggle inference of value to use instead of {@link ClientSslConfig#peerHost()}
     * from client's address when peer host is not specified. By default, inference is enabled.
     * @param shouldInfer value indicating whether inference is on ({@code true}) or off ({@code false}).
     * @return {@code this}
     */
    public abstract GrpcClientBuilder<U, R> inferPeerHost(boolean shouldInfer);

    /**
     * Toggle inference of value to use instead of {@link ClientSslConfig#peerPort()}
     * from client's address when peer port is not specified (equals {@code -1}). By default, inference is enabled.
     * @param shouldInfer value indicating whether inference is on ({@code true}) or off ({@code false}).
     * @return {@code this}
     */
    public abstract GrpcClientBuilder<U, R> inferPeerPort(boolean shouldInfer);

    /**
     * Toggle <a href="https://datatracker.ietf.org/doc/html/rfc6066#section-3">SNI</a>
     * hostname inference from client's address if not explicitly specified
     * via {@link #sslConfig(ClientSslConfig)}. By default, inference is enabled.
     * @param shouldInfer value indicating whether inference is on ({@code true}) or off ({@code false}).
     * @return {@code this}
     */
    public abstract GrpcClientBuilder<U, R> inferSniHostname(boolean shouldInfer);

    /**
     * Updates the automatic retry strategy for the clients generated by this builder. Automatic retries are done by
     * the clients automatically when allowed by the passed {@link AutoRetryStrategyProvider}. These retries are not a
     * substitute for user level retries which are designed to infer retry decisions based on request/error information.
     * Typically such user level retries are done using filters but can also be done differently per request
     * (eg: by using {@link Single#retry(BiIntPredicate)}).
     *
     * @param autoRetryStrategyProvider {@link AutoRetryStrategyProvider} for the automatic retry strategy.
     * @return {@code this}
     * @see io.servicetalk.client.api.DefaultAutoRetryStrategyProvider
     */
    public abstract GrpcClientBuilder<U, R> autoRetryStrategy(
            AutoRetryStrategyProvider autoRetryStrategyProvider);

    /**
     * Provides a means to convert {@link U} unresolved address type into a {@link CharSequence}.
     * An example of where this maybe used is to convert the {@link U} to a default host header. It may also
     * be used in the event of proxying.
     *
     * @param unresolvedAddressToHostFunction invoked to convert the {@link U} unresolved address type into a
     * {@link CharSequence} suitable for use in
     * <a href="https://tools.ietf.org/html/rfc7230#section-5.4">Host Header</a> format.
     * @return {@code this}
     */
    public abstract GrpcClientBuilder<U, R> unresolvedAddressToHost(
            Function<U, CharSequence> unresolvedAddressToHostFunction);

    /**
     * Disables automatically setting {@code Host} headers by inferring from the address or {@link HttpMetaData}.
     * <p>
     * This setting disables the default filter such that no {@code Host} header will be manipulated.
     *
     * @return {@code this}
     * @see #unresolvedAddressToHost(Function)
     * @deprecated Use {@link #hostHeaderFallback(boolean)}.
     */
    @Deprecated
    public abstract GrpcClientBuilder<U, R> disableHostHeaderFallback();

    /**
     * Configures automatically setting {@code Host} headers by inferring from the address or {@link HttpMetaData}.
     * <p>
     * When {@code false} is passed, this setting disables the default filter such that no {@code Host} header will be
     * manipulated.
     *
     * @param enable Whether a default filter for inferring the {@code Host} headers should be added.
     * @return {@code this}
     * @see #unresolvedAddressToHost(Function)
     */
    public GrpcClientBuilder<U, R> hostHeaderFallback(boolean enable) {
        throw new UnsupportedOperationException("Setting automatic host header fallback using this method" +
                " is not yet supported. Only deprecated variant is available currently: disableHostHeaderFallback().");
    }

    /**
     * Set a {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * @param serviceDiscoverer The {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * Lifecycle of the provided {@link ServiceDiscoverer} is managed externally and it should be
     * {@link ServiceDiscoverer#closeAsync() closed} after all built {@link GrpcClient}s are closed and
     * this {@link ServiceDiscoverer} is no longer needed.
     * @return {@code this}.
     */
    public abstract GrpcClientBuilder<U, R> serviceDiscoverer(
            ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer);

    /**
     * Set a {@link HttpLoadBalancerFactory} to create {@link LoadBalancer} instances.
     *
     * @param loadBalancerFactory {@link HttpLoadBalancerFactory} to create {@link LoadBalancer} instances.
     * @return {@code this}.
     */
    public abstract GrpcClientBuilder<U, R> loadBalancerFactory(HttpLoadBalancerFactory<R> loadBalancerFactory);

    /**
     * Append the filter to the chain of filters used to decorate the client created by this builder.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.appendHttpClientFilter(filter1).appendHttpClientFilter(filter2).appendHttpClientFilter(filter3)
     * </pre>
     * making a request to a client wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ client
     * </pre>
     *
     * @param factory {@link StreamingHttpClientFilterFactory} to decorate a client for the purpose of filtering.
     * @return {@code this}
     */
    public final GrpcClientBuilder<U, R> appendHttpClientFilter(StreamingHttpClientFilterFactory factory) {
        appendCatchAllFilterIfRequired();
        doAppendHttpClientFilter(factory);
        return this;
    }

    /**
     * Append the filter to the chain of filters used to decorate the client created by this builder, for every request
     * that passes the provided {@link Predicate}.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.appendHttpClientFilter(filter1).appendHttpClientFilter(filter2).appendHttpClientFilter(filter3)
     * </pre>
     * making a request to a client wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ client
     * </pre>
     *
     * @param predicate the {@link Predicate} to test if the filter must be applied.
     * @param factory {@link StreamingHttpClientFilterFactory} to decorate a client for the purpose of filtering.
     * @return {@code this}
     */
    public final GrpcClientBuilder<U, R> appendHttpClientFilter(Predicate<StreamingHttpRequest> predicate,
                                                                StreamingHttpClientFilterFactory factory) {
        appendCatchAllFilterIfRequired();
        doAppendHttpClientFilter(predicate, factory);
        return this;
    }

    /**
     * Builds a <a href="https://www.grpc.io">gRPC</a> client.
     *
     * @param clientFactory {@link GrpcClientFactory} to use.
     * @param <Client> <a href="https://www.grpc.io">gRPC</a> service that any client built from
     * this factory represents.
     * @param <Filter> Type for client filter
     * @param <FilterableClient> Type of filterable client.
     * @param <FilterFactory> Type of {@link GrpcClientFilterFactory}
     *
     * @return A <a href="https://www.grpc.io">gRPC</a> client.
     */
    public final <Client extends GrpcClient<?>,
            Filter extends FilterableClient, FilterableClient extends FilterableGrpcClient,
            FilterFactory extends GrpcClientFilterFactory<Filter, FilterableClient>> Client
    build(GrpcClientFactory<Client, ?, Filter, FilterableClient, FilterFactory> clientFactory) {
        return clientFactory.newClientForCallFactory(newGrpcClientCallFactory());
    }

    /**
     * Builds a blocking <a href="https://www.grpc.io">gRPC</a> client.
     *
     * @param clientFactory {@link GrpcClientFactory} to use.
     * @param <BlockingClient> Blocking <a href="https://www.grpc.io">gRPC</a> service that any
     * client built from this builder represents.
     * @param <Filter> Type for client filter
     * @param <FilterableClient> Type of filterable client.
     * @param <FilterFactory> Type of {@link GrpcClientFilterFactory}
     *
     * @return A blocking <a href="https://www.grpc.io">gRPC</a> client.
     */
    public final <BlockingClient extends BlockingGrpcClient<?>,
            Filter extends FilterableClient, FilterableClient extends FilterableGrpcClient,
            FilterFactory extends GrpcClientFilterFactory<Filter, FilterableClient>> BlockingClient
    buildBlocking(GrpcClientFactory<?, BlockingClient, Filter, FilterableClient, FilterFactory> clientFactory) {
        return clientFactory.newBlockingClientForCallFactory(newGrpcClientCallFactory());
    }

    /**
     * Create a new {@link GrpcClientCallFactory}.
     *
     * @return A new {@link GrpcClientCallFactory}.
     */
    protected abstract GrpcClientCallFactory newGrpcClientCallFactory();

    /**
     * Append the filter to the chain of filters used to decorate the client created by this builder.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.doAppendHttpClientFilter(filter1).doAppendHttpClientFilter(filter2).doAppendHttpClientFilter(filter3)
     * </pre>
     * making a request to a client wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ client
     * </pre>
     *
     * @param factory {@link StreamingHttpClientFilterFactory} to decorate a client for the purpose of filtering.
     */
    protected abstract void doAppendHttpClientFilter(StreamingHttpClientFilterFactory factory);

    /**
     * Append the filter to the chain of filters used to decorate the client created by this builder, for every request
     * that passes the provided {@link Predicate}.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.doAppendHttpClientFilter(filter1).doAppendHttpClientFilter(filter2).doAppendHttpClientFilter(filter3)
     * </pre>
     * making a request to a client wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ client
     * </pre>
     *
     * @param predicate the {@link Predicate} to test if the filter must be applied.
     * @param factory {@link StreamingHttpClientFilterFactory} to decorate a client for the purpose of filtering.
     */
    protected abstract void doAppendHttpClientFilter(Predicate<StreamingHttpRequest> predicate,
                                                     StreamingHttpClientFilterFactory factory);

    private void appendCatchAllFilterIfRequired() {
        if (!appendedCatchAllFilter) {
            doAppendHttpClientFilter(client -> new StreamingHttpClientFilter(client) {
                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final HttpExecutionStrategy strategy,
                                                                final StreamingHttpRequest request) {
                    final Single<StreamingHttpResponse> resp;
                    try {
                        resp = super.request(delegate, strategy, request);
                    } catch (Throwable t) {
                        return failed(toGrpcException(t));
                    }
                    return resp.onErrorMap(GrpcClientBuilder::toGrpcException);
                }
            });
            appendedCatchAllFilter = true;
        }
    }

    private static GrpcStatusException toGrpcException(Throwable cause) {
        return fromThrowable(cause).asException();
    }
}
