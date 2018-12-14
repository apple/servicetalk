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
import javax.annotation.Nullable;

/**
 * A builder of {@link StreamingHttpClient} instances which call a single server based on the provided unresolved address.
 * <p>
 * It also provides a good set of default settings and configurations, which could be used by most users as-is or
 * could be overridden to address specific use cases.
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
public interface SingleAddressHttpClientBuilder<U, R>
        extends BaseSingleAddressHttpClientBuilder<U, R, ServiceDiscovererEvent<R>> {

    @Override
    SingleAddressHttpClientBuilder<U, R> ioExecutor(IoExecutor ioExecutor);

    @Override
    SingleAddressHttpClientBuilder<U, R> executionStrategy(HttpExecutionStrategy strategy);

    @Override
    SingleAddressHttpClientBuilder<U, R> bufferAllocator(BufferAllocator allocator);

    @Override
    <T> SingleAddressHttpClientBuilder<U, R> socketOption(SocketOption<T> option, T value);

    @Override
    SingleAddressHttpClientBuilder<U, R> enableWireLogging(String loggerName);

    @Override
    SingleAddressHttpClientBuilder<U, R> disableWireLogging();

    @Override
    SingleAddressHttpClientBuilder<U, R> headersFactory(HttpHeadersFactory headersFactory);

    @Override
    SingleAddressHttpClientBuilder<U, R> maxInitialLineLength(int maxInitialLineLength);

    @Override
    SingleAddressHttpClientBuilder<U, R> maxHeaderSize(int maxHeaderSize);

    @Override
    SingleAddressHttpClientBuilder<U, R> headersEncodedSizeEstimate(int headersEncodedSizeEstimate);

    @Override
    SingleAddressHttpClientBuilder<U, R> trailersEncodedSizeEstimate(int trailersEncodedSizeEstimate);

    @Override
    SingleAddressHttpClientBuilder<U, R> maxPipelinedRequests(int maxPipelinedRequests);

    @Override
    SingleAddressHttpClientBuilder<U, R> appendConnectionFilter(HttpConnectionFilterFactory factory);

    @Override
    SingleAddressHttpClientBuilder<U, R> appendConnectionFactoryFilter(
            ConnectionFactoryFilter<R, StreamingHttpConnection> factory);

    @Override
    SingleAddressHttpClientBuilder<U, R> disableHostHeaderFallback();

    @Override
    SingleAddressHttpClientBuilder<U, R> disableWaitForLoadBalancer();

    @Override
    SingleAddressHttpClientBuilder<U, R> serviceDiscoverer(
            ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer);

    @Override
    SingleAddressHttpClientBuilder<U, R> loadBalancerFactory(
            LoadBalancerFactory<R, StreamingHttpConnection> loadBalancerFactory);

    @Override
    SingleAddressHttpClientBuilder<U, R> enableHostHeaderFallback(CharSequence hostHeader);

    @Override
    SingleAddressHttpClientBuilder<U, R> appendClientFilter(HttpClientFilterFactory function);

    @Override
    SingleAddressHttpClientBuilder<U, R> sslConfig(@Nullable SslConfig sslConfig);
}
