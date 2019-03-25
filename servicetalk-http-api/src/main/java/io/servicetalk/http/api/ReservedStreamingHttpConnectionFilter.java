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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnection.SettingKey;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_ALL_STRATEGY;
import static java.util.Objects.requireNonNull;

/**
 * A {@link ReservedStreamingHttpConnectionFilter} that delegates all methods to a different
 * {@link ReservedStreamingHttpConnectionFilter}.
 */
public abstract class ReservedStreamingHttpConnectionFilter extends StreamingHttpConnectionFilter {
    @Nullable
    private final ReservedStreamingHttpConnectionFilter delegate;
    final StreamingHttpRequestResponseFactory reqRespFactory;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link ReservedStreamingHttpConnection} to delegate all calls to
     */
    protected ReservedStreamingHttpConnectionFilter(final ReservedStreamingHttpConnectionFilter delegate) {
        super(requireNonNull(delegate));
        this.delegate = delegate;
        this.reqRespFactory = delegate.reqRespFactory;
    }

    /**
     * Create a new instance.
     *
     * @param delegate The {@link StreamingHttpConnectionFilter} to delegate all calls to
     */
    protected ReservedStreamingHttpConnectionFilter(final StreamingHttpConnectionFilter delegate) {
        this(new HttpConnectionToReservedHttpConnectionFilter(requireNonNull(delegate)));
    }

    // This is only for FilterChainTerminal which overrides all methods
    private ReservedStreamingHttpConnectionFilter(final StreamingHttpRequestResponseFactory reqRespFactory) {
        super(StreamingHttpConnectionFilter.terminal(requireNonNull(reqRespFactory)));
        this.reqRespFactory = requireNonNull(reqRespFactory);
        this.delegate = null;
    }

    /**
     * Releases this reserved {@link HttpClient.ReservedHttpConnection} to be used for subsequent requests.
     * This method must be idempotent, i.e. calling multiple times must not have side-effects.
     *
     * @return the {@code Completable} that is notified on releaseAsync.
     */
    public Completable releaseAsync() {
        assert delegate != null;
        return delegate.releaseAsync();
    }

    @Override
    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                    final HttpExecutionStrategy strategy,
                                                    final StreamingHttpRequest request) {
        return delegate.request(strategy, request);
    }

    @Override
    public ConnectionContext connectionContext() {
        assert delegate != null;
        return delegate.connectionContext();
    }

    @Override
    public <T> Publisher<T> settingStream(final SettingKey<T> settingKey) {
        assert delegate != null;
        return delegate.settingStream(settingKey);
    }

    /**
     * When calculating effective {@link HttpExecutionStrategy} this method is called to merge the strategy for the
     * next {@link ReservedStreamingHttpConnectionFilter} in the filter chain with the {@link HttpExecutionStrategy} of
     * this {@link ReservedStreamingHttpConnectionFilter}.
     *
     * @param mergeWith A {@link HttpExecutionStrategy} with which this {@link ReservedStreamingHttpConnectionFilter}
     * should merge its {@link HttpExecutionStrategy}.
     * @return Merged {@link HttpExecutionStrategy}.
     */
    @Override
    protected HttpExecutionStrategy mergeForEffectiveStrategy(HttpExecutionStrategy mergeWith) {
        return mergeWith.merge(OFFLOAD_ALL_STRATEGY);
    }

    @Override
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

    /**
     * Creates a terminal delegate for a {@link ReservedStreamingHttpConnectionFilter} to indicate the end of a filter
     * chain.
     *
     * <p>All methods of this terminal will throw so the {@link ReservedStreamingHttpConnectionFilter} using this
     * terminal as its delegate NEEDS to override all non-final methods of the {@link
     * ReservedStreamingHttpConnectionFilter} contract.
     *
     * @param reqRespFactory The {@link StreamingHttpRequestResponseFactory} used to {@link
     * #newRequest(HttpRequestMethod, String) create new requests}.
     * @return a terminal delegate for a {@link ReservedStreamingHttpConnectionFilter}.
     */
    public static ReservedStreamingHttpConnectionFilter terminal(
            final StreamingHttpRequestResponseFactory reqRespFactory) {
        return new FilterChainTerminal(reqRespFactory);
    }

    private static final class FilterChainTerminal extends ReservedStreamingHttpConnectionFilter {
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

        @Override
        public Completable releaseAsync() {
            throw new UnsupportedOperationException(FILTER_CHAIN_TERMINAL);
        }
    }
}
