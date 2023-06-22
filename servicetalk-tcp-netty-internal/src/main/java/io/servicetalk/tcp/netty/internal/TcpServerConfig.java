/*
 * Copyright © 2018-2021, 2023 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.NoopTransportObserver;

import io.netty.channel.ChannelOption;

import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.SocketOptionUtils.addOption;
import static io.servicetalk.utils.internal.DurationUtils.ensureNonNegative;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;

/**
 * Configuration for TCP based servers.
 */
public final class TcpServerConfig extends AbstractTcpConfig<ServerSslConfig> {
    /**
     * The maximum length of ClientHello message as defined by
     * <a href="https://www.rfc-editor.org/rfc/rfc5246#section-7.4.1.2">RFC5246</a> and
     * <a href="https://datatracker.ietf.org/doc/html/rfc6347#section-3.2.3">RFC6347</a> as {@code 2^24 - 1}.
     */
    private static final int MAX_CLIENT_HELLO_LENGTH = 0xFFFFFF;
    private static final Duration DEFAULT_CLIENT_HELLO_TIMEOUT = ofSeconds(10); // same as default in Netty SslHandler

    @Nullable
    @SuppressWarnings("rawtypes")
    private Map<ChannelOption, Object> listenOptions;
    private TransportObserver transportObserver = NoopTransportObserver.INSTANCE;
    @Nullable
    private Map<String, ServerSslConfig> sniConfig;
    private int sniMaxClientHelloLength = MAX_CLIENT_HELLO_LENGTH;
    private Duration sniClientHelloTimeout = DEFAULT_CLIENT_HELLO_TIMEOUT;

    @Nullable
    @SuppressWarnings("rawtypes")
    Map<ChannelOption, Object> listenOptions() {
        return listenOptions;
    }

    TransportObserver transportObserver() {
        return transportObserver;
    }

    @Nullable
    public Map<String, ServerSslConfig> sniConfig() {
        return sniConfig;
    }

    int sniMaxClientHelloLength() {
        return sniMaxClientHelloLength;
    }

    Duration sniClientHelloTimeout() {
        return sniClientHelloTimeout;
    }

    /**
     * Sets a {@link TransportObserver} that provides visibility into transport events.
     *
     * @param transportObserver A {@link TransportObserver} that provides visibility into transport events.
     */
    public void transportObserver(final TransportObserver transportObserver) {
        this.transportObserver = requireNonNull(transportObserver);
    }

    /**
     * Add SSL/TLS and SNI related config.
     *
     * @param defaultSslConfig the default {@link ServerSslConfig} used when no SNI match is found.
     * @param sniConfig client SNI hostname values are matched against keys in this {@link Map} and if a match is
     * found the corresponding {@link ServerSslConfig} is used.
     * @return {@code this}
     */
    public TcpServerConfig sslConfig(ServerSslConfig defaultSslConfig, Map<String, ServerSslConfig> sniConfig) {
        sslConfig(defaultSslConfig);
        this.sniConfig = requireNonNull(sniConfig);
        return this;
    }

    /**
     * Add SSL/TLS and SNI related config.
     *
     * @param defaultSslConfig the default {@link ServerSslConfig} used when no SNI match is found.
     * @param sniConfig client SNI hostname values are matched against keys in this {@link Map} and if a match is
     * found the corresponding {@link ServerSslConfig} is used.
     * @param maxClientHelloLength the maximum length of a
     * <a href="https://www.rfc-editor.org/rfc/rfc5246#section-7.4.1.2">ClientHello</a> message in bytes, up to
     * {@code 2^24 - 1} bytes. Zero ({@code 0}) disables validation.
     * @param clientHelloTimeout The timeout for waiting until
     * <a href="https://www.rfc-editor.org/rfc/rfc5246#section-7.4.1.2">ClientHello</a> message is received.
     * Implementations can round the specified {@link Duration} to full time units, depending on their time granularity.
     * {@link Duration#ZERO Zero (0)} disables timeout.
     * @return {@code this}
     */
    public TcpServerConfig sslConfig(ServerSslConfig defaultSslConfig, Map<String, ServerSslConfig> sniConfig,
                                     int maxClientHelloLength, Duration clientHelloTimeout) {
        sslConfig(defaultSslConfig, sniConfig);
        if (maxClientHelloLength < 0 || maxClientHelloLength > MAX_CLIENT_HELLO_LENGTH) {
            throw new IllegalArgumentException("maxClientHelloLength: " + maxClientHelloLength +
                    "(expected [0, " + MAX_CLIENT_HELLO_LENGTH + ']');
        }
        this.sniMaxClientHelloLength = maxClientHelloLength;
        this.sniClientHelloTimeout = ensureNonNegative(clientHelloTimeout, "clientHelloTimeout");
        return this;
    }

    /**
     * Adds a {@link SocketOption} that is applied to the server socket channel which listens/accepts socket channels.
     *
     * @param <T> the type of the value
     * @param option the option to apply
     * @param value the value
     * @throws IllegalArgumentException if the {@link SocketOption} is not supported
     * @see StandardSocketOptions
     * @see ServiceTalkSocketOptions
     */
    public <T> void listenSocketOption(final SocketOption<T> option, T value) {
        if (listenOptions == null) {
            listenOptions = new HashMap<>();
        }
        addOption(listenOptions, option, value);
    }

    /**
     * Create a read only view of this object.
     * @return a read only view of this object.
     */
    public ReadOnlyTcpServerConfig asReadOnly() {
        return new ReadOnlyTcpServerConfig(this);
    }
}
