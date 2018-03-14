/**
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

import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.SslContext;
import io.netty.util.DomainMappingBuilder;
import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.transport.api.IoExecutorGroup;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.BuilderUtils;
import io.servicetalk.transport.netty.internal.WireLogInitializer;

import java.io.InputStream;
import java.net.SocketOption;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
     * @param group {@link IoExecutorGroup} to use for the server.
     */
    public TcpServerConfig(boolean autoRead, IoExecutorGroup group) {
        super(group, autoRead);
    }

    /**
     * The maximum queue length for incoming connection indications (a request to connect) is set to the backlog parameter.
     * If a connection indication arrives when the queue is full, the connection may time out.
     *
     * @param backlog the backlog to use when accepting connections.
     * @return this.
     */
    public TcpServerConfig setBacklog(int backlog) {
        if (backlog < 0) {
            throw new IllegalArgumentException("backlog must be >= 0");
        }
        this.backlog = backlog;
        return this;
    }

    /**
     * Allow to specify the amount of time the server will try to wait for active connections to become inactive before
     * closing these forcible when close is called.
     *
     * @param duration timeout duration.
     * @param unit time unit.
     * @return this.
     */
    public TcpServerConfig setGracefulCloseTime(long duration, TimeUnit unit) {
        if (duration < 0) {
            throw new IllegalArgumentException("gracefulCloseTimeoutMs must be >= 0");
        }
        this.gracefulCloseTimeMs = unit.toMillis(duration);
        return this;
    }

    /**
     * Specify the {@link BufferAllocator} to use.
     * @param allocator the {@link BufferAllocator} to use for allocate new buffers.
     * @return this.
     */
    public TcpServerConfig setAllocator(BufferAllocator allocator) {
        this.allocator = requireNonNull(allocator);
        return this;
    }

    /**
     * Specify the {@link IoExecutorGroup} used to obtain the threads to handle the I/O.
     * @param group the group to use.
     * @return this.
     */
    public TcpServerConfig setIoExecutorGroup(IoExecutorGroup group) {
        this.group = requireNonNull(group);
        return this;
    }

    /**
     * Allows to setup SNI.
     * You can either use {@link #setSslConfig(SslConfig)} or this method.
     * @param mappings mapping hostnames to the ssl configuration that should be used.
     * @param defaultConfig the configuration to use if no hostnames matched from {@code mappings}.
     * @return this.
     * @throws IllegalStateException if the {@link SslConfig#getKeyCertChainSupplier()}, {@link SslConfig#getKeySupplier()}, or {@link SslConfig#getTrustCertChainSupplier()}
     * throws when {@link InputStream#close()} is called.
     */
    public TcpServerConfig sni(@Nullable Map<String, SslConfig> mappings, SslConfig defaultConfig) {
        if (sslContext != null) {
            throw new IllegalStateException("withSsl(...) was already used");
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
     * @throws IllegalStateException if the {@link SslConfig#getKeyCertChainSupplier()}, {@link SslConfig#getKeySupplier()}, or {@link SslConfig#getTrustCertChainSupplier()}
     * throws when {@link InputStream#close()} is called.
     */
    public TcpServerConfig setSslConfig(@Nullable SslConfig config) {
        if (config != null) {
            if (mappings != null) {
                throw new IllegalStateException("withSni(...) was already used");
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
    public <T> TcpServerConfig setOption(SocketOption<T> option, T value) {
        if (option == ServiceTalkSocketOptions.IDLE_TIMEOUT) {
            idleTimeoutMs = (Long) value;
        } else {
            BuilderUtils.addOption(optionMap, option, value);
        }
        return this;
    }

    /**
     * Enables wire-logging for this server at debug level.
     *
     * @param loggerName Name of the logger.
     * @return {@code this}.
     */
    public TcpServerConfig setWireLoggerName(String loggerName) {
        wireLogger = new WireLogInitializer(loggerName, LogLevel.DEBUG);
        return this;
    }

    /**
     * Disabled wire-logging for this server at debug level.
     *
     * @return {@code this}.
     */
    public TcpServerConfig disableWireLog() {
        wireLogger = null;
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
