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

import io.servicetalk.tcp.netty.internal.ReadOnlyTcpClientConfig;

import javax.annotation.Nullable;

final class ReadOnlyHttpClientConfig {
    private final ReadOnlyTcpClientConfig tcpConfig;
    @Nullable
    private final H1ProtocolConfig h1Config;
    @Nullable
    private final H2ProtocolConfig h2Config;
    @Nullable
    private final CharSequence connectAddress;
    private final boolean allowDropTrailers;

    ReadOnlyHttpClientConfig(final HttpClientConfig from) {
        final HttpConfig configs = from.protocolConfigs();
        tcpConfig = from.tcpConfig().asReadOnly(configs.supportedAlpnProtocols());
        h1Config = configs.h1Config();
        h2Config = configs.h2Config();
        connectAddress = from.connectAddress();
        allowDropTrailers = configs.allowDropTrailersReadFromTransport();
    }

    ReadOnlyTcpClientConfig tcpConfig() {
        return tcpConfig;
    }

    @Nullable
    H1ProtocolConfig h1Config() {
        return h1Config;
    }

    @Nullable
    H2ProtocolConfig h2Config() {
        return h2Config;
    }

    boolean allowDropTrailersReadFromTransport() {
        return allowDropTrailers;
    }

    boolean isH2PriorKnowledge() {
        return h2Config != null && h1Config == null;
    }

    @Nullable
    CharSequence connectAddress() {
        return connectAddress;
    }

    boolean hasProxy() {
        return connectAddress != null;
    }
}
