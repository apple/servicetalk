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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.transport.api.ServiceTalkSocketOptions;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.BuilderUtils;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.WireLoggingInitializer;

import io.netty.handler.ssl.SslContext;
import io.netty.util.DomainMappingBuilder;

import java.io.InputStream;
import java.net.SocketOption;
import java.util.Map;
import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.SSLContextFactory.forServer;
import static java.util.Objects.requireNonNull;

/**
 * Configuration for TCP based servers. <p>Internal use only.</p>
 */
public final class TcpServerConfig extends ReadOnlyTcpServerConfig {

    /**
     * New instance.
     *
     * @param autoRead If the channels accepted by the server will have auto-read enabled.
     */
    public TcpServerConfig(boolean autoRead) {
        super(autoRead);
    }

    /**
     * The maximum queue length for incoming connection indications (a request to connect) is set to the backlog parameter.
     * If a connection indication arrives when the queue is full, the connection may time out.
     *
     * @param backlog the backlog to use when accepting connections.
     * @return this.
     */
    public TcpServerConfig backlog(int backlog) {
        if (backlog < 0) {
            throw new IllegalArgumentException("backlog must be >= 0");
        }
        this.backlog = backlog;
        return this;
    }

    /**
     * Allows to setup SNI.
     * You can either use {@link #sslConfig(SslConfig)} or this method.
     * @param mappings mapping hostnames to the ssl configuration that should be used.
     * @param defaultConfig the configuration to use if no hostnames matched from {@code mappings}.
     * @return this.
     * @throws IllegalStateException if the {@link SslConfig#keyCertChainSupplier()}, {@link SslConfig#keySupplier()},
     * or {@link SslConfig#trustCertChainSupplier()}
     * throws when {@link InputStream#close()} is called.
     */
    public TcpServerConfig sniConfig(@Nullable Map<String, SslConfig> mappings, SslConfig defaultConfig) {
        if (sslContext != null) {
            throw new IllegalStateException("sslConfig(...) was already used");
        } else {
            if (mappings != null) {
                DomainMappingBuilder<SslContext> builder = new DomainMappingBuilder<>(forServer(defaultConfig));

                for (Map.Entry<String, SslConfig> entry : mappings.entrySet()) {
                    SslContext ctx = forServer(entry.getValue());
                    builder.add(entry.getKey(), ctx);
                }
                this.mappings = builder.build();
            } else {
                this.mappings = null;
            }
        }
        return this;
    }

    /**
     * Enable SSL/TLS using the provided {@link SslConfig}. To disable it pass in {@code null}.
     * @param config the {@link SslConfig}.
     * @return this.
     * @throws IllegalStateException if the {@link SslConfig#keyCertChainSupplier()}, {@link SslConfig#keySupplier()},
     * or {@link SslConfig#trustCertChainSupplier()}
     * throws when {@link InputStream#close()} is called.
     */
    public TcpServerConfig sslConfig(@Nullable SslConfig config) {
        if (config != null) {
            if (mappings != null) {
                throw new IllegalStateException("sniConfig(...) was already used");
            }
            sslContext = forServer(config);
        } else {
            sslContext = null;
        }
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
    public <T> TcpServerConfig socketOption(SocketOption<T> option, T value) {
        if (option == ServiceTalkSocketOptions.IDLE_TIMEOUT) {
            idleTimeoutMs = (Long) value;
        } else {
            BuilderUtils.addOption(options, option, value);
        }
        return this;
    }

    /**
     * Enable wire-logging for this server. All wire events will be logged at trace level.
     *
     * @param loggerName The name of the logger to log wire events.
     * @return {@code this}.
     */
    public TcpServerConfig enableWireLogging(String loggerName) {
        wireLoggingInitializer = new WireLoggingInitializer(loggerName);
        return this;
    }

    /**
     * Disable previously configured wire-logging for this server.
     * If wire-logging has not been configured before, this method has no effect.
     *
     * @return {@code this}.
     * @see #enableWireLogging(String)
     */
    public TcpServerConfig disableWireLogging() {
        wireLoggingInitializer = null;
        return this;
    }

    /**
     * Sets {@link FlushStrategy} to use for all connections accepted by this server.
     *
     * @param flushStrategy {@link FlushStrategy} to use for all connections accepted by this server.
     * @return {@code this}.
     */
    public TcpServerConfig flushStrategy(FlushStrategy flushStrategy) {
        this.flushStrategy = requireNonNull(flushStrategy);
        return this;
    }

    /**
     * Returns an immutable view of this config, any changes to this config will not alter the returned view.
     *
     * @return {@link ReadOnlyTcpServerConfig}.
     */
    public ReadOnlyTcpServerConfig asReadOnly() {
        return new ReadOnlyTcpServerConfig(this);
    }
}
