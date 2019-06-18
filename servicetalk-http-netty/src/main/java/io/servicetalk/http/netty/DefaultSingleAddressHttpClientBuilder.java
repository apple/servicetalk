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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.MultiAddressHttpClientFilterFactory;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.SslConfig;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.netty.util.NetUtil.toSocketAddressString;
import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.netty.GlobalDnsServiceDiscoverer.globalDnsServiceDiscoverer;
import static io.servicetalk.http.netty.H2ToStH1Utils.HTTP_2_0;
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
    @Nullable
    private final U address;
    @Nullable
    private U proxyAddress;
    private final HttpClientConfig config;
    private final HttpExecutionContextBuilder executionContextBuilder;
    private final ClientStrategyInfluencerChainBuilder influencerChainBuilder;
    private LoadBalancerFactory<R, StreamingHttpConnection> loadBalancerFactory;
    private ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer;
    @Nullable
    private Function<U, StreamingHttpClientFilterFactory> hostHeaderFilterFactoryFunction =
            address -> new HostHeaderHttpRequesterFilter(unresolvedAddressToCharSequence(address));
    @Nullable
    private StreamingHttpConnectionFilterFactory connectionFilterFactory;
    @Nullable
    private StreamingHttpClientFilterFactory clientFilterFactory;
    private boolean lbReadyFilterEnabled = true;
    private ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> connectionFactoryFilter =
            ConnectionFactoryFilter.identity();

    DefaultSingleAddressHttpClientBuilder(
            final U address, final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer) {
        this.address = requireNonNull(address);
        config = new HttpClientConfig(new TcpClientConfig(false));
        executionContextBuilder = new HttpExecutionContextBuilder();
        influencerChainBuilder = new ClientStrategyInfluencerChainBuilder();
        this.loadBalancerFactory = new StrategyInfluencingLoadBalancerFactory<>();
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
    }

    DefaultSingleAddressHttpClientBuilder(
            final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer) {
        address = null; // Unknown address - template builder pending override via: copy(address)
        config = new HttpClientConfig(new TcpClientConfig(false));
        executionContextBuilder = new HttpExecutionContextBuilder();
        influencerChainBuilder = new ClientStrategyInfluencerChainBuilder();
        this.loadBalancerFactory = newRoundRobinFactory();
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
    }

    private DefaultSingleAddressHttpClientBuilder(@Nullable final U address,
                                                  final DefaultSingleAddressHttpClientBuilder<U, R> from) {
        this.address = address;
        this.proxyAddress = from.proxyAddress;
        config = new HttpClientConfig(from.config);
        executionContextBuilder = new HttpExecutionContextBuilder(from.executionContextBuilder);
        influencerChainBuilder = from.influencerChainBuilder.copy();
        this.loadBalancerFactory = from.loadBalancerFactory;
        this.serviceDiscoverer = from.serviceDiscoverer;
        clientFilterFactory = from.clientFilterFactory;
        connectionFilterFactory = from.connectionFilterFactory;
        hostHeaderFilterFactoryFunction = from.hostHeaderFilterFactoryFunction;
        lbReadyFilterEnabled = from.lbReadyFilterEnabled;
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

    static <U> DefaultSingleAddressHttpClientBuilder<U, InetSocketAddress> forResolvedAddress(final U u,
            final InetSocketAddress address) {
        ServiceDiscoverer<U, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> sd =
                new NoopServiceDiscoverer<>(u, address);
        return new DefaultSingleAddressHttpClientBuilder<>(u, sd);
    }

    static DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forUnknownHostAndPort() {
        return new DefaultSingleAddressHttpClientBuilder<>(globalDnsServiceDiscoverer());
    }

    static final class HttpClientBuildContext<U, R> {
        final DefaultSingleAddressHttpClientBuilder<U, R> builder;
        final HttpExecutionContext executionContext;
        final StreamingHttpRequestResponseFactory reqRespFactory;
        @Nullable
        final U proxyAddress;

        HttpClientBuildContext(final DefaultSingleAddressHttpClientBuilder<U, R> builder,
                               final HttpExecutionContext executionContext,
                               final StreamingHttpRequestResponseFactory reqRespFactory,
                               @Nullable final U proxyAddress) {
            this.builder = builder;
            this.executionContext = executionContext;
            this.reqRespFactory = reqRespFactory;
            this.proxyAddress = proxyAddress;
        }

        Publisher<? extends ServiceDiscovererEvent<R>> discover() {
            assert builder.address != null : "Attempted to buildStreaming with an unknown address";
            return builder.serviceDiscoverer.discover(
                    proxyAddress != null ? proxyAddress : builder.address);
        }

        StreamingHttpClient build() {
            return builder.buildStreaming(this);
        }
    }

    @Override
    public StreamingHttpClient buildStreaming() {
        return buildStreaming(copyBuildCtx());
    }

    private StreamingHttpClient buildStreaming(HttpClientBuildContext<U, R> ctx) {
        final ReadOnlyHttpClientConfig roConfig = config.asReadOnly();
        // Track resources that potentially need to be closed when an exception is thrown during buildStreaming
        final CompositeCloseable closeOnException = newCompositeCloseable();
        try {
            final Publisher<? extends ServiceDiscovererEvent<R>> sdEvents = ctx.discover();

            final StreamingHttpRequestResponseFactory reqRespFactory = ctx.reqRespFactory;

            ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> connectionFactoryFilter =
                    this.connectionFactoryFilter;

            if (roConfig.connectAddress() != null && roConfig.tcpClientConfig().sslContext() != null) {
                connectionFactoryFilter = new ProxyConnectConnectionFactoryFilter<R, FilterableStreamingHttpConnection>(
                        roConfig.connectAddress(), reqRespFactory).append(connectionFactoryFilter);
            }

            // closed by the LoadBalancer
            final ConnectionFactory<R, StreamingHttpConnection> connectionFactory;
            if (roConfig.isH2PriorKnowledge()) {
                connectionFactory = new H2LBHttpConnectionFactory<>(roConfig, ctx.executionContext,
                        connectionFilterFactory, reqRespFactory,
                        influencerChainBuilder.buildForConnectionFactory(
                                ctx.executionContext.executionStrategy()), connectionFactoryFilter);
            } else {
                connectionFactory = reservedConnectionsPipelineEnabled(roConfig) ?
                        new PipelinedLBHttpConnectionFactory<>(roConfig, ctx.executionContext,
                                connectionFilterFactory, reqRespFactory,
                                influencerChainBuilder.buildForConnectionFactory(
                                        ctx.executionContext.executionStrategy()), connectionFactoryFilter) :
                        new NonPipelinedLBHttpConnectionFactory<>(roConfig, ctx.executionContext,
                                connectionFilterFactory, reqRespFactory,
                                influencerChainBuilder.buildForConnectionFactory(
                                        ctx.executionContext.executionStrategy()), connectionFactoryFilter);
            }

            final LoadBalancer<? extends StreamingHttpConnection> lbfUntypedForCast =
                    closeOnException.prepend(loadBalancerFactory.newLoadBalancer(sdEvents, connectionFactory));
            @SuppressWarnings("unchecked")
            final LoadBalancer<LoadBalancedStreamingHttpConnection> lb =
                    (LoadBalancer<LoadBalancedStreamingHttpConnection>) lbfUntypedForCast;

            StreamingHttpClientFilterFactory currClientFilterFactory = clientFilterFactory;

            if (roConfig.connectAddress() != null && roConfig.tcpClientConfig().sslContext() == null) {
                // If we're talking to a proxy over http (not https), rewrite the request-target to absolute-form, as
                // specified by the RFC: https://tools.ietf.org/html/rfc7230#section-5.3.2
                currClientFilterFactory = appendFilter(currClientFilterFactory,
                        proxyAbsoluteAddressFilterFactory());
            }

            if (hostHeaderFilterFactoryFunction != null) {
                currClientFilterFactory = appendFilter(currClientFilterFactory,
                        hostHeaderFilterFactoryFunction.apply(address));
            }

            FilterableStreamingHttpClient lbClient = closeOnException.prepend(
                    new LoadBalancedStreamingHttpClient(ctx.executionContext, lb, reqRespFactory));
            if (lbReadyFilterEnabled) {
                lbClient = new LoadBalancerReadyStreamingHttpClientFilter(4, lb.eventStream(), lbClient);
            }
            return new FilterableClientToClient(currClientFilterFactory != null ?
                    currClientFilterFactory.create(lbClient) : lbClient,
                    ctx.executionContext.executionStrategy(),
                    influencerChainBuilder.buildForClient(ctx.executionContext.executionStrategy()));
        } catch (final Throwable t) {
            closeOnException.closeAsync().subscribe();
            throw t;
        }
    }

    private static StreamingHttpClientFilterFactory appendFilter(
            @Nullable final StreamingHttpClientFilterFactory currClientFilterFactory,
            final StreamingHttpClientFilterFactory appendClientFilterFactory) {
        if (currClientFilterFactory == null) {
            return appendClientFilterFactory;
        } else {
            return currClientFilterFactory.append(appendClientFilterFactory);
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
        final HttpExecutionContext exec = clonedBuilder.executionContextBuilder.build();
        final StreamingHttpRequestResponseFactory reqRespFactory = clonedBuilder.config.isH2PriorKnowledge() ?
                new DefaultStreamingHttpRequestResponseFactory(exec.bufferAllocator(),
                        clonedBuilder.config.h2ClientConfig().h2HeadersFactory(), HTTP_2_0) :
                new DefaultStreamingHttpRequestResponseFactory(exec.bufferAllocator(),
                        clonedBuilder.config.headersFactory(), HTTP_1_1);

        return new HttpClientBuildContext<>(clonedBuilder, exec, reqRespFactory, proxyAddress);
    }

    private AbsoluteAddressHttpRequesterFilter proxyAbsoluteAddressFilterFactory() {
        assert address != null : "address should have been set in constructor";
        return new AbsoluteAddressHttpRequesterFilter("http", unresolvedAddressToCharSequence(address));
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> ioExecutor(final IoExecutor ioExecutor) {
        executionContextBuilder.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> executionStrategy(final HttpExecutionStrategy strategy) {
        executionContextBuilder.executionStrategy(strategy);
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
    public SingleAddressHttpClientBuilder<U, R> h2HeadersFactory(final HttpHeadersFactory headersFactory) {
        config.h2ClientConfig().h2HeadersFactory(headersFactory);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> h2PriorKnowledge(final boolean h2PriorKnowledge) {
        config.tcpClientConfig().autoRead(h2PriorKnowledge);
        config.h2PriorKnowledge(h2PriorKnowledge);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> h2FrameLogger(@Nullable final String h2FrameLogger) {
        config.h2ClientConfig().h2FrameLogger(h2FrameLogger);
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
    public DefaultSingleAddressHttpClientBuilder<U, R> proxyAddress(@Nullable U proxyAddress) {
        this.proxyAddress = proxyAddress;
        if (proxyAddress == null) {
            config.connectAddress(null);
        } else {
            assert address != null : "address should have been set in constructor";
            config.connectAddress(unresolvedAddressToCharSequence(address));
        }
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> appendConnectionFilter(
            final StreamingHttpConnectionFilterFactory factory) {
        if (connectionFilterFactory == null) {
            connectionFilterFactory = requireNonNull(factory);
        } else {
            connectionFilterFactory = connectionFilterFactory.append(requireNonNull(factory));
        }
        influencerChainBuilder.add(factory);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> appendConnectionFactoryFilter(
            final ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> factory) {
        connectionFactoryFilter = connectionFactoryFilter.append(factory);
        influencerChainBuilder.add(factory);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> disableHostHeaderFallback() {
        hostHeaderFilterFactoryFunction = null;
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> disableWaitForLoadBalancer() {
        lbReadyFilterEnabled = false;
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> enableHostHeaderFallback(final CharSequence hostHeader) {
        hostHeaderFilterFactoryFunction = address -> new HostHeaderHttpRequesterFilter(hostHeader);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> appendClientFilter(
            final StreamingHttpClientFilterFactory factory) {
        if (clientFilterFactory == null) {
            clientFilterFactory = requireNonNull(factory);
        } else {
            clientFilterFactory = clientFilterFactory.append(factory);
        }
        influencerChainBuilder.add(factory);
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
            final LoadBalancerFactory<R, StreamingHttpConnection> loadBalancerFactory) {
        this.loadBalancerFactory = requireNonNull(loadBalancerFactory);
        influencerChainBuilder.add(loadBalancerFactory);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> sslConfig(@Nullable final SslConfig sslConfig) {
        config.tcpClientConfig().sslConfig(sslConfig);
        return this;
    }

    void appendToStrategyInfluencer(MultiAddressHttpClientFilterFactory<U> multiAddressHttpClientFilterFactory) {
        influencerChainBuilder.add(multiAddressHttpClientFilterFactory);
    }

    HttpExecutionStrategyInfluencer buildStrategyInfluencerForClient(HttpExecutionStrategy strategy) {
        return influencerChainBuilder.buildForClient(strategy);
    }

    // TODO(derek): Temporary, so we can re-enable the ability to create non-pipelined connections for perf testing.
    private static boolean reservedConnectionsPipelineEnabled(final ReadOnlyHttpClientConfig roConfig) {
        return roConfig.maxPipelinedRequests() > 1 ||
                Boolean.valueOf(System.getProperty("io.servicetalk.http.netty.reserved.connections.pipeline", "true"));
    }

    private static <U> CharSequence unresolvedAddressToCharSequence(final U address) {
        if (address instanceof CharSequence) {
            return (CharSequence) address;
        }
        if (address instanceof HostAndPort) {
            final HostAndPort hostAndPort = (HostAndPort) address;
            return toSocketAddressString(hostAndPort.hostName(), hostAndPort.port());
        }
        throw new IllegalArgumentException("Unsupported address type");
    }

    private static final class StrategyInfluencingLoadBalancerFactory<R>
            implements LoadBalancerFactory<R, StreamingHttpConnection>, HttpExecutionStrategyInfluencer {

        private final LoadBalancerFactory<R, StreamingHttpConnection> delegate = newRoundRobinFactory();

        @Override
        public LoadBalancer<StreamingHttpConnection> newLoadBalancer(
                final Publisher<? extends ServiceDiscovererEvent<R>> eventPublisher,
                final ConnectionFactory<R, ? extends StreamingHttpConnection> connectionFactory) {
            return delegate.newLoadBalancer(eventPublisher, connectionFactory);
        }

        @Override
        public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
            // We know that round robin load balancer does not block.
            return strategy;
        }
    }

    private static final class NoopServiceDiscoverer<OriginalAddress>
            implements ServiceDiscoverer<OriginalAddress, InetSocketAddress,
            ServiceDiscovererEvent<InetSocketAddress>> {
        private final ListenableAsyncCloseable closeable = emptyAsyncCloseable();

        private final Publisher<ServiceDiscovererEvent<InetSocketAddress>> resolution;
        private final OriginalAddress originalAddress;

        private NoopServiceDiscoverer(final OriginalAddress originalAddress, final InetSocketAddress address) {
            this.originalAddress = requireNonNull(originalAddress);
            resolution = Publisher.<ServiceDiscovererEvent<InetSocketAddress>>from(
                    new DefaultServiceDiscovererEvent<>(requireNonNull(address), true))
                    // LoadBalancer will flag a termination of service discoverer Publisher as unexpected.
                    .concat(never());
        }

        @Override
        public Publisher<ServiceDiscovererEvent<InetSocketAddress>> discover(final OriginalAddress address) {
            if (!this.originalAddress.equals(address)) {
                return failed(new IllegalArgumentException("Unexpected address resolution request: " + address));
            }
            return resolution;
        }

        @Override
        public Completable onClose() {
            return closeable.onClose();
        }

        @Override
        public Completable closeAsync() {
            return closeable.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return closeable.closeAsyncGracefully();
        }
    }
}
