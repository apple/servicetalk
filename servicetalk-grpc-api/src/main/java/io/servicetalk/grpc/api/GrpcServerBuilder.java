/*
 * Copyright © 2019-2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.api;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
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
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.FutureUtils.awaitResult;
import static io.servicetalk.grpc.api.GrpcUtils.GRPC_CONTENT_TYPE;
import static io.servicetalk.grpc.api.GrpcUtils.newErrorResponse;

/**
 * A builder for building a <a href="https://www.grpc.io">gRPC</a> server.
 */
public abstract class GrpcServerBuilder {

    /**
     * Initializes the underlying {@link HttpServerBuilder} used for the transport layer.
     */
    @FunctionalInterface
    public interface HttpInitializer {

        /**
         * Configures the underlying {@link HttpServerBuilder}.
         * @param builder The builder to customize the HTTP layer.
         */
        void initialize(HttpServerBuilder builder);
    }

    private boolean appendedCatchAllFilter;

    /**
     * Set a function which can configure the underlying {@link HttpServerBuilder} used for the transport layer.
     * <p>
     * Please note that this method shouldn't be mixed with the {@link Deprecated} methods of this class as the order
     * of operations would not be the same as the order in which the calls are made. Please migrate all of the calls
     * to this method.
     * @param initializer Initializes the underlying HTTP transport builder.
     * @return {@code this}.
     */
    public GrpcServerBuilder initializeHttp(HttpInitializer initializer) {
        throw new UnsupportedOperationException("Initializing the HttpServerBuilder using this method is not yet" +
                "supported by " + getClass().getName());
    }

    /**
     * Configurations of various underlying protocol versions.
     * <p>
     * <b>Note:</b> the order of specified protocols will reflect on priorities for ALPN in case the connections use
     * {@link #sslConfig(ServerSslConfig)}.
     *
     * @param protocols {@link HttpProtocolConfig} for each protocol that should be supported.
     * @return {@code this}.
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link HttpServerBuilder#protocols(HttpProtocolConfig...)}
     * on the {@code builder} instance by implementing {@link HttpInitializer#initialize(HttpServerBuilder)}
     * functional interface.
     */
    @Deprecated
    public abstract GrpcServerBuilder protocols(HttpProtocolConfig... protocols);

    /**
     * Set a default timeout during which gRPC calls are expected to complete. This default will be used only if the
     * request includes no timeout; any value specified in client request will supersede this default.
     *
     * @param defaultTimeout {@link Duration} of default timeout which must be positive non-zero.
     * @return {@code this}.
     */
    public abstract GrpcServerBuilder defaultTimeout(Duration defaultTimeout);

    /**
     * Set the SSL/TLS configuration.
     * @param config The configuration to use.
     * @return {@code this}.
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link HttpServerBuilder#sslConfig(ServerSslConfig)}
     * on the {@code builder} instance by implementing {@link HttpInitializer#initialize(HttpServerBuilder)}
     * functional interface.
     */
    @Deprecated
    public abstract GrpcServerBuilder sslConfig(ServerSslConfig config);

    /**
     * Set the SSL/TLS and <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a> configuration.
     * @param defaultConfig The configuration to use is the client certificate's SNI extension isn't present or the
     * SNI hostname doesn't match any values in {@code sniMap}.
     * @param sniMap A map where the keys are matched against the client certificate's SNI extension value in order
     * to provide the corresponding {@link ServerSslConfig}.
     * @return {@code this}.
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link HttpServerBuilder#sslConfig(ServerSslConfig, Map)}
     * on the {@code builder} instance by implementing {@link HttpInitializer#initialize(HttpServerBuilder)}
     * functional interface.
     */
    @Deprecated
    public abstract GrpcServerBuilder sslConfig(ServerSslConfig defaultConfig, Map<String, ServerSslConfig> sniMap);

