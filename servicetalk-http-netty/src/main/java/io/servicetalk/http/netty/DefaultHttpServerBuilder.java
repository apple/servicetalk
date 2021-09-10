/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.serializer.api.SerializationException;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.TransportObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.SERVICE_UNAVAILABLE;
import static io.servicetalk.http.api.HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE;

final class DefaultHttpServerBuilder extends HttpServerBuilder {

    private final HttpServerConfig config = new HttpServerConfig();
    private final HttpExecutionContextBuilder executionContextBuilder = new HttpExecutionContextBuilder();
    private final SocketAddress address;

    DefaultHttpServerBuilder(SocketAddress address) {
        this.address = address;
    }

    @Override
    public HttpServerBuilder protocols(final HttpProtocolConfig... protocols) {
        config.httpConfig().protocols(protocols);
        return this;
    }

    @Override
    public HttpServerBuilder sslConfig(final ServerSslConfig config) {
        this.config.tcpConfig().sslConfig(config);
        return this;
    }

    @Override
    public HttpServerBuilder sslConfig(final ServerSslConfig defaultConfig, final Map<String, ServerSslConfig> sniMap) {
        this.config.tcpConfig().sslConfig(defaultConfig, sniMap);
        return this;
    }

    @Override
    public <T> HttpServerBuilder socketOption(final SocketOption<T> option, final T value) {
        config.tcpConfig().socketOption(option, value);
        return this;
    }

    @Override
    public <T> HttpServerBuilder listenSocketOption(final SocketOption<T> option, final T value) {
        config.tcpConfig().listenSocketOption(option, value);
        return this;
    }

    @Override
    public HttpServerBuilder enableWireLogging(final String loggerName, final LogLevel logLevel,
                                               final BooleanSupplier logUserData) {
        config.tcpConfig().enableWireLogging(loggerName, logLevel, logUserData);
        return this;
    }

    @Override
    public HttpServerBuilder transportObserver(final TransportObserver transportObserver) {
        config.tcpConfig().transportObserver(transportObserver);
        return this;
    }

    @Override
    public HttpServerBuilder lifecycleObserver(final HttpLifecycleObserver lifecycleObserver) {
        config.lifecycleObserver(lifecycleObserver);
        return this;
    }

    @Override
    public HttpServerBuilder allowDropRequestTrailers(final boolean allowDrop) {
        config.httpConfig().allowDropTrailersReadFromTransport(allowDrop);
        return this;
    }

    @Override
    public HttpServerBuilder executor(final Executor executor) {
        executionContextBuilder.executor(executor);
        return this;
    }

    @Override
    public HttpServerBuilder ioExecutor(final IoExecutor ioExecutor) {
        executionContextBuilder.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public HttpServerBuilder bufferAllocator(final BufferAllocator allocator) {
        executionContextBuilder.bufferAllocator(allocator);
        return this;
    }

    @Override
    protected Single<ServerContext> doListen(@Nullable final ConnectionAcceptor connectionAcceptor,
                                             final StreamingHttpService service,
                                             final HttpExecutionStrategy strategy,
                                             final boolean drainRequestPayloadBody) {
        executionContextBuilder.executionStrategy(strategy);
        final HttpExecutionContext httpExecutionContext = executionContextBuilder.build();

        return doListen(connectionAcceptor, httpExecutionContext, service, drainRequestPayloadBody);
    }

    @Override
    protected HttpExecutionContext buildExecutionContext(final HttpExecutionStrategy strategy) {
        executionContextBuilder.executionStrategy(strategy);
        return executionContextBuilder.build();
    }

    @Override
    protected Single<ServerContext> doListen(@Nullable final ConnectionAcceptor connectionAcceptor,
                                             final HttpExecutionContext context,
                                             StreamingHttpService service,
                                             final boolean drainRequestPayloadBody) {
        final ReadOnlyHttpServerConfig roConfig = this.config.asReadOnly();
        service = applyInternalFilters(service, roConfig.lifecycleObserver());

        if (roConfig.tcpConfig().isAlpnConfigured()) {
            return DeferredServerChannelBinder.bind(context, roConfig, address, connectionAcceptor,
                    service, drainRequestPayloadBody, false);
        } else if (roConfig.tcpConfig().sniMapping() != null) {
            return DeferredServerChannelBinder.bind(context, roConfig, address, connectionAcceptor,
                    service, drainRequestPayloadBody, true);
        } else if (roConfig.isH2PriorKnowledge()) {
            return H2ServerParentConnectionContext.bind(context, roConfig, address, connectionAcceptor,
                    service, drainRequestPayloadBody);
        }
        return NettyHttpServer.bind(context, roConfig, address, connectionAcceptor, service, drainRequestPayloadBody);
    }

    private static StreamingHttpService applyInternalFilters(StreamingHttpService service,
                                                             @Nullable final HttpLifecycleObserver lifecycleObserver) {
        service = ExceptionMapperServiceFilter.INSTANCE.create(service);
        service = KeepAliveServiceFilter.INSTANCE.create(service);
        if (lifecycleObserver != null) {
            service = new HttpLifecycleObserverServiceFilter(lifecycleObserver).create(service);
        }
        return service;
    }

    /**
     * Internal filter that makes sure we handle all exceptions from user-defined service and filters.
     */
    private static final class ExceptionMapperServiceFilter
            implements StreamingHttpServiceFilterFactory, HttpExecutionStrategyInfluencer {

        static final StreamingHttpServiceFilterFactory INSTANCE = new ExceptionMapperServiceFilter();

        private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionMapperServiceFilter.class);

        private ExceptionMapperServiceFilter() {
            // Singleton
        }

        @Override
        public StreamingHttpServiceFilter create(final StreamingHttpService service) {
            return new StreamingHttpServiceFilter(service) {
                @Override
                public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                            final StreamingHttpRequest request,
                                                            final StreamingHttpResponseFactory responseFactory) {
                    Single<StreamingHttpResponse> respSingle;
                    try {
                        respSingle = delegate().handle(ctx, request, responseFactory);
                    } catch (Throwable cause) {
                        respSingle = failed(cause);
                    }
                    return respSingle.onErrorReturn(cause -> newErrorResponse(cause, ctx, request, responseFactory));
                }
            };
        }

