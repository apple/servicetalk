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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServerSecurityConfigurator;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;

import java.net.SocketAddress;
import java.net.SocketOption;
import javax.annotation.Nullable;

final class DefaultHttpServerBuilder extends HttpServerBuilder {

    private final HttpServerConfig config = new HttpServerConfig();
    private final HttpExecutionContextBuilder executionContextBuilder = new HttpExecutionContextBuilder();
    private SocketAddress address;

    DefaultHttpServerBuilder(SocketAddress address) {
        this.address = address;
    }

    @Override
    public HttpServerBuilder protocols(final HttpProtocolConfig... protocols) {
        config.httpConfig().protocols(protocols);
        return this;
    }

    @Override
    public HttpServerBuilder backlog(final int backlog) {
        config.tcpConfig().backlog(backlog);
        return this;
    }

    @Override
    public HttpServerSecurityConfigurator secure() {
        return new DefaultHttpServerSecurityConfigurator(securityConfig -> {
            config.tcpConfig().secure(securityConfig);
            return DefaultHttpServerBuilder.this;
        });
    }

    @Override
    public HttpServerSecurityConfigurator secure(final String... sniHostnames) {
        return new DefaultHttpServerSecurityConfigurator(securityConfig -> {
            config.tcpConfig().secure(securityConfig, sniHostnames);
            return DefaultHttpServerBuilder.this;
        });
    }

    @Override
    public <T> HttpServerBuilder socketOption(final SocketOption<T> option, final T value) {
        config.tcpConfig().socketOption(option, value);
        return this;
    }

    @Override
    public HttpServerBuilder enableWireLogging(final String loggerName) {
        config.tcpConfig().enableWireLogging(loggerName);
        return this;
    }

    @Override
    public HttpServerBuilder enableWireLogging(final String loggerName, final LogLevel logLevel,
                                               final boolean logUserData) {
        config.tcpConfig().enableWireLogging(loggerName, logLevel, logUserData);
        return this;
    }

    @Override
    public HttpServerBuilder transportObserver(final TransportObserver transportObserver) {
        config.tcpConfig().transportObserver(transportObserver);
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
        final ReadOnlyHttpServerConfig roConfig = this.config.asReadOnly();
        executionContextBuilder.executionStrategy(strategy);
        final HttpExecutionContext httpExecutionContext = executionContextBuilder.build();
        if (roConfig.isH2PriorKnowledge()) {
            return H2ServerParentConnectionContext.bind(httpExecutionContext, roConfig, address, connectionAcceptor,
                    service, drainRequestPayloadBody);
        }
        return roConfig.tcpConfig().isAlpnConfigured() ?
                AlpnServerContext.bind(httpExecutionContext, roConfig, address, connectionAcceptor,
                        service, drainRequestPayloadBody) :
                NettyHttpServer.bind(httpExecutionContext, roConfig, address, connectionAcceptor,
                        service, drainRequestPayloadBody);
    }
}
