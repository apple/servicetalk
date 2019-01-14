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
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.SslConfig;

import org.reactivestreams.Subscriber;

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
public interface PartitionedHttpClientBuilder<U, R>
        extends BaseSingleAddressHttpClientBuilder<U, R, PartitionedServiceDiscovererEvent<R>> {

    @Override
    PartitionedHttpClientBuilder<U, R> ioExecutor(IoExecutor ioExecutor);

    @Override
    PartitionedHttpClientBuilder<U, R> executionStrategy(HttpExecutionStrategy strategy);

    @Override
    PartitionedHttpClientBuilder<U, R> bufferAllocator(BufferAllocator allocator);

    @Override
    <T> PartitionedHttpClientBuilder<U, R> socketOption(SocketOption<T> option, T value);

    @Override
    PartitionedHttpClientBuilder<U, R> enableWireLogging(String loggerName);

    @Override
    PartitionedHttpClientBuilder<U, R> disableWireLogging();

    @Override
    PartitionedHttpClientBuilder<U, R> headersFactory(HttpHeadersFactory headersFactory);

    @Override
    PartitionedHttpClientBuilder<U, R> maxInitialLineLength(int maxInitialLineLength);

    @Override
    PartitionedHttpClientBuilder<U, R> maxHeaderSize(int maxHeaderSize);

    @Override
    PartitionedHttpClientBuilder<U, R> headersEncodedSizeEstimate(int headersEncodedSizeEstimate);

    @Override
    PartitionedHttpClientBuilder<U, R> trailersEncodedSizeEstimate(int trailersEncodedSizeEstimate);

    @Override
    PartitionedHttpClientBuilder<U, R> maxPipelinedRequests(int maxPipelinedRequests);

    @Override
    PartitionedHttpClientBuilder<U, R> appendConnectionFilter(HttpConnectionFilterFactory factory);

    @Override
    default PartitionedHttpClientBuilder<U, R> appendConnectionFilter(Predicate<StreamingHttpRequest> predicate,
                                                                      HttpConnectionFilterFactory factory) {
        return (PartitionedHttpClientBuilder<U, R>)
                BaseSingleAddressHttpClientBuilder.super.appendConnectionFilter(predicate, factory);
    }

    @Override
    PartitionedHttpClientBuilder<U, R> appendConnectionFactoryFilter(
            ConnectionFactoryFilter<R, StreamingHttpConnection> factory);

    @Override
    PartitionedHttpClientBuilder<U, R> disableHostHeaderFallback();

    @Override
    PartitionedHttpClientBuilder<U, R> disableWaitForLoadBalancer();

    @Override
    PartitionedHttpClientBuilder<U, R> serviceDiscoverer(
            ServiceDiscoverer<U, R, ? extends PartitionedServiceDiscovererEvent<R>> serviceDiscoverer);

    @Override
    PartitionedHttpClientBuilder<U, R> loadBalancerFactory(
            LoadBalancerFactory<R, StreamingHttpConnection> loadBalancerFactory);

    @Override
    PartitionedHttpClientBuilder<U, R> enableHostHeaderFallback(CharSequence hostHeader);

    @Override
    PartitionedHttpClientBuilder<U, R> appendClientFilter(HttpClientFilterFactory function);

    @Override
    default PartitionedHttpClientBuilder<U, R> appendClientFilter(Predicate<StreamingHttpRequest> predicate,
                                                                  HttpClientFilterFactory factory) {
        return (PartitionedHttpClientBuilder<U, R>)
                BaseSingleAddressHttpClientBuilder.super.appendClientFilter(predicate, factory);
    }

    @Override
    PartitionedHttpClientBuilder<U, R> sslConfig(@Nullable SslConfig sslConfig);

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
    PartitionedHttpClientBuilder<U, R> serviceDiscoveryMaxQueueSize(int serviceDiscoveryMaxQueueSize);

    /**
     * Set {@link PartitionMapFactory} to use by all {@link StreamingHttpClient}s created by this builder.
     *
     * @param partitionMapFactory {@link PartitionMapFactory} to use.
     * @return {@code this}.
     */
    PartitionedHttpClientBuilder<U, R> partitionMapFactory(PartitionMapFactory partitionMapFactory);

    /**
     * A function that allows customizing the {@link SingleAddressHttpClientBuilder} used to create the client for a
     * given partition based on its {@link PartitionAttributes}.
     *
     * @param clientFilterFunction {@link BiFunction} used to customize the {@link SingleAddressHttpClientBuilder}
     * before creating the client for the partition
     * @return {@code this}
     */
    PartitionedHttpClientBuilder<U, R> appendClientBuilderFilter(
            PartitionHttpClientBuilderFilterFunction<U, R> clientFilterFunction);
}
