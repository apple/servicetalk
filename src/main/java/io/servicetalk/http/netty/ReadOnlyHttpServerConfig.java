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
    private final HttpHeadersFactory headersFactory;
    private final long clientCloseTimeoutMs;
    private final int maxInitialLineLength;
    private final int maxHeaderSize;
    private final int headersEncodedSizeEstimate;
    private final int trailersEncodedSizeEstimate;

    ReadOnlyHttpServerConfig(final HttpServerConfig from) {
        tcpConfig = from.getTcpConfig().asReadOnly();
        headersFactory = from.getHeadersFactory();
        clientCloseTimeoutMs = from.getClientCloseTimeoutMs();
        maxInitialLineLength = from.getMaxInitialLineLength();
        maxHeaderSize = from.getMaxHeaderSize();
        headersEncodedSizeEstimate = from.getHeadersEncodedSizeEstimate();
        trailersEncodedSizeEstimate = from.getTrailersEncodedSizeEstimate();
    }

    HttpHeadersFactory getHeadersFactory() {
        return headersFactory;
    }

    long getClientCloseTimeoutMs() {
        return clientCloseTimeoutMs;
    }

    int getMaxInitialLineLength() {
        return maxInitialLineLength;
    }

    int getMaxHeaderSize() {
        return maxHeaderSize;
    }

    int getHeadersEncodedSizeEstimate() {
        return headersEncodedSizeEstimate;
    }

    int getTrailersEncodedSizeEstimate() {
        return trailersEncodedSizeEstimate;
    }

    ReadOnlyTcpServerConfig getTcpConfig() {
        return tcpConfig;
    }
}
