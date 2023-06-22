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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ConnectExecutionStrategy;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.ConnectionAcceptorFactory;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.EarlyConnectionAcceptor;
import io.servicetalk.transport.api.ExecutionStrategyInfluencer;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.LateConnectionAcceptor;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;
import io.servicetalk.transport.api.TransportObserver;

import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.internal.BlockingUtils.blockingInvocation;

/**
 * A builder for building HTTP Servers.
 */
public interface HttpServerBuilder {

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
    HttpServerBuilder protocols(HttpProtocolConfig... protocols);

    /**
     * Set the SSL/TLS configuration.
     *
     * @param config The configuration to use.
     * @return {@code this}.
     */
    HttpServerBuilder sslConfig(ServerSslConfig config);

    /**
     * Set the SSL/TLS and <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a> configuration.
     *
     * @param defaultConfig The configuration to use if the client certificate's SNI extension isn't present or the
     * SNI hostname doesn't match any values in {@code sniMap}.
     * @param sniMap A map where the keys are matched against the client certificate's SNI extension value in order
     * to provide the corresponding {@link ServerSslConfig}.
     * @return {@code this}.
     */
    HttpServerBuilder sslConfig(ServerSslConfig defaultConfig, Map<String, ServerSslConfig> sniMap);

    /**
     * Set the SSL/TLS and <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a> configuration.
     *
     * @param defaultConfig The configuration to use if the client certificate's SNI extension isn't present or the
     * SNI hostname doesn't match any values in {@code sniMap}.
     * @param sniMap A map where the keys are matched against the client certificate's SNI extension value in order
     * to provide the corresponding {@link ServerSslConfig}.
     * @param maxClientHelloLength The maximum length of a
     * <a href="https://www.rfc-editor.org/rfc/rfc5246#section-7.4.1.2">ClientHello</a> message in bytes, up to
     * {@code 2^24 - 1} bytes. Zero ({@code 0}) disables validation.
     * @param clientHelloTimeout The timeout for waiting until
     * <a href="https://www.rfc-editor.org/rfc/rfc5246#section-7.4.1.2">ClientHello</a> message is received.
     * Implementations can round the specified {@link Duration} to full time units, depending on their time granularity.
     * {@link Duration#ZERO Zero (0)} disables timeout.
     * @return {@code this}.
     */
    default HttpServerBuilder sslConfig(ServerSslConfig defaultConfig, Map<String, ServerSslConfig> sniMap,
                                        int maxClientHelloLength, Duration clientHelloTimeout) {
        throw new UnsupportedOperationException(
                "sslConfig(ServerSslConfig, Map, int, Durations) is not supported by " + getClass());
    }

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
    <T> HttpServerBuilder socketOption(SocketOption<T> option, T value);

    /**
     * Adds a {@link SocketOption} that is applied to the server socket channel which listens/accepts socket channels.
     * @param <T> the type of the value.
     * @param option the option to apply.
     * @param value the value.
     * @return this.
     * @see StandardSocketOptions
     * @see ServiceTalkSocketOptions
     */
    <T> HttpServerBuilder listenSocketOption(SocketOption<T> option, T value);

    /**
     * Enables wire-logging for this server.
     *
     * @param loggerName The name of the logger to log wire events.
     * @param logLevel The level to log at.
     * @param logUserData {@code true} to include user data (e.g. data, headers, etc.). {@code false} to exclude user
     * data and log only network events. This method is invoked for each data object allowing for dynamic behavior.
     * @return {@code this}.
     */
    HttpServerBuilder enableWireLogging(String loggerName, LogLevel logLevel, BooleanSupplier logUserData);

    /**
     * Sets a {@link TransportObserver} that provides visibility into transport events.
     *
     * @param transportObserver A {@link TransportObserver} that provides visibility into transport events.
     * @return {@code this}.
     */
    HttpServerBuilder transportObserver(TransportObserver transportObserver);

