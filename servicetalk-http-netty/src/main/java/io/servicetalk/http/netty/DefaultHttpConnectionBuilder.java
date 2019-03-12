/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpConnectionBuilder;
import io.servicetalk.http.api.HttpConnectionFilterFactory;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpClientConfig;
import io.servicetalk.tcp.netty.internal.TcpClientChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.tcp.netty.internal.TcpConnector;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.transport.netty.internal.ExecutionContextBuilder;
import io.servicetalk.transport.netty.internal.NettyConnection;
import io.servicetalk.transport.netty.internal.NettyConnection.TerminalPredicate;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.CloseHandler.forPipelinedRequestResponse;
import static java.util.Objects.requireNonNull;

/**
 * A builder for instances of {@link HttpConnectionBuilder}.
 *
 * @param <ResolvedAddress> the type of address after resolution.
 */
public final class DefaultHttpConnectionBuilder<ResolvedAddress> extends HttpConnectionBuilder<ResolvedAddress> {

    private static final Predicate<Object> LAST_CHUNK_PREDICATE = p -> p instanceof HttpHeaders;

    private final HttpClientConfig config;
    private final ExecutionContextBuilder executionContextBuilder = new ExecutionContextBuilder();
    private HttpConnectionFilterFactory connectionFilterFunction = HttpConnectionFilterFactory.identity();

    /**
     * Create a new builder.
     */
    public DefaultHttpConnectionBuilder() {
        config = new HttpClientConfig(new TcpClientConfig(false));
    }

    @Override
    public DefaultHttpConnectionBuilder<ResolvedAddress> ioExecutor(final IoExecutor ioExecutor) {
        executionContextBuilder.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public DefaultHttpConnectionBuilder<ResolvedAddress> bufferAllocator(final BufferAllocator allocator) {
        executionContextBuilder.bufferAllocator(allocator);
        return this;
    }

    @Override
    public Single<StreamingHttpConnection> buildStreaming(final ResolvedAddress resolvedAddress) {
        ReadOnlyHttpClientConfig roConfig = config.asReadOnly();
        HttpExecutionStrategy strategy = executionStrategy();
        Executor executor = strategy.executor();
        if (executor != null) {
            executionContextBuilder.executor(executor);
        }
        ExecutionContext executionContext = executionContextBuilder.build();
        final StreamingHttpRequestResponseFactory reqRespFactory =
                new DefaultStreamingHttpRequestResponseFactory(executionContext.bufferAllocator(),
                        roConfig.headersFactory());

        // Make a best effort to infer HOST header for HttpConnection
        HttpConnectionFilterFactory filterFactory;
        if (resolvedAddress instanceof InetSocketAddress) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) resolvedAddress;
            filterFactory = connectionFilterFunction.append(
                    new HostHeaderHttpRequesterFilter(HostAndPort.of(inetSocketAddress)));
        } else {
            filterFactory = connectionFilterFunction;
        }
        filterFactory = filterFactory.append(
                new ConcurrentRequestsHttpConnectionFilter(roConfig.maxPipelinedRequests()));

