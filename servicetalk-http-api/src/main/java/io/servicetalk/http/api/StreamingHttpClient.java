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
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.concurrent.internal.FutureUtils.awaitTermination;
import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_NONE_STRATEGY;
import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_RECEIVE_META_STRATEGY;
import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_SEND_STRATEGY;
import static java.util.Objects.requireNonNull;

/**
 * The equivalent of {@link HttpClient} but that accepts {@link StreamingHttpRequest} and returns
 * {@link StreamingHttpResponse}.
 */
public final class StreamingHttpClient implements StreamingHttpRequester {

    private final HttpExecutionStrategy strategy;
    final StreamingHttpClientFilter filterChain;
    private final StreamingHttpRequestResponseFactory reqRespFactory;

    /**
     * Create a new instance.
     *
     * @param strategy Default {@link HttpExecutionStrategy} to use.
     */
    StreamingHttpClient(final StreamingHttpClientFilter filterChain,
                        final HttpExecutionStrategy strategy) {
        this.strategy = requireNonNull(strategy);
        this.filterChain = requireNonNull(filterChain);
        reqRespFactory = filterChain.reqRespFactory;
    }

    /**
     * Send a {@code request}.
     *
     * @param request the request to send.
     * @return The response.
     */
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return request(strategy, request);
    }

    @Override
    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                 final StreamingHttpRequest request) {
        return filterChain.request(strategy, request);
    }

    @Override
    public ExecutionContext executionContext() {
        return filterChain.executionContext();
    }

    /**
     * Reserve a {@link StreamingHttpConnection} based on provided {@link HttpRequestMetaData}.
     *
     * @param metaData Allows the underlying layers to know what {@link StreamingHttpConnection}s are valid to
     * reserve for future {@link StreamingHttpRequest requests} with the same {@link HttpRequestMetaData}.
     * For example this may provide some insight into shard or other info.
     * @return a {@link Single} that provides the {@link ReservedStreamingHttpConnection} upon completion.
     */
    public Single<ReservedStreamingHttpConnection> reserveConnection(HttpRequestMetaData metaData) {
        return reserveConnection(strategy, metaData);
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
    public Single<ReservedStreamingHttpConnection> reserveConnection(HttpExecutionStrategy strategy,
                                                                     HttpRequestMetaData metaData) {
        return filterChain.reserveConnection(strategy, metaData)
                .map(rcf -> new ReservedStreamingHttpConnection(rcf, this.strategy));
    }

    /**
     * Convert this {@link StreamingHttpClient} to the {@link HttpClient} API.
     * <p>
     * This API is provided for convenience. It is recommended that
     * filters are implemented using the {@link StreamingHttpClient} asynchronous API for maximum portability.
     * @return a {@link HttpClient} representation of this {@link StreamingHttpRequester}.
     */
    public HttpClient asClient() {
        return new HttpClient(this,
                filterChain.effectiveExecutionStrategy(OFFLOAD_RECEIVE_META_STRATEGY));
    }

    /**
     * Convert this {@link StreamingHttpClient} to the {@link BlockingStreamingHttpClient} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link StreamingHttpClient} asynchronous API for maximum portability.
     * @return a {@link BlockingStreamingHttpClient} representation of this {@link StreamingHttpClient}.
     */
    public BlockingStreamingHttpClient asBlockingStreamingClient() {
        return new BlockingStreamingHttpClient(this,
                filterChain.effectiveExecutionStrategy(OFFLOAD_SEND_STRATEGY));
    }

    /**
     * Convert this {@link StreamingHttpClient} to the {@link BlockingHttpClient} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link StreamingHttpClient} asynchronous API for maximum portability.
     * @return a {@link BlockingHttpClient} representation of this {@link StreamingHttpClient}.
     */
    public BlockingHttpClient asBlockingClient() {
        return new BlockingHttpClient(this, filterChain.effectiveExecutionStrategy(OFFLOAD_NONE_STRATEGY));
    }

    @Override
    public Completable onClose() {
        return filterChain.onClose();
    }

    @Override
    public Completable closeAsync() {
        return filterChain.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return filterChain.closeAsyncGracefully();
    }

    @Override
    public void close() throws Exception {
        awaitTermination(closeAsyncGracefully().toFuture());
    }

    @Override
    public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return reqRespFactory.newRequest(method, requestTarget);
    }

    @Override
    public StreamingHttpResponseFactory httpResponseFactory() {
        return reqRespFactory;
    }

    /**
     * A special type of {@link StreamingHttpConnection} for the exclusive use of the caller of
     * {@link #reserveConnection(HttpRequestMetaData)} and
     * {@link #reserveConnection(HttpExecutionStrategy, HttpRequestMetaData)}.
     */
    public static final class ReservedStreamingHttpConnection extends StreamingHttpConnection {

        final ReservedStreamingHttpConnectionFilter filterChain;

        /**
         * Create a new instance.
         *
         * @param strategy Default {@link HttpExecutionStrategy} to use.
         */
        ReservedStreamingHttpConnection(final ReservedStreamingHttpConnectionFilter filter,
                                        final HttpExecutionStrategy strategy) {
            super(filter, strategy);
            filterChain = filter;
        }

        /**
         * Releases this reserved {@link StreamingHttpConnection} to be used for subsequent requests.
         * This method must be idempotent, i.e. calling multiple times must not have side-effects.
         *
         * @return the {@code Completable} that is notified on releaseAsync.
         */
        public Completable releaseAsync() {
            return filterChain.releaseAsync();
        }

        /**
         * Convert this {@link ReservedStreamingHttpConnection} to the {@link ReservedHttpConnection} API.
         * <p>
         * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
         * filters are implemented using the {@link ReservedStreamingHttpConnection} asynchronous API for maximum
         * portability.
         * @return a {@link ReservedHttpConnection} representation of this
         * {@link ReservedStreamingHttpConnection}.
         */
        @Override
        public ReservedHttpConnection asConnection() {
            return new ReservedHttpConnection(this,
                    filterChain.effectiveExecutionStrategy(OFFLOAD_RECEIVE_META_STRATEGY));
        }

        /**
         * Convert this {@link ReservedStreamingHttpConnection} to the {@link BlockingStreamingHttpClient} API.
         * <p>
         * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
         * filters are implemented using the {@link ReservedStreamingHttpConnection} asynchronous API for maximum
         * portability.
         * @return a {@link BlockingStreamingHttpClient} representation of this {@link ReservedStreamingHttpConnection}.
         */
        @Override
        public ReservedBlockingStreamingHttpConnection asBlockingStreamingConnection() {
            return new ReservedBlockingStreamingHttpConnection(this,
                    filterChain.effectiveExecutionStrategy(OFFLOAD_SEND_STRATEGY));
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
        @Override
        public ReservedBlockingHttpConnection asBlockingConnection() {
            return new ReservedBlockingHttpConnection(this,
                    filterChain.effectiveExecutionStrategy(OFFLOAD_NONE_STRATEGY));
        }
    }
}
