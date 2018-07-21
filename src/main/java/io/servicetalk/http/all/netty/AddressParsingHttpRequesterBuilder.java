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
package io.servicetalk.http.all.netty;

import io.servicetalk.client.api.DefaultGroupKey;
import io.servicetalk.client.api.GroupKey;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.dns.discovery.netty.DefaultDnsServiceDiscovererBuilder;
import io.servicetalk.http.api.AggregatedHttpClient;
import io.servicetalk.http.api.AggregatedHttpRequester;
import io.servicetalk.http.api.BlockingAggregatedHttpRequester;
import io.servicetalk.http.api.BlockingHttpRequester;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpClientBuilder;
import io.servicetalk.http.api.HttpClientGroup;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequester;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.netty.DefaultHttpClientBuilder;
import io.servicetalk.http.utils.RedirectingHttpClientGroup;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.SslConfig;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toAsyncCloseable;
import static io.servicetalk.http.all.netty.SslConfigProviders.plainByDefault;
import static io.servicetalk.http.api.HttpClientGroups.newHttpClientGroup;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.utils.HttpHostHeaderFilter.newHostHeaderFilter;
import static io.servicetalk.loadbalancer.RoundRobinLoadBalancer.newRoundRobinFactory;
import static io.servicetalk.transport.api.SslConfigBuilder.forClient;
import static java.util.Objects.requireNonNull;
import static java.util.function.UnaryOperator.identity;

/**
 * A builder of {@link HttpRequester} instances which have a capacity to call any server based on the parsed address
 * information from each {@link HttpRequest}.
 * <p>
 * It also provides a good set of default settings and configurations, which could be used by most users as-is or
 * could be overridden to address specific use cases.
 */
public final class AddressParsingHttpRequesterBuilder {

    // https://tools.ietf.org/html/rfc2068#section-10.3 says:
    // A user agent SHOULD NOT automatically redirect a request more than 5 times,
    // since such redirections usually indicate an infinite loop.
    private static final int DEFAULT_MAX_REDIRECTS = 5;

    private final DefaultHttpClientBuilder<InetSocketAddress> clientBuilder;
    @Nullable
    private ServiceDiscoverer<HostAndPort, InetSocketAddress> serviceDiscoverer;
    private UnaryOperator<HttpRequester> requesterFilterFactory = identity();
    private UnaryOperator<HttpClientGroup<HostAndPort>> clientGroupFilterFactory = identity();
    private int maxRedirects = DEFAULT_MAX_REDIRECTS;
    private SslConfigProvider sslConfigProvider = plainByDefault();

    /**
     * Create a new instance with a default {@link LoadBalancerFactory}.
     */
    public AddressParsingHttpRequesterBuilder() {
        this(newRoundRobinFactory());
    }

    /**
     * Create a new instance.
     *
     * @param loadBalancerFactory A {@link LoadBalancerFactory} which generates {@link LoadBalancer} objects.
     */
    public AddressParsingHttpRequesterBuilder(
            final LoadBalancerFactory<InetSocketAddress, HttpConnection> loadBalancerFactory) {
        clientBuilder = new DefaultHttpClientBuilder<>(loadBalancerFactory);
    }

    /**
     * Set a {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     *
     * @param serviceDiscoverer A {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * Lifecycle of the provided {@link ServiceDiscoverer} is managed externally and it should be
     * {@link ServiceDiscoverer#closeAsync() closed} after all built {@link HttpRequester}s will be closed and this
     * {@link ServiceDiscoverer} is no longer needed.
     * @return {@code this}.
     */
    public AddressParsingHttpRequesterBuilder setServiceDiscoverer(
            final ServiceDiscoverer<HostAndPort, InetSocketAddress> serviceDiscoverer) {
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
        return this;
    }

