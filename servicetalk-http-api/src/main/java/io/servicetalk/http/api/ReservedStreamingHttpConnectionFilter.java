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
 * A {@link ReservedStreamingHttpConnectionFilter} that delegates all methods to a different
 * {@link ReservedStreamingHttpConnectionFilter}.
 */
// FIXME: 0.42 - extend StreamingHttpConnectionFilter
public class ReservedStreamingHttpConnectionFilter implements FilterableReservedStreamingHttpConnection {
    private final FilterableReservedStreamingHttpConnection delegate;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link FilterableReservedStreamingHttpConnection} to delegate all calls to
     */
    protected ReservedStreamingHttpConnectionFilter(final FilterableReservedStreamingHttpConnection delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public Completable releaseAsync() {
        return delegate.releaseAsync();
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
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return delegate.request(request);
    }

    @Deprecated
    @Override
    public final Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                       final StreamingHttpRequest request) {
        return request(delegate, strategy, request);
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
    public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return delegate.newRequest(method, requestTarget);
    }

    @Override
    public String toString() {
        return getClass().getName() + '(' + delegate + ')';
    }

    /**
     * Get the {@link ReservedStreamingHttpConnection} this method delegates to.
     *
     * @return the {@link ReservedStreamingHttpConnection} this method delegates to.
     */
    protected final FilterableReservedStreamingHttpConnection delegate() {
        return delegate;
    }

    /**
     * Called when the filter needs to delegate the request using the provided {@link StreamingHttpRequester} on
     * which to call {@link StreamingHttpRequester#request(HttpExecutionStrategy, StreamingHttpRequest)}.
     *
     * @param delegate The {@link StreamingHttpRequester} to delegate requests to.
     * @param strategy The {@link HttpExecutionStrategy} to use for executing the request.
     * @param request The request to delegate.
     * @return the response.
     * @deprecated Use {@link #request(StreamingHttpRequest)}. If an {@link HttpExecutionStrategy} needs to be altered,
     * provide a value for {@link HttpContextKeys#HTTP_EXECUTION_STRATEGY_KEY} in the
     * {@link HttpRequestMetaData#context() request context}.
     */
    @Deprecated
    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                    final HttpExecutionStrategy strategy,
                                                    final StreamingHttpRequest request) {
        return Single.defer(() -> {
            request.context().put(HTTP_EXECUTION_STRATEGY_KEY, strategy);
            return request(request).shareContextOnSubscribe();
        });
    }
}
