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
    private final HttpHeadersFactory headersFactory;
    private final int maxInitialLineLength;
    private final int maxHeaderSize;
    private final int maxPipelinedRequests;
    private final int headersEncodedSizeEstimate;
    private final int trailersEncodedSizeEstimate;

    ReadOnlyHttpClientConfig(final HttpClientConfig from) {
        tcpClientConfig = from.tcpClientConfig().asReadOnly();
        headersFactory = from.headersFactory();
        maxInitialLineLength = from.maxInitialLineLength();
        maxHeaderSize = from.maxHeaderSize();
        maxPipelinedRequests = from.maxPipelinedRequests();
        headersEncodedSizeEstimate = from.headersEncodedSizeEstimate();
        trailersEncodedSizeEstimate = from.trailersEncodedSizeEstimate();
    }

    ReadOnlyTcpClientConfig tcpClientConfig() {
        return tcpClientConfig;
    }

    HttpHeadersFactory headersFactory() {
        return headersFactory;
    }

    int maxInitialLineLength() {
        return maxInitialLineLength;
    }

    int maxHeaderSize() {
        return maxHeaderSize;
    }

    int maxPipelinedRequests() {
        return maxPipelinedRequests;
    }

    int headersEncodedSizeEstimate() {
        return headersEncodedSizeEstimate;
    }

    int trailersEncodedSizeEstimate() {
        return trailersEncodedSizeEstimate;
    }
}
