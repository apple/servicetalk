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
import io.servicetalk.transport.netty.internal.BuilderUtils;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.ReadOnlyServerSecurityConfig;
import io.servicetalk.transport.netty.internal.WireLoggingInitializer;

import io.netty.handler.ssl.SslContext;
import io.netty.util.DomainNameMappingBuilder;

import java.net.SocketOption;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.SslContextFactory.forServer;
import static java.util.Objects.requireNonNull;

/**
 * Configuration for TCP based servers. <p>Internal use only.</p>
 */
public final class TcpServerConfig extends ReadOnlyTcpServerConfig {

    @Nullable
    private Map<String, ReadOnlyServerSecurityConfig> sniConfigs;

    /**
     * New instance.
     *
     * @param autoRead If the channels accepted by the server will have auto-read enabled.
     */
    public TcpServerConfig(boolean autoRead) {
        super(autoRead);
    }

    /**
     * Determine if auto read should be enabled.
     *
     * @param autoRead {@code true} to enable auto read.
     * @return this.
     */
    public TcpServerConfig autoRead(boolean autoRead) {
        super.autoRead = autoRead;
        return this;
    }

    /**
     * The maximum queue length for incoming connection indications (a request to connect) is set to the backlog
     * parameter. If a connection indication arrives when the queue is full, the connection may time out.
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
     * Add security related config.
     *
     * @param securityConfig the {@link ReadOnlyServerSecurityConfig} for the passed hostnames.
     * @param sniHostnames SNI hostnames for which this config is defined.
     * @return this.
     */
    public TcpServerConfig secure(ReadOnlyServerSecurityConfig securityConfig, String... sniHostnames) {
        requireNonNull(securityConfig);
        requireNonNull(sniHostnames);
        if (sniConfigs == null) {
            sniConfigs = new HashMap<>();
        }
        for (String sniHostname : sniHostnames) {
            sniConfigs.put(sniHostname, securityConfig);
        }
        return this;
    }

    /**
     * Add security related config.
     *
     * @param securityConfig the {@link ReadOnlyServerSecurityConfig} to use.
     * @return this.
     */
    public TcpServerConfig secure(ReadOnlyServerSecurityConfig securityConfig) {
        sslContext = forServer(securityConfig);
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
        if (sniConfigs != null) {
            if (sslContext == null) {
                throw new IllegalStateException("No default security config defined but found sni config mappings.");
            }
            DomainNameMappingBuilder<SslContext> mappingBuilder = new DomainNameMappingBuilder<>(sslContext);
            for (Map.Entry<String, ReadOnlyServerSecurityConfig> sniConfigEntries : sniConfigs.entrySet()) {
                mappingBuilder.add(sniConfigEntries.getKey(), forServer(sniConfigEntries.getValue()));
            }
            mappings = mappingBuilder.build();
        }
        return new ReadOnlyTcpServerConfig(this);
    }
}
