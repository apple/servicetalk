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

import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.tcp.netty.internal.TcpServerConfig;

import static java.util.Objects.requireNonNull;

final class HttpServerConfig {
    private final TcpServerConfig tcpConfig;
    private HttpHeadersFactory headersFactory = DefaultHttpHeadersFactory.INSTANCE;
    private long clientCloseTimeoutMs = 500;
    private int maxInitialLineLength = 4096;
    private int maxHeaderSize = 8192;
    private int headersEncodedSizeEstimate = 256;
    private int trailersEncodedSizeEstimate = 256;

    HttpServerConfig() {
        tcpConfig = new TcpServerConfig(false);
    }

    HttpHeadersFactory getHeadersFactory() {
        return headersFactory;
    }

    void setHeadersFactory(final HttpHeadersFactory headersFactory) {
        this.headersFactory = requireNonNull(headersFactory);
    }

    long getClientCloseTimeoutMs() {
        return clientCloseTimeoutMs;
    }

    void setClientCloseTimeout(final long clientCloseTimeoutMs) {
        if (clientCloseTimeoutMs < 0) {
            throw new IllegalArgumentException("clientCloseTimeoutMs must be >= 0");
        }
        this.clientCloseTimeoutMs = clientCloseTimeoutMs;
    }

    int getMaxInitialLineLength() {
        return maxInitialLineLength;
    }

    void setMaxInitialLineLength(final int maxInitialLineLength) {
        if (maxInitialLineLength <= 0) {
            throw new IllegalArgumentException("maxInitialLineLength must be > 0");
        }
        this.maxInitialLineLength = maxInitialLineLength;
    }

    int getHeadersEncodedSizeEstimate() {
        return headersEncodedSizeEstimate;
    }

    void setHeadersEncodedSizeEstimate(final int headersEncodedSizeEstimate) {
        this.headersEncodedSizeEstimate = headersEncodedSizeEstimate;
    }

    int getTrailersEncodedSizeEstimate() {
        return trailersEncodedSizeEstimate;
    }

    void setTrailersEncodedSizeEstimate(final int trailersEncodedSizeEstimate) {
        this.trailersEncodedSizeEstimate = trailersEncodedSizeEstimate;
    }

    int getMaxHeaderSize() {
        return maxHeaderSize;
    }

    void setMaxHeaderSize(final int maxHeaderSize) {
        if (maxHeaderSize <= 0) {
            throw new IllegalArgumentException("maxHeaderSize must be > 0");
        }
        this.maxHeaderSize = maxHeaderSize;
    }

    TcpServerConfig getTcpConfig() {
        return tcpConfig;
    }

    ReadOnlyHttpServerConfig asReadOnly() {
        return new ReadOnlyHttpServerConfig(this);
    }
}
