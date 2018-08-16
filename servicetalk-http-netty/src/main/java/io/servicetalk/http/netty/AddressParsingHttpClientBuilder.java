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
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpClientBuilder;
import io.servicetalk.http.api.HttpClientGroup;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponse;
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

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toListenableAsyncCloseable;
import static io.servicetalk.http.api.HttpClientGroups.newHttpClientGroup;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.netty.SslConfigProviders.plainByDefault;
import static io.servicetalk.transport.api.SslConfigBuilder.forClient;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static java.util.Objects.requireNonNull;
import static java.util.function.UnaryOperator.identity;

/**
 * A builder of {@link HttpClient} instances which have a capacity to call any server based on the parsed address
 * information from each {@link HttpRequest}.
 * <p>
 * It also provides a good set of default settings and configurations, which could be used by most users as-is or
 * could be overridden to address specific use cases.
 */
public final class AddressParsingHttpClientBuilder implements HttpClientBuilder {

    // https://tools.ietf.org/html/rfc2068#section-10.3 says:
    // A user agent SHOULD NOT automatically redirect a request more than 5 times,
    // since such redirections usually indicate an infinite loop.
    private static final int DEFAULT_MAX_REDIRECTS = 5;

    private final DefaultHttpClientBuilder<HostAndPort, InetSocketAddress> builderTemplate;
    private UnaryOperator<HttpClientGroup<HostAndPort>> clientGroupFilterFactory = identity();
    private int maxRedirects = DEFAULT_MAX_REDIRECTS;
    private SslConfigProvider sslConfigProvider = plainByDefault();

    // TODO incomplete build marker for DefaultHttpClientBuilder, to be removed when this class is moved to http-netty
    private static final HostAndPort DUMMY_HAP = HostAndPort.of("dummy.invalid", -1);

    /**
     * Create a new instance with a default {@link LoadBalancerFactory} and DNS {@link ServiceDiscoverer}.
     */
    public AddressParsingHttpClientBuilder() {
        builderTemplate = DefaultHttpClientBuilder.forSingleAddress(DUMMY_HAP);
    }

    /**
     * Create a new instance.
     *
     * @param loadBalancerFactory A {@link LoadBalancerFactory} which generates {@link LoadBalancer} objects.
     * @param serviceDiscoverer {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     */
    public AddressParsingHttpClientBuilder(
            final LoadBalancerFactory<InetSocketAddress, HttpConnection> loadBalancerFactory,
            final ServiceDiscoverer<HostAndPort, InetSocketAddress> serviceDiscoverer) {
        builderTemplate = DefaultHttpClientBuilder.forSingleAddress(loadBalancerFactory, serviceDiscoverer, DUMMY_HAP);
    }

    /**
     * Create a new instance with a default {@link LoadBalancerFactory}.
     *
     * @param serviceDiscoverer {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     */
    public AddressParsingHttpClientBuilder(
            final ServiceDiscoverer<HostAndPort, InetSocketAddress> serviceDiscoverer) {
        builderTemplate = DefaultHttpClientBuilder.forSingleAddress(serviceDiscoverer, DUMMY_HAP);
    }

    /**
     * Create a new instance with a DNS {@link ServiceDiscoverer}.
     *
     * @param loadBalancerFactory A {@link LoadBalancerFactory} which generates {@link LoadBalancer} objects.
     */
    public AddressParsingHttpClientBuilder(
            final LoadBalancerFactory<InetSocketAddress, HttpConnection> loadBalancerFactory) {
        builderTemplate = DefaultHttpClientBuilder.forSingleAddress(loadBalancerFactory, DUMMY_HAP);
    }

