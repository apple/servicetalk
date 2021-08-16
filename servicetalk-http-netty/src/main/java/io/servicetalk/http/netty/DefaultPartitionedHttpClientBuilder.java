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
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.DefaultServiceDiscoveryRetryStrategy;
import io.servicetalk.http.api.FilterableReservedStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.PartitionHttpClientBuilderConfigurator;
import io.servicetalk.http.api.PartitionedHttpClientBuilder;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.ServiceDiscoveryRetryStrategy;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.netty.DefaultSingleAddressHttpClientBuilder.HttpClientBuildContext;
import io.servicetalk.transport.api.IoExecutor;

import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.http.netty.DefaultSingleAddressHttpClientBuilder.SD_RETRY_STRATEGY_INIT_DURATION;
import static io.servicetalk.http.netty.DefaultSingleAddressHttpClientBuilder.SD_RETRY_STRATEGY_JITTER;
import static io.servicetalk.http.netty.DefaultSingleAddressHttpClientBuilder.defaultReqRespFactory;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

class DefaultPartitionedHttpClientBuilder<U, R> extends PartitionedHttpClientBuilder<U, R> {

    private ServiceDiscoverer<U, R, PartitionedServiceDiscovererEvent<R>> serviceDiscoverer;
    @Nullable
    private ServiceDiscoveryRetryStrategy<R, PartitionedServiceDiscovererEvent<R>> serviceDiscovererRetryStrategy;
    private final Function<HttpRequestMetaData, PartitionAttributesBuilder> partitionAttributesBuilderFactory;
    private final DefaultSingleAddressHttpClientBuilder<U, R> builderTemplate;
    @Nullable
    private SingleAddressInitializer<U, R> clientInitializer;
    private PartitionHttpClientBuilderConfigurator<U, R> clientFilterFunction = (__, ___) -> { };
    private PartitionMapFactory partitionMapFactory = PowerSetPartitionMapFactory.INSTANCE;
    private int serviceDiscoveryMaxQueueSize = 32;

    DefaultPartitionedHttpClientBuilder(
            final DefaultSingleAddressHttpClientBuilder<U, R> builderTemplate,
            final ServiceDiscoverer<U, R, PartitionedServiceDiscovererEvent<R>> serviceDiscoverer,
            final Function<HttpRequestMetaData, PartitionAttributesBuilder> partitionAttributesBuilderFactory) {
        this.builderTemplate = requireNonNull(builderTemplate);
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
        this.partitionAttributesBuilderFactory = requireNonNull(partitionAttributesBuilderFactory);
    }

    @Override
    public StreamingHttpClient buildStreaming() {
        final HttpClientBuildContext<U, R> buildContext = builderTemplate.copyBuildCtx();
        final HttpExecutionContext executionContext = buildContext.builder.build().executionContext();
        ServiceDiscoverer<U, R, PartitionedServiceDiscovererEvent<R>> psd =
                new DefaultSingleAddressHttpClientBuilder.RetryingServiceDiscoverer<>(
                        serviceDiscoverer,
                        serviceDiscovererRetryStrategy == null ?
                                DefaultServiceDiscoveryRetryStrategy.Builder.<R>withDefaultsForPartitions(
                                        executionContext.executor(), SD_RETRY_STRATEGY_INIT_DURATION,
                                        SD_RETRY_STRATEGY_JITTER).build() :
                                serviceDiscovererRetryStrategy);

        final PartitionedClientFactory<U, R, FilterableStreamingHttpClient> clientFactory = (pa, sd) -> {
            // build new context, user may have changed anything on the builder from the filter
            DefaultSingleAddressHttpClientBuilder<U, R> builder = buildContext.builder.copyBuildCtx().builder;
            builder.serviceDiscoverer(sd);
            clientFilterFunction.configureForPartition(pa, builder);
            if (clientInitializer != null) {
                clientInitializer.initialize(pa, builder);
            }
            return builder.buildStreaming();
        };

        final Publisher<PartitionedServiceDiscovererEvent<R>> psdEvents = psd.discover(buildContext.address())
                .flatMapConcatIterable(identity());

        DefaultPartitionedStreamingHttpClientFilter<U, R> partitionedClient =
                new DefaultPartitionedStreamingHttpClientFilter<>(psdEvents, serviceDiscoveryMaxQueueSize,
                        clientFactory, partitionAttributesBuilderFactory,
                        defaultReqRespFactory(buildContext.httpConfig().asReadOnly(),
                                executionContext.bufferAllocator()),
                        executionContext, partitionMapFactory);
        return new FilterableClientToClient(partitionedClient, executionContext.executionStrategy(),
                buildContext.builder.buildStrategyInfluencerForClient(
                        executionContext.executionStrategy()));
    }

