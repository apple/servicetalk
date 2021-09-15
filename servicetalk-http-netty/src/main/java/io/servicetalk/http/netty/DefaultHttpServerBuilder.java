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
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpApiConversions;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.ConnectionAcceptorFactory;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.TransportObserver;

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
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategyInfluencer.defaultStreamingInfluencer;
import static io.servicetalk.transport.api.ConnectionAcceptor.ACCEPT_ALL;
import static java.util.Objects.requireNonNull;

final class DefaultHttpServerBuilder implements HttpServerBuilder {

    @Nullable
    private ConnectionAcceptorFactory connectionAcceptorFactory;
    private final List<StreamingHttpServiceFilterFactory> noOffloadServiceFilters = new ArrayList<>();
    private final List<StreamingHttpServiceFilterFactory> serviceFilters = new ArrayList<>();
    private HttpExecutionStrategy strategy = defaultStrategy();
    private boolean drainRequestPayloadBody = true;
    private final HttpServerConfig config = new HttpServerConfig();
    private final HttpExecutionContextBuilder executionContextBuilder = new HttpExecutionContextBuilder();
    private final SocketAddress address;

    DefaultHttpServerBuilder(SocketAddress address) {
        appendNonOffloadingServiceFilter(ClearAsyncContextHttpServiceFilter.CLEAR_ASYNC_CONTEXT_HTTP_SERVICE_FILTER);
        this.address = address;
    }

    private static StreamingHttpServiceFilterFactory buildFactory(List<StreamingHttpServiceFilterFactory> filters) {
        return filters.stream()
                .reduce((prev, filter) -> strategy -> prev.create(filter.create(strategy)))
                .orElse(StreamingHttpServiceFilter::new); // unfortunate that we need extra layer
    }

    private static StreamingHttpService buildService(Stream<StreamingHttpServiceFilterFactory> filters,
                                             StreamingHttpService service) {
        return filters
                .reduce((prev, filter) -> svc -> prev.create(filter.create(svc)))
                .map(factory -> (StreamingHttpService) factory.create(service))
                .orElse(service);
    }

    private static HttpExecutionStrategyInfluencer buildInfluencer(List<StreamingHttpServiceFilterFactory> filters,
                                                           HttpExecutionStrategyInfluencer defaultInfluence) {
        return filters.stream()
                .map(filter -> filter instanceof HttpExecutionStrategyInfluencer ?
                        (HttpExecutionStrategyInfluencer) filter :
                        defaultStreamingInfluencer())
                .distinct()
                .reduce(defaultInfluence,
                        (prev, influencer) -> strategy -> influencer.influenceStrategy(prev.influenceStrategy(strategy))
                );
    }

    private static HttpExecutionStrategy influenceStrategy(Object anything, HttpExecutionStrategy strategy) {
        return anything instanceof HttpExecutionStrategyInfluencer ?
                ((HttpExecutionStrategyInfluencer) anything).influenceStrategy(strategy) :
                strategy;
    }

    private static <T> T checkNonOffloading(String desc, HttpExecutionStrategy assumeStrategy, T obj) {
        requireNonNull(obj);
        HttpExecutionStrategy requires = obj instanceof HttpExecutionStrategyInfluencer ?
                ((HttpExecutionStrategyInfluencer) obj).influenceStrategy(noOffloadsStrategy()) :
                assumeStrategy;
        if (requires.isMetadataReceiveOffloaded() || requires.isDataReceiveOffloaded() || requires.isSendOffloaded()) {
            throw new IllegalArgumentException(desc + " required offloading : " + requires);
        }
        return obj;
    }

