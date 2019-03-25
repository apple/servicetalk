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
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ExecutionStrategy;

import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_ALL_STRATEGY;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static java.util.Objects.requireNonNull;

/**
 * A {@link ReservedStreamingHttpConnectionFilter} that delegates all methods to a different
 * {@link ReservedStreamingHttpConnectionFilter}.
 */
public class ReservedStreamingHttpConnectionFilter implements ReservedStreamingHttpConnection {
    private final ReservedStreamingHttpConnection delegate;
    private final HttpExecutionStrategy strategy;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link ReservedStreamingHttpConnection} to delegate all calls to
     */
    protected ReservedStreamingHttpConnectionFilter(final ReservedStreamingHttpConnection delegate) {
        this.delegate = requireNonNull(delegate);
        // TODO(scott): should we compute this lazily?
        this.strategy = computeExecutionStrategy(defaultStrategy());
    }

    @Override
    public Completable releaseAsync() {
        return delegate.releaseAsync();
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
        return request(delegate, strategy, request);
    }

    @Override
    public final Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                       final StreamingHttpRequest request) {
        return request(delegate, strategy, request);
    }

    @Override
    public ExecutionContext executionContext() {
        return delegate.executionContext();
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }

    @Override
    public HttpExecutionStrategy computeExecutionStrategy(final HttpExecutionStrategy other) {
        return delegate.computeExecutionStrategy(other.merge(executionStrategy()));
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
    public StreamingHttpResponse newResponse(final HttpResponseStatus status) {
        return delegate.newResponse(status);
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
     */
    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                    final HttpExecutionStrategy strategy,
                                                    final StreamingHttpRequest request) {
        return delegate.request(strategy, request);
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
