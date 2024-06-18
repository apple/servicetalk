/*
 * Copyright © 2018-2024 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.EmptyHttpHeaders;
import io.servicetalk.http.api.FilterableReservedStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpContextKeys;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategies;
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
import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.util.Locale;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.netty.handler.codec.http.HttpScheme.HTTP;
import static io.netty.handler.codec.http.HttpScheme.HTTPS;
import static io.servicetalk.buffer.api.CharSequences.caseInsensitiveHashCode;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.http.api.HttpContextKeys.HTTP_EXECUTION_STRATEGY_KEY;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadAll;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMetaDataFactory.newRequestMetaData;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
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

    // Use HttpRequestMetaData to access "https" constant used by Uri3986 class to optimize "equals" check to be a
    // trivial reference check.
    @SuppressWarnings("DataFlowIssue")
    private static final String HTTPS_SCHEME = newRequestMetaData(HTTP_1_1, GET, "https://invalid./",
            EmptyHttpHeaders.INSTANCE).scheme();

    private final Function<HostAndPort, SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress>> builderFactory;
    private final HttpExecutionContextBuilder executionContextBuilder = new HttpExecutionContextBuilder();

    @Nullable
    private HttpHeadersFactory headersFactory;
    @Nullable
    private RedirectConfig redirectConfig;
    private int defaultHttpPort = HTTP.port();
    private int defaultHttpsPort = HTTPS.port();
    @Nullable
    private SingleAddressInitializer<HostAndPort, InetSocketAddress> singleAddressInitializer;

    DefaultMultiAddressUrlHttpClientBuilder(
            final Function<HostAndPort, SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress>> bFactory) {
        this.builderFactory = requireNonNull(bFactory);
    }

    @Override
    public StreamingHttpClient buildStreaming() {
        final HttpExecutionContext executionContext = executionContextBuilder.build();
        return new FilterableClientToClient(buildStreamingUrlHttpClient(), executionContext);
    }

    StreamingUrlHttpClient buildStreamingUrlHttpClient() {
        final CompositeCloseable closeables = newCompositeCloseable();
        try {
            final HttpExecutionContext executionContext = executionContextBuilder.build();
            final ClientFactory clientFactory =
                    new ClientFactory(builderFactory, executionContext, singleAddressInitializer);
            final UrlKeyFactory keyFactory = new UrlKeyFactory(defaultHttpPort, defaultHttpsPort);
            final HttpHeadersFactory headersFactory = this.headersFactory;
            return closeables.prepend(
                    new StreamingUrlHttpClient(executionContext, keyFactory, clientFactory,
                            new DefaultStreamingHttpRequestResponseFactory(executionContext.bufferAllocator(),
                                    headersFactory != null ? headersFactory : DefaultHttpHeadersFactory.INSTANCE,
                                    HTTP_1_1)));
        } catch (final Throwable t) {
            closeables.closeAsync().subscribe();
            throw t;
        }
    }

    /**
     * Creates a {@link UrlKey} based on {@link HttpRequestMetaData} information and rewrites absolute-form URL into a
     * relative-form URL with a "host" header.
     */
    private static final class UrlKeyFactory {

        private final int defaultHttpPort;
        private final int defaultHttpsPort;

        UrlKeyFactory(final int defaultHttpPort, final int defaultHttpsPort) {
            this.defaultHttpPort = defaultHttpPort;
            this.defaultHttpsPort = defaultHttpsPort;
        }

        UrlKey get(final HttpRequestMetaData metaData) throws MalformedURLException {
            final String scheme = ensureUrlComponentNonNull(metaData.scheme(), "scheme");
            assert scheme.equals(scheme.toLowerCase(Locale.ENGLISH)) : "scheme must be in lowercase";
            final String host = ensureUrlComponentNonNull(metaData.host(), "host");
            final int parsedPort = metaData.port();
            final int port = parsedPort >= 0 ? parsedPort :
                    (HTTPS_SCHEME.equals(scheme) ? defaultHttpsPort : defaultHttpPort);
            // setHostHeader(metaData, host, parsedPort);
            // metaData.requestTarget(absoluteToRelativeFormRequestTarget(metaData.requestTarget(), scheme, host));
            return new UrlKey(scheme, host, port);
        }

        private static String ensureUrlComponentNonNull(@Nullable final String value,
                                                        final String name) throws MalformedURLException {
            if (value == null) {
                throw new MalformedURLException("Request-target does not contain " + name +
                        ", expected absolute-form URL (scheme://host/path)");
            }
            return value;
        }

        private static void setHostHeader(final HttpRequestMetaData metaData, final String host, final int port) {
            if (metaData.version().compareTo(HTTP_1_1) >= 0 && !metaData.headers().contains(HOST)) {
                // Host header must be identical to authority component of the target URI,
                // as described in https://datatracker.ietf.org/doc/html/rfc7230#section-5.4
                String authority = host;
                if (port >= 0) {
                    authority = authority + ':' + port;
                }
                metaData.headers().add(HOST, authority);
            }
        }

        // This code is similar to io.servicetalk.http.utils.RedirectSingle#absoluteToRelativeFormRequestTarget
        // but cannot be shared because we don't have an internal module for http
        private static String absoluteToRelativeFormRequestTarget(final String requestTarget,
                                                                  final String scheme, final String host) {
            final int fromIndex = scheme.length() + 3 + host.length();  // +3 because of "://" delimiter after scheme
            final int relativeReferenceIdx = requestTarget.indexOf('/', fromIndex);
            if (relativeReferenceIdx >= 0) {
                return requestTarget.substring(relativeReferenceIdx);
            }
            final int questionMarkIdx = requestTarget.indexOf('?', fromIndex);
            return questionMarkIdx < 0 ? "/" : '/' + requestTarget.substring(questionMarkIdx);
        }
    }

    private static final class UrlKey {
        final String scheme;
        final String host;
        final int port;
        private final int hashCode;

        UrlKey(final String scheme, final String host, final int port) {
            this.scheme = scheme;
            this.host = host;
            this.port = port;
            // hashCode is required at least once, but may be necessary multiple times for a single selectClient run
            this.hashCode = 31 * (caseInsensitiveHashCode(host) + port) + scheme.hashCode();
        }

        /**
         * This class is used only within controlled scope, a non-null argument and class match are guaranteed.
         */
        @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            final UrlKey urlKey = (UrlKey) o;
            return port == urlKey.port && scheme.equals(urlKey.scheme) && host.equalsIgnoreCase(urlKey.host);
        }

        @Override
        public int hashCode() {
            return hashCode;
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
            final HostAndPort hostAndPort = HostAndPort.of(urlKey.host, urlKey.port);
            final SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                    requireNonNull(builderFactory.apply(hostAndPort));

            setExecutionContext(builder, executionContext);
            if (HTTPS_SCHEME.equals(urlKey.scheme)) {
                builder.sslConfig(DEFAULT_CLIENT_SSL_CONFIG);
            }

            builder.appendClientFilter(HttpExecutionStrategyUpdater.INSTANCE);

            if (singleAddressInitializer != null) {
                singleAddressInitializer.initialize(urlKey.scheme, hostAndPort, builder);
            }

            return builder.buildStreaming();
        }
    }

    private static void singleClientStrategyUpdate(ContextMap context, HttpExecutionStrategy singleStrategy) {
        HttpExecutionStrategy requestStrategy = context.getOrDefault(HTTP_EXECUTION_STRATEGY_KEY, defaultStrategy());
        assert requestStrategy != null : "Request strategy unexpectedly null";
        HttpExecutionStrategy useStrategy = defaultStrategy() == requestStrategy ?
                // For all apis except async streaming default conversion has already been done.
                // This is the default to required strategy resolution for the async streaming client.
                offloadAll() :
                defaultStrategy() == singleStrategy || !singleStrategy.hasOffloads() ?
                        // single client is default or has no *additional* offloads
                        requestStrategy :
                        // add single client offloads to existing strategy
                        requestStrategy.merge(singleStrategy);

        if (useStrategy != requestStrategy) {
            LOGGER.debug("Request strategy {} changes to {}. SingleAddressClient strategy: {}",
                    requestStrategy, useStrategy, singleStrategy);
            context.put(HTTP_EXECUTION_STRATEGY_KEY, useStrategy);
        }
    }

    /**
     * When request transitions from the multi-address level to the single-address level, this filter will make sure
     * that any missing offloading required by the selected single-address client will be applied for the request
     * execution. This filter never reduces offloading, it can only add missing offloading flags. Users who want to
     * execute a request without offloading must specify {@link HttpExecutionStrategies#offloadNone()} strategy at the
     * {@link MultiAddressHttpClientBuilder} or explicitly set the required strategy at request context with
     * {@link HttpContextKeys#HTTP_EXECUTION_STRATEGY_KEY}.
     */
    private static final class HttpExecutionStrategyUpdater implements StreamingHttpClientFilterFactory {

        static final StreamingHttpClientFilterFactory INSTANCE = new HttpExecutionStrategyUpdater();

        private HttpExecutionStrategyUpdater() {
            // Singleton
        }

        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {
                @Override
                protected Single<StreamingHttpResponse> request(
                        final StreamingHttpRequester delegate, final StreamingHttpRequest request) {
                    return defer(() -> {
                        singleClientStrategyUpdate(request.context(), client.executionContext().executionStrategy());
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

    static final class StreamingUrlHttpClient implements FilterableStreamingHttpClient {
        private final HttpExecutionContext executionContext;
        private final StreamingHttpRequestResponseFactory reqRespFactory;
        private final ClientGroup<UrlKey, FilterableStreamingHttpClient> group;
        private final UrlKeyFactory keyFactory;

        StreamingUrlHttpClient(final HttpExecutionContext executionContext,
                               final UrlKeyFactory keyFactory, final ClientFactory clientFactory,
                               final StreamingHttpRequestResponseFactory reqRespFactory) {
            this.reqRespFactory = requireNonNull(reqRespFactory);
            this.group = ClientGroup.from(clientFactory);
            this.keyFactory = keyFactory;
            this.executionContext = requireNonNull(executionContext);
        }

        FilterableStreamingHttpClient selectClient(HttpRequestMetaData metaData) throws MalformedURLException {
            return group.get(keyFactory.get(metaData));
        }

        @Override
        public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                final HttpRequestMetaData metaData) {
            return defer(() -> {
                try {
                    FilterableStreamingHttpClient singleClient = selectClient(metaData);
                    singleClientStrategyUpdate(metaData.context(), singleClient.executionContext().executionStrategy());

                    return singleClient.reserveConnection(metaData).shareContextOnSubscribe();
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
            return group.onClose();
        }

        @Override
        public Completable onClosing() {
            return group.onClosing();
        }

        @Override
        public Completable closeAsync() {
            return group.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return group.closeAsyncGracefully();
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
            @Nullable final RedirectConfig config) {
        this.redirectConfig = config;
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> defaultHttpPort(final int port) {
        this.defaultHttpPort = verifyPortRange(port);
        return this;
    }

    @Override
    public MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> defaultHttpsPort(final int port) {
        this.defaultHttpsPort = verifyPortRange(port);
        return this;
    }

    /**
     * Verifies that the given port number is within the allowed range.
     *
     * @param port the port number to verify.
     * @return the port number if in range.
     */
    private static int verifyPortRange(final int port) {
        if (port < 1 || port > 0xFFFF) {
            throw new IllegalArgumentException("Provided port number is out of range (between 1 and 65535): " + port);
        }
        return port;
    }
}
