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

import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_ALL_STRATEGY;
import static java.util.Objects.requireNonNull;

/**
 * An implementation of {@link StreamingHttpService} that delegates all methods to the provided
 * {@link StreamingHttpService}.
 */
public class StreamingHttpServiceFilter extends StreamingHttpService {

    private final StreamingHttpService delegate;

    /**
     * New instance.
     *
     * @param delegate {@link StreamingHttpService} to delegate all calls.
     */
    public StreamingHttpServiceFilter(final StreamingHttpService delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx, final StreamingHttpRequest request,
                                                final StreamingHttpResponseFactory responseFactory) {
        return delegate.handle(ctx, request, responseFactory);
    }

    /**
     * Determine the effective {@link HttpExecutionStrategy} given the passed {@link HttpExecutionStrategy} and the
     * strategy required by this {@link StreamingHttpServiceFilter}.
     *
     * @param strategy A {@link HttpExecutionStrategy} as determined by the caller of this method.
     * @return Effective {@link HttpExecutionStrategy}.
     */
    final HttpExecutionStrategy effectiveExecutionStrategy(HttpExecutionStrategy strategy) {
        // Since the next service is a filter, we are still in filter chain, so propagate the call
        if (delegate instanceof StreamingHttpServiceFilter) {
            // A streaming filter will offload all paths by default. Implementations can override the behavior and do
            // something sophisticated if required.
            return ((StreamingHttpServiceFilter) delegate).effectiveExecutionStrategy(
                    mergeForEffectiveStrategy(strategy));
        }
        // End of the filter chain.
        return delegate.executionStrategy().merge(mergeForEffectiveStrategy(strategy));
    }

    /**
     * When calculating effective {@link HttpExecutionStrategy} this method is called to merge the strategy for the
     * next {@link StreamingHttpService} in the filter chain with the {@link HttpExecutionStrategy} of this
     * {@link StreamingHttpServiceFilter}.
     *
     * @param mergeWith A {@link HttpExecutionStrategy} with which this {@link StreamingHttpServiceFilter} should merge
     * its {@link HttpExecutionStrategy}.
     * @return Merged {@link HttpExecutionStrategy}.
     */
    protected HttpExecutionStrategy mergeForEffectiveStrategy(HttpExecutionStrategy mergeWith) {
        return mergeWith.merge(OFFLOAD_ALL_STRATEGY);
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }

    @Override
    public final HttpExecutionStrategy executionStrategy() {
        return delegate.executionStrategy();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return delegate.closeAsyncGracefully();
    }

    /**
     * Returns {@link StreamingHttpService} to which all calls are delegated.
     *
     * @return {@link StreamingHttpService} to which all calls are delegated.
     */
    protected final StreamingHttpService delegate() {
        return delegate;
    }
}