    /**
     * Set a {@link SslConfigProvider} for appropriate {@link SslConfig}s.
     *
     * @param sslConfigProvider A {@link SslConfigProvider} to use.
     * @return {@code this}.
     */
    public AddressParsingHttpRequesterBuilder setSslConfigProvider(final SslConfigProvider sslConfigProvider) {
        this.sslConfigProvider = requireNonNull(sslConfigProvider);
        return this;
    }

    /**
     * Add a {@link SocketOption} for all connections.
     *
     * @param <T> A type of the value.
     * @param option An option to apply.
     * @param value A value of the option.
     * @return {@code this}.
     */
    public <T> AddressParsingHttpRequesterBuilder setSocketOption(final SocketOption<T> option, final T value) {
        clientBuilder.setSocketOption(option, value);
        return this;
    }

    /**
     * Enable wire-logging for connections created by this builder. All wire events will be logged at trace level.
     *
     * @param loggerName A name of the logger to log wire events.
     * @return {@code this}.
     */
    public AddressParsingHttpRequesterBuilder enableWireLogging(final String loggerName) {
        clientBuilder.enableWireLogging(loggerName);
        return this;
    }

    /**
     * Disable previously configured wire-logging for connections created by this builder.
     * If wire-logging has not been configured before, this method has no effect.
     *
     * @return {@code this}.
     * @see #enableWireLogging(String)
     */
    public AddressParsingHttpRequesterBuilder disableWireLogging() {
        clientBuilder.disableWireLogging();
        return this;
    }

    /**
     * Set the {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding responses.
     *
     * @param headersFactory A {@link HttpHeadersFactory} to use.
     * @return {@code this}.
     */
    public AddressParsingHttpRequesterBuilder setHeadersFactory(final HttpHeadersFactory headersFactory) {
        clientBuilder.setHeadersFactory(headersFactory);
        return this;
    }

    /**
     * Set the maximum size of the initial HTTP line for created {@link HttpRequester}.
     *
     * @param maxInitialLineLength A {@link HttpRequester} will throw TooLongFrameException if the initial
     * HTTP line exceeds this length.
     * @return {@code this}.
     */
    public AddressParsingHttpRequesterBuilder setMaxInitialLineLength(final int maxInitialLineLength) {
        clientBuilder.setMaxInitialLineLength(maxInitialLineLength);
        return this;
    }

    /**
     * Set the maximum total size of HTTP headers, which could be send be created {@link HttpRequester}.
     *
     * @param maxHeaderSize A {@link HttpRequester} will throw TooLongFrameException if the total size of all
     * HTTP headers exceeds this length.
     * @return {@code this}.
     */
    public AddressParsingHttpRequesterBuilder setMaxHeaderSize(final int maxHeaderSize) {
        clientBuilder.setMaxHeaderSize(maxHeaderSize);
        return this;
    }

    /**
     * Set the value used to calculate an exponential moving average of the encoded size of the initial line and the
     * headers for a guess for future buffer allocations.
     *
     * @param headersEncodedSizeEstimate An estimated size of encoded initial line and headers.
     * @return {@code this}.
     */
    public AddressParsingHttpRequesterBuilder setHeadersEncodedSizeEstimate(
            final int headersEncodedSizeEstimate) {
        clientBuilder.setHeadersEncodedSizeEstimate(headersEncodedSizeEstimate);
        return this;
    }

    /**
     * Set the value used to calculate an exponential moving average of the encoded size of the trailers for a guess for
     * future buffer allocations.
     *
     * @param trailersEncodedSizeEstimate An estimated size of encoded trailers.
     * @return {@code this}.
     */
    public AddressParsingHttpRequesterBuilder setTrailersEncodedSizeEstimate(
            final int trailersEncodedSizeEstimate) {
        clientBuilder.setTrailersEncodedSizeEstimate(trailersEncodedSizeEstimate);
        return this;
    }

