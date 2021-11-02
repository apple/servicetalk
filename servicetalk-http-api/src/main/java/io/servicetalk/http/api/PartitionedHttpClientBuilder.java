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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.client.api.partition.PartitionMapFactory;
import io.servicetalk.client.api.partition.PartitionedServiceDiscovererEvent;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.transport.api.IoExecutor;

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
public interface PartitionedHttpClientBuilder<U, R> extends HttpClientBuilder<U, R, ServiceDiscovererEvent<R>> {
    /**
     * Initializes the {@link SingleAddressHttpClientBuilder} for each new client.
     * @param <U> the type of address before resolution (unresolved address)
     * @param <R> the type of address after resolution (resolved address)
     */
    @FunctionalInterface
    interface SingleAddressInitializer<U, R> {
        /**
         * Configures the passed {@link SingleAddressHttpClientBuilder} for a given set of {@link PartitionAttributes}.
         *
         * @param attr the {@link PartitionAttributes} for the partition
         * @param builder {@link SingleAddressHttpClientBuilder} to configure for the given {@link PartitionAttributes}
         */
        void initialize(PartitionAttributes attr, SingleAddressHttpClientBuilder<U, R> builder);

        /**
         * Appends the passed {@link SingleAddressInitializer} to this
         * {@link SingleAddressInitializer} such that this {@link SingleAddressInitializer} is
         * applied first and then the passed {@link SingleAddressInitializer}.
         *
         * @param toAppend {@link SingleAddressInitializer} to append
         * @return A composite {@link SingleAddressInitializer} after the append operation.
         */
        default SingleAddressInitializer<U, R> append(SingleAddressInitializer<U, R> toAppend) {
            return (attr, builder) -> {
                initialize(attr, builder);
                toAppend.initialize(attr, builder);
            };
        }
    }

    @Override
    PartitionedHttpClientBuilder<U, R> ioExecutor(IoExecutor ioExecutor);

    @Override
    PartitionedHttpClientBuilder<U, R> executor(Executor executor);

    @Override
    PartitionedHttpClientBuilder<U, R> executionStrategy(HttpExecutionStrategy strategy);

    @Override
    PartitionedHttpClientBuilder<U, R> bufferAllocator(BufferAllocator allocator);

    /**
     * Sets a {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     *
     * @param serviceDiscoverer The {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * Lifecycle of the provided {@link ServiceDiscoverer} is managed externally and it should be
     * {@link ServiceDiscoverer#closeAsync() closed} after all built {@link StreamingHttpClient}s will be closed and
     * this {@link ServiceDiscoverer} is no longer needed.
     * @return {@code this}.
     */
    PartitionedHttpClientBuilder<U, R> serviceDiscoverer(
            ServiceDiscoverer<U, R, PartitionedServiceDiscovererEvent<R>> serviceDiscoverer);

    /**
     * Sets a retry strategy to retry errors emitted by {@link ServiceDiscoverer}.
     * <p>
     * Note, calling this method will unset the value provided via
     * {@link #retryServiceDiscoveryErrors(BiIntFunction)} if it was called before.
     * @param retryStrategy a retry strategy to retry errors emitted by {@link ServiceDiscoverer}.
     * @return {@code this}.
     * @see DefaultServiceDiscoveryRetryStrategy.Builder
     * @deprecated Use {@link #retryServiceDiscoveryErrors(BiIntFunction)} preferably with the standard utilities from
     * {@link io.servicetalk.concurrent.api.RetryStrategies}.
     */
    @Deprecated
    default PartitionedHttpClientBuilder<U, R> retryServiceDiscoveryErrors(
            ServiceDiscoveryRetryStrategy<R, PartitionedServiceDiscovererEvent<R>> retryStrategy) {
        throw new UnsupportedOperationException("retryServiceDiscoveryErrors accepting ServiceDiscoveryRetryStrategy" +
                " is not supported by " + getClass().getName());
    }

    /**
     * Sets a retry strategy to retry errors emitted by {@link ServiceDiscoverer}.
     * <p>
     * Note, calling this method will unset the value provided via
     * {@link #retryServiceDiscoveryErrors(ServiceDiscoveryRetryStrategy)} if it was called before.
     * @param retryStrategy a retry strategy to retry errors emitted by {@link ServiceDiscoverer}.
     * @return {@code this}.
     * @see DefaultServiceDiscoveryRetryStrategy.Builder
     * @see io.servicetalk.concurrent.api.RetryStrategies
     */
    default PartitionedHttpClientBuilder<U, R> retryServiceDiscoveryErrors(
            BiIntFunction<Throwable, ? extends Completable> retryStrategy) {
        throw new UnsupportedOperationException("retryServiceDiscoveryErrors accepting BiIntFunction" +
                " is not supported by " + getClass().getName());
    }

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
     * Sets {@link PartitionMapFactory} to use by all {@link StreamingHttpClient}s created by this builder.
     *
     * @param partitionMapFactory {@link PartitionMapFactory} to use.
     * @return {@code this}.
     */
    PartitionedHttpClientBuilder<U, R> partitionMapFactory(PartitionMapFactory partitionMapFactory);

    /**
     * Set a function which can customize options for each {@link StreamingHttpClient} that is built.
     * @param initializer Initializes the {@link SingleAddressHttpClientBuilder} used to build new
     * {@link StreamingHttpClient}s.
     * @return {@code this}
     */
    PartitionedHttpClientBuilder<U, R> initializer(SingleAddressInitializer<U, R> initializer);
}
