/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ChainingSslConfigBuilders;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.SslConfig;

import java.io.InputStream;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;

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
    public HttpServerBuilder sniConfig(@Nullable final Map<String, SslConfig> mappings, final SslConfig defaultConfig) {
        config.tcpConfig().sniConfig(mappings, defaultConfig);
        return this;
    }

    @Override
    public ServerSslConfigBuilder<HttpServerBuilder> enableSsl(final KeyManagerFactory keyManagerFactory) {
        return ChainingSslConfigBuilders.forServer(() -> this, sslConfig -> config.tcpConfig().sslConfig(sslConfig),
                keyManagerFactory);
    }

    @Override
    public ServerSslConfigBuilder<HttpServerBuilder> enableSsl(final Supplier<InputStream> keyCertChainSupplier,
                                                               final Supplier<InputStream> keySupplier) {
        return ChainingSslConfigBuilders.forServer(() -> this, sslConfig -> config.tcpConfig().sslConfig(sslConfig),
                keyCertChainSupplier, keySupplier);
    }

    @Override
    public ServerSslConfigBuilder<HttpServerBuilder> enableSsl(final Supplier<InputStream> keyCertChainSupplier,
                                                               final Supplier<InputStream> keySupplier,
                                                               final String keyPassword) {
        return ChainingSslConfigBuilders.forServer(() -> this, sslConfig -> config.tcpConfig().sslConfig(sslConfig),
                keyCertChainSupplier, keySupplier, keyPassword);
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
    protected Single<ServerContext> doListen(@Nullable ConnectionAcceptor connectionAcceptor,
                                             StreamingHttpService service,
                                             HttpExecutionStrategy strategy,
                                             boolean drainRequestPayloadBody) {
        ReadOnlyHttpServerConfig roConfig = this.config.asReadOnly();
        executionContextBuilder.executionStrategy(strategy);
        return NettyHttpServer.bind(executionContextBuilder.build(), roConfig, address, connectionAcceptor,
                service, drainRequestPayloadBody);
    }
}
