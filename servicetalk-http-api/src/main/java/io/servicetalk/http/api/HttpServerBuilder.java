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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpApiConversions.ServiceAdapterHolder;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.ConnectionAcceptorFactory;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;
import io.servicetalk.transport.api.TransportObserver;

import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static io.servicetalk.http.api.HttpApiConversions.toStreamingHttpService;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategyInfluencer.defaultStreamingInfluencer;
import static io.servicetalk.http.api.StrategyInfluencerAwareConversions.toConditionalServiceFilterFactory;
import static io.servicetalk.transport.api.ConnectionAcceptor.ACCEPT_ALL;
import static java.util.Objects.requireNonNull;

/**
 * A builder for building HTTP Servers.
 */
public abstract class HttpServerBuilder {

    @Nullable
    private ConnectionAcceptorFactory connectionAcceptorFactory;
    private final List<StreamingHttpServiceFilterFactory> noOffloadServiceFilters = new ArrayList<>();
    private final List<StreamingHttpServiceFilterFactory> serviceFilters = new ArrayList<>();
    private HttpExecutionStrategy strategy = defaultStrategy();
    private boolean drainRequestPayloadBody = true;

    /**
     * Create a new instance.
     */
    protected HttpServerBuilder() {
        // Async context clear goes before everything else.
        appendNonOffloadingServiceFilter(ClearAsyncContextHttpServiceFilter.CLEAR_ASYNC_CONTEXT_HTTP_SERVICE_FILTER);
    }

    /**
     * Configurations of various HTTP protocol versions.
     * <p>
     * <b>Note:</b> the order of specified protocols will reflect on priorities for
     * <a href="https://tools.ietf.org/html/rfc7301">ALPN</a> in case the connections use
     * {@link #sslConfig(ServerSslConfig)}.
     *
     * @param protocols {@link HttpProtocolConfig} for each protocol that should be supported.
     * @return {@code this}.
     */
    public abstract HttpServerBuilder protocols(HttpProtocolConfig... protocols);

    /**
     * Sets the maximum queue length for incoming connection indications (a request to connect) is set to the backlog
     * parameter. If a connection indication arrives when the queue is full, the connection may time out.
     * @deprecated Use {@link #listenSocketOption(SocketOption, Object)} with key
     * {@link ServiceTalkSocketOptions#SO_BACKLOG}.
     * @param backlog the backlog to use when accepting connections.
     * @return {@code this}.
     */
    @Deprecated
    public HttpServerBuilder backlog(int backlog) {
        listenSocketOption(ServiceTalkSocketOptions.SO_BACKLOG, backlog);
        return this;
    }

    /**
     * Initiates security configuration for this server. Calling any {@code commit} method on the returned
     * {@link HttpServerSecurityConfigurator} will commit the configuration.
     * @deprecated Use {@link #sslConfig(ServerSslConfig)}.
     * @return {@link HttpServerSecurityConfigurator} to configure security for this server. It is
     * mandatory to call any one of the {@code commit} methods after all configuration is done.
     */
    @Deprecated
    public abstract HttpServerSecurityConfigurator secure();

    /**
     * Set the SSL/TLS configuration.
     * @param config The configuration to use.
     * @return {@code this}.
     */
    public abstract HttpServerBuilder sslConfig(ServerSslConfig config);

    /**
     * Set the SSL/TLS and <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a> configuration.
     * @param defaultConfig The configuration to use is the client certificate's SNI extension isn't present or the
     * SNI hostname doesn't match any values in {@code sniMap}.
     * @param sniMap A map where the keys are matched against the client certificate's SNI extension value in order
     * to provide the corresponding {@link ServerSslConfig}.
     * @return {@code this}.
     */
    public abstract HttpServerBuilder sslConfig(ServerSslConfig defaultConfig, Map<String, ServerSslConfig> sniMap);

