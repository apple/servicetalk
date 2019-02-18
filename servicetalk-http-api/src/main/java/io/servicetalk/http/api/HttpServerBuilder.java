/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.ConnectionAcceptorFilter;
import io.servicetalk.transport.api.ConnectionAcceptorFilterFactory;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.SslConfig;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static io.servicetalk.http.api.HttpServiceFilterFactory.identity;
import static io.servicetalk.transport.api.ConnectionAcceptor.ACCEPT_ALL;
import static java.util.Objects.requireNonNull;

/**
 * A builder for building HTTP Servers.
 */
public abstract class HttpServerBuilder {

    private ConnectionAcceptorFilterFactory connectionAcceptorFilterFactory =
            ConnectionAcceptorFilterFactory.identity();
    private HttpServiceFilterFactory serviceFilter = identity();

    /**
     * Sets the {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding requests.
     *
     * @param headersFactory the {@link HttpHeadersFactory} to use.
     * @return this
     */
    public abstract HttpServerBuilder headersFactory(HttpHeadersFactory headersFactory);

    /**
     * Set how long to wait (in milliseconds) for a client to close the connection (if no keep-alive is set) before the
     * server will close the connection.
     *
     * @param clientCloseTimeoutMs {@code 0} if the server should close the connection immediately, or
     * {@code > 0} if a wait time should be used.
     * @return this
     */
    public abstract HttpServerBuilder clientCloseTimeout(long clientCloseTimeoutMs);

    /**
     * The server will throw {@link Exception} if the initial HTTP line exceeds this length.
     *
     * @param maxInitialLineLength The server will throw {@link Exception} if the initial HTTP line exceeds this
     * length.
     * @return this.
     */
    public abstract HttpServerBuilder maxInitialLineLength(int maxInitialLineLength);

    /**
     * The server will throw {@link Exception} if the total size of all HTTP headers exceeds this length.
     *
     * @param maxHeaderSize The server will throw {@link Exception} if the total size of all HTTP headers exceeds
     * this length.
     * @return this.
     */
    public abstract HttpServerBuilder maxHeaderSize(int maxHeaderSize);

    /**
     * Used to calculate an exponential moving average of the encoded size of the initial line and the headers for a
     * guess for future buffer allocations.
     *
     * @param headersEncodedSizeEstimate estimated initial value.
     * @return this
     */
    public abstract HttpServerBuilder headersEncodedSizeEstimate(int headersEncodedSizeEstimate);

    /**
     * Used to calculate an exponential moving average of the encoded size of the trailers for a guess for future
     * buffer allocations.
     *
     * @param trailersEncodedSizeEstimate estimated initial value.
     * @return this;
     */
    public abstract HttpServerBuilder trailersEncodedSizeEstimate(int trailersEncodedSizeEstimate);

    /**
     * The maximum queue length for incoming connection indications (a request to connect) is set to the backlog
     * parameter. If a connection indication arrives when the queue is full, the connection may time out.
     *
     * @param backlog the backlog to use when accepting connections.
     * @return this.
     */
    public abstract HttpServerBuilder backlog(int backlog);

    /**
     * Allows to setup SNI.
     * You can either use {@link #sslConfig(SslConfig)} or this method.
     *
     * @param mappings mapping hostnames to the ssl configuration that should be used.
     * @param defaultConfig the configuration to use if no hostnames matched from {@code mappings}.
     * @return this.
     * @throws IllegalStateException if the {@link SslConfig#keyCertChainSupplier()}, {@link SslConfig#keySupplier()},
     * or {@link SslConfig#trustCertChainSupplier()} throws when
     * {@link InputStream#close()} is called.
     */
    public abstract HttpServerBuilder sniConfig(@Nullable Map<String, SslConfig> mappings, SslConfig defaultConfig);

    /**
     * Enable SSL/TLS using the provided {@link SslConfig}. To disable it pass in {@code null}.
     *
     * @param sslConfig the {@link SslConfig}.
     * @return this.
     * @throws IllegalStateException if the {@link SslConfig#keyCertChainSupplier()}, {@link SslConfig#keySupplier()},
     * or {@link SslConfig#trustCertChainSupplier()} throws when
     * {@link InputStream#close()} is called.
     */
    public abstract HttpServerBuilder sslConfig(@Nullable SslConfig sslConfig);

