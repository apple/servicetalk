/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServerSecurityConfigurator;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpServerConfig;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;

import io.netty.handler.ssl.ApplicationProtocolNegotiator;
import io.netty.handler.ssl.SslContext;

import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;

final class DefaultHttpServerBuilder extends HttpServerBuilder {

    private final HttpServerConfig config = new HttpServerConfig();
    private final HttpExecutionContextBuilder executionContextBuilder = new HttpExecutionContextBuilder();
    private SocketAddress address;

    DefaultHttpServerBuilder(SocketAddress address) {
        this.address = address;
    }

    @Override
    public HttpServerBuilder headersFactory(final HttpHeadersFactory headersFactory) {
        config.headersFactory(headersFactory);
        return this;
    }

    @Override
    public HttpServerBuilder h2HeadersFactory(final HttpHeadersFactory headersFactory) {
        config.h2ServerConfig().h2HeadersFactory(headersFactory);
        return this;
    }

    @Override
    public HttpServerBuilder h2HeadersSensitivityDetector(
            final BiPredicate<CharSequence, CharSequence> h2HeadersSensitivityDetector) {
        config.h2ServerConfig().h2HeadersSensitivityDetector(h2HeadersSensitivityDetector);
        return this;
    }

    @Override
    public HttpServerBuilder h2PriorKnowledge(final boolean h2PriorKnowledge) {
        config.tcpConfig().autoRead(h2PriorKnowledge);
        config.h2PriorKnowledge(h2PriorKnowledge);
        return this;
    }

    @Override
    public HttpServerBuilder h2FrameLogger(@Nullable final String h2FrameLogger) {
        config.h2ServerConfig().h2FrameLogger(h2FrameLogger);
        return this;
    }

    @Override
    public HttpServerBuilder clientCloseTimeout(final long clientCloseTimeoutMs) {
        config.clientCloseTimeout(clientCloseTimeoutMs);
        return this;
    }

    @Override
    public HttpServerBuilder maxInitialLineLength(final int maxInitialLineLength) {
        config.maxInitialLineLength(maxInitialLineLength);
        return this;
    }

    @Override
    public HttpServerBuilder maxHeaderSize(final int maxHeaderSize) {
        config.maxHeaderSize(maxHeaderSize);
        return this;
    }

    @Override
    public HttpServerBuilder headersEncodedSizeEstimate(final int headersEncodedSizeEstimate) {
        config.headersEncodedSizeEstimate(headersEncodedSizeEstimate);
        return this;
    }

    @Override
    public HttpServerBuilder trailersEncodedSizeEstimate(final int trailersEncodedSizeEstimate) {
        config.trailersEncodedSizeEstimate(trailersEncodedSizeEstimate);
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
    public HttpServerBuilder disableWireLogging() {
        config.tcpConfig().disableWireLogging();
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
        return useAlpn(roConfig.tcpConfig()) ?
                AlpnServerContext.bind(httpExecutionContext, roConfig, address, connectionAcceptor,
                        service, drainRequestPayloadBody) :
                NettyHttpServer.bind(httpExecutionContext, roConfig, address, connectionAcceptor,
                        service, drainRequestPayloadBody);
    }

    private static boolean useAlpn(final ReadOnlyTcpServerConfig config) {
        if (config.isSniEnabled()) {
            return true;
        }
        final SslContext sslContext = config.sslContext();
        if (sslContext == null) {
            return false;
        }
        @SuppressWarnings("deprecation")
        final ApplicationProtocolNegotiator apn = sslContext.applicationProtocolNegotiator();
        return apn != null && !apn.protocols().isEmpty();
    }
}
