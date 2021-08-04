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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.IoExecutor;

import java.net.SocketOption;
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
public abstract class GrpcClientBuilder<U, R>
        implements SingleAddressGrpcClientBuilder<U, R, ServiceDiscovererEvent<R>> {

    private boolean appendedCatchAllFilter;

    @Override
    public abstract GrpcClientBuilder<U, R> ioExecutor(IoExecutor ioExecutor);

    @Override
    public abstract GrpcClientBuilder<U, R> bufferAllocator(BufferAllocator allocator);

    @Override
    public abstract GrpcClientBuilder<U, R> executionStrategy(GrpcExecutionStrategy strategy);

    @Override
    public abstract <T> GrpcClientBuilder<U, R> socketOption(SocketOption<T> option, T value);

    @Override
    public abstract GrpcClientBuilder<U, R> enableWireLogging(String loggerName, LogLevel logLevel,
                                                              BooleanSupplier logUserData);

    @Override
    public abstract GrpcClientBuilder<U, R> protocols(HttpProtocolConfig... protocols);

    @Override
    public abstract GrpcClientBuilder<U, R> defaultTimeout(Duration defaultTimeout);

    @Override
    public abstract GrpcClientBuilder<U, R> appendConnectionFactoryFilter(
            ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> factory);

    @Override
    public abstract GrpcClientBuilder<U, R> appendConnectionFilter(StreamingHttpConnectionFilterFactory factory);

    @Override
    public abstract GrpcClientBuilder<U, R> appendConnectionFilter(Predicate<StreamingHttpRequest> predicate,
                                                                   StreamingHttpConnectionFilterFactory factory);

    @Override
    public abstract GrpcClientBuilder<U, R> sslConfig(ClientSslConfig sslConfig);

    @Override
    public abstract GrpcClientBuilder<U, R> inferPeerHost(boolean shouldInfer);

    @Override
    public abstract GrpcClientBuilder<U, R> inferPeerPort(boolean shouldInfer);

    @Override
    public abstract GrpcClientBuilder<U, R> inferSniHostname(boolean shouldInfer);

    @Override
    public abstract GrpcClientBuilder<U, R> autoRetryStrategy(
            AutoRetryStrategyProvider autoRetryStrategyProvider);

    @Override
    public abstract GrpcClientBuilder<U, R> unresolvedAddressToHost(
            Function<U, CharSequence> unresolvedAddressToHostFunction);

    @Override
    public abstract GrpcClientBuilder<U, R> disableHostHeaderFallback();

    @Override
    public abstract GrpcClientBuilder<U, R> serviceDiscoverer(
            ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer);

    @Override
    public abstract GrpcClientBuilder<U, R> loadBalancerFactory(HttpLoadBalancerFactory<R> loadBalancerFactory);

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
     * Returns a {@link MultiClientBuilder} to be used to create multiple clients sharing the same underlying transport
     * instance.
     *
     * @return A blocking <a href="https://www.grpc.io">gRPC</a> client.
     */
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
     */
    protected abstract void doAppendHttpClientFilter(StreamingHttpClientFilterFactory factory);

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

    /**
     * An interface to create multiple <a href="https://www.grpc.io">gRPC</a> clients sharing the
     * same underlying transport instance.
     */
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
