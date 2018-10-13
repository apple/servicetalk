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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpConnectionBuilder;
import io.servicetalk.http.api.HttpConnectionFilterFactory;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.tcp.netty.internal.TcpClientChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.tcp.netty.internal.TcpConnector;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.NettyConnection;

import java.io.InputStream;
import java.net.SocketOption;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.CloseHandler.forPipelinedRequestResponse;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static java.util.Objects.requireNonNull;

/**
 * A builder for instances of {@link HttpConnectionBuilder}.
 *
 * @param <ResolvedAddress> the type of address after resolution.
 */
public final class DefaultHttpConnectionBuilder<ResolvedAddress> implements HttpConnectionBuilder<ResolvedAddress> {

    private static final Predicate<Object> LAST_CHUNK_PREDICATE = p -> p instanceof HttpHeaders;

    private final HttpClientConfig config;
    private HttpConnectionFilterFactory connectionFilterFunction = HttpConnectionFilterFactory.identity();
    private ExecutionContext executionContext = globalExecutionContext();

    /**
     * Create a new builder.
     */
    public DefaultHttpConnectionBuilder() {
        config = new HttpClientConfig(new TcpClientConfig(false));
    }

    private static Predicate<Object> getLastChunkPredicate() {
        return LAST_CHUNK_PREDICATE;
    }

    @Override
    public DefaultHttpConnectionBuilder<ResolvedAddress> executionContext(final ExecutionContext context) {
        executionContext = requireNonNull(context);
        return this;
    }

    @Override
    public Single<StreamingHttpConnection> buildStreaming(final ResolvedAddress resolvedAddress) {
        ReadOnlyHttpClientConfig roConfig = config.asReadOnly();

        final StreamingHttpRequestResponseFactory reqRespFactory =
                new DefaultStreamingHttpRequestResponseFactory(executionContext.bufferAllocator(),
                        roConfig.getHeadersFactory());

        return (roConfig.getMaxPipelinedRequests() == 1 ?
                buildForNonPipelined(executionContext, resolvedAddress, roConfig, connectionFilterFunction,
                        reqRespFactory)
                : buildForPipelined(executionContext, resolvedAddress, roConfig, connectionFilterFunction,
                reqRespFactory))
                    .map(filteredConnection -> new ConcurrentRequestsHttpConnectionFilter(filteredConnection,
                            roConfig.getMaxPipelinedRequests()));
    }

    static <ResolvedAddress> Single<StreamingHttpConnection> buildForPipelined(
            final ExecutionContext executionContext, ResolvedAddress resolvedAddress, ReadOnlyHttpClientConfig roConfig,
            final HttpConnectionFilterFactory connectionFilterFunction,
            final StreamingHttpRequestResponseFactory reqRespFactory) {
        return buildStreaming(executionContext, resolvedAddress, roConfig, conn ->
                connectionFilterFunction.apply(
                        new PipelinedStreamingHttpConnection(conn, roConfig, executionContext, reqRespFactory)));
    }

    static <ResolvedAddress> Single<StreamingHttpConnection> buildForNonPipelined(
            final ExecutionContext executionContext, ResolvedAddress resolvedAddress, ReadOnlyHttpClientConfig roConfig,
            final HttpConnectionFilterFactory connectionFilterFunction,
            final StreamingHttpRequestResponseFactory reqRespFactory) {
        return buildStreaming(executionContext, resolvedAddress, roConfig, conn ->
                connectionFilterFunction.apply(
                        new NonPipelinedStreamingHttpConnection(conn, roConfig, executionContext, reqRespFactory)));
    }

    private static <ResolvedAddress> Single<StreamingHttpConnection> buildStreaming(
            final ExecutionContext executionContext, ResolvedAddress resolvedAddress, ReadOnlyHttpClientConfig roConfig,
            final Function<NettyConnection<Object, Object>, StreamingHttpConnection> mapper) {
        return new Single<StreamingHttpConnection>() {
            @Override
            protected void handleSubscribe(
                    Subscriber<? super StreamingHttpConnection> subscriber) {

                final CloseHandler closeHandler = forPipelinedRequestResponse(true);
                final ChannelInitializer initializer = new TcpClientChannelInitializer(roConfig.getTcpClientConfig())
                        .andThen(new HttpClientChannelInitializer(roConfig, closeHandler));

                final TcpConnector<Object, Object> connector = new TcpConnector<>(roConfig.getTcpClientConfig(),
                        initializer, DefaultHttpConnectionBuilder::getLastChunkPredicate, null, closeHandler);

                connector.connect(executionContext, resolvedAddress, false).map(mapper).subscribe(subscriber);
            }
        };
    }

