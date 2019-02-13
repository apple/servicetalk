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
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_ALL_STRATEGY;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static java.util.Objects.requireNonNull;

/**
 * A {@link StreamingHttpConnection} that delegates all methods to a different {@link StreamingHttpConnection}.
 */
public class StreamingHttpConnectionFilter extends StreamingHttpConnection {
    private final StreamingHttpConnection delegate;
    private final HttpExecutionStrategy defaultStrategy;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link StreamingHttpConnection} to delegate all calls to.
     */
    public StreamingHttpConnectionFilter(final StreamingHttpConnection delegate) {
        super(delegate.reqRespFactory, defaultStrategy());
        this.delegate = requireNonNull(delegate);
        this.defaultStrategy = defaultStrategy();
    }

    /**
     * Get the {@link StreamingHttpConnection} that this class delegates to.
     *
     * @return the {@link StreamingHttpConnection} that this class delegates to.
     */
    protected final StreamingHttpConnection delegate() {
        return delegate;
    }

    @Override
    public ConnectionContext connectionContext() {
        return delegate.connectionContext();
    }

    @Override
    public <T> Publisher<T> settingStream(final SettingKey<T> settingKey) {
        return delegate.settingStream(settingKey);
    }

    @Override
    public final Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return request(defaultStrategy, request);
    }

    @Override
    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
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
        // Since the next connection is a filter, we are still in filter chain, so propagate the call
        if (delegate instanceof StreamingHttpConnectionFilter) {
            // A streaming filter will offload all paths by default. Implementations can override the behavior and do
            // something sophisticated if required.
            return ((StreamingHttpConnectionFilter) delegate).effectiveExecutionStrategy(
                    mergeForEffectiveStrategy(strategy));
        }
        return delegate.executionStrategy().merge(strategy);
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
}
