/*
 * Copyright © 2018-2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpExceptionMapperServiceFilter;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServerSecurityConfigurator;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;
import io.servicetalk.transport.api.TransportObserver;

import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.Map;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;

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

    /**
     * Sets the maximum queue length for incoming connection indications (a request to connect) is set to the backlog
     * parameter. If a connection indication arrives when the queue is full, the connection may time out.
     *
     * @deprecated Use {@link #listenSocketOption(SocketOption, Object)} with key
     * {@link ServiceTalkSocketOptions#SO_BACKLOG}.
     * @param backlog the backlog to use when accepting connections.
     * @return {@code this}.
     */
    @Deprecated
    public HttpServerBuilder backlog(int backlog) {
        listenSocketOption(ServiceTalkSocketOptions.SO_BACKLOG, backlog);
        return this;
    }

    @Deprecated
    @Override
    public HttpServerSecurityConfigurator secure() {
        return new DefaultHttpServerSecurityConfigurator(config -> {
            this.config.tcpConfig().sslConfig(config);
            return DefaultHttpServerBuilder.this;
        });
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
    public HttpServerBuilder enableWireLogging(final String loggerName) {
        config.tcpConfig().enableWireLogging(loggerName);
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
    public HttpServerBuilder ioExecutor(final IoExecutor ioExecutor) {
        executionContextBuilder.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public HttpServerBuilder executor(final Executor executor) {
        executionContextBuilder.executor(executor);
        return this;
    }

    @Override
    public HttpServerBuilder bufferAllocator(final BufferAllocator allocator) {
        executionContextBuilder.bufferAllocator(allocator);
        return this;
    }

    @Override
    protected Single<ServerContext> doListen(@Nullable final ConnectionAcceptor connectionAcceptor,
                                             StreamingHttpService service,
                                             final HttpExecutionStrategy strategy,
                                             final boolean drainRequestPayloadBody) {
        final ReadOnlyHttpServerConfig roConfig = this.config.asReadOnly();
        service = applyInternalFilters(service, roConfig.lifecycleObserver());
        executionContextBuilder.executionStrategy(strategy);
        final HttpExecutionContext httpExecutionContext = executionContextBuilder.build();
        if (roConfig.tcpConfig().isAlpnConfigured()) {
            return DeferredServerChannelBinder.bind(httpExecutionContext, roConfig, address, connectionAcceptor,
                    service, drainRequestPayloadBody, false);
        } else if (roConfig.tcpConfig().sniMapping() != null) {
            return DeferredServerChannelBinder.bind(httpExecutionContext, roConfig, address, connectionAcceptor,
                    service, drainRequestPayloadBody, true);
        } else if (roConfig.isH2PriorKnowledge()) {
            return H2ServerParentConnectionContext.bind(httpExecutionContext, roConfig, address, connectionAcceptor,
                    service, drainRequestPayloadBody);
        }
        return NettyHttpServer.bind(httpExecutionContext, roConfig, address, connectionAcceptor, service,
                drainRequestPayloadBody);
    }

    private static StreamingHttpService applyInternalFilters(StreamingHttpService service,
                                                             @Nullable final HttpLifecycleObserver lifecycleObserver) {
        service = HttpExceptionMapperServiceFilter.INSTANCE.create(service);
        service = KeepAliveServiceFilter.INSTANCE.create(service);
        if (lifecycleObserver != null) {
            service = new HttpLifecycleObserverServiceFilter(lifecycleObserver).create(service);
        }
        // TODO: apply ClearAsyncContextHttpServiceFilter here when it's moved to http-netty module by
        //  https://github.com/apple/servicetalk/pull/1820
        return service;
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
                    // ExceptionMapperServiceFilter
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
