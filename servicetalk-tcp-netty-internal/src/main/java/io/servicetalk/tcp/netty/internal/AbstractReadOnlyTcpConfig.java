/*
 * Copyright © 2019-2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.logging.api.UserDataLoggerConfig;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;
import io.servicetalk.transport.netty.internal.FlushStrategy;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;

/**
 * Read only view of {@link AbstractTcpConfig}.
 *
 * @param <SecurityConfig> type of security configuration
 * @param <ReadOnlyView> type of read-only view
 */
abstract class AbstractReadOnlyTcpConfig<SecurityConfig, ReadOnlyView> {

    @SuppressWarnings("rawtypes")
    private final Map<ChannelOption, Object> options;
    @Nullable
    private final Long idleTimeoutMs;
    private final FlushStrategy flushStrategy;
    @Nullable
    private final UserDataLoggerConfig wireLoggerConfig;
    @Nullable
    private final String preferredAlpnProtocol;

    protected AbstractReadOnlyTcpConfig(final AbstractTcpConfig<SecurityConfig, ReadOnlyView> from,
                                        @Nullable final String preferredAlpnProtocol) {
        options = from.options() == null ? emptyMap() : unmodifiableMap(new HashMap<>(from.options()));
        idleTimeoutMs = from.idleTimeoutMs();
        flushStrategy = from.flushStrategy();
        wireLoggerConfig = from.wireLoggerConfig();
        this.preferredAlpnProtocol = preferredAlpnProtocol;
    }

    /**
     * Returns the {@link ChannelOption}s for all channels.
     *
     * @return Unmodifiable map of options
     */
    @SuppressWarnings("rawtypes")
    public final Map<ChannelOption, Object> options() {
        return options;
    }

    /**
     * Returns the idle timeout as expressed via option {@link ServiceTalkSocketOptions#IDLE_TIMEOUT}.
     *
     * @return idle timeout in milliseconds
     */
    @Nullable
    public final Long idleTimeoutMs() {
        return idleTimeoutMs;
    }

    /**
     * Returns the {@link FlushStrategy} for this client.
     *
     * @return {@link FlushStrategy} for this client
     */
    public final FlushStrategy flushStrategy() {
        return flushStrategy;
    }

    /**
     * Get the {@link UserDataLoggerConfig} for wire logging.
     *
     * @return the {@link UserDataLoggerConfig} for wire logging, or {@code null}.
     */
    @Nullable
    public final UserDataLoggerConfig wireLoggerConfig() {
        return wireLoggerConfig;
    }

    /**
     * Returns {@code true} if the <a href="https://tools.ietf.org/html/rfc7301#section-6">TLS ALPN Extension</a> is
     * configured.
     *
     * @return {@code true} if the <a href="https://tools.ietf.org/html/rfc7301#section-6">TLS ALPN Extension</a> is
     * configured
     */
    public boolean isAlpnConfigured() {
        return preferredAlpnProtocol != null;
    }

    /**
     * Get the preferred ALPN protocol. If a protocol sensitive decision must be made without knowing which protocol is
     * negotiated (e.g. at the client level) this protocol can be used as a best guess.
     * @return the preferred ALPN protocol.
     */
    @Nullable
    public String preferredAlpnProtocol() {
        return preferredAlpnProtocol;
    }

    /**
     * Returns the {@link SslContext}.
     *
     * @return {@link SslContext}, {@code null} if none specified
     */
    @Nullable
    public abstract SslContext sslContext();
}
