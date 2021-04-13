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
import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.api.GrpcClientCallFactory;
import io.servicetalk.grpc.api.GrpcClientSecurityConfigurator;
import io.servicetalk.grpc.api.GrpcExecutionStrategy;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientSecurityConfigurator;
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
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.grpc.api.GrpcMetadata.GRPC_MAX_TIMEOUT;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;

final class DefaultGrpcClientBuilder<U, R> extends GrpcClientBuilder<U, R> {
    /**
     * gRPC protocol HTTP header used for timeout per
     * <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP2</a>
     */
    private static final CharSequence GRPC_TIMEOUT_HEADER_KEY = newAsciiString("grpc-timeout");

    /**
     * Allowed time units
     */
    private static final char[] TIMEOUT_UNIT_CHARS = "numSMH".toCharArray();

    /**
     * Chronounits of timeout units
     */
    private static final ChronoUnit[] TIMEOUT_CHRONOUNITS = {
            ChronoUnit.NANOS,
            ChronoUnit.MICROS,
            ChronoUnit.MILLIS,
            ChronoUnit.SECONDS,
            ChronoUnit.MINUTES,
            ChronoUnit.HOURS,
    };

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

        if (Duration.ZERO.compareTo(Objects.requireNonNull(defaultTimeout, "defaultTimeout")) >= 0) {
            throw new IllegalArgumentException("defaultTimeout: " + defaultTimeout + " (expected > 0)");
        }

        this.defaultTimeout = defaultTimeout;
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

    @Deprecated
    @Override
    public GrpcClientBuilder<U, R> enableWireLogging(final String loggerName) {
        httpClientBuilder.enableWireLogging(loggerName);
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

    @Deprecated
    @Override
    public GrpcClientSecurityConfigurator<U, R> secure() {
        SingleAddressHttpClientSecurityConfigurator<U, R> httpConfigurator = httpClientBuilder.secure();
        return new DefaultGrpcClientSecurityConfigurator<>(httpConfigurator, this);
    }

    @Override
    public GrpcClientBuilder<U, R> sslConfig(final ClientSslConfig sslConfig) {
        httpClientBuilder.sslConfig(sslConfig);
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
    public GrpcClientBuilder<U, R> disableHostHeaderFallback() {
        httpClientBuilder.disableHostHeaderFallback();
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
        Duration timeout = null == defaultTimeout || GRPC_MAX_TIMEOUT.compareTo(defaultTimeout) < 0 ?
                null : defaultTimeout;
        if (!invokedBuild && null != timeout) {
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

    /**
     * Extract the timeout duration from the HTTP headers if present.
     *
     * @param request The HTTP request to be used as source of the GRPC timeout header
     * @return The non-negative timeout duration which may null if not present
     * @throws IllegalArgumentException if the timeout value is malformed
     */
    static @Nullable Duration readTimeoutHeader(HttpRequestMetaData request) {
        CharSequence grpcTimeoutValue = request.headers().get(GRPC_TIMEOUT_HEADER_KEY);
        return null == grpcTimeoutValue ? null : parseTimeoutHeader(grpcTimeoutValue);
    }

    /**
     * Parse a gRPC HTTP timeout header grpcTimeoutValue as a duration.
     *
     * @param grpcTimeoutValue the text value of {@link #GRPC_TIMEOUT_HEADER_KEY} header to be parsed to a timeout
     * duration
     * @return The non-negative timeout duration
     * @throws IllegalArgumentException if the timeout value is malformed
     */
    static Duration parseTimeoutHeader(CharSequence grpcTimeoutValue) {
        if (grpcTimeoutValue.length() < 2 || grpcTimeoutValue.length() > 9) {
            throw new IllegalArgumentException("grpcTimeoutValue: " + grpcTimeoutValue +
                    " (expected 2-9 characters)");
        }

        char unitChar = grpcTimeoutValue.charAt(grpcTimeoutValue.length() - 1);
        int unitsIdx = 0;
        while (unitsIdx < TIMEOUT_UNIT_CHARS.length && unitChar != TIMEOUT_UNIT_CHARS[unitsIdx]) {
            unitsIdx++;
        }
        if (TIMEOUT_UNIT_CHARS.length == unitsIdx) {
            // Unrecognized units or malformed header
            throw new IllegalArgumentException("grpcTimeoutValue: " + grpcTimeoutValue +
                    " (Bad unit '" + unitChar + "')");
        }

        // parse number
        long runningTotal = 0;
        for (int digitIdx = 0; digitIdx < grpcTimeoutValue.length() - 1; digitIdx++) {
            char digitChar = grpcTimeoutValue.charAt(digitIdx);
            if (digitChar < '0' || digitChar > '9') {
                // Bad digit
                throw new IllegalArgumentException("grpcTimeoutValue: " + grpcTimeoutValue +
                        " (Bad time unit '" + digitChar + "')");
            } else {
                runningTotal = runningTotal * 10L + (long) (digitChar - '0');
            }
        }

        return Duration.of(runningTotal, TIMEOUT_CHRONOUNITS[unitsIdx]);
    }
}
