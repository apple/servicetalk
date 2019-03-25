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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingStreamingHttpClient.ReservedBlockingStreamingHttpConnection;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;
import io.servicetalk.http.api.StreamingHttpClientToBlockingHttpClient.ReservedStreamingHttpConnectionToReservedBlockingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClientToBlockingStreamingHttpClient.ReservedStreamingHttpConnectionToBlockingStreaming;
import io.servicetalk.http.api.StreamingHttpClientToHttpClient.ReservedStreamingHttpConnectionToReservedHttpConnection;

import static io.servicetalk.http.api.BlockingHttpClient.ReservedBlockingHttpConnection;
import static io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;

/**
 * The equivalent of {@link HttpClient} but that accepts {@link StreamingHttpRequest} and returns
 * {@link StreamingHttpResponse}.
 */
public interface StreamingHttpClient extends FilterableStreamingHttpClient<ReservedStreamingHttpConnection> {
    /**
     * Send a {@code request}.
     *
     * @param request the request to send.
     * @return The response.
     */
    Single<StreamingHttpResponse> request(StreamingHttpRequest request);

    /**
     * Reserve a {@link StreamingHttpConnection} based on provided {@link HttpRequestMetaData}.
     *
     * @param metaData Allows the underlying layers to know what {@link StreamingHttpConnection}s are valid to
     * reserve for future {@link StreamingHttpRequest requests} with the same {@link HttpRequestMetaData}.
     * For example this may provide some insight into shard or other info.
     * @return a {@link Single} that provides the {@link ReservedStreamingHttpConnection} upon completion.
     */
    Single<ReservedStreamingHttpConnection> reserveConnection(HttpRequestMetaData metaData);

    /**
     * Convert this {@link StreamingHttpClient} to the {@link HttpClient} API.
     * <p>
     * This API is provided for convenience. It is recommended that
     * filters are implemented using the {@link StreamingHttpClient} asynchronous API for maximum portability.
     * @return a {@link HttpClient} representation of this {@link StreamingHttpRequester}.
     */
    default HttpClient asClient() {
        return new StreamingHttpClientToHttpClient(this);
    }

    /**
     * Convert this {@link StreamingHttpClient} to the {@link BlockingStreamingHttpClient} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link StreamingHttpClient} asynchronous API for maximum portability.
     * @return a {@link BlockingStreamingHttpClient} representation of this {@link StreamingHttpClient}.
     */
    default BlockingStreamingHttpClient asBlockingStreamingClient() {
        return new StreamingHttpClientToBlockingStreamingHttpClient(this);
    }

    /**
     * Convert this {@link StreamingHttpClient} to the {@link BlockingHttpClient} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link StreamingHttpClient} asynchronous API for maximum portability.
     * @return a {@link BlockingHttpClient} representation of this {@link StreamingHttpClient}.
     */
    default BlockingHttpClient asBlockingClient() {
        return new StreamingHttpClientToBlockingHttpClient(this);
    }

    /**
     * A special type of {@link StreamingHttpConnection} for the exclusive use of the caller of
     * {@link #reserveConnection(HttpRequestMetaData)} and
     * {@link #reserveConnection(HttpExecutionStrategy, HttpRequestMetaData)}.
     */
    interface ReservedStreamingHttpConnection extends StreamingHttpConnection,
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
}
