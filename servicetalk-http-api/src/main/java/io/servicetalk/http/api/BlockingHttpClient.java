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

import io.servicetalk.http.api.BlockingHttpClientToHttpClient.ReservedBlockingHttpConnectionToReservedHttpConnection;
import io.servicetalk.http.api.BlockingHttpClientToStreamingHttpClient.BlockingReservedStreamingHttpConnectionToReserved;
import io.servicetalk.http.api.BlockingStreamingHttpClient.ReservedBlockingStreamingHttpConnection;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;
import io.servicetalk.http.api.HttpClient.UpgradableHttpResponse;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;

/**
 * The equivalent of {@link HttpClient} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
public abstract class BlockingHttpClient extends BlockingHttpRequester {
    /**
     * Create a new instance.
     *
     * @param reqRespFactory The {@link HttpRequestResponseFactory} used to
     * {@link #newRequest(HttpRequestMethod, String) create new requests} and {@link #httpResponseFactory()}.
     */
    protected BlockingHttpClient(final HttpRequestResponseFactory reqRespFactory) {
        super(reqRespFactory);
    }

    /**
     * Reserve a {@link ReservedBlockingHttpConnection} for handling the provided
     * {@link HttpRequest} but <b>does not execute it</b>!
     *
     * @param request Allows the underlying layers to know what {@link BlockingHttpConnection}s are valid to
     * reserve. For example this may provide some insight into shard or other info.
     * @return a {@link ReservedBlockingHttpConnection}.
     * @throws Exception if a exception occurs during the reservation process.
     * @see StreamingHttpClient#reserveConnection(StreamingHttpRequest)
     */
    public ReservedBlockingHttpConnection reserveConnection(HttpRequest request) throws Exception {
        return reserveConnection(executionStrategy(), request);
    }

    /**
     * Reserve a {@link ReservedBlockingHttpConnection} for handling the provided
     * {@link HttpRequest} but <b>does not execute it</b>!
     *
     * @param strategy {@link HttpExecutionStrategy} to use.
     * @param request Allows the underlying layers to know what {@link BlockingHttpConnection}s are valid to
     * reserve. For example this may provide some insight into shard or other info.
     * @return a {@link ReservedBlockingHttpConnection}.
     * @throws Exception if a exception occurs during the reservation process.
     * @see StreamingHttpClient#reserveConnection(HttpExecutionStrategy, StreamingHttpRequest)
     */
    public abstract ReservedBlockingHttpConnection reserveConnection(HttpExecutionStrategy strategy,
                                                                     HttpRequest request) throws Exception;

    /**
     * Attempt a <a href="https://tools.ietf.org/html/rfc7230.html#section-6.7">protocol upgrade</a>.
     * As part of the <a href="https://tools.ietf.org/html/rfc7230.html#section-6.7">protocol upgrade</a> process there
     * cannot be any pipelined requests pending or any pipeline requests issued during the upgrade process. That means
     * the {@link BlockingHttpConnection} associated with the {@link UpgradableHttpResponse} will be
     * reserved for exclusive use. The code responsible for determining the result of the upgrade attempt is responsible
     * for calling {@link UpgradableHttpResponse#httpConnection(boolean)}.
     *
     * @param request the request which initiates the upgrade.
     * @return An {@link UpgradableHttpResponse} for the upgrade attempt and also contains the
     * {@link BlockingHttpConnection} used for the upgrade.
     * @throws Exception if a exception occurs during the upgrade process.
     * @see StreamingHttpClient#upgradeConnection(StreamingHttpRequest)
     */
    public abstract UpgradableHttpResponse upgradeConnection(HttpRequest request) throws Exception;

    /**
     * Convert this {@link BlockingHttpClient} to the {@link StreamingHttpClient} API.
     *
     * @return a {@link StreamingHttpClient} representation of this {@link BlockingHttpClient}.
     */
    public final StreamingHttpClient asStreamingClient() {
        return asStreamingClientInternal();
    }

    /**
     * Convert this {@link BlockingHttpClient} to the {@link HttpClient} API.
     *
     * @return a {@link HttpClient} representation of this {@link BlockingHttpClient}.
     */
    public final HttpClient asClient() {
        return asClientInternal();
    }

    /**
     * Convert this {@link BlockingHttpClient} to the {@link BlockingStreamingHttpClient} API.
     *
     * @return a {@link BlockingStreamingHttpClient} representation of this {@link BlockingHttpClient}.
     */
    public final BlockingStreamingHttpClient asBlockingStreamingClient() {
        return asStreamingClient().asBlockingStreamingClient();
    }

    StreamingHttpClient asStreamingClientInternal() {
        return new BlockingHttpClientToStreamingHttpClient(this);
    }

    HttpClient asClientInternal() {
        return new BlockingHttpClientToHttpClient(this);
    }

    /**
     * A special type of {@link BlockingHttpConnection} for the exclusive use of the caller of
     * {@link #reserveConnection(HttpRequest)}.
     * @see StreamingHttpClient.ReservedStreamingHttpConnection
     */
    public abstract static class ReservedBlockingHttpConnection extends BlockingHttpConnection {
        /**
         * Create a new instance.
         *
         * @param reqRespFactory The {@link HttpRequestResponseFactory} used to
         * {@link #newRequest(HttpRequestMethod, String) create new requests} and {@link #httpResponseFactory()}.
         */
        protected ReservedBlockingHttpConnection(final HttpRequestResponseFactory reqRespFactory) {
            super(reqRespFactory);
        }

        /**
         * Releases this reserved {@link BlockingHttpConnection} to be used for subsequent requests.
         * This method must be idempotent, i.e. calling multiple times must not have side-effects.
         *
         * @throws Exception if any exception occurs during releasing.
         */
        public abstract void release() throws Exception;

        /**
         * Convert this {@link ReservedBlockingHttpConnection} to the {@link ReservedStreamingHttpConnection} API.
         * <p>
         * Note that the resulting {@link ReservedStreamingHttpConnection} may still be subject to any blocking, in
         * memory aggregation, and other behavior as this {@link ReservedBlockingHttpConnection}.
         *
         * @return a {@link StreamingHttpClient.ReservedStreamingHttpConnection} representation of this
         * {@link ReservedBlockingHttpConnection}.
         */
        public final ReservedStreamingHttpConnection asReservedStreamingConnection() {
            return asStreamingConnectionInternal();
        }

        /**
         * Convert this {@link ReservedBlockingHttpConnection} to the {@link ReservedHttpConnection}
         * API.
         * <p>
         * Note that the resulting {@link ReservedHttpConnection} may still be subject to any blocking, in
         * memory aggregation, and other behavior as this {@link ReservedBlockingHttpConnection}.
         *
         * @return a {@link ReservedHttpConnection} representation of this
         * {@link ReservedBlockingHttpConnection}.
         */
        public final ReservedHttpConnection asReservedConnection() {
            return asConnectionInternal();
        }

        /**
         * Convert this {@link ReservedBlockingHttpConnection} to the {@link ReservedBlockingStreamingHttpConnection}
         * API.
         * <p>
         * Note that the resulting {@link ReservedBlockingStreamingHttpConnection} may still be subject to in
         * memory aggregation and other behavior as this {@link ReservedBlockingHttpConnection}.
         *
         * @return a {@link ReservedBlockingStreamingHttpConnection} representation of this
         * {@link ReservedBlockingHttpConnection}.
         */
        public final ReservedBlockingStreamingHttpConnection asReservedBlockingStreamingConnection() {
            return asReservedStreamingConnection().asReservedBlockingStreamingConnection();
        }

        @Override
        ReservedStreamingHttpConnection asStreamingConnectionInternal() {
            return new BlockingReservedStreamingHttpConnectionToReserved(this);
        }

        @Override
        ReservedHttpConnection asConnectionInternal() {
            return new ReservedBlockingHttpConnectionToReservedHttpConnection(this);
        }
    }
}
