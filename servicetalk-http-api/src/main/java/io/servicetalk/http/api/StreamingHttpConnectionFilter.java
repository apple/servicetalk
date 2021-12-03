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

import static io.servicetalk.http.api.HttpContextKeys.HTTP_EXECUTION_STRATEGY_KEY;
import static java.util.Objects.requireNonNull;

/**
 * A {@link StreamingHttpConnection} that delegates all methods to a different {@link StreamingHttpConnection}.
 */
public class StreamingHttpConnectionFilter implements FilterableStreamingHttpConnection {
    private final FilterableStreamingHttpConnection delegate;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link FilterableStreamingHttpConnection} to delegate all calls to.
     */
    protected StreamingHttpConnectionFilter(final FilterableStreamingHttpConnection delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return delegate.request(request);
    }

    @Deprecated
    @Override
    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                 final StreamingHttpRequest request) {
        return Single.defer(() -> {
            request.context().put(HTTP_EXECUTION_STRATEGY_KEY, strategy);
            return request(request).shareContextOnSubscribe();
        });
    }

    @Override
    public HttpConnectionContext connectionContext() {
        return delegate.connectionContext();
    }

    @Override
    public <T> Publisher<? extends T> transportEventStream(final HttpEventKey<T> eventKey) {
        return delegate.transportEventStream(eventKey);
    }

    @Override
    public HttpExecutionContext executionContext() {
        return delegate.executionContext();
    }

    @Override
    public StreamingHttpResponseFactory httpResponseFactory() {
        return delegate.httpResponseFactory();
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
    public final StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return delegate.newRequest(method, requestTarget);
    }

    @Override
    public String toString() {
        return getClass().getName() + '(' + delegate + ')';
    }

    /**
     * Get the {@link FilterableStreamingHttpConnection} this method delegates to.
     *
     * @return the {@link FilterableStreamingHttpConnection} this method delegates to.
     */
    protected final FilterableStreamingHttpConnection delegate() {
        return delegate;
    }
}
