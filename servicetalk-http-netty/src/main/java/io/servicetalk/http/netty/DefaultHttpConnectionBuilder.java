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
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpConnectionBuilder;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.StrategyInfluencerChainBuilder;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
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
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
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
    private final StrategyInfluencerChainBuilder influencerChainBuilder;
    private HttpExecutionStrategy strategy = defaultStrategy();
    @Nullable
    private StreamingHttpConnectionFilterFactory connectionFilterFunction;
    @Nullable
    private Function<ResolvedAddress, StreamingHttpConnectionFilterFactory> hostHeaderFilterFactory =
            DefaultHttpConnectionBuilder::defaultHostHeaderFilterFactory;

    /**
     * Create a new builder.
     */
    public DefaultHttpConnectionBuilder() {
        config = new HttpClientConfig(new TcpClientConfig(false));
        influencerChainBuilder = new StrategyInfluencerChainBuilder();
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
    public DefaultHttpConnectionBuilder<ResolvedAddress> executionStrategy(final HttpExecutionStrategy strategy) {
        this.strategy = requireNonNull(strategy);
        return this;
    }

    @Override
    public Single<StreamingHttpConnection> buildStreaming(final ResolvedAddress resolvedAddress) {
        ReadOnlyHttpClientConfig roConfig = config.asReadOnly();
        final HttpExecutionStrategy strategy = this.strategy;
        Executor executor = strategy.executor();
        if (executor != null) {
            executionContextBuilder.executor(executor);
        }
        ExecutionContext executionContext = executionContextBuilder.build();
        final StreamingHttpRequestResponseFactory reqRespFactory =
                new DefaultStreamingHttpRequestResponseFactory(executionContext.bufferAllocator(),
                        roConfig.headersFactory());
        influencerChainBuilder.prepend(strategy::merge);
        HttpExecutionStrategyInfluencer strategyInfluencer = influencerChainBuilder.build();

        StreamingHttpConnectionFilterFactory filterFactory;
        if (connectionFilterFunction != null) {
            if (hostHeaderFilterFactory != null) {
                filterFactory = connectionFilterFunction.append(hostHeaderFilterFactory.apply(resolvedAddress));
            } else {
                filterFactory = connectionFilterFunction;
            }
        } else if (hostHeaderFilterFactory != null) {
            filterFactory = hostHeaderFilterFactory.apply(resolvedAddress);
        } else {
            filterFactory = null;
        }

        final StreamingHttpConnectionFilterFactory finalFilterFactory = filterFactory;
        return (reservedConnectionsPipelineEnabled(roConfig) ?
                buildStreaming(executionContext, resolvedAddress, roConfig).map(conn -> {
                    FilterableStreamingHttpConnection limitedConn = new ConcurrentRequestsHttpConnectionFilter(
                            new PipelinedStreamingHttpConnection(conn, roConfig, executionContext, reqRespFactory
                            ), roConfig.maxPipelinedRequests());
                    return finalFilterFactory == null ? limitedConn : finalFilterFactory.create(limitedConn);
                }) :
                buildStreaming(executionContext, resolvedAddress, roConfig).map(conn -> {
                    FilterableStreamingHttpConnection limitedConn = new ConcurrentRequestsHttpConnectionFilter(
                            new NonPipelinedStreamingHttpConnection(conn, roConfig, executionContext, reqRespFactory
                            ), roConfig.maxPipelinedRequests());
                    return finalFilterFactory == null ? limitedConn : finalFilterFactory.create(limitedConn);
                })
                ).map(conn -> new FilterableConnectionToConnection(conn, strategy, strategyInfluencer));
    }

    // TODO(derek): Temporary, so we can re-enable the ability to create non-pipelined connections for perf testing.
    static boolean reservedConnectionsPipelineEnabled(final ReadOnlyHttpClientConfig roConfig) {
        return roConfig.maxPipelinedRequests() > 1 ||
                Boolean.valueOf(System.getProperty("io.servicetalk.http.netty.reserved.connections.pipeline", "true"));
    }

    static <ResolvedAddress> Single<? extends NettyConnection<Object, Object>> buildStreaming(
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

    @Override
    public <T> DefaultHttpConnectionBuilder<ResolvedAddress> socketOption(final SocketOption<T> option, T value) {
        config.tcpClientConfig().socketOption(option, value);
        return this;
    }

    @Override
    public DefaultHttpConnectionBuilder<ResolvedAddress> enableWireLogging(final String loggerName) {
        config.tcpClientConfig().enableWireLogging(loggerName);
        return this;
    }

    @Override
    public DefaultHttpConnectionBuilder<ResolvedAddress> disableWireLogging() {
        config.tcpClientConfig().disableWireLogging();
        return this;
    }

    @Override
    public DefaultHttpConnectionBuilder<ResolvedAddress> headersFactory(final HttpHeadersFactory headersFactory) {
        config.headersFactory(headersFactory);
        return this;
    }

    @Override
    public DefaultHttpConnectionBuilder<ResolvedAddress> maxInitialLineLength(final int maxInitialLineLength) {
        config.maxInitialLineLength(maxInitialLineLength);
        return this;
    }

    @Override
    public DefaultHttpConnectionBuilder<ResolvedAddress> maxHeaderSize(final int maxHeaderSize) {
        config.maxHeaderSize(maxHeaderSize);
        return this;
    }

    @Override
    public DefaultHttpConnectionBuilder<ResolvedAddress> headersEncodedSizeEstimate(
            final int headersEncodedSizeEstimate) {
        config.headersEncodedSizeEstimate(headersEncodedSizeEstimate);
        return this;
    }

    @Override
    public DefaultHttpConnectionBuilder<ResolvedAddress> trailersEncodedSizeEstimate(
            final int trailersEncodedSizeEstimate) {
        config.trailersEncodedSizeEstimate(trailersEncodedSizeEstimate);
        return this;
    }

    @Override
    public DefaultHttpConnectionBuilder<ResolvedAddress> maxPipelinedRequests(final int maxPipelinedRequests) {
        config.maxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    @Override
    public DefaultHttpConnectionBuilder<ResolvedAddress> disableHostHeaderFallback() {
        hostHeaderFilterFactory = null;
        return this;
    }

    @Override
    public DefaultHttpConnectionBuilder<ResolvedAddress> enableHostHeaderFallback(final CharSequence hostHeader) {
        hostHeaderFilterFactory = __ -> new HostHeaderHttpRequesterFilter(hostHeader);
        return this;
    }

    @Override
    public DefaultHttpConnectionBuilder<ResolvedAddress> appendConnectionFilter(
            final StreamingHttpConnectionFilterFactory factory) {
        if (connectionFilterFunction == null) {
            connectionFilterFunction = requireNonNull(factory);
        } else {
            connectionFilterFunction = connectionFilterFunction.append(requireNonNull(factory));
        }
        influencerChainBuilder.appendIfInfluencer(factory);
        return this;
    }

    @Override
    public DefaultHttpConnectionBuilder<ResolvedAddress> appendConnectionFilter(
            final Predicate<StreamingHttpRequest> predicate, final StreamingHttpConnectionFilterFactory factory) {
        super.appendConnectionFilter(predicate, factory);
        return this;
    }

    private static <R> HostHeaderHttpRequesterFilter defaultHostHeaderFilterFactory(final R address) {
        // Make a best effort to infer HOST header for HttpConnection
        if (address instanceof InetSocketAddress) {
            return new HostHeaderHttpRequesterFilter(HostAndPort.of((InetSocketAddress) address));
        }
        throw new IllegalArgumentException("Unsupported host header address type, provide an override");
    }
}
