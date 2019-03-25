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

import io.servicetalk.http.api.BlockingHttpClient.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;

import static io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;

/**
 * The equivalent of {@link StreamingHttpClient} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
public interface BlockingStreamingHttpClient extends BlockingStreamingHttpRequester {
    /**
     * Send a {@code request}.
     *
     * @param request the request to send.
     * @return The response.
     * @throws Exception if an exception occurs during the request processing.
     */
    BlockingStreamingHttpResponse request(BlockingStreamingHttpRequest request) throws Exception;

    /**
     * Reserve a {@link BlockingStreamingHttpConnection} based on provided {@link HttpRequestMetaData}.
     *
     * @param metaData Allows the underlying layers to know what {@link BlockingStreamingHttpConnection}s are valid to
     * reserve for future {@link BlockingStreamingHttpRequest requests} with the same {@link HttpRequestMetaData}.
     * For example this may provide some insight into shard or other info.
     * @return a {@link ReservedBlockingStreamingHttpConnection}.
     * @throws Exception if a exception occurs during the reservation process.
     */
    ReservedBlockingStreamingHttpConnection reserveConnection(HttpRequestMetaData metaData) throws Exception;

    /**
     * Reserve a {@link BlockingStreamingHttpConnection} based on provided {@link HttpRequestMetaData}.
     *
     * @param strategy {@link HttpExecutionStrategy} to use.
     * @param metaData Allows the underlying layers to know what {@link BlockingStreamingHttpConnection}s are valid to
     * reserve for future {@link BlockingStreamingHttpRequest requests} with the same {@link HttpRequestMetaData}.
     * For example this may provide some insight into shard or other info.
     * @return a {@link ReservedBlockingStreamingHttpConnection}.
     * @throws Exception if a exception occurs during the reservation process.
     */
    ReservedBlockingStreamingHttpConnection reserveConnection(
            HttpExecutionStrategy strategy, HttpRequestMetaData metaData) throws Exception;

    /**
     * Convert this {@link BlockingStreamingHttpClient} to the {@link StreamingHttpClient} API.
     * <p>
     * Note that the resulting {@link StreamingHttpClient} may still be subject to any blocking, in memory aggregation,
     * and other behavior as this {@link BlockingStreamingHttpClient}.
     *
     * @return a {@link StreamingHttpClient} representation of this {@link BlockingStreamingHttpClient}.
     */
    StreamingHttpClient asStreamingClient();

    /**
     * Convert this {@link BlockingStreamingHttpClient} to the {@link HttpClient} API.
     * <p>
     * Note that the resulting {@link HttpClient} may still be subject to any blocking, in memory aggregation,
     * and other behavior as this {@link BlockingStreamingHttpClient}.
     *
     * @return a {@link HttpClient} representation of this {@link BlockingStreamingHttpClient}.
     */
    default HttpClient asClient() {
        return asStreamingClient().asClient();
    }

    /**
     * Convert this {@link BlockingStreamingHttpClient} to the {@link BlockingHttpClient} API.
     * <p>
     * Note that the resulting {@link BlockingHttpClient} may still be subject to in memory
     * aggregation and other behavior as this {@link BlockingStreamingHttpClient}.
     *
     * @return a {@link BlockingHttpClient} representation of this {@link BlockingStreamingHttpClient}.
     */
    default BlockingHttpClient asBlockingClient() {
        return asStreamingClient().asBlockingClient();
    }

    /**
     * A special type of {@link BlockingStreamingHttpConnection} for the exclusive use of the caller of
     * {@link #reserveConnection(HttpRequestMetaData)} and
     * {@link #reserveConnection(HttpExecutionStrategy, HttpRequestMetaData)}.
     */
    interface ReservedBlockingStreamingHttpConnection extends BlockingStreamingHttpConnection {
        /**
         * Releases this reserved {@link BlockingStreamingHttpConnection} to be used for subsequent requests.
         * This method must be idempotent, i.e. calling multiple times must not have side-effects.
         *
         * @throws Exception if any exception occurs during releasing.
         */
        void release() throws Exception;

        @Override
        ReservedStreamingHttpConnection asStreamingConnection();

        @Override
        default ReservedHttpConnection asConnection() {
            return asStreamingConnection().asConnection();
        }

        @Override
        default ReservedBlockingHttpConnection asBlockingConnection() {
            return asStreamingConnection().asBlockingConnection();
        }
    }
}