    /**
     * Adds a {@link SocketOption} that is applied to connected/accepted socket channels.
     *
     * @param <T> the type of the value.
     * @param option the option to apply.
     * @param value the value.
     * @return this.
     * @see StandardSocketOptions
     * @see ServiceTalkSocketOptions
     */
    public abstract <T> HttpServerBuilder socketOption(SocketOption<T> option, T value);

    /**
     * Adds a {@link SocketOption} that is applied to the server socket channel which listens/accepts socket channels.
     * @param <T> the type of the value.
     * @param option the option to apply.
     * @param value the value.
     * @return this.
     * @see StandardSocketOptions
     * @see ServiceTalkSocketOptions
     */
    public abstract <T> HttpServerBuilder listenSocketOption(SocketOption<T> option, T value);

    /**
     * Enables wire-logging for this server.
     * <p>
     * @deprecated Use {@link #enableWireLogging(String, LogLevel, BooleanSupplier)} instead.
     * @param loggerName The name of the logger to log wire events.
     * @return {@code this}.
     */
    @Deprecated
    public abstract HttpServerBuilder enableWireLogging(String loggerName);

    /**
     * Enables wire-logging for this server.
     *
     * @param loggerName The name of the logger to log wire events.
     * @param logLevel The level to log at.
     * @param logUserData {@code true} to include user data (e.g. data, headers, etc.). {@code false} to exclude user
     * data and log only network events.
     * @return {@code this}.
     */
    public abstract HttpServerBuilder enableWireLogging(String loggerName, LogLevel logLevel,
                                                        BooleanSupplier logUserData);

    /**
     * Sets a {@link TransportObserver} that provides visibility into transport events.
     *
     * @param transportObserver A {@link TransportObserver} that provides visibility into transport events.
     * @return {@code this}.
     */
    public abstract HttpServerBuilder transportObserver(TransportObserver transportObserver);

    /**
     * Disables automatic consumption of request {@link StreamingHttpRequest#payloadBody() payload body} when it is not
     * consumed by the service.
     * <p>
     * For <a href="https://tools.ietf.org/html/rfc7230#section-6.3">persistent HTTP connections</a> it is required to
     * eventually consume the entire request payload to enable reading of the next request. This is required because
     * requests are pipelined for HTTP/1.1, so if the previous request is not completely read, next request can not be
     * read from the socket. For cases when there is a possibility that user may forget to consume request payload,
     * ServiceTalk automatically consumes request payload body. This automatic consumption behavior may create some
     * overhead and can be disabled using this method when it is guaranteed that all request paths consumes all request
     * payloads eventually. An example of guaranteed consumption are {@link HttpRequest non-streaming APIs}.
     *
     * @return {@code this}.
     * @deprecated Use {@link #drainRequestPayloadBody(boolean)}.
     */
    @Deprecated
    public final HttpServerBuilder disableDrainingRequestPayloadBody() {
        this.drainRequestPayloadBody = false;
        return this;
    }

    /**
     * Configure automatic consumption of request {@link StreamingHttpRequest#payloadBody() payload body} when it is not
     * consumed by the service.
     * <p>
     * For <a href="https://tools.ietf.org/html/rfc7230#section-6.3">persistent HTTP connections</a> it is required to
     * eventually consume the entire request payload to enable reading of the next request. This is required because
     * requests are pipelined for HTTP/1.1, so if the previous request is not completely read, next request can not be
     * read from the socket. For cases when there is a possibility that user may forget to consume request payload,
     * ServiceTalk automatically consumes request payload body. This automatic consumption behavior may create some
     * overhead and can be disabled using this method when it is guaranteed that all request paths consumes all request
     * payloads eventually. An example of guaranteed consumption are {@link HttpRequest non-streaming APIs}.
     *
     * @param enable When {@code false} it will disable the automatic consumption of request
     * {@link StreamingHttpRequest#payloadBody()}.
     * @return {@code this}.
     */
    public final HttpServerBuilder drainRequestPayloadBody(final boolean enable) {
        this.drainRequestPayloadBody = enable;
        return this;
    }

