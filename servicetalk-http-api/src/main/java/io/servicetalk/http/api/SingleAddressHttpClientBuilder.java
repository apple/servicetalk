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
import io.servicetalk.client.api.AutoRetryStrategyProvider;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.IoExecutor;

import java.net.SocketOption;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

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
        extends BaseSingleAddressHttpClientBuilder<U, R, ServiceDiscovererEvent<R>> {

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> ioExecutor(IoExecutor ioExecutor);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> executionStrategy(HttpExecutionStrategy strategy);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> bufferAllocator(BufferAllocator allocator);

    @Override
    public abstract <T> SingleAddressHttpClientBuilder<U, R> socketOption(SocketOption<T> option, T value);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> enableWireLogging(String loggerName,
                                                                           LogLevel logLevel,
                                                                           BooleanSupplier logUserData);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> protocols(HttpProtocolConfig... protocols);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> appendConnectionFilter(
            StreamingHttpConnectionFilterFactory factory);

    @Override
    public SingleAddressHttpClientBuilder<U, R> appendConnectionFilter(Predicate<StreamingHttpRequest> predicate,
                                                                       StreamingHttpConnectionFilterFactory factory) {
        return (SingleAddressHttpClientBuilder<U, R>)
                super.appendConnectionFilter(predicate, factory);
    }

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> appendConnectionFactoryFilter(
            ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> factory);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> disableHostHeaderFallback();

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> allowDropResponseTrailers(boolean allowDrop);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> autoRetryStrategy(
            AutoRetryStrategyProvider autoRetryStrategyProvider);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> serviceDiscoverer(
            ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> retryServiceDiscoveryErrors(
            ServiceDiscoveryRetryStrategy<R, ServiceDiscovererEvent<R>> retryStrategy);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> loadBalancerFactory(
            HttpLoadBalancerFactory<R> loadBalancerFactory);

    /**
     * Provides a means to convert {@link U} unresolved address type into a {@link CharSequence}.
     * An example of where this maybe used is to convert the {@link U} to a default host header. It may also
     * be used in the event of proxying.
     *
     * @param unresolvedAddressToHostFunction invoked to convert the {@link U} unresolved address type into a
     * {@link CharSequence} suitable for use in
     * <a href="https://tools.ietf.org/html/rfc7230#section-5.4">Host Header</a> format.
     * @return {@code this}
     */
    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> unresolvedAddressToHost(
            Function<U, CharSequence> unresolvedAddressToHostFunction);

    @Override
    public abstract SingleAddressHttpClientBuilder<U, R> appendClientFilter(StreamingHttpClientFilterFactory factory);

    @Override
    public SingleAddressHttpClientBuilder<U, R> appendClientFilter(Predicate<StreamingHttpRequest> predicate,
                                                                   StreamingHttpClientFilterFactory factory) {
        return (SingleAddressHttpClientBuilder<U, R>)
                super.appendClientFilter(predicate, factory);
    }

    /**
     * Set the SSL/TLS configuration.
     * @param sslConfig The configuration to use.
     * @return {@code this}.
     */
    public abstract SingleAddressHttpClientBuilder<U, R> sslConfig(ClientSslConfig sslConfig);

    /**
     * Toggle inference of value to use instead of {@link ClientSslConfig#peerHost()}
     * from client's address when peer host is not specified. By default, inference is enabled.
     * @param shouldInfer value indicating whether inference is on ({@code true}) or off ({@code false}).
     * @return {@code this}
     */
    public abstract SingleAddressHttpClientBuilder<U, R> inferPeerHost(boolean shouldInfer);

    /**
     * Toggle inference of value to use instead of {@link ClientSslConfig#peerPort()}
     * from client's address when peer port is not specified (equals {@code -1}). By default, inference is enabled.
     * @param shouldInfer value indicating whether inference is on ({@code true}) or off ({@code false}).
     * @return {@code this}
     */
    public abstract SingleAddressHttpClientBuilder<U, R> inferPeerPort(boolean shouldInfer);

    /**
     * Toggle <a href="https://datatracker.ietf.org/doc/html/rfc6066#section-3">SNI</a>
     * hostname inference from client's address if not explicitly specified
     * via {@link #sslConfig(ClientSslConfig)}. By default, inference is enabled.
     * @param shouldInfer value indicating whether inference is on ({@code true}) or off ({@code false}).
     * @return {@code this}
     */
    public abstract SingleAddressHttpClientBuilder<U, R> inferSniHostname(boolean shouldInfer);
}
