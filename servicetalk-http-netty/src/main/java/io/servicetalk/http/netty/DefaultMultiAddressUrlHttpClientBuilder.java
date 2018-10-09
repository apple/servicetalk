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

import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.DefaultGroupKey;
import io.servicetalk.client.api.GroupKey;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.ClientGroupFilterFunction;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpClientBuilder;
import io.servicetalk.http.api.HttpClientGroupFilterFactory;
import io.servicetalk.http.api.HttpConnectionFilterFactory;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpScheme;
import io.servicetalk.http.api.MultiAddressHttpClientBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.SslConfigProvider;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientGroup;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.RedirectingStreamingHttpClientGroup;
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
import static io.servicetalk.http.api.SslConfigProviders.plainByDefault;
import static io.servicetalk.transport.api.SslConfigBuilder.forClient;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static java.util.Objects.requireNonNull;

/**
 * A builder of {@link StreamingHttpClient} instances which have a capacity to call any server based on the parsed absolute-form
 * URL address information from each {@link StreamingHttpRequest}.
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
    private ExecutionContext executionContext = globalExecutionContext();
    private SslConfigProvider sslConfigProvider = plainByDefault();
    private ClientGroupFilterFunction<HostAndPort> clientGroupFilterFunction = ClientGroupFilterFunction.identity();
    private int maxRedirects = DEFAULT_MAX_REDIRECTS;
    private HttpClientGroupFilterFactory<HostAndPort> clientFilterFunction = HttpClientGroupFilterFactory.identity();
    @Nullable
    private Function<HostAndPort, CharSequence> hostHeaderTransformer;

    DefaultMultiAddressUrlHttpClientBuilder(
            final DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builderTemplate) {
        this.builderTemplate = requireNonNull(builderTemplate);
    }

    @Override
    public StreamingHttpClient buildStreaming() {
        final ExecutionContext executionContext = this.executionContext;
        final CompositeCloseable closeables = newCompositeCloseable();
        // Tracks StreamingHttpClient dependencies for clean-up on exception during buildStreaming
        final CompositeCloseable closeOnException = newCompositeCloseable();
        try {
            final ClientBuilderFactory clientBuilderFactory = new ClientBuilderFactory(builderTemplate,
                    sslConfigProvider, clientFilterFunction, hostHeaderTransformer, executionContext);

            final DefaultStreamingHttpRequestResponseFactory reqRespFactory =
                    new DefaultStreamingHttpRequestResponseFactory(executionContext.bufferAllocator(),
                            clientBuilderFactory.headersFactory());

            StreamingHttpClientGroup<HostAndPort> clientGroup = closeOnException.prepend(
                    clientGroupFilterFunction.apply(
                    newHttpClientGroup(reqRespFactory, (gk, md) ->
                            clientBuilderFactory.apply(gk, md).buildStreaming())));
            final CacheableGroupKeyFactory groupKeyFactory = closeables.prepend(closeOnException.prepend(
                    new CacheableGroupKeyFactory(executionContext, sslConfigProvider)));
            clientGroup = maxRedirects <= 0 ? clientGroup : new RedirectingStreamingHttpClientGroup<>(
                    clientGroup, groupKeyFactory, executionContext, maxRedirects);
            final StreamingHttpClient client = closeables.prepend(closeOnException.prepend(
                    clientGroup.asClient(groupKeyFactory, executionContext)));

            return new StreamingHttpClientWithDependencies(client, toListenableAsyncCloseable(closeables),
                    reqRespFactory);
        } catch (final Throwable t) {
            closeOnException.closeAsync().subscribe();
            throw t;
        }
    }

    /**
     * Returns a cached {@link GroupKey} or creates a new one based on {@link StreamingHttpRequest} information.
     */
    private static final class CacheableGroupKeyFactory
            implements Function<StreamingHttpRequest, GroupKey<HostAndPort>>, AsyncCloseable {

        private final ConcurrentMap<String, GroupKey<HostAndPort>> groupKeyCache = new ConcurrentHashMap<>();
        private final ExecutionContext executionContext;
        private final SslConfigProvider sslConfigProvider;

        CacheableGroupKeyFactory(final ExecutionContext executionContext, final SslConfigProvider sslConfigProvider) {
            this.executionContext = requireNonNull(executionContext);
            this.sslConfigProvider = sslConfigProvider;
        }

        @Override
        public GroupKey<HostAndPort> apply(final StreamingHttpRequest request) {
            final String host = request.effectiveHost();
            if (host == null) {
                throw new IllegalArgumentException(
                        "StreamingHttpRequest does not contain information about target server address." +
                                " Request-target: " + request.requestTarget() +
                                ", HOST header: " + request.headers().get(HOST));
            }
            final int effectivePort = request.effectivePort();
            final int port = effectivePort >= 0 ? effectivePort :
                    sslConfigProvider.defaultPort(HttpScheme.schemeForValue(request.scheme()), host);
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
     * Creates a new {@link HttpClientBuilder} with appropriate {@link SslConfig} for specified
     * {@link HostAndPort}.
     */
    private static final class ClientBuilderFactory implements BiFunction<GroupKey<HostAndPort>, HttpRequestMetaData,
            SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress>> {

        private final DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builderTemplate;
        private final SslConfigProvider sslConfigProvider;
        private final HttpClientGroupFilterFactory<HostAndPort> clientFilterFunction;
        @Nullable
        private final Function<HostAndPort, CharSequence> hostHeaderTransformer;
        private final ExecutionContext executionContext;

        @SuppressWarnings("unchecked")
        ClientBuilderFactory(
                final DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builderTemplate,
                final SslConfigProvider sslConfigProvider,
                final HttpClientGroupFilterFactory<HostAndPort> clientFilterFunction,
                @Nullable final Function<HostAndPort, CharSequence> hostHeaderTransformer,
                final ExecutionContext executionContext) {
            // Copy existing builder to prevent runtime changes after buildStreaming() was invoked
            this.builderTemplate = builderTemplate.copy();
            this.sslConfigProvider = sslConfigProvider;
            this.clientFilterFunction = clientFilterFunction;
            this.hostHeaderTransformer = hostHeaderTransformer;
            this.executionContext = executionContext;
        }

        @SuppressWarnings("unchecked")
        @Override
        public SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> apply(
                final GroupKey<HostAndPort> groupKey, final HttpRequestMetaData requestMetaData) {
            final HttpScheme scheme = HttpScheme.schemeForValue(requestMetaData.scheme());
            final HostAndPort hostAndPort = groupKey.address();
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
            return builder.executionContext(executionContext)
                    .sslConfig(sslConfig)
                    .appendClientFilter(clientFilterFunction.asClientFilter(groupKey.address()));
        }

        HttpHeadersFactory headersFactory() {
            return builderTemplate.getHeadersFactory();
        }
    }

    private static final class StreamingHttpClientWithDependencies extends StreamingHttpClient {

        private final StreamingHttpClient httpClient;
        private final ListenableAsyncCloseable closeable;

        StreamingHttpClientWithDependencies(final StreamingHttpClient httpClient,
                                            final ListenableAsyncCloseable closeable,
                                            final StreamingHttpRequestResponseFactory factory) {
            super(factory);
            this.httpClient = requireNonNull(httpClient);
            this.closeable = requireNonNull(closeable);
        }

        @Override
        public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
            return httpClient.request(request);
        }

        @Override
        public Single<? extends ReservedStreamingHttpConnection> reserveConnection(final StreamingHttpRequest request) {
            return httpClient.reserveConnection(request);
        }

        @Override
        public Single<? extends UpgradableStreamingHttpResponse> upgradeConnection(
                final StreamingHttpRequest request) {
            return httpClient.upgradeConnection(request);
        }

        @Override
        public ExecutionContext executionContext() {
            return httpClient.executionContext();
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
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> executionContext(
            final ExecutionContext context) {
        this.executionContext = requireNonNull(context);
        return this;
    }

    @Override
    public <T> MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> socketOption(
            final SocketOption<T> option, final T value) {
        builderTemplate.socketOption(option, value);
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
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> headersFactory(
            final HttpHeadersFactory headersFactory) {
        builderTemplate.headersFactory(headersFactory);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> maxInitialLineLength(
            final int maxInitialLineLength) {
        builderTemplate.maxInitialLineLength(maxInitialLineLength);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> maxHeaderSize(final int maxHeaderSize) {
        builderTemplate.maxHeaderSize(maxHeaderSize);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> headersEncodedSizeEstimate(
            final int headersEncodedSizeEstimate) {
        builderTemplate.headersEncodedSizeEstimate(headersEncodedSizeEstimate);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> trailersEncodedSizeEstimate(
            final int trailersEncodedSizeEstimate) {
        builderTemplate.trailersEncodedSizeEstimate(trailersEncodedSizeEstimate);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> maxPipelinedRequests(
            final int maxPipelinedRequests) {
        builderTemplate.maxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> appendConnectionFilter(
            final HttpConnectionFilterFactory factory) {
        builderTemplate.appendConnectionFilter(factory);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> appendConnectionFactoryFilter(
            final ConnectionFactoryFilter<InetSocketAddress, StreamingHttpConnection> factory) {
        builderTemplate.appendConnectionFactoryFilter(factory);
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
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> maxRedirects(final int maxRedirects) {
        this.maxRedirects = maxRedirects;
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> sslConfigProvider(
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
            final HttpClientGroupFilterFactory<HostAndPort> function) {
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
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> serviceDiscoverer(
            final ServiceDiscoverer<HostAndPort, InetSocketAddress,
                    ? extends ServiceDiscovererEvent<InetSocketAddress>> sd) {
        builderTemplate.serviceDiscoverer(sd);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> loadBalancerFactory(
            final LoadBalancerFactory<InetSocketAddress, StreamingHttpConnection> loadBalancerFactory) {
        builderTemplate.loadBalancerFactory(loadBalancerFactory);
        return this;
    }
}
