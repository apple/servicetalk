/*
 * Copyright © 2018-2019, 2021-2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableCompletable;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.FilterableReservedStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.MultiAddressHttpClientBuilder;
import io.servicetalk.http.api.RedirectConfig;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.utils.RedirectingHttpRequesterFilter;
import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.netty.handler.codec.http.HttpScheme.HTTP;
import static io.netty.handler.codec.http.HttpScheme.HTTPS;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toListenableAsyncCloseable;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverCompleteFromSource;
import static io.servicetalk.http.api.HttpContextKeys.HTTP_EXECUTION_STRATEGY_KEY;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadAll;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.netty.DefaultSingleAddressHttpClientBuilder.setExecutionContext;
import static java.util.Objects.requireNonNull;

/**
 * A builder of {@link StreamingHttpClient} instances which have a capacity to call any server based on the parsed
 * absolute-form URL address information from each {@link StreamingHttpRequest}.
 * <p>
 * If {@link HttpRequestMetaData#requestTarget()} is not an absolute-form URL, a {@link MalformedURLException} will be
 * returned or thrown.
 * <p>
 * It also provides a good set of default settings and configurations, which could be used by most users as-is or
 * could be overridden to address specific use cases.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7230#section-5.3.2">absolute-form rfc7230#section-5.3.2</a>
 */