    /**
     * Enable SSL/TLS using the provided {@link SslConfig}. To disable it pass in {@code null}.
     *
     * @param sslConfig the {@link SslConfig}.
     * @return this.
     * @throws IllegalStateException if the {@link SslConfig#getKeyCertChainSupplier()},
     * {@link SslConfig#getKeySupplier()}, or {@link SslConfig#getTrustCertChainSupplier()}
     * throws when {@link InputStream#close()} is called.
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> setSslConfig(@Nullable final SslConfig sslConfig) {
        config.getTcpClientConfig().setSslConfig(sslConfig);
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
    public <T> DefaultHttpConnectionBuilder<ResolvedAddress> setSocketOption(final SocketOption<T> option, T value) {
        config.getTcpClientConfig().setSocketOption(option, value);
        return this;
    }

    /**
     * Enable wire-logging for connections created by this builder. All wire events will be logged at trace level.
     *
     * @param loggerName The name of the logger to log wire events.
     * @return {@code this}.
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> enableWireLogging(final String loggerName) {
        config.getTcpClientConfig().enableWireLogging(loggerName);
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
        config.getTcpClientConfig().disableWireLogging();
        return this;
    }

    /**
     * Set the {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding responses.
     *
     * @param headersFactory the {@link HttpHeadersFactory} to use.
     * @return {@code this}.
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> setHeadersFactory(final HttpHeadersFactory headersFactory) {
        config.setHeadersFactory(headersFactory);
        return this;
    }

    /**
     * Set the maximum size of the initial HTTP line for created {@link StreamingHttpClient}.
     *
     * @param maxInitialLineLength The {@link StreamingHttpClient} will throw TooLongFrameException if the initial HTTP
     * line exceeds this length.
     * @return {@code this}.
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> setMaxInitialLineLength(final int maxInitialLineLength) {
        config.setMaxInitialLineLength(maxInitialLineLength);
        return this;
    }

    /**
     * Set the maximum total size of HTTP headers, which could be send be created {@link StreamingHttpClient}.
     *
     * @param maxHeaderSize The {@link StreamingHttpClient} will throw TooLongFrameException if the total size of all HTTP
     * headers exceeds this length.
     * @return {@code this}.
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> setMaxHeaderSize(final int maxHeaderSize) {
        config.setMaxHeaderSize(maxHeaderSize);
        return this;
    }

    /**
     * Set the value used to calculate an exponential moving average of the encoded size of the initial line and the
     * headers for a guess for future buffer allocations.
     *
     * @param headersEncodedSizeEstimate An estimated size of encoded initial line and headers.
     * @return {@code this}.
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> setHeadersEncodedSizeEstimate(
            final int headersEncodedSizeEstimate) {
        config.setHeadersEncodedSizeEstimate(headersEncodedSizeEstimate);
        return this;
    }

    /**
     * Set the value used to calculate an exponential moving average of the encoded size of the trailers for a guess for
     * future buffer allocations.
     *
     * @param trailersEncodedSizeEstimate An estimated size of encoded trailers.
     * @return {@code this}.
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> setTrailersEncodedSizeEstimate(
            final int trailersEncodedSizeEstimate) {
        config.setTrailersEncodedSizeEstimate(trailersEncodedSizeEstimate);
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
    public DefaultHttpConnectionBuilder<ResolvedAddress> setMaxPipelinedRequests(final int maxPipelinedRequests) {
        config.setMaxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    /**
     * Set the filter that is used to decorate {@link StreamingHttpConnection} created by this builder.
     * <p>
     * Note this method will be used to decorate the result of {@link #buildStreaming(Object)} before it is returned to
     * the user.
     *
     * @param function decorates a {@link StreamingHttpConnection} for the purpose of filtering
     * @return {@code this}
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> setConnectionFilterFunction(
            final HttpConnectionFilterFactory function) {
        this.connectionFilterFunction = requireNonNull(function);
        return this;
    }
}