    /**
     * Set the maximum number of pipelined HTTP requests to queue up, anything above this will be rejected,
     * {@code 1} means pipelining is disabled and requests and responses are processed sequentially.
     * <p>
     * Request pipelining requires HTTP 1.1.
     *
     * @param maxPipelinedRequests A maximum number of pipelined requests to queue up.
     * @return {@code this}.
     */
    public AddressParsingHttpRequesterBuilder setMaxPipelinedRequests(final int maxPipelinedRequests) {
        clientBuilder.setMaxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    /**
     * Set a {@link Function} which is used as a factory to filter/decorate {@link HttpRequester} created by this
     * builder.
     * <p>
     * Filtering allows you to wrap {@link HttpRequester} and modify behavior during request/response processing.
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     *
     * @param requesterFilterFactory A {@link UnaryOperator} to decorate {@link HttpRequester} for the purpose of
     * filtering.
     * @return {@code this}.
     */
    public AddressParsingHttpRequesterBuilder setRequesterFilterFactory(
            final UnaryOperator<HttpRequester> requesterFilterFactory) {
        this.requesterFilterFactory = requireNonNull(requesterFilterFactory);
        return this;
    }

    /**
     * Set a {@link Function} which is used as a factory to filter/decorate {@link HttpClientGroup} used by created
     * {@link HttpRequester}.
     * <p>
     * Filtering allows you to wrap {@link HttpClientGroup} and modify behavior during request/response processing.
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     *
     * @param clientGroupFilterFactory A {@link UnaryOperator} to decorate {@link HttpClientGroup} for the purpose of
     * filtering.
     * @return {@code this}.
     */
    public AddressParsingHttpRequesterBuilder setClientGroupFilterFactory(final
            UnaryOperator<HttpClientGroup<HostAndPort>> clientGroupFilterFactory) {
        this.clientGroupFilterFactory = requireNonNull(clientGroupFilterFactory);
        return this;
    }

    /**
     * Set a {@link Function} which is used as a factory to filter/decorate {@link HttpClient} used by created
     * {@link HttpRequester}.
     * <p>
     * Filtering allows you to wrap {@link HttpClient} and modify behavior during request/response processing.
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     * <p>
     * Note this method will be used to decorate the result of
     * {@link HttpClientBuilder#build(ExecutionContext, Publisher)} before it is returned to the
     * {@link HttpClientGroup}.
     *
     * @param clientFilterFactory A {@link BiFunction} to decorate {@link HttpClient} for the purpose of filtering.
     * @return {@code this}.
     */
    public AddressParsingHttpRequesterBuilder setClientFilterFactory(
            final BiFunction<HttpClient, Publisher<Object>, HttpClient> clientFilterFactory) {
        clientBuilder.setClientFilterFactory(clientFilterFactory);
        return this;
    }

    /**
     * Set a {@link Function} which is used as a factory to filter/decorate {@link HttpConnection} used by created
     * {@link HttpRequester}.
     * <p>
     * Filtering allows you to wrap {@link HttpConnection} and modify behavior during request/response processing.
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     *
     * @param connectionFilterFactory A {@link UnaryOperator} to decorate {@link HttpConnection} for the purpose of
     * filtering.
     * @return {@code this}.
     */
    public AddressParsingHttpRequesterBuilder setConnectionFilterFactory(
            final UnaryOperator<HttpConnection> connectionFilterFactory) {
        clientBuilder.setConnectionFilterFactory(connectionFilterFactory);
        return this;
    }

    /**
     * Set a maximum number of redirects to follow.
     *
     * @param maxRedirects A maximum number of redirects to follow. Use a nonpositive number to disable redirects.
     * @return {@code this}.
     */
    public AddressParsingHttpRequesterBuilder setMaxRedirects(final int maxRedirects) {
        this.maxRedirects = maxRedirects;
        return this;
    }

    /**
     * Build a new {@link HttpRequester}.
     *
     * @param executionContext A {@link ExecutionContext} used for {@link HttpRequester#getExecutionContext()} and
     * to build new {@link HttpClient}s.
     * @return A new {@link HttpRequester}.
     */
    public HttpRequester build(final ExecutionContext executionContext) {
        requireNonNull(executionContext);
        final CompositeCloseable closeables = newCompositeCloseable();
        try {
            final boolean serviceDiscovererProvided = serviceDiscoverer != null;
            final ServiceDiscoverer<HostAndPort, InetSocketAddress> serviceDiscoverer = serviceDiscovererProvided ?
                    this.serviceDiscoverer :
                    closeables.prepend(new DefaultDnsServiceDiscovererBuilder(executionContext).build());

            final ClientBuilderFactory clientBuilderFactory =
                    new ClientBuilderFactory(clientBuilder, sslConfigProvider);
            HttpClientGroup<HostAndPort> clientGroup = closeables.prepend(clientGroupFilterFactory.apply(
                    closeables.prepend(newHttpClientGroup((gk, md) -> clientBuilderFactory.apply(gk, md)
                            .build(executionContext, serviceDiscoverer.discover(gk.getAddress()))))));
            final CacheableGroupKeyFactory groupKeyFactory =
                    closeables.prepend(new CacheableGroupKeyFactory(executionContext, sslConfigProvider));
            clientGroup = maxRedirects <= 0 ? clientGroup :
                    new RedirectingHttpClientGroup<>(clientGroup, groupKeyFactory, executionContext, maxRedirects);
            final HttpRequester requester = closeables.prepend(requesterFilterFactory.apply(
                            closeables.prepend(clientGroup.asRequester(groupKeyFactory, executionContext))));

            return new AddressParsingHttpRequester(requester, toAsyncCloseable(closeables::closeAsync));
        } catch (final Exception e) {
            closeables.closeAsync().subscribe();
            throw e;
        }
    }

    /**
     * Build a new {@link AggregatedHttpRequester}.
     *
     * @param executionContext The {@link ExecutionContext} used for
     * {@link AggregatedHttpRequester#getExecutionContext()} and to build new {@link AggregatedHttpClient}s.
     * @return A new {@link AggregatedHttpRequester}.
     */
    public AggregatedHttpRequester buildAggregated(final ExecutionContext executionContext) {
        return build(executionContext).asAggregatedRequester();
    }

    /**
     * Build a new {@link BlockingHttpRequester}.
     *
     * @param executionContext The {@link ExecutionContext} used for
     * {@link BlockingHttpRequester#getExecutionContext()} and to build new {@link BlockingHttpRequester}s.
     * @return A new {@link BlockingHttpRequester}.
     */
    public BlockingHttpRequester buildBlocking(final ExecutionContext executionContext) {
        return build(executionContext).asBlockingRequester();
    }

    /**
     * Build a new {@link BlockingAggregatedHttpRequester}.
     *
     * @param executionContext The {@link ExecutionContext} used for
     * {@link BlockingAggregatedHttpRequester#getExecutionContext()} and to build new
     * {@link BlockingAggregatedHttpRequester}s.
     * @return A new {@link BlockingHttpRequester}.
     */
    public BlockingAggregatedHttpRequester buildBlockingAggregated(final ExecutionContext executionContext) {
        return build(executionContext).asBlockingAggregatedRequester();
    }

    /**
     * Returns a cached {@link GroupKey} or creates a new one based on {@link HttpRequest} information.
     */
    private static final class CacheableGroupKeyFactory
            implements Function<HttpRequest<HttpPayloadChunk>, GroupKey<HostAndPort>>, AsyncCloseable {

        private final ConcurrentMap<String, GroupKey<HostAndPort>> groupKeyCache = new ConcurrentHashMap<>();
        private final ExecutionContext executionContext;
        private final SslConfigProvider sslConfigProvider;

        CacheableGroupKeyFactory(final ExecutionContext executionContext, final SslConfigProvider sslConfigProvider) {
            this.executionContext = requireNonNull(executionContext);
            this.sslConfigProvider = sslConfigProvider;
        }

        @Override
        public GroupKey<HostAndPort> apply(final HttpRequest<HttpPayloadChunk> request) {
            final String host = request.getEffectiveHost();
            if (host == null) {
                throw new IllegalArgumentException(
                        "HttpRequest does not contain information about target server address." +
                        " Request-target: " + request.getRequestTarget() +
                        ", HOST header: " + request.getHeaders().get(HOST));
            }
            final int effectivePort = request.getEffectivePort();
            final int port = effectivePort >= 0 ? effectivePort :
                    sslConfigProvider.defaultPort(HttpScheme.from(request.getScheme()), host);
            final String authority = host + ':' + port;

            final GroupKey<HostAndPort> groupKey = groupKeyCache.get(authority);
            return groupKey != null ? groupKey : groupKeyCache.computeIfAbsent(authority, ignore ->
                    new DefaultGroupKey<>(HostAndPort.of(host, port), executionContext));
        }

        @Override
        public Completable closeAsync() {
            // Make a best effort to clear the map. Note that we don't attempt to resolve race conditions between
            // closing the Requester and in flight requests adding Keys to the map. We also don't attempt to remove
            // from the map if a request fails, or a request is made after the Requester is closed.
            return new Completable() {
                @Override
                protected void handleSubscribe(final Subscriber subscriber) {
                    subscriber.onSubscribe(IGNORE_CANCEL);
                    groupKeyCache.clear();
                    subscriber.onComplete();
                }
            };
        }
    }

    /**
     * Creates a new {@link DefaultHttpClientBuilder} with appropriate {@link SslConfig} for specified
     * {@link HostAndPort}.
     */
    private static final class ClientBuilderFactory implements BiFunction<GroupKey<HostAndPort>, HttpRequestMetaData,
            DefaultHttpClientBuilder<InetSocketAddress>> {

        private final DefaultHttpClientBuilder<InetSocketAddress> clientBuilder;
        private final SslConfigProvider sslConfigProvider;

        ClientBuilderFactory(final DefaultHttpClientBuilder<InetSocketAddress> clientBuilder,
                             final SslConfigProvider sslConfigProvider) {
            // Copy existing builder to prevent runtime changes after build() was invoked
            this.clientBuilder = new DefaultHttpClientBuilder<>(clientBuilder);
            this.sslConfigProvider = sslConfigProvider;
        }

        @Override
        public DefaultHttpClientBuilder<InetSocketAddress> apply(final GroupKey<HostAndPort> groupKey,
                                                                 final HttpRequestMetaData requestMetaData) {
            final HttpScheme scheme = HttpScheme.from(requestMetaData.getScheme());
            final HostAndPort hostAndPort = groupKey.getAddress();
            SslConfig sslConfig;
            switch (scheme) {
                case HTTP:
                    sslConfig = null;
                    break;
                case HTTPS:
                    sslConfig = sslConfigProvider.forHostAndPort(hostAndPort);
                    if (sslConfig == null) {
                        sslConfig = forClient(hostAndPort).build();
                    }
                    break;
                case NONE:
                    sslConfig = sslConfigProvider.forHostAndPort(hostAndPort);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown scheme: " + scheme);
            }

            return new DefaultHttpClientBuilder<>(clientBuilder)
                    .setSslConfig(sslConfig)
                    .addClientFilterFactory(c -> newHostHeaderFilter(hostAndPort, c));
        }
    }

    private static final class AddressParsingHttpRequester extends HttpRequester {

        private final HttpRequester requester;
        private final ListenableAsyncCloseable closeable;

        AddressParsingHttpRequester(final HttpRequester requester,
                                    final ListenableAsyncCloseable closeable) {
            this.requester = requireNonNull(requester);
            this.closeable = requireNonNull(closeable);
        }

        @Override
        public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
            return requester.request(request);
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return requester.getExecutionContext();
        }

        @Override
        public Completable onClose() {
            return closeable.onClose();
        }

        @Override
        public Completable closeAsync() {
            return closeable.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return closeable.closeAsyncGracefully();
        }
    }
}