    /**
     * Add a {@link SocketOption} that is applied.
     *
     * @param <T> the type of the value.
     * @param option the option to apply.
     * @param value the value.
     * @return this.
     */
    public abstract <T> HttpServerBuilder socketOption(SocketOption<T> option, T value);

    /**
     * Enable wire-logging for this server. All wire events will be logged at trace level.
     *
     * @param loggerName The name of the logger to log wire events.
     * @return {@code this}.
     */
    public abstract HttpServerBuilder enableWireLogging(String loggerName);

    /**
     * Disable previously configured wire-logging for this server.
     * If wire-logging has not been configured before, this method has no effect.
     *
     * @return {@code this}.
     * @see #enableWireLogging(String)
     */
    public abstract HttpServerBuilder disableWireLogging();

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
     *     filter1 =&gt; filter2 =&gt; filter3
     * </pre>
     * @param factory {@link ConnectionAcceptorFilterFactory} to append. Lifetime of this
     * {@link ConnectionAcceptorFilterFactory} is managed by this builder and the server started thereof.
     * @return {@code this}
     */
    public final HttpServerBuilder appendConnectionAcceptorFilter(final ConnectionAcceptorFilterFactory factory) {
        this.connectionAcceptorFilterFactory = connectionAcceptorFilterFactory.append(factory);
        return this;
    }

    /**
     * Append the filter to the chain of filters used to decorate the {@link StreamingHttpService} used by this
     * builder.
     * <p>
     * Note this method will be used to decorate the {@link StreamingHttpRequestHandler} passed to
     * {@link #listenStreaming(StreamingHttpRequestHandler)} before it is used by the server.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * accepting a request by a service wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; service
     * </pre>
     * @param factory {@link HttpServiceFilterFactory} to append.
     * @return {@code this}
     */
    public final HttpServerBuilder appendServiceFilter(final HttpServiceFilterFactory factory) {
        serviceFilter = serviceFilter.append(factory);
        return this;
    }

    /**
     * Append the filter to the chain of filters used to decorate the {@link StreamingHttpService} used by this builder,
     * for every request that passes the provided {@link Predicate}.
     * <p>
     * Note this method will be used to decorate the {@link StreamingHttpRequestHandler} passed to
     * {@link #listenStreaming(StreamingHttpRequestHandler)} before it is used by the server.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * accepting a request by a service wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; service
     * </pre>
     * @param predicate the {@link Predicate} to test if the filter must be applied.
     * @param factory {@link HttpServiceFilterFactory} to append.
     * @return {@code this}
     */
    public final HttpServerBuilder appendServiceFilter(final Predicate<StreamingHttpRequest> predicate,
                                                       final HttpServiceFilterFactory factory) {
        requireNonNull(predicate);
        requireNonNull(factory);

        return appendServiceFilter(service ->
                new ConditionalHttpServiceFilter(predicate, factory.create(service), service));
    }

    /**
     * Append the filter to the chain of filters used to decorate the {@link StreamingHttpService} used by this
     * builder.
     * <p>
     * Note this method will be used to decorate the {@link StreamingHttpRequestHandler} passed to
     * {@link #listenStreaming(StreamingHttpRequestHandler)} before it is used by the server.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * accepting a request by a service wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; service
     * </pre>
     * @param factory {@link HttpRequestHandlerFilterFactory} to append.
     * @return {@code this}
     */
    public final HttpServerBuilder appendRequestHandlerFilter(final HttpRequestHandlerFilterFactory factory) {
        return appendServiceFilter(factory.asServiceFilterFactory());
    }

    /**
     * Conditionally append the filter to the chain of filters used to decorate the {@link StreamingHttpService} used by
     * this builder.
     * <p>
     * Note this method will be used to decorate the {@link StreamingHttpRequestHandler} passed to
     * {@link #listenStreaming(StreamingHttpRequestHandler)} before it is used by the server.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * accepting a request by a service wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; service
     * </pre>
     * @param predicate the {@link Predicate} to test if the filter must be applied.
     * @param factory {@link HttpRequestHandlerFilterFactory} to append.
     * @return {@code this}
     */
    public final HttpServerBuilder appendRequestHandlerFilter(final Predicate<StreamingHttpRequest> predicate,
                                                              final HttpRequestHandlerFilterFactory factory) {
        return appendServiceFilter(predicate, factory.asServiceFilterFactory());
    }

    /**
     * Sets the address to listen on.
     *
     * @param address The listen address for the server.
     * @return {@code this}.
     * @see #port(int)
     */
    public abstract HttpServerBuilder address(SocketAddress address);

