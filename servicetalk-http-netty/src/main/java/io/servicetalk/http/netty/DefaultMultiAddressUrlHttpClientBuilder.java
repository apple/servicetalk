/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.client.api.ClientGroup;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableCompletable;
import io.servicetalk.http.api.FilterableReservedStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.MultiAddressHttpClientBuilder;
import io.servicetalk.http.api.MultiAddressHttpClientFilterFactory;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.SslConfigProvider;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.netty.DefaultSingleAddressHttpClientBuilder.HttpClientBuildContext;
import io.servicetalk.http.utils.RedirectingHttpRequesterFilter;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.SslConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toListenableAsyncCloseable;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.SslConfigProviders.plainByDefault;
import static io.servicetalk.transport.api.SslConfigBuilder.forClient;
import static java.util.Objects.requireNonNull;

/**
 * A builder of {@link StreamingHttpClient} instances which have a capacity to call any server based on the parsed
 * absolute-form URL address information from each {@link StreamingHttpRequest}.
 * <p>
 * It also provides a good set of default settings and configurations, which could be used by most users as-is or
 * could be overridden to address specific use cases.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7230#section-5.3.2">absolute-form rfc7230#section-5.3.2</a>
 */
final class DefaultMultiAddressUrlHttpClientBuilder extends MultiAddressHttpClientBuilder<HostAndPort,
        InetSocketAddress> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMultiAddressUrlHttpClientBuilder.class);

    // https://tools.ietf.org/html/rfc2068#section-10.3 says:
    // A user agent SHOULD NOT automatically redirect a request more than 5 times,
    // since such redirects usually indicate an infinite loop.
    private static final int DEFAULT_MAX_REDIRECTS = 5;

    private final DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builderTemplate;
    private SslConfigProvider sslConfigProvider = plainByDefault();
    private int maxRedirects = DEFAULT_MAX_REDIRECTS;
    @Nullable
    private MultiAddressHttpClientFilterFactory<HostAndPort> clientFilterFactory;
    @Nullable
    private Function<HostAndPort, CharSequence> hostHeaderTransformer;

    DefaultMultiAddressUrlHttpClientBuilder(
            final DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builderTemplate) {
        this.builderTemplate = requireNonNull(builderTemplate);
    }

    @Override
    public StreamingHttpClient buildStreaming() {
        final CompositeCloseable closeables = newCompositeCloseable();
        try {
            final HttpClientBuildContext<HostAndPort, InetSocketAddress> buildContext = builderTemplate.copyBuildCtx();

            final ClientFactory clientFactory = new ClientFactory(buildContext.builder,
                    sslConfigProvider, clientFilterFactory, hostHeaderTransformer);

            CachingKeyFactory keyFactory = closeables.prepend(new CachingKeyFactory(sslConfigProvider));

            FilterableStreamingHttpClient urlClient = closeables.prepend(
                    new StreamingUrlHttpClient(buildContext.executionContext, clientFactory, keyFactory,
                            buildContext.reqRespFactory));

            // Need to wrap the top level client (group) in order for non-relative redirects to work
            urlClient = maxRedirects <= 0 ? urlClient :
                    new RedirectingHttpRequesterFilter(false, maxRedirects).create(urlClient, empty());

            return new FilterableClientToClient(urlClient, buildContext.executionContext.executionStrategy(),
                    buildContext.builder.buildStrategyInfluencerForClient(
                            buildContext.executionContext.executionStrategy()));
        } catch (final Throwable t) {
            closeables.closeAsync().subscribe();
            throw t;
        }
    }

    /**
     * Returns a cached {@link UrlKey} or creates a new one based on {@link StreamingHttpRequest} information.
     */
    private static final class CachingKeyFactory
            implements Function<HttpRequestMetaData, UrlKey>, AsyncCloseable {

        private final ConcurrentMap<String, UrlKey> urlKeyCache = new ConcurrentHashMap<>();
        private final SslConfigProvider sslConfigProvider;

        CachingKeyFactory(final SslConfigProvider sslConfigProvider) {
            this.sslConfigProvider = sslConfigProvider;
        }

        @Override
        public UrlKey apply(final HttpRequestMetaData metaData) {
            final String host = metaData.effectiveHost();
            if (host == null) {
                throw new IllegalArgumentException(
                        "StreamingHttpRequest does not contain information about target server address." +
                                " Request-target: " + metaData.requestTarget() +
                                ", HOST header: " + metaData.headers().get(HOST));
            }
            final int effectivePort = metaData.effectivePort();
            final int port = effectivePort >= 0 ? effectivePort :
                    sslConfigProvider.defaultPort(metaData.scheme(), host);
            final String key = metaData.scheme() + host + ':' + port;
            final UrlKey urlKey = urlKeyCache.get(key);
            return urlKey != null ? urlKey : urlKeyCache.computeIfAbsent(key, ignore ->
                    new UrlKey(metaData.scheme(), HostAndPort.of(host, port)));
        }

        @Override
        public Completable closeAsync() {
            // Make a best effort to clear the map. Note that we don't attempt to resolve race conditions between
            // closing the client and in flight requests adding Keys to the map. We also don't attempt to remove
            // from the map if a request fails, or a request is made after the client is closed.
            return new SubscribableCompletable() {
                @Override
                protected void handleSubscribe(final Subscriber subscriber) {
                    subscriber.onSubscribe(IGNORE_CANCEL);
                    urlKeyCache.clear();
                    subscriber.onComplete();
                }
            };
        }
    }

    private static class UrlKey {

        @Nullable
        final String scheme;
        final HostAndPort hostAndPort;

        UrlKey(@Nullable final String scheme, final HostAndPort hostAndPort) {
            this.scheme = scheme;
            this.hostAndPort = hostAndPort;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final UrlKey urlKey = (UrlKey) o;
            return Objects.equals(scheme, urlKey.scheme) && hostAndPort.equals(urlKey.hostAndPort);
        }

        @Override
        public int hashCode() {
            return 31 * hostAndPort.hashCode() + Objects.hashCode(scheme);
        }
    }

    /**
     * Creates a new {@link SingleAddressHttpClientBuilder} with appropriate {@link SslConfig} for specified
     * {@link HostAndPort}.
     */
    private static final class ClientFactory implements Function<UrlKey, FilterableStreamingHttpClient> {

        private final DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builderTemplate;
        private final SslConfigProvider sslConfigProvider;
        @Nullable
        private final MultiAddressHttpClientFilterFactory<HostAndPort> clientFilterFactory;
        @Nullable
        private final Function<HostAndPort, CharSequence> hostHeaderTransformer;

        ClientFactory(
                final DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builderTemplate,
                final SslConfigProvider sslConfigProvider,
                @Nullable final MultiAddressHttpClientFilterFactory<HostAndPort> clientFilterFactory,
                @Nullable final Function<HostAndPort, CharSequence> hostHeaderTransformer) {
            this.builderTemplate = builderTemplate;
            this.sslConfigProvider = sslConfigProvider;
            this.clientFilterFactory = clientFilterFactory;
            this.hostHeaderTransformer = hostHeaderTransformer;
        }

        @Override
        public StreamingHttpClient apply(final UrlKey urlKey) {
            SslConfig sslConfig;
            if ("http".equalsIgnoreCase(urlKey.scheme)) {
                sslConfig = null;
            } else if ("https".equalsIgnoreCase(urlKey.scheme)) {
                sslConfig = sslConfigProvider.forHostAndPort(urlKey.hostAndPort);
                if (sslConfig == null) {
                    // fallback to the default SSL config if none was provided by user
                    sslConfig = forClient(urlKey.hostAndPort).build();
                }
            } else {
                sslConfig = sslConfigProvider.forHostAndPort(urlKey.hostAndPort);
            }

            // Copy existing builder to prevent changes at runtime when concurrently creating clients for new addresses
            final HttpClientBuildContext<HostAndPort, InetSocketAddress> buildContext =
                    builderTemplate.copyBuildCtx(urlKey.hostAndPort);

            if (hostHeaderTransformer != null) {
                try {
                    buildContext.builder.enableHostHeaderFallback(hostHeaderTransformer.apply(urlKey.hostAndPort));
                } catch (Exception e) {
                    LOGGER.error("Failed to transform address for host header override", e);
                }
            }
            buildContext.builder.sslConfig(sslConfig);
            if (clientFilterFactory != null) {
                buildContext.builder.appendClientFilter(clientFilterFactory.asClientFilter(urlKey.hostAndPort));
            }
            return buildContext.build();
        }
    }

    private static final class StreamingUrlHttpClient implements FilterableStreamingHttpClient {
        private final HttpExecutionContext executionContext;
        private final StreamingHttpRequestResponseFactory reqRespFactory;
        private final ClientGroup<UrlKey, FilterableStreamingHttpClient> group;
        private final CachingKeyFactory keyFactory;
        private final ListenableAsyncCloseable closeable;

        StreamingUrlHttpClient(final HttpExecutionContext executionContext,
                               final Function<UrlKey, FilterableStreamingHttpClient> clientFactory,
                               final CachingKeyFactory keyFactory,
                               final StreamingHttpRequestResponseFactory reqRespFactory) {
            this.reqRespFactory = requireNonNull(reqRespFactory);
            this.group = ClientGroup.from(clientFactory);
            this.keyFactory = keyFactory;
            CompositeCloseable compositeCloseable = newCompositeCloseable();
            compositeCloseable.append(group);
            compositeCloseable.append(keyFactory);
            closeable = toListenableAsyncCloseable(compositeCloseable);
            this.executionContext = requireNonNull(executionContext);
        }

        private FilterableStreamingHttpClient selectClient(
                HttpRequestMetaData metaData) {
            return group.get(keyFactory.apply(metaData));
        }

        @Override
        public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                final HttpExecutionStrategy strategy, final HttpRequestMetaData metaData) {
            return defer(() -> selectClient(metaData).reserveConnection(strategy, metaData).subscribeShareContext());
        }

        @Override
        public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                     final StreamingHttpRequest request) {
            return defer(() -> selectClient(request).request(strategy, request).subscribeShareContext());
        }

        @Override
        public HttpExecutionContext executionContext() {
            return executionContext;
        }

        @Override
        public StreamingHttpResponseFactory httpResponseFactory() {
            return reqRespFactory;
        }

        @Override
        public void close() throws Exception {
            closeable.closeAsync().toFuture().get();
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

        @Override
        public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return reqRespFactory.newRequest(method, requestTarget);
        }
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> ioExecutor(final IoExecutor ioExecutor) {
        builderTemplate.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> bufferAllocator(
            final BufferAllocator allocator) {
        builderTemplate.bufferAllocator(allocator);
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
            final StreamingHttpConnectionFilterFactory factory) {
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
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> appendClientFilter(
            final StreamingHttpClientFilterFactory function) {
        if (clientFilterFactory == null) {
            clientFilterFactory = function.asMultiAddressClientFilter();
        } else {
            clientFilterFactory = clientFilterFactory.append(function.asMultiAddressClientFilter());
        }
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
            final MultiAddressHttpClientFilterFactory<HostAndPort> function) {
        if (clientFilterFactory == null) {
            clientFilterFactory = requireNonNull(function);
        } else {
            clientFilterFactory = clientFilterFactory.append(requireNonNull(function));
        }
        builderTemplate.appendToStrategyInfluencer(function);
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

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> executionStrategy(
            final HttpExecutionStrategy strategy) {
        builderTemplate.executionStrategy(strategy);
        return this;
    }
}
