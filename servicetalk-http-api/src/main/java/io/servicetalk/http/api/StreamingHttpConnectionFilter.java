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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.StreamingHttpConnection.SettingKey;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_ALL_STRATEGY;
import static java.util.Objects.requireNonNull;

/**
 * A {@link StreamingHttpConnectionFilter} that delegates all methods to a different {@link
 * StreamingHttpConnectionFilter}.
 */
public class StreamingHttpConnectionFilter implements StreamingHttpRequester {

    @Nullable
    private final StreamingHttpConnectionFilter delegate;
    final StreamingHttpRequestResponseFactory reqRespFactory;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link StreamingHttpConnection} to delegate all calls to.
     */
    public StreamingHttpConnectionFilter(final StreamingHttpConnectionFilter delegate) {
        this.delegate = requireNonNull(delegate);
        this.reqRespFactory = delegate.reqRespFactory;
    }

    // This is only for FilterChainTerminal which overrides all methods
    private StreamingHttpConnectionFilter(final StreamingHttpRequestResponseFactory reqRespFactory) {
        this.reqRespFactory = requireNonNull(reqRespFactory);
        this.delegate = null; // won't be de-referenced
    }

    /**
     * Get the {@link ConnectionContext}.
     *
     * @return the {@link ConnectionContext}.
     */
    public ConnectionContext connectionContext() {
        assert delegate != null;
        return delegate.connectionContext();
    }

    /**
     * Returns a {@link Publisher} that gives the current value of the setting as well as subsequent changes to
     * the setting value as long as the {@link PublisherSource.Subscriber} has expressed enough demand.
     *
     * @param settingKey Name of the setting to fetch.
     * @param <T> Type of the setting value.
     * @return {@link Publisher} for the setting values.
     */
    public <T> Publisher<T> settingStream(final SettingKey<T> settingKey) {
        assert delegate != null;
        return delegate.settingStream(settingKey);
    }

    @Override
    public final Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                       final StreamingHttpRequest request) {
        assert delegate != null;
        return request(delegate, strategy, request);
    }

    /**
     * Called when the filter needs to delegate the request using the provided {@link StreamingHttpRequester} on
     * which to call {@link StreamingHttpRequester#request(HttpExecutionStrategy, StreamingHttpRequest)}.
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
     * Determine the effective {@link HttpExecutionStrategy} given the passed {@link HttpExecutionStrategy} and the
     * strategy required by this {@link StreamingHttpConnectionFilter}.
     *
     * @param strategy A {@link HttpExecutionStrategy} as determined by the caller of this method.
     * @return Effective {@link HttpExecutionStrategy}.
     */
    final HttpExecutionStrategy effectiveExecutionStrategy(HttpExecutionStrategy strategy) {
        if (delegate != null) { // can't avoid runtime check for FilterChainTerminal - we want to keep this final
            // Since the next connection is a filter, we are still in filter chain, so propagate the call
            // A streaming filter will offload all paths by default. Implementations can override the behavior and do
            // something sophisticated if required.
            return delegate.effectiveExecutionStrategy(mergeForEffectiveStrategy(strategy));
        }
        return strategy;
    }

    /**
     * When calculating effective {@link HttpExecutionStrategy} this method is called to merge the strategy for the
     * next {@link StreamingHttpConnection} in the filter chain with the {@link HttpExecutionStrategy} of this
     * {@link StreamingHttpConnectionFilter}.
     *
     * @param mergeWith A {@link HttpExecutionStrategy} with which this {@link StreamingHttpConnectionFilter} should
     * merge its {@link HttpExecutionStrategy}.
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
     * Creates a terminal delegate for a {@link StreamingHttpConnectionFilter} to indicate the end of a filter chain.
     *
     * <p>All methods of this terminal will throw so the {@link StreamingHttpConnectionFilter} using this terminal as
     * its delegate NEEDS to override all non-final methods of the {@link StreamingHttpConnectionFilter} contract.
     *
     * @param reqRespFactory The {@link StreamingHttpRequestResponseFactory} used to {@link
     * #newRequest(HttpRequestMethod, String) create new requests}.
     * @return a terminal delegate for a {@link StreamingHttpConnectionFilter}.
     */
    public static StreamingHttpConnectionFilter terminal(final StreamingHttpRequestResponseFactory reqRespFactory) {
       return new FilterChainTerminal(reqRespFactory);
    }

    // This filter is the terminal of the filter chain, the intended use is as delegate for transport implementations
    private static final class FilterChainTerminal extends StreamingHttpConnectionFilter {
        private static final String FILTER_CHAIN_TERMINAL = "FilterChain Terminal";

        private FilterChainTerminal(final StreamingHttpRequestResponseFactory reqRespFactory) {
            super(reqRespFactory);
        }

        @Override
        public ConnectionContext connectionContext() {
            throw new UnsupportedOperationException(FILTER_CHAIN_TERMINAL);
        }

        @Override
        public <T> Publisher<T> settingStream(final SettingKey<T> settingKey) {
            throw new UnsupportedOperationException(FILTER_CHAIN_TERMINAL);
        }

        @Override
        protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
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
    }
}
