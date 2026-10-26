/*
 * Copyright © 2018-2019, 2021, 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.ProxyConfig;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpClientConfig;
import io.servicetalk.transport.netty.internal.ExpectContinueTimeout;

import java.util.function.LongConsumer;
import javax.annotation.Nullable;

final class ReadOnlyHttpClientConfig {
    private final ReadOnlyTcpClientConfig tcpConfig;
    @Nullable
    private final H1ProtocolConfig h1Config;
    @Nullable
    private final H2ProtocolConfig h2Config;
    @Nullable
    private final ProxyConfig<String> proxyConfig;
    private final boolean allowDropTrailers;
    private final LongConsumer payloadSizeLimiter;
    private final ExpectContinueTimeout expectContinueTimeout;
    private final ExpectContinueTimeout h1ExpectContinueTimeout;

    ReadOnlyHttpClientConfig(final HttpClientConfig from, @Nullable final Object owner) {
        final HttpConfig configs = from.protocolConfigs();
        tcpConfig = from.tcpConfig().asReadOnly();
        h1Config = configs.h1Config();
        h2Config = configs.h2Config();
        proxyConfig = from.proxyConfig();
        allowDropTrailers = configs.allowDropTrailersReadFromTransport();
        payloadSizeLimiter = configs.newAggregatedPayloadSizeLimiter(owner);
        expectContinueTimeout = new ExpectContinueTimeoutLogger(from.expectContinueTimeoutNanos());
        h1ExpectContinueTimeout = from.isExpectContinueTimeoutIgnoredByH1() ?
                ExpectContinueTimeout.NONE : expectContinueTimeout;
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

    LongConsumer payloadSizeLimiter() {
        return payloadSizeLimiter;
    }

    ExpectContinueTimeout expectContinueTimeout() {
        return expectContinueTimeout;
    }

    /**
     * {@link ExpectContinueTimeout} for HTTP/1.1 connections, {@link ExpectContinueTimeout#NONE} when pipelining.
     */
    ExpectContinueTimeout h1ExpectContinueTimeout() {
        return h1ExpectContinueTimeout;
    }

    boolean isH2PriorKnowledge() {
        return h2Config != null && h1Config == null;
    }

    @Nullable
    ProxyConfig<String> proxyConfig() {
        return proxyConfig;
    }

    boolean hasProxy() {
        return proxyConfig != null;
    }
}
