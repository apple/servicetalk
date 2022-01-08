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
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;

import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.time.Duration;
import java.util.Collection;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.netty.util.NetUtil.toSocketAddressString;
import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.AVAILABLE;
import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoffDeltaJitter;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.netty.AlpnIds.HTTP_2;
import static io.servicetalk.http.netty.GlobalDnsServiceDiscoverer.globalDnsServiceDiscoverer;
import static io.servicetalk.http.netty.GlobalDnsServiceDiscoverer.globalSrvDnsServiceDiscoverer;
import static io.servicetalk.http.netty.StrategyInfluencerAwareConversions.toConditionalClientFilterFactory;
import static io.servicetalk.http.netty.StrategyInfluencerAwareConversions.toConditionalConnectionFilterFactory;
import static java.lang.Integer.parseInt;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;
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
final class DefaultSingleAddressHttpClientBuilder<U, R> implements SingleAddressHttpClientBuilder<U, R> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSingleAddressHttpClientBuilder.class);
    private static final RetryingHttpRequesterFilter DEFAULT_AUTO_RETRIES =
            new RetryingHttpRequesterFilter.Builder().build();

    static final Duration SD_RETRY_STRATEGY_INIT_DURATION = ofSeconds(10);
    static final Duration SD_RETRY_STRATEGY_JITTER = ofSeconds(5);

    @Nullable
    private final U address;
    @Nullable
    private U proxyAddress;
    private final HttpClientConfig config;
    final HttpExecutionContextBuilder executionContextBuilder;
    private final ClientStrategyInfluencerChainBuilder strategyComputation;
    private HttpLoadBalancerFactory<R> loadBalancerFactory;
    private ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer;
    private Function<U, CharSequence> hostToCharSequenceFunction =
            DefaultSingleAddressHttpClientBuilder::toAuthorityForm;
    private boolean addHostHeaderFallbackFilter = true;
    @Nullable
    private BiIntFunction<Throwable, ? extends Completable> serviceDiscovererRetryStrategy;
    @Nullable
    private StreamingHttpConnectionFilterFactory connectionFilterFactory;
    @Nullable
    private ContextAwareStreamingHttpClientFilterFactory clientFilterFactory;

    private ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> connectionFactoryFilter =
            ConnectionFactoryFilter.identity();

    @Nullable
    private RetryingHttpRequesterFilter retryingHttpRequesterFilter;

    DefaultSingleAddressHttpClientBuilder(
            final U address, final ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer) {
        this.address = requireNonNull(address);
        config = new HttpClientConfig();
        executionContextBuilder = new HttpExecutionContextBuilder();
        strategyComputation = new ClientStrategyInfluencerChainBuilder();
        this.loadBalancerFactory = DefaultHttpLoadBalancerFactory.Builder.<R>fromDefaults().build();
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
    }

    DefaultSingleAddressHttpClientBuilder(
            final ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer) {
        address = null; // Unknown address - template builder pending override via: copy(address)
        config = new HttpClientConfig();
        executionContextBuilder = new HttpExecutionContextBuilder();
        strategyComputation = new ClientStrategyInfluencerChainBuilder();
        this.loadBalancerFactory = DefaultHttpLoadBalancerFactory.Builder.<R>fromDefaults().build();
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
    }

    private DefaultSingleAddressHttpClientBuilder(@Nullable final U address,
                                                  final DefaultSingleAddressHttpClientBuilder<U, R> from) {
        this.address = address;
        proxyAddress = from.proxyAddress;
        config = new HttpClientConfig(from.config);
        executionContextBuilder = new HttpExecutionContextBuilder(from.executionContextBuilder);
        strategyComputation = from.strategyComputation.copy();
        loadBalancerFactory = from.loadBalancerFactory;
        serviceDiscoverer = from.serviceDiscoverer;
        serviceDiscovererRetryStrategy = from.serviceDiscovererRetryStrategy;
        clientFilterFactory = from.clientFilterFactory;
        connectionFilterFactory = from.connectionFilterFactory;
        hostToCharSequenceFunction = from.hostToCharSequenceFunction;
        addHostHeaderFallbackFilter = from.addHostHeaderFallbackFilter;
        connectionFactoryFilter = from.connectionFactoryFilter;
        retryingHttpRequesterFilter = from.retryingHttpRequesterFilter;
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

    static <U, R extends SocketAddress> DefaultSingleAddressHttpClientBuilder<U, R> forResolvedAddress(
            final U u, final Function<U, R> toResolvedAddressMapper) {
        ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> sd = new NoopServiceDiscoverer<>(toResolvedAddressMapper);
        return new DefaultSingleAddressHttpClientBuilder<>(u, sd);
    }

    static DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forUnknownHostAndPort() {
        return new DefaultSingleAddressHttpClientBuilder<>(globalDnsServiceDiscoverer());
    }

    static final class HttpClientBuildContext<U, R> {
        final DefaultSingleAddressHttpClientBuilder<U, R> builder;
        private final ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> sd;
        private final SdStatusCompletable sdStatus;

        @Nullable
        private final BiIntFunction<Throwable, ? extends Completable> serviceDiscovererRetryStrategy;
        @Nullable
        private final U proxyAddress;

        HttpClientBuildContext(
                final DefaultSingleAddressHttpClientBuilder<U, R> builder,
                final ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> sd,
                @Nullable final BiIntFunction<Throwable, ? extends Completable> serviceDiscovererRetryStrategy,
                @Nullable final U proxyAddress) {
            this.builder = builder;
            this.serviceDiscovererRetryStrategy = serviceDiscovererRetryStrategy;
            this.proxyAddress = proxyAddress;
            this.sd = sd;
            this.sdStatus = new SdStatusCompletable();
        }

        U address() {
            assert builder.address != null : "Attempted to buildStreaming with an unknown address";
            return proxyAddress != null ? proxyAddress :
                    // the builder can be modified post-context creation, therefore proxy can be set
                    (builder.proxyAddress != null ? builder.proxyAddress : builder.address);
        }

        HttpClientConfig httpConfig() {
            return builder.config;
        }

        StreamingHttpClient build() {
            return buildStreaming(this);
        }

        ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer(
                HttpExecutionContext executionContext) {
            BiIntFunction<Throwable, ? extends Completable> sdRetryStrategy = serviceDiscovererRetryStrategy;
            if (sdRetryStrategy == null) {
                sdRetryStrategy = retryWithConstantBackoffDeltaJitter(__ -> true, SD_RETRY_STRATEGY_INIT_DURATION,
                        SD_RETRY_STRATEGY_JITTER, executionContext.executor());
            }
            return new RetryingServiceDiscoverer<>(new StatusAwareServiceDiscoverer<>(sd, sdStatus), sdRetryStrategy);
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
            final Publisher<? extends Collection<? extends ServiceDiscovererEvent<R>>> sdEvents =
                    ctx.serviceDiscoverer(executionContext).discover(ctx.address());

            ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> connectionFactoryFilter =
                    ctx.builder.connectionFactoryFilter;
            ExecutionStrategy connectionFactoryStrategy =
                    ctx.builder.strategyComputation.buildForConnectionFactory();

            final SslContext sslContext = roConfig.tcpConfig().sslContext();
            if (roConfig.hasProxy() && sslContext != null) {
                assert roConfig.connectAddress() != null;
                ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> proxy =
                        new ProxyConnectConnectionFactoryFilter<>(roConfig.connectAddress());
                connectionFactoryFilter = proxy.append(connectionFactoryFilter);
                connectionFactoryStrategy = connectionFactoryStrategy.merge(proxy.requiredOffloads());
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
                        connectionFactoryStrategy, connectionFactoryFilter,
                        ctx.builder.loadBalancerFactory::toLoadBalancedConnection);
            } else if (roConfig.tcpConfig().preferredAlpnProtocol() != null) {
                H1ProtocolConfig h1Config = roConfig.h1Config();
                H2ProtocolConfig h2Config = roConfig.h2Config();
                connectionFactory = new AlpnLBHttpConnectionFactory<>(roConfig, executionContext,
                        ctx.builder.connectionFilterFactory, new AlpnReqRespFactoryFunc(
                                executionContext.bufferAllocator(),
                                h1Config == null ? null : h1Config.headersFactory(),
                                h2Config == null ? null : h2Config.headersFactory()),
                        connectionFactoryStrategy, connectionFactoryFilter,
                        ctx.builder.loadBalancerFactory::toLoadBalancedConnection);
            } else {
                H1ProtocolConfig h1Config = roConfig.h1Config();
                assert h1Config != null;
                connectionFactory = new PipelinedLBHttpConnectionFactory<>(roConfig, executionContext,
                        ctx.builder.connectionFilterFactory, reqRespFactory,
                        connectionFactoryStrategy, connectionFactoryFilter,
                        ctx.builder.loadBalancerFactory::toLoadBalancedConnection);
            }

            final LoadBalancer<LoadBalancedStreamingHttpConnection> lb =
                    closeOnException.prepend(ctx.builder.loadBalancerFactory.newLoadBalancer(
                            targetAddress(ctx),
                            sdEvents,
                            connectionFactory));

            ContextAwareStreamingHttpClientFilterFactory currClientFilterFactory = ctx.builder.clientFilterFactory;
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
            if (ctx.builder.retryingHttpRequesterFilter == null) {
                ctx.builder.retryingHttpRequesterFilter = DEFAULT_AUTO_RETRIES;
                currClientFilterFactory = appendFilter(currClientFilterFactory,
                        ctx.builder.retryingHttpRequesterFilter);
            }
            HttpExecutionStrategy computedStrategy = ctx.builder.strategyComputation.buildForClient(executionStrategy);
            LOGGER.debug("Client for {} created with base strategy {} → computed strategy {}",
                    targetAddress(ctx), executionStrategy, computedStrategy);
            return new FilterableClientToClient(currClientFilterFactory != null ?
                    currClientFilterFactory.create(lbClient, lb.eventStream(), ctx.sdStatus) :
                        lbClient, computedStrategy);
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

    private static <U, R> String targetAddress(final HttpClientBuildContext<U, R> ctx) {
        return ctx.proxyAddress == null ?
                ctx.builder.address.toString() : ctx.builder.address + " (via " + ctx.proxyAddress + ")";
    }

    private static ContextAwareStreamingHttpClientFilterFactory appendFilter(
            @Nullable final ContextAwareStreamingHttpClientFilterFactory currClientFilterFactory,
            final StreamingHttpClientFilterFactory appendClientFilterFactory) {
        if (appendClientFilterFactory instanceof RetryingHttpRequesterFilter) {
            if (currClientFilterFactory == null) {
                return (client, lbEventStream, sdStatus) -> {
                    final RetryingHttpRequesterFilter.ContextAwareRetryingHttpClientFilter filter =
                            (RetryingHttpRequesterFilter.ContextAwareRetryingHttpClientFilter)
                                    appendClientFilterFactory.create(client);
                    filter.inject(lbEventStream, sdStatus);
                    return filter;
                };
            } else {
                return (client, lbEventStream, sdStatus) -> {
                    final RetryingHttpRequesterFilter.ContextAwareRetryingHttpClientFilter filter =
                            (RetryingHttpRequesterFilter.ContextAwareRetryingHttpClientFilter)
                                    appendClientFilterFactory.create(client);
                    filter.inject(lbEventStream, sdStatus);
                    return currClientFilterFactory.create(filter, lbEventStream, sdStatus);
                };
            }
        } else if (appendClientFilterFactory instanceof ContextAwareStreamingHttpClientFilterFactory) {
            if (currClientFilterFactory == null) {
                return ((ContextAwareStreamingHttpClientFilterFactory) appendClientFilterFactory);
            } else {
                return (client, lbEventStream, sdError) ->
                        currClientFilterFactory.create(
                                ((ContextAwareStreamingHttpClientFilterFactory) appendClientFilterFactory)
                                        .create(client, lbEventStream, sdError), lbEventStream, sdError);
            }
        } else {
            if (currClientFilterFactory == null) {
                return (client, lbEventStream, sdError) -> appendClientFilterFactory.create(client);
            } else {
                return (client, lbEventStream, sdError) ->
                        currClientFilterFactory.create(appendClientFilterFactory.create(client),
                                lbEventStream, sdError);
            }
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
    public SingleAddressHttpClientBuilder<U, R> proxyAddress(final U proxyAddress) {
        this.proxyAddress = requireNonNull(proxyAddress);
        config.connectAddress(hostToCharSequenceFunction.apply(address));
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> ioExecutor(final IoExecutor ioExecutor) {
        executionContextBuilder.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> executor(final Executor executor) {
        executionContextBuilder.executor(executor);
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
        strategyComputation.add(factory);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> appendConnectionFilter(
            final Predicate<StreamingHttpRequest> predicate, final StreamingHttpConnectionFilterFactory factory) {
        return appendConnectionFilter(toConditionalConnectionFilterFactory(predicate, factory));
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
        connectionFactoryFilter = connectionFactoryFilter.append(requireNonNull(factory));
        strategyComputation.add(factory);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> hostHeaderFallback(final boolean enable) {
        addHostHeaderFallbackFilter = enable;
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> allowDropResponseTrailers(final boolean allowDrop) {
        config.protocolConfigs().allowDropTrailersReadFromTransport(allowDrop);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> appendClientFilter(final Predicate<StreamingHttpRequest> predicate,
                                                                   final StreamingHttpClientFilterFactory factory) {
        if (factory instanceof RetryingHttpRequesterFilter) {
            if (retryingHttpRequesterFilter != null) {
                throw new IllegalStateException("Retrying HTTP requester filter was already found in " +
                        "the filter chain, only a single instance of that is allowed.");
            }
            retryingHttpRequesterFilter = (RetryingHttpRequesterFilter) factory;
        }
        return appendClientFilter(toConditionalClientFilterFactory(predicate, factory));
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
        if (factory instanceof RetryingHttpRequesterFilter) {
            if (retryingHttpRequesterFilter != null) {
                throw new IllegalStateException("Retrying HTTP requester filter was already found in " +
                        "the filter chain, only a single instance of that is allowed.");
            }
            retryingHttpRequesterFilter = (RetryingHttpRequesterFilter) factory;
        }
        clientFilterFactory = appendFilter(clientFilterFactory, factory);
        strategyComputation.add(factory);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> serviceDiscoverer(
            final ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer) {
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> retryServiceDiscoveryErrors(
            final BiIntFunction<Throwable, ? extends Completable> retryStrategy) {
        this.serviceDiscovererRetryStrategy = requireNonNull(retryStrategy);
        return this;
    }

    @Override
    public DefaultSingleAddressHttpClientBuilder<U, R> loadBalancerFactory(
            final HttpLoadBalancerFactory<R> loadBalancerFactory) {
        this.loadBalancerFactory = requireNonNull(loadBalancerFactory);
        strategyComputation.add(loadBalancerFactory);
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

    /**
     * returns the required strategy for the added client filters and factories.
     *
     * @param strategy the user execution strategy.
     * @return the required strategy for the added client filters and factories.
     */
    HttpExecutionStrategy computeChainStrategy(HttpExecutionStrategy strategy) {
        return strategyComputation.buildForClient(strategy);
    }

    private static <U> CharSequence toAuthorityForm(final U address) {
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

    private static final class NoopServiceDiscoverer<UnresolvedAddress, ResolvedAddress>
            implements ServiceDiscoverer<UnresolvedAddress, ResolvedAddress,
            ServiceDiscovererEvent<ResolvedAddress>> {
        private final ListenableAsyncCloseable closeable = emptyAsyncCloseable();

        private final Function<UnresolvedAddress, ResolvedAddress> toResolvedAddressMapper;

        private NoopServiceDiscoverer(final Function<UnresolvedAddress, ResolvedAddress> toResolvedAddressMapper) {
            this.toResolvedAddressMapper = requireNonNull(toResolvedAddressMapper);
        }

        @Override
        public Publisher<Collection<ServiceDiscovererEvent<ResolvedAddress>>> discover(
                final UnresolvedAddress address) {
            return Publisher.<Collection<ServiceDiscovererEvent<ResolvedAddress>>>from(
                    singletonList(new DefaultServiceDiscovererEvent<>(
                            requireNonNull(toResolvedAddressMapper.apply(address)), AVAILABLE)))
                    // LoadBalancer will flag a termination of service discoverer Publisher as unexpected.
                    .concat(never());
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
        private final BiIntFunction<Throwable, ? extends Completable> retryStrategy;

        RetryingServiceDiscoverer(final ServiceDiscoverer<U, R, E> delegate,
                                  final BiIntFunction<Throwable, ? extends Completable> retryStrategy) {
            super(delegate);
            this.retryStrategy = requireNonNull(retryStrategy);
        }

        @Override
        public Publisher<Collection<E>> discover(final U u) {
            return delegate().discover(u).retryWhen(retryStrategy);
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
                if (h1Factory == null) { // it is OK if we race and re-initialize the instance.
                    h1Factory = new DefaultStreamingHttpRequestResponseFactory(allocator,
                            headersFactory(h1HeadersFactory, HTTP_1_1), HTTP_1_1);
                }
                return h1Factory;
            } else if (version == HTTP_2_0) {
                if (h2Factory == null) { // it is OK if we race and re-initialize the instance.
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

    @FunctionalInterface
    interface ContextAwareStreamingHttpClientFilterFactory extends StreamingHttpClientFilterFactory {
        StreamingHttpClientFilter create(FilterableStreamingHttpClient client,
                                         @Nullable Publisher<Object> lbEventStream,
                                         @Nullable Completable sdStatus);

        @Override
        default StreamingHttpClientFilter create(FilterableStreamingHttpClient client) {
            return create(client, null, null);
        }
    }
}