    /**
     * Provide a hint if request <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailers</a> are allowed to
     * be dropped. This hint maybe ignored if the transport can otherwise infer that the
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailers</a> should be preserved. For example, if the
     * request headers contain <a href="https://tools.ietf.org/html/rfc7230#section-4.4">Trailer</a>
     * then this hint maybe ignored.
     * @param allowDrop {@code true} if request <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailers</a>
     * are allowed to be dropped.
     * @return {@code this}
     */
    public abstract HttpServerBuilder allowDropRequestTrailers(boolean allowDrop);

    /**
     * Appends the filter to the chain of filters used to decorate the {@link ConnectionAcceptor} used by this builder.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder
     *          .appendConnectionAcceptorFilter(filter1)
     *          .appendConnectionAcceptorFilter(filter2)
     *          .appendConnectionAcceptorFilter(filter3)
     * </pre>
     * accepting a connection by a filter wrapped by this filter chain, the order of invocation of these filters will
     * be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3
     * </pre>
     *
     * @param factory {@link ConnectionAcceptorFactory} to append. Lifetime of this
     * {@link ConnectionAcceptorFactory} is managed by this builder and the server started thereof.
     * @return {@code this}
     */
    public final HttpServerBuilder appendConnectionAcceptorFilter(final ConnectionAcceptorFactory factory) {
        if (connectionAcceptorFactory == null) {
            connectionAcceptorFactory = factory;
        } else {
            connectionAcceptorFactory = connectionAcceptorFactory.append(factory);
        }
        return this;
    }

    /**
     * Appends a non-offloading filter to the chain of filters used to decorate the {@link StreamingHttpService} used
     * by this builder.
     * <p>
     * Note this method will be used to decorate the {@link StreamingHttpService} passed to
     * {@link #listenStreaming(StreamingHttpService)} before it is used by the server.
     * <p>
     * The order of execution of these filters are in order of append, before the filters added with
     * {@link #appendServiceFilter(StreamingHttpServiceFilterFactory)}. If 3 filters are added as follows:
     * <pre>
     *     builder.appendServiceFilter(filter1).appendNonOffloadingServiceFilter(filter2).appendServiceFilter(filter3)
     * </pre>
     * accepting a request by a service wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter2 ⇒ filter1 ⇒ filter3 ⇒ service
     * </pre>
     *
     * @param factory {@link StreamingHttpServiceFilterFactory} to append.
     * @return {@code this}
     * @throws IllegalArgumentException if the provided filter requires offloading.
     */
    public final HttpServerBuilder appendNonOffloadingServiceFilter(final StreamingHttpServiceFilterFactory factory) {
        noOffloadServiceFilters.add(checkNonOffloading("Non-offloading filter",
                HttpExecutionStrategies.defaultStrategy(), factory));
        return this;
    }

    /**
     * Appends a non-offloading filter to the chain of filters used to decorate the {@link StreamingHttpService} used
     * by this builder, for every request that passes the provided {@link Predicate}. Filters added via this method
     * will be executed before offloading occurs and before filters appended via
     * {@link #appendServiceFilter(StreamingHttpServiceFilterFactory)}.
     * <p>
     * Note this method will be used to decorate the {@link StreamingHttpService} passed to
     * {@link #listenStreaming(StreamingHttpService)} before it is used by the server.
     * The order of execution of these filters are in order of append, before the filters added with
     * {@link #appendServiceFilter(StreamingHttpServiceFilterFactory)}. If 3 filters are added as follows:
     * <pre>
     *     builder.appendServiceFilter(filter1).appendNonOffloadingServiceFilter(filter2).appendServiceFilter(filter3)
     * </pre>
     * accepting a request by a service wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter2 ⇒ filter1 ⇒ filter3 ⇒ service
     * </pre>
     *
     * @param predicate the {@link Predicate} to test if the filter must be applied. This must not block.
     * @param factory {@link StreamingHttpServiceFilterFactory} to append.
     * @return {@code this}
     * @throws IllegalArgumentException if the provided filter or predicate requires offloading.
     */
    public final HttpServerBuilder appendNonOffloadingServiceFilter(final Predicate<StreamingHttpRequest> predicate,
                                                                    final StreamingHttpServiceFilterFactory factory) {
        checkNonOffloading("Non-offloading predicate", noOffloadsStrategy(), predicate);
        checkNonOffloading("Non-offloading filter", HttpExecutionStrategies.defaultStrategy(), factory);
        noOffloadServiceFilters.add(
                service -> new ConditionalHttpServiceFilter(predicate, factory.create(service), service));
        return this;
    }

