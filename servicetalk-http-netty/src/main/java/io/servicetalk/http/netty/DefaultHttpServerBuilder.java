/*
 * Copyright © 2018-2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ConnectionAcceptorFactory;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.ExecutionStrategyInfluencer;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.InfluencerConnectionAcceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpApiConversions.toStreamingHttpService;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.netty.StrategyInfluencerAwareConversions.toConditionalServiceFilterFactory;
import static io.servicetalk.transport.api.ConnectionAcceptor.ACCEPT_ALL;
import static java.util.Objects.requireNonNull;

final class DefaultHttpServerBuilder implements HttpServerBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHttpServerBuilder.class);

    private static final HttpExecutionStrategy REQRESP_OFFLOADS = HttpExecutionStrategies.customStrategyBuilder()
            .offloadReceiveMetadata().offloadReceiveData().offloadSend().build();

    @Nullable
    private ConnectionAcceptorFactory connectionAcceptorFactory;
    private final List<StreamingHttpServiceFilterFactory> noOffloadServiceFilters = new ArrayList<>();
    private final List<StreamingHttpServiceFilterFactory> serviceFilters = new ArrayList<>();
    private HttpExecutionStrategy strategy = defaultStrategy();
    private boolean drainRequestPayloadBody = true;
    private final HttpServerConfig config = new HttpServerConfig();
    private final HttpExecutionContextBuilder executionContextBuilder = new HttpExecutionContextBuilder();
    private final SocketAddress address;

    // Do not use this ctor directly, HttpServers is the entry point for creating a new builder.
    DefaultHttpServerBuilder(SocketAddress address) {
        appendNonOffloadingServiceFilter(ClearAsyncContextHttpServiceFilter.CLEAR_ASYNC_CONTEXT_HTTP_SERVICE_FILTER);
        this.address = address;
    }

    private static StreamingHttpServiceFilterFactory buildFactory(List<StreamingHttpServiceFilterFactory> filters) {
        return filters.stream()
                .reduce((prev, filter) -> service -> prev.create(filter.create(service)))
                .orElse(StreamingHttpServiceFilter::new); // unfortunate that we need extra layer
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
    public HttpServerBuilder drainRequestPayloadBody(final boolean enable) {
        this.drainRequestPayloadBody = enable;
        return this;
    }

    @Override
    public HttpServerBuilder appendConnectionAcceptorFilter(final ConnectionAcceptorFactory factory) {
        if (connectionAcceptorFactory == null) {
            connectionAcceptorFactory = factory;
        } else {
            connectionAcceptorFactory = connectionAcceptorFactory.append(factory);
        }
        return this;
    }

    @Override
    public HttpServerBuilder appendNonOffloadingServiceFilter(final StreamingHttpServiceFilterFactory factory) {
        noOffloadServiceFilters.add(checkNonOffloading("Filter", defaultStrategy(), factory));
        return this;
    }

    @Override
    public HttpServerBuilder appendNonOffloadingServiceFilter(final Predicate<StreamingHttpRequest> predicate,
                                                              final StreamingHttpServiceFilterFactory factory) {
        checkNonOffloading("Predicate", offloadNone(), predicate);
        checkNonOffloading("Filter", defaultStrategy(), factory);
        noOffloadServiceFilters.add(toConditionalServiceFilterFactory(predicate, factory));
        return this;
    }

    @Override
    public HttpServerBuilder appendServiceFilter(final StreamingHttpServiceFilterFactory factory) {
        requireNonNull(factory);
        serviceFilters.add(factory);
        return this;
    }

    @Override
    public HttpServerBuilder appendServiceFilter(final Predicate<StreamingHttpRequest> predicate,
                                                 final StreamingHttpServiceFilterFactory factory) {
        appendServiceFilter(toConditionalServiceFilterFactory(predicate, factory));
        return this;
    }

    @Override
    public HttpServerBuilder protocols(final HttpProtocolConfig... protocols) {
        config.httpConfig().protocols(protocols);
        return this;
    }

    @Override
    public HttpServerBuilder sslConfig(final ServerSslConfig config) {
        this.config.tcpConfig().sslConfig(config);
        return this;
    }

    @Override
    public HttpServerBuilder sslConfig(final ServerSslConfig defaultConfig, final Map<String, ServerSslConfig> sniMap) {
        this.config.tcpConfig().sslConfig(defaultConfig, sniMap);
        return this;
    }

    @Override
    public <T> HttpServerBuilder socketOption(final SocketOption<T> option, final T value) {
        config.tcpConfig().socketOption(option, value);
        return this;
    }

    @Override
    public <T> HttpServerBuilder listenSocketOption(final SocketOption<T> option, final T value) {
        config.tcpConfig().listenSocketOption(option, value);
        return this;
    }

    @Override
    public HttpServerBuilder enableWireLogging(final String loggerName, final LogLevel logLevel,
                                               final BooleanSupplier logUserData) {
        config.tcpConfig().enableWireLogging(loggerName, logLevel, logUserData);
        return this;
    }

    @Override
    public HttpServerBuilder transportObserver(final TransportObserver transportObserver) {
        config.tcpConfig().transportObserver(transportObserver);
        return this;
    }

    @Override
    public HttpServerBuilder lifecycleObserver(final HttpLifecycleObserver lifecycleObserver) {
        config.lifecycleObserver(lifecycleObserver);
        return this;
    }

    @Override
    public HttpServerBuilder allowDropRequestTrailers(final boolean allowDrop) {
        config.httpConfig().allowDropTrailersReadFromTransport(allowDrop);
        return this;
    }

    @Override
    public HttpServerBuilder executor(final Executor executor) {
        executionContextBuilder.executor(executor);
        return this;
    }

    @Override
    public HttpServerBuilder executionStrategy(HttpExecutionStrategy strategy) {
        this.strategy = requireNonNull(strategy);
        return this;
    }

    @Override
    public HttpServerBuilder ioExecutor(final IoExecutor ioExecutor) {
        executionContextBuilder.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public HttpServerBuilder bufferAllocator(final BufferAllocator allocator) {
        executionContextBuilder.bufferAllocator(allocator);
        return this;
    }

    @Override
    public Single<HttpServerContext> listen(final HttpService service) {
        StreamingHttpService streamingService = toStreamingHttpService(
                computeServiceStrategy(HttpService.class, service), service);
        return listenForService(streamingService, streamingService.requiredOffloads());
    }

    @Override
    public Single<HttpServerContext> listenStreaming(final StreamingHttpService service) {
        return listenForService(service, computeServiceStrategy(StreamingHttpService.class, service));
    }

    @Override
    public Single<HttpServerContext> listenBlocking(final BlockingHttpService service) {
        StreamingHttpService streamingService = toStreamingHttpService(
                computeServiceStrategy(BlockingHttpService.class, service), service);
        return listenForService(streamingService, streamingService.requiredOffloads());
    }

    @Override
    public Single<HttpServerContext> listenBlockingStreaming(final BlockingStreamingHttpService service) {
        StreamingHttpService streamingService = toStreamingHttpService(
                computeServiceStrategy(BlockingStreamingHttpService.class, service), service);
        return listenForService(streamingService, streamingService.requiredOffloads());
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
     * @param rawService {@link StreamingHttpService} to use for the server.
     * @param computedStrategy the computed {@link HttpExecutionStrategy} to use for the service.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    private Single<HttpServerContext> listenForService(final StreamingHttpService rawService,
                                                       final HttpExecutionStrategy computedStrategy) {
        InfluencerConnectionAcceptor connectionAcceptor = connectionAcceptorFactory == null ? null :
                InfluencerConnectionAcceptor.withStrategy(connectionAcceptorFactory.create(ACCEPT_ALL),
                        connectionAcceptorFactory.requiredOffloads());

        final StreamingHttpService filteredService;
        final HttpExecutionContext executionContext;

        if (noOffloadServiceFilters.isEmpty()) {
            filteredService = serviceFilters.isEmpty() ? rawService : buildService(serviceFilters.stream(), rawService);
            executionContext = buildExecutionContext(computedStrategy);
        } else {
            Stream<StreamingHttpServiceFilterFactory> nonOffloadingFilters = noOffloadServiceFilters.stream();

            if (computedStrategy.isRequestResponseOffloaded()) {
                executionContext = buildExecutionContext(REQRESP_OFFLOADS.missing(computedStrategy));
                BooleanSupplier shouldOffload = executionContext.ioExecutor().shouldOffloadSupplier();
                // We are going to have to offload, even if just to the raw service
                OffloadingFilter offloadingFilter =
                        new OffloadingFilter(computedStrategy, buildFactory(serviceFilters), shouldOffload);
                nonOffloadingFilters = Stream.concat(nonOffloadingFilters, Stream.of(offloadingFilter));
            } else {
                // All the filters can be appended.
                nonOffloadingFilters = Stream.concat(nonOffloadingFilters, serviceFilters.stream());
                executionContext = buildExecutionContext(computedStrategy);
            }
            filteredService = buildService(nonOffloadingFilters, rawService);
        }

        final HttpExecutionStrategy builderStrategy = this.strategy;
        return doBind(executionContext, connectionAcceptor, filteredService)
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

    private Single<HttpServerContext> doBind(final HttpExecutionContext executionContext,
                                             @Nullable final InfluencerConnectionAcceptor connectionAcceptor,
                                             final StreamingHttpService service) {
        ReadOnlyHttpServerConfig roConfig = config.asReadOnly();
        StreamingHttpService filteredService = applyInternalFilters(service, roConfig.lifecycleObserver());

        if (roConfig.tcpConfig().isAlpnConfigured()) {
            return DeferredServerChannelBinder.bind(executionContext, roConfig, address, connectionAcceptor,
                    filteredService, drainRequestPayloadBody, false);
        } else if (roConfig.tcpConfig().sniMapping() != null) {
            return DeferredServerChannelBinder.bind(executionContext, roConfig, address, connectionAcceptor,
                    filteredService, drainRequestPayloadBody, true);
        } else if (roConfig.isH2PriorKnowledge()) {
            return H2ServerParentConnectionContext.bind(executionContext, roConfig, address, connectionAcceptor,
                    filteredService, drainRequestPayloadBody);
        }
        return NettyHttpServer.bind(executionContext, roConfig, address, connectionAcceptor,
                filteredService, drainRequestPayloadBody);
    }

    private <T extends HttpExecutionStrategyInfluencer> HttpExecutionStrategy computeServiceStrategy(
            final Class<T> clazz, final T service) {
        final HttpExecutionStrategy serviceStrategy = service.requiredOffloads();
        LOGGER.debug("{} '{}' requires {} strategy.", clazz.getSimpleName(), service, serviceStrategy);
        final HttpExecutionStrategy builderStrategy = this.strategy;
        final HttpExecutionStrategy computedStrategy = computeRequiredStrategy(serviceFilters, serviceStrategy);
        return defaultStrategy() == builderStrategy ? computedStrategy :
                builderStrategy.hasOffloads() ? builderStrategy.merge(computedStrategy) : builderStrategy;
    }

    private static StreamingHttpService applyInternalFilters(StreamingHttpService service,
                                                             @Nullable final HttpLifecycleObserver lifecycleObserver) {
        service = HttpExceptionMapperServiceFilter.INSTANCE.create(service);
        service = KeepAliveServiceFilter.INSTANCE.create(service);
        if (lifecycleObserver != null) {
            service = new HttpLifecycleObserverServiceFilter(lifecycleObserver).create(service);
        }
        // TODO: apply ClearAsyncContextHttpServiceFilter here when it's moved to http-netty module by
        //  https://github.com/apple/servicetalk/pull/1820
        return service;
    }

    /**
     * Internal filter that correctly sets {@link HttpHeaderNames#CONNECTION} header value based on the requested
     * keep-alive policy.
     */
    private static final class KeepAliveServiceFilter implements StreamingHttpServiceFilterFactory {

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
