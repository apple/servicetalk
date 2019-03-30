/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;

final class DefaultStreamingHttpClient implements StreamingHttpClient {
    private final FilterableStreamingHttpClient client;
    private final HttpExecutionStrategy strategy;

    DefaultStreamingHttpClient(FilterableStreamingHttpClient filteredClient) {
        strategy = filteredClient.computeExecutionStrategy(defaultStrategy());
        client = filteredClient;
    }

    @Override
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return request(strategy, request);
    }

    @Override
    public Single<ReservedStreamingHttpConnection> reserveConnection(final HttpRequestMetaData metaData) {
        return reserveConnection(strategy, metaData);
    }

    @Override
    public Single<ReservedStreamingHttpConnection> reserveConnection(final HttpExecutionStrategy strategy,
                                                                     final HttpRequestMetaData metaData) {
        return client.reserveConnection(strategy, metaData);
    }

    @Override
    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                 final StreamingHttpRequest request) {
        return client.request(strategy, request);
    }

    @Override
    public ExecutionContext executionContext() {
        return client.executionContext();
    }

    @Override
    public StreamingHttpResponseFactory httpResponseFactory() {
        return client.httpResponseFactory();
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    @Override
    public HttpExecutionStrategy computeExecutionStrategy(final HttpExecutionStrategy other) {
        return client.computeExecutionStrategy(other);
    }

    @Override
    public Completable onClose() {
        return client.onClose();
    }

    @Override
    public Completable closeAsync() {
        return client.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return client.closeAsyncGracefully();
    }

    @Override
    public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return client.newRequest(method, requestTarget);
    }
}
