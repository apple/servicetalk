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

import io.servicetalk.tcp.netty.internal.TcpServerConfig;

final class HttpServerConfig {

    private final TcpServerConfig tcpConfig;
    private final HttpProtocolConfig protocolConfigs;

    HttpServerConfig() {
        tcpConfig = new TcpServerConfig();
        protocolConfigs = new HttpProtocolConfig();
    }

    TcpServerConfig tcpConfig() {
        return tcpConfig;
    }

    HttpProtocolConfig protocolConfigs() {
        return protocolConfigs;
    }

    ReadOnlyHttpServerConfig asReadOnly() {
        final ReadOnlyHttpServerConfig roConfig = new ReadOnlyHttpServerConfig(this);
        if (roConfig.tcpConfig().sslContext() == null && roConfig.h1Config() != null && roConfig.h2Config() != null) {
            throw new IllegalStateException("Cleartext HTTP/1.1 -> HTTP/2 (h2c) upgrade is not supported");
        }
        return roConfig;
    }
}
