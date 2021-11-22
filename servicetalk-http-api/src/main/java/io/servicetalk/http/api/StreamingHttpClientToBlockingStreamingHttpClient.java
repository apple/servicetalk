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

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static io.servicetalk.http.api.HttpContextKeys.HTTP_EXECUTION_STRATEGY_KEY;
import static io.servicetalk.http.api.RequestResponseFactories.toBlockingStreaming;
import static io.servicetalk.http.api.StreamingHttpConnectionToBlockingStreamingHttpConnection.DEFAULT_BLOCKING_STREAMING_CONNECTION_STRATEGY;
import static java.util.Objects.requireNonNull;

final class StreamingHttpClientToBlockingStreamingHttpClient implements BlockingStreamingHttpClient {
    private final StreamingHttpClient client;
    private final HttpExecutionStrategy strategy;
    private final HttpExecutionContext context;
    private final BlockingStreamingHttpRequestResponseFactory reqRespFactory;

    StreamingHttpClientToBlockingStreamingHttpClient(final StreamingHttpClient client,
                                                     final HttpExecutionStrategy strategy) {
        this.strategy = DEFAULT_BLOCKING_STREAMING_CONNECTION_STRATEGY.merge(strategy);
        this.client = client;
        context = new DelegatingHttpExecutionContext(client.executionContext()) {
            @Override
            public HttpExecutionStrategy executionStrategy() {
                return StreamingHttpClientToBlockingStreamingHttpClient.this.strategy;
            }
        };
        reqRespFactory = toBlockingStreaming(client);
    }

    @Override
    public ReservedBlockingStreamingHttpConnection reserveConnection(final HttpRequestMetaData metaData)
            throws Exception {
        // It is assumed that users will always apply timeouts at the StreamingHttpService layer (e.g. via filter).
        // So we don't apply any explicit timeout here and just wait forever.
        metaData.context().putIfAbsent(HTTP_EXECUTION_STRATEGY_KEY, strategy);
        return blockingInvocation(client.reserveConnection(metaData)
                .map(c -> new ReservedStreamingHttpConnectionToBlockingStreaming(c, this.strategy, reqRespFactory)));
    }

    @Override
    public StreamingHttpClient asStreamingClient() {
        return client;
    }

    @Override
    public BlockingStreamingHttpResponse request(final BlockingStreamingHttpRequest request) throws Exception {
        request.context().putIfAbsent(HTTP_EXECUTION_STRATEGY_KEY, strategy);
        return blockingInvocation(client.request(request.toStreamingRequest())).toBlockingStreamingResponse();
    }

    @Override
    public HttpExecutionContext executionContext() {
        return context;
    }

    @Override
    public BlockingStreamingHttpResponseFactory httpResponseFactory() {
        return reqRespFactory;
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    @Override
    public void closeGracefully() throws Exception {
        client.closeGracefully();
    }

    @Override
    public BlockingStreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return reqRespFactory.newRequest(method, requestTarget);
    }

    static final class ReservedStreamingHttpConnectionToBlockingStreaming implements
                                                                              ReservedBlockingStreamingHttpConnection {
        private final ReservedStreamingHttpConnection connection;
        private final HttpExecutionStrategy strategy;
        private final HttpConnectionContext context;
        private final HttpExecutionContext executionContext;
        private final BlockingStreamingHttpRequestResponseFactory reqRespFactory;

        ReservedStreamingHttpConnectionToBlockingStreaming(final ReservedStreamingHttpConnection connection,
                                                           final HttpExecutionStrategy strategy) {
            this(connection, DEFAULT_BLOCKING_STREAMING_CONNECTION_STRATEGY.merge(strategy),
                    toBlockingStreaming(connection));
        }

        ReservedStreamingHttpConnectionToBlockingStreaming(
                final ReservedStreamingHttpConnection connection,
                final HttpExecutionStrategy strategy,
                final BlockingStreamingHttpRequestResponseFactory reqRespFactory) {

            this.connection = requireNonNull(connection);
            this.strategy = strategy;
            final HttpConnectionContext originalCtx = connection.connectionContext();
            executionContext = new DelegatingHttpExecutionContext(connection.executionContext()) {
                @Override
                public HttpExecutionStrategy executionStrategy() {
                    return ReservedStreamingHttpConnectionToBlockingStreaming.this.strategy;
                }
            };
            context = new DelegatingHttpConnectionContext(originalCtx) {
                @Override
                public HttpExecutionContext executionContext() {
                    return executionContext;
                }
            };
            this.reqRespFactory = reqRespFactory;
        }

        @Override
        public void release() throws Exception {
            blockingInvocation(connection.releaseAsync());
        }

        @Override
        public ReservedStreamingHttpConnection asStreamingConnection() {
            return connection;
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
        public BlockingStreamingHttpResponse request(final BlockingStreamingHttpRequest request) throws Exception {
            return blockingInvocation(connection.request(request.toStreamingRequest()))
                    .toBlockingStreamingResponse();
        }

        @Override
        public HttpExecutionContext executionContext() {
            return executionContext;
        }

        @Override
        public BlockingStreamingHttpResponseFactory httpResponseFactory() {
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
        public BlockingStreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return reqRespFactory.newRequest(method, requestTarget);
        }
    }
}
