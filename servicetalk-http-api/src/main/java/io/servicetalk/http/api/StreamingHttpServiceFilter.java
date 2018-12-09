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

import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;

/**
 * An implementation of {@link StreamingHttpService} that delegates all methods to the provided
 * {@link StreamingHttpService}.
 */
public class StreamingHttpServiceFilter extends StreamingHttpService {

    private final StreamingHttpService delegate;
    private final HttpExecutionStrategy strategy;

    /**
     * New instance.
     *
     * @param delegate {@link StreamingHttpService} to delegate all calls.
     */
    public StreamingHttpServiceFilter(final StreamingHttpService delegate) {
        this(delegate, defaultStrategy());
    }

    /**
     * New instance.
     *
     * @param delegate {@link StreamingHttpService} to delegate all calls.
     * @param strategy {@link HttpExecutionStrategy} for this {@link StreamingHttpServiceFilter}.
     */
    public StreamingHttpServiceFilter(final StreamingHttpService delegate, final HttpExecutionStrategy strategy) {
        this.delegate = delegate;
        this.strategy = strategy;
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx, final StreamingHttpRequest request,
                                                final StreamingHttpResponseFactory responseFactory) {
        return delegate.handle(ctx, request, responseFactory);
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }

    @Override
    public final HttpExecutionStrategy executionStrategy() {
        return strategy;
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
    protected StreamingHttpService delegate() {
        return delegate;
    }
}