    /**
     * Sets a {@link HttpLifecycleObserver} that provides visibility into HTTP lifecycle events.
     * <p>
     * This method allows setting a {@link HttpLifecycleObserver} in a position that captures entire state of the
     * execution chain, including all filters and internal post-processing. If it's necessary to observe an exchange
     * after other filters or apply it conditionally, consider using
     * {@code io.servicetalk.http.netty.HttpLifecycleObserverServiceFilter} instead.
     *
     * @param lifecycleObserver A {@link HttpLifecycleObserver} that provides visibility into HTTP lifecycle events.
     * @return {@code this}.
     */
    HttpServerBuilder lifecycleObserver(HttpLifecycleObserver lifecycleObserver);

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
    HttpServerBuilder drainRequestPayloadBody(boolean enable);

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
    HttpServerBuilder allowDropRequestTrailers(boolean allowDrop);

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
     * <p>The connection acceptor will, by default, not be offloaded. If your filter requires the
     * {@link ConnectionAcceptor#accept(ConnectionContext)} to be offloaded then your
     * {@link ConnectionAcceptorFactory} will need to return {@link ConnectExecutionStrategy#offloadAll()} from the
     * {@link ExecutionStrategyInfluencer#requiredOffloads()}.
     *
     * @param factory {@link ConnectionAcceptorFactory} to append. Lifetime of this
     * {@link ConnectionAcceptorFactory} is managed by this builder and the server started thereof.
     * @return {@code this}
     * @deprecated use {@link #appendLateConnectionAcceptor(LateConnectionAcceptor)} instead.
     */
    @Deprecated // FIXME 0.43 - remove deprecated method
    default HttpServerBuilder appendConnectionAcceptorFilter(ConnectionAcceptorFactory factory) {
        throw new UnsupportedOperationException("This method should not be implemented - consider " +
                "using appendLateConnectionAcceptor instead");
    }

    /**
     * Appends the {@link EarlyConnectionAcceptor} to be called when a new connection has been created.
     * <p>
     * The difference between the {@link EarlyConnectionAcceptor} and the {@link LateConnectionAcceptor} is that the
     * early one is called right after the connection has been accepted - and most importantly - before any TLS
     * handshake has been performed. This allows to terminate connections quickly and spend less CPU resources if the
     * amount of information provided to make such a decision is sufficient.
     * <p>
     * The order of execution of these acceptors are in order of append. If 3 acceptors are added as follows:
     * <pre>
     *     builder
     *          .appendEarlyConnectionAcceptor(acceptor1)
     *          .appendEarlyConnectionAcceptor(acceptor2)
     *          .appendEarlyConnectionAcceptor(acceptor3)
     * </pre>
     * the order of invocation of these filters will be:
     * <pre>
     *     acceptor1 ⇒ acceptor2 ⇒ acceptor3
     * </pre>
     * <p>
     * The acceptor is offloaded by default. If an acceptor in the chain fails the {@link Completable}, the later ones
     * will not be called.
     *
     * @param acceptor the acceptor to append to the chain of acceptors.
     * @return this {@link HttpServerBuilder} for chaining purposes.
     * @see #appendLateConnectionAcceptor(LateConnectionAcceptor)
     */
    default HttpServerBuilder appendEarlyConnectionAcceptor(EarlyConnectionAcceptor acceptor) {
        throw new UnsupportedOperationException("appendEarlyConnectionAcceptor is not supported by " + getClass());
    }

    /**
     * Appends the {@link LateConnectionAcceptor} to be called when a new connection has been created.
     * <p>
     * The {@link LateConnectionAcceptor} (compared to the {@link EarlyConnectionAcceptor}) gets called later in the
     * connection establishment process. Instead of being invoked right after the connection has been accepted, this
     * acceptor gets called after the connection is fully initialized, the TLS handshake has been completed and as a
     * result has more contextual information available in the {@link ConnectionInfo}.
     * <p>
     * The order of execution of these acceptors are in order of append. If 3 acceptors are added as follows:
     * <pre>
     *     builder
     *          .appendLateConnectionAcceptor(acceptor1)
     *          .appendLateConnectionAcceptor(acceptor2)
     *          .appendLateConnectionAcceptor(acceptor3)
     * </pre>
     * the order of invocation of these filters will be:
     * <pre>
     *     acceptor1 ⇒ acceptor2 ⇒ acceptor3
     * </pre>
     * <p>
     * The acceptor is offloaded by default. If an acceptor in the chain fails the {@link Completable}, the later ones
     * will not be called.
     *
     * @param acceptor the acceptor to append to the chain of acceptors.
     * @return this {@link HttpServerBuilder} for chaining purposes.
     * @see #appendEarlyConnectionAcceptor(EarlyConnectionAcceptor)
     */
    default HttpServerBuilder appendLateConnectionAcceptor(LateConnectionAcceptor acceptor) {
        throw new UnsupportedOperationException("appendLateConnectionAcceptor is not supported by " + getClass());
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
     *     builder
     *         .appendServiceFilter(filter1)
     *         .appendNonOffloadingServiceFilter(filter2)
     *         .appendServiceFilter(filter3)
     * </pre>
     * accepting a request by a service wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter2 ⇒ [offloading] ⇒ filter1 ⇒ filter3 ⇒ service
     * </pre>
     *
     * @param factory {@link StreamingHttpServiceFilterFactory} to append.
     * @return {@code this}
     * @throws IllegalArgumentException if the provided filter requires offloading.
     */
    HttpServerBuilder appendNonOffloadingServiceFilter(StreamingHttpServiceFilterFactory factory);

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
     *     builder
     *         .appendServiceFilter(filter1)
     *         .appendNonOffloadingServiceFilter(filter2)
     *         .appendServiceFilter(filter3)
     * </pre>
     * accepting a request by a service wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter2 ⇒ [offloading] ⇒ filter1 ⇒ filter3 ⇒ service
     * </pre>
     *
     * @param predicate the {@link Predicate} to test if the filter must be applied. This must not block.
     * @param factory {@link StreamingHttpServiceFilterFactory} to append.
     * @return {@code this}
     * @throws IllegalArgumentException if the provided filter or predicate requires offloading.
     */
    HttpServerBuilder appendNonOffloadingServiceFilter(Predicate<StreamingHttpRequest> predicate,
                                                       StreamingHttpServiceFilterFactory factory);

    /**
     * Appends the filter to the chain of filters used to decorate the {@link StreamingHttpService} used by this
     * builder.
     * <p>
     * Note this method will be used to decorate the {@link StreamingHttpService} passed to
     * {@link #listenStreaming(StreamingHttpService)} before it is used by the server.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.appendServiceFilter(filter1).appendServiceFilter(filter2).appendServiceFilter(filter3)
     * </pre>
     * accepting a request by a service wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *      filter1 ⇒ filter2 ⇒ filter3 ⇒ service
     * </pre>
     *
     * @param factory {@link StreamingHttpServiceFilterFactory} to append.
     * @return {@code this}
     */
    HttpServerBuilder appendServiceFilter(StreamingHttpServiceFilterFactory factory);

    /**
     * Appends the filter to the chain of filters used to decorate the {@link StreamingHttpService} used by this
     * builder, for every request that passes the provided {@link Predicate}.
     * <p>
     * Note this method will be used to decorate the {@link StreamingHttpService} passed to
     * {@link #listenStreaming(StreamingHttpService)} before it is used by the server.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.appendServiceFilter(filter1).appendServiceFilter(filter2).appendServiceFilter(filter3)
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
    HttpServerBuilder appendServiceFilter(Predicate<StreamingHttpRequest> predicate,
                                          StreamingHttpServiceFilterFactory factory);

    /**
     * Sets the {@link IoExecutor} to be used by this server.
     *
     * @param ioExecutor {@link IoExecutor} to use.
     * @return {@code this}.
     */
    HttpServerBuilder ioExecutor(IoExecutor ioExecutor);

    /**
     * Sets the {@link Executor} to be used by this server.
     *
     * @param executor {@link Executor} to use.
     * @return {@code this}.
     */
    HttpServerBuilder executor(Executor executor);

    /**
     * Sets the {@link BufferAllocator} to be used by this server.
     *
     * @param allocator {@link BufferAllocator} to use.
     * @return {@code this}.
     */
    HttpServerBuilder bufferAllocator(BufferAllocator allocator);

    /**
     * Sets the {@link HttpExecutionStrategy} to be used by this server.
     *
     * @param strategy {@link HttpExecutionStrategy} to use by this server.
     * @return {@code this}.
     */
    HttpServerBuilder executionStrategy(HttpExecutionStrategy strategy);

    /**
     * Starts this server and returns the {@link HttpServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (e.g. TCP) supports it this will result in a socket bind/listen on {@code address}.
     * <p>
     * Note that this method is generic in the sense that it accepts all HTTP {@link HttpServiceBase} implementations
     * to be passed in, namely {@link StreamingHttpService}, {@link HttpService}, {@link BlockingStreamingHttpService}
     * and {@link BlockingHttpService}. It is especially useful when Dependency Injection is used and the type of
     * service is not known at compile time.
     *
     * @param service Service invoked for every request received by this server. The returned {@link HttpServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link HttpServerContext} is closed.
     * @return A {@link HttpServerContext} by blocking the calling thread until the server is successfully started or
     * throws an {@link Exception} if the server could not be started.
     * @throws IllegalArgumentException if an unsupported {@link HttpServiceBase} type is being provided.
     * @throws Exception if the server could not be started.
     */
    default HttpServerContext listenServiceAndAwait(HttpServiceBase service) throws Exception {
        return blockingInvocation(listenService(service));
    }

    /**
     * Starts this server and returns the {@link HttpServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (e.g. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param service Service invoked for every request received by this server. The returned {@link HttpServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link HttpServerContext} is closed.
     * @return A {@link HttpServerContext} by blocking the calling thread until the server is successfully started or
     * throws an {@link Exception} if the server could not be started.
     * @throws Exception if the server could not be started.
     */
    default HttpServerContext listenAndAwait(HttpService service) throws Exception {
        return blockingInvocation(listen(service));
    }

    /**
     * Starts this server and returns the {@link HttpServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (e.g. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param service Service invoked for every request received by this server. The returned {@link HttpServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link HttpServerContext} is closed.
     * @return A {@link HttpServerContext} by blocking the calling thread until the server is successfully started or
     * throws an {@link Exception} if the server could not be started.
     * @throws Exception if the server could not be started.
     */
    default HttpServerContext listenStreamingAndAwait(StreamingHttpService service) throws Exception {
        return blockingInvocation(listenStreaming(service));
    }

    /**
     * Starts this server and returns the {@link HttpServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (e.g. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param service Service invoked for every request received by this server. The returned {@link HttpServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link HttpServerContext} is closed.
     * @return A {@link HttpServerContext} by blocking the calling thread until the server is successfully started or
     * throws an {@link Exception} if the server could not be started.
     * @throws Exception if the server could not be started.
     */
    default HttpServerContext listenBlockingAndAwait(BlockingHttpService service) throws Exception {
        return blockingInvocation(listenBlocking(service));
    }

    /**
     * Starts this server and returns the {@link HttpServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (e.g. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param service Service invoked for every request received by this server. The returned {@link HttpServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link HttpServerContext} is closed.
     * @return A {@link HttpServerContext} by blocking the calling thread until the server is successfully started or
     * throws an {@link Exception} if the server could not be started.
     * @throws Exception if the server could not be started.
     */
    default HttpServerContext listenBlockingStreamingAndAwait(BlockingStreamingHttpService service) throws Exception {
        return blockingInvocation(listenBlockingStreaming(service));
    }

    /**
     * Starts this server and returns the {@link HttpServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (e.g. TCP) supports it this will result in a socket bind/listen on {@code address}.
     * <p>
     * Note that this method is generic in the sense that it accepts all HTTP {@link HttpServiceBase} implementations
     * to be passed in, namely {@link StreamingHttpService}, {@link HttpService}, {@link BlockingStreamingHttpService}
     * and {@link BlockingHttpService}. It is especially useful when Dependency Injection is used and the type of
     * service is not known at compile time.
     *
     * @param service Service invoked for every request received by this server. The returned {@link HttpServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link HttpServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     * @throws IllegalArgumentException if an unsupported {@link HttpServiceBase} type is being provided.
     */
    default Single<HttpServerContext> listenService(final HttpServiceBase service) {
        if (service instanceof HttpService) {
            return listen((HttpService) service);
        } else if (service instanceof StreamingHttpService) {
            return listenStreaming((StreamingHttpService) service);
        } else if (service instanceof BlockingHttpService) {
            return listenBlocking((BlockingHttpService) service);
        } else if (service instanceof BlockingStreamingHttpService) {
            return listenBlockingStreaming((BlockingStreamingHttpService) service);
        } else {
            return Single.failed(new IllegalArgumentException("Unsupported service type: " + service));
        }
    }

    /**
     * Starts this server and returns the {@link HttpServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (e.g. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param service Service invoked for every request received by this server. The returned {@link HttpServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link HttpServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    Single<HttpServerContext> listen(HttpService service);

    /**
     * Starts this server and returns the {@link HttpServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (e.g. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param service Service invoked for every request received by this server. The returned {@link HttpServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link HttpServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    Single<HttpServerContext> listenStreaming(StreamingHttpService service);

    /**
     * Starts this server and returns the {@link HttpServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (e.g. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param service Service invoked for every request received by this server. The returned {@link HttpServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link HttpServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    Single<HttpServerContext> listenBlocking(BlockingHttpService service);

    /**
     * Starts this server and returns the {@link HttpServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (e.g. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param service Service invoked for every request received by this server. The returned {@link HttpServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link HttpServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    Single<HttpServerContext> listenBlockingStreaming(BlockingStreamingHttpService service);
}
