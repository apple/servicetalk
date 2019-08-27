/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.api.GrpcClientCallFactory;
import io.servicetalk.grpc.api.GrpcClientSecurityConfigurator;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientSecurityConfigurator;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.transport.api.IoExecutor;

import java.net.SocketOption;
import java.util.function.Predicate;
import javax.annotation.Nullable;

final class DefaultGrpcClientBuilder<U, R> extends GrpcClientBuilder<U, R> {

    private final SingleAddressHttpClientBuilder<U, R> httpClientBuilder;

    DefaultGrpcClientBuilder(final SingleAddressHttpClientBuilder<U, R> httpClientBuilder) {
        this.httpClientBuilder = httpClientBuilder;
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
    public GrpcClientBuilder<U, R> executionStrategy(final HttpExecutionStrategy strategy) {
        httpClientBuilder.executionStrategy(strategy);
        return this;
    }

    @Override
    public <T> GrpcClientBuilder<U, R> socketOption(final SocketOption<T> option, final T value) {
        httpClientBuilder.socketOption(option, value);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> enableWireLogging(final String loggerName) {
        httpClientBuilder.enableWireLogging(loggerName);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> disableWireLogging() {
        httpClientBuilder.disableWireLogging();
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> headersFactory(final HttpHeadersFactory headersFactory) {
        httpClientBuilder.headersFactory(headersFactory);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> h2HeadersFactory(final HttpHeadersFactory headersFactory) {
        httpClientBuilder.h2HeadersFactory(headersFactory);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> h2PriorKnowledge(final boolean h2PriorKnowledge) {
        httpClientBuilder.h2PriorKnowledge(h2PriorKnowledge);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> h2FrameLogger(@Nullable final String h2FrameLogger) {
        httpClientBuilder.h2FrameLogger(h2FrameLogger);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> maxInitialLineLength(final int maxInitialLineLength) {
        httpClientBuilder.maxInitialLineLength(maxInitialLineLength);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> maxHeaderSize(final int maxHeaderSize) {
        httpClientBuilder.maxHeaderSize(maxHeaderSize);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> headersEncodedSizeEstimate(final int headersEncodedSizeEstimate) {
        httpClientBuilder.headersEncodedSizeEstimate(headersEncodedSizeEstimate);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> trailersEncodedSizeEstimate(final int trailersEncodedSizeEstimate) {
        httpClientBuilder.trailersEncodedSizeEstimate(trailersEncodedSizeEstimate);
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
    public GrpcClientSecurityConfigurator<U, R> secure() {
        SingleAddressHttpClientSecurityConfigurator<U, R> httpConfigurator = httpClientBuilder.secure();
        return new DefaultGrpcClientSecurityConfigurator<>(httpConfigurator, this);
    }

    @Override
    public GrpcClientBuilder<U, R> serviceDiscoverer(
            final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer) {
        httpClientBuilder.serviceDiscoverer(serviceDiscoverer);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> loadBalancerFactory(
            final LoadBalancerFactory<R, StreamingHttpConnection> loadBalancerFactory) {
        httpClientBuilder.loadBalancerFactory(loadBalancerFactory);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> appendHttpClientFilter(final StreamingHttpClientFilterFactory factory) {
        httpClientBuilder.appendClientFilter(factory);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> appendHttpClientFilter(final Predicate<StreamingHttpRequest> predicate,
                                                          final StreamingHttpClientFilterFactory factory) {
        httpClientBuilder.appendClientFilter(predicate, factory);
        return this;
    }

    @Override
    protected GrpcClientCallFactory newGrpcClientCallFactory() {
        return GrpcClientCallFactory.from(httpClientBuilder.buildStreaming());
    }
}
