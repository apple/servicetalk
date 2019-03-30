/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.client.api.ClientGroup;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.internal.DefaultPartitionedClientGroup;
import io.servicetalk.client.api.internal.DefaultPartitionedClientGroup.PartitionedClientFactory;
import io.servicetalk.client.api.internal.partition.PowerSetPartitionMapFactory;
import io.servicetalk.client.api.partition.ClosedPartitionException;
import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.client.api.partition.PartitionAttributesBuilder;
import io.servicetalk.client.api.partition.PartitionMapFactory;
import io.servicetalk.client.api.partition.PartitionedServiceDiscovererEvent;
import io.servicetalk.client.api.partition.UnknownPartitionException;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.EmptyHttpHeaders;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.PartitionHttpClientBuilderFilterFunction;
import io.servicetalk.http.api.PartitionedHttpClientBuilder;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpRequests;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.netty.DefaultSingleAddressHttpClientBuilder.HttpClientBuildContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.SslConfig;

import java.net.SocketOption;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static java.util.Objects.requireNonNull;

class DefaultPartitionedHttpClientBuilder<U, R> extends PartitionedHttpClientBuilder<U, R> {

    private final Function<HttpRequestMetaData, PartitionAttributesBuilder> partitionAttributesBuilderFactory;
    private final DefaultSingleAddressHttpClientBuilder<U, R> builderTemplate;
    private PartitionHttpClientBuilderFilterFunction<U, R> clientFilterFunction = (__, ___) -> { };
    private PartitionMapFactory partitionMapFactory = PowerSetPartitionMapFactory.INSTANCE;
    private int serviceDiscoveryMaxQueueSize = 32;

    DefaultPartitionedHttpClientBuilder(
            final DefaultSingleAddressHttpClientBuilder<U, R> builderTemplate,
            final Function<HttpRequestMetaData, PartitionAttributesBuilder> partitionAttributesBuilderFactory) {
        this.builderTemplate = requireNonNull(builderTemplate);
        this.partitionAttributesBuilderFactory = requireNonNull(partitionAttributesBuilderFactory);
    }

    @Override
    public StreamingHttpClient buildStreaming() {
        final HttpClientBuildContext<U, R> buildContext = builderTemplate.copyBuildCtx();

        final PartitionedClientFactory<U, R, FilterableStreamingHttpClient>
                clientFactory = (pa, sd) -> {
            // build new context, user may have changed anything on the builder from the filter
            DefaultSingleAddressHttpClientBuilder<U, R> builder = buildContext.builder.copyBuildCtx().builder;
            builder.serviceDiscoverer(sd);
            clientFilterFunction.apply(pa, builder);
            return builder.buildStreaming();
        };

        @SuppressWarnings("unchecked")
        final Publisher<? extends PartitionedServiceDiscovererEvent<R>> psdEvents =
                (Publisher<? extends PartitionedServiceDiscovererEvent<R>>) buildContext.discover();
        HttpExecutionStrategy strategy = buildContext.executionStrategy();

        return new DefaultStreamingHttpClient(new DefaultPartitionedStreamingHttpClientFilter<>(psdEvents,
                serviceDiscoveryMaxQueueSize, clientFactory, partitionAttributesBuilderFactory,
                buildContext.reqRespFactory, buildContext.executionContext, partitionMapFactory, strategy));
    }

    private static final class DefaultPartitionedStreamingHttpClientFilter<U, R> implements
                                                                                 FilterableStreamingHttpClient {

        private static final Function<PartitionAttributes, FilterableStreamingHttpClient> PARTITION_CLOSED = pa ->
                new NoopPartitionClient(new ClosedPartitionException(pa, "Partition closed "));
        private static final Function<PartitionAttributes, FilterableStreamingHttpClient> PARTITION_UNKNOWN = pa ->
                new NoopPartitionClient(new UnknownPartitionException(pa, "Partition unknown"));

        private final ClientGroup<PartitionAttributes, FilterableStreamingHttpClient> group;
        private final Function<HttpRequestMetaData, PartitionAttributesBuilder> pabf;
        private final ExecutionContext executionContext;
        private final StreamingHttpRequestResponseFactory reqRespFactory;
        private final HttpExecutionStrategy strategy;

        DefaultPartitionedStreamingHttpClientFilter(
                final Publisher<? extends PartitionedServiceDiscovererEvent<R>> psdEvents,
                final int psdMaxQueueSize,
                final PartitionedClientFactory<U, R, FilterableStreamingHttpClient> clientFactory,
                final Function<HttpRequestMetaData, PartitionAttributesBuilder> pabf,
                final StreamingHttpRequestResponseFactory reqRespFactory,
                final ExecutionContext executionContext,
                final PartitionMapFactory partitionMapFactory, final HttpExecutionStrategy strategy) {
            this.pabf = pabf;
            this.executionContext = executionContext;
            this.group = new DefaultPartitionedClientGroup<>(PARTITION_CLOSED, PARTITION_UNKNOWN, clientFactory,
                    partitionMapFactory, psdEvents, psdMaxQueueSize);
            this.strategy = requireNonNull(strategy);
            this.reqRespFactory = requireNonNull(reqRespFactory);
        }

        private FilterableStreamingHttpClient selectClient(
                final HttpRequestMetaData metaData) {
            return group.get(pabf.apply(metaData).build());
        }

        @Override
        public Single<ReservedStreamingHttpConnection> reserveConnection(final HttpExecutionStrategy strategy,
                                                                         final HttpRequestMetaData metaData) {
            return defer(() -> selectClient(metaData).reserveConnection(strategy, metaData).subscribeShareContext());
        }

        @Override
        public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                     final StreamingHttpRequest request) {
            return defer(() -> selectClient(request).request(strategy, request).subscribeShareContext());
        }

