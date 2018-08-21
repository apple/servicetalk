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
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.ClientGroupFilterFunction;
import io.servicetalk.http.api.ConnectionFilterFunction;
import io.servicetalk.http.api.GroupedClientFilterFunction;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpClientGroup;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.utils.RedirectingHttpClientGroup;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.SslConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toListenableAsyncCloseable;
import static io.servicetalk.http.api.HttpClientGroups.newHttpClientGroup;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.netty.SslConfigProviders.plainByDefault;
import static io.servicetalk.transport.api.SslConfigBuilder.forClient;
import static java.util.Objects.requireNonNull;

/**
 * A builder of {@link HttpClient} instances which have a capacity to call any server based on the parsed absolute-form
 * URL address information from each {@link HttpRequest}.
 * <p>
 * It also provides a good set of default settings and configurations, which could be used by most users as-is or
 * could be overridden to address specific use cases.
 * @see <a href="https://tools.ietf.org/html/rfc7230#section-5.3.2">absolute-form rfc7230#section-5.3.2</a>
 */
final class DefaultMultiAddressUrlHttpClientBuilder
        implements MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMultiAddressUrlHttpClientBuilder.class);

    // https://tools.ietf.org/html/rfc2068#section-10.3 says:
    // A user agent SHOULD NOT automatically redirect a request more than 5 times,
    // since such redirects usually indicate an infinite loop.
    private static final int DEFAULT_MAX_REDIRECTS = 5;

    private final DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builderTemplate;
    private SslConfigProvider sslConfigProvider = plainByDefault();
    private ClientGroupFilterFunction<HostAndPort> clientGroupFilterFunction = ClientGroupFilterFunction.identity();
    private int maxRedirects = DEFAULT_MAX_REDIRECTS;
    private GroupedClientFilterFunction<HostAndPort> clientFilterFunction = GroupedClientFilterFunction.identity();
    @Nullable
    private Function<HostAndPort, CharSequence> hostHeaderTransformer;

    DefaultMultiAddressUrlHttpClientBuilder(
            final DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builderTemplate) {
        this.builderTemplate = requireNonNull(builderTemplate);
    }

    @Override
    public HttpClient build(final ExecutionContext executionContext) {
        requireNonNull(executionContext);
        final CompositeCloseable closeables = newCompositeCloseable();
        // Tracks HttpClient dependencies for clean-up on exception during build
        final CompositeCloseable closeOnException = newCompositeCloseable();
        try {
            final ClientBuilderFactory clientBuilderFactory = new ClientBuilderFactory(builderTemplate,
                    sslConfigProvider, clientFilterFunction, hostHeaderTransformer);
            HttpClientGroup<HostAndPort> clientGroup = closeOnException.prepend(clientGroupFilterFunction.apply(
                    newHttpClientGroup((gk, md) -> clientBuilderFactory.apply(gk, md).build(executionContext))));
            final CacheableGroupKeyFactory groupKeyFactory = closeables.prepend(closeOnException.prepend(
                    new CacheableGroupKeyFactory(executionContext, sslConfigProvider)));
            clientGroup = maxRedirects <= 0 ? clientGroup :
                    new RedirectingHttpClientGroup<>(clientGroup, groupKeyFactory, executionContext, maxRedirects);
            final HttpClient client = closeables.prepend(closeOnException.prepend(
                    clientGroup.asClient(groupKeyFactory, executionContext)));

            return new HttpClientWithDependencies(client, toListenableAsyncCloseable(closeables));
        } catch (final Throwable t) {
            closeOnException.closeAsync().subscribe();
            throw t;
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
            // closing the client and in flight requests adding Keys to the map. We also don't attempt to remove
            // from the map if a request fails, or a request is made after the client is closed.
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
     * Creates a new {@link BaseHttpClientBuilder} with appropriate {@link SslConfig} for specified
     * {@link HostAndPort}.
     */
    private static final class ClientBuilderFactory implements BiFunction<GroupKey<HostAndPort>, HttpRequestMetaData,
            SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress>> {

        private final DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builderTemplate;
        private final SslConfigProvider sslConfigProvider;
        private final GroupedClientFilterFunction<HostAndPort> clientFilterFunction;
        @Nullable
        private final Function<HostAndPort, CharSequence> hostHeaderTransformer;

        @SuppressWarnings("unchecked")
        ClientBuilderFactory(
                final DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builderTemplate,
                final SslConfigProvider sslConfigProvider,
                final GroupedClientFilterFunction<HostAndPort> clientFilterFunction,
                @Nullable final Function<HostAndPort, CharSequence> hostHeaderTransformer) {
            // Copy existing builder to prevent runtime changes after build() was invoked
            this.builderTemplate = builderTemplate.copy();
            this.sslConfigProvider = sslConfigProvider;
            this.clientFilterFunction = clientFilterFunction;
            this.hostHeaderTransformer = hostHeaderTransformer;
        }

        @SuppressWarnings("unchecked")
        @Override
        public SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> apply(
                final GroupKey<HostAndPort> groupKey, final HttpRequestMetaData requestMetaData) {
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

            // Copy existing builder to prevent changes at runtime when concurrently creating clients for new addresses
            SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder = builderTemplate.copy(hostAndPort);
            if (hostHeaderTransformer != null) {
                try {
                    builder.enableHostHeaderFallback(hostHeaderTransformer.apply(hostAndPort));
                } catch (Exception e) {
                    LOGGER.error("Failed to transform address for host header override", e);
                }
            }
            return builder
                    .setSslConfig(sslConfig)
                    .appendClientFilter(clientFilterFunction.asClientFilter(groupKey.getAddress()));
        }
    }

    private static final class HttpClientWithDependencies extends HttpClient {

        private final HttpClient httpClient;
        private final ListenableAsyncCloseable closeable;

        HttpClientWithDependencies(final HttpClient httpClient,
                                   final ListenableAsyncCloseable closeable) {
            this.httpClient = requireNonNull(httpClient);
            this.closeable = requireNonNull(closeable);
        }

        @Override
        public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
            return httpClient.request(request);
        }

        @Override
        public Single<? extends ReservedHttpConnection> reserveConnection(final HttpRequest<HttpPayloadChunk> request) {
            return httpClient.reserveConnection(request);
        }

        @Override
        public Single<? extends UpgradableHttpResponse<HttpPayloadChunk>> upgradeConnection(
                final HttpRequest<HttpPayloadChunk> request) {
            return httpClient.upgradeConnection(request);
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return httpClient.getExecutionContext();
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

    @Override
    public <T> MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> setSocketOption(
            final SocketOption<T> option, final T value) {
        builderTemplate.setSocketOption(option, value);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> enableWireLogging(final String loggerName) {
        builderTemplate.enableWireLogging(loggerName);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> disableWireLogging() {
        builderTemplate.disableWireLogging();
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> setHeadersFactory(
            final HttpHeadersFactory headersFactory) {
        builderTemplate.setHeadersFactory(headersFactory);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> setMaxInitialLineLength(
            final int maxInitialLineLength) {
        builderTemplate.setMaxInitialLineLength(maxInitialLineLength);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> setMaxHeaderSize(final int maxHeaderSize) {
        builderTemplate.setMaxHeaderSize(maxHeaderSize);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> setHeadersEncodedSizeEstimate(
            final int headersEncodedSizeEstimate) {
        builderTemplate.setHeadersEncodedSizeEstimate(headersEncodedSizeEstimate);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> setTrailersEncodedSizeEstimate(
            final int trailersEncodedSizeEstimate) {
        builderTemplate.setTrailersEncodedSizeEstimate(trailersEncodedSizeEstimate);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> setMaxPipelinedRequests(
            final int maxPipelinedRequests) {
        builderTemplate.setMaxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> appendConnectionFilter(
            final ConnectionFilterFunction function) {
        builderTemplate.appendConnectionFilter(function);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> disableHostHeaderFallback() {
        builderTemplate.disableHostHeaderFallback();
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> disableWaitForLoadBalancer() {
        builderTemplate.disableWaitForLoadBalancer();
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> setMaxRedirects(final int maxRedirects) {
        this.maxRedirects = maxRedirects;
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> setSslConfigProvider(
            final SslConfigProvider sslConfigProvider) {
        this.sslConfigProvider = requireNonNull(sslConfigProvider);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> enableHostHeaderFallback(
            final Function<HostAndPort, CharSequence> hostHeaderTransformer) {
        this.hostHeaderTransformer = requireNonNull(hostHeaderTransformer);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> appendClientFilter(
            final GroupedClientFilterFunction<HostAndPort> function) {
        clientFilterFunction = clientFilterFunction.append(requireNonNull(function));
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> appendClientGroupFilter(
            final ClientGroupFilterFunction<HostAndPort> function) {
        clientGroupFilterFunction = clientGroupFilterFunction.append(requireNonNull(function));
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> setServiceDiscoverer(
            final ServiceDiscoverer<HostAndPort, InetSocketAddress> serviceDiscoverer) {
        builderTemplate.setServiceDiscoverer(serviceDiscoverer);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> setLoadBalancerFactory(
            final LoadBalancerFactory<InetSocketAddress, HttpConnection> loadBalancerFactory) {
        builderTemplate.setLoadBalancerFactory(loadBalancerFactory);
        return this;
    }
}
