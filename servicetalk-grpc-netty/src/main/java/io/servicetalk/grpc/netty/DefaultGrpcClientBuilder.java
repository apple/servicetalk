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
import io.servicetalk.client.api.AutoRetryStrategyProvider;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.api.GrpcClientCallFactory;
import io.servicetalk.grpc.api.GrpcClientSecurityConfigurator;
import io.servicetalk.grpc.api.GrpcExecutionStrategy;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientSecurityConfigurator;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.TransportObserver;

import java.net.SocketOption;
import java.util.function.Predicate;

import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;

final class DefaultGrpcClientBuilder<U, R> extends GrpcClientBuilder<U, R> {

    private final SingleAddressHttpClientBuilder<U, R> httpClientBuilder;

    DefaultGrpcClientBuilder(final SingleAddressHttpClientBuilder<U, R> httpClientBuilder) {
        this.httpClientBuilder = httpClientBuilder.protocols(h2Default());
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
    public GrpcClientBuilder<U, R> enableWireLogging(final String loggerName) {
        httpClientBuilder.enableWireLogging(loggerName);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> transportObserver(final TransportObserver transportObserver) {
        httpClientBuilder.transportObserver(transportObserver);
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
    public GrpcClientSecurityConfigurator<U, R> secure() {
        SingleAddressHttpClientSecurityConfigurator<U, R> httpConfigurator = httpClientBuilder.secure();
        return new DefaultGrpcClientSecurityConfigurator<>(httpConfigurator, this);
    }

    @Override
    public GrpcClientBuilder<U, R> autoRetryStrategy(
            final AutoRetryStrategyProvider autoRetryStrategyProvider) {
        httpClientBuilder.autoRetryStrategy(autoRetryStrategyProvider);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> serviceDiscoverer(
            final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer) {
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
        return GrpcClientCallFactory.from(httpClientBuilder.buildStreaming());
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