    private static StreamingHttpServiceFilterFactory toConditionalServiceFilterFactory(
            final Predicate<StreamingHttpRequest> predicate, final StreamingHttpServiceFilterFactory original) {
        requireNonNull(predicate);
        requireNonNull(original);

        if (original instanceof HttpExecutionStrategyInfluencer) {
            HttpExecutionStrategyInfluencer influencer = (HttpExecutionStrategyInfluencer) original;
            return new StrategyInfluencingStreamingServiceFilterFactory() {
                @Override
                public StreamingHttpServiceFilter create(final StreamingHttpService service) {
                    return new ConditionalHttpServiceFilter(predicate, original.create(service), service);
                }

                @Override
                public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
                    return influencer.influenceStrategy(strategy);
                }
            };
        }
        return service -> new ConditionalHttpServiceFilter(predicate, original.create(service), service);
    }

    private interface StrategyInfluencingStreamingServiceFilterFactory
            extends StreamingHttpServiceFilterFactory, HttpExecutionStrategyInfluencer {
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
        noOffloadServiceFilters.add(checkNonOffloading("Non-offloading filter", defaultStrategy(), factory));
        return this;
    }

    @Override
    public HttpServerBuilder appendNonOffloadingServiceFilter(final Predicate<StreamingHttpRequest> predicate,
                                                              final StreamingHttpServiceFilterFactory factory) {
        checkNonOffloading("Non-offloading predicate", noOffloadsStrategy(), predicate);
        checkNonOffloading("Non-offloading filter", defaultStrategy(), factory);
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
    public Single<ServerContext> listen(final HttpService service) {
        return listenForAdapter(toStreamingHttpService(service, strategyInfluencer(service)));
    }

    @Override
    public Single<ServerContext> listenStreaming(final StreamingHttpService service) {
        return listenForService(service, strategy);
    }

    @Override
    public Single<ServerContext> listenBlocking(final BlockingHttpService service) {
        return listenForAdapter(toStreamingHttpService(service, strategyInfluencer(service)));
    }

    @Override
    public Single<ServerContext> listenBlockingStreaming(final BlockingStreamingHttpService service) {
        return listenForAdapter(toStreamingHttpService(service, strategyInfluencer(service)));
    }

    /**
     * Build the execution context for this builder.
     *
     * @param strategy The execution strategy to be used for the context
     * @return the configured and built HTTP execution context.
     */
    private HttpExecutionContext buildExecutionContext(final HttpExecutionStrategy strategy) {
        executionContextBuilder.executionStrategy(strategy);
        return executionContextBuilder.build();
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (e.g. TCP) supports it this should result in a socket bind/listen on {@code address}.
     *
     * @param connectionAcceptor {@link ConnectionAcceptor} to use for the server.
     * @param context the {@link HttpExecutionContext} to use for the service.
     * @param service {@link StreamingHttpService} to use for the server.
     * @param drainRequestPayloadBody if {@code true} the server implementation should automatically subscribe and
     * ignore the {@link StreamingHttpRequest#payloadBody() payload body} of incoming requests.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    private Single<ServerContext> doListen(@Nullable final ConnectionAcceptor connectionAcceptor,
                                           final HttpExecutionContext context,
                                           final StreamingHttpService service,
                                           final boolean drainRequestPayloadBody) {
        final ReadOnlyHttpServerConfig roConfig = this.config.asReadOnly();
        if (roConfig.tcpConfig().isAlpnConfigured()) {
            return DeferredServerChannelBinder.bind(context, roConfig, address, connectionAcceptor,
                    service, drainRequestPayloadBody, false);
        } else if (roConfig.tcpConfig().sniMapping() != null) {
            return DeferredServerChannelBinder.bind(context, roConfig, address, connectionAcceptor,
                    service, drainRequestPayloadBody, true);
        } else if (roConfig.isH2PriorKnowledge()) {
            return H2ServerParentConnectionContext.bind(context, roConfig, address, connectionAcceptor,
                    service, drainRequestPayloadBody);
        }
        return NettyHttpServer.bind(context, roConfig, address, connectionAcceptor, service, drainRequestPayloadBody);
    }

    private Single<ServerContext> listenForAdapter(HttpApiConversions.ServiceAdapterHolder adapterHolder) {
        return listenForService(adapterHolder.adaptor(), adapterHolder.serviceInvocationStrategy());
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
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
     * @param strategy the {@link HttpExecutionStrategy} to use for the service.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    private Single<ServerContext> listenForService(StreamingHttpService rawService, HttpExecutionStrategy strategy) {
        ConnectionAcceptor connectionAcceptor = connectionAcceptorFactory == null ? null :
                connectionAcceptorFactory.create(ACCEPT_ALL);

        final StreamingHttpService filteredService;
        HttpExecutionContext serviceContext;

        if (noOffloadServiceFilters.isEmpty()) {
            filteredService = serviceFilters.isEmpty() ? rawService : buildService(serviceFilters.stream(), rawService);
            serviceContext = buildExecutionContext(strategy);
        } else {
            Stream<StreamingHttpServiceFilterFactory> nonOffloadingFilters = noOffloadServiceFilters.stream();

            if (strategy.hasOffloads()) {
                serviceContext = buildExecutionContext(noOffloadsStrategy());
                BooleanSupplier shouldOffload = serviceContext.ioExecutor().shouldOffloadSupplier();
                // We are going to have to offload, even if just to the raw service
                OffloadingFilter offloadingFilter =
                        new OffloadingFilter(strategy, buildFactory(serviceFilters), shouldOffload);
                nonOffloadingFilters = Stream.concat(nonOffloadingFilters, Stream.of(offloadingFilter));
            } else {
                // All the filters can be appended.
                nonOffloadingFilters = Stream.concat(nonOffloadingFilters, serviceFilters.stream());
                serviceContext = buildExecutionContext(strategy);
            }
            filteredService = buildService(nonOffloadingFilters, rawService);
        }

        return doListen(connectionAcceptor, serviceContext, filteredService, drainRequestPayloadBody);
    }

    private HttpExecutionStrategyInfluencer strategyInfluencer(Object service) {
        HttpExecutionStrategyInfluencer influencer =
                buildInfluencer(serviceFilters, strategy -> influenceStrategy(service, strategy));
        HttpExecutionStrategy useStrategy = influencer.influenceStrategy(strategy);

        return s -> s.merge(useStrategy);
    }
}
