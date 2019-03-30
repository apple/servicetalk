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
import io.servicetalk.client.api.internal.ReservableRequestConcurrencyController;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static java.util.Objects.requireNonNull;

/**
 * Makes the wrapped {@link StreamingHttpConnection} aware of the {@link LoadBalancer}.
 */
final class LoadBalancedStreamingHttpConnection implements ReservedStreamingHttpConnection,
                                                           ReservableRequestConcurrencyController {
    private final ReservableRequestConcurrencyController limiter;
    private final FilterableStreamingHttpConnection filteredConnection;
    private final HttpExecutionStrategy strategy;

    LoadBalancedStreamingHttpConnection(FilterableStreamingHttpConnection filteredConnection,
                                        ReservableRequestConcurrencyController limiter) {
        this.strategy = filteredConnection.computeExecutionStrategy(defaultStrategy());
        this.filteredConnection = filteredConnection;
        this.limiter = requireNonNull(limiter);
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
        return filteredConnection.request(strategy, request);
    }

    @Override
    public ConnectionContext connectionContext() {
        return filteredConnection.connectionContext();
    }

    @Override
    public <T> Publisher<T> settingStream(final SettingKey<T> settingKey) {
        return filteredConnection.settingStream(settingKey);
    }

    @Override
    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                 final StreamingHttpRequest request) {
        return filteredConnection.request(strategy, request);
    }

    @Override
    public ExecutionContext executionContext() {
        return filteredConnection.executionContext();
    }

    @Override
    public StreamingHttpResponseFactory httpResponseFactory() {
        return filteredConnection.httpResponseFactory();
    }

    @Override
    public void close() throws Exception {
        filteredConnection.close();
    }

    @Override
    public HttpExecutionStrategy computeExecutionStrategy(final HttpExecutionStrategy other) {
        return filteredConnection.computeExecutionStrategy(other);
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
}
