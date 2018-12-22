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

import static java.util.Objects.requireNonNull;

interface BaseSingleAddressHttpClientBuilder<U, R, SDE extends ServiceDiscovererEvent<R>>
        extends HttpClientBuilder<U, R, SDE> {

    @Override
    BaseSingleAddressHttpClientBuilder<U, R, SDE> ioExecutor(IoExecutor ioExecutor);

    @Override
    BaseSingleAddressHttpClientBuilder<U, R, SDE> executionStrategy(HttpExecutionStrategy strategy);

    @Override
    BaseSingleAddressHttpClientBuilder<U, R, SDE> bufferAllocator(BufferAllocator allocator);

    @Override
    <T> BaseSingleAddressHttpClientBuilder<U, R, SDE> socketOption(SocketOption<T> option, T value);

    @Override
    BaseSingleAddressHttpClientBuilder<U, R, SDE> enableWireLogging(String loggerName);

    @Override
    BaseSingleAddressHttpClientBuilder<U, R, SDE> disableWireLogging();

    @Override
    BaseSingleAddressHttpClientBuilder<U, R, SDE> headersFactory(HttpHeadersFactory headersFactory);

    @Override
    BaseSingleAddressHttpClientBuilder<U, R, SDE> maxInitialLineLength(int maxInitialLineLength);

    @Override
    BaseSingleAddressHttpClientBuilder<U, R, SDE> maxHeaderSize(int maxHeaderSize);

    @Override
    BaseSingleAddressHttpClientBuilder<U, R, SDE> headersEncodedSizeEstimate(int headersEncodedSizeEstimate);

    @Override
    BaseSingleAddressHttpClientBuilder<U, R, SDE> trailersEncodedSizeEstimate(int trailersEncodedSizeEstimate);

    @Override
    BaseSingleAddressHttpClientBuilder<U, R, SDE> maxPipelinedRequests(int maxPipelinedRequests);

    @Override
    BaseSingleAddressHttpClientBuilder<U, R, SDE> appendConnectionFilter(HttpConnectionFilterFactory factory);

    @Override
    default BaseSingleAddressHttpClientBuilder<U, R, SDE> appendConnectionFilter(
            Predicate<StreamingHttpRequest> predicate,
            HttpConnectionFilterFactory factory) {
        return (BaseSingleAddressHttpClientBuilder<U, R, SDE>)
                HttpClientBuilder.super.appendConnectionFilter(predicate, factory);
    }

    @Override
    BaseSingleAddressHttpClientBuilder<U, R, SDE> appendConnectionFactoryFilter(
            ConnectionFactoryFilter<R, StreamingHttpConnection> factory);

    @Override
    BaseSingleAddressHttpClientBuilder<U, R, SDE> disableHostHeaderFallback();

    @Override
    BaseSingleAddressHttpClientBuilder<U, R, SDE> disableWaitForLoadBalancer();

    @Override
    BaseSingleAddressHttpClientBuilder<U, R, SDE> serviceDiscoverer(
            ServiceDiscoverer<U, R, ? extends SDE> serviceDiscoverer);

    @Override
    BaseSingleAddressHttpClientBuilder<U, R, SDE> loadBalancerFactory(
            LoadBalancerFactory<R, StreamingHttpConnection> loadBalancerFactory);

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
    BaseSingleAddressHttpClientBuilder<U, R, SDE> enableHostHeaderFallback(CharSequence hostHeader);

    /**
     * Append the filter to the chain of filters used to decorate the {@link StreamingHttpClient} created by this
     * builder.
     * <p>
     * Note this method will be used to decorate the result of {@link #buildStreaming()} before it is
     * returned to the user.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * making a request to a client wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; client
     * </pre>
     * @param factory {@link HttpClientFilterFactory} to decorate a {@link StreamingHttpClient} for the purpose of
     * filtering.
     * @return {@code this}
     */
    BaseSingleAddressHttpClientBuilder<U, R, SDE> appendClientFilter(HttpClientFilterFactory factory);

    /**
     * Append the filter to the chain of filters used to decorate the {@link StreamingHttpClient} created by this
     * builder, for every request that passes the provided {@link Predicate}.
     * <p>
     * Note this method will be used to decorate the result of {@link #buildStreaming()} before it is
     * returned to the user.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * making a request to a client wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; client
     * </pre>
     * @param predicate the {@link Predicate} to test if the filter must be applied.
     * @param factory {@link HttpClientFilterFactory} to decorate a {@link StreamingHttpClient} for the purpose of
     * filtering.
     * @return {@code this}
     */
    default BaseSingleAddressHttpClientBuilder<U, R, SDE> appendClientFilter(Predicate<StreamingHttpRequest> predicate,
                                                                             HttpClientFilterFactory factory) {
        requireNonNull(predicate);
        requireNonNull(factory);

        return appendClientFilter((client, lbEvents) ->
                new ConditionalHttpClientFilter(predicate, factory.create(client, lbEvents), client));
    }

    /**
     * Enable SSL/TLS using the provided {@link SslConfig}. To disable it pass in {@code null}.
     *
     * @param sslConfig the {@link SslConfig}.
     * @return this.
     * @throws IllegalStateException if the {@link SslConfig#getKeyCertChainSupplier()},
     * {@link SslConfig#getKeySupplier()}, or {@link SslConfig#getTrustCertChainSupplier()}
     * throws when {@link InputStream#close()} is called.
     */
    BaseSingleAddressHttpClientBuilder<U, R, SDE> sslConfig(@Nullable SslConfig sslConfig);
}
