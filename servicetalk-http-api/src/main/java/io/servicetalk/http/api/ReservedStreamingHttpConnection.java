/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.http.api.StreamingHttpClientToBlockingHttpClient.ReservedStreamingHttpConnectionToReservedBlockingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClientToBlockingStreamingHttpClient.ReservedStreamingHttpConnectionToBlockingStreaming;
import io.servicetalk.http.api.StreamingHttpClientToHttpClient.ReservedStreamingHttpConnectionToReservedHttpConnection;

/**
 * A special type of {@link StreamingHttpConnection} for the exclusive use of the caller of
 * {@link StreamingHttpClient#reserveConnection(HttpRequestMetaData)} and
 * {@link StreamingHttpClient#reserveConnection(HttpExecutionStrategy, HttpRequestMetaData)}.
 */
public interface ReservedStreamingHttpConnection extends StreamingHttpConnection,
                                                         FilterableReservedStreamingHttpConnection {
    @Override
    default ReservedHttpConnection asConnection() {
        return new ReservedStreamingHttpConnectionToReservedHttpConnection(this);
    }

    @Override
    default ReservedBlockingStreamingHttpConnection asBlockingStreamingConnection() {
        return new ReservedStreamingHttpConnectionToBlockingStreaming(this);
    }

    @Override
    default ReservedBlockingHttpConnection asBlockingConnection() {
        return new ReservedStreamingHttpConnectionToReservedBlockingHttpConnection(this);
    }
}
