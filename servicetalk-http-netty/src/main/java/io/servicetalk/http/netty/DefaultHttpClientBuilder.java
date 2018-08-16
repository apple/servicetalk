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
import io.servicetalk.client.api.ServiceDiscoverer;
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
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.SslConfig;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.http.netty.GlobalDnsServiceDiscoverer.globalDnsServiceDiscoverer;
import static io.servicetalk.http.utils.HttpHostHeaderFilter.newHostHeaderFilter;
import static io.servicetalk.loadbalancer.RoundRobinLoadBalancer.newRoundRobinFactory;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static java.util.Objects.requireNonNull;
import static java.util.function.UnaryOperator.identity;

/**
 * A builder for instances of {@link HttpClient}.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
public final class DefaultHttpClientBuilder<U, R> implements HttpClientBuilder {

    // Allows creating builders with an unknown address until build time, eg. AddressParsingHttpRequesterBuilder
    private static final HostAndPort DUMMY_HAP = HostAndPort.of("dummy.invalid", -1);

    private final HttpClientConfig config;
    private final LoadBalancerFactory<R, HttpConnection> lbFactory;
    private final ServiceDiscoverer<U, R> serviceDiscoverer;
    private final U address;
    private BiFunction<HttpClient, Publisher<Object>, HttpClient> clientFilterFactory = (client, lbEvents) ->
            new LoadBalancerReadyHttpClient(4, lbEvents, client);
    private UnaryOperator<HttpConnection> connectionFilterFactory = identity();

    private final BiFunction<HttpClient, Publisher<Object>, HttpClient> hostHeaderFilter;

    private DefaultHttpClientBuilder(final LoadBalancerFactory<R, HttpConnection> loadBalancerFactory,
                                     final ServiceDiscoverer<U, R> serviceDiscoverer,
                                     final U address,
                                     final BiFunction<HttpClient, Publisher<Object>, HttpClient> hostHeaderFilter) {
        this.lbFactory = requireNonNull(loadBalancerFactory);
        this.hostHeaderFilter = requireNonNull(hostHeaderFilter);
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
        this.address = requireNonNull(address);
        this.config = new HttpClientConfig(new TcpClientConfig(false));
    }

    private DefaultHttpClientBuilder(final U address,
                                     final DefaultHttpClientBuilder<U, R> from) {
        this.address = requireNonNull(address);
        lbFactory = from.lbFactory;
        clientFilterFactory = from.clientFilterFactory;
        connectionFilterFactory = from.connectionFilterFactory;
        serviceDiscoverer = from.serviceDiscoverer;
        hostHeaderFilter = from.hostHeaderFilter;
        config = new HttpClientConfig(from.config);
    }

    @Override
    public HttpClient build() {
        return build(globalExecutionContext());
    }

    @Override
    public HttpClient build(final ExecutionContext executionContext) {
        ReadOnlyHttpClientConfig roConfig = config.asReadOnly();

        assert !DUMMY_HAP.equals(address) : "Attempted to build with a dummy address";

        Publisher<Event<R>> addressEventStream = serviceDiscoverer.discover(address);

        ConnectionFactory<R, LoadBalancedHttpConnection> connectionFactory =
                roConfig.getMaxPipelinedRequests() == 1 ?
                    new NonPipelinedLBHttpConnectionFactory<>(roConfig, executionContext, connectionFilterFactory) :
                    new PipelinedLBHttpConnectionFactory<>(roConfig, executionContext, connectionFilterFactory);

        // TODO we should revisit generics on LoadBalancerFactory to avoid casts
        LoadBalancer<? extends HttpConnection> lbfUntypedForCast = lbFactory
                .newLoadBalancer(addressEventStream, connectionFactory);
        LoadBalancer<LoadBalancedHttpConnection> loadBalancer =
                (LoadBalancer<LoadBalancedHttpConnection>) lbfUntypedForCast;
        addClientFilterFactory(defaultHostClientFilterFactory(address));
        return clientFilterFactory.apply(new DefaultHttpClient(executionContext, loadBalancer),
                loadBalancer.getEventStream());
    }

    /**
     * Creates a new {@link DefaultHttpClientBuilder} by copying from another instance.
     *
     * @param address the {@code UnresolvedAddress} to resolve and connect to using the service discover provided in the
     * passed in {@link DefaultHttpClientBuilder}. This address is also converted to host header using the configured
     * {@code hostHeaderFilter} on the passed in {@link DefaultHttpClientBuilder}.
     * @param from builder to copy from
     * @param <U> the type of address before resolution (unresolved address)
     * @param <R> the type of address after resolution (resolved address)
     * @return new builder with same settings as the passed in builder
     * TODO make pkg-pvt when AddressParsingHttpRequesterBuilder is moved to http-netty
     */
    public static <U, R> DefaultHttpClientBuilder<U, R> from(final U address,
                                                             final DefaultHttpClientBuilder<U, R> from) {
        return new DefaultHttpClientBuilder<>(address, from);
    }

    /**
     * Creates a {@link DefaultHttpClientBuilder} for an address with user provided load balancer and service discovery.
     *
     * @param loadBalancerFactory factory to create the {@link LoadBalancer}
     * @param serviceDiscoverer {@link ServiceDiscoverer} used to resolve the {@code UnresolvedAddress} @param address
     * @param address the {@code UnresolvedAddress} to connect to resolved using the {@code serviceDiscoverer}. This
     * address will also be used for the host header using a best effort conversion. Use this method {@link
     * #forSingleAddress(LoadBalancerFactory, ServiceDiscoverer, Object, Function)} if you have an custom {@code
     * UnresolvedAddress} type.
     * @param <U> the type of address before resolution (unresolved address)
     * @param <R> the type of address after resolution (resolved address)
     * @return new builder with provided configuration
     */
    public static <U, R> DefaultHttpClientBuilder<U, R> forSingleAddress(
            final LoadBalancerFactory<R, HttpConnection> loadBalancerFactory,
            final ServiceDiscoverer<U, R> serviceDiscoverer,
            final U address) {
        return new DefaultHttpClientBuilder<>(loadBalancerFactory, requireNonNull(serviceDiscoverer), address,
                defaultHostClientFilterFactory(address));
    }

    private static <U> BiFunction<HttpClient, Publisher<Object>, HttpClient> defaultHostClientFilterFactory(
            final U address) {
        final BiFunction<HttpClient, Publisher<Object>, HttpClient> clientFilterFactory;
        if (address instanceof CharSequence) {
            clientFilterFactory = (c, p) -> newHostHeaderFilter((CharSequence) address, c);
        } else if (address instanceof HostAndPort) {
            clientFilterFactory = (c, p) -> newHostHeaderFilter((HostAndPort) address, c);
        } else {
            throw new IllegalArgumentException("Unsupported host address type, provide a transformer");
        }
        return clientFilterFactory;
    }

    /**
     * Creates a {@link DefaultHttpClientBuilder} for an address with user provided load balancer and service discovery.
     *
     * @param loadBalancerFactory factory to create the {@link LoadBalancer}
     * @param serviceDiscoverer {@link ServiceDiscoverer} used to resolve the {@code UnresolvedAddress}
     * @param address the {@code UnresolvedAddress} to connect to resolved using the {@code serviceDiscoverer}. This
     * address will also be used for the host header, converted to {@link CharSequence} using the provided {@code
     * addressTransformer}.
     * @param addressTransformer used to convert the {@code address} to a host header
     * @param <U> the type of address before resolution (unresolved address)
     * @param <R> the type of address after resolution (resolved address)
     * @return new builder with provided configuration
     */
    public static <U, R> DefaultHttpClientBuilder<U, R> forSingleAddress(
            final LoadBalancerFactory<R, HttpConnection> loadBalancerFactory,
            final ServiceDiscoverer<U, R> serviceDiscoverer,
            final U address,
            final Function<U, CharSequence> addressTransformer) {
        final CharSequence transformedAddress = addressTransformer.apply(address);
        return new DefaultHttpClientBuilder<>(loadBalancerFactory, serviceDiscoverer, address,
                (c, p) -> newHostHeaderFilter(transformedAddress, c));
    }

    /**
     * Creates a {@link DefaultHttpClientBuilder} for an address with default load balancer and user provided service
     * discovery.
     *
     * @param serviceDiscoverer {@link ServiceDiscoverer} used to resolve the {@code UnresolvedAddress} @param address
     * @param address the {@code UnresolvedAddress} to connect to resolved using the {@code serviceDiscoverer}. This
     * address will also be used for the host header using a best effort conversion. Use this method {@link
     * #forSingleAddress(ServiceDiscoverer, Object, Function)} if you have an custom {@code
     * UnresolvedAddress} type.
     * @param <U> the type of address before resolution (unresolved address)
     * @param <R> the type of address after resolution (resolved address)
     * @return new builder with provided configuration
     */
    public static <U, R> DefaultHttpClientBuilder<U, R> forSingleAddress(
            final ServiceDiscoverer<U, R> serviceDiscoverer,
            final U address) {
        return new DefaultHttpClientBuilder<>(newRoundRobinFactory(), serviceDiscoverer, address,
                defaultHostClientFilterFactory(address));
    }

    /**
     * Creates a {@link DefaultHttpClientBuilder} for an address with default load balancer and user provided service
     * discovery.
     *
     * @param serviceDiscoverer {@link ServiceDiscoverer} used to resolve the {@code UnresolvedAddress}
     * @param address the {@code UnresolvedAddress} to connect to resolved using the {@code serviceDiscoverer}. This
     * address will also be used for the host header, converted to {@link CharSequence} using the provided {@code
     * addressTransformer}.
     * @param addressTransformer used to convert the {@code address} to a host header
     * @param <U> the type of address before resolution (unresolved address)
     * @param <R> the type of address after resolution (resolved address)
     * @return new builder with provided configuration
     */
    public static <U, R> DefaultHttpClientBuilder<U, R> forSingleAddress(
            final ServiceDiscoverer<U, R> serviceDiscoverer,
            final U address,
            final Function<U, CharSequence> addressTransformer) {
        final CharSequence transformedAddress = addressTransformer.apply(address);
        return new DefaultHttpClientBuilder<>(newRoundRobinFactory(), serviceDiscoverer, address,
                (c, p) -> newHostHeaderFilter(transformedAddress, c));
    }

    /**
     * Creates a {@link DefaultHttpClientBuilder} for an address with user provided load balancer and DNS service
     * discovery.
     *
     * @param loadBalancerFactory factory to create the {@link LoadBalancer}
     * @param address the {@code UnresolvedAddress} to connect to resolved using the {@code serviceDiscoverer}. This
     * address will also be used for the host header, converted to {@link CharSequence} using the provided {@code
     * addressTransformer}.
     * @return new builder with provided configuration
     */
    public static DefaultHttpClientBuilder<HostAndPort, InetSocketAddress> forSingleAddress(
            final LoadBalancerFactory<InetSocketAddress, HttpConnection> loadBalancerFactory,
            final HostAndPort address) {
        return new DefaultHttpClientBuilder<>(loadBalancerFactory, globalDnsServiceDiscoverer(), address,
                (c, p) -> newHostHeaderFilter(address, c));
    }

    /**
     * Creates a {@link DefaultHttpClientBuilder} for an address with default load balancer and DNS service discovery.
     *
     * @param host host to connect to, resolved using the service discoverer. This will also be used for the host
     * header together with the {@code port}.
     * @param port port to connect to
     * @return new builder for the address
     */
    public static DefaultHttpClientBuilder<HostAndPort, InetSocketAddress> forSingleAddress(String host, int port) {
        return forSingleAddress(HostAndPort.of(host, port));
    }

    /**
     * Creates a {@link DefaultHttpClientBuilder} for an address with default load balancer and DNS service discovery.
     *
     * @param address the {@code UnresolvedAddress} to connect to, resolved using the service discoverer. This
     * address will also be used for the host header.
     * @return new builder for the address
     */
    public static DefaultHttpClientBuilder<HostAndPort, InetSocketAddress> forSingleAddress(HostAndPort address) {
        return new DefaultHttpClientBuilder<>(newRoundRobinFactory(),
                globalDnsServiceDiscoverer(), address,
                (c, p) -> newHostHeaderFilter(address, c));
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
    public DefaultHttpClientBuilder<U, R> setSslConfig(@Nullable final SslConfig sslConfig) {
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
    public <T> DefaultHttpClientBuilder<U, R> setSocketOption(SocketOption<T> option, T value) {
        config.getTcpClientConfig().setSocketOption(option, value);
        return this;
    }

    /**
     * Enable wire-logging for connections created by this builder. All wire events will be logged at trace level.
     *
     * @param loggerName The name of the logger to log wire events.
     * @return {@code this}.
     */
    public DefaultHttpClientBuilder<U, R> enableWireLogging(final String loggerName) {
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
    public DefaultHttpClientBuilder<U, R> disableWireLogging() {
        config.getTcpClientConfig().disableWireLogging();
        return this;
    }

    /**
     * Set the {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding responses.
     *
     * @param headersFactory the {@link HttpHeadersFactory} to use.
     * @return {@code this}.
     */
    public DefaultHttpClientBuilder<U, R> setHeadersFactory(final HttpHeadersFactory headersFactory) {
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
    public DefaultHttpClientBuilder<U, R> setMaxInitialLineLength(final int maxInitialLineLength) {
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
    public DefaultHttpClientBuilder<U, R> setMaxHeaderSize(final int maxHeaderSize) {
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
    public DefaultHttpClientBuilder<U, R> setHeadersEncodedSizeEstimate(final int headersEncodedSizeEstimate) {
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
    public DefaultHttpClientBuilder<U, R> setTrailersEncodedSizeEstimate(final int trailersEncodedSizeEstimate) {
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
    public DefaultHttpClientBuilder<U, R> setMaxPipelinedRequests(final int maxPipelinedRequests) {
        config.setMaxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    /**
     * Set the {@link Function} which is used as a factory to filter/decorate {@link HttpConnection} created by this
     * builder.
     * <p>
     * Filtering allows you to wrap a {@link HttpConnection} and modify behavior during request/response processing
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     *
     * @param connectionFilterFactory {@link UnaryOperator} to decorate a {@link HttpConnection} for the purpose of
     * filtering.
     * @return {@code this}
     */
    public DefaultHttpClientBuilder<U, R> setConnectionFilterFactory(
            final UnaryOperator<HttpConnection> connectionFilterFactory) {
        this.connectionFilterFactory = requireNonNull(connectionFilterFactory);
        return this;
    }

    /**
     * Set the filter factory that is used to decorate {@link HttpClient} created by this builder.
     * <p>
     * Note this method will be used to decorate the result of {@link #build(ExecutionContext)} before it is
     * returned to the user.
     *
     * @param clientFilterFactory {@link BiFunction} to decorate a {@link HttpClient} for the purpose of filtering.
     * The signature of the {@link BiFunction} is as follows:
     * <pre>
     *     PostFilteredHttpClient func(PreFilteredHttpClient, {@link LoadBalancer#getEventStream()})
     * </pre>
     * @return {@code this}
     */
    public DefaultHttpClientBuilder<U, R> setClientFilterFactory(
            final BiFunction<HttpClient, Publisher<Object>, HttpClient> clientFilterFactory) {
        this.clientFilterFactory = requireNonNull(clientFilterFactory);
        return this;
    }

    /**
     * Adds a client filter on to the existing {@link HttpClient} filter {@link BiFunction} from
     * {@link #setClientFilterFactory(BiFunction)}.
     * <p>
     * The order of execution of these filters are in reverse order of addition. If 3 filters are added as follows:
     * <pre>
     *     builder.addClientFilterFactory(filter1).addClientFilterFactory(filter2).addClientFilterFactory(filter3)
     * </pre>
     * then while making a request to the client built by this builder the order of invocation of these filters will be:
     * <pre>
     *     filter3 =&gt; filter2 =&gt; filter1
     * </pre>
     * @param clientFilterFactory {@link BiFunction} to decorate a {@link HttpClient} for the purpose of filtering.
     * The signature of the {@link BiFunction} is as follows:
     * <pre>
     *     PostFilteredHttpClient func(PreFilteredHttpClient, {@link LoadBalancer#getEventStream()})
     * </pre>
     * @return {@code this}
     */
    public DefaultHttpClientBuilder<U, R> addClientFilterFactory(
            final BiFunction<HttpClient, Publisher<Object>, HttpClient> clientFilterFactory) {
        requireNonNull(clientFilterFactory);
        BiFunction<HttpClient, Publisher<Object>, HttpClient> oldFilterFactory = this.clientFilterFactory;
        this.clientFilterFactory = (httpClient, objectPublisher) ->
                clientFilterFactory.apply(oldFilterFactory.apply(httpClient, objectPublisher), objectPublisher);
        return this;
    }

    /**
     * Append a client filter on to the existing {@link HttpClient} filter {@link BiFunction} from
     * {@link #setClientFilterFactory(BiFunction)}.
     * <p>
     * The order of execution of these filters are in reverse order of addition. If 3 filters are added as follows:
     * <pre>
     *     builder.addClientFilterFactory(filter1).addClientFilterFactory(filter2).addClientFilterFactory(filter3)
     * </pre>
     * then while making a request to the client built by this builder the order of invocation of these filters will be:
     * <pre>
     *     filter3 =&gt; filter2 =&gt; filter1
     * </pre>
     * @param clientFilterFactory {@link Function} to decorate a {@link HttpClient} for the purpose of filtering.
     * @return {@code this}
     */
    public DefaultHttpClientBuilder<U, R> addClientFilterFactory(
            final Function<HttpClient, HttpClient> clientFilterFactory) {
        requireNonNull(clientFilterFactory);
        BiFunction<HttpClient, Publisher<Object>, HttpClient> oldFilterFactory = this.clientFilterFactory;
        this.clientFilterFactory = (httpClient, objectPublisher) ->
                clientFilterFactory.apply(oldFilterFactory.apply(httpClient, objectPublisher));
        return this;
    }
}
