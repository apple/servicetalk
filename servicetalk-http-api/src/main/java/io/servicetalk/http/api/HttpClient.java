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
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.RequestResponseFactories.toAggregated;

/**
 * Provides a means to issue requests against HTTP service. The implementation is free to maintain a collection of
 * {@link HttpConnection} instances and distribute calls to {@link #request(HttpRequest)} amongst this collection.
 */
public final class HttpClient extends HttpRequester {

    private final StreamingHttpClient client;

    /**
     * Create a new instance.
     *
     * @param client {@link StreamingHttpClient} to convert from.
     * @param strategy Default {@link HttpExecutionStrategy} to use.
     */
    HttpClient(final StreamingHttpClient client, final HttpExecutionStrategy strategy) {
        super(toAggregated(client.reqRespFactory), strategy);
        this.client = client;
    }

    /**
     * Reserve an {@link HttpConnection} based on provided {@link HttpRequestMetaData}.
     *
     * @param metaData Allows the underlying layers to know what {@link HttpConnection}s are valid to
     * reserve for future {@link HttpRequest requests} with the same {@link HttpRequestMetaData}.
     * For example this may provide some insight into shard or other info.
     * @return a {@link Single} that provides the {@link ReservedHttpConnection} upon completion.
     */
    public Single<ReservedHttpConnection> reserveConnection(HttpRequestMetaData metaData) {
        return reserveConnection(executionStrategy(), metaData);
    }

    /**
     * Reserve an {@link HttpConnection} based on provided {@link HttpRequestMetaData}.
     * <p>
     * <b>Note:</b> reserved connections are not restricted by {@link BaseHttpBuilder#maxPipelinedRequests(int)}.
     *
     * @param strategy {@link HttpExecutionStrategy} to use.
     * @param metaData Allows the underlying layers to know what {@link HttpConnection}s are valid to
     * reserve for future {@link HttpRequest requests} with the same {@link HttpRequestMetaData}.
     * For example this may provide some insight into shard or other info.
     * @return a {@link Single} that provides the {@link ReservedHttpConnection} upon completion.
     */
    public Single<ReservedHttpConnection> reserveConnection(final HttpExecutionStrategy strategy,
                                                            final HttpRequestMetaData metaData) {
        return client.reserveConnection(strategy, metaData)
                .map(c -> new ReservedHttpConnection(c, executionStrategy()));
    }

    /**
     * Convert this {@link HttpClient} to the {@link StreamingHttpClient} API.
     *
     * @return a {@link StreamingHttpClient} representation of this {@link HttpClient}.
     */
    public StreamingHttpClient asStreamingClient() {
        return client;
    }

    /**
     * Convert this {@link HttpClient} to the {@link BlockingStreamingHttpClient} API.
     *
     * @return a {@link BlockingStreamingHttpClient} representation of this {@link HttpClient}.
     */
    public BlockingStreamingHttpClient asBlockingStreamingClient() {
        return asStreamingClient().asBlockingStreamingClient();
    }

    /**
     * Convert this {@link HttpClient} to the {@link BlockingHttpClient} API.
     *
     * @return a {@link BlockingHttpClient} representation of this {@link HttpClient}.
     */
    public BlockingHttpClient asBlockingClient() {
        return asStreamingClient().asBlockingClient();
    }

    @Override
    public Single<HttpResponse> request(final HttpExecutionStrategy strategy, final HttpRequest request) {
        return client.request(strategy, request.toStreamingRequest()).flatMap(StreamingHttpResponse::toResponse);
    }

    @Override
    public ExecutionContext executionContext() {
        return client.executionContext();
    }

    @Override
    public Completable onClose() {
        return client.onClose();
    }

    @Override
    public Completable closeAsync() {
        return client.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return client.closeAsyncGracefully();
    }

    /**
     * A special type of {@link HttpConnection} for the exclusive use of the caller of
     * {@link #reserveConnection(HttpRequestMetaData)} and
     * {@link #reserveConnection(HttpExecutionStrategy, HttpRequestMetaData)}.
     */
    public static final class ReservedHttpConnection extends HttpConnection {

        private final ReservedStreamingHttpConnection connection;

        /**
         * Create a new instance.
         *
         * @param connection {@link ReservedStreamingHttpConnection} to convert from.
         * @param strategy Default {@link HttpExecutionStrategy} to use.
         */
        ReservedHttpConnection(final ReservedStreamingHttpConnection connection,
                               final HttpExecutionStrategy strategy) {
            super(connection, strategy);
            this.connection = connection;
        }

        /**
         * Releases this reserved {@link ReservedHttpConnection} to be used for subsequent requests.
         * This method must be idempotent, i.e. calling multiple times must not have side-effects.
         *
         * @return the {@code Completable} that is notified on releaseAsync.
         */
        public Completable releaseAsync() {
            return asStreamingConnection().releaseAsync();
        }

        /**
         * Convert this {@link ReservedHttpConnection} to the {@link ReservedStreamingHttpConnection} API.
         *
         * @return a {@link ReservedStreamingHttpConnection} representation of this {@link ReservedHttpConnection}.
         */
        @Override
        public ReservedStreamingHttpConnection asStreamingConnection() {
            return connection;
        }

        /**
         * Convert this {@link ReservedHttpConnection} to the {@link ReservedBlockingStreamingHttpConnection} API.
         *
         * @return a {@link ReservedBlockingStreamingHttpConnection} representation of this
         * {@link ReservedHttpConnection}.
         */
        @Override
        public ReservedBlockingStreamingHttpConnection asBlockingStreamingConnection() {
            return asStreamingConnection().asBlockingStreamingConnection();
        }

        /**
         * Convert this {@link ReservedHttpConnection} to the {@link ReservedBlockingHttpConnection}
         * API.
         *
         * @return a {@link ReservedBlockingHttpConnection} representation of this
         * {@link ReservedHttpConnection}.
         */
        @Override
        public ReservedBlockingHttpConnection asBlockingConnection() {
            return asStreamingConnection().asBlockingConnection();
        }
    }
}
