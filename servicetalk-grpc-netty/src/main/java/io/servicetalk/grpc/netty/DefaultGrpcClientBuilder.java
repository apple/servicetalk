/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.netty;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.client.api.AutoRetryStrategyProvider;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.api.GrpcClientCallFactory;
import io.servicetalk.grpc.api.GrpcExecutionStrategy;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.http.api.FilterableReservedStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.ReservedStreamingHttpConnectionFilter;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.TimeoutFromRequest;
import io.servicetalk.http.utils.TimeoutHttpRequesterFilter;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.IoExecutor;

import java.net.SocketOption;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.grpc.api.GrpcStatus.fromThrowable;
import static io.servicetalk.grpc.internal.DeadlineUtils.GRPC_MAX_TIMEOUT;
import static io.servicetalk.grpc.internal.DeadlineUtils.readTimeoutHeader;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.utils.internal.DurationUtils.ensurePositive;
import static io.servicetalk.utils.internal.DurationUtils.isInfinite;
import static java.util.Objects.requireNonNull;

final class DefaultGrpcClientBuilder<U, R> extends GrpcClientBuilder<U, R> {

    /**
     * A function which determines the timeout for a given request.
     */
    private static final TimeoutFromRequest GRPC_TIMEOUT_REQHDR = new TimeoutFromRequest() {
        /**
         * Return the timeout duration extracted from the GRPC timeout HTTP header if present
         *
         * @param request The HTTP request to be used as source of the timeout filter duration.
         * @return The non-negative timeout duration which may be null
         * @throws IllegalArgumentException if the timeout value is malformed
         */
        public @Nullable Duration apply(HttpRequestMetaData request) {
            return readTimeoutHeader(request);
        }

        @Override
        public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
            // we do not block and have no influence on strategy
            return strategy;
        }
    };

    @Nullable
    private Duration defaultTimeout;
    private HttpInitializer<U, R> httpInitializer = builder -> {
        // no-op
    };

    private HttpInitializer<U, R> directHttpInitializer = builder -> {
        // no-op
    };

    private final Supplier<SingleAddressHttpClientBuilder<U, R>> httpClientBuilderSupplier;

    DefaultGrpcClientBuilder(final Supplier<SingleAddressHttpClientBuilder<U, R>> httpClientBuilderSupplier) {
        this.httpClientBuilderSupplier = httpClientBuilderSupplier;
    }

    @Override
    public GrpcClientBuilder<U, R> initializeHttp(final GrpcClientBuilder.HttpInitializer<U, R> initializer) {
        httpInitializer = requireNonNull(initializer);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> defaultTimeout(Duration defaultTimeout) {
        this.defaultTimeout = ensurePositive(defaultTimeout, "defaultTimeout");
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> executor(final Executor executor) {
        directHttpInitializer = directHttpInitializer.append(builder -> builder.executor(executor));
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> ioExecutor(final IoExecutor ioExecutor) {
        directHttpInitializer = directHttpInitializer.append(builder -> builder.ioExecutor(ioExecutor));
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> bufferAllocator(final BufferAllocator allocator) {
        directHttpInitializer = directHttpInitializer.append(builder -> builder.bufferAllocator(allocator));
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> executionStrategy(final GrpcExecutionStrategy strategy) {
        directHttpInitializer = directHttpInitializer.append(builder -> builder.executionStrategy(strategy));
        return this;
    }

    @Override
    public <T> GrpcClientBuilder<U, R> socketOption(final SocketOption<T> option, final T value) {
        directHttpInitializer = directHttpInitializer.append(builder -> builder.socketOption(option, value));
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> enableWireLogging(final String loggerName, final LogLevel logLevel,
                                                     final BooleanSupplier logUserData) {
        directHttpInitializer = directHttpInitializer.append(builder ->
                builder.enableWireLogging(loggerName, logLevel, logUserData));
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> protocols(HttpProtocolConfig... protocols) {
        directHttpInitializer = directHttpInitializer.append(builder -> builder.protocols(protocols));
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> appendConnectionFactoryFilter(
            final ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> factory) {
        directHttpInitializer = directHttpInitializer.append(builder -> builder.appendConnectionFactoryFilter(factory));
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> appendConnectionFilter(
            final StreamingHttpConnectionFilterFactory factory) {
        directHttpInitializer = directHttpInitializer.append(builder -> builder.appendConnectionFilter(factory));
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> appendConnectionFilter(
            final Predicate<StreamingHttpRequest> predicate, final StreamingHttpConnectionFilterFactory factory) {
        directHttpInitializer = directHttpInitializer.append(builder ->
                builder.appendConnectionFilter(predicate, factory));
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> sslConfig(final ClientSslConfig sslConfig) {
        directHttpInitializer = directHttpInitializer.append(builder -> builder.sslConfig(sslConfig));
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> inferPeerHost(final boolean shouldInfer) {
        directHttpInitializer = directHttpInitializer.append(builder -> builder.inferPeerHost(shouldInfer));
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> inferPeerPort(final boolean shouldInfer) {
        directHttpInitializer = directHttpInitializer.append(builder -> builder.inferPeerPort(shouldInfer));
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> inferSniHostname(final boolean shouldInfer) {
        directHttpInitializer = directHttpInitializer.append(builder -> builder.inferSniHostname(shouldInfer));
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> autoRetryStrategy(
            final AutoRetryStrategyProvider autoRetryStrategyProvider) {
        directHttpInitializer = directHttpInitializer.append(builder ->
                builder.autoRetryStrategy(autoRetryStrategyProvider));
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> unresolvedAddressToHost(
            final Function<U, CharSequence> unresolvedAddressToHostFunction) {
        directHttpInitializer = directHttpInitializer.append(builder ->
                builder.unresolvedAddressToHost(unresolvedAddressToHostFunction));
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> hostHeaderFallback(final boolean enable) {
        directHttpInitializer = directHttpInitializer.append(builder -> builder.hostHeaderFallback(enable));
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> serviceDiscoverer(
            final ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer) {
        directHttpInitializer = directHttpInitializer.append(builder -> builder.serviceDiscoverer(serviceDiscoverer));
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> loadBalancerFactory(final HttpLoadBalancerFactory<R> loadBalancerFactory) {
        directHttpInitializer = directHttpInitializer.append(builder ->
                builder.loadBalancerFactory(loadBalancerFactory));
        return this;
    }

    @Override
    protected GrpcClientCallFactory newGrpcClientCallFactory() {
        SingleAddressHttpClientBuilder<U, R> builder = httpClientBuilderSupplier.get().protocols(h2Default());
        builder.appendClientFilter(CatchAllHttpClientFilter.INSTANCE);
        directHttpInitializer.initialize(builder);
        httpInitializer.initialize(builder);
        builder.appendClientFilter(new TimeoutHttpRequesterFilter(GRPC_TIMEOUT_REQHDR, true));
        Duration timeout = isInfinite(defaultTimeout, GRPC_MAX_TIMEOUT) ? null : defaultTimeout;
        return GrpcClientCallFactory.from(builder.buildStreaming(), timeout);
    }

    @Override
    protected void doAppendHttpClientFilter(final StreamingHttpClientFilterFactory factory) {
        directHttpInitializer = directHttpInitializer.append(builder -> builder.appendClientFilter(factory));
    }

    @Override
    public void doAppendHttpClientFilter(final Predicate<StreamingHttpRequest> predicate,
                                         final StreamingHttpClientFilterFactory factory) {
        directHttpInitializer = directHttpInitializer.append(builder -> builder.appendClientFilter(predicate, factory));
    }

    static final class CatchAllHttpClientFilter implements StreamingHttpClientFilterFactory,
                                                           HttpExecutionStrategyInfluencer {

        static final StreamingHttpClientFilterFactory INSTANCE = new CatchAllHttpClientFilter();

        private CatchAllHttpClientFilter() {
            // Singleton
        }

        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {

                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final HttpExecutionStrategy strategy,
                                                                final StreamingHttpRequest request) {
                    return CatchAllHttpClientFilter.request(delegate, strategy, request);
                }

                @Override
                public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                        final HttpExecutionStrategy strategy, final HttpRequestMetaData metaData) {

                    return delegate().reserveConnection(strategy, metaData)
                            .map(r -> new ReservedStreamingHttpConnectionFilter(r) {
                                @Override
                                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                                final HttpExecutionStrategy strategy,
                                                                                final StreamingHttpRequest request) {
                                    return CatchAllHttpClientFilter.request(delegate, strategy, request);
                                }
                            });
                }
            };
        }

        private static Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                             final HttpExecutionStrategy strategy,
                                                             final StreamingHttpRequest request) {
            final Single<StreamingHttpResponse> resp;
            try {
                resp = delegate.request(strategy, request);
            } catch (Throwable t) {
                return failed(toGrpcException(t));
            }
            return resp.onErrorMap(CatchAllHttpClientFilter::toGrpcException);
        }

        private static GrpcStatusException toGrpcException(Throwable cause) {
            return fromThrowable(cause).asException();
        }

        @Override
        public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
            return strategy;    // no influence since we do not block
        }
    }
}
