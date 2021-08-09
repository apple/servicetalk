/*
 * Copyright © 2018-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.buffer.api.CharSequences;
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
import io.servicetalk.http.api.DefaultServiceDiscoveryRetryStrategy;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.ServiceDiscoveryRetryStrategy;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;

import io.netty.handler.ssl.SslContext;
import io.netty.util.NetUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.time.Duration;
import java.util.Collection;
import java.util.function.BooleanSupplier;
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
import static io.servicetalk.http.netty.AlpnIds.HTTP_2;
import static io.servicetalk.http.netty.GlobalDnsServiceDiscoverer.globalDnsServiceDiscoverer;
import static io.servicetalk.http.netty.GlobalDnsServiceDiscoverer.globalSrvDnsServiceDiscoverer;
import static java.lang.Integer.parseInt;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

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
    static final Duration SD_RETRY_STRATEGY_INIT_DURATION = ofSeconds(10);
    static final Duration SD_RETRY_STRATEGY_JITTER = ofSeconds(5);
    @Nullable
    private final U address;
    @Nullable
    private U proxyAddress;
    private final HttpClientConfig config;
    final HttpExecutionContextBuilder executionContextBuilder;
    private final ClientStrategyInfluencerChainBuilder influencerChainBuilder;
    private HttpLoadBalancerFactory<R> loadBalancerFactory;
    private ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer;
    private Function<U, CharSequence> hostToCharSequenceFunction = this::toAuthorityForm;
    private boolean addHostHeaderFallbackFilter = true;
    @Nullable
    private ServiceDiscoveryRetryStrategy<R, ServiceDiscovererEvent<R>> serviceDiscovererRetryStrategy;
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
            final ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer) {
        this(address, serviceDiscoverer);
        this.proxyAddress = proxyAddress;
        this.hostToCharSequenceFunction = requireNonNull(hostToCharSequenceFunction);
        config.connectAddress(hostToCharSequenceFunction.apply(address));
    }

    DefaultSingleAddressHttpClientBuilder(
            final U address, final ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer) {
        this.address = requireNonNull(address);
        config = new HttpClientConfig();
        executionContextBuilder = new HttpExecutionContextBuilder();
        influencerChainBuilder = new ClientStrategyInfluencerChainBuilder();
        this.loadBalancerFactory = DefaultHttpLoadBalancerFactory.Builder.<R>fromDefaults().build();
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
    }

    DefaultSingleAddressHttpClientBuilder(
            final ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer) {
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
        addHostHeaderFallbackFilter = from.addHostHeaderFallbackFilter;
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

    static <U, R extends SocketAddress> DefaultSingleAddressHttpClientBuilder<U, R> forResolvedAddress(
            final U u, final R address) {
        ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> sd =
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
        private final ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> sd;
        private final SdStatusCompletable sdStatus;

        @Nullable
        private final ServiceDiscoveryRetryStrategy<R, ServiceDiscovererEvent<R>> serviceDiscovererRetryStrategy;
        @Nullable
        private final U proxyAddress;

        HttpClientBuildContext(
                final DefaultSingleAddressHttpClientBuilder<U, R> builder,
                final ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> sd,
                @Nullable final ServiceDiscoveryRetryStrategy<R, ServiceDiscovererEvent<R>>
                        serviceDiscovererRetryStrategy,
                @Nullable final U proxyAddress) {
            this.builder = builder;
            this.serviceDiscovererRetryStrategy = serviceDiscovererRetryStrategy;
            this.proxyAddress = proxyAddress;
            this.sd = sd;
            this.sdStatus = new SdStatusCompletable();
        }

        U address() {
            assert builder.address != null : "Attempted to buildStreaming with an unknown address";
            return proxyAddress != null ? proxyAddress : builder.address;
        }

        HttpClientConfig httpConfig() {
            return builder.config;
        }

        StreamingHttpClient build() {
            return buildStreaming(this);
        }

        ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer(
                HttpExecutionContext executionContext) {
            return new RetryingServiceDiscoverer<>(new StatusAwareServiceDiscoverer<>(sd, sdStatus),
                            serviceDiscovererRetryStrategy == null ?
                                    DefaultServiceDiscoveryRetryStrategy.Builder
                                            .<R>withDefaults(executionContext.executor(),
                                                    SD_RETRY_STRATEGY_INIT_DURATION, SD_RETRY_STRATEGY_JITTER).build() :
                                    serviceDiscovererRetryStrategy);
        }
    }

    @Override
    public StreamingHttpClient buildStreaming() {
        return buildStreaming(copyBuildCtx());
    }

    private static <U, R> StreamingHttpClient buildStreaming(final HttpClientBuildContext<U, R> ctx) {
        final ReadOnlyHttpClientConfig roConfig = ctx.httpConfig().asReadOnly();
        final HttpExecutionContext executionContext = ctx.builder.executionContextBuilder.build();
        if (roConfig.h2Config() != null && roConfig.hasProxy()) {
            throw new IllegalStateException("Proxying is not yet supported with HTTP/2");
        }

        // Track resources that potentially need to be closed when an exception is thrown during buildStreaming
        final CompositeCloseable closeOnException = newCompositeCloseable();
        try {
            final Publisher<ServiceDiscovererEvent<R>> sdEvents =
                    ctx.serviceDiscoverer(executionContext).discover(ctx.address()).flatMapConcatIterable(identity());

            ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> connectionFactoryFilter =
                    ctx.builder.connectionFactoryFilter;

            final SslContext sslContext = roConfig.tcpConfig().sslContext();
            if (roConfig.hasProxy() && sslContext != null) {
                assert roConfig.connectAddress() != null;
                connectionFactoryFilter = appendConnectionFactoryFilter(
                        new ProxyConnectConnectionFactoryFilter<>(roConfig.connectAddress()), connectionFactoryFilter);
            }

            final HttpExecutionStrategy executionStrategy = executionContext.executionStrategy();
            // closed by the LoadBalancer
            final ConnectionFactory<R, LoadBalancedStreamingHttpConnection> connectionFactory;
            final StreamingHttpRequestResponseFactory reqRespFactory = defaultReqRespFactory(roConfig,
                    executionContext.bufferAllocator());
            if (roConfig.isH2PriorKnowledge()) {
                H2ProtocolConfig h2Config = roConfig.h2Config();
                assert h2Config != null;
                connectionFactory = new H2LBHttpConnectionFactory<>(roConfig, executionContext,
                        ctx.builder.connectionFilterFactory, reqRespFactory,
                        ctx.builder.influencerChainBuilder.buildForConnectionFactory(executionStrategy),
                        connectionFactoryFilter, ctx.builder.loadBalancerFactory::toLoadBalancedConnection);
            } else if (roConfig.tcpConfig().preferredAlpnProtocol() != null) {
                H1ProtocolConfig h1Config = roConfig.h1Config();
                H2ProtocolConfig h2Config = roConfig.h2Config();
                connectionFactory = new AlpnLBHttpConnectionFactory<>(roConfig, executionContext,
                        ctx.builder.connectionFilterFactory, new AlpnReqRespFactoryFunc(
                                executionContext.bufferAllocator(),
                                h1Config == null ? null : h1Config.headersFactory(),
                                h2Config == null ? null : h2Config.headersFactory()),
                        ctx.builder.influencerChainBuilder.buildForConnectionFactory(executionStrategy),
                        connectionFactoryFilter, ctx.builder.loadBalancerFactory::toLoadBalancedConnection);
            } else {
                H1ProtocolConfig h1Config = roConfig.h1Config();
                assert h1Config != null;
                connectionFactory = new PipelinedLBHttpConnectionFactory<>(roConfig, executionContext,
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
            if (ctx.builder.addHostHeaderFallbackFilter) {
                currClientFilterFactory = appendFilter(currClientFilterFactory, new HostHeaderHttpRequesterFilter(
                        ctx.builder.hostToCharSequenceFunction.apply(ctx.builder.address)));
            }

            FilterableStreamingHttpClient lbClient = closeOnException.prepend(
                    new LoadBalancedStreamingHttpClient(executionContext, lb, reqRespFactory));
            if (ctx.builder.autoRetry != null) {
                lbClient = new AutoRetryFilter(lbClient,
                        ctx.builder.autoRetry.newStrategy(lb.eventStream(), ctx.sdStatus));
            }
            return new FilterableClientToClient(currClientFilterFactory != null ?
                    currClientFilterFactory.create(lbClient) : lbClient, executionStrategy,
                    ctx.builder.influencerChainBuilder.buildForClient(executionStrategy));
        } catch (final Throwable t) {
            closeOnException.closeAsync().subscribe();
            throw t;
        }
    }

    static StreamingHttpRequestResponseFactory defaultReqRespFactory(ReadOnlyHttpClientConfig roConfig,
                                                                     BufferAllocator allocator) {
        if (roConfig.isH2PriorKnowledge()) {
            H2ProtocolConfig h2Config = roConfig.h2Config();
            assert h2Config != null;
            return new DefaultStreamingHttpRequestResponseFactory(allocator, h2Config.headersFactory(), HTTP_2_0);
        } else if (roConfig.tcpConfig().preferredAlpnProtocol() != null) {
            H1ProtocolConfig h1Config = roConfig.h1Config();
            H2ProtocolConfig h2Config = roConfig.h2Config();
            // The client can't know which protocol will be negotiated on the connection/transport level, so just
            // default to the preferred protocol and pay additional conversion costs if we don't get our preference.
            String preferredAlpnProtocol = roConfig.tcpConfig().preferredAlpnProtocol();
            if (HTTP_2.equals(preferredAlpnProtocol)) {
                assert h2Config != null;
                return new DefaultStreamingHttpRequestResponseFactory(allocator, h2Config.headersFactory(), HTTP_2_0);
            } else { // just fall back to h1, all other protocols should convert to/from it.
                if (h1Config == null) {
                    throw new IllegalStateException(preferredAlpnProtocol +
                            " is preferred ALPN protocol, falling back to " + HTTP_1_1 + " but the " + HTTP_1_1 +
                            " protocol was not configured.");
                }
                return new DefaultStreamingHttpRequestResponseFactory(allocator, h1Config.headersFactory(), HTTP_1_1);
            }
        } else {
            H1ProtocolConfig h1Config = roConfig.h1Config();
            assert h1Config != null;
            return new DefaultStreamingHttpRequestResponseFactory(allocator, h1Config.headersFactory(), HTTP_1_1);
        }
    }

    private static StreamingHttpClientFilterFactory appendFilter(
            @Nullable final StreamingHttpClientFilterFactory currClientFilterFactory,
            final StreamingHttpClientFilterFactory appendClientFilterFactory) {
        if (currClientFilterFactory == null) {
            return appendClientFilterFactory;
        } else {
            return client -> currClientFilterFactory.create(appendClientFilterFactory.create(client));
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
        return new HttpClientBuildContext<>(clonedBuilder,
                this.serviceDiscoverer, this.serviceDiscovererRetryStrategy, this.proxyAddress);
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
    public SingleAddressHttpClientBuilder<U, R> enableWireLogging(final String loggerName, final LogLevel logLevel,
                                                                  final BooleanSupplier logUserData) {
        config.tcpConfig().enableWireLogging(loggerName, logLevel, logUserData);
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
        requireNonNull(factory);
        connectionFilterFactory = appendConnectionFilter(connectionFilterFactory, factory);
        influencerChainBuilder.add(factory);
        return this;
    }

    // Use another method to keep final references and avoid StackOverflowError
    private static StreamingHttpConnectionFilterFactory appendConnectionFilter(
            @Nullable final StreamingHttpConnectionFilterFactory current,
            final StreamingHttpConnectionFilterFactory next) {
        return current == null ? next : connection -> current.create(next.create(connection));
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> appendConnectionFactoryFilter(
            final ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> factory) {
        requireNonNull(factory);
        connectionFactoryFilter = appendConnectionFactoryFilter(connectionFactoryFilter, factory);
        influencerChainBuilder.add(factory);
        return this;
    }

    private static <R> ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> appendConnectionFactoryFilter(
            final ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> current,
            final ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> next) {
        return connection -> current.create(next.create(connection));
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> disableHostHeaderFallback() {
        addHostHeaderFallbackFilter = false;
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> allowDropResponseTrailers(final boolean allowDrop) {
        config.protocolConfigs().allowDropTrailersReadFromTransport(allowDrop);
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
            final Function<U, CharSequence> unresolvedAddressToHostFunction) {
        this.hostToCharSequenceFunction = requireNonNull(unresolvedAddressToHostFunction);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> appendClientFilter(
            final StreamingHttpClientFilterFactory factory) {
        requireNonNull(factory);
        clientFilterFactory = appendFilter(clientFilterFactory, factory);
        influencerChainBuilder.add(factory);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> serviceDiscoverer(
            final ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer) {
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
    public DefaultSingleAddressHttpClientBuilder<U, R> sslConfig(ClientSslConfig sslConfig) {
        assert address != null;
        // defer setting the fallback host/port so the user has a chance to configure hostToCharSequenceFunction.
        setFallbackHostAndPort(config, address);
        config.tcpConfig().sslConfig(sslConfig);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> inferPeerHost(boolean shouldInfer) {
        config.inferPeerHost(shouldInfer);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> inferPeerPort(boolean shouldInfer) {
        config.inferPeerPort(shouldInfer);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> inferSniHostname(boolean shouldInfer) {
        config.inferSniHostname(shouldInfer);
        return this;
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
        return address.toString();
    }

    private void setFallbackHostAndPort(HttpClientConfig config, U address) {
        if (address instanceof HostAndPort) {
            HostAndPort hostAndPort = (HostAndPort) address;
            config.fallbackPeerHost(hostAndPort.hostName());
            config.fallbackPeerPort(hostAndPort.port());
        } else if (address instanceof InetSocketAddress) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
            config.fallbackPeerHost(inetSocketAddress.getHostString());
            config.fallbackPeerPort(inetSocketAddress.getPort());
        } else {
            CharSequence cs = hostToCharSequenceFunction.apply(address);
            if (cs == null) {
                config.fallbackPeerHost(null);
                config.fallbackPeerPort(-1);
            } else {
                int colon = CharSequences.indexOf(cs, ':', 0);
                if (colon < 0) {
                    config.fallbackPeerHost(cs.toString());
                    config.fallbackPeerPort(-1);
                } else if (cs.charAt(0) == '[') {
                    colon = CharSequences.indexOf(cs, ']', 1);
                    if (colon < 0) {
                        throw new IllegalArgumentException("unable to find end ']' of IPv6 address: " + cs);
                    }
                    config.fallbackPeerHost(cs.subSequence(1, colon).toString());
                    ++colon;
                    if (colon >= cs.length()) {
                        config.fallbackPeerPort(-1);
                    } else if (cs.charAt(colon) != ':') {
                        throw new IllegalArgumentException("':' expected after ']' for IPv6 address: " + cs);
                    } else {
                        config.fallbackPeerPort(parseInt(cs.subSequence(colon + 1, cs.length()).toString()));
                    }
                } else {
                    config.fallbackPeerHost(cs.subSequence(0, colon).toString());
                    config.fallbackPeerPort(parseInt(cs.subSequence(colon + 1, cs.length()).toString()));
                }
            }
        }
    }

    private static final class NoopServiceDiscoverer<OriginalAddress, ResolvedAddress>
            implements ServiceDiscoverer<OriginalAddress, ResolvedAddress,
            ServiceDiscovererEvent<ResolvedAddress>> {
        private final ListenableAsyncCloseable closeable = emptyAsyncCloseable();

        private final Publisher<Collection<ServiceDiscovererEvent<ResolvedAddress>>> resolution;
        private final OriginalAddress originalAddress;

        private NoopServiceDiscoverer(final OriginalAddress originalAddress, final ResolvedAddress address) {
            this.originalAddress = requireNonNull(originalAddress);
            resolution = Publisher.<Collection<ServiceDiscovererEvent<ResolvedAddress>>>from(
                    singletonList(new DefaultServiceDiscovererEvent<>(requireNonNull(address), true)))
                    // LoadBalancer will flag a termination of service discoverer Publisher as unexpected.
                    .concat(never());
        }

        @Override
        public Publisher<Collection<ServiceDiscovererEvent<ResolvedAddress>>> discover(
                final OriginalAddress address) {
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

    private static final class StatusAwareServiceDiscoverer<U, R, E extends ServiceDiscovererEvent<R>>
            extends DelegatingServiceDiscoverer<U, R, E> {
        private final SdStatusCompletable status;

        StatusAwareServiceDiscoverer(final ServiceDiscoverer<U, R, E> delegate, final SdStatusCompletable status) {
            super(delegate);
            this.status = status;
        }

        @Override
        public Publisher<Collection<E>> discover(final U u) {
            return delegate().discover(u)
                    .beforeOnError(status::nextError)
                    .beforeOnNext(__ -> status.resetError());
            // We do not complete sdStatus to let LB decide when to retry if SD completes.
        }
    }

    static final class RetryingServiceDiscoverer<U, R, E extends ServiceDiscovererEvent<R>>
            extends DelegatingServiceDiscoverer<U, R, E> {
        private final ServiceDiscoveryRetryStrategy<R, E> retryStrategy;

        RetryingServiceDiscoverer(final ServiceDiscoverer<U, R, E> delegate,
                                  final ServiceDiscoveryRetryStrategy<R, E> retryStrategy) {
            super(delegate);
            this.retryStrategy = requireNonNull(retryStrategy);
        }

        @Override
        public Publisher<Collection<E>> discover(final U u) {
            return retryStrategy.apply(delegate().discover(u));
        }
    }

    private abstract static class DelegatingServiceDiscoverer<U, R, E extends ServiceDiscovererEvent<R>>
            implements ServiceDiscoverer<U, R, E> {
        private final ServiceDiscoverer<U, R, E> delegate;

        DelegatingServiceDiscoverer(final ServiceDiscoverer<U, R, E> delegate) {
            this.delegate = requireNonNull(delegate);
        }

        ServiceDiscoverer<U, R, E> delegate() {
            return delegate;
        }

        @Override
        public Completable onClose() {
            return delegate.onClose();
        }

        @Override
        public Completable closeAsync() {
            return delegate.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return delegate.closeAsyncGracefully();
        }
    }

    private static final class AlpnReqRespFactoryFunc implements
                                                  Function<HttpProtocolVersion, StreamingHttpRequestResponseFactory> {
        private final BufferAllocator allocator;
        @Nullable
        private final HttpHeadersFactory h1HeadersFactory;
        @Nullable
        private final HttpHeadersFactory h2HeadersFactory;
        @Nullable
        private StreamingHttpRequestResponseFactory h1Factory;
        @Nullable
        private StreamingHttpRequestResponseFactory h2Factory;

        AlpnReqRespFactoryFunc(final BufferAllocator allocator, @Nullable final HttpHeadersFactory h1HeadersFactory,
                               @Nullable final HttpHeadersFactory h2HeadersFactory) {
            this.allocator = allocator;
            this.h1HeadersFactory = h1HeadersFactory;
            this.h2HeadersFactory = h2HeadersFactory;
        }

        @Override
        public StreamingHttpRequestResponseFactory apply(final HttpProtocolVersion version) {
            if (version == HTTP_1_1) {
                if (h1Factory == null) { // its OK if we race and re-initialize the instance.
                    h1Factory = new DefaultStreamingHttpRequestResponseFactory(allocator,
                            headersFactory(h1HeadersFactory, HTTP_1_1), HTTP_1_1);
                }
                return h1Factory;
            } else if (version == HTTP_2_0) {
                if (h2Factory == null) { // its OK if we race and re-initialize the instance.
                    h2Factory = new DefaultStreamingHttpRequestResponseFactory(allocator,
                            headersFactory(h2HeadersFactory, HTTP_2_0), HTTP_2_0);
                }
                return h2Factory;
            } else if (version.major() <= 1) {
                // client doesn't generate HTTP_1_0 requests, no need to cache.
                return new DefaultStreamingHttpRequestResponseFactory(allocator,
                        headersFactory(h1HeadersFactory, version), version);
            } else if (version.major() == 2) {
                return new DefaultStreamingHttpRequestResponseFactory(allocator,
                        headersFactory(h2HeadersFactory, version), version);
            } else {
                throw new IllegalArgumentException("unsupported protocol: " + version);
            }
        }

        private static HttpHeadersFactory headersFactory(@Nullable HttpHeadersFactory factory,
                                                         HttpProtocolVersion version) {
            if (factory == null) {
                throw new IllegalStateException("HeadersFactory config not found for selected protocol: " + version);
            }
            return factory;
        }
    }
}
