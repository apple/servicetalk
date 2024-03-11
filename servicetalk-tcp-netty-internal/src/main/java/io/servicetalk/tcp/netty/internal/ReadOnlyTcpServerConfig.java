/*
 * Copyright © 2018-2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.api.SslListenMode;
import io.servicetalk.transport.api.TransportObserver;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.util.Mapping;

import java.net.SocketOption;
import java.time.Duration;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Read only view of {@link TcpServerConfig}.
 */
public interface ReadOnlyTcpServerConfig extends ReadOnlyTcpConfig<ServerSslConfig> {

    /**
     * Returns {@code true} if the <a href="https://tools.ietf.org/html/rfc7301#section-6">TLS ALPN Extension</a> is
     * configured on either default or any of the SNI configurations.
     *
     * @return {@code true} if the <a href="https://tools.ietf.org/html/rfc7301#section-6">TLS ALPN Extension</a> is
     * configured on either default or any of the SNI configurations.
     */
    boolean isAlpnConfigured();

    /**
     * Returns the {@link TransportObserver} if any for all channels.
     *
     * @return the {@link TransportObserver} if any
     */
    TransportObserver transportObserver();

    SslListenMode sslListenMode();

    /**
     * Gets the {@link Mapping} for SNI.
     *
     * @return the {@link Mapping} for SNI, {@code null} if SNI isn't enabled.
     */
    @Nullable
    Mapping<String, SslContext> sniMapping();

    int sniMaxClientHelloLength();

    Duration sniClientHelloTimeout();

    /**
     * Returns the {@link SocketOption}s that are applied to the server socket channel which listens/accepts socket
     * channels.
     *
     * @return Unmodifiable map of options
     */
    @SuppressWarnings("rawtypes")
    Map<ChannelOption, Object> listenOptions();
}
