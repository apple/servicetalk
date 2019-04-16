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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpConnection;
import io.servicetalk.http.api.BlockingStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.HttpApiConversions.toBlockingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toBlockingStreamingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toConnection;

final class FilterableConnectionToConnection implements StreamingHttpConnection {
    private final FilterableStreamingHttpConnection filteredConnection;
    private final HttpExecutionStrategy streamingStrategy;
    private final HttpExecutionStrategyInfluencer strategyInfluencer;

    FilterableConnectionToConnection(final FilterableStreamingHttpConnection filteredConnection,
                                     final HttpExecutionStrategy streamingStrategy,
                                     final HttpExecutionStrategyInfluencer strategyInfluencer) {
        this.filteredConnection = filteredConnection;
        this.streamingStrategy = streamingStrategy;
        this.strategyInfluencer = strategyInfluencer;
    }

    @Override
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return request(streamingStrategy, request);
    }

    @Override
    public HttpConnection asConnection() {
        return toConnection(this, strategyInfluencer);
    }

    @Override
    public BlockingStreamingHttpConnection asBlockingStreamingConnection() {
        return toBlockingStreamingConnection(this, strategyInfluencer);
    }

    @Override
    public BlockingHttpConnection asBlockingConnection() {
        return toBlockingConnection(this, strategyInfluencer);
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
