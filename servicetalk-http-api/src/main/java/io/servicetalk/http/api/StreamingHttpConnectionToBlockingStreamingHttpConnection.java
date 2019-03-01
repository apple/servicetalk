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
import io.servicetalk.http.api.StreamingHttpConnection.SettingKey;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_SEND_STRATEGY;

final class StreamingHttpConnectionToBlockingStreamingHttpConnection extends BlockingStreamingHttpConnection {
    private final StreamingHttpConnection connection;

    private StreamingHttpConnectionToBlockingStreamingHttpConnection(final StreamingHttpConnection connection,
                                                                     final HttpExecutionStrategy strategy) {
        super(new StreamingHttpRequestResponseFactoryToBlockingStreamingHttpRequestResponseFactory(
                connection.reqRespFactory), strategy);
        this.connection = connection;
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
    public BlockingStreamingHttpResponse request(final HttpExecutionStrategy strategy,
                                                 final BlockingStreamingHttpRequest request) throws Exception {
        return BlockingUtils.request(connection, strategy, request);
    }

    @Override
    public ExecutionContext executionContext() {
        return connection.executionContext();
    }

    @Override
    public void close() throws Exception {
        blockingInvocation(connection.closeAsync());
    }

    @Override
    public StreamingHttpConnection asStreamingConnection() {
        return connection;
    }

    static BlockingStreamingHttpConnection transform(StreamingHttpConnection conn) {
        final HttpExecutionStrategy defaultStrategy =
                conn.filterChain.effectiveExecutionStrategy(OFFLOAD_SEND_STRATEGY);
        return new StreamingHttpConnectionToBlockingStreamingHttpConnection(conn, defaultStrategy);
    }
}
