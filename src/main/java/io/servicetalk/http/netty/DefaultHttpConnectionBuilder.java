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

import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpConnectionBuilder;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.LastHttpPayloadChunk;
import io.servicetalk.tcp.netty.internal.TcpClientChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.tcp.netty.internal.TcpConnector;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.Connection;
import io.servicetalk.transport.netty.internal.NettyIoExecutor;
import io.servicetalk.transport.netty.internal.NettyIoExecutors;

import java.io.InputStream;
import java.net.SocketOption;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A builder for instances of {@link HttpConnectionBuilder}.
 *
 * @param <ResolvedAddress> the type of address after resolution.
 */
public final class DefaultHttpConnectionBuilder<ResolvedAddress>
        implements HttpConnectionBuilder<ResolvedAddress, HttpPayloadChunk, HttpPayloadChunk> {

    private static final Predicate<Object> LAST_CHUNK_PREDICATE = p -> p instanceof LastHttpPayloadChunk;

    private final HttpClientConfig config;

    /**
     * Create a new builder.
     */
    public DefaultHttpConnectionBuilder() {
        this(new HttpClientConfig(new TcpClientConfig(false)));
    }

    /**
     * @param config pre-load the builder with {@link HttpClientConfig} passed on from higher level builders
     */
    DefaultHttpConnectionBuilder(final HttpClientConfig config) {
        this.config = requireNonNull(config);
    }

    private static Predicate<Object> getLastChunkPredicate() {
        return LAST_CHUNK_PREDICATE;
    }

    @Override
    public Single<HttpConnection<HttpPayloadChunk, HttpPayloadChunk>> build(
            final IoExecutor ioExecutor, final Executor executor, final ResolvedAddress resolvedAddress) {

        ReadOnlyHttpClientConfig roConfig = config.asReadOnly();

        NettyIoExecutor nettyIoExecutor = NettyIoExecutors.toNettyIoExecutor(ioExecutor);
        return new Single<HttpConnection<HttpPayloadChunk, HttpPayloadChunk>>() {
            @Override
            protected void handleSubscribe(
                    Subscriber<? super HttpConnection<HttpPayloadChunk, HttpPayloadChunk>> subscriber) {

                final ChannelInitializer initializer = new TcpClientChannelInitializer(roConfig.getTcpClientConfig())
                        .andThen(new HttpClientChannelInitializer(roConfig));

                final TcpConnector<Object, Object> connector = new TcpConnector<>(roConfig.getTcpClientConfig(),
                        initializer, DefaultHttpConnectionBuilder::getLastChunkPredicate);

                connector.connect(nettyIoExecutor, executor, resolvedAddress, false)
                        .map(c -> connectionStrategy(c, roConfig, executor))
                        .subscribe(subscriber);
            }
        };
    }

    private static HttpConnection<HttpPayloadChunk, HttpPayloadChunk> connectionStrategy(
            Connection<Object, Object> connection, ReadOnlyHttpClientConfig config, Executor executor) {

        return config.getMaxPipelinedRequests() != 1 ? new PipelinedHttpConnection(connection, config, executor) :
                new NonPipelinedHttpConnection(connection, config, executor);
    }

    /**
     * Specify the {@link BufferAllocator} to use.
     * @param allocator the {@link BufferAllocator} to use for allocate new buffers.
     * @return this.
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> setAllocator(BufferAllocator allocator) {
        config.getTcpClientConfig().setAllocator(allocator);
        return this;
    }

    BufferAllocator getAllocator() {
        return config.getTcpClientConfig().getAllocator();
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
    public DefaultHttpConnectionBuilder<ResolvedAddress> setSslConfig(@Nullable SslConfig sslConfig) {
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
    public <T> DefaultHttpConnectionBuilder<ResolvedAddress> setOption(SocketOption<T> option, T value) {
        config.getTcpClientConfig().setOption(option, value);
        return this;
    }

    /**
     * Enables wire-logging for this client at debug level.
     *
     * @param loggerName Name of the logger.
     * @return {@code this}.
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> setWireLoggerName(String loggerName) {
        config.getTcpClientConfig().setWireLoggerName(loggerName);
        return this;
    }

    /**
     * Disabled wire-logging for this client at debug level.
     *
     * @return {@code this}.
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> disableWireLog() {
        config.getTcpClientConfig().disableWireLog();
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
     * Set the maximum size of the initial HTTP line for created {@link HttpClient}.
     *
     * @param maxInitialLineLength The {@link HttpClient} will throw TooLongFrameException if the initial HTTP
     * line exceeds this length.
     * @return {@code this}.
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> setMaxInitialLineLength(final int maxInitialLineLength) {
        config.setMaxInitialLineLength(maxInitialLineLength);
        return this;
    }

    /**
     * Set the maximum total size of HTTP headers, which could be send be created {@link HttpClient}.
     *
     * @param maxHeaderSize The {@link HttpClient} will throw TooLongFrameException if the total size of all HTTP
     * headers exceeds this length.
     * @return {@code this}.
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> setMaxHeaderSize(final int maxHeaderSize) {
        config.setMaxHeaderSize(maxHeaderSize);
        return this;
    }

    /**
     * Set the maximum size of HttpContents which will be send by created {@link HttpClient}.
     *
     * @param maxChunkSize A {@link HttpClient} will break contents or chunks whose size exceeds this value into
     * multiple HttpContents whose length is less than maxChunkSize.
     * @return {@code this}.
     */
    public DefaultHttpConnectionBuilder<ResolvedAddress> setMaxChunkSize(final int maxChunkSize) {
        config.setMaxChunkSize(maxChunkSize);
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
}
