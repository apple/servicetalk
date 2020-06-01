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
import io.servicetalk.client.api.AutoRetryStrategyProvider;
import io.servicetalk.client.api.ClientGroup;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableCompletable;
import io.servicetalk.http.api.FilterableReservedStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.MultiAddressHttpClientBuilder;
import io.servicetalk.http.api.MultiAddressHttpClientFilterFactory;
import io.servicetalk.http.api.SingleAddressHttpClientSecurityConfigurator;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.netty.DefaultSingleAddressHttpClientBuilder.HttpClientBuildContext;
import io.servicetalk.http.utils.RedirectingHttpRequesterFilter;
import io.servicetalk.transport.api.ClientSecurityConfigurator;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.netty.handler.codec.http.HttpScheme.HTTP;
import static io.netty.handler.codec.http.HttpScheme.HTTPS;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toListenableAsyncCloseable;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverCompleteFromSource;
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
final class DefaultMultiAddressUrlHttpClientBuilder
        extends MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> {
    // https://tools.ietf.org/html/rfc2068#section-10.3 says:
    // A user agent SHOULD NOT automatically redirect a request more than 5 times,
    // since such redirects usually indicate an infinite loop.
    private static final int DEFAULT_MAX_REDIRECTS = 5;

    private static final String HTTPS_SCHEME = HTTPS.toString();

    private final DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builderTemplate;

    private int maxRedirects = DEFAULT_MAX_REDIRECTS;
    @Nullable
    private MultiAddressHttpClientFilterFactory<HostAndPort> clientFilterFactory;
    @Nullable
    private Function<HostAndPort, CharSequence> unresolvedAddressToHostFunction;
    @Nullable
    private BiConsumer<HostAndPort, ClientSecurityConfigurator> sslConfigFunction;

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
                    clientFilterFactory, unresolvedAddressToHostFunction, sslConfigFunction);

            final CachingKeyFactory keyFactory = closeables.prepend(new CachingKeyFactory());

            FilterableStreamingHttpClient urlClient = closeables.prepend(
                    new StreamingUrlHttpClient(buildContext.executionContext, clientFactory, keyFactory,
                            buildContext.reqRespFactory));

            // Need to wrap the top level client (group) in order for non-relative redirects to work
            urlClient = maxRedirects <= 0 ? urlClient :
                    new RedirectingHttpRequesterFilter(false, maxRedirects).create(urlClient);

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

        @Override
        public UrlKey apply(final HttpRequestMetaData metaData) {
            final String host = metaData.host();
            if (host == null) {
                throw new IllegalArgumentException(
                        "Request-target does not contain target host address: " + metaData.requestTarget() +
                                ", expected absolute-form URL");
            }

            final String scheme = metaData.scheme();
            if (scheme == null) {
                throw new IllegalArgumentException("Request-target does not contains scheme: " +
                        metaData.requestTarget() + ", expected absolute-form URL");
            }

            final int parsedPort = metaData.port();
            final int port = parsedPort >= 0 ? parsedPort : (HTTPS_SCHEME.equals(scheme) ? HTTPS : HTTP).port();

            metaData.requestTarget(absoluteToRelativeFormRequestTarget(metaData.requestTarget(), scheme, host));

            final String key = scheme + ':' + host + ':' + port;
            final UrlKey urlKey = urlKeyCache.get(key);
            return urlKey != null ? urlKey : urlKeyCache.computeIfAbsent(key, ignore ->
                    new UrlKey(scheme, HostAndPort.of(host, port)));
        }

        // This code is similar to io.servicetalk.http.utils.RedirectSingle#absoluteToRelativeFormRequestTarget
        // but cannot be shared because we don't have an internal module for http
        private static String absoluteToRelativeFormRequestTarget(final String requestTarget,
                                                                  final String scheme, final String host) {
            final int fromIndex = scheme.length() + 3 + host.length();  // +3 because of "://" delimiter after scheme
            final int relativeReferenceIdx = requestTarget.indexOf('/', fromIndex);
            return relativeReferenceIdx < 0 ? "/" : requestTarget.substring(relativeReferenceIdx);
        }

        @Override
        public Completable closeAsync() {
            // Make a best effort to clear the map. Note that we don't attempt to resolve race conditions between
            // closing the client and in flight requests adding Keys to the map. We also don't attempt to remove
            // from the map if a request fails, or a request is made after the client is closed.
            return new SubscribableCompletable() {
                @Override
                protected void handleSubscribe(final Subscriber subscriber) {
                    urlKeyCache.clear();
                    deliverCompleteFromSource(subscriber);
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

    private static final class ClientFactory implements Function<UrlKey, FilterableStreamingHttpClient> {

        private final DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builderTemplate;
        @Nullable
        private final MultiAddressHttpClientFilterFactory<HostAndPort> clientFilterFactory;
        @Nullable
        private final Function<HostAndPort, CharSequence> hostHeaderTransformer;
        @Nullable
        private BiConsumer<HostAndPort, ClientSecurityConfigurator> sslConfigFunction;

        ClientFactory(
                final DefaultSingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builderTemplate,
                @Nullable final MultiAddressHttpClientFilterFactory<HostAndPort> clientFilterFactory,
                @Nullable final Function<HostAndPort, CharSequence> hostHeaderTransformer,
                @Nullable final BiConsumer<HostAndPort, ClientSecurityConfigurator> sslConfigFunction) {
            this.builderTemplate = builderTemplate;
            this.clientFilterFactory = clientFilterFactory;
            this.hostHeaderTransformer = hostHeaderTransformer;
            this.sslConfigFunction = sslConfigFunction;
        }

        @Override
        public StreamingHttpClient apply(final UrlKey urlKey) {
            // Copy existing builder to prevent changes at runtime when concurrently creating clients for new addresses
            final HttpClientBuildContext<HostAndPort, InetSocketAddress> buildContext =
                    builderTemplate.copyBuildCtx(urlKey.hostAndPort);

            if (hostHeaderTransformer != null) {
                buildContext.builder.unresolvedAddressToHost(hostHeaderTransformer);
            }

            if (HTTPS_SCHEME.equalsIgnoreCase(urlKey.scheme)) {
                final SingleAddressHttpClientSecurityConfigurator<HostAndPort, InetSocketAddress> securityConfigurator =
                        buildContext.builder.secure();
                if (sslConfigFunction != null) {
                    sslConfigFunction.accept(urlKey.hostAndPort, securityConfigurator);
                }
                securityConfigurator.commit();
            }

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
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> protocols(HttpProtocolConfig... protocols) {
        builderTemplate.protocols(protocols);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> secure(
            final BiConsumer<HostAndPort, ClientSecurityConfigurator> sslConfigFunction) {
        this.sslConfigFunction = requireNonNull(sslConfigFunction);
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
            final ConnectionFactoryFilter<InetSocketAddress, FilterableStreamingHttpConnection> factory) {
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
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> autoRetryStrategy(
            final AutoRetryStrategyProvider autoRetryStrategyProvider) {
        builderTemplate.autoRetryStrategy(autoRetryStrategyProvider);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> maxRedirects(final int maxRedirects) {
        this.maxRedirects = maxRedirects;
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> unresolvedAddressToHost(
            Function<HostAndPort, CharSequence> unresolvedAddressToHostFunction) {
        this.unresolvedAddressToHostFunction = requireNonNull(unresolvedAddressToHostFunction);
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
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> retryServiceDiscoveryErrors(
            final BiIntFunction<Throwable, ? extends Completable> retryStrategy) {
        builderTemplate.retryServiceDiscoveryErrors(retryStrategy);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> loadBalancerFactory(
            final HttpLoadBalancerFactory<InetSocketAddress> loadBalancerFactory) {
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
