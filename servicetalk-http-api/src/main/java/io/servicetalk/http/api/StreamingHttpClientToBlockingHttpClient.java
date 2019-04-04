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
import io.servicetalk.http.api.FilterableStreamingHttpConnection.SettingKey;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static io.servicetalk.http.api.HttpApiConversions.DEFAULT_BLOCKING_CLIENT_STRATEGY;
import static io.servicetalk.http.api.RequestResponseFactories.toAggregated;
import static java.util.Objects.requireNonNull;

final class StreamingHttpClientToBlockingHttpClient implements BlockingHttpClient {
    private final StreamingHttpClient client;
    private final HttpExecutionStrategy strategy;
    private final HttpRequestResponseFactory reqRespFactory;

    StreamingHttpClientToBlockingHttpClient(final StreamingHttpClient client,
                                            final HttpExecutionStrategyInfluencer influencer) {
        strategy = influencer.influenceStrategy(DEFAULT_BLOCKING_CLIENT_STRATEGY);
        this.client = client;
        reqRespFactory = toAggregated(client);
    }

    @Override
    public HttpResponse request(final HttpRequest request) throws Exception {
        return request(strategy, request);
    }

    @Override
    public ReservedBlockingHttpConnection reserveConnection(final HttpRequestMetaData metaData) throws Exception {
        return reserveConnection(strategy, metaData);
    }

    @Override
    public ReservedBlockingHttpConnection reserveConnection(final HttpExecutionStrategy strategy,
                                                            final HttpRequestMetaData metaData) throws Exception {
        return blockingInvocation(client.reserveConnection(strategy, metaData)
                .map(c -> new ReservedStreamingHttpConnectionToReservedBlockingHttpConnection(c, this.strategy,
                        reqRespFactory)));
    }

    @Override
    public StreamingHttpClient asStreamingClient() {
        return client;
    }

    @Override
    public HttpResponse request(final HttpExecutionStrategy strategy, final HttpRequest request) throws Exception {
        return BlockingUtils.request(client, strategy, request);
    }

    @Override
    public ExecutionContext executionContext() {
        return client.executionContext();
    }

    @Override
    public HttpResponseFactory httpResponseFactory() {
        return reqRespFactory;
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    @Override
    public HttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return reqRespFactory.newRequest(method, requestTarget);
    }

    static final class ReservedStreamingHttpConnectionToReservedBlockingHttpConnection implements
                                                                                       ReservedBlockingHttpConnection {
        private final ReservedStreamingHttpConnection connection;
        private final HttpExecutionStrategy strategy;
        private final HttpRequestResponseFactory reqRespFactory;

        ReservedStreamingHttpConnectionToReservedBlockingHttpConnection(
                ReservedStreamingHttpConnection connection, final HttpExecutionStrategyInfluencer influencer) {
            this(connection, influencer.influenceStrategy(DEFAULT_BLOCKING_CLIENT_STRATEGY), toAggregated(connection));
        }

        ReservedStreamingHttpConnectionToReservedBlockingHttpConnection(ReservedStreamingHttpConnection connection,
                                                                        HttpExecutionStrategy strategy,
                                                                        HttpRequestResponseFactory reqRespFactory) {
            this.strategy = strategy;
            this.connection = requireNonNull(connection);
            this.reqRespFactory = requireNonNull(reqRespFactory);
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
        public HttpResponse request(final HttpRequest request) throws Exception {
            return request(strategy, request);
        }

        @Override
        public ConnectionContext connectionContext() {
            return connection.connectionContext();
        }

        @Override
        public <T> BlockingIterable<T> settingIterable(final SettingKey<T> settingKey) {
            return connection.settingStream(settingKey).toIterable();
        }

        @Override
        public HttpResponse request(final HttpExecutionStrategy strategy, final HttpRequest request) throws Exception {
            return BlockingUtils.request(connection, strategy, request);
        }

        @Override
        public ExecutionContext executionContext() {
            return connection.executionContext();
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
        public HttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return reqRespFactory.newRequest(method, requestTarget);
        }
    }
}
