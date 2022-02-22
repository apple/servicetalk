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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.ReservableRequestConcurrencyController;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableReservedControlledStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpConnectionContext;
import io.servicetalk.http.api.HttpEventKey;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.ReservedBlockingStreamingHttpConnection;
import io.servicetalk.http.api.ReservedHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.transport.api.ExecutionStrategyInfluencer;

import static io.servicetalk.http.api.HttpApiConversions.toReservedBlockingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toReservedBlockingStreamingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toReservedConnection;
import static java.util.Objects.requireNonNull;

/**
 * Makes the wrapped {@link StreamingHttpConnection} aware of the {@link LoadBalancer}.
 */
final class LoadBalancedStreamingHttpConnection
        implements FilterableReservedControlledStreamingHttpLoadBalancedConnection,
                   // Since we do not have filters for reserved connection, we rely on the original implementation to
                   // be an influencer hence we can try to correctly delegate when possible.
                   // Reserved connection given to the user will use the correct strategy and influencer chain since
                   // we wrap before returning to the user.
                   ExecutionStrategyInfluencer<HttpExecutionStrategy> {
    private final ReservableRequestConcurrencyController limiter;
    private final FilterableStreamingHttpLoadBalancedConnection filteredConnection;
    private final HttpExecutionStrategy connectStrategy;

    LoadBalancedStreamingHttpConnection(FilterableStreamingHttpLoadBalancedConnection filteredConnection,
                                        ReservableRequestConcurrencyController limiter,
                                        HttpExecutionStrategy connectStrategy) {
        this.filteredConnection = filteredConnection;
        this.limiter = requireNonNull(limiter);
        this.connectStrategy = connectStrategy;
    }

    @Override
    public boolean tryReserve() {
        return limiter.tryReserve();
    }

    @Override
    public Result tryRequest() {
        return limiter.tryRequest();
    }

    @Override
    public void requestFinished() {
        limiter.requestFinished();
    }

    @Override
    public Completable releaseAsync() {
        return limiter.releaseAsync();
    }

    @Override
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return filteredConnection.request(request);
    }

    @Override
    public HttpConnectionContext connectionContext() {
        return filteredConnection.connectionContext();
    }

    @Override
    public <T> Publisher<? extends T> transportEventStream(final HttpEventKey<T> eventKey) {
        return filteredConnection.transportEventStream(eventKey);
    }

    @Override
    public HttpExecutionContext executionContext() {
        return filteredConnection.executionContext();
    }

    @Override
    public StreamingHttpResponseFactory httpResponseFactory() {
        return filteredConnection.httpResponseFactory();
    }

    @Override
    public Completable onClose() {
        return filteredConnection.onClose();
    }

    @Override
    public Completable closeAsync() {
        return filteredConnection.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return filteredConnection.closeAsyncGracefully();
    }

    @Override
    public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return filteredConnection.newRequest(method, requestTarget);
    }

    @Override
    public ReservedHttpConnection asConnection() {
        return toReservedConnection(this, connectStrategy);
    }

    @Override
    public ReservedBlockingStreamingHttpConnection asBlockingStreamingConnection() {
        return toReservedBlockingStreamingConnection(this, connectStrategy);
    }

    @Override
    public ReservedBlockingHttpConnection asBlockingConnection() {
        return toReservedBlockingConnection(this, connectStrategy);
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return connectStrategy;
    }

    @Override
    public int score() {
        return filteredConnection.score();
    }
}
