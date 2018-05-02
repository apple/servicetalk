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
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpClientConfig;

final class ReadOnlyHttpClientConfig {

    private final ReadOnlyTcpClientConfig tcpClientConfig;
    private final int maxInitialLineLength;
    private final int maxHeaderSize;
    private final int maxChunkSize;
    private final int maxPipelinedRequests;
    private final int headersEncodedSizeEstimate;
    private final int trailersEncodedSizeEstimate;
    private final HttpHeadersFactory headersFactory;

    ReadOnlyHttpClientConfig(final HttpClientConfig from) {
        tcpClientConfig = from.getTcpClientConfig().asReadOnly();
        maxInitialLineLength = from.getMaxInitialLineLength();
        maxHeaderSize = from.getMaxHeaderSize();
        maxChunkSize = from.getMaxChunkSize();
        headersEncodedSizeEstimate = from.getHeadersEncodedSizeEstimate();
        trailersEncodedSizeEstimate = from.getTrailersEncodedSizeEstimate();
        maxPipelinedRequests = from.getMaxPipelinedRequests();
        headersFactory = from.getHeadersFactory();
    }

    ReadOnlyTcpClientConfig getTcpClientConfig() {
        return tcpClientConfig;
    }

    public int getMaxPipelinedRequests() {
        return maxPipelinedRequests;
    }

    int getMaxInitialLineLength() {
        return maxInitialLineLength;
    }

    int getMaxHeaderSize() {
        return maxHeaderSize;
    }

    int getMaxChunkSize() {
        return maxChunkSize;
    }

    int getHeadersEncodedSizeEstimate() {
        return headersEncodedSizeEstimate;
    }

    int getTrailersEncodedSizeEstimate() {
        return trailersEncodedSizeEstimate;
    }

    public HttpHeadersFactory getHeadersFactory() {
        return headersFactory;
    }
}