    /**
     * Appends the filter to the chain of filters used to decorate the {@link StreamingHttpService} used by this
     * builder.
     * <p>
     * Note this method will be used to decorate the {@link StreamingHttpService} passed to
     * {@link #listenStreaming(StreamingHttpService)} before it is used by the server.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * accepting a request by a service wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *      filter1 ⇒ filter2 ⇒ filter3 ⇒ service
     * </pre>
     *
     * @param factory {@link StreamingHttpServiceFilterFactory} to append.
     * @return {@code this}
     */
    public final HttpServerBuilder appendServiceFilter(final StreamingHttpServiceFilterFactory factory) {
        requireNonNull(factory);
        serviceFilters.add(factory);
        return this;
    }

    /**
     * Appends the filter to the chain of filters used to decorate the {@link StreamingHttpService} used by this
     * builder, for every request that passes the provided {@link Predicate}.
     * <p>
     * Note this method will be used to decorate the {@link StreamingHttpService} passed to
     * {@link #listenStreaming(StreamingHttpService)} before it is used by the server.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * accepting a request by a service wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ service
     * </pre>
     *
     * @param predicate the {@link Predicate} to test if the filter must be applied. This must not block.
     * @param factory {@link StreamingHttpServiceFilterFactory} to append.
     * @return {@code this}
     */
    public final HttpServerBuilder appendServiceFilter(final Predicate<StreamingHttpRequest> predicate,
                                                       final StreamingHttpServiceFilterFactory factory) {
        appendServiceFilter(toConditionalServiceFilterFactory(predicate, factory));
        return this;
    }

    /**
     * Sets the {@link IoExecutor} to be used by this server.
     *
     * @param ioExecutor {@link IoExecutor} to use.
     * @return {@code this}.
     */
    public abstract HttpServerBuilder ioExecutor(IoExecutor ioExecutor);

    /**
     * Sets the {@link Executor} to use.
     *
     * @param executor {@link Executor} to use.
     * @return {@code this}.
     */
    public HttpServerBuilder executor(Executor executor) {
        throw new UnsupportedOperationException("Setting Executor not yet supported by " + getClass().getSimpleName());
    }

    /**
     * Sets the {@link BufferAllocator} to be used by this server.
     *
     * @param allocator {@link BufferAllocator} to use.
     * @return {@code this}.
     */
    public abstract HttpServerBuilder bufferAllocator(BufferAllocator allocator);