    /**
     * Add a {@link SocketOption} that is applied.
     *
     * @param <T> the type of the value.
     * @param option the option to apply.
     * @param value the value.
     * @return {@code this}.
     * @see StandardSocketOptions
     * @see ServiceTalkSocketOptions
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link HttpServerBuilder#socketOption(SocketOption, Object)}
     * on the {@code builder} instance by implementing {@link HttpInitializer#initialize(HttpServerBuilder)}
     * functional interface.
     */
    @Deprecated
    public abstract <T> GrpcServerBuilder socketOption(SocketOption<T> option, T value);

    /**
     * Adds a {@link SocketOption} that is applied to the server socket channel which listens/accepts socket channels.
     * @param <T> the type of the value.
     * @param option the option to apply.
     * @param value the value.
     * @return this.
     * @see StandardSocketOptions
     * @see ServiceTalkSocketOptions
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link HttpServerBuilder#listenSocketOption(SocketOption, Object)}
     * on the {@code builder} instance by implementing {@link HttpInitializer#initialize(HttpServerBuilder)}
     * functional interface.
     */
    @Deprecated
    public abstract <T> GrpcServerBuilder listenSocketOption(SocketOption<T> option, T value);

    /**
     * Enables wire-logging for connections created by this builder.
     *
     * @param loggerName The name of the logger to log wire events.
     * @param logLevel The level to log at.
     * @param logUserData {@code true} to include user data (e.g. data, headers, etc.). {@code false} to exclude user
     * data and log only network events.
     * @return {@code this}.
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link HttpServerBuilder#enableWireLogging(String, LogLevel, BooleanSupplier)}
     * on the {@code builder} instance by implementing {@link HttpInitializer#initialize(HttpServerBuilder)}
     * functional interface.
     */
    @Deprecated
    public abstract GrpcServerBuilder enableWireLogging(String loggerName, LogLevel logLevel,
                                                        BooleanSupplier logUserData);

    /**
     * Sets a {@link TransportObserver} that provides visibility into transport events.
     *
     * @param transportObserver A {@link TransportObserver} that provides visibility into transport events.
     * @return {@code this}.
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link HttpServerBuilder#transportObserver(TransportObserver)}
     * on the {@code builder} instance by implementing {@link HttpInitializer#initialize(HttpServerBuilder)}
     * functional interface.
     */
    @Deprecated
    public abstract GrpcServerBuilder transportObserver(TransportObserver transportObserver);

    /**
     * Sets a {@link GrpcLifecycleObserver} that provides visibility into gRPC lifecycle events.
     *
     * @param lifecycleObserver A {@link GrpcLifecycleObserver} that provides visibility into gRPC lifecycle events.
     * @return {@code this}.
     */
    public abstract GrpcServerBuilder lifecycleObserver(GrpcLifecycleObserver lifecycleObserver);

    /**
     * Configures automatic consumption of request {@link StreamingHttpRequest#payloadBody() payload body} when it is
     * not consumed by the service.
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
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link HttpServerBuilder#drainRequestPayloadBody(boolean)}
     * on the {@code builder} instance by implementing {@link HttpInitializer#initialize(HttpServerBuilder)}
     * functional interface.
     */
    @Deprecated
    public abstract GrpcServerBuilder drainRequestPayloadBody(boolean enable);

    /**
     * Append the filter to the chain of filters used to decorate the {@link ConnectionAcceptor} used by this builder.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.appendConnectionAcceptorFilter(filter1).appendConnectionAcceptorFilter(filter2).
     *     appendConnectionAcceptorFilter(filter3)
     * </pre>
     * accepting a connection by a filter wrapped by this filter chain, the order of invocation of these filters will
     * be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3
     * </pre>
     * @param factory {@link ConnectionAcceptorFactory} to append. Lifetime of this
     * {@link ConnectionAcceptorFactory} is managed by this builder and the server started thereof.
     * @return {@code this}.
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link HttpServerBuilder#appendConnectionAcceptorFilter(ConnectionAcceptorFactory)}
     * on the {@code builder} instance by implementing {@link HttpInitializer#initialize(HttpServerBuilder)}
     * functional interface.
     */
    @Deprecated
    public abstract GrpcServerBuilder appendConnectionAcceptorFilter(ConnectionAcceptorFactory factory);

