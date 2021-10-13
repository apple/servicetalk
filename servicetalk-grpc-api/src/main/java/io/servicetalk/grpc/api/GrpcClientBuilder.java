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
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.grpc.internal.DeadlineUtils;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.IoExecutor;

import java.net.SocketOption;
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
public abstract class GrpcClientBuilder<U, R>
        implements SingleAddressGrpcClientBuilder<U, R, ServiceDiscovererEvent<R>> {

    /**
     * Initializes the underlying {@link SingleAddressHttpClientBuilder} used for the transport layer.
     * @param <U> unresolved address
     * @param <R> resolved address
     */
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
     * gRPC timeout is stored in context as a deadline so that when propagated to a new request the remaining time to be
     * included in the request can be calculated.
     *
     * @deprecated Do not use. This is internal implementation details that users should not depend on.
     */
    @Deprecated
    protected static final AsyncContextMap.Key<Long> GRPC_DEADLINE_KEY = DeadlineUtils.GRPC_DEADLINE_KEY;

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
     * {@inheritDoc}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#executor(Executor)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    @Override
    public GrpcClientBuilder<U, R> executor(Executor executor) {
        throw new UnsupportedOperationException("Method executor is not supported by " + getClass().getName());
    }

    /**
     * {@inheritDoc}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#ioExecutor(IoExecutor)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    @Override
    public GrpcClientBuilder<U, R> ioExecutor(IoExecutor ioExecutor) {
        throw new UnsupportedOperationException("Method ioExecutor is not supported by " + getClass().getName());
    }

    /**
     * {@inheritDoc}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#bufferAllocator(BufferAllocator)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    @Override
    public GrpcClientBuilder<U, R> bufferAllocator(BufferAllocator allocator) {
        throw new UnsupportedOperationException("Method bufferAllocator is not supported by " + getClass().getName());
    }

    /**
     * {@inheritDoc}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#executionStrategy(HttpExecutionStrategy)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    @Override
    public GrpcClientBuilder<U, R> executionStrategy(GrpcExecutionStrategy strategy) {
        throw new UnsupportedOperationException("Method executionStrategy is not supported by " + getClass().getName());
    }

    /**
     * {@inheritDoc}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#socketOption(SocketOption, Object)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    @Override
    public <T> GrpcClientBuilder<U, R> socketOption(SocketOption<T> option, T value) {
        throw new UnsupportedOperationException("Method socketOption is not supported by " + getClass().getName());
    }

    /**
     * {@inheritDoc}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#enableWireLogging(String)}
     * on the {@code builder} instance by implementing
     * {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Override
    @Deprecated
    public GrpcClientBuilder<U, R> enableWireLogging(String loggerName) {
        throw new UnsupportedOperationException("Method enableWireLogging is not supported by " + getClass().getName());
    }

    /**
     * {@inheritDoc}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#enableWireLogging(String, LogLevel, BooleanSupplier)}
     * on the {@code builder} instance by implementing
     * {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    @Override
    public GrpcClientBuilder<U, R> enableWireLogging(String loggerName, LogLevel logLevel,
                                                              BooleanSupplier logUserData) {
        throw new UnsupportedOperationException("Method enableWireLogging is not supported by " + getClass().getName());
    }

    /**
     * {@inheritDoc}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#protocols(HttpProtocolConfig...)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    @Override
    public GrpcClientBuilder<U, R> protocols(HttpProtocolConfig... protocols) {
        throw new UnsupportedOperationException("Method protocols is not supported by " + getClass().getName());
    }

    @Override
    public abstract GrpcClientBuilder<U, R> defaultTimeout(Duration defaultTimeout);

    /**
     * {@inheritDoc}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#appendConnectionFactoryFilter(ConnectionFactoryFilter)}
     * on the {@code builder} instance by implementing
     * {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    @Override
    public GrpcClientBuilder<U, R> appendConnectionFactoryFilter(
            ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> factory) {
        throw new UnsupportedOperationException("Method appendConnectionFactoryFilter is not supported by " +
                getClass().getName());
    }

    /**
     * {@inheritDoc}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#appendConnectionFilter(StreamingHttpConnectionFilterFactory)}
     * on the {@code builder} instance by implementing
     * {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    @Override
    public GrpcClientBuilder<U, R> appendConnectionFilter(StreamingHttpConnectionFilterFactory factory) {
        throw new UnsupportedOperationException("Method appendConnectionFilter is not supported by " +
                getClass().getName());
    }

    /**
     * {@inheritDoc}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#appendConnectionFilter(Predicate, StreamingHttpConnectionFilterFactory)}
     * on the {@code builder} instance by implementing
     * {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    @Override
    public GrpcClientBuilder<U, R> appendConnectionFilter(Predicate<StreamingHttpRequest> predicate,
                                                                   StreamingHttpConnectionFilterFactory factory) {
        throw new UnsupportedOperationException("Method appendConnectionFilter is not supported by " +
                getClass().getName());
    }

    /**
     * {@inheritDoc}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#sslConfig(ClientSslConfig)}
     * on the {@code builder} instance by implementing
     * {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    @Override
    public GrpcClientSecurityConfigurator<U, R> secure() {
        throw new UnsupportedOperationException("Method secure is not supported by " + getClass().getName());
    }

    /**
     * {@inheritDoc}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#sslConfig(ClientSslConfig)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    @Override
    public GrpcClientBuilder<U, R> sslConfig(ClientSslConfig sslConfig) {
        throw new UnsupportedOperationException("Method sslConfig is not supported by " + getClass().getName());
    }

    /**
     * {@inheritDoc}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#inferPeerHost(boolean)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    @Override
    public GrpcClientBuilder<U, R> inferPeerHost(boolean shouldInfer) {
        throw new UnsupportedOperationException("Method inferPeerHost is not supported by " + getClass().getName());
    }

    /**
     * {@inheritDoc}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#inferPeerPort(boolean)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    @Override
    public GrpcClientBuilder<U, R> inferPeerPort(boolean shouldInfer) {
        throw new UnsupportedOperationException("Method inferPeerPort is not supported by " + getClass().getName());
    }

    /**
     * {@inheritDoc}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#inferSniHostname(boolean)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    @Override
    public GrpcClientBuilder<U, R> inferSniHostname(boolean shouldInfer) {
        throw new UnsupportedOperationException("Method inferSniHostname is not supported by " + getClass().getName());
    }

    /**
     * {@inheritDoc}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#autoRetryStrategy(AutoRetryStrategyProvider)}
     * on the {@code builder} instance by implementing
     * {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    @Override
    public GrpcClientBuilder<U, R> autoRetryStrategy(AutoRetryStrategyProvider autoRetryStrategyProvider) {
        throw new UnsupportedOperationException("Method autoRetryStrategy is not supported by " + getClass().getName());
    }

    /**
     * {@inheritDoc}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#hostHeaderFallback(boolean)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    @Override
    public GrpcClientBuilder<U, R> disableHostHeaderFallback() {
        throw new UnsupportedOperationException("Method disableHostHeaderFallback is not supported by " +
                getClass().getName());
    }

    /**
     * {@inheritDoc}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    @Override
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
     * {@inheritDoc}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#serviceDiscoverer(ServiceDiscoverer)} on the {@code builder} instance
     * by implementing {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    @Override
    public GrpcClientBuilder<U, R> serviceDiscoverer(
            ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer) {
        throw new UnsupportedOperationException("Method serviceDiscoverer is not supported by " + getClass().getName());
    }

    /**
     * {@inheritDoc}
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link SingleAddressHttpClientBuilder#loadBalancerFactory(HttpLoadBalancerFactory)}
     * on the {@code builder} instance by implementing
     * {@link HttpInitializer#initialize(SingleAddressHttpClientBuilder)} functional interface.
     */
    @Deprecated
    @Override
    public GrpcClientBuilder<U, R> loadBalancerFactory(HttpLoadBalancerFactory<R> loadBalancerFactory) {
        throw new UnsupportedOperationException("Method loadBalancerFactory is not supported by " +
                getClass().getName());
    }

    /**
     * Append the filter to the chain of filters used to decorate the client created by this builder.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
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
     *     builder.append(filter1).append(filter2).append(filter3)
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
     * Returns a {@link MultiClientBuilder} to be used to create multiple clients sharing the same underlying transport
     * instance.
     *
     * @return A blocking <a href="https://www.grpc.io">gRPC</a> client.
     * @deprecated Use {@link GrpcClientFactory#newClient(GrpcClientCallFactory)}
     * or {@link GrpcClientFactory#newBlockingClient(GrpcClientCallFactory)}
     * and provide a custom {@link GrpcClientCallFactory} instead.
     */
    @Deprecated
    public final MultiClientBuilder buildMulti() {
        GrpcClientCallFactory callFactory = newGrpcClientCallFactory();
        return new MultiClientBuilder() {
            @Override
            public <Client extends GrpcClient<?>,
                    Filter extends FilterableClient, FilterableClient extends FilterableGrpcClient,
                    FilterFactory extends GrpcClientFilterFactory<Filter, FilterableClient>> Client
            build(final GrpcClientFactory<Client, ?, Filter, FilterableClient, FilterFactory> clientFactory) {
                return clientFactory.newClient(callFactory);
            }

            @Override
            public <BlockingClient extends BlockingGrpcClient<?>,
                    Filter extends FilterableClient, FilterableClient extends FilterableGrpcClient,
                    FilterFactory extends GrpcClientFilterFactory<Filter, FilterableClient>> BlockingClient
            buildBlocking(
                    final GrpcClientFactory<?, BlockingClient, Filter, FilterableClient, FilterFactory> clientFactory) {
                return clientFactory.newBlockingClient(callFactory);
            }
        };
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
     *     builder.append(filter1).append(filter2).append(filter3)
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
     *     builder.append(filter1).append(filter2).append(filter3)
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

    /**
     * An interface to create multiple <a href="https://www.grpc.io">gRPC</a> clients sharing the
     * same underlying transport instance.
     * @deprecated Use {@link GrpcClientFactory#newClient(GrpcClientCallFactory)}
     * or {@link GrpcClientFactory#newBlockingClient(GrpcClientCallFactory)}
     * and provide a custom {@link GrpcClientCallFactory} instead.
     */
    @Deprecated
    public interface MultiClientBuilder {

        /**
         * Builds a <a href="https://www.grpc.io">gRPC</a> client.
         *
         * @param clientFactory {@link GrpcClientFactory} to use.
         * @param <Client> <a href="https://www.grpc.io">gRPC</a> service that any client built
         * from this factory represents.
         * @param <Filter> Type for client filter
         * @param <FilterableClient> Type of filterable client.
         * @param <FilterFactory> Type of {@link GrpcClientFilterFactory}
         *
         * @return A <a href="https://www.grpc.io">gRPC</a> client.
         */
        <Client extends GrpcClient<?>,
                Filter extends FilterableClient, FilterableClient extends FilterableGrpcClient,
                FilterFactory extends GrpcClientFilterFactory<Filter, FilterableClient>> Client
        build(GrpcClientFactory<Client, ?, Filter, FilterableClient, FilterFactory> clientFactory);

        /**
         * Builds a blocking <a href="https://www.grpc.io">gRPC</a> client.
         *
         * @param clientFactory {@link GrpcClientFactory} to use.
         * @param <BlockingClient> Blocking <a href="https://www.grpc.io">gRPC</a> service that
         * any client built from this builder represents.
         * @param <Filter> Type for client filter
         * @param <FilterableClient> Type of filterable client.
         * @param <FilterFactory> Type of {@link GrpcClientFilterFactory}
         *
         * @return A blocking <a href="https://www.grpc.io">gRPC</a> client.
         */
        <BlockingClient extends BlockingGrpcClient<?>,
                Filter extends FilterableClient, FilterableClient extends FilterableGrpcClient,
                FilterFactory extends GrpcClientFilterFactory<Filter, FilterableClient>> BlockingClient
        buildBlocking(GrpcClientFactory<?, BlockingClient, Filter, FilterableClient, FilterFactory> clientFactory);
    }
}
