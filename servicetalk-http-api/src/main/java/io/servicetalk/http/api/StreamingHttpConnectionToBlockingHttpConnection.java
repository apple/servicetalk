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

import io.servicetalk.concurrent.BlockingIterable;

import static io.servicetalk.http.api.DefaultHttpExecutionStrategy.OFFLOAD_NONE_STRATEGY;
import static io.servicetalk.http.api.RequestResponseFactories.toAggregated;

final class StreamingHttpConnectionToBlockingHttpConnection implements BlockingHttpConnection {
    static final HttpExecutionStrategy DEFAULT_BLOCKING_CONNECTION_STRATEGY = OFFLOAD_NONE_STRATEGY;
    private final StreamingHttpConnection connection;
    private final HttpExecutionStrategy strategy;
    private final HttpConnectionContext context;
    private final HttpExecutionContext executionContext;
    private final HttpRequestResponseFactory reqRespFactory;

    StreamingHttpConnectionToBlockingHttpConnection(final StreamingHttpConnection connection,
                                                    final HttpExecutionStrategy strategy) {
        this.strategy = DEFAULT_BLOCKING_CONNECTION_STRATEGY.merge(strategy);
        this.connection = connection;
        final HttpConnectionContext originalCtx = connection.connectionContext();
        executionContext = new DelegatingHttpExecutionContext(connection.executionContext()) {
            @Override
            public HttpExecutionStrategy executionStrategy() {
                return StreamingHttpConnectionToBlockingHttpConnection.this.strategy;
            }
        };
        context = new DelegatingHttpConnectionContext(originalCtx) {
            @Override
            public HttpExecutionContext executionContext() {
                return executionContext;
            }
        };
        reqRespFactory = toAggregated(connection);
    }

    @Override
    public HttpConnectionContext connectionContext() {
        return context;
    }

    @Override
    public <T> BlockingIterable<? extends T> transportEventIterable(final HttpEventKey<T> eventKey) {
        return connection.transportEventStream(eventKey).toIterable();
    }

    @Override
    public StreamingHttpConnection asStreamingConnection() {
        return connection;
    }

    @Override
    public HttpResponse request(final HttpRequest request) throws Exception {
        return BlockingUtils.request(connection, request);
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
    public void closeGracefully() throws Exception {
        connection.closeGracefully();
    }

    @Override
    public HttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return reqRespFactory.newRequest(method, requestTarget);
    }
}