    /**
     * Append the filter to the chain of filters used to decorate the service used by this builder.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * accepting a request by a service wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ service
     * </pre>
     * @param factory {@link StreamingHttpServiceFilterFactory} to append.
     * @return {@code this}.
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link HttpServerBuilder#appendServiceFilter(StreamingHttpServiceFilterFactory)}
     * on the {@code builder} instance by implementing {@link HttpInitializer#initialize(HttpServerBuilder)}
     * functional interface.
     */
    @Deprecated
    public final GrpcServerBuilder appendHttpServiceFilter(StreamingHttpServiceFilterFactory factory) {
        doAppendHttpServiceFilter(factory);
        return this;
    }

    /**
     * Append the filter to the chain of filters used to decorate the service used by this builder, for every request
     * that passes the provided {@link Predicate}.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * accepting a request by a service wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ service
     * </pre>
     * @param predicate the {@link Predicate} to test if the filter must be applied.
     * @param factory {@link StreamingHttpServiceFilterFactory} to append.
     * @return {@code this}.
     * @deprecated Call {@link #initializeHttp(HttpInitializer)} and use
     * {@link HttpServerBuilder#appendServiceFilter(Predicate, StreamingHttpServiceFilterFactory)}
     * on the {@code builder} instance by implementing {@link HttpInitializer#initialize(HttpServerBuilder)}
     * functional interface.
     */
    @Deprecated
    public final GrpcServerBuilder appendHttpServiceFilter(Predicate<StreamingHttpRequest> predicate,
                                                           StreamingHttpServiceFilterFactory factory) {
        doAppendHttpServiceFilter(predicate, factory);
        return this;
    }

    /**
     * Sets the {@link Executor} to be used by this server.
     *
     * @param executor {@link Executor} to use.
     * @return {@code this}.
     */
    public abstract GrpcServerBuilder executor(Executor executor);

    /**
     * Sets the {@link IoExecutor} to be used by this server.
     *
     * @param ioExecutor {@link IoExecutor} to use.
     * @return {@code this}.
     */
    public abstract GrpcServerBuilder ioExecutor(IoExecutor ioExecutor);

    /**
     * Sets the {@link BufferAllocator} to be used by this server.
     *
     * @param allocator {@link BufferAllocator} to use.
     * @return {@code this}.
     */
    public abstract GrpcServerBuilder bufferAllocator(BufferAllocator allocator);

