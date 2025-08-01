/*
 * Copyright © 2018-2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpExceptionMapperServiceFilter;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.http.utils.HttpRequestAutoDrainingServiceFilter;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ConnectExecutionStrategy;
import io.servicetalk.transport.api.ConnectionAcceptorFactory;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.EarlyConnectionAcceptor;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.ExecutionStrategyInfluencer;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.LateConnectionAcceptor;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.TransportConfig;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.InfluencerConnectionAcceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.net.SocketOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.defer;
import static io.servicetalk.http.api.HttpApiConversions.toStreamingHttpService;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.netty.StrategyInfluencerAwareConversions.toConditionalServiceFilterFactory;
import static io.servicetalk.transport.api.ConnectionAcceptor.ACCEPT_ALL;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

// Non-final to give more flexibility for testing, production code must use HttpServers static factories.
class DefaultHttpServerBuilder implements HttpServerBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHttpServerBuilder.class);

    private static final HttpExecutionStrategy REQRESP_OFFLOADS = HttpExecutionStrategies.customStrategyBuilder()
            .offloadReceiveMetadata().offloadReceiveData().offloadSend().build();

    @Nullable
    private ConnectionAcceptorFactory connectionAcceptorFactory;
    private final List<StreamingHttpServiceFilterFactory> noOffloadServiceFilters = new ArrayList<>();
    private final List<StreamingHttpServiceFilterFactory> serviceFilters = new ArrayList<>();
    private final List<EarlyConnectionAcceptor> earlyConnectionAcceptors = new ArrayList<>();
    private final List<LateConnectionAcceptor> lateConnectionAcceptors = new ArrayList<>();
    private HttpExecutionStrategy strategy = defaultStrategy();
    private boolean drainRequestPayloadBody = true;
    private final HttpServerConfig config = new HttpServerConfig();
    private final HttpExecutionContextBuilder executionContextBuilder = new HttpExecutionContextBuilder();
    private final SocketAddress address;

    // Do not use this ctor directly, HttpServers is the entry point for creating a new builder.
    DefaultHttpServerBuilder(SocketAddress address) {
        this.address = address;
    }

    private static StreamingHttpService buildService(Stream<StreamingHttpServiceFilterFactory> filters,
                                                     StreamingHttpService service) {
        return filters
                .reduce((prev, filter) -> svc -> prev.create(filter.create(svc)))
                .map(factory -> (StreamingHttpService) factory.create(service))
                .orElse(service);
    }

    private static HttpExecutionStrategy computeRequiredStrategy(List<StreamingHttpServiceFilterFactory> filters,
                                                                 HttpExecutionStrategy serviceStrategy) {
        HttpExecutionStrategy current = serviceStrategy;
        for (StreamingHttpServiceFilterFactory filter : filters) {
            HttpExecutionStrategy next = current.merge(filter.requiredOffloads());
            if (current != next) {
                LOGGER.debug("{} '{}' changes execution strategy from '{}' to '{}'",
                        StreamingHttpServiceFilterFactory.class, filter, current, next);
                current = next;
            }
        }
        return current;
    }

    private static <T> T checkNonOffloading(String what, ExecutionStrategy fallbackValue, T obj) {
        ExecutionStrategy requires = obj instanceof ExecutionStrategyInfluencer ?
                ((ExecutionStrategyInfluencer<?>) obj).requiredOffloads() :
                fallbackValue;
        if (requires.hasOffloads()) {
            throw new IllegalArgumentException(what + " '" + obj.getClass().getName() + "' requires offloading: " +
                    requires + ". Therefore, it cannot be used with 'appendNonOffloadingServiceFilter(...)', " +
                    "use 'appendServiceFilter(...)' instead.");
        }
        return obj;
    }

    @Override
    public final HttpServerBuilder drainRequestPayloadBody(final boolean enable) {
        this.drainRequestPayloadBody = enable;
        return this;
    }

    @Override
    public final HttpServerBuilder appendConnectionAcceptorFilter(final ConnectionAcceptorFactory factory) {
        if (connectionAcceptorFactory == null) {
            connectionAcceptorFactory = factory;
        } else {
            connectionAcceptorFactory = connectionAcceptorFactory.append(factory);
        }
        return this;
    }

    @Override
    public final HttpServerBuilder appendEarlyConnectionAcceptor(final EarlyConnectionAcceptor acceptor) {
        earlyConnectionAcceptors.add(requireNonNull(acceptor));
        return this;
    }

    @Override
    public final HttpServerBuilder appendLateConnectionAcceptor(final LateConnectionAcceptor acceptor) {
        lateConnectionAcceptors.add(requireNonNull(acceptor));
        return this;
    }

    @Override
    public final HttpServerBuilder appendNonOffloadingServiceFilter(final StreamingHttpServiceFilterFactory factory) {
        appendNonOffloadingServiceFilter(noOffloadServiceFilters, factory);
        return this;
    }

    private static void appendNonOffloadingServiceFilter(
            final List<StreamingHttpServiceFilterFactory> noOffloadServiceFilters,
            final StreamingHttpServiceFilterFactory factory) {
        noOffloadServiceFilters.add(checkNonOffloading("Filter", defaultStrategy(), factory));
    }

    @Override
    public final HttpServerBuilder appendNonOffloadingServiceFilter(final Predicate<StreamingHttpRequest> predicate,
                                                                    final StreamingHttpServiceFilterFactory factory) {
        checkNonOffloading("Predicate", offloadNone(), predicate);
        checkNonOffloading("Filter", defaultStrategy(), factory);
        noOffloadServiceFilters.add(toConditionalServiceFilterFactory(predicate, factory));
        return this;
    }

    @Override
    public final HttpServerBuilder appendServiceFilter(final StreamingHttpServiceFilterFactory factory) {
        requireNonNull(factory);
        serviceFilters.add(factory);
        return this;
    }

    @Override
    public final HttpServerBuilder appendServiceFilter(final Predicate<StreamingHttpRequest> predicate,
                                                       final StreamingHttpServiceFilterFactory factory) {
        appendServiceFilter(toConditionalServiceFilterFactory(predicate, factory));
        return this;
    }

    @Override
    public final HttpServerBuilder protocols(final HttpProtocolConfig... protocols) {
        config.httpConfig().protocols(protocols);
        return this;
    }

    @Override
    public final HttpServerBuilder sslConfig(final ServerSslConfig config) {
        this.config.tcpConfig().sslConfig(requireNonNull(config, "config"));
        return this;
    }

    @Override
    public final HttpServerBuilder sslConfig(final ServerSslConfig defaultConfig,
                                             final Map<String, ServerSslConfig> sniMap) {
        this.config.tcpConfig().sslConfig(requireNonNull(defaultConfig, "defaultConfig"),
                requireNonNull(sniMap, "sniMap"));
        return this;
    }

    @Override
    public final HttpServerBuilder sslConfig(final ServerSslConfig defaultConfig,
                                             final Map<String, ServerSslConfig> sniMap,
                                             final int maxClientHelloLength,
                                             final Duration clientHelloTimeout) {
        this.config.tcpConfig().sslConfig(requireNonNull(defaultConfig, "defaultConfig"),
                requireNonNull(sniMap, "sniMap"), maxClientHelloLength,
                requireNonNull(clientHelloTimeout, "clientHelloTimeout"));
        return this;
    }

    @Override
    public final HttpServerBuilder sslConfig(final ServerSslConfig config, final boolean acceptInsecureConnections) {
        this.config.tcpConfig().sslConfig(config, acceptInsecureConnections);
        return this;
    }

    @Override
    public final HttpServerBuilder transportConfig(final TransportConfig transportConfig) {
        config.tcpConfig().transportConfig(transportConfig);
        return this;
    }

    @Override
    public final <T> HttpServerBuilder socketOption(final SocketOption<T> option, final T value) {
        config.tcpConfig().socketOption(option, value);
        return this;
    }

    @Override
    public final <T> HttpServerBuilder listenSocketOption(final SocketOption<T> option, final T value) {
        config.tcpConfig().listenSocketOption(option, value);
        return this;
    }

    @Override
    public final HttpServerBuilder enableWireLogging(final String loggerName, final LogLevel logLevel,
                                               final BooleanSupplier logUserData) {
        config.tcpConfig().enableWireLogging(loggerName, logLevel, logUserData);
        return this;
    }

    @Override
    public final HttpServerBuilder transportObserver(final TransportObserver transportObserver) {
        config.tcpConfig().transportObserver(transportObserver);
        return this;
    }

    @Override
    public final HttpServerBuilder lifecycleObserver(final HttpLifecycleObserver lifecycleObserver) {
        config.lifecycleObserver(lifecycleObserver);
        return this;
    }

    @Override
    public final HttpServerBuilder allowDropRequestTrailers(final boolean allowDrop) {
        config.httpConfig().allowDropTrailersReadFromTransport(allowDrop);
        return this;
    }

    @Override
    public final HttpServerBuilder executor(final Executor executor) {
        executionContextBuilder.executor(executor);
        return this;
    }

    @Override
    public final HttpServerBuilder executionStrategy(HttpExecutionStrategy strategy) {
        this.strategy = requireNonNull(strategy);
        return this;
    }

    @Override
    public final HttpServerBuilder ioExecutor(final IoExecutor ioExecutor) {
        executionContextBuilder.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public final HttpServerBuilder bufferAllocator(final BufferAllocator allocator) {
        executionContextBuilder.bufferAllocator(allocator);
        return this;
    }

    @Override
    public final Single<HttpServerContext> listen(final HttpService service) {
        final List<StreamingHttpServiceFilterFactory> serviceFilters = initServiceFilters();
        final StreamingHttpService streamingService = toStreamingHttpService(computeServiceStrategy(
                HttpService.class, service, this.strategy, serviceFilters), service);
        return listenForService(streamingService, serviceFilters, streamingService.requiredOffloads());
    }

    @Override
    public final Single<HttpServerContext> listenStreaming(final StreamingHttpService service) {
        final List<StreamingHttpServiceFilterFactory> serviceFilters = initServiceFilters();
        return listenForService(service, serviceFilters, computeServiceStrategy(
                StreamingHttpService.class, service, this.strategy, serviceFilters));
    }

    @Override
    public final Single<HttpServerContext> listenBlocking(final BlockingHttpService service) {
        final List<StreamingHttpServiceFilterFactory> serviceFilters = initServiceFilters();
        final StreamingHttpService streamingService = toStreamingHttpService(computeServiceStrategy(
                BlockingHttpService.class, service, this.strategy, serviceFilters), service);
        return listenForService(streamingService, serviceFilters, streamingService.requiredOffloads());
    }

    @Override
    public final Single<HttpServerContext> listenBlockingStreaming(final BlockingStreamingHttpService service) {
        final List<StreamingHttpServiceFilterFactory> serviceFilters = initServiceFilters();
        final StreamingHttpService streamingService = toStreamingHttpService(computeServiceStrategy(
                BlockingStreamingHttpService.class, service, this.strategy, serviceFilters), service);
        return listenForService(streamingService, serviceFilters, streamingService.requiredOffloads());
    }

    /**
     * In internal hook to get access to entire filters chain and modify that for testing if necessary.
     *
     * @param filters the original stream of filters
     * @return modified stream of filters
     */
    Stream<StreamingHttpServiceFilterFactory> alterFilters(final Stream<StreamingHttpServiceFilterFactory> filters) {
        return filters;
    }

    private List<StreamingHttpServiceFilterFactory> initServiceFilters() {
        // Take snapshot of currently appended filters:
        final List<StreamingHttpServiceFilterFactory> filters = new ArrayList<>(this.serviceFilters);

        // Append internal filters:
        // 1. The watchdog sits at the very beginning of the response flow (the end of the filter pipeline) so that any
        // payload coming from the service is ensured to be tracked before subsequent filters get a chance to drop it
        // without being accounted for.
        filters.add(HttpMessageDiscardWatchdogServiceFilter.INSTANCE);

        return unmodifiableList(filters);
    }

    private List<StreamingHttpServiceFilterFactory> initNonOffloadsServiceFilters(
            @Nullable final HttpLifecycleObserver lifecycleObserver) {
        final List<StreamingHttpServiceFilterFactory> filters = new ArrayList<>();
        // Append internal filters:
        if (drainRequestPayloadBody) {
            appendNonOffloadingServiceFilter(filters, HttpRequestAutoDrainingServiceFilter.INSTANCE);
        }
        if (lifecycleObserver != null) {
            appendNonOffloadingServiceFilter(filters, new HttpLifecycleObserverServiceFilter(lifecycleObserver));
        }
        appendNonOffloadingServiceFilter(filters, KeepAliveServiceFilter.INSTANCE);
        appendNonOffloadingServiceFilter(filters, HttpExceptionMapperServiceFilter.INSTANCE);
        // This filter is placed at the end of the response lifecycle (so beginning of the user-defined filters) to
        // ensure that any discarded payloads coming from the service are cleaned up.
        appendNonOffloadingServiceFilter(filters, HttpMessageDiscardWatchdogServiceFilter.CLEANER);

        // Take snapshot of currently appended filters:
        filters.addAll(this.noOffloadServiceFilters);
        return unmodifiableList(filters);
    }

    private HttpExecutionContext buildExecutionContext(final HttpExecutionStrategy strategy) {
        executionContextBuilder.executionStrategy(strategy);
        return executionContextBuilder.build();
    }

    /**
     * Starts this server and returns the {@link HttpServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (e.g. TCP) supports it this should result in a socket bind/listen on {@code address}.
     * <p>/p>
     * The execution path for a request will be offloaded from the IO thread as required to ensure safety. The
     * <dl>
     *     <dt>read side</dt>
     *     <dd>IO thread → request → non-offload filters → offload filters → raw service</dd>
     *     <dt>subscribe/request side</dt>
     *     <dd>IO thread → subscribe/request/cancel → non-offload filters → offload filters → raw service</dd>
     * </dl>
     *
     * @param service {@link StreamingHttpService} to use for the server.
     * @param computedStrategy the computed {@link HttpExecutionStrategy} to use for the service.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    private Single<HttpServerContext> listenForService(final StreamingHttpService service,
                                                       final List<StreamingHttpServiceFilterFactory> serviceFilters,
                                                       final HttpExecutionStrategy computedStrategy) {
        final InfluencerConnectionAcceptor connectionAcceptor = connectionAcceptorFactory == null ? null :
                InfluencerConnectionAcceptor.withStrategy(connectionAcceptorFactory.create(ACCEPT_ALL),
                        connectionAcceptorFactory.requiredOffloads());

        final EarlyConnectionAcceptor earlyConnectionAcceptor = buildEarlyConnectionAcceptor(earlyConnectionAcceptors);
        final LateConnectionAcceptor lateConnectionAcceptor = buildLateConnectionAcceptor(lateConnectionAcceptors);

        final ReadOnlyHttpServerConfig roConfig = config.asReadOnly();
        final List<StreamingHttpServiceFilterFactory> noOffloadServiceFilters =
                initNonOffloadsServiceFilters(config.lifecycleObserver());

        final HttpExecutionContext executionContext;
        Stream<StreamingHttpServiceFilterFactory> filters = noOffloadServiceFilters.stream();
        if (computedStrategy.isRequestResponseOffloaded()) {
            executionContext = buildExecutionContext(REQRESP_OFFLOADS.missing(computedStrategy));
            final BooleanSupplier shouldOffload = executionContext.ioExecutor().shouldOffloadSupplier();
            // We are going to have to offload, even if just to the raw service
            filters = Stream.concat(filters, Stream.of(new OffloadingFilter(computedStrategy, shouldOffload)));
        } else {
            executionContext = buildExecutionContext(computedStrategy);
        }
        // All the filters can be appended.
        filters = Stream.concat(filters, serviceFilters.stream());
        final StreamingHttpService filteredService = buildService(alterFilters(filters), service);

        final HttpExecutionStrategy builderStrategy = this.strategy;
        return doBind(roConfig, executionContext, connectionAcceptor, filteredService, earlyConnectionAcceptor,
                lateConnectionAcceptor)
                .afterOnSuccess(serverContext -> {
                    if (builderStrategy != defaultStrategy() &&
                            builderStrategy.missing(computedStrategy) != offloadNone()) {
                        LOGGER.info("Server for address {} created with the builder strategy {} but resulting " +
                                        "computed strategy is {}. One of the filters or a final service enforce " +
                                        "additional offloading. To find out what filter or service is " +
                                        "it, enable debug level logging for {}.", serverContext.listenAddress(),
                                builderStrategy, computedStrategy, DefaultHttpServerBuilder.class);
                    } else if (builderStrategy == computedStrategy) {
                        LOGGER.debug("Server for address {} created with the execution strategy {}.",
                                serverContext.listenAddress(), computedStrategy);
                    } else {
                        LOGGER.debug("Server for address {} created with the builder strategy {}, " +
                                        "resulting computed strategy is {}.",
                                serverContext.listenAddress(), builderStrategy, computedStrategy);
                    }
                });
    }

    private Single<HttpServerContext> doBind(final ReadOnlyHttpServerConfig roConfig,
                                             final HttpExecutionContext executionContext,
                                             @Nullable final InfluencerConnectionAcceptor connectionAcceptor,
                                             final StreamingHttpService filteredService,
                                             @Nullable final EarlyConnectionAcceptor earlyConnectionAcceptor,
                                             @Nullable final LateConnectionAcceptor lateConnectionAcceptor) {

        if (roConfig.tcpConfig().sslConfig() != null && roConfig.tcpConfig().acceptInsecureConnections()) {
            HttpServerConfig configWithoutSsl = new HttpServerConfig(config);
            configWithoutSsl.tcpConfig().sslConfig(null);
            if (roConfig.h1Config() != null && roConfig.h2Config() != null) {
                // For non-SSL, if both H1 and H2 are configured at the same time we force-fallback to H1
                configWithoutSsl.httpConfig().protocols(roConfig.h1Config());
            }
            ReadOnlyHttpServerConfig roConfigWithoutSsl = configWithoutSsl.asReadOnly();
            return OptionalSslNegotiator.bind(executionContext, roConfig, roConfigWithoutSsl, address,
                    connectionAcceptor, filteredService, earlyConnectionAcceptor,
                    lateConnectionAcceptor);
        } else if (roConfig.tcpConfig().isAlpnConfigured()) {
            return DeferredServerChannelBinder.bind(executionContext, roConfig, address, connectionAcceptor,
                    filteredService, false, earlyConnectionAcceptor, lateConnectionAcceptor);
        } else if (roConfig.tcpConfig().sniMapping() != null) {
            return DeferredServerChannelBinder.bind(executionContext, roConfig, address, connectionAcceptor,
                    filteredService, true, earlyConnectionAcceptor, lateConnectionAcceptor);
        } else if (roConfig.isH2PriorKnowledge()) {
            return H2ServerParentConnectionContext.bind(executionContext, roConfig, address, connectionAcceptor,
                    filteredService, earlyConnectionAcceptor, lateConnectionAcceptor);
        }
        return NettyHttpServer.bind(executionContext, roConfig, address, connectionAcceptor,
                filteredService, earlyConnectionAcceptor, lateConnectionAcceptor);
    }

    private static <T extends HttpExecutionStrategyInfluencer> HttpExecutionStrategy computeServiceStrategy(
            final Class<T> clazz, final T service, final HttpExecutionStrategy builderStrategy,
            final List<StreamingHttpServiceFilterFactory> serviceFilters) {
        final HttpExecutionStrategy serviceStrategy = service.requiredOffloads();
        LOGGER.debug("{} '{}' requires {} strategy.", clazz.getSimpleName(), service, serviceStrategy);
        final HttpExecutionStrategy computedStrategy = computeRequiredStrategy(serviceFilters, serviceStrategy);
        return defaultStrategy() == builderStrategy ? computedStrategy :
                builderStrategy.hasOffloads() ? builderStrategy.merge(computedStrategy) : builderStrategy;
    }

    /**
     * Combines all early acceptors into one by concatenating the callbacks and merging their execution strategies.
     *
     * @param acceptors the acceptors to combine into one.
     * @return the combined acceptor with merged execution strategies.
     */
    @Nullable
    private static EarlyConnectionAcceptor buildEarlyConnectionAcceptor(final List<EarlyConnectionAcceptor> acceptors) {
        return acceptors
                .stream()
                .reduce((prev, acceptor) -> new EarlyConnectionAcceptor() {
                    @Override
                    @SuppressWarnings("deprecation")
                    public Completable accept(final ConnectionInfo info) {
                        // Defer invocation of the next acceptor, but share context between them.
                        return prev.accept(info).concat(defer(() -> acceptor.accept(info)).shareContextOnSubscribe())
                                .shareContextOnSubscribe(); // Share context with the rest of the chain.
                    }

                    @Override
                    public Completable accept(final ConnectionContext ctx) {
                        // Defer invocation of the next acceptor, but share context between them.
                        return prev.accept(ctx).concat(defer(() -> acceptor.accept(ctx)).shareContextOnSubscribe())
                                .shareContextOnSubscribe(); // Share context with the rest of the chain.
                    }

                    @Override
                    public ConnectExecutionStrategy requiredOffloads() {
                        return prev.requiredOffloads().merge(acceptor.requiredOffloads());
                    }
                })
                .orElse(null);
    }

    /**
     * Combines all late acceptors into one by concatenating their callbacks and merging their execution strategies.
     *
     * @param acceptors the acceptors to combine into one.
     * @return the combined acceptor with merged execution strategies.
     */
    @Nullable
    private static LateConnectionAcceptor buildLateConnectionAcceptor(final List<LateConnectionAcceptor> acceptors) {
        return acceptors
                .stream()
                .reduce((prev, acceptor) -> new LateConnectionAcceptor() {
                    @Override
                    @SuppressWarnings("deprecation")
                    public Completable accept(final ConnectionInfo info) {
                        // Defer invocation of the next acceptor, but share context between them.
                        return prev.accept(info).concat(defer(() -> acceptor.accept(info)).shareContextOnSubscribe())
                                .shareContextOnSubscribe(); // Share context with the rest of the chain.
                    }

                    @Override
                    public Completable accept(final ConnectionContext ctx) {
                        // Defer invocation of the next acceptor, but share context between them.
                        return prev.accept(ctx).concat(defer(() -> acceptor.accept(ctx)).shareContextOnSubscribe())
                                .shareContextOnSubscribe(); // Share context with the rest of the chain.
                    }

                    @Override
                    public ConnectExecutionStrategy requiredOffloads() {
                        return prev.requiredOffloads().merge(acceptor.requiredOffloads());
                    }
                })
                .orElse(null);
    }

    /**
     * Internal filter that correctly sets {@link HttpHeaderNames#CONNECTION} header value based on the requested
     * keep-alive policy.
     */
    static final class KeepAliveServiceFilter implements StreamingHttpServiceFilterFactory {

        static final StreamingHttpServiceFilterFactory INSTANCE = new KeepAliveServiceFilter();

        private KeepAliveServiceFilter() {
            // Singleton
        }

        @Override
        public StreamingHttpServiceFilter create(final StreamingHttpService service) {
            return new StreamingHttpServiceFilter(service) {
                @Override
                public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                            final StreamingHttpRequest request,
                                                            final StreamingHttpResponseFactory responseFactory) {
                    final HttpKeepAlive keepAlive = HttpKeepAlive.responseKeepAlive(request);
                    // Don't expect any exceptions from delegate because it's already wrapped with
                    // ExceptionMapperServiceFilterFactory
                    return delegate().handle(ctx, request, responseFactory).map(response -> {
                        keepAlive.addConnectionHeaderIfNecessary(response);
                        return response;
                    });
                }
            };
        }

        @Override
        public HttpExecutionStrategy requiredOffloads() {
            // no influence since we do not block
            return offloadNone();
        }
    }
}
