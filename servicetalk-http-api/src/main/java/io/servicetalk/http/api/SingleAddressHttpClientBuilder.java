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
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.SslConfig;

import java.net.SocketOption;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * A builder of {@link StreamingHttpClient} instances which call a single server based on the provided unresolved
 * address.
 * <p>
 * It also provides a good set of default settings and configurations, which could be used by most users as-is or
 * could be overridden to address specific use cases.
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
public abstract class SingleAddressHttpClientBuilder<U, R>
        implements BaseSingleAddressHttpClientBuilder<U, R, ServiceDiscovererEvent<R>> {
    private HttpExecutionStrategy strategy = DEFAULT_BUILDER_STRATEGY;

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> ioExecutor(IoExecutor ioExecutor);

    @Override
    public final SingleAddressHttpClientBuilder<U, R> executionStrategy(HttpExecutionStrategy strategy) {
        this.strategy = strategy;
        return this;
    }

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> bufferAllocator(BufferAllocator allocator);

    @Override
    public abstract <T> SingleAddressHttpClientBuilder<U, R> socketOption(SocketOption<T> option, T value);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> enableWireLogging(String loggerName);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> disableWireLogging();

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> headersFactory(HttpHeadersFactory headersFactory);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> maxInitialLineLength(int maxInitialLineLength);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> maxHeaderSize(int maxHeaderSize);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> headersEncodedSizeEstimate(int headersEncodedSizeEstimate);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> trailersEncodedSizeEstimate(int trailersEncodedSizeEstimate);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> maxPipelinedRequests(int maxPipelinedRequests);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> appendConnectionFilter(HttpConnectionFilterFactory factory);

    @Override
    public SingleAddressHttpClientBuilder<U, R> appendConnectionFilter(Predicate<StreamingHttpRequest> predicate,
                                                                       HttpConnectionFilterFactory factory) {
        return (SingleAddressHttpClientBuilder<U, R>)
                BaseSingleAddressHttpClientBuilder.super.appendConnectionFilter(predicate, factory);
    }

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> appendConnectionFactoryFilter(
            ConnectionFactoryFilter<R, StreamingHttpConnectionFilter> factory);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> disableHostHeaderFallback();

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> disableWaitForLoadBalancer();

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> serviceDiscoverer(
            ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> loadBalancerFactory(
            LoadBalancerFactory<R, StreamingHttpConnectionFilter> loadBalancerFactory);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> enableHostHeaderFallback(CharSequence hostHeader);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> appendClientFilter(HttpClientFilterFactory function);

    @Override
    public SingleAddressHttpClientBuilder<U, R> appendClientFilter(Predicate<StreamingHttpRequest> predicate,
                                                                   HttpClientFilterFactory factory) {
        return (SingleAddressHttpClientBuilder<U, R>)
                BaseSingleAddressHttpClientBuilder.super.appendClientFilter(predicate, factory);
    }

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> sslConfig(@Nullable SslConfig sslConfig);

    /**
     * Returns the {@link HttpExecutionStrategy} used by this {@link SingleAddressHttpClientBuilder}.
     *
     * @return {@link HttpExecutionStrategy} used by this {@link SingleAddressHttpClientBuilder}.
     */
    protected HttpExecutionStrategy executionStrategy() {
        return strategy;
    }
}
