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
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.ClientFilterFunction;
import io.servicetalk.http.api.ConnectionFilterFunction;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.LoadBalancerReadyStreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.utils.StreamingHttpConnectionHostHeaderFilter;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.SslConfig;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.http.netty.GlobalDnsServiceDiscoverer.globalDnsServiceDiscoverer;
import static io.servicetalk.loadbalancer.RoundRobinLoadBalancer.newRoundRobinFactory;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static java.util.Objects.requireNonNull;

/**
 * A builder of {@link StreamingHttpClient} instances which call a single server based on the provided address.
 * <p>
 * It also provides a good set of default settings and configurations, which could be used by most users as-is or
 * could be overridden to address specific use cases.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
final class DefaultSingleAddressHttpClientBuilder<U, R> implements SingleAddressHttpClientBuilder<U, R> {

    // Allows creating builders with an unknown address until buildStreaming time, eg. MultiAddressUrlHttpClientBuilder
    private static final HostAndPort UNKNOWN = HostAndPort.of("unknown.invalid", -1);

    private static final ClientFilterFunction LB_READY_FILTER =
            (client, lbEvents) -> new LoadBalancerReadyStreamingHttpClient(4, lbEvents, client);

    private final U address;
    private final HttpClientConfig config;
    private ExecutionContext executionContext = globalExecutionContext();
    private LoadBalancerFactory<R, StreamingHttpConnection> loadBalancerFactory;
    private ServiceDiscoverer<U, R> serviceDiscoverer;
    private Function<U, ConnectionFilterFunction> hostHeaderFilterFunction =
            DefaultSingleAddressHttpClientBuilder::defaultHostClientFilterFactory;
    private ConnectionFilterFunction connectionFilterFunction = ConnectionFilterFunction.identity();
    private ClientFilterFunction clientFilterFunction = ClientFilterFunction.identity();
    private ClientFilterFunction lbReadyFilter = LB_READY_FILTER;

    DefaultSingleAddressHttpClientBuilder(final ServiceDiscoverer<U, R> serviceDiscoverer,
                                          final U address) {
        config = new HttpClientConfig(new TcpClientConfig(false));
        this.loadBalancerFactory = newRoundRobinFactory();
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
        this.address = requireNonNull(address);
    }

    private DefaultSingleAddressHttpClientBuilder(
            final LoadBalancerFactory<R, StreamingHttpConnection> loadBalancerFactory,
            final ServiceDiscoverer<U, R> serviceDiscoverer,
            final U address,
            final DefaultSingleAddressHttpClientBuilder<U, R> from) {
        config = new HttpClientConfig(from.config);
        this.address = requireNonNull(address);
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
        this.loadBalancerFactory = requireNonNull(loadBalancerFactory);
        clientFilterFunction = from.clientFilterFunction;
        connectionFilterFunction = from.connectionFilterFunction;
        hostHeaderFilterFunction = from.hostHeaderFilterFunction;
        lbReadyFilter = from.lbReadyFilter;
    }

    DefaultSingleAddressHttpClientBuilder<U, R> copy() {
        return copy(address);
    }

    DefaultSingleAddressHttpClientBuilder<U, R> copy(final U address) {
        return new DefaultSingleAddressHttpClientBuilder<>(loadBalancerFactory, serviceDiscoverer, address, this);
    }

    static DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forHostAndPort(
            final HostAndPort address) {
        return new DefaultSingleAddressHttpClientBuilder<>(globalDnsServiceDiscoverer(), address);
    }

    static DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forUnknownHostAndPort() {
        return forHostAndPort(UNKNOWN);
    }

    @SuppressWarnings("unchecked")
    @Override
    public StreamingHttpClient buildStreaming() {
        assert UNKNOWN != address : "Attempted to buildStreaming with an unknown address";
        final ReadOnlyHttpClientConfig roConfig = config.asReadOnly();
        final ExecutionContext exec = executionContext;
        // Track resources that potentially need to be closed when an exception is thrown during buildStreaming
        final CompositeCloseable closeOnException = newCompositeCloseable();
        try {
            Publisher<Event<R>> sdEvents = serviceDiscoverer.discover(address);

            final StreamingHttpRequestResponseFactory reqRespFactory =
                    new DefaultStreamingHttpRequestResponseFactory(exec.bufferAllocator(),
                            roConfig.getHeadersFactory());
            final ConnectionFilterFunction connectionFilters = connectionFilterFunction.append(
                    hostHeaderFilterFunction.apply(address));

            // closed by the LoadBalancer
            ConnectionFactory<R, LoadBalancedStreamingHttpConnection> connectionFactory =
                    closeOnException.prepend(roConfig.getMaxPipelinedRequests() == 1 ?
                        new NonPipelinedLBHttpConnectionFactory<>(roConfig, exec, connectionFilters, reqRespFactory) :
                        new PipelinedLBHttpConnectionFactory<>(roConfig, exec, connectionFilters, reqRespFactory));

            LoadBalancer<? extends StreamingHttpConnection> lbfUntypedForCast = closeOnException.prepend(
                     loadBalancerFactory.newLoadBalancer(sdEvents, connectionFactory));
            LoadBalancer<LoadBalancedStreamingHttpConnection> lb =
                    (LoadBalancer<LoadBalancedStreamingHttpConnection>) lbfUntypedForCast;

            return clientFilterFunction.append(lbReadyFilter).apply(closeOnException.prepend(
                    new DefaultStreamingHttpClient(exec, lb, reqRespFactory)), lb.getEventStream());
        } catch (final Throwable t) {
            closeOnException.closeAsync().subscribe();
            throw t;
        }
    }

    private static <U> ConnectionFilterFunction defaultHostClientFilterFactory(final U address) {
        if (address instanceof CharSequence) {
            return c -> new StreamingHttpConnectionHostHeaderFilter((CharSequence) address, c);
        }
        if (address instanceof HostAndPort) {
            return c -> new StreamingHttpConnectionHostHeaderFilter((HostAndPort) address, c);
        }
        throw new IllegalArgumentException("Unsupported host header address type, provide an override");
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> executionContext(final ExecutionContext context) {
        this.executionContext = requireNonNull(context);
        return this;
    }

    @Override
    public <T> SingleAddressHttpClientBuilder<U, R> socketOption(SocketOption<T> option, T value) {
        config.getTcpClientConfig().setSocketOption(option, value);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> enableWireLogging(final String loggerName) {
        config.getTcpClientConfig().enableWireLogging(loggerName);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> disableWireLogging() {
        config.getTcpClientConfig().disableWireLogging();
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> headersFactory(final HttpHeadersFactory headersFactory) {
        config.setHeadersFactory(headersFactory);
        return this;
    }

    HttpHeadersFactory getHeadersFactory() {
        return config.getHeadersFactory();
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> maxInitialLineLength(final int maxInitialLineLength) {
        config.setMaxInitialLineLength(maxInitialLineLength);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> maxHeaderSize(final int maxHeaderSize) {
        config.setMaxHeaderSize(maxHeaderSize);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> headersEncodedSizeEstimate(final int headersEncodedSizeEstimate) {
        config.setHeadersEncodedSizeEstimate(headersEncodedSizeEstimate);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> trailersEncodedSizeEstimate(final int trailersEncodedSizeEstimate) {
        config.setTrailersEncodedSizeEstimate(trailersEncodedSizeEstimate);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> maxPipelinedRequests(final int maxPipelinedRequests) {
        config.setMaxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> appendConnectionFilter(final ConnectionFilterFunction function) {
        connectionFilterFunction = connectionFilterFunction.append(requireNonNull(function));
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> disableHostHeaderFallback() {
        hostHeaderFilterFunction = address -> ConnectionFilterFunction.identity();
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> disableWaitForLoadBalancer() {
        lbReadyFilter = ClientFilterFunction.identity();
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> enableHostHeaderFallback(final CharSequence hostHeader) {
        hostHeaderFilterFunction = address -> connection ->
                new StreamingHttpConnectionHostHeaderFilter(hostHeader, connection);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> appendClientFilter(final ClientFilterFunction function) {
        clientFilterFunction = clientFilterFunction.append(function);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> serviceDiscoverer(final ServiceDiscoverer<U, R> serviceDiscoverer) {
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> loadBalancerFactory(
            final LoadBalancerFactory<R, StreamingHttpConnection> loadBalancerFactory) {
        this.loadBalancerFactory = requireNonNull(loadBalancerFactory);
        return this;
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> sslConfig(@Nullable final SslConfig sslConfig) {
        config.getTcpClientConfig().setSslConfig(sslConfig);
        return this;
    }
}
