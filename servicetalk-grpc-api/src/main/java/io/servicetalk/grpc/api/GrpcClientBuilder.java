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
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
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

/**
 * A builder for building a <a href="https://www.grpc.io">gRPC</a> client.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
public abstract class GrpcClientBuilder<U, R> {

    /**
     * Initializes the underlying {@link SingleAddressHttpClientBuilder} used for the transport layer.
     * @param <U> unresolved address
     * @param <R> resolved address
     */
    @FunctionalInterface
    public interface HttpInitializer<U, R> {

        /**
         * Configures the underlying {@link SingleAddressHttpClientBuilder}.
         * @param builder The builder to customize the HTTP layer.
         */
        void initialize(SingleAddressHttpClientBuilder<U, R> builder);

        /**
         * Appends the passed {@link HttpInitializer} to this {@link HttpInitializer} such that this instance is
         * applied first and then the argument's {@link HttpInitializer}.
         * @param toAppend {@link HttpInitializer} to append.
         * @return A composite {@link HttpInitializer} after the append operation.
         */
        default HttpInitializer<U, R> append(HttpInitializer<U, R> toAppend) {
            return builder -> {
                initialize(builder);
                toAppend.initialize(builder);
            };
        }
    }

    /**
     * Set a function which can configure the underlying {@link SingleAddressHttpClientBuilder} used for
     * the transport layer.
     * <p>
     * Please note that this method shouldn't be mixed with the {@link Deprecated} methods of this class as the order
     * of operations would not be the same as the order in which the calls are made. Please migrate all of the calls
     * to this method.
     * @param initializer Initializes the underlying HTTP transport builder.
     * @return {@code this}.
     */
    public GrpcClientBuilder<U, R> initializeHttp(HttpInitializer<U, R> initializer) {
        throw new UnsupportedOperationException("Initializing the GrpcClientBuilder using this method is not yet" +
                " supported by " + getClass().getName());
    }

    /**
     * Sets the {@link Executor} for all clients created from this builder.
     *
     * @param executor {@link Executor} to use.
     * @return {@code this}.
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#executor(Executor)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    public GrpcClientBuilder<U, R> executor(Executor executor) {
        throw new UnsupportedOperationException("Method executor is not supported by " + getClass().getName());
    }

    /**
     * Sets the {@link IoExecutor} for all clients created from this builder.
     *
     * @param ioExecutor {@link IoExecutor} to use.
     * @return {@code this}.
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#ioExecutor(IoExecutor)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    public GrpcClientBuilder<U, R> ioExecutor(IoExecutor ioExecutor) {
        throw new UnsupportedOperationException("Method ioExecutor is not supported by " + getClass().getName());
    }

    /**
     * Sets the {@link BufferAllocator} for all clients created from this builder.
     *
     * @param allocator {@link BufferAllocator} to use.
     * @return {@code this}.
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#bufferAllocator(BufferAllocator)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    public GrpcClientBuilder<U, R> bufferAllocator(BufferAllocator allocator) {
        throw new UnsupportedOperationException("Method bufferAllocator is not supported by " + getClass().getName());
    }

    /**
     * Sets the {@link GrpcExecutionStrategy} for all clients created from this builder.
     *
     * @param strategy {@link GrpcExecutionStrategy} to use.
     * @return {@code this}.
     * @see GrpcExecutionStrategies
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#executionStrategy(HttpExecutionStrategy)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    public GrpcClientBuilder<U, R> executionStrategy(GrpcExecutionStrategy strategy) {
        throw new UnsupportedOperationException("Method executionStrategy is not supported by " + getClass().getName());
    }

    /**
     * Add a {@link SocketOption} for all clients created by this builder.
     *
     * @param option the option to apply.
     * @param value the value.
     * @param <T> the type of the value.
     * @return {@code this}.
     * @see StandardSocketOptions
     * @see ServiceTalkSocketOptions
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#socketOption(SocketOption, Object)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    public <T> GrpcClientBuilder<U, R> socketOption(SocketOption<T> option, T value) {
        throw new UnsupportedOperationException("Method socketOption is not supported by " + getClass().getName());
    }

    /**
     * Enables wire-logging for connections created by this builder.
     *
     * @param loggerName The name of the logger to log wire events.
     * @param logLevel The level to log at.
     * @param logUserData {@code true} to include user data (e.g. data, headers, etc.). {@code false} to exclude user
     * data and log only network events.
     * @return {@code this}.
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#enableWireLogging(String, LogLevel, BooleanSupplier)}
     * on the {@code builder} instance by implementing
     * {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    public GrpcClientBuilder<U, R> enableWireLogging(String loggerName, LogLevel logLevel,
                                                              BooleanSupplier logUserData) {
        throw new UnsupportedOperationException("Method enableWireLogging is not supported by " + getClass().getName());
    }

    /**
     * Configurations of various underlying protocol versions.
     * <p>
     * <b>Note:</b> the order of specified protocols will reflect on priorities for ALPN in case the connections are
     * secured.
     *
     * @param protocols {@link HttpProtocolConfig} for each protocol that should be supported.
     * @return {@code this}.
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#protocols(HttpProtocolConfig...)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    public GrpcClientBuilder<U, R> protocols(HttpProtocolConfig... protocols) {
        throw new UnsupportedOperationException("Method protocols is not supported by " + getClass().getName());
    }

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
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#appendConnectionFactoryFilter(ConnectionFactoryFilter)}
     * on the {@code builder} instance by implementing
     * {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    public GrpcClientBuilder<U, R> appendConnectionFactoryFilter(
            ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> factory) {
        throw new UnsupportedOperationException("Method appendConnectionFactoryFilter is not supported by " +
                getClass().getName());
    }

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
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#appendConnectionFilter(StreamingHttpConnectionFilterFactory)}
     * on the {@code builder} instance by implementing
     * {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    public GrpcClientBuilder<U, R> appendConnectionFilter(StreamingHttpConnectionFilterFactory factory) {
        throw new UnsupportedOperationException("Method appendConnectionFilter is not supported by " +
                getClass().getName());
    }

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
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#appendConnectionFilter(Predicate, StreamingHttpConnectionFilterFactory)}
     * on the {@code builder} instance by implementing
     * {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    public GrpcClientBuilder<U, R> appendConnectionFilter(Predicate<StreamingHttpRequest> predicate,
                                                                   StreamingHttpConnectionFilterFactory factory) {
        throw new UnsupportedOperationException("Method appendConnectionFilter is not supported by " +
                getClass().getName());
    }

    /**
     * Set the SSL/TLS configuration.
     * @param sslConfig The configuration to use.
     * @return {@code this}.
     * @see io.servicetalk.transport.api.ClientSslConfigBuilder
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#sslConfig(ClientSslConfig)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    public GrpcClientBuilder<U, R> sslConfig(ClientSslConfig sslConfig) {
        throw new UnsupportedOperationException("Method sslConfig is not supported by " + getClass().getName());
    }

    /**
     * Toggle inference of value to use instead of {@link ClientSslConfig#peerHost()}
     * from client's address when peer host is not specified. By default, inference is enabled.
     * @param shouldInfer value indicating whether inference is on ({@code true}) or off ({@code false}).
     * @return {@code this}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#inferPeerHost(boolean)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    public GrpcClientBuilder<U, R> inferPeerHost(boolean shouldInfer) {
        throw new UnsupportedOperationException("Method inferPeerHost is not supported by " + getClass().getName());
    }

    /**
     * Toggle inference of value to use instead of {@link ClientSslConfig#peerPort()}
     * from client's address when peer port is not specified (equals {@code -1}). By default, inference is enabled.
     * @param shouldInfer value indicating whether inference is on ({@code true}) or off ({@code false}).
     * @return {@code this}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#inferPeerPort(boolean)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    public GrpcClientBuilder<U, R> inferPeerPort(boolean shouldInfer) {
        throw new UnsupportedOperationException("Method inferPeerPort is not supported by " + getClass().getName());
    }

    /**
     * Toggle <a href="https://datatracker.ietf.org/doc/html/rfc6066#section-3">SNI</a>
     * hostname inference from client's address if not explicitly specified
     * via {@link #sslConfig(ClientSslConfig)}. By default, inference is enabled.
     * @param shouldInfer value indicating whether inference is on ({@code true}) or off ({@code false}).
     * @return {@code this}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#inferSniHostname(boolean)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    public GrpcClientBuilder<U, R> inferSniHostname(boolean shouldInfer) {
        throw new UnsupportedOperationException("Method inferSniHostname is not supported by " + getClass().getName());
    }

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
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#autoRetryStrategy(AutoRetryStrategyProvider)}
     * on the {@code builder} instance by implementing
     * {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    public GrpcClientBuilder<U, R> autoRetryStrategy(AutoRetryStrategyProvider autoRetryStrategyProvider) {
        throw new UnsupportedOperationException("Method autoRetryStrategy is not supported by " + getClass().getName());
    }

    /**
     * Provides a means to convert {@link U} unresolved address type into a {@link CharSequence}.
     * An example of where this maybe used is to convert the {@link U} to a default host header. It may also
     * be used in the event of proxying.
     *
     * @param unresolvedAddressToHostFunction invoked to convert the {@link U} unresolved address type into a
     * {@link CharSequence} suitable for use in
     * <a href="https://tools.ietf.org/html/rfc7230#section-5.4">Host Header</a> format.
     * @return {@code this}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    public GrpcClientBuilder<U, R> unresolvedAddressToHost(Function<U, CharSequence> unresolvedAddressToHostFunction) {
        throw new UnsupportedOperationException("Method unresolvedAddressToHost is not supported by " +
                getClass().getName());
    }

    /**
     * Configures automatically setting {@code Host} headers by inferring from the address or {@link HttpMetaData}.
     * <p>
     * When {@code false} is passed, this setting disables the default filter such that no {@code Host} header will be
     * manipulated.
     *
     * @param enable Whether a default filter for inferring the {@code Host} headers should be added.
     * @return {@code this}
     * @see #unresolvedAddressToHost(Function)
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#hostHeaderFallback(boolean)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    public GrpcClientBuilder<U, R> hostHeaderFallback(boolean enable) {
        throw new UnsupportedOperationException("Method hostHeaderFallback is not supported by " +
                getClass().getName());
    }

    /**
     * Set a {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * @param serviceDiscoverer The {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * Lifecycle of the provided {@link ServiceDiscoverer} is managed externally and it should be
     * {@link ServiceDiscoverer#closeAsync() closed} after all built {@link GrpcClient}s are closed and
     * this {@link ServiceDiscoverer} is no longer needed.
     * @return {@code this}.
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#serviceDiscoverer(ServiceDiscoverer)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    public GrpcClientBuilder<U, R> serviceDiscoverer(
            ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer) {
        throw new UnsupportedOperationException("Method serviceDiscoverer is not supported by " + getClass().getName());
    }

    /**
     * Set a {@link HttpLoadBalancerFactory} to create {@link LoadBalancer} instances.
     *
     * @param loadBalancerFactory {@link HttpLoadBalancerFactory} to create {@link LoadBalancer} instances.
     * @return {@code this}.
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#loadBalancerFactory(HttpLoadBalancerFactory)}
     * on the {@code builder} instance by implementing
     * {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    public GrpcClientBuilder<U, R> loadBalancerFactory(HttpLoadBalancerFactory<R> loadBalancerFactory) {
        throw new UnsupportedOperationException("Method loadBalancerFactory is not supported by " +
                getClass().getName());
    }

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
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#appendClientFilter(StreamingHttpClientFilterFactory)}
     * on the {@code builder} instance by implementing
     * {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    public final GrpcClientBuilder<U, R> appendHttpClientFilter(StreamingHttpClientFilterFactory factory) {
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
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#appendClientFilter(Predicate, StreamingHttpClientFilterFactory)}
     * on the {@code builder} instance by implementing
     * {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    public final GrpcClientBuilder<U, R> appendHttpClientFilter(Predicate<StreamingHttpRequest> predicate,
                                                                StreamingHttpClientFilterFactory factory) {
        doAppendHttpClientFilter(predicate, factory);
        return this;
    }

    /**
     * Builds a <a href="https://www.grpc.io">gRPC</a> client.
     *
     * @param clientFactory {@link GrpcClientFactory} to use.
     * @param <Client> <a href="https://www.grpc.io">gRPC</a> service that any client built from
     * this factory represents.
     * @param <FilterableClient> Type of filterable client.
     *
     * @return A <a href="https://www.grpc.io">gRPC</a> client.
     */
    public final <Client extends GrpcClient<?>, FilterableClient extends FilterableGrpcClient> Client
    build(GrpcClientFactory<Client, ?, FilterableClient> clientFactory) {
        return clientFactory.newClientForCallFactory(newGrpcClientCallFactory());
    }

    /**
     * Builds a blocking <a href="https://www.grpc.io">gRPC</a> client.
     *
     * @param clientFactory {@link GrpcClientFactory} to use.
     * @param <BlockingClient> Blocking <a href="https://www.grpc.io">gRPC</a> service that any
     * client built from this builder represents.
     * @param <FilterableClient> Type of filterable client.
     *
     * @return A blocking <a href="https://www.grpc.io">gRPC</a> client.
     */
    public final <BlockingClient extends BlockingGrpcClient<?>,
            FilterableClient extends FilterableGrpcClient> BlockingClient
    buildBlocking(GrpcClientFactory<?, BlockingClient, FilterableClient> clientFactory) {
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
     * @deprecated Users of this API should call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#appendClientFilter(StreamingHttpClientFilterFactory)}
     * on the {@code builder} instance by implementing
     * {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     * <p>
     * Please note: this method used to be abstract - keep the overridden implementation during transition phase.
     */
    @Deprecated
    protected void doAppendHttpClientFilter(StreamingHttpClientFilterFactory factory) {
        throw new UnsupportedOperationException("Appending client filters using doAppendHttpClientFilter method" +
                " is deprecated and not supported by " + getClass().getName());
    }

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
     * @deprecated Users of this API should call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#appendClientFilter(Predicate, StreamingHttpClientFilterFactory)}
     * on the {@code builder} instance by implementing
     * {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     * <p>
     * Please note: this method used to be abstract - keep the overridden implementation during transition phase.
     */
    @Deprecated
    protected void doAppendHttpClientFilter(Predicate<StreamingHttpRequest> predicate,
                                                     StreamingHttpClientFilterFactory factory) {
        throw new UnsupportedOperationException("Appending client filters using doAppendHttpClientFilter method" +
                " is deprecated and not supported by " + getClass().getName());
    }
}
