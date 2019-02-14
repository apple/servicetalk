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
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_RECEIVE_META_STRATEGY;
import static java.util.Objects.requireNonNull;

final class StreamingHttpConnectionToHttpConnection extends HttpConnection {
    private final StreamingHttpConnection connection;

    private StreamingHttpConnectionToHttpConnection(final StreamingHttpConnection connection,
                                                    final HttpExecutionStrategy strategy) {
        super(new StreamingHttpRequestResponseFactoryToHttpRequestResponseFactory(connection.reqRespFactory), strategy);
        this.connection = requireNonNull(connection);
    }

    @Override
    public ConnectionContext connectionContext() {
        return connection.connectionContext();
    }

    @Override
    public <T> Publisher<T> settingStream(final StreamingHttpConnection.SettingKey<T> settingKey) {
        return connection.settingStream(settingKey);
    }

    @Override
    public Single<HttpResponse> request(final HttpExecutionStrategy strategy, final HttpRequest request) {
        return connection.request(strategy, request.toStreamingRequest()).flatMap(StreamingHttpResponse::toResponse);
    }

    @Override
    public ExecutionContext executionContext() {
        return connection.executionContext();
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
    StreamingHttpConnection asStreamingConnectionInternal() {
        return connection;
    }

    static HttpConnection transform(StreamingHttpConnection conn) {
        final HttpExecutionStrategy defaultStrategy = conn instanceof StreamingHttpConnectionFilter ?
                ((StreamingHttpConnectionFilter) conn).effectiveExecutionStrategy(OFFLOAD_RECEIVE_META_STRATEGY) :
                OFFLOAD_RECEIVE_META_STRATEGY;
        return new StreamingHttpConnectionToHttpConnection(conn, defaultStrategy);
    }
}
