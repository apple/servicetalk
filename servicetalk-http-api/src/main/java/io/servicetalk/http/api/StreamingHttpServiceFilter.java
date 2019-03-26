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
import io.servicetalk.transport.api.ExecutionStrategy;

import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_ALL_STRATEGY;
import static java.util.Objects.requireNonNull;

/**
 * An implementation of {@link StreamingHttpService} that delegates all methods to the provided
 * {@link StreamingHttpService}.
 */
public class StreamingHttpServiceFilter implements StreamingHttpService {

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

    @Override
    public HttpExecutionStrategy computeExecutionStrategy(HttpExecutionStrategy other) {
        return delegate.computeExecutionStrategy(executionStrategy().merge(other));
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
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

    /**
     * The {@link ExecutionStrategy} considering the programming constraints of this {@link StreamingHttpServiceFilter}
     * in isolation. This strategy should be the "least common denominator" for example if any blocking is done this
     * method should reflect that.
     *
     * @return The {@link ExecutionStrategy} considering the programming constraints of this
     * {@link StreamingHttpServiceFilter} in isolation.
     */
    protected HttpExecutionStrategy executionStrategy() {
        return OFFLOAD_ALL_STRATEGY;
    }
}
