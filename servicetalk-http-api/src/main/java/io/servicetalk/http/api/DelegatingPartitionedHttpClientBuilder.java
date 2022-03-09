/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.partition.PartitionMapFactory;
import io.servicetalk.client.api.partition.PartitionedServiceDiscovererEvent;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.transport.api.IoExecutor;

import static java.util.Objects.requireNonNull;

/**
 * A {@link PartitionedHttpClientBuilder} that delegates all methods to another {@link PartitionedHttpClientBuilder}.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
public class DelegatingPartitionedHttpClientBuilder<U, R> implements PartitionedHttpClientBuilder<U, R> {

    private PartitionedHttpClientBuilder<U, R> delegate;

    /**
     * Create a new instance.
     *
     * @param delegate {@link PartitionedHttpClientBuilder} to which all methods are delegated.
     */
    public DelegatingPartitionedHttpClientBuilder(final PartitionedHttpClientBuilder<U, R> delegate) {
        this.delegate = requireNonNull(delegate);
    }

    /**
     * Returns the {@link PartitionedHttpClientBuilder} delegate.
     *
     * @return Delegate {@link PartitionedHttpClientBuilder}.
     */
    protected final PartitionedHttpClientBuilder<U, R> delegate() {
        return delegate;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{delegate=" + delegate() + '}';
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> ioExecutor(final IoExecutor ioExecutor) {
        delegate = delegate.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> executor(final Executor executor) {
        delegate = delegate.executor(executor);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> executionStrategy(final HttpExecutionStrategy strategy) {
        delegate = delegate.executionStrategy(strategy);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> bufferAllocator(final BufferAllocator allocator) {
        delegate = delegate.bufferAllocator(allocator);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> headersFactory(final HttpHeadersFactory headersFactory) {
        delegate = delegate.headersFactory(headersFactory);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> serviceDiscoverer(
            final ServiceDiscoverer<U, R, PartitionedServiceDiscovererEvent<R>> serviceDiscoverer) {
        delegate = delegate.serviceDiscoverer(serviceDiscoverer);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> retryServiceDiscoveryErrors(
            final BiIntFunction<Throwable, ? extends Completable> retryStrategy) {
        delegate = delegate.retryServiceDiscoveryErrors(retryStrategy);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> serviceDiscoveryMaxQueueSize(final int serviceDiscoveryMaxQueueSize) {
        delegate = delegate.serviceDiscoveryMaxQueueSize(serviceDiscoveryMaxQueueSize);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> partitionMapFactory(final PartitionMapFactory partitionMapFactory) {
        delegate = delegate.partitionMapFactory(partitionMapFactory);
        return this;
    }

    @Override
    public PartitionedHttpClientBuilder<U, R> initializer(final SingleAddressInitializer<U, R> initializer) {
        delegate = delegate.initializer(initializer);
        return this;
    }

    @Override
    public HttpClient build() {
        return delegate.build();
    }

    @Override
    public StreamingHttpClient buildStreaming() {
        return delegate.buildStreaming();
    }

    @Override
    public BlockingHttpClient buildBlocking() {
        return delegate.buildBlocking();
    }

    @Override
    public BlockingStreamingHttpClient buildBlockingStreaming() {
        return delegate.buildBlockingStreaming();
    }
}
