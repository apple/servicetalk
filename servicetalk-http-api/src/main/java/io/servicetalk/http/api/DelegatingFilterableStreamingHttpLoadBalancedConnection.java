/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import static java.util.Objects.requireNonNull;

/**
 * Implementation of {@link FilterableStreamingHttpLoadBalancedConnection} that delegates all methods.
 */
public class DelegatingFilterableStreamingHttpLoadBalancedConnection
        implements FilterableStreamingHttpLoadBalancedConnection {
    private final FilterableStreamingHttpLoadBalancedConnection delegate;

    /**
     * Create a new instance.
     * @param delegate The instance to delegate to.
     */
    public DelegatingFilterableStreamingHttpLoadBalancedConnection(
            final FilterableStreamingHttpLoadBalancedConnection delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public Result tryRequest() {
        return delegate.tryRequest();
    }

    @Override
    public void requestFinished() {
        delegate.requestFinished();
    }

    @Override
    public boolean tryReserve() {
        return delegate.tryReserve();
    }

    @Override
    public int score() {
        return delegate.score();
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
    public Completable onClose() {
        return delegate.onClose();
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
    public void close() throws Exception {
        delegate.close();
    }

    @Override
    public void closeGracefully() throws Exception {
        delegate.closeGracefully();
    }

    @Override
    public Completable releaseAsync() {
        return delegate.releaseAsync();
    }

    @Override
    public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return delegate.newRequest(method, requestTarget);
    }

    @Override
    public StreamingHttpRequest get(final String requestTarget) {
        return delegate.get(requestTarget);
    }

    @Override
    public StreamingHttpRequest post(final String requestTarget) {
        return delegate.post(requestTarget);
    }

    @Override
    public StreamingHttpRequest put(final String requestTarget) {
        return delegate.put(requestTarget);
    }

    @Override
    public StreamingHttpRequest options(final String requestTarget) {
        return delegate.options(requestTarget);
    }

    @Override
    public StreamingHttpRequest head(final String requestTarget) {
        return delegate.head(requestTarget);
    }

    @Override
    public StreamingHttpRequest trace(final String requestTarget) {
        return delegate.trace(requestTarget);
    }

    @Override
    public StreamingHttpRequest delete(final String requestTarget) {
        return delegate.delete(requestTarget);
    }

    @Override
    public StreamingHttpRequest patch(final String requestTarget) {
        return delegate.patch(requestTarget);
    }

    @Override
    public StreamingHttpRequest connect(final String requestTarget) {
        return delegate.connect(requestTarget);
    }

    @Override
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return delegate.request(request);
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
    public String toString() {
        return delegate.toString();
    }
}
