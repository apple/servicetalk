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

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.http.api.HttpClient.UpgradableHttpResponse;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnection.SettingKey;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static io.servicetalk.http.api.StreamingHttpClientToHttpClient.doUpgradeConnection;
import static java.util.Objects.requireNonNull;

final class StreamingHttpClientToBlockingHttpClient extends BlockingHttpClient {
    private final StreamingHttpClient client;

    StreamingHttpClientToBlockingHttpClient(StreamingHttpClient client) {
        super(new StreamingHttpRequestResponseFactoryToHttpRequestResponseFactory(client.reqRespFactory));
        this.client = requireNonNull(client);
    }

    @Override
    public ReservedBlockingHttpConnection reserveConnection(final HttpRequest request) throws Exception {
        return blockingInvocation(client.reserveConnection(request.toStreamingRequest())
                .map(ReservedStreamingHttpConnectionToBlocking::new));
    }

    @Override
    public UpgradableHttpResponse upgradeConnection(final HttpRequest request) throws Exception {
        return blockingInvocation(doUpgradeConnection(client, request));
    }

    @Override
    public HttpResponse request(final HttpRequest request) throws Exception {
        return BlockingUtils.request(client, request);
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return client.getExecutionContext();
    }

    @Override
    public void close() throws Exception {
        blockingInvocation(client.closeAsync());
    }

    @Override
    StreamingHttpClient asStreamingClientInternal() {
        return client;
    }

    Completable onClose() {
        return client.onClose();
    }

    static final class ReservedStreamingHttpConnectionToBlocking extends ReservedBlockingHttpConnection {
        private final ReservedStreamingHttpConnection connection;

        ReservedStreamingHttpConnectionToBlocking(ReservedStreamingHttpConnection connection) {
            super(new StreamingHttpRequestResponseFactoryToHttpRequestResponseFactory(connection.reqRespFactory));
            this.connection = requireNonNull(connection);
        }

        @Override
        public void release() throws Exception {
            blockingInvocation(connection.releaseAsync());
        }

        @Override
        public ConnectionContext getConnectionContext() {
            return connection.getConnectionContext();
        }

        @Override
        public <T> BlockingIterable<T> getSettingIterable(final SettingKey<T> settingKey) {
            return connection.getSettingStream(settingKey).toIterable();
        }

        @Override
        public HttpResponse request(final HttpRequest request) throws Exception {
            return BlockingUtils.request(connection, request);
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return connection.getExecutionContext();
        }

        @Override
        public void close() throws Exception {
            blockingInvocation(connection.closeAsync());
        }

        @Override
        ReservedStreamingHttpConnection asStreamingConnectionInternal() {
            return connection;
        }

        Completable onClose() {
            return connection.onClose();
        }
    }
}