    /**
     * Sets the {@link HttpExecutionStrategy} to be used by this server.
     *
     * @param strategy {@link HttpExecutionStrategy} to use by this server.
     * @return {@code this}.
     */
    public final HttpServerBuilder executionStrategy(HttpExecutionStrategy strategy) {
        this.strategy = requireNonNull(strategy);
        return this;
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (e.g. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} by blocking the calling thread until the server is successfully started or
     * throws an {@link Exception} if the server could not be started.
     * @throws Exception if the server could not be started.
     */
    public final ServerContext listenAndAwait(final HttpService service) throws Exception {
        return blockingInvocation(listen(service));
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (e.g. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param handler Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} by blocking the calling thread until the server is successfully started or
     * throws an {@link Exception} if the server could not be started.
     * @throws Exception if the server could not be started.
     */
    public final ServerContext listenStreamingAndAwait(final StreamingHttpService handler) throws Exception {
        return blockingInvocation(listenStreaming(handler));
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (e.g. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} by blocking the calling thread until the server is successfully started or
     * throws an {@link Exception} if the server could not be started.
     * @throws Exception if the server could not be started.
     */
    public final ServerContext listenBlockingAndAwait(final BlockingHttpService service) throws Exception {
        return blockingInvocation(listenBlocking(service));
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (e.g. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param handler Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} by blocking the calling thread until the server is successfully started or
     * throws an {@link Exception} if the server could not be started.
     * @throws Exception if the server could not be started.
     */
    public final ServerContext listenBlockingStreamingAndAwait(
            final BlockingStreamingHttpService handler) throws Exception {
        return blockingInvocation(listenBlockingStreaming(handler));
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (e.g. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    public final Single<ServerContext> listen(final HttpService service) {
        return listenForAdapter(toStreamingHttpService(service, strategyInfluencer(service)));
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (e.g. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    public final Single<ServerContext> listenStreaming(final StreamingHttpService service) {
        return listenForService(service, strategy);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (e.g. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    public final Single<ServerContext> listenBlocking(final BlockingHttpService service) {
        return listenForAdapter(toStreamingHttpService(service, strategyInfluencer(service)));
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (e.g. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param service Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    public final Single<ServerContext> listenBlockingStreaming(final BlockingStreamingHttpService service) {
        return listenForAdapter(toStreamingHttpService(service, strategyInfluencer(service)));
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (e.g. TCP) supports it this should result in a socket bind/listen on {@code address}.
     *
     * @param connectionAcceptor {@link ConnectionAcceptor} to use for the server.
     * @param service {@link StreamingHttpService} to use for the server.
     * @param strategy the {@link HttpExecutionStrategy} to use for the service.
     * @param drainRequestPayloadBody if {@code true} the server implementation should automatically subscribe and
     * ignore the {@link StreamingHttpRequest#payloadBody() payload body} of incoming requests.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    protected abstract Single<ServerContext> doListen(@Nullable ConnectionAcceptor connectionAcceptor,
                                                      StreamingHttpService service,
                                                      HttpExecutionStrategy strategy,
                                                      boolean drainRequestPayloadBody);

    private Single<ServerContext> listenForAdapter(ServiceAdapterHolder adapterHolder) {
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

        if (noOffloadServiceFilters.isEmpty()) {
            filteredService = serviceFilters.isEmpty() ? rawService : buildService(serviceFilters.stream(), rawService);
        } else {
            boolean anyOffloads = strategy.isSendOffloaded() ||
                    strategy.isMetadataReceiveOffloaded() ||
                    strategy.isDataReceiveOffloaded();

            Stream<StreamingHttpServiceFilterFactory> nonOffloadingFilters = noOffloadServiceFilters.stream();

            if (anyOffloads) {
                // We are going to have to offload, even if just to the raw service
                nonOffloadingFilters = Stream.concat(nonOffloadingFilters,
                        Stream.of(new OffloadingFilter(strategy, buildFactory(serviceFilters))));
                strategy = null != strategy.executor() ?
                        HttpExecutionStrategies.customStrategyBuilder()
                                .offloadNone().executor(strategy.executor()).build() :
                        noOffloadsStrategy();
            } else {
                // All the filters can be appended.
                nonOffloadingFilters = Stream.concat(nonOffloadingFilters, serviceFilters.stream());
            }
            filteredService = buildService(nonOffloadingFilters, rawService);
        }

        return doListen(connectionAcceptor, filteredService, strategy, drainRequestPayloadBody);
    }

    private HttpExecutionStrategyInfluencer strategyInfluencer(Object service) {
        HttpExecutionStrategyInfluencer influencer =
                buildInfluencer(serviceFilters, strategy -> influenceStrategy(service, strategy));
        HttpExecutionStrategy useStrategy = influencer.influenceStrategy(strategy);

        return s -> s.merge(useStrategy);
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
}
