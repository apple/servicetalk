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

import io.servicetalk.http.api.BlockingStreamingHttpClient.ReservedBlockingStreamingHttpConnection;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static io.servicetalk.http.api.RequestResponseFactories.toAggregated;

/**
 * The equivalent of {@link HttpClient} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
public final class BlockingHttpClient extends BlockingHttpRequester {

    private final StreamingHttpClient client;

    /**
     * Create a new instance.
     *
     * @param client {@link StreamingHttpClient} to convert from.
     */
    BlockingHttpClient(final StreamingHttpClient client, final HttpExecutionStrategy strategy) {
        super(toAggregated(client.reqRespFactory), strategy);
        this.client = client;
    }

    /**
     * Reserve a {@link BlockingHttpConnection} based on provided {@link HttpRequestMetaData}.
     *
     * @param metaData Allows the underlying layers to know what {@link BlockingHttpConnection}s are valid to
     * reserve for future {@link HttpRequest requests} with the same {@link HttpRequestMetaData}.
     * For example this may provide some insight into shard or other info.
     * @return a {@link ReservedBlockingHttpConnection}.
     * @throws Exception if a exception occurs during the reservation process.
     */
    public ReservedBlockingHttpConnection reserveConnection(HttpRequestMetaData metaData) throws Exception {
        return reserveConnection(executionStrategy(), metaData);
    }

    /**
     * Reserve a {@link BlockingHttpConnection} based on provided {@link HttpRequestMetaData}.
     *
     * @param strategy {@link HttpExecutionStrategy} to use.
     * @param metaData Allows the underlying layers to know what {@link BlockingHttpConnection}s are valid to
     * reserve for future {@link HttpRequest requests} with the same {@link HttpRequestMetaData}.
     * For example this may provide some insight into shard or other info.
     * @return a {@link ReservedBlockingHttpConnection}.
     * @throws Exception if a exception occurs during the reservation process.
     */
    public ReservedBlockingHttpConnection reserveConnection(HttpExecutionStrategy strategy,
                                                                     HttpRequestMetaData metaData) throws Exception {
        return blockingInvocation(client.reserveConnection(strategy, metaData)
                .map(c -> new ReservedBlockingHttpConnection(c, executionStrategy())));
    }

    @Override
    public HttpResponse request(final HttpExecutionStrategy strategy, final HttpRequest request) throws Exception {
        return BlockingUtils.request(client, strategy, request);
    }

    /**
     * Convert this {@link BlockingHttpClient} to the {@link StreamingHttpClient} API.
     *
     * @return a {@link StreamingHttpClient} representation of this {@link BlockingHttpClient}.
     */
    public StreamingHttpClient asStreamingClient() {
        return client;
    }

    /**
     * Convert this {@link BlockingHttpClient} to the {@link HttpClient} API.
     *
     * @return a {@link HttpClient} representation of this {@link BlockingHttpClient}.
     */
    public HttpClient asClient() {
        return asStreamingClient().asClient();
    }

    /**
     * Convert this {@link BlockingHttpClient} to the {@link BlockingStreamingHttpClient} API.
     *
     * @return a {@link BlockingStreamingHttpClient} representation of this {@link BlockingHttpClient}.
     */
    public BlockingStreamingHttpClient asBlockingStreamingClient() {
        return asStreamingClient().asBlockingStreamingClient();
    }

    @Override
    public ExecutionContext executionContext() {
        return client.executionContext();
    }

    @Override
    public void close() throws Exception {
        blockingInvocation(client.closeAsync());
    }

    /**
     * A special type of {@link BlockingHttpConnection} for the exclusive use of the caller of
     * {@link #reserveConnection(HttpRequestMetaData)} and
     * {@link #reserveConnection(HttpExecutionStrategy, HttpRequestMetaData)}.
     */
    public static final class ReservedBlockingHttpConnection extends BlockingHttpConnection {

        private final ReservedStreamingHttpConnection connection;

        /**
         * Create a new instance.
         *
         * @param connection {@link ReservedStreamingHttpConnection} to convert from.
         * @param strategy Default {@link HttpExecutionStrategy} to use.
         */
        ReservedBlockingHttpConnection(final ReservedStreamingHttpConnection connection,
                                       final HttpExecutionStrategy strategy) {
            super(connection, strategy);
            this.connection = connection;
        }

        /**
         * Releases this reserved {@link BlockingHttpConnection} to be used for subsequent requests.
         * This method must be idempotent, i.e. calling multiple times must not have side-effects.
         *
         * @throws Exception if any exception occurs during releasing.
         */
        public void release() throws Exception {
            blockingInvocation(connection.releaseAsync());
        }

        /**
         * Convert this {@link ReservedBlockingHttpConnection} to the {@link ReservedStreamingHttpConnection} API.
         * <p>
         * Note that the resulting {@link ReservedStreamingHttpConnection} may still be subject to any blocking, in
         * memory aggregation, and other behavior as this {@link ReservedBlockingHttpConnection}.
         *
         * @return a {@link StreamingHttpClient.ReservedStreamingHttpConnection} representation of this
         * {@link ReservedBlockingHttpConnection}.
         */
        @Override
        public ReservedStreamingHttpConnection asStreamingConnection() {
            return connection;
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
        @Override
        public ReservedHttpConnection asConnection() {
            return asStreamingConnection().asConnection();
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
        @Override
        public ReservedBlockingStreamingHttpConnection asBlockingStreamingConnection() {
            return asStreamingConnection().asBlockingStreamingConnection();
        }
    }
}
