/*
 * Copyright © 2019-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcExecutionStrategy;
import io.servicetalk.grpc.api.GrpcServerBuilder;
import io.servicetalk.grpc.api.GrpcServiceFactory;
import io.servicetalk.grpc.api.GrpcServiceFactory.ServerBinder;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.http.utils.TimeoutFromRequest;
import io.servicetalk.http.utils.TimeoutHttpServiceFilter;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ConnectionAcceptorFactory;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ExecutionContextBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketOption;
import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.grpc.api.GrpcExecutionStrategies.defaultStrategy;
import static io.servicetalk.grpc.internal.DeadlineUtils.GRPC_DEADLINE_KEY;
import static io.servicetalk.grpc.internal.DeadlineUtils.readTimeoutHeader;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.utils.internal.DurationUtils.ensurePositive;

final class DefaultGrpcServerBuilder extends GrpcServerBuilder implements ServerBinder {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultGrpcServerBuilder.class);

    private final HttpServerBuilder httpServerBuilder;
    private final ExecutionContextBuilder contextBuilder = new ExecutionContextBuilder()
            // Make sure we always set a strategy so that ExecutionContextBuilder does not create a strategy which is
            // not compatible with gRPC.
            .executionStrategy(defaultStrategy());

    /**
     * A duration greater than zero or null for no timeout.
     */
    @Nullable
    private Duration defaultTimeout;
    private boolean invokedBuild;

    DefaultGrpcServerBuilder(final HttpServerBuilder httpServerBuilder) {
        this.httpServerBuilder = httpServerBuilder.protocols(h2Default()).allowDropRequestTrailers(true);
    }

    @Override
    public GrpcServerBuilder defaultTimeout(Duration defaultTimeout) {
        if (invokedBuild) {
            throw new IllegalStateException("default timeout cannot be modified after build, create a new builder");
        }
        this.defaultTimeout = ensurePositive(defaultTimeout, "defaultTimeout");
        return this;
    }

    @Override
    public GrpcServerBuilder protocols(final HttpProtocolConfig... protocols) {
        httpServerBuilder.protocols(protocols);
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
    public GrpcServerBuilder drainRequestPayloadBody(boolean enable) {
        httpServerBuilder.drainRequestPayloadBody(enable);
        return this;
    }

    @Override
    public GrpcServerBuilder appendConnectionAcceptorFilter(final ConnectionAcceptorFactory factory) {
        httpServerBuilder.appendConnectionAcceptorFilter(factory);
        return this;
    }

    @Override
    public GrpcServerBuilder executor(final Executor executor) {
        contextBuilder.executor(executor);
        httpServerBuilder.executor(executor);
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

    private HttpServerBuilder preBuild() {
        if (!invokedBuild) {
            doAppendHttpServiceFilter(new TimeoutHttpServiceFilter(grpcDetermineTimeout(defaultTimeout), true));
        }
        invokedBuild = true;
        return httpServerBuilder;
    }

    private static TimeoutFromRequest grpcDetermineTimeout(@Nullable Duration defaultTimeout) {
        return new TimeoutFromRequest() {
            /**
             * Return the timeout duration extracted from the GRPC timeout HTTP header if present or default timeout.
             *
             * @param request The HTTP request to be used as source of the GRPC timeout header
             * @return The non-negative timeout duration which may be null
             */
            @Override
            public @Nullable Duration apply(HttpRequestMetaData request) {
                @Nullable
                Duration requestTimeout = readTimeoutHeader(request);
                @Nullable
                Duration timeout = null != requestTimeout ? requestTimeout : defaultTimeout;

                if (null != timeout) {
                    // Store the timeout in the context as a deadline to be used for any client requests created
                    // during the context of handling this request.
                    try {
                        Long deadline = System.nanoTime() + timeout.toNanos();
                        AsyncContext.put(GRPC_DEADLINE_KEY, deadline);
                    } catch (UnsupportedOperationException ignored) {
                        LOGGER.debug("Async context disabled, timeouts will not be propagated to client requests");
                        // ignored -- async context has probably been disabled.
                        // Timeout propagation will be partially disabled.
                        // cancel()s will still happen which will accomplish the same effect though less efficiently
                    }
                }

                return timeout;
            }

            @Override
            public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
                // We don't block so have no influence on strategy.
                return strategy;
            }
        };
    }

    @Override
    public Single<ServerContext> bind(final HttpService service) {
        return preBuild().listen(service);
    }

    @Override
    public Single<ServerContext> bindStreaming(final StreamingHttpService service) {
        return preBuild().listenStreaming(service);
    }

    @Override
    public Single<ServerContext> bindBlocking(final BlockingHttpService service) {
        return preBuild().listenBlocking(service);
    }

    @Override
    public Single<ServerContext> bindBlockingStreaming(final BlockingStreamingHttpService service) {
        return preBuild().listenBlockingStreaming(service);
    }
}
