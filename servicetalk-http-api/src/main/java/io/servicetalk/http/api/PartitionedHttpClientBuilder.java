/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.client.api.partition.PartitionMapFactory;
import io.servicetalk.client.api.partition.PartitionedServiceDiscovererEvent;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.SslConfig;

import java.net.SocketOption;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * A builder of homogeneous {@link StreamingHttpClient} instances which call the server associated with a partition
 * selected from a set of {@link PartitionedServiceDiscovererEvent}s resolved from a single unresolved address.
 * <p>
 * Partition selection uses a function to infer {@link PartitionAttributes} from the {@link HttpRequestMetaData}.
 * It also provides a good set of default settings and configurations, which could be used by most users as-is or
 * could be overridden to address specific use cases.
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
public abstract class PartitionedHttpClientBuilder<U, R>
        extends BaseSingleAddressHttpClientBuilder<U, R, PartitionedServiceDiscovererEvent<R>> {

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> ioExecutor(IoExecutor ioExecutor);

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> executionStrategy(HttpExecutionStrategy strategy);

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> bufferAllocator(BufferAllocator allocator);

    @Override
    public abstract <T> PartitionedHttpClientBuilder<U, R> socketOption(SocketOption<T> option, T value);

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> enableWireLogging(String loggerName);

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> disableWireLogging();

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> headersFactory(HttpHeadersFactory headersFactory);

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> h2HeadersFactory(HttpHeadersFactory headersFactory);

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> h2PriorKnowledge(boolean h2PriorKnowledge);

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> h2FrameLogger(@Nullable String h2FrameLogger);

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> maxInitialLineLength(int maxInitialLineLength);

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> maxHeaderSize(int maxHeaderSize);

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> headersEncodedSizeEstimate(int headersEncodedSizeEstimate);

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> trailersEncodedSizeEstimate(int trailersEncodedSizeEstimate);

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> maxPipelinedRequests(int maxPipelinedRequests);

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> appendConnectionFilter(
            StreamingHttpConnectionFilterFactory factory);

    @Override
    public PartitionedHttpClientBuilder<U, R> appendConnectionFilter(Predicate<StreamingHttpRequest> predicate,
                                                                     StreamingHttpConnectionFilterFactory factory) {
        return (PartitionedHttpClientBuilder<U, R>)
                super.appendConnectionFilter(predicate, factory);
    }

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> appendConnectionFactoryFilter(
            ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> factory);

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> disableHostHeaderFallback();

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> disableWaitForLoadBalancer();

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> serviceDiscoverer(
            ServiceDiscoverer<U, R, ? extends PartitionedServiceDiscovererEvent<R>> serviceDiscoverer);

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> loadBalancerFactory(
            LoadBalancerFactory<R, StreamingHttpConnection> loadBalancerFactory);

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> enableHostHeaderFallback(CharSequence hostHeader);

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> appendClientFilter(StreamingHttpClientFilterFactory function);

    @Override
    public PartitionedHttpClientBuilder<U, R> appendClientFilter(Predicate<StreamingHttpRequest> predicate,
                                                                 StreamingHttpClientFilterFactory factory) {
        return (PartitionedHttpClientBuilder<U, R>)
                super.appendClientFilter(predicate, factory);
    }

    @Override
    public abstract PartitionedHttpClientBuilder<U, R> sslConfig(@Nullable SslConfig sslConfig);

    /**
     * Sets the maximum amount of {@link ServiceDiscovererEvent} objects that will be queued for each partition.
     * <p>
     * It is assumed that the {@link Subscriber}s will process events in a timely manner (typically synchronously)
     * so this typically doesn't need to be very large.
     *
     * @param serviceDiscoveryMaxQueueSize the maximum amount of {@link ServiceDiscovererEvent} objects that will be
     * queued for each partition.
     * @return {@code this}.
     */
    public abstract PartitionedHttpClientBuilder<U, R> serviceDiscoveryMaxQueueSize(int serviceDiscoveryMaxQueueSize);

    /**
     * Set {@link PartitionMapFactory} to use by all {@link StreamingHttpClient}s created by this builder.
     *
     * @param partitionMapFactory {@link PartitionMapFactory} to use.
     * @return {@code this}.
     */
    public abstract PartitionedHttpClientBuilder<U, R> partitionMapFactory(PartitionMapFactory partitionMapFactory);

    /**
     * A function that allows customizing the {@link SingleAddressHttpClientBuilder} used to create the client for a
     * given partition based on its {@link PartitionAttributes}.
     *
     * @param clientFilterFunction {@link BiFunction} used to customize the {@link SingleAddressHttpClientBuilder}
     * before creating the client for the partition
     * @return {@code this}
     */
    public abstract PartitionedHttpClientBuilder<U, R> appendClientBuilderFilter(
            PartitionHttpClientBuilderConfigurator<U, R> clientFilterFunction);
}
