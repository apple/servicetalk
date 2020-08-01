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

import io.servicetalk.transport.api.ServiceTalkSocketOptions;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.ReadOnlyServerSecurityConfig;

import io.netty.channel.ChannelOption;

import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.FlushStrategies.defaultFlushStrategy;
import static io.servicetalk.transport.netty.internal.SocketOptionUtils.addOption;
import static java.util.Objects.requireNonNull;

/**
 * Common configuration for TCP based clients and servers.
 *
 * @param <SecurityConfig> type of security configuration
 * @param <ReadOnlyView> type of read-only view
 */
abstract class AbstractTcpConfig<SecurityConfig, ReadOnlyView> {

    @Nullable
    @SuppressWarnings("rawtypes")
    private Map<ChannelOption, Object> options;
    @Nullable
    private Long idleTimeoutMs;
    private FlushStrategy flushStrategy = defaultFlushStrategy();
    @Nullable
    private String wireLoggerName;
    @Nullable
    private SecurityConfig securityConfig;

    protected AbstractTcpConfig() {
    }

    protected AbstractTcpConfig(final AbstractTcpConfig<SecurityConfig, ReadOnlyView> from) {
        options = from.options;
        idleTimeoutMs = from.idleTimeoutMs;
        flushStrategy = from.flushStrategy;
        wireLoggerName = from.wireLoggerName;
        securityConfig = from.securityConfig;
    }

    @Nullable
    @SuppressWarnings("rawtypes")
    final Map<ChannelOption, Object> options() {
        return options;
    }

    @Nullable
    final Long idleTimeoutMs() {
        return idleTimeoutMs;
    }

    final FlushStrategy flushStrategy() {
        return flushStrategy;
    }

    @Nullable
    final String wireLoggerName() {
        return wireLoggerName;
    }

    @Nullable
    final SecurityConfig securityConfig() {
        return securityConfig;
    }

    /**
     * Add a {@link SocketOption} that is applied.
     *
     * @param <T> the type of the value
     * @param option the option to apply
     * @param value the value
     * @throws IllegalArgumentException if the {@link SocketOption} is not supported
     * @see StandardSocketOptions
     * @see ServiceTalkSocketOptions
     */
    public final <T> void socketOption(final SocketOption<T> option, T value) {
        requireNonNull(option);
        requireNonNull(value);
        if (option == ServiceTalkSocketOptions.IDLE_TIMEOUT) {
            idleTimeoutMs = (Long) value;
        } else {
            if (options == null) {
                options = new HashMap<>();
            }
            addOption(options, option, value);
        }
    }

    /**
     * Sets {@link FlushStrategy} to use for all connections.
     *
     * @param flushStrategy {@link FlushStrategy} to use for all connections
     */
    public final void flushStrategy(final FlushStrategy flushStrategy) {
        this.flushStrategy = requireNonNull(flushStrategy);
    }

    /**
     * Enable wire-logging for all connections. All wire events will be logged at trace level.
     *
     * @param loggerName The name of the logger to log wire events
     */
    public final void enableWireLogging(final String loggerName) {
        wireLoggerName = requireNonNull(loggerName);
    }

    /**
     * Add security related config.
     *
     * @param securityConfig the {@link ReadOnlyServerSecurityConfig} to use
     */
    public final void secure(final SecurityConfig securityConfig) {
        this.securityConfig = requireNonNull(securityConfig);
    }

    /**
     * Returns an immutable view of this config, any changes to this config will not alter the returned view.
     *
     * @param supportedAlpnProtocols a list of supported protocols for ALPN configuration
     * @return an immutable view of this config
     */
    public abstract ReadOnlyView asReadOnly(List<String> supportedAlpnProtocols);
}
