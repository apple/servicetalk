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
import io.servicetalk.tcp.netty.internal.TcpClientConfig;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

final class HttpClientConfig {

    private final TcpClientConfig tcpClientConfig;
    private HttpHeadersFactory headersFactory = DefaultHttpHeadersFactory.INSTANCE;
    private H2ClientConfig h2ClientConfig = new H2ClientConfig();
    private boolean h2PriorKnowledge;
    private int maxInitialLineLength = 4096;
    private int maxHeaderSize = 8192;
    private int maxPipelinedRequests = 1;
    private int headersEncodedSizeEstimate = 256;
    private int trailersEncodedSizeEstimate = 256;
    @Nullable
    private CharSequence connectAddress;

    HttpClientConfig(final TcpClientConfig tcpClientConfig) {
        this.tcpClientConfig = requireNonNull(tcpClientConfig);
    }

    HttpClientConfig(final HttpClientConfig from) {
        tcpClientConfig = new TcpClientConfig(from.tcpClientConfig);
        h2ClientConfig = new H2ClientConfig(from.h2ClientConfig);
        headersFactory = from.headersFactory;
        h2PriorKnowledge = from.h2PriorKnowledge;
        maxInitialLineLength = from.maxInitialLineLength;
        maxHeaderSize = from.maxHeaderSize;
        maxPipelinedRequests = from.maxPipelinedRequests;
        headersEncodedSizeEstimate = from.headersEncodedSizeEstimate;
        trailersEncodedSizeEstimate = from.trailersEncodedSizeEstimate;
        this.connectAddress = from.connectAddress;
    }

    TcpClientConfig tcpClientConfig() {
        return tcpClientConfig;
    }

    H2ClientConfig h2ClientConfig() {
        return h2ClientConfig;
    }

    HttpHeadersFactory headersFactory() {
        return headersFactory;
    }

    void headersFactory(final HttpHeadersFactory headersFactory) {
        this.headersFactory = requireNonNull(headersFactory);
    }

    boolean isH2PriorKnowledge() {
        return h2PriorKnowledge;
    }

    void h2PriorKnowledge(boolean h2PriorKnowledge) {
        this.h2PriorKnowledge = h2PriorKnowledge;
    }

    int maxInitialLineLength() {
        return maxInitialLineLength;
    }

    void maxInitialLineLength(final int maxInitialLineLength) {
        if (maxInitialLineLength <= 0) {
            throw new IllegalArgumentException("maxInitialLineLength must be > 0");
        }
        this.maxInitialLineLength = maxInitialLineLength;
    }

    int maxHeaderSize() {
        return maxHeaderSize;
    }

    void maxHeaderSize(final int maxHeaderSize) {
        if (maxHeaderSize <= 0) {
            throw new IllegalArgumentException("maxHeaderSize must be > 0");
        }
        this.maxHeaderSize = maxHeaderSize;
    }

    int maxPipelinedRequests() {
        return maxPipelinedRequests;
    }

    void maxPipelinedRequests(final int maxPipelinedRequests) {
        if (maxPipelinedRequests <= 0) {
            throw new IllegalArgumentException("maxPipelinedRequests must be > 0");
        }
        this.maxPipelinedRequests = maxPipelinedRequests;
    }

    int headersEncodedSizeEstimate() {
        return headersEncodedSizeEstimate;
    }

    void headersEncodedSizeEstimate(final int headersEncodedSizeEstimate) {
        if (headersEncodedSizeEstimate <= 0) {
            throw new IllegalArgumentException("headersEncodedSizeEstimate must be > 0");
        }
        this.headersEncodedSizeEstimate = headersEncodedSizeEstimate;
    }

    int trailersEncodedSizeEstimate() {
        return trailersEncodedSizeEstimate;
    }

    void trailersEncodedSizeEstimate(final int trailersEncodedSizeEstimate) {
        if (trailersEncodedSizeEstimate <= 0) {
            throw new IllegalArgumentException("trailersEncodedSizeEstimate must be > 0");
        }
        this.trailersEncodedSizeEstimate = trailersEncodedSizeEstimate;
    }

    @Nullable
    public CharSequence connectAddress() {
        return connectAddress;
    }

    public void connectAddress(@Nullable final CharSequence connectAddress) {
        this.connectAddress = connectAddress;
    }

    ReadOnlyHttpClientConfig asReadOnly() {
        return new ReadOnlyHttpClientConfig(this);
    }
}
