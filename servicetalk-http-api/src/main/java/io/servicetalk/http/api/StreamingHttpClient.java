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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.BlockingStreamingHttpClient.ReservedBlockingStreamingHttpConnection;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;
import io.servicetalk.http.api.StreamingHttpClientToBlockingHttpClient.ReservedStreamingHttpConnectionToBlocking;
import io.servicetalk.http.api.StreamingHttpClientToBlockingStreamingHttpClient.ReservedStreamingHttpConnectionToBlockingStreaming;
import io.servicetalk.http.api.StreamingHttpClientToHttpClient.ReservedStreamingHttpConnectionToReservedHttpConnection;

/**
 * The equivalent of {@link HttpClient} but that accepts {@link StreamingHttpRequest} and returns
 * {@link StreamingHttpResponse}.
 */
public abstract class StreamingHttpClient extends StreamingHttpRequester {
    /**
     * Create a new instance.
     *
     * @param reqRespFactory The {@link StreamingHttpRequestResponseFactory} used to
     * {@link #newRequest(HttpRequestMethod, String) create new requests} and {@link #httpResponseFactory()}.
     */
    protected StreamingHttpClient(final StreamingHttpRequestResponseFactory reqRespFactory) {
        super(reqRespFactory);
    }

    /**
     * Reserve a {@link StreamingHttpConnection} based on provided {@link HttpRequestMetaData}.
     *
     * @param metaData Allows the underlying layers to know what {@link StreamingHttpConnection}s are valid to
     * reserve for future {@link StreamingHttpRequest requests} with the same {@link HttpRequestMetaData}.
     * For example this may provide some insight into shard or other info.
     * @return a {@link Single} that provides the {@link ReservedStreamingHttpConnection} upon completion.
     */
    public Single<? extends ReservedStreamingHttpConnection> reserveConnection(HttpRequestMetaData metaData) {
        return reserveConnection(executionStrategy(), metaData);
    }

    /**
     * Reserve a {@link StreamingHttpConnection} based on provided {@link HttpRequestMetaData}.
     *
     * @param strategy {@link HttpExecutionStrategy} to use.
     * @param metaData Allows the underlying layers to know what {@link StreamingHttpConnection}s are valid to
     * reserve for future {@link StreamingHttpRequest requests} with the same {@link HttpRequestMetaData}.
     * For example this may provide some insight into shard or other info.
     * @return a {@link Single} that provides the {@link ReservedStreamingHttpConnection} upon completion.
     */
    public abstract Single<? extends ReservedStreamingHttpConnection> reserveConnection(HttpExecutionStrategy strategy,
                                                                                        HttpRequestMetaData metaData);

    /**
     * Convert this {@link StreamingHttpClient} to the {@link HttpClient} API.
     * <p>
     * This API is provided for convenience. It is recommended that
     * filters are implemented using the {@link StreamingHttpClient} asynchronous API for maximum portability.
     * @return a {@link HttpClient} representation of this {@link StreamingHttpRequester}.
     */
    public final HttpClient asClient() {
        return asClientInternal();
    }

    /**
     * Convert this {@link StreamingHttpClient} to the {@link BlockingStreamingHttpClient} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link StreamingHttpClient} asynchronous API for maximum portability.
     * @return a {@link BlockingStreamingHttpClient} representation of this {@link StreamingHttpClient}.
     */
    public final BlockingStreamingHttpClient asBlockingStreamingClient() {
        return asBlockingStreamingClientInternal();
    }

    /**
     * Convert this {@link StreamingHttpClient} to the {@link BlockingHttpClient} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link StreamingHttpClient} asynchronous API for maximum portability.
     * @return a {@link BlockingHttpClient} representation of this {@link StreamingHttpClient}.
     */
    public final BlockingHttpClient asBlockingClient() {
        return asBlockingClientInternal();
    }

    HttpClient asClientInternal() {
        return new StreamingHttpClientToHttpClient(this);
    }

    BlockingStreamingHttpClient asBlockingStreamingClientInternal() {
        return new StreamingHttpClientToBlockingStreamingHttpClient(this);
    }

    BlockingHttpClient asBlockingClientInternal() {
        return new StreamingHttpClientToBlockingHttpClient(this);
    }

    /**
     * A special type of {@link StreamingHttpConnection} for the exclusive use of the caller of
     * {@link #reserveConnection(HttpRequestMetaData)} and
     * {@link #reserveConnection(HttpExecutionStrategy, HttpRequestMetaData)}.
     */
    public abstract static class ReservedStreamingHttpConnection extends StreamingHttpConnection {
        /**
         * Create a new instance.
         *
         * @param reqRespFactory The {@link StreamingHttpRequestResponseFactory} used to
         * {@link #newRequest(HttpRequestMethod, String) create new requests} and {@link #httpResponseFactory()}.
         */
        protected ReservedStreamingHttpConnection(final StreamingHttpRequestResponseFactory reqRespFactory) {
            super(reqRespFactory);
        }

        /**
         * Releases this reserved {@link StreamingHttpConnection} to be used for subsequent requests.
         * This method must be idempotent, i.e. calling multiple times must not have side-effects.
         *
         * @return the {@code Completable} that is notified on releaseAsync.
         */
        public abstract Completable releaseAsync();

        /**
         * Convert this {@link ReservedStreamingHttpConnection} to the {@link ReservedHttpConnection} API.
         * <p>
         * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
         * filters are implemented using the {@link ReservedStreamingHttpConnection} asynchronous API for maximum
         * portability.
         * @return a {@link ReservedHttpConnection} representation of this
         * {@link ReservedStreamingHttpConnection}.
         */
        public final ReservedHttpConnection asReservedConnection() {
            return asConnectionInternal();
        }

        /**
         * Convert this {@link ReservedStreamingHttpConnection} to the {@link BlockingStreamingHttpClient} API.
         * <p>
         * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
         * filters are implemented using the {@link ReservedStreamingHttpConnection} asynchronous API for maximum
         * portability.
         * @return a {@link BlockingStreamingHttpClient} representation of this {@link ReservedStreamingHttpConnection}.
         */
        public final ReservedBlockingStreamingHttpConnection asReservedBlockingStreamingConnection() {
            return asBlockingStreamingConnectionInternal();
        }

        /**
         * Convert this {@link ReservedStreamingHttpConnection} to the {@link ReservedBlockingHttpConnection} API.
         * <p>
         * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
         * filters are implemented using the {@link ReservedStreamingHttpConnection} asynchronous API for maximum
         * portability.
         * @return a {@link ReservedBlockingHttpConnection} representation of this
         * {@link ReservedStreamingHttpConnection}.
         */
        public final ReservedBlockingHttpConnection asReservedBlockingConnection() {
            return asBlockingConnectionInternal();
        }

        @Override
        ReservedHttpConnection asConnectionInternal() {
            return new ReservedStreamingHttpConnectionToReservedHttpConnection(this);
        }

        @Override
        ReservedBlockingStreamingHttpConnection asBlockingStreamingConnectionInternal() {
            return new ReservedStreamingHttpConnectionToBlockingStreaming(this);
        }

        @Override
        ReservedBlockingHttpConnection asBlockingConnectionInternal() {
            return new ReservedStreamingHttpConnectionToBlocking(this);
        }
    }
}
