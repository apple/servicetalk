/*
 * Copyright © 2018-2019, 2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.FilterableReservedStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.PartitionedHttpClientBuilder;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.transport.api.IoExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoffDeltaJitter;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.http.api.HttpApiConversions.toStreamingClient;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.netty.DefaultSingleAddressHttpClientBuilder.SD_RETRY_STRATEGY_INIT_DURATION;
import static io.servicetalk.http.netty.DefaultSingleAddressHttpClientBuilder.SD_RETRY_STRATEGY_JITTER;
import static io.servicetalk.http.netty.DefaultSingleAddressHttpClientBuilder.setExecutionContext;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

final class DefaultPartitionedHttpClientBuilder<U, R> implements PartitionedHttpClientBuilder<U, R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPartitionedHttpClientBuilder.class);

    private final U address;
    private final Function<HttpRequestMetaData, PartitionAttributesBuilder> partitionAttributesBuilderFactory;
    private final Supplier<SingleAddressHttpClientBuilder<U, R>> builderFactory;
    private final HttpExecutionContextBuilder executionContextBuilder = new HttpExecutionContextBuilder();
    private ServiceDiscoverer<U, R, PartitionedServiceDiscovererEvent<R>> serviceDiscoverer;
    @Nullable
    private BiIntFunction<Throwable, ? extends Completable> serviceDiscovererRetryStrategy;
    private int serviceDiscoveryMaxQueueSize = 32;
    @Nullable
    private HttpHeadersFactory headersFactory;
    @Nullable
    private SingleAddressInitializer<U, R> clientInitializer;
    private PartitionMapFactory partitionMapFactory = PowerSetPartitionMapFactory.INSTANCE;

    DefaultPartitionedHttpClientBuilder(
            final U address,
            final Supplier<SingleAddressHttpClientBuilder<U, R>> builderFactory,
            final ServiceDiscoverer<U, R, PartitionedServiceDiscovererEvent<R>> serviceDiscoverer,
            final Function<HttpRequestMetaData, PartitionAttributesBuilder> partitionAttributesBuilderFactory) {
        this.address = requireNonNull(address);
        this.builderFactory = requireNonNull(builderFactory);
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
        this.partitionAttributesBuilderFactory = requireNonNull(partitionAttributesBuilderFactory);
    }

    @Override
    public StreamingHttpClient buildStreaming() {
        final HttpExecutionContext executionContext = executionContextBuilder.build();
        BiIntFunction<Throwable, ? extends Completable> sdRetryStrategy = serviceDiscovererRetryStrategy;
        if (sdRetryStrategy == null) {
            sdRetryStrategy = retryWithConstantBackoffDeltaJitter(__ -> true, SD_RETRY_STRATEGY_INIT_DURATION,
                    SD_RETRY_STRATEGY_JITTER, executionContext.executor());
        }
        final ServiceDiscoverer<U, R, PartitionedServiceDiscovererEvent<R>> psd =
                new DefaultSingleAddressHttpClientBuilder.RetryingServiceDiscoverer<>(serviceDiscoverer,
                        sdRetryStrategy);

        final PartitionedClientFactory<U, R, FilterableStreamingHttpClient> clientFactory = (pa, sd) -> {
            // build new context, user may have changed anything on the builder from the filter
            final SingleAddressHttpClientBuilder<U, R> builder = requireNonNull(builderFactory.get());
            builder.serviceDiscoverer(sd);
            setExecutionContext(builder, executionContext);
            if (clientInitializer != null) {
                clientInitializer.initialize(pa, builder);
            }
            return builder.buildStreaming();
        };

        final Publisher<PartitionedServiceDiscovererEvent<R>> psdEvents = psd.discover(address)
                .flatMapConcatIterable(identity());
        final HttpHeadersFactory headersFactory = this.headersFactory;
        final DefaultPartitionedStreamingHttpClientFilter<U, R> partitionedClient =
                new DefaultPartitionedStreamingHttpClientFilter<>(psdEvents, serviceDiscoveryMaxQueueSize,
                        clientFactory, partitionAttributesBuilderFactory,
                        new DefaultStreamingHttpRequestResponseFactory(executionContext.bufferAllocator(),
                                headersFactory != null ? headersFactory : DefaultHttpHeadersFactory.INSTANCE, HTTP_1_1),
                        executionContext, partitionMapFactory);

        LOGGER.debug("Partitioned client created with base strategy {}", executionContext.executionStrategy());
        return toStreamingClient(partitionedClient, executionContext.executionStrategy());
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
                final HttpRequestMetaData metaData) {
            return defer(() -> selectClient(metaData).reserveConnection(metaData).shareContextOnSubscribe());
        }

        @Override
        public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
            return defer(() -> selectClient(request).request(request).shareContextOnSubscribe());
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
        public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
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
        public Single<ReservedStreamingHttpConnection> reserveConnection(final HttpRequestMetaData metaData) {
            return failed(ex);
        }

        @Override
        public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            throw ex;
        }
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> executor(final Executor executor) {
        executionContextBuilder.executor(executor);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> ioExecutor(final IoExecutor ioExecutor) {
        executionContextBuilder.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> bufferAllocator(final BufferAllocator allocator) {
        executionContextBuilder.bufferAllocator(allocator);
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
            final BiIntFunction<Throwable, ? extends Completable> retryStrategy) {
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
        this.executionContextBuilder.executionStrategy(strategy);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> headersFactory(final HttpHeadersFactory headersFactory) {
        this.headersFactory = requireNonNull(headersFactory);
        return this;
    }
}
