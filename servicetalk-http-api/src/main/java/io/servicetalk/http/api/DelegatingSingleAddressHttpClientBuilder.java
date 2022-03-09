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
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.IoExecutor;

import java.net.SocketOption;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * A {@link SingleAddressHttpClientBuilder} that delegates all methods to another
 * {@link SingleAddressHttpClientBuilder}.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
public class DelegatingSingleAddressHttpClientBuilder<U, R> implements SingleAddressHttpClientBuilder<U, R> {

    private final SingleAddressHttpClientBuilder<U, R> delegate;

    /**
     * Create a new instance.
     *
     * @param delegate {@link SingleAddressHttpClientBuilder} to which all methods are delegated.
     */
    public DelegatingSingleAddressHttpClientBuilder(final SingleAddressHttpClientBuilder<U, R> delegate) {
        this.delegate = requireNonNull(delegate);
    }

    /**
     * Returns the {@link SingleAddressHttpClientBuilder} delegate.
     *
     * @return Delegate {@link SingleAddressHttpClientBuilder}.
     */
    protected final SingleAddressHttpClientBuilder<U, R> delegate() {
        return delegate;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{delegate=" + delegate() + '}';
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> proxyAddress(final U proxyAddress) {
        delegate.proxyAddress(proxyAddress);
        return this;
    }

    @Override
    public <T> SingleAddressHttpClientBuilder<U, R> socketOption(final SocketOption<T> option, final T value) {
        delegate.socketOption(option, value);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> enableWireLogging(final String loggerName, final LogLevel logLevel,
                                                                  final BooleanSupplier logUserData) {
        delegate.enableWireLogging(loggerName, logLevel, logUserData);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> protocols(final HttpProtocolConfig... protocols) {
        delegate.protocols(protocols);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> hostHeaderFallback(final boolean enable) {
        delegate.hostHeaderFallback(enable);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> allowDropResponseTrailers(final boolean allowDrop) {
        delegate.allowDropResponseTrailers(allowDrop);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> appendConnectionFilter(
            final StreamingHttpConnectionFilterFactory factory) {
        delegate.appendConnectionFilter(factory);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> appendConnectionFilter(
            final Predicate<StreamingHttpRequest> predicate, final StreamingHttpConnectionFilterFactory factory) {
        delegate.appendConnectionFilter(predicate, factory);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> ioExecutor(final IoExecutor ioExecutor) {
        delegate.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> executor(final Executor executor) {
        delegate.executor(executor);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> executionStrategy(final HttpExecutionStrategy strategy) {
        delegate.executionStrategy(strategy);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> bufferAllocator(final BufferAllocator allocator) {
        delegate.bufferAllocator(allocator);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> appendConnectionFactoryFilter(
            final ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> factory) {
        delegate.appendConnectionFactoryFilter(factory);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> appendClientFilter(final StreamingHttpClientFilterFactory factory) {
        delegate.appendClientFilter(factory);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> appendClientFilter(final Predicate<StreamingHttpRequest> predicate,
                                                                   final StreamingHttpClientFilterFactory factory) {
        delegate.appendClientFilter(predicate, factory);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> unresolvedAddressToHost(
            final Function<U, CharSequence> unresolvedAddressToHostFunction) {
        delegate.unresolvedAddressToHost(unresolvedAddressToHostFunction);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> serviceDiscoverer(
            final ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer) {
        delegate.serviceDiscoverer(serviceDiscoverer);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> retryServiceDiscoveryErrors(
            final BiIntFunction<Throwable, ? extends Completable> retryStrategy) {
        delegate.retryServiceDiscoveryErrors(retryStrategy);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> loadBalancerFactory(
            final HttpLoadBalancerFactory<R> loadBalancerFactory) {
        delegate.loadBalancerFactory(loadBalancerFactory);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> sslConfig(final ClientSslConfig sslConfig) {
        delegate.sslConfig(sslConfig);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> inferPeerHost(final boolean shouldInfer) {
        delegate.inferPeerHost(shouldInfer);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> inferPeerPort(final boolean shouldInfer) {
        delegate.inferPeerPort(shouldInfer);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> inferSniHostname(final boolean shouldInfer) {
        delegate.inferSniHostname(shouldInfer);
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
