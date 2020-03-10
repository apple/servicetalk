/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.tcp.netty.internal.TcpClientConfig;

import javax.annotation.Nullable;

final class HttpClientConfig {

    private final TcpClientConfig tcpConfig;
    private final HttpConfig protocolConfigs;
    @Nullable
    private CharSequence connectAddress;

    HttpClientConfig() {
        tcpConfig = new TcpClientConfig();
        protocolConfigs = new HttpConfig();
    }

    HttpClientConfig(final HttpClientConfig from) {
        tcpConfig = new TcpClientConfig(from.tcpConfig());
        protocolConfigs = new HttpConfig(from.protocolConfigs());
        connectAddress = from.connectAddress;
    }

    TcpClientConfig tcpConfig() {
        return tcpConfig;
    }

    HttpConfig protocolConfigs() {
        return protocolConfigs;
    }

    boolean isH2PriorKnowledge() {
        return protocolConfigs.h2Config() != null && protocolConfigs.h1Config() == null;
    }

    @Nullable
    CharSequence connectAddress() {
        return connectAddress;
    }

    void connectAddress(@Nullable final CharSequence connectAddress) {
        this.connectAddress = connectAddress;
    }

    ReadOnlyHttpClientConfig asReadOnly() {
        final ReadOnlyHttpClientConfig roConfig = new ReadOnlyHttpClientConfig(this);
        if (roConfig.tcpConfig().sslContext() == null && roConfig.h1Config() != null && roConfig.h2Config() != null) {
            throw new IllegalStateException("Cleartext HTTP/1.1 -> HTTP/2 (h2c) upgrade is not supported");
        }
        return roConfig;
    }
}
