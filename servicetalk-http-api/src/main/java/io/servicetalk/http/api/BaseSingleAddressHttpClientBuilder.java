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
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.SslConfig;

import java.io.InputStream;
import java.net.SocketOption;
import java.util.function.Predicate;
import javax.annotation.Nullable;

abstract class BaseSingleAddressHttpClientBuilder<U, R, SDE extends ServiceDiscovererEvent<R>>
        extends HttpClientBuilder<U, R, SDE> {

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> ioExecutor(IoExecutor ioExecutor);

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> bufferAllocator(BufferAllocator allocator);

    @Override
    public abstract <T> BaseSingleAddressHttpClientBuilder<U, R, SDE> socketOption(SocketOption<T> option, T value);

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> enableWireLogging(String loggerName);

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> disableWireLogging();

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> headersFactory(HttpHeadersFactory headersFactory);

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> maxInitialLineLength(int maxInitialLineLength);

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> maxHeaderSize(int maxHeaderSize);

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> headersEncodedSizeEstimate(
            int headersEncodedSizeEstimate);

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> trailersEncodedSizeEstimate(
            int trailersEncodedSizeEstimate);

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> maxPipelinedRequests(int maxPipelinedRequests);

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> appendConnectionFilter(
            StreamingHttpConnectionFilterFactory factory);

    @Override
    public BaseSingleAddressHttpClientBuilder<U, R, SDE> appendConnectionFilter(
            Predicate<StreamingHttpRequest> predicate,
            StreamingHttpConnectionFilterFactory factory) {
        return (BaseSingleAddressHttpClientBuilder<U, R, SDE>) super.appendConnectionFilter(predicate, factory);
    }

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> appendConnectionFactoryFilter(
            ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> factory);

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> disableHostHeaderFallback();

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> disableWaitForLoadBalancer();

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> serviceDiscoverer(
            ServiceDiscoverer<U, R, ? extends SDE> serviceDiscoverer);

    @Override
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> loadBalancerFactory(
            LoadBalancerFactory<R, FilterableStreamingHttpConnection> loadBalancerFactory);

    /**
     * Automatically set the provided {@link HttpHeaderNames#HOST} on {@link StreamingHttpRequest}s when it's missing.
     * <p>
     * For known address types such as {@link HostAndPort} the {@link HttpHeaderNames#HOST} is inferred and
     * automatically set by default, if you have a custom address type or want to override the inferred value use this
     * method. Use {@link #disableHostHeaderFallback()} if you don't want any {@link HttpHeaderNames#HOST} manipulation
     * at all.
     * @param hostHeader the value for the {@link HttpHeaderNames#HOST}
     * @return {@code this}
     */
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> enableHostHeaderFallback(CharSequence hostHeader);

    /**
     * Enable SSL/TLS using the provided {@link SslConfig}. To disable it pass in {@code null}.
     *
     * @param sslConfig the {@link SslConfig}.
     * @return this.
     * @throws IllegalStateException if the {@link SslConfig#keyCertChainSupplier()}, {@link SslConfig#keySupplier()},
     * or {@link SslConfig#trustCertChainSupplier()}
     * throws when {@link InputStream#close()} is called.
     */
    public abstract BaseSingleAddressHttpClientBuilder<U, R, SDE> sslConfig(@Nullable SslConfig sslConfig);
}
