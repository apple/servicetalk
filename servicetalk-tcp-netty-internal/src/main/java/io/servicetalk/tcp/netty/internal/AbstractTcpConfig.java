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

import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.logging.api.UserDataLoggerConfig;
import io.servicetalk.logging.slf4j.internal.DefaultUserDataLoggerConfig;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.FlushStrategy;

import io.netty.channel.ChannelOption;

import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.FlushStrategies.defaultFlushStrategy;
import static io.servicetalk.transport.netty.internal.SocketOptionUtils.addOption;
import static java.util.Objects.requireNonNull;

/**
 * Common configuration for TCP based clients and servers.
 *
 * @param <SslConfigType> type of {@link SslConfig}.
 */
abstract class AbstractTcpConfig<SslConfigType> {

    @Nullable
    @SuppressWarnings("rawtypes")
    private Map<ChannelOption, Object> options;
    @Nullable
    private Long idleTimeoutMs;
    private FlushStrategy flushStrategy = defaultFlushStrategy();
    @Nullable
    private UserDataLoggerConfig wireLoggerConfig;
    @Nullable
    private SslConfigType sslConfig;

    protected AbstractTcpConfig() {
    }

    protected AbstractTcpConfig(final AbstractTcpConfig<SslConfigType> from) {
        options = from.options;
        idleTimeoutMs = from.idleTimeoutMs;
        flushStrategy = from.flushStrategy;
        wireLoggerConfig = from.wireLoggerConfig;
        sslConfig = from.sslConfig;
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
    final UserDataLoggerConfig wireLoggerConfig() {
        return wireLoggerConfig;
    }

    /**
     * Get the {@link SslConfigType}.
     *
     * @return the {@link SslConfigType}, or {@code null} if SSL/TLS is not configured.
     */
    @Nullable
    public final SslConfigType sslConfig() {
        return sslConfig;
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
     * Enable wire-logging for all connections.
     *
     * @param loggerName provides the logger to log data/events to/from the wire.
     * @param logLevel the level to log data/events to/from the wire.
     * @param logUserData {@code true} to include user data (e.g. data, headers, etc.). {@code false} to exclude user
     * data and log only network events. This method is invoked for each data object allowing for dynamic behavior.
     */
    public final void enableWireLogging(final String loggerName,
                                        final LogLevel logLevel,
                                        final BooleanSupplier logUserData) {
        wireLoggerConfig = new DefaultUserDataLoggerConfig(loggerName, logLevel, logUserData);
    }

    /**
     * Add SSL/TLS related config.
     *
     * @param sslConfig the {@link SslConfigType}.
     */
    public final void sslConfig(final SslConfigType sslConfig) {
        this.sslConfig = requireNonNull(sslConfig);
    }
}