        private static StreamingHttpResponse newErrorResponse(final Throwable cause,
                                                              final HttpServiceContext ctx,
                                                              final StreamingHttpRequest request,
                                                              final StreamingHttpResponseFactory responseFactory) {
            final HttpResponseStatus status;
            if (cause instanceof RejectedExecutionException) {
                status = SERVICE_UNAVAILABLE;
                LOGGER.error("Task rejected by service processing for connection={}, request='{} {} {}'. Returning: {}",
                        ctx, request.method(), request.requestTarget(), request.version(), status);
            } else if (cause instanceof SerializationException) {
                // It is assumed that a failure occurred when attempting to deserialize the request.
                status = UNSUPPORTED_MEDIA_TYPE;
                LOGGER.error("Failed to deserialize or serialize for connection={}, request='{} {} {}'. Returning: {}",
                        ctx, request.method(), request.requestTarget(), request.version(), status);
            } else {
                status = INTERNAL_SERVER_ERROR;
                LOGGER.error("Unexpected exception during service processing for connection={}, request='{} {} {}'. " +
                            "Returning: {}", ctx, request.method(), request.requestTarget(), request.version(), status);
            }
            return responseFactory.newResponse(status).setHeader(CONTENT_LENGTH, ZERO);
        }

        @Override
        public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
            return strategy;    // no influence since we do not block
        }
    }

    /**
     * Internal filter that correctly sets {@link HttpHeaderNames#CONNECTION} header value based on the requested
     * keep-alive policy.
     */
    private static final class KeepAliveServiceFilter implements StreamingHttpServiceFilterFactory,
                                                                 HttpExecutionStrategyInfluencer {

        static final StreamingHttpServiceFilterFactory INSTANCE = new KeepAliveServiceFilter();

        private KeepAliveServiceFilter() {
            // Singleton
        }

        @Override
        public StreamingHttpServiceFilter create(final StreamingHttpService service) {
            return new StreamingHttpServiceFilter(service) {
                @Override
                public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                            final StreamingHttpRequest request,
                                                            final StreamingHttpResponseFactory responseFactory) {
                    final HttpKeepAlive keepAlive = HttpKeepAlive.responseKeepAlive(request);
                    // Don't expect any exceptions from delegate because it's already wrapped with
                    // ExceptionMapperServiceFilterFactory
                    return delegate().handle(ctx, request, responseFactory).map(response -> {
                        keepAlive.addConnectionHeaderIfNecessary(response);
                        return response;
                    });
                }
            };
        }

        @Override
        public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
            return strategy;    // no influence since we do not block
        }
    }
}
