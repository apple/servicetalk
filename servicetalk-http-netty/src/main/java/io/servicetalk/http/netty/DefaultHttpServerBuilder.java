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
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.ExecutionContextBuilder;

import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.Map;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

final class DefaultHttpServerBuilder extends HttpServerBuilder {

    private final HttpServerConfig config = new HttpServerConfig();
    private final ExecutionContextBuilder executionContextBuilder = new ExecutionContextBuilder();
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
    public HttpServerBuilder sslConfig(@Nullable final SslConfig sslConfig) {
        config.tcpConfig().sslConfig(sslConfig);
        return this;
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
    public HttpServerBuilder address(final SocketAddress address) {
        this.address = requireNonNull(address);
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
    public Single<ServerContext> doListen(final ConnectionAcceptor connectionAcceptor,
                                          final StreamingHttpService service) {
        ReadOnlyHttpServerConfig roConfig = this.config.asReadOnly();
        Executor executor = service.executionStrategy().executor();
        if (executor != null) {
            executionContextBuilder.executor(executor);
        }
        return NettyHttpServer.bind(executionContextBuilder.build(), roConfig, address, connectionAcceptor,
                service);
    }
}
