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
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_ALL_STRATEGY;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;

/**
 * A {@link StreamingHttpClient} that delegates all methods to a different {@link StreamingHttpClient}.
 */
public class StreamingHttpClientFilter extends StreamingHttpClient {
    private final StreamingHttpClient delegate;
    private final HttpExecutionStrategy defaultStrategy;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link StreamingHttpClient} to delegate all calls to.
     */
    public StreamingHttpClientFilter(final StreamingHttpClient delegate) {
        super(delegate.reqRespFactory, defaultStrategy());
        this.delegate = delegate;
        defaultStrategy = defaultStrategy();
    }

    @Override
    public final Single<ReservedStreamingHttpConnection> reserveConnection(
            final HttpRequestMetaData metaData) {
        return reserveConnection(defaultStrategy, metaData);
    }

    @Override
    public final Single<ReservedStreamingHttpConnection> reserveConnection(
            final HttpExecutionStrategy strategy, final HttpRequestMetaData metaData) {
        return reserveConnection(delegate, strategy, metaData);
    }

    @Override
    public final Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return request(delegate, defaultStrategy, request);
    }

    @Override
    public final Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                       final StreamingHttpRequest request) {
        return request(delegate, strategy, request);
    }

    /**
     * Called when the filter needs to delegate the request using the provided {@link StreamingHttpRequester} on which
     * to call {@link StreamingHttpRequester#request(HttpExecutionStrategy, StreamingHttpRequest)}.
     *
     * @param delegate The {@link StreamingHttpRequester} to delegate requests to.
     * @param strategy The {@link HttpExecutionStrategy} to use for executing the request.
     * @param request The request to delegate.
     * @return the response.
     */
    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                    final HttpExecutionStrategy strategy,
                                                    final StreamingHttpRequest request) {
        return delegate.request(strategy, request);
    }

    /**
     * Called when the filter needs to delegate the reserve connection request using the provided {@link
     * StreamingHttpClient} on which to call {@link StreamingHttpClient#reserveConnection(HttpExecutionStrategy,
     * HttpRequestMetaData)}.
     *
     * @param delegate the {@link StreamingHttpClient} to delegate requests to.
     * @param strategy the {@link HttpExecutionStrategy} to use for reserving a connection.
     * @param metaData the {@link HttpRequestMetaData} for reserving a connection.
     * @return a {@link Single} that provides the {@link ReservedStreamingHttpConnection} upon completion.
     */
    protected Single<ReservedStreamingHttpConnection> reserveConnection(final StreamingHttpClient delegate,
                                                                        final HttpExecutionStrategy strategy,
                                                                        final HttpRequestMetaData metaData) {
        return delegate.reserveConnection(strategy, metaData).map(ClientFilterToReservedConnectionFilter::new);
    }

    /**
     * Determine the effective {@link HttpExecutionStrategy} given the passed {@link HttpExecutionStrategy} and the
     * strategy required by this {@link StreamingHttpClientFilter}.
     *
     * @param strategy A {@link HttpExecutionStrategy} as determined by the caller of this method.
     * @return Effective {@link HttpExecutionStrategy}.
     */
    final HttpExecutionStrategy effectiveExecutionStrategy(HttpExecutionStrategy strategy) {
        // Since the next client is a filter, we are still in filter chain, so propagate the call
        if (delegate instanceof StreamingHttpClientFilter) {
            // A streaming filter will offload all paths by default. Implementations can override the behavior and do
            // something sophisticated if required.
            return ((StreamingHttpClientFilter) delegate).effectiveExecutionStrategy(
                    mergeForEffectiveStrategy(strategy));
        }
        // End of the filter chain, delegate will be our client which will not override the passed strategy
        return strategy;
    }

    /**
     * When calculating effective {@link HttpExecutionStrategy} this method is called to merge the strategy for the
     * next {@link StreamingHttpClient} in the filter chain with the {@link HttpExecutionStrategy} of this
     * {@link StreamingHttpClientFilter}.
     *
     * @param mergeWith A {@link HttpExecutionStrategy} with which this {@link StreamingHttpClientFilter} should merge
     * its {@link HttpExecutionStrategy}.
     * @return Merged {@link HttpExecutionStrategy}.
     */
    protected HttpExecutionStrategy mergeForEffectiveStrategy(HttpExecutionStrategy mergeWith) {
        return mergeWith.merge(OFFLOAD_ALL_STRATEGY);
    }

    @Override
    public ExecutionContext executionContext() {
        return delegate.executionContext();
    }

    @Override
    public Completable onClose() {
        return delegate.onClose();
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return delegate.closeAsyncGracefully();
    }

    @Override
    public String toString() {
        return getClass().getName() + '(' + delegate + ')';
    }

    private final class ClientFilterToReservedConnectionFilter extends ReservedStreamingHttpConnectionFilter {

        ClientFilterToReservedConnectionFilter(final ReservedStreamingHttpConnection reserved) {
            super(reserved);
        }

        @Override
        public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                     final StreamingHttpRequest request) {
            return StreamingHttpClientFilter.this.request(delegate(), strategy, request);
        }
    }
}