    /**
     * Sets the {@link HttpExecutionStrategy} to be used by this server.
     *
     * @param strategy {@link HttpExecutionStrategy} to use by this server.
     * @return {@code this}.
     */
    public abstract GrpcServerBuilder executionStrategy(GrpcExecutionStrategy strategy);

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param services {@link GrpcBindableService}(s) to create a <a href="https://www.grpc.io">gRPC</a> service.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    public final Single<ServerContext> listen(GrpcBindableService<?, ?, ?>... services) {
        GrpcServiceFactory<?, ?, ?>[] factories = Arrays.stream(services)
                .map(GrpcBindableService::bindService)
                .toArray(GrpcServiceFactory<?, ?, ?>[]::new);
        return listen(factories);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param serviceFactories {@link GrpcServiceFactory}(s) to create a <a href="https://www.grpc.io">gRPC</a> service.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    public final Single<ServerContext> listen(GrpcServiceFactory<?, ?, ?>... serviceFactories) {
        return doListen(GrpcServiceFactory.merge(serviceFactories));
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param serviceFactories {@link GrpcServiceFactory}(s) to create a <a href="https://www.grpc.io">gRPC</a> service.
     * @return A {@link ServerContext} by blocking the calling thread until the server is successfully started or
     * throws an {@link Exception} if the server could not be started.
     * @throws Exception if the server could not be started.
     */
    public final ServerContext listenAndAwait(GrpcServiceFactory<?, ?, ?>... serviceFactories) throws Exception {
        return awaitResult(listen(serviceFactories).toFuture());
    }

     /**
      * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
      * <p>
      * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
      *
      * @param services {@link GrpcBindableService}(s) to create a <a href="https://www.grpc.io">gRPC</a> service.
      * @return A {@link ServerContext} by blocking the calling thread until the server is successfully started or
      * throws an {@link Exception} if the server could not be started.
      * @throws Exception if the server could not be started.
      */
     public final ServerContext listenAndAwait(GrpcBindableService<?, ?, ?>... services) throws Exception {
         GrpcServiceFactory<?, ?, ?>[] factories = Arrays.stream(services)
                 .map(GrpcBindableService::bindService)
                 .toArray(GrpcServiceFactory<?, ?, ?>[]::new);
         return listenAndAwait(factories);
     }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param serviceFactory {@link GrpcServiceFactory} to create a <a href="https://www.grpc.io">gRPC</a> service.
     * @return A {@link ServerContext} by blocking the calling thread until the server is successfully started or
     * throws an {@link Exception} if the server could not be started.
     */
    protected abstract Single<ServerContext> doListen(GrpcServiceFactory<?, ?, ?> serviceFactory);

    /**
     * Append the filter to the chain of filters used to decorate the service used by this builder.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * accepting a request by a service wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ service
     * </pre>
     * @param factory {@link StreamingHttpServiceFilterFactory} to append.
     */
    protected abstract void doAppendHttpServiceFilter(StreamingHttpServiceFilterFactory factory);

    /**
     * Append the filter to the chain of filters used to decorate the service used by this builder, for every request
     * that passes the provided {@link Predicate}.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * accepting a request by a service wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ service
     * </pre>
     * @param predicate the {@link Predicate} to test if the filter must be applied.
     * @param factory {@link StreamingHttpServiceFilterFactory} to append.
     */
    protected abstract void doAppendHttpServiceFilter(Predicate<StreamingHttpRequest> predicate,
                                                      StreamingHttpServiceFilterFactory factory);

    protected void appendCatchAllFilterIfRequired() {
        // TODO(dj): Move to DefaultGrpcServerBuilder and remove the check as the call is in the constructor.
        // This code depends on GrpcUtils which is inaccessible from the servicetalk-grpc-netty module.
        // When this class is converted to an interface we can also refactor that part.
        if (!appendedCatchAllFilter) {
            doAppendHttpServiceFilter(CatchAllHttpServiceFilter::new);
            appendedCatchAllFilter = true;
        }
    }

    static final class CatchAllHttpServiceFilter extends StreamingHttpServiceFilter {
        CatchAllHttpServiceFilter(final StreamingHttpService service) {
            super(service);
        }

        @Override
        public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                    final StreamingHttpRequest request,
                                                    final StreamingHttpResponseFactory responseFactory) {
            final Single<StreamingHttpResponse> handle;
            try {
                handle = delegate().handle(ctx, request, responseFactory);
            } catch (Throwable cause) {
                return succeeded(convertToGrpcErrorResponse(ctx, responseFactory, cause));
            }
            return handle.onErrorReturn(cause -> convertToGrpcErrorResponse(ctx, responseFactory, cause));
        }

        private static StreamingHttpResponse convertToGrpcErrorResponse(
                final HttpServiceContext ctx, final StreamingHttpResponseFactory responseFactory,
                final Throwable cause) {
            return newErrorResponse(responseFactory, GRPC_CONTENT_TYPE, cause,
                    ctx.executionContext().bufferAllocator());
        }
    }
}
