/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ReadOnlyServerSecurityConfig;

import io.netty.util.NetUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Configuration for TCP based servers.
 */
public final class TcpServerConfig extends AbstractTcpConfig<ReadOnlyServerSecurityConfig, ReadOnlyTcpServerConfig> {

    @Nullable
    private Map<String, ReadOnlyServerSecurityConfig> sniConfigs;
    private int backlog = NetUtil.SOMAXCONN;
    @Nullable
    private TransportObserver transportObserver;

    @Nullable
    Map<String, ReadOnlyServerSecurityConfig> sniConfigs() {
        return sniConfigs;
    }

    int backlog() {
        return backlog;
    }

    @Nullable
    TransportObserver transportObserver() {
        return transportObserver;
    }

    /**
     * Add security related config.
     *
     * @param securityConfig the {@link ReadOnlyServerSecurityConfig} for the passed hostnames
     * @param sniHostnames SNI hostnames for which this config is defined
     * @return {@code this}
     */
    public TcpServerConfig secure(final ReadOnlyServerSecurityConfig securityConfig, final String... sniHostnames) {
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
     * The maximum queue length for incoming connection indications (a request to connect) is set to the backlog
     * parameter. If a connection indication arrives when the queue is full, the connection may time out.
     *
     * @param backlog the backlog to use when accepting connections
     * @return {@code this}
     */
    public TcpServerConfig backlog(final int backlog) {
        if (backlog < 0) {
            throw new IllegalArgumentException("backlog must be >= 0");
        }
        this.backlog = backlog;
        return this;
    }

    /**
     * Sets a {@link TransportObserver} that provides visibility into transport events.
     *
     * @param transportObserver A {@link TransportObserver} that provides visibility into transport events.
     */
    public void transportObserver(final TransportObserver transportObserver) {
        this.transportObserver = requireNonNull(transportObserver);
    }

    @Override
    public ReadOnlyTcpServerConfig asReadOnly(final List<String> supportedAlpnProtocols) {
        return new ReadOnlyTcpServerConfig(this, supportedAlpnProtocols);
    }
}