        @Override
        public ExecutionContext executionContext() {
            return executionContext;
        }

        @Override
        public StreamingHttpResponseFactory httpResponseFactory() {
            return reqRespFactory;
        }

        @Override
        public void close() throws Exception {
            group.closeAsync().toFuture().get();
        }

        @Override
        public HttpExecutionStrategy computeExecutionStrategy(final HttpExecutionStrategy other) {
            // TODO(scott): this will not represent the strategy from the Client. That means the computed strategy
            // may not reflect the full execution path.
            return strategy.merge(other);
        }

        @Override
        public Completable onClose() {
            return group.onClose();
        }

        @Override
        public Completable closeAsync() {
            return group.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return group.closeAsyncGracefully();
        }

        @Override
        public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return reqRespFactory.newRequest(method, requestTarget);
        }
    }

    private static final class NoopPartitionClient implements FilterableStreamingHttpClient {
        private final RuntimeException ex;

        NoopPartitionClient(RuntimeException ex) {
            this.ex = ex;
        }

        @Override
        public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                     final StreamingHttpRequest request) {
            return failed(ex);
        }

        @Override
        public ExecutionContext executionContext() {
            throw ex;
        }

        @Override
        public StreamingHttpResponseFactory httpResponseFactory() {
            return new DefaultStreamingHttpResponseFactory(DefaultHttpHeadersFactory.INSTANCE, DEFAULT_ALLOCATOR);
        }

        @Override
        public void close() {
        }

        @Override
        public HttpExecutionStrategy computeExecutionStrategy(final HttpExecutionStrategy other) {
            return other;
        }

        @Override
        public Completable onClose() {
            return completed();
        }

        @Override
        public Completable closeAsync() {
            return completed();
        }

        @Override
        public Single<ReservedStreamingHttpConnection> reserveConnection(final HttpExecutionStrategy strategy,
                                                                         final HttpRequestMetaData metaData) {
            return failed(ex);
        }

        @Override
        public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return StreamingHttpRequests.newRequest(method, requestTarget, HTTP_1_1, EmptyHttpHeaders.INSTANCE,
                    EmptyHttpHeaders.INSTANCE, DEFAULT_ALLOCATOR, empty());
        }
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> ioExecutor(final IoExecutor ioExecutor) {
        builderTemplate.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> bufferAllocator(final BufferAllocator allocator) {
        builderTemplate.bufferAllocator(allocator);
        return this;
    }

    @Override
    public <T> PartitionedHttpClientBuilder<U, R> socketOption(final SocketOption<T> option, final T value) {
        builderTemplate.socketOption(option, value);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> enableWireLogging(final String loggerName) {
        builderTemplate.enableWireLogging(loggerName);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> disableWireLogging() {
        builderTemplate.disableWireLogging();
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> headersFactory(final HttpHeadersFactory headersFactory) {
        builderTemplate.headersFactory(headersFactory);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> maxInitialLineLength(final int maxInitialLineLength) {
        builderTemplate.maxInitialLineLength(maxInitialLineLength);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> maxHeaderSize(final int maxHeaderSize) {
        builderTemplate.maxHeaderSize(maxHeaderSize);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> headersEncodedSizeEstimate(final int headersEncodedSizeEstimate) {
        builderTemplate.headersEncodedSizeEstimate(headersEncodedSizeEstimate);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> trailersEncodedSizeEstimate(final int trailersEncodedSizeEstimate) {
        builderTemplate.trailersEncodedSizeEstimate(trailersEncodedSizeEstimate);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> maxPipelinedRequests(final int maxPipelinedRequests) {
        builderTemplate.maxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> appendConnectionFilter(
            final StreamingHttpConnectionFilterFactory factory) {
        builderTemplate.appendConnectionFilter(factory);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> appendConnectionFactoryFilter(
            final ConnectionFactoryFilter<R, StreamingHttpConnection> factory) {
        builderTemplate.appendConnectionFactoryFilter(factory);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> disableHostHeaderFallback() {
        builderTemplate.disableHostHeaderFallback();
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> disableWaitForLoadBalancer() {
        builderTemplate.disableWaitForLoadBalancer();
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> serviceDiscoverer(
            final ServiceDiscoverer<U, R, ? extends PartitionedServiceDiscovererEvent<R>> serviceDiscoverer) {
        builderTemplate.serviceDiscoverer(serviceDiscoverer);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> loadBalancerFactory(
            final LoadBalancerFactory<R, StreamingHttpConnection> loadBalancerFactory) {
        builderTemplate.loadBalancerFactory(loadBalancerFactory);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> enableHostHeaderFallback(final CharSequence hostHeader) {
        builderTemplate.enableHostHeaderFallback(hostHeader);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> appendClientFilter(final StreamingHttpClientFilterFactory function) {
        builderTemplate.appendClientFilter(function);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> sslConfig(@Nullable final SslConfig sslConfig) {
        builderTemplate.sslConfig(sslConfig);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> serviceDiscoveryMaxQueueSize(final int serviceDiscoveryMaxQueueSize) {
        this.serviceDiscoveryMaxQueueSize = serviceDiscoveryMaxQueueSize;
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> partitionMapFactory(final PartitionMapFactory partitionMapFactory) {
        this.partitionMapFactory = partitionMapFactory;
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> appendClientBuilderFilter(
            final PartitionHttpClientBuilderFilterFunction<U, R> clientFilterFunction) {
        this.clientFilterFunction = this.clientFilterFunction.append(clientFilterFunction);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> executionStrategy(final HttpExecutionStrategy strategy) {
        this.builderTemplate.executionStrategy(strategy);
        return this;
    }
}
