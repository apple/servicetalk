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
import io.servicetalk.http.api.BlockingStreamingHttpClientToStreamingHttpClient.BlockingToReservedStreamingHttpConnection;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;

/**
 * The equivalent of {@link StreamingHttpClient} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
public abstract class BlockingStreamingHttpClient extends BlockingStreamingHttpRequester {

    /**
     * Create a new instance.
     *
     * @param reqRespFactory The {@link BlockingStreamingHttpRequestResponseFactory} used to
     * {@link #newRequest(HttpRequestMethod, String) create new requests} and {@link #httpResponseFactory()}.
     * @param strategy Default {@link HttpExecutionStrategy} to use.
     */
    BlockingStreamingHttpClient(final BlockingStreamingHttpRequestResponseFactory reqRespFactory,
                                final HttpExecutionStrategy strategy) {
        super(reqRespFactory, strategy);
    }

    /**
     * Reserve a {@link BlockingStreamingHttpConnection} based on provided {@link HttpRequestMetaData}.
     *
     * @param metaData Allows the underlying layers to know what {@link BlockingStreamingHttpConnection}s are valid to
     * reserve for future {@link BlockingStreamingHttpRequest requests} with the same {@link HttpRequestMetaData}.
     * For example this may provide some insight into shard or other info.
     * @return a {@link ReservedBlockingStreamingHttpConnection}.
     * @throws Exception if a exception occurs during the reservation process.
     */
    public ReservedBlockingStreamingHttpConnection reserveConnection(HttpRequestMetaData metaData) throws Exception {
        return reserveConnection(executionStrategy(), metaData);
    }

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
    public abstract ReservedBlockingStreamingHttpConnection reserveConnection(
            HttpExecutionStrategy strategy, HttpRequestMetaData metaData) throws Exception;

    /**
     * Convert this {@link BlockingStreamingHttpClient} to the {@link StreamingHttpClient} API.
     * <p>
     * Note that the resulting {@link StreamingHttpClient} may still be subject to any blocking, in memory aggregation,
     * and other behavior as this {@link BlockingStreamingHttpClient}.
     *
     * @return a {@link StreamingHttpClient} representation of this {@link BlockingStreamingHttpClient}.
     */
    public final StreamingHttpClient asStreamingClient() {
        return asStreamingClientInternal();
    }

    /**
     * Convert this {@link BlockingStreamingHttpClient} to the {@link HttpClient} API.
     * <p>
     * Note that the resulting {@link HttpClient} may still be subject to any blocking, in memory aggregation,
     * and other behavior as this {@link BlockingStreamingHttpClient}.
     *
     * @return a {@link HttpClient} representation of this {@link BlockingStreamingHttpClient}.
     */
    public final HttpClient asClient() {
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
    public final BlockingHttpClient asBlockingClient() {
        return asStreamingClient().asBlockingClient();
    }

    StreamingHttpClient asStreamingClientInternal() {
        return BlockingStreamingHttpClientToStreamingHttpClient.transform(this);
    }

    /**
     * A special type of {@link BlockingStreamingHttpConnection} for the exclusive use of the caller of
     * {@link #reserveConnection(HttpRequestMetaData)} and
     * {@link #reserveConnection(HttpExecutionStrategy, HttpRequestMetaData)}.
     */
    public abstract static class ReservedBlockingStreamingHttpConnection extends BlockingStreamingHttpConnection {

        /**
         * Create a new instance.
         *
         * @param reqRespFactory The {@link BlockingStreamingHttpRequestResponseFactory} used to
         * {@link #newRequest(HttpRequestMethod, String) create new requests} and {@link #httpResponseFactory()}.
         * @param strategy Default {@link HttpExecutionStrategy} to use.
         */
        ReservedBlockingStreamingHttpConnection(final BlockingStreamingHttpRequestResponseFactory reqRespFactory,
                                                final HttpExecutionStrategy strategy) {
            super(reqRespFactory, strategy);
        }

        /**
         * Releases this reserved {@link BlockingStreamingHttpConnection} to be used for subsequent requests.
         * This method must be idempotent, i.e. calling multiple times must not have side-effects.
         *
         * @throws Exception if any exception occurs during releasing.
         */
        public abstract void release() throws Exception;

        /**
         * Convert this {@link ReservedBlockingStreamingHttpConnection} to the {@link ReservedStreamingHttpConnection}
         * API.
         * <p>
         * Note that the resulting {@link ReservedStreamingHttpConnection} may still be subject to any blocking, in
         * memory aggregation, and other behavior as this {@link ReservedBlockingStreamingHttpConnection}.
         *
         * @return a {@link ReservedStreamingHttpConnection} representation of this
         * {@link ReservedBlockingStreamingHttpConnection}.
         */
        public final ReservedStreamingHttpConnection asReservedStreamingConnection() {
            return asStreamingConnectionInternal();
        }

        /**
         * Convert this {@link ReservedBlockingStreamingHttpConnection} to the {@link ReservedHttpConnection} API.
         * <p>
         * Note that the resulting {@link ReservedHttpConnection} may still be subject to any blocking, in
         * memory aggregation, and other behavior as this {@link ReservedBlockingStreamingHttpConnection}.
         *
         * @return a {@link ReservedHttpConnection} representation of this
         * {@link ReservedBlockingStreamingHttpConnection}.
         */
        public final ReservedHttpConnection asReservedConnection() {
            return asReservedStreamingConnection().asReservedConnection();
        }

        /**
         * Convert this {@link ReservedBlockingStreamingHttpConnection} to the
         * {@link ReservedBlockingHttpConnection} API.
         * <p>
         * Note that the resulting {@link ReservedBlockingHttpConnection} may still be subject to in memory
         * aggregation and other behavior as this {@link ReservedBlockingStreamingHttpConnection}.
         *
         * @return a {@link ReservedBlockingHttpConnection} representation of this
         * {@link ReservedBlockingStreamingHttpConnection}.
         */
        public final ReservedBlockingHttpConnection asReservedBlockingConnection() {
            return asReservedStreamingConnection().asReservedBlockingConnection();
        }

        @Override
        ReservedStreamingHttpConnection asStreamingConnectionInternal() {
            return BlockingToReservedStreamingHttpConnection.transform(this);
        }
    }
}
