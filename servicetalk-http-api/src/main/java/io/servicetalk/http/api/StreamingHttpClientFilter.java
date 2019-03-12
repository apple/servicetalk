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
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionContext;

import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_ALL_STRATEGY;
import static java.util.Objects.requireNonNull;

/**
 * A {@link StreamingHttpClient} that delegates all methods to a different {@link StreamingHttpClient}.
 */
public class StreamingHttpClientFilter implements StreamingHttpRequestFactory,
                                                  StreamingHttpRequestFunction,
                                                  ListenableAsyncCloseable {
    @Nullable
    private final StreamingHttpClientFilter delegate;
    final StreamingHttpRequestResponseFactory reqRespFactory;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link StreamingHttpClient} to delegate all calls to.
     */
    public StreamingHttpClientFilter(final StreamingHttpClientFilter delegate) {
        reqRespFactory = delegate.reqRespFactory;
        this.delegate = delegate;
    }

    // This is only for FilterChainTerminal which overrides all methods
    private StreamingHttpClientFilter(final StreamingHttpRequestResponseFactory reqRespFactory) {
        this.reqRespFactory = requireNonNull(reqRespFactory);
        delegate = null;
    }

    /**
     * Reserve a {@link StreamingHttpConnection} based on provided {@link HttpRequestMetaData}.
     *
     * @param strategy {@link HttpExecutionStrategy} to use.
     * @param metaData Allows the underlying layers to know what {@link StreamingHttpConnection}s are valid to
     * reserve for future {@link StreamingHttpRequest requests} with the same {@link HttpRequestMetaData}.
     * For example this may provide some insight into shard or other info.
     * @return a {@link Single} that provides the {@link ReservedStreamingHttpConnectionFilter} upon completion.
     */
    public final Single<ReservedStreamingHttpConnectionFilter> reserveConnection(final HttpExecutionStrategy strategy,
                                                                                 final HttpRequestMetaData metaData) {
        assert delegate != null;
        return reserveConnection(delegate, strategy, metaData);
    }

    @Override
    public final Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                       final StreamingHttpRequest request) {
        assert delegate != null;
        return request(delegate, strategy, request);
    }

    /**
     * Called when the filter needs to delegate the request using the provided {@link StreamingHttpRequestFunction} on which
     * to call {@link StreamingHttpRequestFunction#request(HttpExecutionStrategy, StreamingHttpRequest)}.
     *
     * @param delegate The {@link StreamingHttpRequestFunction} to delegate requests to.
     * @param strategy The {@link HttpExecutionStrategy} to use for executing the request.
     * @param request The request to delegate.
     * @return the response.
     */
    protected Single<StreamingHttpResponse> request(final StreamingHttpRequestFunction delegate,
                                                    final HttpExecutionStrategy strategy,
                                                    final StreamingHttpRequest request) {
        return delegate.request(strategy, request);
    }

    /**
     * Called when the filter needs to delegate the reserve connection request using the provided {@link
     * StreamingHttpClient} on which to call {@link StreamingHttpClient#reserveConnection(HttpExecutionStrategy,
     * HttpRequestMetaData)}.
     *
     * @param delegate the {@link StreamingHttpClientFilter} to delegate requests to.
     * @param strategy the {@link HttpExecutionStrategy} to use for reserving a connection.
     * @param metaData the {@link HttpRequestMetaData} for reserving a connection.
     * @return a {@link Single} that provides the {@link ReservedStreamingHttpConnectionFilter} upon completion.
     */
    protected Single<ReservedStreamingHttpConnectionFilter> reserveConnection(final StreamingHttpClientFilter delegate,
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
        if (delegate != null) { // can't avoid runtime check for FilterChainTerminal - we want to keep this final
            // A streaming filter will offload all paths by default. Implementations can override the behavior and do
            // something sophisticated if required.
            return delegate.effectiveExecutionStrategy(mergeForEffectiveStrategy(strategy));
        }
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

    /**
     * Get the {@link ExecutionContext} used during construction of this object.
     * <p>
     * Note that the {@link ExecutionContext#ioExecutor()} will not necessarily be associated with a specific thread
     * unless that was how this object was built.
     *
     * @return the {@link ExecutionContext} used during construction of this object.
     */
    public ExecutionContext executionContext() {
        assert delegate != null;
        return delegate.executionContext();
    }

    @Override
    public Completable onClose() {
        assert delegate != null;
        return delegate.onClose();
    }

    @Override
    public Completable closeAsync() {
        assert delegate != null;
        return delegate.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        assert delegate != null;
        return delegate.closeAsyncGracefully();
    }

    @Override
    public String toString() {
        return getClass().getName() + '(' + delegate + ')';
    }

    @Override
    public final StreamingHttpResponseFactory httpResponseFactory() {
        return reqRespFactory;
    }

    @Override
    public final StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return reqRespFactory.newRequest(method, requestTarget);
    }

    /**
     * Creates a terminal delegate for a {@link StreamingHttpClientFilter} to indicate the end of a filter chain.
     *
     * <p>All methods of this terminal will throw so the {@link StreamingHttpClientFilter} using this terminal as
     * its delegate NEEDS to override all non-final methods of the {@link StreamingHttpClientFilter} contract.
     *
     * @param reqRespFactory The {@link StreamingHttpRequestResponseFactory} used to {@link
     * #newRequest(HttpRequestMethod, String) create new requests}.
     * @return a terminal delegate for a {@link StreamingHttpClientFilter}.
     */
    public static StreamingHttpClientFilter terminal(final StreamingHttpRequestResponseFactory reqRespFactory) {
        return new FilterChainTerminal(reqRespFactory);
    }

    // This filter is the terminal of the filter chain, the intended use is as delegate for transport implementations
    private static final class FilterChainTerminal extends StreamingHttpClientFilter {
        private static final String FILTER_CHAIN_TERMINAL = "FilterChain Terminal";

        private FilterChainTerminal(final StreamingHttpRequestResponseFactory reqRespFactory) {
            super(reqRespFactory);
        }

        @Override
        protected Single<StreamingHttpResponse> request(final StreamingHttpRequestFunction delegate,
                                                        final HttpExecutionStrategy strategy,
                                                        final StreamingHttpRequest request) {
            throw new UnsupportedOperationException(FILTER_CHAIN_TERMINAL);
        }

        @Override
        protected HttpExecutionStrategy mergeForEffectiveStrategy(final HttpExecutionStrategy mergeWith) {
            return mergeWith;
        }

        @Override
        public ExecutionContext executionContext() {
            throw new UnsupportedOperationException(FILTER_CHAIN_TERMINAL);
        }

        @Override
        public Completable onClose() {
            throw new UnsupportedOperationException(FILTER_CHAIN_TERMINAL);
        }

        @Override
        public Completable closeAsync() {
            throw new UnsupportedOperationException(FILTER_CHAIN_TERMINAL);
        }

        @Override
        public Completable closeAsyncGracefully() {
            throw new UnsupportedOperationException(FILTER_CHAIN_TERMINAL);
        }

        @Override
        protected Single<ReservedStreamingHttpConnectionFilter> reserveConnection(
                final StreamingHttpClientFilter delegate,
                final HttpExecutionStrategy strategy,
                final HttpRequestMetaData metaData) {
            throw new UnsupportedOperationException(FILTER_CHAIN_TERMINAL);
        }
    }

    private final class ClientFilterToReservedConnectionFilter extends ReservedStreamingHttpConnectionFilter {

        private ClientFilterToReservedConnectionFilter(final ReservedStreamingHttpConnectionFilter reserved) {
            super(reserved);
        }

        @Override
        protected Single<StreamingHttpResponse> request(final StreamingHttpConnectionFilter delegate,
                                                        final HttpExecutionStrategy strategy,
                                                        final StreamingHttpRequest request) {
            return StreamingHttpClientFilter.this.request(delegate, strategy, request);
        }
    }
}
