/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.AutoRetryStrategyProvider;
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.DefaultAutoRetryStrategyProvider.Builder;
import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.CharSequences;
import io.servicetalk.http.api.DefaultServiceDiscoveryRetryStrategy;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.MultiAddressHttpClientFilterFactory;
import io.servicetalk.http.api.ServiceDiscoveryRetryStrategy;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientSecurityConfigurator;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;

import io.netty.handler.ssl.SslContext;
import io.netty.util.NetUtil;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.netty.util.NetUtil.toSocketAddressString;
import static io.servicetalk.client.api.AutoRetryStrategyProvider.DISABLE_AUTO_RETRIES;
import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.netty.GlobalDnsServiceDiscoverer.globalDnsServiceDiscoverer;
import static io.servicetalk.http.netty.GlobalDnsServiceDiscoverer.globalSrvDnsServiceDiscoverer;
import static java.time.Duration.ofSeconds;
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
    private HttpLoadBalancerFactory<R> loadBalancerFactory;
    private ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer;
    private Function<U, CharSequence> hostToCharSequenceFunction = this::toAuthorityForm;
    @Nullable
    private ServiceDiscoveryRetryStrategy<R, ? super ServiceDiscovererEvent<R>> serviceDiscovererRetryStrategy;
    @Nullable
    private Function<U, StreamingHttpClientFilterFactory> hostHeaderFilterFactoryFunction =
            address -> new HostHeaderHttpRequesterFilter(hostToCharSequenceFunction.apply(address));
    @Nullable
    private StreamingHttpConnectionFilterFactory connectionFilterFactory;
    @Nullable
    private StreamingHttpClientFilterFactory clientFilterFactory;
    @Nullable
    private AutoRetryStrategyProvider autoRetry = new Builder().build();
    private ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> connectionFactoryFilter =
            ConnectionFactoryFilter.identity();

    DefaultSingleAddressHttpClientBuilder(
            final U address, final U proxyAddress, Function<U, CharSequence> hostToCharSequenceFunction,
            final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer) {
        this(address, serviceDiscoverer);
        this.proxyAddress = proxyAddress;
        this.hostToCharSequenceFunction = requireNonNull(hostToCharSequenceFunction);
        config.connectAddress(hostToCharSequenceFunction.apply(address));
    }

    DefaultSingleAddressHttpClientBuilder(
            final U address, final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer) {
        this.address = requireNonNull(address);
        config = new HttpClientConfig();
        executionContextBuilder = new HttpExecutionContextBuilder();
        influencerChainBuilder = new ClientStrategyInfluencerChainBuilder();
        this.loadBalancerFactory = DefaultHttpLoadBalancerFactory.Builder.<R>fromDefaults().build();
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
    }

    DefaultSingleAddressHttpClientBuilder(
            final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer) {
        address = null; // Unknown address - template builder pending override via: copy(address)
        config = new HttpClientConfig();
        executionContextBuilder = new HttpExecutionContextBuilder();
        influencerChainBuilder = new ClientStrategyInfluencerChainBuilder();
        this.loadBalancerFactory = DefaultHttpLoadBalancerFactory.Builder.<R>fromDefaults().build();
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
    }

    private DefaultSingleAddressHttpClientBuilder(@Nullable final U address,
                                                  final DefaultSingleAddressHttpClientBuilder<U, R> from) {
        this.address = address;
        proxyAddress = from.proxyAddress;
        config = new HttpClientConfig(from.config);
        executionContextBuilder = new HttpExecutionContextBuilder(from.executionContextBuilder);
        influencerChainBuilder = from.influencerChainBuilder.copy();
        loadBalancerFactory = from.loadBalancerFactory;
        serviceDiscoverer = from.serviceDiscoverer;
        serviceDiscovererRetryStrategy = from.serviceDiscovererRetryStrategy;
        clientFilterFactory = from.clientFilterFactory;
        connectionFilterFactory = from.connectionFilterFactory;
        hostToCharSequenceFunction = from.hostToCharSequenceFunction;
        hostHeaderFilterFactoryFunction = from.hostHeaderFilterFactoryFunction;
        autoRetry = from.autoRetry;
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

    static DefaultSingleAddressHttpClientBuilder<String, InetSocketAddress> forServiceAddress(
            final String serviceName) {
        return new DefaultSingleAddressHttpClientBuilder<>(serviceName, globalSrvDnsServiceDiscoverer());
    }

    static DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forHostAndPortViaProxy(
            final HostAndPort address, final HostAndPort proxyAddress) {
        return new DefaultSingleAddressHttpClientBuilder<>(address, proxyAddress,
                hostAndPort -> toSocketAddressString(hostAndPort.hostName(), hostAndPort.port()),
                globalDnsServiceDiscoverer());
    }

    static <U> DefaultSingleAddressHttpClientBuilder<U, InetSocketAddress> forResolvedAddress(
            final U u, final InetSocketAddress address) {
        ServiceDiscoverer<U, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> sd =
                new NoopServiceDiscoverer<>(u, address);
        return new DefaultSingleAddressHttpClientBuilder<>(u, sd);
    }

    static DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forResolvedAddressViaProxy(
            final HostAndPort u, final InetSocketAddress address, final HostAndPort proxyAddress) {
        ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> sd =
                new NoopServiceDiscoverer<>(u, address);
        return new DefaultSingleAddressHttpClientBuilder<>(u, proxyAddress,
                hostAndPort -> toSocketAddressString(hostAndPort.hostName(), hostAndPort.port()), sd);
    }

    static DefaultSingleAddressHttpClientBuilder<InetSocketAddress, InetSocketAddress> forResolvedAddressViaProxy(
            final InetSocketAddress u, final InetSocketAddress address, final InetSocketAddress proxyAddress) {
        ServiceDiscoverer<InetSocketAddress, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> sd =
                new NoopServiceDiscoverer<>(u, address);
        return new DefaultSingleAddressHttpClientBuilder<>(u, proxyAddress, NetUtil::toSocketAddressString, sd);
    }

    static DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forUnknownHostAndPort() {
        return new DefaultSingleAddressHttpClientBuilder<>(globalDnsServiceDiscoverer());
    }

    static final class HttpClientBuildContext<U, R> {
        final DefaultSingleAddressHttpClientBuilder<U, R> builder;
        final HttpExecutionContext executionContext;
        final StreamingHttpRequestResponseFactory reqRespFactory;
        final ServiceDiscoveryRetryStrategy<R, ? super ServiceDiscovererEvent<R>> serviceDiscovererRetryStrategy;
        @Nullable
        final U proxyAddress;

        HttpClientBuildContext(
                final DefaultSingleAddressHttpClientBuilder<U, R> builder, final HttpExecutionContext executionContext,
                final StreamingHttpRequestResponseFactory reqRespFactory,
                @Nullable final ServiceDiscoveryRetryStrategy<R, ? super ServiceDiscovererEvent<R>> sdRetryStrategy,
                @Nullable final U proxyAddress) {
            this.builder = builder;
            this.executionContext = executionContext;
            this.reqRespFactory = reqRespFactory;
            this.serviceDiscovererRetryStrategy = sdRetryStrategy == null ?
                    DefaultServiceDiscoveryRetryStrategy.Builder.<R>withDefaults(executionContext.executor(),
                            ofSeconds(60)).build() : sdRetryStrategy;
            this.proxyAddress = proxyAddress;
        }

        Publisher<? extends ServiceDiscovererEvent<R>> discover() {
            assert builder.address != null : "Attempted to buildStreaming with an unknown address";
            final Publisher<? extends ServiceDiscovererEvent<R>> sdEvents =
                    builder.serviceDiscoverer.discover(proxyAddress != null ? proxyAddress : builder.address);
            return serviceDiscovererRetryStrategy.apply(sdEvents);
        }

        Publisher<? extends ServiceDiscovererEvent<R>> discover(final SdStatusCompletable sdStatus) {
            assert builder.address != null : "Attempted to buildStreaming with an unknown address";
            final Publisher<? extends ServiceDiscovererEvent<R>> sdEvents =
                    builder.serviceDiscoverer.discover(proxyAddress != null ? proxyAddress : builder.address);
            return serviceDiscovererRetryStrategy.apply(sdEvents.beforeOnError(sdStatus::nextError))
                    .whenOnNext(__ -> sdStatus.resetError());
            // We do not complete sdStatus to let LB decide when to retry if SD completes.
        }

        StreamingHttpClient build() {
            return buildStreaming(this);
        }
    }

    @Override
    public StreamingHttpClient buildStreaming() {
        return buildStreaming(copyBuildCtx());
    }

    private static <U, R> StreamingHttpClient buildStreaming(final HttpClientBuildContext<U, R> ctx) {
        final ReadOnlyHttpClientConfig roConfig = ctx.builder.config.asReadOnly();

        if (roConfig.h2Config() != null && roConfig.hasProxy()) {
            throw new IllegalStateException("Proxying is not yet supported with HTTP/2");
        }

        // Track resources that potentially need to be closed when an exception is thrown during buildStreaming
        final CompositeCloseable closeOnException = newCompositeCloseable();
        try {
            final SdStatusCompletable sdStatus = new SdStatusCompletable();
            final Publisher<? extends ServiceDiscovererEvent<R>> sdEvents = ctx.discover(sdStatus);

            final StreamingHttpRequestResponseFactory reqRespFactory = ctx.reqRespFactory;

            ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> connectionFactoryFilter =
                    ctx.builder.connectionFactoryFilter;

            final SslContext sslContext = roConfig.tcpConfig().sslContext();
            if (roConfig.hasProxy() && sslContext != null) {
                assert roConfig.connectAddress() != null;
                connectionFactoryFilter = new ProxyConnectConnectionFactoryFilter<R, FilterableStreamingHttpConnection>(
                        roConfig.connectAddress()).append(connectionFactoryFilter);
            }

            final HttpExecutionStrategy executionStrategy = ctx.executionContext.executionStrategy();
            // closed by the LoadBalancer
            final ConnectionFactory<R, LoadBalancedStreamingHttpConnection> connectionFactory;
            if (roConfig.isH2PriorKnowledge()) {
                connectionFactory = new H2LBHttpConnectionFactory<>(roConfig, ctx.executionContext,
                        ctx.builder.connectionFilterFactory, reqRespFactory,
                        ctx.builder.influencerChainBuilder.buildForConnectionFactory(executionStrategy),
                        connectionFactoryFilter, ctx.builder.loadBalancerFactory::toLoadBalancedConnection);
            } else if (roConfig.tcpConfig().isAlpnConfigured()) {
                connectionFactory = new AlpnLBHttpConnectionFactory<>(roConfig, ctx.executionContext,
                        ctx.builder.connectionFilterFactory, reqRespFactory,
                        ctx.builder.influencerChainBuilder.buildForConnectionFactory(executionStrategy),
                        connectionFactoryFilter, ctx.builder.loadBalancerFactory::toLoadBalancedConnection);
            } else {
                connectionFactory = new PipelinedLBHttpConnectionFactory<>(roConfig, ctx.executionContext,
                        ctx.builder.connectionFilterFactory, reqRespFactory,
                        ctx.builder.influencerChainBuilder.buildForConnectionFactory(executionStrategy),
                        connectionFactoryFilter, ctx.builder.loadBalancerFactory::toLoadBalancedConnection);
            }

            final LoadBalancer<LoadBalancedStreamingHttpConnection> lb =
                    closeOnException.prepend(ctx.builder.loadBalancerFactory.newLoadBalancer(sdEvents,
                            connectionFactory));

            StreamingHttpClientFilterFactory currClientFilterFactory = ctx.builder.clientFilterFactory;
            if (roConfig.hasProxy() && sslContext == null) {
                // If we're talking to a proxy over http (not https), rewrite the request-target to absolute-form, as
                // specified by the RFC: https://tools.ietf.org/html/rfc7230#section-5.3.2
                currClientFilterFactory = appendFilter(currClientFilterFactory,
                        ctx.builder.proxyAbsoluteAddressFilterFactory());
            }
            if (ctx.builder.hostHeaderFilterFactoryFunction != null) {
                currClientFilterFactory = appendFilter(currClientFilterFactory,
                        ctx.builder.hostHeaderFilterFactoryFunction.apply(ctx.builder.address));
            }

            FilterableStreamingHttpClient lbClient = closeOnException.prepend(
                    new LoadBalancedStreamingHttpClient(ctx.executionContext, lb, reqRespFactory));
            if (ctx.builder.autoRetry != null) {
                lbClient = new AutoRetryFilter(lbClient,
                        ctx.builder.autoRetry.newStrategy(lb.eventStream(), sdStatus));
            }
            return new FilterableClientToClient(currClientFilterFactory != null ?
                    currClientFilterFactory.create(lbClient) : lbClient, executionStrategy,
                    ctx.builder.influencerChainBuilder.buildForClient(executionStrategy));
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
        final StreamingHttpRequestResponseFactory reqRespFactory;
        if (clonedBuilder.config.isH2PriorKnowledge()) {
            assert clonedBuilder.config.protocolConfigs().h2Config() != null;
            reqRespFactory = new DefaultStreamingHttpRequestResponseFactory(exec.bufferAllocator(),
                    clonedBuilder.config.protocolConfigs().h2Config().headersFactory(), HTTP_2_0);
        } else {
            assert clonedBuilder.config.protocolConfigs().h1Config() != null;
            reqRespFactory = new DefaultStreamingHttpRequestResponseFactory(exec.bufferAllocator(),
                    clonedBuilder.config.protocolConfigs().h1Config().headersFactory(), HTTP_1_1);
        }

        return new HttpClientBuildContext<>(clonedBuilder, exec, reqRespFactory, serviceDiscovererRetryStrategy,
                proxyAddress);
    }

    private AbsoluteAddressHttpRequesterFilter proxyAbsoluteAddressFilterFactory() {
        assert address != null : "address should have been set in constructor";
        return new AbsoluteAddressHttpRequesterFilter("http", hostToCharSequenceFunction.apply(address));
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
        config.tcpConfig().socketOption(option, value);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> enableWireLogging(final String loggerName) {
        config.tcpConfig().enableWireLogging(loggerName);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> protocols(final HttpProtocolConfig... protocols) {
        config.protocolConfigs().protocols(protocols);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> appendConnectionFilter(
            final StreamingHttpConnectionFilterFactory factory) {
        connectionFilterFactory = connectionFilterFactory == null ?
                requireNonNull(factory) : connectionFilterFactory.append(factory);
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
    public SingleAddressHttpClientBuilder<U, R> autoRetryStrategy(
            final AutoRetryStrategyProvider autoRetryStrategyProvider) {
        autoRetry = autoRetryStrategyProvider == DISABLE_AUTO_RETRIES ? null :
                requireNonNull(autoRetryStrategyProvider);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> unresolvedAddressToHost(
            Function<U, CharSequence> unresolvedAddressToHostFunction) {
        this.hostToCharSequenceFunction = requireNonNull(unresolvedAddressToHostFunction);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> appendClientFilter(
            final StreamingHttpClientFilterFactory factory) {
        clientFilterFactory = clientFilterFactory == null ? requireNonNull(factory) :
                clientFilterFactory.append(factory);
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
    public DefaultSingleAddressHttpClientBuilder<U, R> retryServiceDiscoveryErrors(
            final ServiceDiscoveryRetryStrategy<R, ServiceDiscovererEvent<R>> retryStrategy) {
        this.serviceDiscovererRetryStrategy = requireNonNull(retryStrategy);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> loadBalancerFactory(
            final HttpLoadBalancerFactory<R> loadBalancerFactory) {
        this.loadBalancerFactory = requireNonNull(loadBalancerFactory);
        influencerChainBuilder.add(loadBalancerFactory);
        return this;
    }

    @Override
    public SingleAddressHttpClientSecurityConfigurator<U, R> secure() {
        assert address != null;
        return new DefaultSingleAddressHttpClientSecurityConfigurator<>(
                unresolvedHostFunction(address).toString(), unresolvedPortFunction(address),
                securityConfig -> {
                    config.tcpConfig().secure(securityConfig);
                    return DefaultSingleAddressHttpClientBuilder.this;
                });
    }

    void appendToStrategyInfluencer(MultiAddressHttpClientFilterFactory<U> multiAddressHttpClientFilterFactory) {
        influencerChainBuilder.add(multiAddressHttpClientFilterFactory);
    }

    HttpExecutionStrategyInfluencer buildStrategyInfluencerForClient(HttpExecutionStrategy strategy) {
        return influencerChainBuilder.buildForClient(strategy);
    }

    private CharSequence toAuthorityForm(final U address) {
        if (address instanceof CharSequence) {
            return (CharSequence) address;
        }
        if (address instanceof HostAndPort) {
            final HostAndPort hostAndPort = (HostAndPort) address;
            return toSocketAddressString(hostAndPort.hostName(), hostAndPort.port());
        }
        if (address instanceof InetSocketAddress) {
            return toSocketAddressString((InetSocketAddress) address);
        }
        throw new IllegalArgumentException("Unsupported address type, unable to convert " + address.getClass() +
                " to CharSequence");
    }

    private CharSequence unresolvedHostFunction(final U address) {
        if (address instanceof HostAndPort) {
            return ((HostAndPort) address).hostName();
        }
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) address).getHostString();
        }
        CharSequence cs = hostToCharSequenceFunction.apply(address);
        int colon = CharSequences.indexOf(cs, ':', 0);
        if (colon < 0) {
            return cs;
        }
        return cs.subSequence(0, colon);
    }

    private int unresolvedPortFunction(final U address) {
        if (address instanceof HostAndPort) {
            return ((HostAndPort) address).port();
        }
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) address).getPort();
        }
        CharSequence cs = hostToCharSequenceFunction.apply(address);
        int colon = CharSequences.indexOf(cs, ':', 0);
        if (colon < 0) {
            return -1;
        }
        return Integer.parseInt(cs.subSequence(colon + 1, cs.length() - 1).toString());
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

    private static final class SdStatusCompletable extends Completable {
        private volatile CompletableSource.Processor processor = newCompletableProcessor();
        private boolean seenError;  //  this is only accessed from nextError and resetError which are not concurrent

        @Override
        protected void handleSubscribe(final Subscriber subscriber) {
            processor.subscribe(subscriber);
        }

        void nextError(final Throwable t) {
            seenError = true;
            final CompletableSource.Processor oldProcessor = processor;
            oldProcessor.onError(t);
            final CompletableSource.Processor newProcessor = newCompletableProcessor();
            newProcessor.onError(t);
            processor = newProcessor;
        }

        void resetError() {
            if (seenError) {
                processor = newCompletableProcessor();
                seenError = false;
            }
        }
    }
}
