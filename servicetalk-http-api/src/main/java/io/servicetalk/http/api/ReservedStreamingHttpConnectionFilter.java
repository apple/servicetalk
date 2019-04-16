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
import io.servicetalk.http.api.StreamingHttpClientToBlockingHttpClient.ReservedStreamingHttpConnectionToReservedBlockingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClientToBlockingStreamingHttpClient.ReservedStreamingHttpConnectionToBlockingStreaming;
import io.servicetalk.http.api.StreamingHttpClientToHttpClient.ReservedStreamingHttpConnectionToReservedHttpConnection;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static java.util.Objects.requireNonNull;

/**
 * A {@link ReservedStreamingHttpConnectionFilter} that delegates all methods to a different
 * {@link ReservedStreamingHttpConnectionFilter}.
 */
public class ReservedStreamingHttpConnectionFilter implements ReservedStreamingHttpConnection {
    private final ReservedStreamingHttpConnection delegate;
    private final HttpExecutionStrategy strategy;
    private final HttpExecutionStrategyInfluencer influencer;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link ReservedStreamingHttpConnection} to delegate all calls to
     */
    protected ReservedStreamingHttpConnectionFilter(final ReservedStreamingHttpConnection delegate) {
        this.delegate = requireNonNull(delegate);
        if (delegate instanceof HttpExecutionStrategyInfluencer) {
            influencer = (HttpExecutionStrategyInfluencer) delegate;
        } else {
            influencer = strategy -> defaultStrategy().merge(strategy);
        }
        this.strategy = influencer.influenceStrategy(defaultStrategy());
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
    public ReservedHttpConnection asConnection() {
        return new ReservedStreamingHttpConnectionToReservedHttpConnection(this, influencer);
    }

    @Override
    public ReservedBlockingStreamingHttpConnection asBlockingStreamingConnection() {
        return new ReservedStreamingHttpConnectionToBlockingStreaming(this, influencer);
    }

    @Override
    public ReservedBlockingHttpConnection asBlockingConnection() {
        return new ReservedStreamingHttpConnectionToReservedBlockingHttpConnection(this, influencer);
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
    public StreamingHttpResponseFactory httpResponseFactory() {
        return delegate.httpResponseFactory();
    }

    @Override
    public void close() throws Exception {
        delegate.close();
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
     */
    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                    final HttpExecutionStrategy strategy,
                                                    final StreamingHttpRequest request) {
        return delegate.request(strategy, request);
    }
}
