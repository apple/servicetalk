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
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static io.servicetalk.http.api.RequestResponseFactories.toBlockingStreaming;

/**
 * The equivalent of {@link StreamingHttpClient} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
public final class BlockingStreamingHttpClient implements BlockingStreamingHttpRequester {

    private final StreamingHttpClient client;
    private final HttpExecutionStrategy strategy;
    private final BlockingStreamingHttpRequestResponseFactory reqRespFactory;

    /**
     * Create a new instance.
     *
     * @param client {@link StreamingHttpClient} to convert from.
     * @param strategy Default {@link HttpExecutionStrategy} to use.
     */
    BlockingStreamingHttpClient(final StreamingHttpClient client,
                                final HttpExecutionStrategy strategy) {
        reqRespFactory = toBlockingStreaming(client.filterChain.reqRespFactory);
        this.client = client;
        this.strategy = strategy;
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
        return reserveConnection(strategy, metaData);
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
    public ReservedBlockingStreamingHttpConnection reserveConnection(
            HttpExecutionStrategy strategy, HttpRequestMetaData metaData) throws Exception {
        // It is assumed that users will always apply timeouts at the StreamingHttpService layer (e.g. via filter).
        // So we don't apply any explicit timeout here and just wait forever.
        return new ReservedBlockingStreamingHttpConnection(
                blockingInvocation(client.reserveConnection(strategy, metaData)), this.strategy);
    }

    @Override
    public BlockingStreamingHttpResponse request(final BlockingStreamingHttpRequest request) throws Exception {
        return request(strategy, request);
    }

    @Override
    public BlockingStreamingHttpResponse request(final HttpExecutionStrategy strategy,
                                                 final BlockingStreamingHttpRequest request) throws Exception {
        return blockingInvocation(client.request(strategy, request.toStreamingRequest())).toBlockingStreamingResponse();
    }

    @Override
    public ExecutionContext executionContext() {
        return client.executionContext();
    }

    /**
     * Convert this {@link BlockingStreamingHttpClient} to the {@link StreamingHttpClient} API.
     * <p>
     * Note that the resulting {@link StreamingHttpClient} may still be subject to any blocking, in memory aggregation,
     * and other behavior as this {@link BlockingStreamingHttpClient}.
     *
     * @return a {@link StreamingHttpClient} representation of this {@link BlockingStreamingHttpClient}.
     */
    public StreamingHttpClient asStreamingClient() {
        return client;
    }

    /**
     * Convert this {@link BlockingStreamingHttpClient} to the {@link HttpClient} API.
     * <p>
     * Note that the resulting {@link HttpClient} may still be subject to any blocking, in memory aggregation,
     * and other behavior as this {@link BlockingStreamingHttpClient}.
     *
     * @return a {@link HttpClient} representation of this {@link BlockingStreamingHttpClient}.
     */
    public HttpClient asClient() {
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
    public BlockingHttpClient asBlockingClient() {
        return asStreamingClient().asBlockingClient();
    }

    @Override
    public void close() throws Exception {
        blockingInvocation(client.closeAsync());
    }

    @Override
    public BlockingStreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return reqRespFactory.newRequest(method, requestTarget);
    }

    @Override
    public BlockingStreamingHttpResponseFactory httpResponseFactory() {
        return reqRespFactory;
    }

    /**
     * A special type of {@link BlockingStreamingHttpConnection} for the exclusive use of the caller of
     * {@link #reserveConnection(HttpRequestMetaData)} and
     * {@link #reserveConnection(HttpExecutionStrategy, HttpRequestMetaData)}.
     */
    public static final class ReservedBlockingStreamingHttpConnection extends BlockingStreamingHttpConnection {

        private final ReservedStreamingHttpConnection connection;

        /**
         * Create a new instance.
         *
         * @param connection {@link StreamingHttpConnection} to convert from.
         * {@link #newRequest(HttpRequestMethod, String) create new requests}.
         * @param strategy Default {@link HttpExecutionStrategy} to use.
         */
        ReservedBlockingStreamingHttpConnection(final ReservedStreamingHttpConnection connection,
                                                final HttpExecutionStrategy strategy) {
            super(connection, strategy);
            this.connection = connection;
        }

        /**
         * Releases this reserved {@link BlockingStreamingHttpConnection} to be used for subsequent requests.
         * This method must be idempotent, i.e. calling multiple times must not have side-effects.
         *
         * @throws Exception if any exception occurs during releasing.
         */
        public void release() throws Exception {
            blockingInvocation(connection.releaseAsync());
        }

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
        @Override
        public ReservedStreamingHttpConnection asStreamingConnection() {
            return connection;
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
        @Override
        public ReservedHttpConnection asConnection() {
            return asStreamingConnection().asConnection();
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
        @Override
        public ReservedBlockingHttpConnection asBlockingConnection() {
            return asStreamingConnection().asBlockingConnection();
        }
    }
}
