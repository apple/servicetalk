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

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer.Event;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpClientBuilder;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.LoadBalancerReadyHttpClient;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.SslConfig;

import java.io.InputStream;
import java.net.SocketOption;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;
import static java.util.function.UnaryOperator.identity;

/**
 * A builder for instances of {@link HttpClient}.
 * @param <ResolvedAddress> the type of address after resolution
 */
public final class DefaultHttpClientBuilder<ResolvedAddress>
        implements HttpClientBuilder<ResolvedAddress, Event<ResolvedAddress>> {

    private final HttpClientConfig config;
    private final LoadBalancerFactory<ResolvedAddress, HttpConnection> lbFactory;
    private BiFunction<HttpClient, Publisher<Object>, HttpClient> clientFilterFactory = (client, lbEvents) ->
                new LoadBalancerReadyHttpClient(4, lbEvents, client);
    private UnaryOperator<HttpConnection> connectionFilterFactory = identity();

    /**
     * Create a new instance.
     * @param loadBalancerFactory factory of {@link LoadBalancer} objects for {@link HttpConnection}s.
     */
    public DefaultHttpClientBuilder(final LoadBalancerFactory<ResolvedAddress, HttpConnection> loadBalancerFactory) {
        this(loadBalancerFactory, new HttpClientConfig(new TcpClientConfig(false)));
    }

    /**
     * @param loadBalancerFactory factory of {@link LoadBalancer} objects for {@link HttpConnection}s
     * @param config pre-load the builder with {@link HttpClientConfig} passed on from higher level builders
     */
    DefaultHttpClientBuilder(final LoadBalancerFactory<ResolvedAddress, HttpConnection> loadBalancerFactory,
                             final HttpClientConfig config) {
        this.lbFactory = requireNonNull(loadBalancerFactory);
        this.config = requireNonNull(config);
    }

    @Override
    public HttpClient build(final ExecutionContext executionContext,
                            final Publisher<Event<ResolvedAddress>> addressEventStream) {
        ReadOnlyHttpClientConfig roConfig = config.asReadOnly();
        ConnectionFactory<ResolvedAddress, LoadBalancedHttpConnection> connectionFactory =
                roConfig.getMaxPipelinedRequests() == 1 ?
                        new NonPipelinedLBHttpConnectionFactory<>(roConfig, executionContext, connectionFilterFactory) :
                        new PipelinedLBHttpConnectionFactory<>(roConfig, executionContext, connectionFilterFactory);

        // TODO we should revisit generics on LoadBalancerFactory to avoid casts
        LoadBalancer<? extends HttpConnection> lbfUntypedForCast =
                lbFactory.newLoadBalancer(addressEventStream, connectionFactory);
        LoadBalancer<LoadBalancedHttpConnection> loadBalancer =
                (LoadBalancer<LoadBalancedHttpConnection>) lbfUntypedForCast;
        return clientFilterFactory.apply(new DefaultHttpClient(executionContext, loadBalancer),
                                            loadBalancer.getEventStream());
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
        this.config.getTcpClientConfig().setSslConfig(sslConfig);
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
        config.getTcpClientConfig().setOption(option, value);
        return this;
    }

    /**
     * Enables wire-logging for this client at debug level.
     *
     * @param loggerName Name of the logger.
     * @return {@code this}.
     */
    public DefaultHttpClientBuilder<ResolvedAddress> setWireLoggerName(String loggerName) {
        config.getTcpClientConfig().setWireLoggerName(loggerName);
        return this;
    }

    /**
     * Disabled wire-logging for this client at debug level.
     *
     * @return {@code this}.
     */
    public DefaultHttpClientBuilder<ResolvedAddress> disableWireLog() {
        config.getTcpClientConfig().disableWireLog();
        return this;
    }

    /**
     * Set the {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding responses.
     *
     * @param headersFactory the {@link HttpHeadersFactory} to use.
     * @return {@code this}.
     */
    public DefaultHttpClientBuilder<ResolvedAddress> setHeadersFactory(final HttpHeadersFactory headersFactory) {
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
    public DefaultHttpClientBuilder<ResolvedAddress> setMaxInitialLineLength(final int maxInitialLineLength) {
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
    public DefaultHttpClientBuilder<ResolvedAddress> setMaxHeaderSize(final int maxHeaderSize) {
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
    public DefaultHttpClientBuilder<ResolvedAddress> setHeadersEncodedSizeEstimate(
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
    public DefaultHttpClientBuilder<ResolvedAddress> setTrailersEncodedSizeEstimate(
            final int trailersEncodedSizeEstimate) {
        config.setTrailersEncodedSizeEstimate(trailersEncodedSizeEstimate);
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
        config.setMaxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    /**
     * Set the {@link Function} which is used as a factory to filter/decorate {@link HttpConnection} created by this
     * builder.
     * <p>
     * Filtering allows you to wrap a {@link HttpConnection} and modify behavior during request/response processing
     * Some potential candidates for filtering include logging, metrics, and decorating responses
     * @param connectionFilterFactory {@link UnaryOperator} to decorate a {@link HttpConnection} for the purpose of
     * filtering.
     * @return {@code this}
     */
    public DefaultHttpClientBuilder<ResolvedAddress> setConnectionFilterFactory(
            UnaryOperator<HttpConnection> connectionFilterFactory) {
        this.connectionFilterFactory = requireNonNull(connectionFilterFactory);
        return this;
    }

    /**
     * Set the filter factory that is used to decorate {@link HttpClient} created by this builder.
     * <p>
     * Note this method will be used to decorate the result of {@link #build(ExecutionContext, Publisher)} before it is
     * returned to the user.
     * @param clientFilterFactory {@link BiFunction} to decorate a {@link HttpClient} for the purpose of filtering.
     * @return {@code this}
     */
    public DefaultHttpClientBuilder<ResolvedAddress> setClientFilterFactory(
            BiFunction<HttpClient, Publisher<Object>, HttpClient> clientFilterFactory) {
        this.clientFilterFactory = requireNonNull(clientFilterFactory);
        return this;
    }
}
