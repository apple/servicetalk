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
import io.servicetalk.http.api.HttpClientFilterFactory;
import io.servicetalk.http.api.HttpConnectionFilterFactory;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.MultiAddressHttpClientBuilder;
import io.servicetalk.http.api.MultiAddressHttpClientFilterFactory;
import io.servicetalk.http.api.ReservedStreamingHttpConnectionFilter;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.SslConfigProvider;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestFunction;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
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
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toListenableAsyncCloseable;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.MultiAddressHttpClientFilterFactory.identity;
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
    private MultiAddressHttpClientFilterFactory<HostAndPort> clientFilterFunction = identity();
    @Nullable
    private Function<HostAndPort, CharSequence> hostHeaderTransformer;

    DefaultMultiAddressUrlHttpClientBuilder(
            final DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builderTemplate) {
        this.builderTemplate = requireNonNull(builderTemplate);
    }

    // This is only to be called by HttpClientBuilder.buildStreaming()
    @Override
    protected <T> T buildFilterChain(final BiFunction<StreamingHttpClientFilter, HttpExecutionStrategy, T> assembler) {
        final CompositeCloseable closeables = newCompositeCloseable();
        CachingKeyFactory keyFactory = null;
        try {
            final HttpClientBuildContext<HostAndPort, InetSocketAddress> buildContext = builderTemplate.copyBuildCtx();

            final ClientFilterFactory clientFilterFactory = new ClientFilterFactory(buildContext.builder,
                    sslConfigProvider, clientFilterFunction, hostHeaderTransformer);

            keyFactory = closeables.prepend(new CachingKeyFactory(sslConfigProvider));

            StreamingHttpClientFilter filterChain = closeables.prepend(
                    new StreamingUrlHttpClientFilter(buildContext.reqRespFactory, clientFilterFactory, keyFactory));

            // Need to wrap the top level client (group) in order for non-relative redirects to work
            filterChain = maxRedirects <= 0 ? filterChain :
                    new RedirectingHttpRequesterFilter(false, maxRedirects).create(filterChain);

            filterChain = new StreamingHttpClientWithDependencies(filterChain, toListenableAsyncCloseable(closeables));

            return assembler.apply(filterChain, buildContext.executionStrategy());
        } catch (final Throwable t) {
            if (keyFactory != null) {
                keyFactory.closeAsync().subscribe();
            }
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
    private static final class ClientFilterFactory implements Function<UrlKey, StreamingHttpClientFilter> {

        private final DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builderTemplate;
        private final SslConfigProvider sslConfigProvider;
        private final MultiAddressHttpClientFilterFactory<HostAndPort> clientFilterFunction;
        @Nullable
        private final Function<HostAndPort, CharSequence> hostHeaderTransformer;

        ClientFilterFactory(
                final DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builderTemplate,
                final SslConfigProvider sslConfigProvider,
                final MultiAddressHttpClientFilterFactory<HostAndPort> clientFilterFunction,
                @Nullable final Function<HostAndPort, CharSequence> hostHeaderTransformer) {
            this.builderTemplate = builderTemplate;
            this.sslConfigProvider = sslConfigProvider;
            this.clientFilterFunction = clientFilterFunction;
            this.hostHeaderTransformer = hostHeaderTransformer;
        }

        @Override
        public StreamingHttpClientFilter apply(final UrlKey urlKey) {
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
            buildContext.builder
                    .sslConfig(sslConfig)
                    .appendClientFilter(clientFilterFunction.asClientFilter(urlKey.hostAndPort));
            return buildContext.build((filter, __) -> filter);
        }
    }

    private static class StreamingUrlHttpClientFilter extends StreamingHttpClientFilter {

        private final ClientGroup<UrlKey, StreamingHttpClientFilter> group;
        private final CachingKeyFactory keyFactory;

        StreamingUrlHttpClientFilter(final StreamingHttpRequestResponseFactory reqRespFactory,
                                     final Function<UrlKey, StreamingHttpClientFilter> clientFactory,
                                     final CachingKeyFactory keyFactory) {
            super(terminal(reqRespFactory));
            this.group = ClientGroup.from(clientFactory);
            this.keyFactory = keyFactory;
        }

        private StreamingHttpClientFilter selectClient(HttpRequestMetaData metaData) {
            return group.get(keyFactory.apply(metaData));
        }

        @Override
        protected Single<ReservedStreamingHttpConnectionFilter> reserveConnection(
                final StreamingHttpClientFilter terminalDelegate,
                final HttpExecutionStrategy strategy,
                final HttpRequestMetaData metaData) {
            // Don't call the terminal delegate!
            return defer(() -> selectClient(metaData).reserveConnection(strategy, metaData).subscribeShareContext());
        }

        @Override
        protected Single<StreamingHttpResponse> request(final StreamingHttpRequestFunction terminalDelegate,
                                                        final HttpExecutionStrategy strategy,
                                                        final StreamingHttpRequest request) {
            // Don't call the terminal delegate!
            return defer(() -> selectClient(request).request(strategy, request).subscribeShareContext());
        }

        @Override
        public Completable onClose() {
            return group.onClose();
        }

        @Override
        public Completable closeAsync() {
            return group.closeAsync();
        }
    }

    private static final class StreamingHttpClientWithDependencies extends StreamingHttpClientFilter {

        private final ListenableAsyncCloseable closeable;

        StreamingHttpClientWithDependencies(final StreamingHttpClientFilter next,
                                            final ListenableAsyncCloseable closeable) {
            super(requireNonNull(next));
            this.closeable = requireNonNull(closeable);
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
            final HttpConnectionFilterFactory factory) {
        builderTemplate.appendConnectionFilter(factory);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> appendConnectionFactoryFilter(
            final ConnectionFactoryFilter<InetSocketAddress, StreamingHttpConnectionFilter> factory) {
        builderTemplate.appendConnectionFactoryFilter(factory);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> appendClientFilter(
            final HttpClientFilterFactory function) {
        clientFilterFunction = clientFilterFunction.append(function.asMultiAddressClientFilter());
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
        clientFilterFunction = clientFilterFunction.append(requireNonNull(function));
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
            final LoadBalancerFactory<InetSocketAddress, StreamingHttpConnectionFilter> loadBalancerFactory) {
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