final class DefaultMultiAddressUrlHttpClientBuilder
        implements MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMultiAddressUrlHttpClientBuilder.class);

    private static final String HTTPS_SCHEME = HTTPS.toString();

    private final Function<HostAndPort, SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress>> builderFactory;
    private final HttpExecutionContextBuilder executionContextBuilder = new HttpExecutionContextBuilder();

    @Nullable
    private HttpHeadersFactory headersFactory;
    @Nullable
    private RedirectConfig redirectConfig;
    @Nullable
    private SingleAddressInitializer<HostAndPort, InetSocketAddress> singleAddressInitializer;

    DefaultMultiAddressUrlHttpClientBuilder(
            final Function<HostAndPort, SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress>> bFactory) {
        this.builderFactory = requireNonNull(bFactory);
    }

    @Override
    public StreamingHttpClient buildStreaming() {
        final CompositeCloseable closeables = newCompositeCloseable();
        try {
            final HttpExecutionContext executionContext = executionContextBuilder.build();
            final ClientFactory clientFactory = new ClientFactory(builderFactory, executionContext,
                    singleAddressInitializer);
            final CachingKeyFactory keyFactory = closeables.prepend(new CachingKeyFactory());
            final HttpHeadersFactory headersFactory = this.headersFactory;
            FilterableStreamingHttpClient urlClient = closeables.prepend(
                    new StreamingUrlHttpClient(executionContext, clientFactory, keyFactory,
                            new DefaultStreamingHttpRequestResponseFactory(executionContext.bufferAllocator(),
                                    headersFactory != null ? headersFactory : DefaultHttpHeadersFactory.INSTANCE,
                                    HTTP_1_1)));

            // Need to wrap the top level client (group) in order for non-relative redirects to work
            urlClient = redirectConfig == null ? urlClient :
                    new RedirectingHttpRequesterFilter(redirectConfig).create(urlClient);

            LOGGER.debug("Multi-address client created with base strategy {}", executionContext.executionStrategy());
            return new FilterableClientToClient(urlClient, executionContext.executionStrategy());
        } catch (final Throwable t) {
            closeables.closeAsync().subscribe();
            throw t;
        }
    }

    /**
     * Returns a cached {@link UrlKey} or creates a new one based on {@link StreamingHttpRequest} information.
     */
    private static final class CachingKeyFactory implements AsyncCloseable {

        private final ConcurrentMap<String, UrlKey> urlKeyCache = new ConcurrentHashMap<>();

        public UrlKey get(final HttpRequestMetaData metaData) throws MalformedURLException {
            final String host = metaData.host();
            if (host == null) {
                throw new MalformedURLException(
                        "Request-target does not contain target host address: " + metaData.requestTarget() +
                                ", expected absolute-form URL");
            }

            final String scheme = metaData.scheme();
            if (scheme == null) {
                throw new MalformedURLException("Request-target does not contains scheme: " +
                        metaData.requestTarget() + ", expected absolute-form URL");
            }

            final int parsedPort = metaData.port();
            final int port = parsedPort >= 0 ? parsedPort :
                    (HTTPS_SCHEME.equalsIgnoreCase(scheme) ? HTTPS : HTTP).port();

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

    private static final class UrlKey {
        final String scheme;
        final HostAndPort hostAndPort;

        UrlKey(final String scheme, final HostAndPort hostAndPort) {
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
            return scheme.equals(urlKey.scheme) && hostAndPort.equals(urlKey.hostAndPort);
        }

        @Override
        public int hashCode() {
            return 31 * hostAndPort.hashCode() + scheme.hashCode();
        }
    }

    private static final class ClientFactory implements Function<UrlKey, FilterableStreamingHttpClient> {
        private static final ClientSslConfig DEFAULT_CLIENT_SSL_CONFIG = new ClientSslConfigBuilder().build();
        private final Function<HostAndPort, SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress>>
                builderFactory;
        private final HttpExecutionContext executionContext;
        @Nullable
        private final SingleAddressInitializer<HostAndPort, InetSocketAddress> singleAddressInitializer;

        ClientFactory(final Function<HostAndPort, SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress>>
                        builderFactory,
                final HttpExecutionContext executionContext,
                @Nullable final SingleAddressInitializer<HostAndPort, InetSocketAddress> singleAddressInitializer) {
            this.builderFactory = builderFactory;
            this.executionContext = executionContext;
            this.singleAddressInitializer = singleAddressInitializer;
        }

        @Override
        public StreamingHttpClient apply(final UrlKey urlKey) {
            final SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                    requireNonNull(builderFactory.apply(urlKey.hostAndPort));

            setExecutionContext(builder, executionContext);
            if (HTTPS_SCHEME.equalsIgnoreCase(urlKey.scheme)) {
                builder.sslConfig(DEFAULT_CLIENT_SSL_CONFIG);
            }

            builder.appendClientFilter(MultiAddressStrategyWrapper.INSTANCE);

            if (singleAddressInitializer != null) {
                singleAddressInitializer.initialize(urlKey.scheme, urlKey.hostAndPort, builder);
            }

            return builder.buildStreaming();
        }
    }

    private static final class MultiAddressStrategyWrapper implements StreamingHttpClientFilterFactory {

        static final StreamingHttpClientFilterFactory INSTANCE = new MultiAddressStrategyWrapper();

        private MultiAddressStrategyWrapper() {
            // Singleton
        }

        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {
                @Override
                protected Single<StreamingHttpResponse> request(
                        final StreamingHttpRequester delegate, final StreamingHttpRequest request) {
                    return defer(() -> {
                        HttpExecutionStrategy singleStrategy = client.executionContext().executionStrategy();
                        HttpExecutionStrategy requestStrategy =
                                request.context().getOrDefault(HTTP_EXECUTION_STRATEGY_KEY, defaultStrategy());
                        HttpExecutionStrategy useStrategy =
                                null == requestStrategy || defaultStrategy() == requestStrategy ?
                                        offloadAll() :
                                        defaultStrategy() == singleStrategy || !singleStrategy.hasOffloads() ?
                                                // single client is default or has no *additional* offloads
                                                requestStrategy :
                                                // add single client offloads to existing strategy
                                                requestStrategy.merge(singleStrategy);

                        if (useStrategy != requestStrategy) {
                            LOGGER.debug("Request strategy {} changes to {}. SingleAddressClient strategy: {}",
                                    requestStrategy, useStrategy, singleStrategy);
                            request.context().put(HTTP_EXECUTION_STRATEGY_KEY, useStrategy);
                        }
                        return delegate.request(request);
                    });
                }
            };
        }

        @Override
        public HttpExecutionStrategy requiredOffloads() {
            return offloadNone();
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

        private FilterableStreamingHttpClient selectClient(HttpRequestMetaData metaData) throws MalformedURLException {
            return group.get(keyFactory.get(metaData));
        }

        @Override
        public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                final HttpRequestMetaData metaData) {
            return defer(() -> {
                try {
                    return selectClient(metaData).reserveConnection(metaData).shareContextOnSubscribe();
                } catch (Throwable t) {
                    return Single.<FilterableReservedStreamingHttpConnection>failed(t).shareContextOnSubscribe();
                }
            });
        }

        @Override
        public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
            return defer(() -> {
                try {
                    return selectClient(request).request(request).shareContextOnSubscribe();
                } catch (Throwable t) {
                    return Single.<StreamingHttpResponse>failed(t).shareContextOnSubscribe();
                }
            });
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
        executionContextBuilder.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> executor(final Executor executor) {
        executionContextBuilder.executor(executor);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> bufferAllocator(
            final BufferAllocator allocator) {
        executionContextBuilder.bufferAllocator(allocator);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> executionStrategy(
            final HttpExecutionStrategy strategy) {
        executionContextBuilder.executionStrategy(strategy);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> headersFactory(
            final HttpHeadersFactory headersFactory) {
        this.headersFactory = requireNonNull(headersFactory);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> initializer(
            final SingleAddressInitializer<HostAndPort, InetSocketAddress> initializer) {
        this.singleAddressInitializer = requireNonNull(initializer);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> followRedirects(
            final RedirectConfig config) {
        this.redirectConfig = requireNonNull(config);
        return this;
    }
}