        return (roConfig.maxPipelinedRequests() == 1 ?
                buildForNonPipelined(executionContext, resolvedAddress, roConfig, filterFactory, reqRespFactory) :
                buildForPipelined(executionContext, resolvedAddress, roConfig, filterFactory, reqRespFactory))
                    .map(filterChain ->
                        StreamingHttpConnection.newStreamingConnectionWorkAroundToBeFixed(filterChain, strategy));
    }

    static <ResolvedAddress> Single<StreamingHttpConnectionFilter> buildForPipelined(
            final ExecutionContext executionContext, ResolvedAddress resolvedAddress, ReadOnlyHttpClientConfig roConfig,
            final HttpConnectionFilterFactory connectionFilterFunction,
            final StreamingHttpRequestResponseFactory reqRespFactory) {
        return buildStreaming(executionContext, resolvedAddress, roConfig).map(conn ->
                connectionFilterFunction.create(
                        new PipelinedStreamingHttpConnectionFilter(conn, roConfig, executionContext, reqRespFactory)));
    }

    static <ResolvedAddress> Single<StreamingHttpConnectionFilter> buildForNonPipelined(
            final ExecutionContext executionContext, ResolvedAddress resolvedAddress, ReadOnlyHttpClientConfig roConfig,
            final HttpConnectionFilterFactory connectionFilterFunction,
            final StreamingHttpRequestResponseFactory reqRespFactory) {
        return buildStreaming(executionContext, resolvedAddress, roConfig).map(conn ->
                connectionFilterFunction.create(
                    new NonPipelinedStreamingHttpConnectionFilter(conn, roConfig, executionContext, reqRespFactory)));
    }

    private static <ResolvedAddress> Single<? extends NettyConnection<Object, Object>> buildStreaming(
            final ExecutionContext executionContext, ResolvedAddress resolvedAddress,
            ReadOnlyHttpClientConfig roConfig) {
        // This state is read only, so safe to keep a copy across Subscribers
        final ReadOnlyTcpClientConfig roTcpClientConfig = roConfig.tcpClientConfig();
        return TcpConnector.connect(null, resolvedAddress, roTcpClientConfig, executionContext)
                .flatMap(channel -> {
                    CloseHandler closeHandler = forPipelinedRequestResponse(true, channel.config());
                    return DefaultNettyConnection.initChannel(channel, executionContext.bufferAllocator(),
                            executionContext.executor(), new TerminalPredicate<>(LAST_CHUNK_PREDICATE), closeHandler,
                            roTcpClientConfig.flushStrategy(), new TcpClientChannelInitializer(
                                    roConfig.tcpClientConfig()).andThen(new HttpClientChannelInitializer(roConfig,
                                    closeHandler)));
                });
    }

    /**
     * Enable SSL/TLS using the provided {@link SslConfig}. To disable it pass in {@code null}.
     *
     * @param sslConfig the {@link SslConfig}.
     * @return this.
     * @throws IllegalStateException if the {@link SslConfig#keyCertChainSupplier()}, {@link SslConfig#keySupplier()},
     * or {@link SslConfig#trustCertChainSupplier()}
     * throws when {@link InputStream#close()} is called.
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> sslConfig(@Nullable final SslConfig sslConfig) {
        config.tcpClientConfig().sslConfig(sslConfig);
        return this;
    }

    /**
     * Add a {@link SocketOption} for all connections created by this client.
     *
     * @param <T> the type of the value.
     * @param option the option to apply.
     * @param value the value.
     * @return this.
     */
    public <T> DefaultHttpConnectionBuilder<ResolvedAddress> socketOption(final SocketOption<T> option, T value) {
        config.tcpClientConfig().socketOption(option, value);
        return this;
    }

    /**
     * Enable wire-logging for connections created by this builder. All wire events will be logged at trace level.
     *
     * @param loggerName The name of the logger to log wire events.
     * @return {@code this}.
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> enableWireLogging(final String loggerName) {
        config.tcpClientConfig().enableWireLogging(loggerName);
        return this;
    }

    /**
     * Disable previously configured wire-logging for connections created by this builder.
     * If wire-logging has not been configured before, this method has no effect.
     *
     * @return {@code this}.
     * @see #enableWireLogging(String)
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> disableWireLogging() {
        config.tcpClientConfig().disableWireLogging();
        return this;
    }

    /**
     * Set the {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding responses.
     *
     * @param headersFactory the {@link HttpHeadersFactory} to use.
     * @return {@code this}.
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> headersFactory(final HttpHeadersFactory headersFactory) {
        config.headersFactory(headersFactory);
        return this;
    }

    /**
     * Set the maximum size of the initial HTTP line for created {@link StreamingHttpClient}.
     *
     * @param maxInitialLineLength The {@link StreamingHttpClient} will throw TooLongFrameException if the initial HTTP
     * line exceeds this length.
     * @return {@code this}.
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> maxInitialLineLength(final int maxInitialLineLength) {
        config.maxInitialLineLength(maxInitialLineLength);
        return this;
    }

    /**
     * Set the maximum total size of HTTP headers, which could be send be created {@link StreamingHttpClient}.
     *
     * @param maxHeaderSize The {@link StreamingHttpClient} will throw TooLongFrameException if the total size of all
     * HTTP headers exceeds this length.
     * @return {@code this}.
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> maxHeaderSize(final int maxHeaderSize) {
        config.maxHeaderSize(maxHeaderSize);
        return this;
    }

    /**
     * Set the value used to calculate an exponential moving average of the encoded size of the initial line and the
     * headers for a guess for future buffer allocations.
     *
     * @param headersEncodedSizeEstimate An estimated size of encoded initial line and headers.
     * @return {@code this}.
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> headersEncodedSizeEstimate(
            final int headersEncodedSizeEstimate) {
        config.headersEncodedSizeEstimate(headersEncodedSizeEstimate);
        return this;
    }

    /**
     * Set the value used to calculate an exponential moving average of the encoded size of the trailers for a guess for
     * future buffer allocations.
     *
     * @param trailersEncodedSizeEstimate An estimated size of encoded trailers.
     * @return {@code this}.
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> trailersEncodedSizeEstimate(
            final int trailersEncodedSizeEstimate) {
        config.trailersEncodedSizeEstimate(trailersEncodedSizeEstimate);
        return this;
    }

    /**
     * Set the maximum number of pipelined HTTP requests to queue up, anything above this will be rejected,
     * 1 means pipelining is disabled and requests and responses are processed sequentially.
     * <p>
     * Request pipelining requires HTTP 1.1.
     *
     * @param maxPipelinedRequests number of pipelined requests to queue up
     * @return {@code this}.
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> maxPipelinedRequests(final int maxPipelinedRequests) {
        config.maxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    @Override
    public DefaultHttpConnectionBuilder<ResolvedAddress> appendConnectionFilter(
            final HttpConnectionFilterFactory function) {
        connectionFilterFunction = connectionFilterFunction.append(requireNonNull(function));
        return this;
    }

    @Override
    public DefaultHttpConnectionBuilder<ResolvedAddress> appendConnectionFilter(
            final Predicate<StreamingHttpRequest> predicate, final HttpConnectionFilterFactory factory) {
        super.appendConnectionFilter(predicate, factory);
        return this;
    }
}