    private static final class DefaultPartitionedStreamingHttpClientFilter<U, R> implements
                                                                                 FilterableStreamingHttpClient {

        private static final Function<PartitionAttributes, FilterableStreamingHttpClient> PARTITION_CLOSED = pa ->
                new NoopPartitionClient(new ClosedPartitionException(pa, "Partition closed"));
        private static final Function<PartitionAttributes, FilterableStreamingHttpClient> PARTITION_UNKNOWN = pa ->
                new NoopPartitionClient(new UnknownPartitionException(pa, "Partition unknown"));

        private final ClientGroup<PartitionAttributes, FilterableStreamingHttpClient> group;
        private final Function<HttpRequestMetaData, PartitionAttributesBuilder> pabf;
        private final HttpExecutionContext executionContext;
        private final StreamingHttpRequestResponseFactory reqRespFactory;

        DefaultPartitionedStreamingHttpClientFilter(
                final Publisher<PartitionedServiceDiscovererEvent<R>> psdEvents,
                final int psdMaxQueueSize,
                final PartitionedClientFactory<U, R, FilterableStreamingHttpClient> clientFactory,
                final Function<HttpRequestMetaData, PartitionAttributesBuilder> pabf,
                final StreamingHttpRequestResponseFactory reqRespFactory,
                final HttpExecutionContext executionContext,
                final PartitionMapFactory partitionMapFactory) {
            this.pabf = pabf;
            this.executionContext = executionContext;
            this.group = new DefaultPartitionedClientGroup<>(PARTITION_CLOSED, PARTITION_UNKNOWN, clientFactory,
                    partitionMapFactory, psdEvents, psdMaxQueueSize);
            this.reqRespFactory = requireNonNull(reqRespFactory);
        }

        private FilterableStreamingHttpClient selectClient(
                final HttpRequestMetaData metaData) {
            return group.get(pabf.apply(metaData).build());
        }

        @Override
        public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                final HttpExecutionStrategy strategy, final HttpRequestMetaData metaData) {
            return defer(() -> selectClient(metaData).reserveConnection(strategy, metaData).subscribeShareContext());
        }

        @Override
        public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                     final StreamingHttpRequest request) {

            return defer(() -> selectClient(request).request(strategy, request).subscribeShareContext());
        }

        @Override
        public HttpExecutionContext executionContext() {
            return executionContext;
        }

        @Override
        public StreamingHttpResponseFactory httpResponseFactory() {
            return reqRespFactory;
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
        private final ListenableAsyncCloseable close = emptyAsyncCloseable();
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
        public HttpExecutionContext executionContext() {
            throw ex;
        }

        @Override
        public StreamingHttpResponseFactory httpResponseFactory() {
            throw ex;
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

        @Override
        public Single<ReservedStreamingHttpConnection> reserveConnection(final HttpExecutionStrategy strategy,
                                                                         final HttpRequestMetaData metaData) {
            return failed(ex);
        }

        @Override
        public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            throw ex;
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
    public PartitionedHttpClientBuilder<U, R> serviceDiscoverer(
            final ServiceDiscoverer<U, R, PartitionedServiceDiscovererEvent<R>> serviceDiscoverer) {
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> retryServiceDiscoveryErrors(
            ServiceDiscoveryRetryStrategy<R, PartitionedServiceDiscovererEvent<R>> retryStrategy) {
        this.serviceDiscovererRetryStrategy = requireNonNull(retryStrategy);
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
    public PartitionedHttpClientBuilder<U, R> initializer(final SingleAddressInitializer<U, R> initializer) {
        this.clientInitializer = requireNonNull(initializer);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> executionStrategy(final HttpExecutionStrategy strategy) {
        this.builderTemplate.executionStrategy(strategy);
        return this;
    }
}