    /**
     * Sets the port to listen on, the IP address is the wildcard address.
     *
     * @param port The listen port for the server
     * @return {@code this}.
     * @see #address(SocketAddress)
     */
    public HttpServerBuilder port(int port) {
        return address(new InetSocketAddress(port));
    }

    /**
     * Sets the {@link IoExecutor} to be used by this server.
     *
     * @param ioExecutor {@link IoExecutor} to use.
     * @return {@code this}.
     */
    public abstract HttpServerBuilder ioExecutor(IoExecutor ioExecutor);

    /**
     * Sets the {@link BufferAllocator} to be used by this server.
     *
     * @param allocator {@link BufferAllocator} to use.
     * @return {@code this}.
     */
    public abstract HttpServerBuilder bufferAllocator(BufferAllocator allocator);

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param handler Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} by blocking the calling thread until the server is successfully started or
     * throws an {@link Exception} if the server could not be started.
     * @throws Exception if the server could not be started.
     */
    public final ServerContext listenAndAwait(final HttpRequestHandler handler) throws Exception {
        return blockingInvocation(listen(handler));
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param handler Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} by blocking the calling thread until the server is successfully started or
     * throws an {@link Exception} if the server could not be started.
     * @throws Exception if the server could not be started.
     */
    public final ServerContext listenStreamingAndAwait(final StreamingHttpRequestHandler handler) throws Exception {
        return blockingInvocation(listenStreaming(handler));
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param handler Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} by blocking the calling thread until the server is successfully started or
     * throws an {@link Exception} if the server could not be started.
     * @throws Exception if the server could not be started.
     */
    public final ServerContext listenBlockingAndAwait(final BlockingHttpRequestHandler handler) throws Exception {
        return blockingInvocation(listenBlocking(handler));
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param handler Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link ServerContext} by blocking the calling thread until the server is successfully started or
     * throws an {@link Exception} if the server could not be started.
     * @throws Exception if the server could not be started.
     */
    public final ServerContext listenBlockingStreamingAndAwait(
            final BlockingStreamingHttpRequestHandler handler) throws Exception {
        return blockingInvocation(listenBlockingStreaming(handler));
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param handler Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    public final Single<ServerContext> listen(final HttpRequestHandler handler) {
        return listenStreaming(handler.asService().asStreamingService());
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param handler Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    public final Single<ServerContext> listenStreaming(final StreamingHttpRequestHandler handler) {
        ConnectionAcceptorFilter connectionAcceptorFilter = connectionAcceptorFilterFactory.create(ACCEPT_ALL);
        StreamingHttpService svc = handler.asStreamingService();
        HttpExecutionStrategy strategy = svc.executionStrategy();
        StreamingHttpServiceFilter filterChain = serviceFilter.create(svc);
        HttpExecutionStrategy effectiveStrategy = filterChain.effectiveExecutionStrategy(strategy);
        StreamingHttpServiceFilter finalService = new StreamingHttpServiceFilter(filterChain, effectiveStrategy);
        return doListen(connectionAcceptorFilter, finalService);
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param handler Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    public final Single<ServerContext> listenBlocking(final BlockingHttpRequestHandler handler) {
        return listenStreaming(handler.asBlockingService().asStreamingService());
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this will result in a socket bind/listen on {@code address}.
     *
     * @param handler Service invoked for every request received by this server. The returned {@link ServerContext}
     * manages the lifecycle of the {@code service}, ensuring it is closed when the {@link ServerContext} is closed.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    public final Single<ServerContext> listenBlockingStreaming(final BlockingStreamingHttpRequestHandler handler) {
        return listenStreaming(handler.asBlockingStreamingService().asStreamingService());
    }

    /**
     * Starts this server and returns the {@link ServerContext} after the server has been successfully started.
     * <p>
     * If the underlying protocol (eg. TCP) supports it this should result in a socket bind/listen on {@code address}.
     *
     * @param connectionAcceptorFilter {@link ConnectionAcceptorFilter} to use for the server.
     * @param service {@link StreamingHttpService} to use for the server.
     * @return A {@link Single} that completes when the server is successfully started or terminates with an error if
     * the server could not be started.
     */
    protected abstract Single<ServerContext> doListen(ConnectionAcceptorFilter connectionAcceptorFilter,
                                                      StreamingHttpService service);
}
