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
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpServerStarter;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.transport.api.ContextFilter;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.NettyIoExecutor;

import java.io.InputStream;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.Map;
import javax.annotation.Nullable;

import static io.servicetalk.http.netty.NettyHttpServer.bind;

/**
 * Netty implementation of {@link HttpServerStarter}.
 */
public final class DefaultHttpServerStarter implements HttpServerStarter {

    private final HttpServerConfig config;

    /**
     * New instance.
     *
     * @param ioExecutor {@link NettyIoExecutor} to use for the server.
     */
    public DefaultHttpServerStarter(final IoExecutor ioExecutor) {
        this.config = new HttpServerConfig(ioExecutor);
    }

    /**
     * Sets the {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding requests.
     *
     * @param headersFactory the {@link HttpHeadersFactory} to use.
     * @return this
     */
    public DefaultHttpServerStarter setHeadersFactory(final HttpHeadersFactory headersFactory) {
        config.setHeadersFactory(headersFactory);
        return this;
    }

    /**
     * Set how long to wait (in milliseconds) for a client to close the connection (if no keep-alive is set) before the
     * server will close the connection.
     *
     * @param clientCloseTimeoutMs {@code 0} if the server should close the connection immediately, or
     * {@code > 0} if a wait time should be used.
     * @return this
     */
    public DefaultHttpServerStarter setClientCloseTimeout(final long clientCloseTimeoutMs) {
        config.setClientCloseTimeout(clientCloseTimeoutMs);
        return this;
    }

    /**
     * The server will throw TooLongFrameException if the initial HTTP line exceeds this length.
     *
     * @param maxInitialLineLength The server will throw TooLongFrameException if the initial HTTP line exceeds this
     * length.
     * @return this.
     */
    public DefaultHttpServerStarter setMaxInitialLineLength(final int maxInitialLineLength) {
        config.setMaxInitialLineLength(maxInitialLineLength);
        return this;
    }

    /**
     * The server will throw TooLongFrameException if the total size of all HTTP headers exceeds this length.
     *
     * @param maxHeaderSize The server will throw TooLongFrameException if the total size of all HTTP headers exceeds
     * this length.
     * @return this.
     */
    public DefaultHttpServerStarter setMaxHeaderSize(final int maxHeaderSize) {
        config.setMaxHeaderSize(maxHeaderSize);
        return this;
    }

    /**
     * Used to calculate an exponential moving average of the encoded size of the initial line and the headers for a
     * guess for future buffer allocations.
     *
     * @param headersEncodedSizeEstimate estimated initial value.
     * @return this
     */
    public DefaultHttpServerStarter setHeadersEncodedSizeEstimate(final int headersEncodedSizeEstimate) {
        config.setHeadersEncodedSizeEstimate(headersEncodedSizeEstimate);
        return this;
    }

    /**
     * Used to calculate an exponential moving average of the encoded size of the trailers for a guess for future
     * buffer allocations.
     *
     * @param trailersEncodedSizeEstimate estimated initial value.
     * @return this;
     */
    public DefaultHttpServerStarter setTrailersEncodedSizeEstimate(final int trailersEncodedSizeEstimate) {
        config.setTrailersEncodedSizeEstimate(trailersEncodedSizeEstimate);
        return this;
    }

    /**
     * The maximum queue length for incoming connection indications (a request to connect) is set to the backlog
     * parameter. If a connection indication arrives when the queue is full, the connection may time out.
     *
     * @param backlog the backlog to use when accepting connections.
     * @return this.
     */
    public DefaultHttpServerStarter setBacklog(final int backlog) {
        config.getTcpConfig().setBacklog(backlog);
        return this;
    }

    /**
     * Specify the {@link BufferAllocator} to use.
     *
     * @param allocator the {@link BufferAllocator} to use for allocate new buffers.
     * @return this.
     */
    public DefaultHttpServerStarter setAllocator(final BufferAllocator allocator) {
        config.getTcpConfig().setAllocator(allocator);
        return this;
    }

    /**
     * Allows to setup SNI.
     * You can either use {@link #setSslConfig(SslConfig)} or this method.
     *
     * @param mappings mapping hostnames to the ssl configuration that should be used.
     * @param defaultConfig the configuration to use if no hostnames matched from {@code mappings}.
     * @return this.
     * @throws IllegalStateException if the {@link SslConfig#getKeyCertChainSupplier()}, {@link SslConfig#getKeySupplier()}, or {@link SslConfig#getTrustCertChainSupplier()}
     * throws when {@link InputStream#close()} is called.
     */
    public DefaultHttpServerStarter setSniConfig(@Nullable final Map<String, SslConfig> mappings, final SslConfig defaultConfig) {
        config.getTcpConfig().setSniConfig(mappings, defaultConfig);
        return this;
    }

    /**
     * Enable SSL/TLS using the provided {@link SslConfig}. To disable it pass in {@code null}.
     *
     * @param sslConfig the {@link SslConfig}.
     * @return this.
     * @throws IllegalStateException if the {@link SslConfig#getKeyCertChainSupplier()}, {@link SslConfig#getKeySupplier()}, or {@link SslConfig#getTrustCertChainSupplier()}
     * throws when {@link InputStream#close()} is called.
     */
    public DefaultHttpServerStarter setSslConfig(@Nullable final SslConfig sslConfig) {
        config.getTcpConfig().setSslConfig(sslConfig);
        return this;
    }

    /**
     * Add a {@link SocketOption} that is applied.
     *
     * @param <T> the type of the value.
     * @param option the option to apply.
     * @param value the value.
     * @return this.
     */
    public <T> DefaultHttpServerStarter setSocketOption(final SocketOption<T> option, final T value) {
        config.getTcpConfig().setOption(option, value);
        return this;
    }

    /**
     * Enables wire-logging for this server at debug level.
     *
     * @param loggerName Name of the logger.
     * @return {@code this}.
     */
    public DefaultHttpServerStarter setWireLoggerName(final String loggerName) {
        config.getTcpConfig().setWireLoggerName(loggerName);
        return this;
    }

    /**
     * Disabled wire-logging for this server at debug level.
     *
     * @return {@code this}.
     */
    public DefaultHttpServerStarter disableWireLog() {
        config.getTcpConfig().disableWireLog();
        return this;
    }

    @Override
    public Single<ServerContext> start(final SocketAddress address, final ContextFilter contextFilter,
                                       final Executor executor,
                                       final HttpService service) {
        return bind(config.asReadOnly(), address, contextFilter, executor, service);
    }
}
