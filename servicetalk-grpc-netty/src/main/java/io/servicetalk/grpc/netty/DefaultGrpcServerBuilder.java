/*
 * Copyright © 2019-2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcExecutionStrategy;
import io.servicetalk.grpc.api.GrpcServerBuilder;
import io.servicetalk.grpc.api.GrpcServiceFactory;
import io.servicetalk.grpc.api.GrpcServiceFactory.ServerBinder;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ConnectionAcceptorFactory;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ExecutionContextBuilder;

import java.net.SocketOption;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static io.servicetalk.grpc.api.GrpcExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;

final class DefaultGrpcServerBuilder extends GrpcServerBuilder implements ServerBinder {

    private final HttpServerBuilder httpServerBuilder;
    private final ExecutionContextBuilder contextBuilder = new ExecutionContextBuilder()
            // Make sure we always set a strategy so that ExecutionContextBuilder does not create a strategy which is
            // not compatible with gRPC.
            .executionStrategy(defaultStrategy());

    DefaultGrpcServerBuilder(final HttpServerBuilder httpServerBuilder) {
        this.httpServerBuilder = httpServerBuilder.protocols(h2Default()).allowDropRequestTrailers(true);
    }

    @Override
    public GrpcServerBuilder protocols(final HttpProtocolConfig... protocols) {
        httpServerBuilder.protocols(protocols);
        return this;
    }

    @Deprecated
    @Override
    public GrpcServerBuilder backlog(final int backlog) {
        httpServerBuilder.backlog(backlog);
        return this;
    }

    @Override
    public GrpcServerBuilder sslConfig(final ServerSslConfig config) {
        httpServerBuilder.sslConfig(config);
        return this;
    }

    @Override
    public GrpcServerBuilder sslConfig(final ServerSslConfig defaultConfig, final Map<String, ServerSslConfig> sniMap) {
        httpServerBuilder.sslConfig(defaultConfig, sniMap);
        return this;
    }

    @Override
    public <T> GrpcServerBuilder socketOption(final SocketOption<T> option, final T value) {
        httpServerBuilder.socketOption(option, value);
        return this;
    }

    @Override
    public <T> GrpcServerBuilder listenSocketOption(final SocketOption<T> option, final T value) {
        httpServerBuilder.listenSocketOption(option, value);
        return this;
    }

    @Deprecated
    @Override
    public GrpcServerBuilder enableWireLogging(final String loggerName) {
        httpServerBuilder.enableWireLogging(loggerName);
        return this;
    }

    @Override
    public GrpcServerBuilder enableWireLogging(final String loggerName, final LogLevel logLevel,
                                               final BooleanSupplier logUserData) {
        httpServerBuilder.enableWireLogging(loggerName, logLevel, logUserData);
        return this;
    }

    @Override
    public GrpcServerBuilder transportObserver(final TransportObserver transportObserver) {
        httpServerBuilder.transportObserver(transportObserver);
        return this;
    }

    @Override
    public GrpcServerBuilder disableDrainingRequestPayloadBody() {
        httpServerBuilder.disableDrainingRequestPayloadBody();
        return this;
    }

    @Override
    public GrpcServerBuilder appendConnectionAcceptorFilter(final ConnectionAcceptorFactory factory) {
        httpServerBuilder.appendConnectionAcceptorFilter(factory);
        return this;
    }

    @Override
    public GrpcServerBuilder ioExecutor(final IoExecutor ioExecutor) {
        contextBuilder.ioExecutor(ioExecutor);
        httpServerBuilder.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public GrpcServerBuilder bufferAllocator(final BufferAllocator allocator) {
        contextBuilder.bufferAllocator(allocator);
        httpServerBuilder.bufferAllocator(allocator);
        return this;
    }

    @Override
    public GrpcServerBuilder executionStrategy(final GrpcExecutionStrategy strategy) {
        contextBuilder.executionStrategy(strategy);
        httpServerBuilder.executionStrategy(strategy);
        return this;
    }

    @Override
    protected Single<ServerContext> doListen(final GrpcServiceFactory<?, ?, ?> serviceFactory) {
        return serviceFactory.bind(this, contextBuilder.build());
    }

    @Override
    protected void doAppendHttpServiceFilter(final StreamingHttpServiceFilterFactory factory) {
        httpServerBuilder.appendServiceFilter(factory);
    }

    @Override
    protected void doAppendHttpServiceFilter(final Predicate<StreamingHttpRequest> predicate,
                                             final StreamingHttpServiceFilterFactory factory) {
        httpServerBuilder.appendServiceFilter(predicate, factory);
    }

    @Override
    public Single<ServerContext> bind(final HttpService service) {
        return httpServerBuilder.listen(service);
    }

    @Override
    public Single<ServerContext> bindStreaming(final StreamingHttpService service) {
        return httpServerBuilder.listenStreaming(service);
    }

    @Override
    public Single<ServerContext> bindBlocking(final BlockingHttpService service) {
        return httpServerBuilder.listenBlocking(service);
    }

    @Override
    public Single<ServerContext> bindBlockingStreaming(final BlockingStreamingHttpService service) {
        return httpServerBuilder.listenBlockingStreaming(service);
    }
}
