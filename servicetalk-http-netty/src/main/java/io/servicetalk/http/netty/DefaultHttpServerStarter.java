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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpServerStarter;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestHandler;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ContextFilter;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.SslConfig;

import java.io.InputStream;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.Map;
import javax.annotation.Nullable;

import static io.servicetalk.http.netty.NettyHttpServer.bind;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;

/**
 * Netty implementation of {@link HttpServerStarter}.
 */
public final class DefaultHttpServerStarter implements HttpServerStarter {

    private final HttpServerConfig config;

    /**
     * New instance.
     */
    public DefaultHttpServerStarter() {
        this.config = new HttpServerConfig();
    }

    /**
     * Sets the {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding requests.
     *
     * @param headersFactory the {@link HttpHeadersFactory} to use.
     * @return this
     */
    public DefaultHttpServerStarter headersFactory(final HttpHeadersFactory headersFactory) {
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
    public DefaultHttpServerStarter clientCloseTimeout(final long clientCloseTimeoutMs) {
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
    public DefaultHttpServerStarter maxInitialLineLength(final int maxInitialLineLength) {
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
    public DefaultHttpServerStarter maxHeaderSize(final int maxHeaderSize) {
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
    public DefaultHttpServerStarter headersEncodedSizeEstimate(final int headersEncodedSizeEstimate) {
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
    public DefaultHttpServerStarter trailersEncodedSizeEstimate(final int trailersEncodedSizeEstimate) {
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
    public DefaultHttpServerStarter backlog(final int backlog) {
        config.getTcpConfig().setBacklog(backlog);
        return this;
    }

    /**
     * Allows to setup SNI.
     * You can either use {@link #sslConfig(SslConfig)} or this method.
     *
     * @param mappings mapping hostnames to the ssl configuration that should be used.
     * @param defaultConfig the configuration to use if no hostnames matched from {@code mappings}.
     * @return this.
     * @throws IllegalStateException if the {@link SslConfig#getKeyCertChainSupplier()},
     * {@link SslConfig#getKeySupplier()}, or {@link SslConfig#getTrustCertChainSupplier()} throws when
     * {@link InputStream#close()} is called.
     */
    public DefaultHttpServerStarter sniConfig(@Nullable final Map<String, SslConfig> mappings,
                                              final SslConfig defaultConfig) {
        config.getTcpConfig().setSniConfig(mappings, defaultConfig);
        return this;
    }

    /**
     * Enable SSL/TLS using the provided {@link SslConfig}. To disable it pass in {@code null}.
     *
     * @param sslConfig the {@link SslConfig}.
     * @return this.
     * @throws IllegalStateException if the {@link SslConfig#getKeyCertChainSupplier()},
     * {@link SslConfig#getKeySupplier()}, or {@link SslConfig#getTrustCertChainSupplier()} throws when
     * {@link InputStream#close()} is called.
     */
    public DefaultHttpServerStarter sslConfig(@Nullable final SslConfig sslConfig) {
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
    public <T> DefaultHttpServerStarter socketOption(final SocketOption<T> option, final T value) {
        config.getTcpConfig().setSocketOption(option, value);
        return this;
    }

    /**
     * Enable wire-logging for this server. All wire events will be logged at trace level.
     *
     * @param loggerName The name of the logger to log wire events.
     * @return {@code this}.
     */
    public DefaultHttpServerStarter enableWireLogging(final String loggerName) {
        config.getTcpConfig().enableWireLogging(loggerName);
        return this;
    }

    /**
     * Disable previously configured wire-logging for this server.
     * If wire-logging has not been configured before, this method has no effect.
     *
     * @return {@code this}.
     * @see #enableWireLogging(String)
     */
    public DefaultHttpServerStarter disableWireLogging() {
        config.getTcpConfig().disableWireLogging();
        return this;
    }

    @Override
    public Single<ServerContext> startStreaming(final ExecutionContext executionContext, final SocketAddress address,
                                                final ContextFilter contextFilter,
                                                final StreamingHttpRequestHandler handler) {
        return bind(executionContext, config.asReadOnly(), address, contextFilter,
                handler instanceof StreamingHttpService ?
                        (StreamingHttpService) handler :
                        new StreamingHttpService() {
                            @Override
                            public Single<StreamingHttpResponse> handle(
                                    final HttpServiceContext ctx, final StreamingHttpRequest request,
                                    final StreamingHttpResponseFactory responseFactory) {
                                return handler.handle(ctx, request, responseFactory);
                            }
                        });
    }

    @Override
    public Single<ServerContext> startStreaming(final SocketAddress address, final ContextFilter contextFilter,
                                                final StreamingHttpRequestHandler service) {
        return startStreaming(globalExecutionContext(), address, contextFilter, service);
    }
}
