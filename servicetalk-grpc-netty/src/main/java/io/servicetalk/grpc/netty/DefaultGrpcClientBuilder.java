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
import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.api.GrpcClientCallFactory;
import io.servicetalk.grpc.api.GrpcExecutionStrategy;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
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
import javax.annotation.Nullable;

import static io.servicetalk.grpc.internal.DeadlineUtils.GRPC_MAX_TIMEOUT;
import static io.servicetalk.grpc.internal.DeadlineUtils.readTimeoutHeader;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.utils.internal.DurationUtils.ensurePositive;
import static io.servicetalk.utils.internal.DurationUtils.isInfinite;

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
    private boolean invokedBuild;

    private final SingleAddressHttpClientBuilder<U, R> httpClientBuilder;

    DefaultGrpcClientBuilder(final SingleAddressHttpClientBuilder<U, R> httpClientBuilder) {
        this.httpClientBuilder = httpClientBuilder.protocols(h2Default());
    }

    @Override
    public GrpcClientBuilder<U, R> defaultTimeout(Duration defaultTimeout) {
        if (invokedBuild) {
            throw new IllegalStateException("default timeout cannot be modified after build, create a new builder");
        }
        this.defaultTimeout = ensurePositive(defaultTimeout, "defaultTimeout");
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> executor(final Executor executor) {
        httpClientBuilder.executor(executor);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> ioExecutor(final IoExecutor ioExecutor) {
        httpClientBuilder.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> bufferAllocator(final BufferAllocator allocator) {
        httpClientBuilder.bufferAllocator(allocator);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> executionStrategy(final GrpcExecutionStrategy strategy) {
        httpClientBuilder.executionStrategy(strategy);
        return this;
    }

    @Override
    public <T> GrpcClientBuilder<U, R> socketOption(final SocketOption<T> option, final T value) {
        httpClientBuilder.socketOption(option, value);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> enableWireLogging(final String loggerName, final LogLevel logLevel,
                                                     final BooleanSupplier logUserData) {
        httpClientBuilder.enableWireLogging(loggerName, logLevel, logUserData);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> protocols(HttpProtocolConfig... protocols) {
        httpClientBuilder.protocols(protocols);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> appendConnectionFactoryFilter(
            final ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> factory) {
        httpClientBuilder.appendConnectionFactoryFilter(factory);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> appendConnectionFilter(
            final StreamingHttpConnectionFilterFactory factory) {
        httpClientBuilder.appendConnectionFilter(factory);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> appendConnectionFilter(
            final Predicate<StreamingHttpRequest> predicate, final StreamingHttpConnectionFilterFactory factory) {
        httpClientBuilder.appendConnectionFilter(predicate, factory);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> sslConfig(final ClientSslConfig sslConfig) {
        httpClientBuilder.sslConfig(sslConfig);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> inferPeerHost(final boolean shouldInfer) {
        httpClientBuilder.inferPeerHost(shouldInfer);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> inferPeerPort(final boolean shouldInfer) {
        httpClientBuilder.inferPeerPort(shouldInfer);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> inferSniHostname(final boolean shouldInfer) {
        httpClientBuilder.inferSniHostname(shouldInfer);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> autoRetryStrategy(
            final AutoRetryStrategyProvider autoRetryStrategyProvider) {

        httpClientBuilder.autoRetryStrategy(autoRetryStrategyProvider);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> unresolvedAddressToHost(
            final Function<U, CharSequence> unresolvedAddressToHostFunction) {
        httpClientBuilder.unresolvedAddressToHost(unresolvedAddressToHostFunction);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> hostHeaderFallback(final boolean enable) {
        httpClientBuilder.hostHeaderFallback(enable);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> serviceDiscoverer(
            final ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer) {
        httpClientBuilder.serviceDiscoverer(serviceDiscoverer);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> loadBalancerFactory(final HttpLoadBalancerFactory<R> loadBalancerFactory) {
        httpClientBuilder.loadBalancerFactory(loadBalancerFactory);
        return this;
    }

    @Override
    protected GrpcClientCallFactory newGrpcClientCallFactory() {
        Duration timeout = isInfinite(defaultTimeout, GRPC_MAX_TIMEOUT) ? null : defaultTimeout;
        if (!invokedBuild) {
            httpClientBuilder.appendClientFilter(new TimeoutHttpRequesterFilter(GRPC_TIMEOUT_REQHDR, true));
        }
        invokedBuild = true;
        return GrpcClientCallFactory.from(httpClientBuilder.buildStreaming(), timeout);
    }

    @Override
    protected void doAppendHttpClientFilter(final StreamingHttpClientFilterFactory factory) {
        httpClientBuilder.appendClientFilter(factory);
    }

    @Override
    public void doAppendHttpClientFilter(final Predicate<StreamingHttpRequest> predicate,
                                         final StreamingHttpClientFilterFactory factory) {
        httpClientBuilder.appendClientFilter(predicate, factory);
    }
}
