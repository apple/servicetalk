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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpServerConfig;

final class ReadOnlyHttpServerConfig {
    private final ReadOnlyTcpServerConfig tcpConfig;
    private final ReadOnlyH2ServerConfig h2ServerConfig;
    private final HttpHeadersFactory headersFactory;
    private final long clientCloseTimeoutMs;
    private final int maxInitialLineLength;
    private final int maxHeaderSize;
    private final int headersEncodedSizeEstimate;
    private final int trailersEncodedSizeEstimate;
    private final boolean h2PriorKnowledge;

    ReadOnlyHttpServerConfig(final HttpServerConfig from) {
        tcpConfig = from.tcpConfig().asReadOnly();
        h2ServerConfig = from.h2ServerConfig().asReadOnly();
        headersFactory = from.headersFactory();
        h2PriorKnowledge = from.isH2PriorKnowledge();
        clientCloseTimeoutMs = from.clientCloseTimeoutMs();
        maxInitialLineLength = from.maxInitialLineLength();
        maxHeaderSize = from.maxHeaderSize();
        headersEncodedSizeEstimate = from.headersEncodedSizeEstimate();
        trailersEncodedSizeEstimate = from.trailersEncodedSizeEstimate();
    }

    HttpHeadersFactory headersFactory() {
        return headersFactory;
    }

    ReadOnlyH2ServerConfig h2ServerConfig() {
        return h2ServerConfig;
    }

    boolean isH2PriorKnowledge() {
        return h2PriorKnowledge;
    }

    long clientCloseTimeoutMs() {
        return clientCloseTimeoutMs;
    }

    int maxInitialLineLength() {
        return maxInitialLineLength;
    }

    int maxHeaderSize() {
        return maxHeaderSize;
    }

    int headersEncodedSizeEstimate() {
        return headersEncodedSizeEstimate;
    }

    int trailersEncodedSizeEstimate() {
        return trailersEncodedSizeEstimate;
    }

    ReadOnlyTcpServerConfig tcpConfig() {
        return tcpConfig;
    }
}
