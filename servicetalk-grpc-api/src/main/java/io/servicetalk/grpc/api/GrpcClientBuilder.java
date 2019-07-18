/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.IoExecutor;

import java.net.SocketOption;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * A builder for building a <a href="https://www.grpc.io">gRPC</a> client.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
public abstract class GrpcClientBuilder<U, R>
        implements SingleAddressGrpcClientBuilder<U, R, ServiceDiscovererEvent<R>> {
    @Override
    public abstract GrpcClientBuilder<U, R> ioExecutor(IoExecutor ioExecutor);

    @Override
    public abstract GrpcClientBuilder<U, R> bufferAllocator(BufferAllocator allocator);

    @Override
    public abstract GrpcClientBuilder<U, R> executionStrategy(HttpExecutionStrategy strategy);

    @Override
    public abstract <T> GrpcClientBuilder<U, R> socketOption(SocketOption<T> option, T value);

    @Override
    public abstract GrpcClientBuilder<U, R> enableWireLogging(String loggerName);

    @Override
    public abstract GrpcClientBuilder<U, R> disableWireLogging();

    @Override
    public abstract GrpcClientBuilder<U, R> headersFactory(HttpHeadersFactory headersFactory);

    @Override
    public abstract GrpcClientBuilder<U, R> h2HeadersFactory(HttpHeadersFactory headersFactory);

    @Override
    public abstract GrpcClientBuilder<U, R> h2PriorKnowledge(boolean h2PriorKnowledge);

    @Override
    public abstract GrpcClientBuilder<U, R> h2FrameLogger(@Nullable String h2FrameLogger);

    @Override
    public abstract GrpcClientBuilder<U, R> maxInitialLineLength(int maxInitialLineLength);

    @Override
    public abstract GrpcClientBuilder<U, R> maxHeaderSize(int maxHeaderSize);

    @Override
    public abstract GrpcClientBuilder<U, R> headersEncodedSizeEstimate(int headersEncodedSizeEstimate);

    @Override
    public abstract GrpcClientBuilder<U, R> trailersEncodedSizeEstimate(int trailersEncodedSizeEstimate);

    @Override
    public abstract GrpcClientBuilder<U, R> appendConnectionFactoryFilter(
            ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> factory);

    @Override
    public abstract GrpcClientBuilder<U, R> appendConnectionFilter(StreamingHttpConnectionFilterFactory factory);

    @Override
    public abstract GrpcClientBuilder<U, R> appendConnectionFilter(Predicate<StreamingHttpRequest> predicate,
                                                                   StreamingHttpConnectionFilterFactory factory);

    @Override
    public abstract ClientSslConfigBuilder<? extends GrpcClientBuilder<U, R>> enableSsl();

    @Override
    public abstract GrpcClientBuilder<U, R> serviceDiscoverer(
            ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer);

    @Override
    public abstract GrpcClientBuilder<U, R> loadBalancerFactory(
            LoadBalancerFactory<R, StreamingHttpConnection> loadBalancerFactory);

    /**
     * Append the filter to the chain of filters used to decorate the client created by this builder.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * making a request to a client wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; client
     * </pre>
     *
     * @param factory {@link StreamingHttpClientFilterFactory} to decorate a client for the purpose of filtering.
     * @return {@code this}
     */
    public abstract GrpcClientBuilder<U, R> appendHttpClientFilter(StreamingHttpClientFilterFactory factory);

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
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; client
     * </pre>
     *
     * @param predicate the {@link Predicate} to test if the filter must be applied.
     * @param factory {@link StreamingHttpClientFilterFactory} to decorate a client for the purpose of filtering.
     * @return {@code this}
     */
    public abstract GrpcClientBuilder<U, R> appendHttpClientFilter(Predicate<StreamingHttpRequest> predicate,
                                                                   StreamingHttpClientFilterFactory factory);

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
            Filter extends FilterableClient, FilterableClient extends ListenableAsyncCloseable & AutoCloseable,
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
            Filter extends FilterableClient, FilterableClient extends ListenableAsyncCloseable & AutoCloseable,
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
                    Filter extends FilterableClient, FilterableClient extends ListenableAsyncCloseable & AutoCloseable,
                    FilterFactory extends GrpcClientFilterFactory<Filter, FilterableClient>> Client
            build(final GrpcClientFactory<Client, ?, Filter, FilterableClient, FilterFactory> clientFactory) {
                return clientFactory.newClient(callFactory);
            }

            @Override
            public <BlockingClient extends BlockingGrpcClient<?>,
                    Filter extends FilterableClient, FilterableClient extends ListenableAsyncCloseable & AutoCloseable,
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
                Filter extends FilterableClient, FilterableClient extends ListenableAsyncCloseable & AutoCloseable,
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
                Filter extends FilterableClient, FilterableClient extends ListenableAsyncCloseable & AutoCloseable,
                FilterFactory extends GrpcClientFilterFactory<Filter, FilterableClient>> BlockingClient
        buildBlocking(GrpcClientFactory<?, BlockingClient, Filter, FilterableClient, FilterFactory> clientFactory);
    }
}
