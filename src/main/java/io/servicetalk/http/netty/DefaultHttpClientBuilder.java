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
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer.Event;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpClientBuilder;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.SslConfig;

import java.io.InputStream;
import java.net.SocketOption;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

/**
 * A builder for instances of {@link HttpClient}.
 * @param <ResolvedAddress> the type of address after resolution
 */
public final class DefaultHttpClientBuilder<ResolvedAddress>
        implements HttpClientBuilder<ResolvedAddress, Event<ResolvedAddress>, HttpPayloadChunk, HttpPayloadChunk> {

    private final DefaultHttpConnectionBuilder<ResolvedAddress> builder;
    private final LoadBalancerFactory<ResolvedAddress, HttpConnection<HttpPayloadChunk, HttpPayloadChunk>> lbFactory;
    private Function<HttpConnection<HttpPayloadChunk, HttpPayloadChunk>,
            HttpConnection<HttpPayloadChunk, HttpPayloadChunk>> connFilter = identity();

    /**
     * Create a new instance.
     * @param loadBalancerFactory factory of {@link LoadBalancer} objects for {@link HttpConnection}s.
     */
    public DefaultHttpClientBuilder(final LoadBalancerFactory<ResolvedAddress,
            HttpConnection<HttpPayloadChunk, HttpPayloadChunk>> loadBalancerFactory) {
        builder = new DefaultHttpConnectionBuilder<>();
        this.lbFactory = requireNonNull(loadBalancerFactory);
    }

    /**
     * @param loadBalancerFactory factory of {@link LoadBalancer} objects for {@link HttpConnection}s
     * @param config pre-load the builder with {@link HttpClientConfig} passed on from higher level builders
     */
    DefaultHttpClientBuilder(final LoadBalancerFactory<ResolvedAddress,
                             HttpConnection<HttpPayloadChunk, HttpPayloadChunk>> loadBalancerFactory,
                             final HttpClientConfig config) {
        builder = new DefaultHttpConnectionBuilder<>(config);
        this.lbFactory = requireNonNull(loadBalancerFactory);
    }

    @Override
    public HttpClient<HttpPayloadChunk, HttpPayloadChunk> build(final IoExecutor ioExecutor, final Executor executor,
        final Publisher<Event<ResolvedAddress>> addressEventStream) {
        return new DefaultHttpClient<>(ioExecutor, executor, builder.getAllocator(), builder, addressEventStream,
                    connFilter, lbFactory);
    }

    /**
     * Specify the {@link BufferAllocator} to use.
     * @param allocator the {@link BufferAllocator} to use for allocating new buffers.
     * @return this.
     */
    public DefaultHttpClientBuilder<ResolvedAddress> setAllocator(BufferAllocator allocator) {
        builder.setAllocator(allocator);
        return this;
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
    public DefaultHttpClientBuilder<ResolvedAddress> setSslConfig(@Nullable SslConfig sslConfig) {
        builder.setSslConfig(sslConfig);
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
    public <T> DefaultHttpClientBuilder<ResolvedAddress> setOption(SocketOption<T> option, T value) {
        builder.setOption(option, value);
        return this;
    }

    /**
     * Enables wire-logging for this client at debug level.
     *
     * @param loggerName Name of the logger.
     * @return {@code this}.
     */
    public DefaultHttpClientBuilder<ResolvedAddress> setWireLoggerName(String loggerName) {
        builder.setWireLoggerName(loggerName);
        return this;
    }

    /**
     * Disabled wire-logging for this client at debug level.
     *
     * @return {@code this}.
     */
    public DefaultHttpClientBuilder<ResolvedAddress> disableWireLog() {
        builder.disableWireLog();
        return this;
    }

    /**
     * Set the {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding responses.
     *
     * @param headersFactory the {@link HttpHeadersFactory} to use.
     * @return {@code this}.
     */
    public DefaultHttpClientBuilder<ResolvedAddress> setHeadersFactory(final HttpHeadersFactory headersFactory) {
        builder.setHeadersFactory(headersFactory);
        return this;
    }

    /**
     * Set the maximum size of the initial HTTP line for created {@link HttpClient}.
     *
     * @param maxInitialLineLength The {@link HttpClient} will throw TooLongFrameException if the initial HTTP
     * line exceeds this length.
     * @return {@code this}.
     */
    public DefaultHttpClientBuilder<ResolvedAddress> setMaxInitialLineLength(final int maxInitialLineLength) {
        builder.setMaxInitialLineLength(maxInitialLineLength);
        return this;
    }

    /**
     * Set the maximum total size of HTTP headers, which could be send be created {@link HttpClient}.
     *
     * @param maxHeaderSize The {@link HttpClient} will throw TooLongFrameException if the total size of all HTTP
     * headers exceeds this length.
     * @return {@code this}.
     */
    public DefaultHttpClientBuilder<ResolvedAddress> setMaxHeaderSize(final int maxHeaderSize) {
        builder.setMaxHeaderSize(maxHeaderSize);
        return this;
    }

    /**
     * Set the maximum size of HttpContents which will be send by created {@link HttpClient}.
     *
     * @param maxChunkSize A {@link HttpClient} will break contents or chunks whose size exceeds this value into
     * multiple HttpContents whose length is less than maxChunkSize.
     * @return {@code this}.
     */
    public DefaultHttpClientBuilder<ResolvedAddress> setMaxChunkSize(final int maxChunkSize) {
        builder.setMaxChunkSize(maxChunkSize);
        return this;
    }

    /**
     * Set the value used to calculate an exponential moving average of the encoded size of the initial line and the
     * headers for a guess for future buffer allocations.
     *
     * @param headersEncodedSizeEstimate An estimated size of encoded initial line and headers.
     * @return {@code this}.
     */
    public DefaultHttpClientBuilder<ResolvedAddress> setHeadersEncodedSizeEstimate(
            final int headersEncodedSizeEstimate) {
        builder.setHeadersEncodedSizeEstimate(headersEncodedSizeEstimate);
        return this;
    }

    /**
     * Set the value used to calculate an exponential moving average of the encoded size of the trailers for a guess for
     * future buffer allocations.
     *
     * @param trailersEncodedSizeEstimate An estimated size of encoded trailers.
     * @return {@code this}.
     */
    public DefaultHttpClientBuilder<ResolvedAddress> setTrailersEncodedSizeEstimate(
            final int trailersEncodedSizeEstimate) {
        builder.setTrailersEncodedSizeEstimate(trailersEncodedSizeEstimate);
        return this;
    }

    /**
     * Set the maximum number of pipelined HTTP requests to queue up, anything above this will be rejected,
     * 1 means pipelining is disabled and requests and responses are processed sequentially.
     * <p>
     * Request pipelining requires HTTP 1.1
     *
     * @param maxPipelinedRequests number of pipelined requests to queue up
     * @return {@code this}.
     */
    public DefaultHttpClientBuilder<ResolvedAddress> setMaxPipelinedRequests(final int maxPipelinedRequests) {
        builder.setMaxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    /**
     * Defines a filter {@link Function} to decorate {@link HttpConnection} created by this builder.
     * <p>
     * Filtering allows you to wrap a {@link HttpConnection} and modify behavior during request/response processing
     * Some potential candidates for filtering include logging, metrics, and decorating responses
     * @param connFilter {@link Function} to decorate a {@link HttpConnection} for the purpose of filtering
     * @return {@code this}
     */
    public DefaultHttpClientBuilder<ResolvedAddress> setConnectionFilter(
            Function<HttpConnection<HttpPayloadChunk, HttpPayloadChunk>,
                    HttpConnection<HttpPayloadChunk, HttpPayloadChunk>> connFilter) {
        this.connFilter = requireNonNull(connFilter);
        return this;
    }
}
