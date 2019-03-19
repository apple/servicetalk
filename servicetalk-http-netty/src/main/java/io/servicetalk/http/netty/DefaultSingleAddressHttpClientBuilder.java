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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpClientFilterFactory;
import io.servicetalk.http.api.HttpConnectionFilterFactory;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.LoadBalancerReadyStreamingHttpClientFilter;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.ExecutionContextBuilder;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.http.netty.GlobalDnsServiceDiscoverer.globalDnsServiceDiscoverer;
import static io.servicetalk.loadbalancer.RoundRobinLoadBalancer.newRoundRobinFactory;
import static java.util.Objects.requireNonNull;

/**
 * A builder of {@link StreamingHttpClient} instances which call a single server based on the provided address.
 * <p>
 * It also provides a good set of default settings and configurations, which could be used by most users as-is or
 * could be overridden to address specific use cases.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
final class DefaultSingleAddressHttpClientBuilder<U, R> extends SingleAddressHttpClientBuilder<U, R> {

    private static final HttpClientFilterFactory LB_READY_FILTER =
            (client, lbEvents) -> new LoadBalancerReadyStreamingHttpClientFilter(4, lbEvents, client);

    @Nullable
    private final U address;
    private final HttpClientConfig config;
    private final ExecutionContextBuilder executionContextBuilder;
    private LoadBalancerFactory<R, StreamingHttpConnectionFilter> loadBalancerFactory;
    private ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer;
    private Function<U, HttpClientFilterFactory> hostHeaderFilterFactory =
            DefaultSingleAddressHttpClientBuilder::defaultHostHeaderFilterFactory;
    private HttpConnectionFilterFactory connectionFilterFunction = HttpConnectionFilterFactory.identity();
    private HttpClientFilterFactory clientFilterFunction = HttpClientFilterFactory.identity();
    private HttpClientFilterFactory lbReadyFilter = LB_READY_FILTER;
    private ConnectionFactoryFilter<R, StreamingHttpConnectionFilter> connectionFactoryFilter =
            ConnectionFactoryFilter.identity();

    DefaultSingleAddressHttpClientBuilder(
            final U address, final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer) {
        this.address = requireNonNull(address);
        config = new HttpClientConfig(new TcpClientConfig(false));
        this.executionContextBuilder = new ExecutionContextBuilder();
        this.loadBalancerFactory = newRoundRobinFactory();
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
    }

    DefaultSingleAddressHttpClientBuilder(
            final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer) {
        address = null; // Unknown address - template builder pending override via: copy(address)
        config = new HttpClientConfig(new TcpClientConfig(false));
        this.executionContextBuilder = new ExecutionContextBuilder();
        this.loadBalancerFactory = newRoundRobinFactory();
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
    }

    private DefaultSingleAddressHttpClientBuilder(@Nullable final U address,
                                                  final DefaultSingleAddressHttpClientBuilder<U, R> from) {
        this.address = address;
        config = new HttpClientConfig(from.config);
        executionContextBuilder = new ExecutionContextBuilder(from.executionContextBuilder);
        executionStrategy(from.executionStrategy());
        this.loadBalancerFactory = from.loadBalancerFactory;
        this.serviceDiscoverer = from.serviceDiscoverer;
        clientFilterFunction = from.clientFilterFunction;
        connectionFilterFunction = from.connectionFilterFunction;
        hostHeaderFilterFactory = from.hostHeaderFilterFactory;
        lbReadyFilter = from.lbReadyFilter;
        connectionFactoryFilter = from.connectionFactoryFilter;
    }

    private DefaultSingleAddressHttpClientBuilder<U, R> copy() {
        return new DefaultSingleAddressHttpClientBuilder<>(address, this);
    }

    private DefaultSingleAddressHttpClientBuilder<U, R> copy(final U address) {
        return new DefaultSingleAddressHttpClientBuilder<>(requireNonNull(address), this);
    }

    static DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forHostAndPort(
            final HostAndPort address) {
        return new DefaultSingleAddressHttpClientBuilder<>(address, globalDnsServiceDiscoverer());
    }

    static DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forUnknownHostAndPort() {
        return new DefaultSingleAddressHttpClientBuilder<>(globalDnsServiceDiscoverer());
    }

    static final class HttpClientBuildContext<U, R> {
        final DefaultSingleAddressHttpClientBuilder<U, R> builder;
        final ExecutionContext executionContext;
        final StreamingHttpRequestResponseFactory reqRespFactory;

        private HttpClientBuildContext(final DefaultSingleAddressHttpClientBuilder<U, R> builder,
                                       final ExecutionContext executionContext,
                                       final StreamingHttpRequestResponseFactory reqRespFactory) {
            this.builder = builder;
            this.executionContext = executionContext;
            this.reqRespFactory = reqRespFactory;
        }

        HttpExecutionStrategy executionStrategy() {
            return builder.executionStrategy();
        }

        Publisher<? extends ServiceDiscovererEvent<R>> discover() {
            assert builder.address != null : "Attempted to buildStreaming with an unknown address";
            return builder.serviceDiscoverer.discover(builder.address);
        }

        <T> T build(final BiFunction<StreamingHttpClientFilter, HttpExecutionStrategy, T> assembler) {
            return builder.buildFilterChainFromContext(this, assembler);
        }
    }

    // This is only to be called by HttpClientBuilder.buildStreaming() - @see #buildContext()
    @Override
    protected <T> T buildFilterChain(final BiFunction<StreamingHttpClientFilter, HttpExecutionStrategy, T> assembler) {
        return copyBuildCtx().build(assembler);
    }

    private <T> T buildFilterChainFromContext(
            final HttpClientBuildContext<U, R> ctx,
            final BiFunction<StreamingHttpClientFilter, HttpExecutionStrategy, T> assembler) {

        final ReadOnlyHttpClientConfig roConfig = config.asReadOnly();
        // Track resources that potentially need to be closed when an exception is thrown during buildStreaming
        final CompositeCloseable closeOnException = newCompositeCloseable();
        try {
            final HttpExecutionStrategy strategy = executionStrategy();
            final Publisher<? extends ServiceDiscovererEvent<R>> sdEvents = ctx.discover();

            final StreamingHttpRequestResponseFactory reqRespFactory = ctx.reqRespFactory;

            // closed by the LoadBalancer
            final ConnectionFactory<R, ? extends StreamingHttpConnectionFilter> connectionFactory =
                    connectionFactoryFilter.create(closeOnException.prepend(roConfig.maxPipelinedRequests() == 1 ?
                            new NonPipelinedLBHttpConnectionFactory<>(roConfig, ctx.executionContext,
                                    connectionFilterFunction, reqRespFactory, strategy) :
                            new PipelinedLBHttpConnectionFactory<>(roConfig, ctx.executionContext,
                                    connectionFilterFunction, reqRespFactory, strategy)));

            final LoadBalancer<? extends StreamingHttpConnectionFilter> lbfUntypedForCast = closeOnException.prepend(
                    loadBalancerFactory.newLoadBalancer(sdEvents, connectionFactory));
            @SuppressWarnings("unchecked")
            final LoadBalancer<LoadBalancedStreamingHttpConnectionFilter> lb =
                    (LoadBalancer<LoadBalancedStreamingHttpConnectionFilter>) lbfUntypedForCast;

            final DefaultStreamingHttpClientFilter transport = closeOnException.prepend(
                    new DefaultStreamingHttpClientFilter(ctx.executionContext, strategy, lb, reqRespFactory));

            return assembler.apply(clientFilterFunction
                            .append(lbReadyFilter)
                            .append(hostHeaderFilterFactory.apply(address))
                            .create(transport, lb.eventStream()), strategy);
        } catch (final Throwable t) {
            closeOnException.closeAsync().subscribe();
            throw t;
        }
    }

    /**
     * Creates a context before building the client, avoid concurrent changes at runtime.
     */
    HttpClientBuildContext<U, R> copyBuildCtx() {
        return buildContext0(null);
    }

    /**
     * Creates a context before building the client with a provided address, avoid concurrent changes at runtime.
     */
    HttpClientBuildContext<U, R> copyBuildCtx(U address) {
        assert this.address == null : "Not intended to change the address, only to supply lazily";
        return buildContext0(address);
    }

    private HttpClientBuildContext<U, R> buildContext0(@Nullable U address) {

        final DefaultSingleAddressHttpClientBuilder<U, R> clonedBuilder = address == null ? copy() : copy(address);

        final HttpExecutionStrategy strategy = clonedBuilder.executionStrategy();
        Executor executor = strategy.executor();
        if (executor != null) {
            clonedBuilder.executionContextBuilder.executor(executor);
        }
        final ExecutionContext exec = clonedBuilder.executionContextBuilder.build();

        final StreamingHttpRequestResponseFactory reqRespFactory =
                new DefaultStreamingHttpRequestResponseFactory(exec.bufferAllocator(),
                        clonedBuilder.config.headersFactory());

        return new HttpClientBuildContext<>(clonedBuilder, exec, reqRespFactory);
    }

    private static <U> HostHeaderHttpRequesterFilter defaultHostHeaderFilterFactory(final U address) {
        if (address instanceof CharSequence) {
            return new HostHeaderHttpRequesterFilter((CharSequence) address);
        }
        if (address instanceof HostAndPort) {
            return new HostHeaderHttpRequesterFilter((HostAndPort) address);
        }
        throw new IllegalArgumentException("Unsupported host header address type, provide an override");
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> ioExecutor(final IoExecutor ioExecutor) {
        executionContextBuilder.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> bufferAllocator(final BufferAllocator allocator) {
        executionContextBuilder.bufferAllocator(allocator);
        return this;
    }

    @Override
    public <T> DefaultSingleAddressHttpClientBuilder<U, R> socketOption(SocketOption<T> option, T value) {
        config.tcpClientConfig().socketOption(option, value);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> enableWireLogging(final String loggerName) {
        config.tcpClientConfig().enableWireLogging(loggerName);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> disableWireLogging() {
        config.tcpClientConfig().disableWireLogging();
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> headersFactory(final HttpHeadersFactory headersFactory) {
        config.headersFactory(headersFactory);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> maxInitialLineLength(final int maxInitialLineLength) {
        config.maxInitialLineLength(maxInitialLineLength);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> maxHeaderSize(final int maxHeaderSize) {
        config.maxHeaderSize(maxHeaderSize);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> headersEncodedSizeEstimate(
            final int headersEncodedSizeEstimate) {
        config.headersEncodedSizeEstimate(headersEncodedSizeEstimate);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> trailersEncodedSizeEstimate(
            final int trailersEncodedSizeEstimate) {
        config.trailersEncodedSizeEstimate(trailersEncodedSizeEstimate);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> maxPipelinedRequests(final int maxPipelinedRequests) {
        config.maxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> appendConnectionFilter(
            final HttpConnectionFilterFactory factory) {
        connectionFilterFunction = connectionFilterFunction.append(requireNonNull(factory));
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> appendConnectionFactoryFilter(
            final ConnectionFactoryFilter<R, StreamingHttpConnectionFilter> factory) {
        connectionFactoryFilter = connectionFactoryFilter.append(factory);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> disableHostHeaderFallback() {
        hostHeaderFilterFactory = address -> HttpClientFilterFactory.identity();
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> disableWaitForLoadBalancer() {
        lbReadyFilter = HttpClientFilterFactory.identity();
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> enableHostHeaderFallback(final CharSequence hostHeader) {
        hostHeaderFilterFactory = address -> new HostHeaderHttpRequesterFilter(hostHeader);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> appendClientFilter(final HttpClientFilterFactory function) {
        clientFilterFunction = clientFilterFunction.append(function);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> serviceDiscoverer(
            final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer) {
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> loadBalancerFactory(
            final LoadBalancerFactory<R, StreamingHttpConnectionFilter> loadBalancerFactory) {
        this.loadBalancerFactory = requireNonNull(loadBalancerFactory);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> sslConfig(@Nullable final SslConfig sslConfig) {
        config.tcpClientConfig().sslConfig(sslConfig);
        return this;
    }
}