    /**
     * Set a {@link SslConfigProvider} for appropriate {@link SslConfig}s.
     *
     * @param sslConfigProvider A {@link SslConfigProvider} to use.
     * @return {@code this}.
     */
    public AddressParsingHttpClientBuilder setSslConfigProvider(final SslConfigProvider sslConfigProvider) {
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
    public <T> AddressParsingHttpClientBuilder setSocketOption(final SocketOption<T> option, final T value) {
        builderTemplate.setSocketOption(option, value);
        return this;
    }

    /**
     * Enable wire-logging for connections created by this builder. All wire events will be logged at trace level.
     *
     * @param loggerName A name of the logger to log wire events.
     * @return {@code this}.
     */
    public AddressParsingHttpClientBuilder enableWireLogging(final String loggerName) {
        builderTemplate.enableWireLogging(loggerName);
        return this;
    }

    /**
     * Disable previously configured wire-logging for connections created by this builder.
     * If wire-logging has not been configured before, this method has no effect.
     *
     * @return {@code this}.
     * @see #enableWireLogging(String)
     */
    public AddressParsingHttpClientBuilder disableWireLogging() {
        builderTemplate.disableWireLogging();
        return this;
    }

    /**
     * Set the {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding responses.
     *
     * @param headersFactory A {@link HttpHeadersFactory} to use.
     * @return {@code this}.
     */
    public AddressParsingHttpClientBuilder setHeadersFactory(final HttpHeadersFactory headersFactory) {
        builderTemplate.setHeadersFactory(headersFactory);
        return this;
    }

    /**
     * Set the maximum size of the initial HTTP line for created {@link HttpClient}.
     *
     * @param maxInitialLineLength A {@link HttpClient} will throw TooLongFrameException if the initial
     * HTTP line exceeds this length.
     * @return {@code this}.
     */
    public AddressParsingHttpClientBuilder setMaxInitialLineLength(final int maxInitialLineLength) {
        builderTemplate.setMaxInitialLineLength(maxInitialLineLength);
        return this;
    }

    /**
     * Set the maximum total size of HTTP headers, which could be send be created {@link HttpClient}.
     *
     * @param maxHeaderSize A {@link HttpClient} will throw TooLongFrameException if the total size of all
     * HTTP headers exceeds this length.
     * @return {@code this}.
     */
    public AddressParsingHttpClientBuilder setMaxHeaderSize(final int maxHeaderSize) {
        builderTemplate.setMaxHeaderSize(maxHeaderSize);
        return this;
    }

    /**
     * Set the value used to calculate an exponential moving average of the encoded size of the initial line and the
     * headers for a guess for future buffer allocations.
     *
     * @param headersEncodedSizeEstimate An estimated size of encoded initial line and headers.
     * @return {@code this}.
     */
    public AddressParsingHttpClientBuilder setHeadersEncodedSizeEstimate(
            final int headersEncodedSizeEstimate) {
        builderTemplate.setHeadersEncodedSizeEstimate(headersEncodedSizeEstimate);
        return this;
    }

    /**
     * Set the value used to calculate an exponential moving average of the encoded size of the trailers for a guess for
     * future buffer allocations.
     *
     * @param trailersEncodedSizeEstimate An estimated size of encoded trailers.
     * @return {@code this}.
     */
    public AddressParsingHttpClientBuilder setTrailersEncodedSizeEstimate(
            final int trailersEncodedSizeEstimate) {
        builderTemplate.setTrailersEncodedSizeEstimate(trailersEncodedSizeEstimate);
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
    public AddressParsingHttpClientBuilder setMaxPipelinedRequests(final int maxPipelinedRequests) {
        builderTemplate.setMaxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    /**
     * Set a {@link Function} which is used as a factory to filter/decorate {@link HttpClientGroup} used by created
     * {@link HttpClient}.
     * <p>
     * Filtering allows you to wrap {@link HttpClientGroup} and modify behavior during request/response processing.
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     *
     * @param clientGroupFilterFactory A {@link UnaryOperator} to decorate {@link HttpClientGroup} for the purpose of
     * filtering.
     * @return {@code this}.
     */
    public AddressParsingHttpClientBuilder setClientGroupFilterFactory(
            final UnaryOperator<HttpClientGroup<HostAndPort>> clientGroupFilterFactory) {
        this.clientGroupFilterFactory = requireNonNull(clientGroupFilterFactory);
        return this;
    }

    /**
     * Set a {@link Function} which is used as a factory to filter/decorate {@link HttpClient} used by created
     * {@link HttpClient}.
     * <p>
     * Filtering allows you to wrap {@link HttpClient} and modify behavior during request/response processing.
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     * <p>
     * Note this method will be used to decorate the result of
     * {@link HttpClientBuilder#build(ExecutionContext)} before it is returned to the
     * {@link HttpClientGroup}.
     *
     * @param clientFilterFactory A {@link BiFunction} to decorate {@link HttpClient} for the purpose of filtering.
     * @return {@code this}.
     */
    public AddressParsingHttpClientBuilder setClientFilterFactory(
            final BiFunction<HttpClient, Publisher<Object>, HttpClient> clientFilterFactory) {
        builderTemplate.setClientFilterFactory(clientFilterFactory);
        return this;
    }

    /**
     * Set a {@link Function} which is used as a factory to filter/decorate {@link HttpConnection} used by created
     * {@link HttpClient}.
     * <p>
     * Filtering allows you to wrap {@link HttpConnection} and modify behavior during request/response processing.
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     *
     * @param connectionFilterFactory A {@link UnaryOperator} to decorate {@link HttpConnection} for the purpose of
     * filtering.
     * @return {@code this}.
     */
    public AddressParsingHttpClientBuilder setConnectionFilterFactory(
            final UnaryOperator<HttpConnection> connectionFilterFactory) {
        builderTemplate.setConnectionFilterFactory(connectionFilterFactory);
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
    public AddressParsingHttpClientBuilder addClientFilterFactory(
            final BiFunction<HttpClient, Publisher<Object>, HttpClient> clientFilterFactory) {
        this.builderTemplate.addClientFilterFactory(clientFilterFactory);
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
    public AddressParsingHttpClientBuilder addClientFilterFactory(
            final Function<HttpClient, HttpClient> clientFilterFactory) {
        builderTemplate.addClientFilterFactory(clientFilterFactory);
        return this;
    }

    /**
     * Set a maximum number of redirects to follow.
     *
     * @param maxRedirects A maximum number of redirects to follow. Use a nonpositive number to disable redirects.
     * @return {@code this}.
     */
    public AddressParsingHttpClientBuilder setMaxRedirects(final int maxRedirects) {
        this.maxRedirects = maxRedirects;
        return this;
    }

    /**
     * Build a new {@link HttpClient}, using a default {@link ExecutionContext}.
     *
     * @return A new {@link HttpClient}.
     * @see #build(ExecutionContext)
     */
    @Override
    public HttpClient build() {
        return build(globalExecutionContext());
    }

    /**
     * Build a new {@link HttpClient}.
     *
     * @param executionContext A {@link ExecutionContext} used for {@link HttpClient#getExecutionContext()} and
     * to build new {@link HttpClient}s.
     * @return A new {@link HttpClient}.
     */
    @Override
    public HttpClient build(final ExecutionContext executionContext) {
        requireNonNull(executionContext);
        final CompositeCloseable closeables = newCompositeCloseable();
        try {

            final ClientBuilderFactory clientBuilderFactory =
                    new ClientBuilderFactory(builderTemplate, sslConfigProvider);
            HttpClientGroup<HostAndPort> clientGroup = closeables.prepend(clientGroupFilterFactory.apply(
                    closeables.prepend(newHttpClientGroup((gk, md) -> clientBuilderFactory.apply(gk, md)
                            .build(executionContext)))));
            final CacheableGroupKeyFactory groupKeyFactory =
                    closeables.prepend(new CacheableGroupKeyFactory(executionContext, sslConfigProvider));
            clientGroup = maxRedirects <= 0 ? clientGroup :
                    new RedirectingHttpClientGroup<>(clientGroup, groupKeyFactory, executionContext, maxRedirects);
            final HttpClient client = closeables.prepend(clientGroup.asClient(groupKeyFactory, executionContext));

            return new AddressParsingHttpClient(client, toListenableAsyncCloseable(closeables));
        } catch (final Exception e) {
            closeables.closeAsync().subscribe();
            throw e;
        }
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
            // closing the Client and in flight requests adding Keys to the map. We also don't attempt to remove
            // from the map if a request fails, or a request is made after the Client is closed.
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
            DefaultHttpClientBuilder<HostAndPort, InetSocketAddress>> {

        private final DefaultHttpClientBuilder<HostAndPort, InetSocketAddress> builderTemplate;
        private final SslConfigProvider sslConfigProvider;

        ClientBuilderFactory(final DefaultHttpClientBuilder<HostAndPort, InetSocketAddress> builderTemplate,
                             final SslConfigProvider sslConfigProvider) {
            // Copy existing builder to prevent runtime changes after build() was invoked
            this.builderTemplate = DefaultHttpClientBuilder.from(DUMMY_HAP, builderTemplate);
            this.sslConfigProvider = sslConfigProvider;
        }

        @Override
        public DefaultHttpClientBuilder<HostAndPort, InetSocketAddress> apply(final GroupKey<HostAndPort> groupKey,
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

            return DefaultHttpClientBuilder.from(hostAndPort, builderTemplate).setSslConfig(sslConfig);
        }
    }

    private static final class AddressParsingHttpClient extends HttpClient {

        private final HttpClient client;
        private final ListenableAsyncCloseable closeable;

        AddressParsingHttpClient(final HttpClient client,
                                 final ListenableAsyncCloseable closeable) {
            this.client = requireNonNull(client);
            this.closeable = requireNonNull(closeable);
        }

        @Override
        public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
            return client.request(request);
        }

        @Override
        public Single<? extends ReservedHttpConnection> reserveConnection(final HttpRequest<HttpPayloadChunk> request) {
            return client.reserveConnection(request);
        }

        @Override
        public Single<? extends UpgradableHttpResponse<HttpPayloadChunk>> upgradeConnection(final HttpRequest<HttpPayloadChunk> request) {
            return client.upgradeConnection(request);
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return client.getExecutionContext();
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
