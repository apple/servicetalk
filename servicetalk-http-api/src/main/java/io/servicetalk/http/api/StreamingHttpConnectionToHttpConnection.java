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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection.SettingKey;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.DelegatingConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_RECEIVE_DATA_STRATEGY;
import static io.servicetalk.http.api.RequestResponseFactories.toAggregated;

final class StreamingHttpConnectionToHttpConnection implements HttpConnection {
    /**
     * For aggregation, we invoke the user callback (Single from client#request()) after the payload is completed,
     * hence we need to offload data.
     */
    static final HttpExecutionStrategy DEFAULT_CONNECTION_STRATEGY = OFFLOAD_RECEIVE_DATA_STRATEGY;
    private final StreamingHttpConnection connection;
    private final HttpExecutionStrategy strategy;
    private final ConnectionContext context;
    private final HttpExecutionContext executionContext;
    private final HttpRequestResponseFactory reqRespFactory;

    StreamingHttpConnectionToHttpConnection(final StreamingHttpConnection connection,
                                            final HttpExecutionStrategyInfluencer influencer) {
        strategy = influencer.influenceStrategy(DEFAULT_CONNECTION_STRATEGY);
        this.connection = connection;
        ConnectionContext originalCtx = connection.connectionContext();
        executionContext = new DelegatingHttpExecutionContext(connection.executionContext()) {
            @Override
            public HttpExecutionStrategy executionStrategy() {
                return strategy;
            }
        };
        context = new DelegatingConnectionContext(originalCtx) {
            @Override
            public ExecutionContext executionContext() {
                return executionContext;
            }
        };
        reqRespFactory = toAggregated(connection);
    }

    @Override
    public Single<HttpResponse> request(final HttpRequest request) {
        return request(strategy, request);
    }

    @Override
    public ConnectionContext connectionContext() {
        return context;
    }

    @Override
    public <T> Publisher<T> settingStream(final SettingKey<T> settingKey) {
        return connection.settingStream(settingKey);
    }

    @Override
    public StreamingHttpConnection asStreamingConnection() {
        return connection;
    }

    @Override
    public Single<HttpResponse> request(final HttpExecutionStrategy strategy, final HttpRequest request) {
        return connection.request(strategy, request.toStreamingRequest()).flatMap(StreamingHttpResponse::toResponse);
    }

    @Override
    public HttpExecutionContext executionContext() {
        return executionContext;
    }

    @Override
    public HttpResponseFactory httpResponseFactory() {
        return reqRespFactory;
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }

    @Override
    public Completable onClose() {
        return connection.onClose();
    }

    @Override
    public Completable closeAsync() {
        return connection.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return connection.closeAsyncGracefully();
    }

    @Override
    public HttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return reqRespFactory.newRequest(method, requestTarget);
    }
}
