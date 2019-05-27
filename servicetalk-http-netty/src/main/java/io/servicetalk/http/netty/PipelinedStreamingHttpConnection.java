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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.transport.netty.internal.DefaultNettyPipelinedConnection;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnection;

import javax.annotation.Nullable;

final class PipelinedStreamingHttpConnection
        extends AbstractStreamingHttpConnection<DefaultNettyPipelinedConnection<Object, Object>> {

    private final NettyConnection<Object, Object> nettyConnection;

    PipelinedStreamingHttpConnection(final NettyConnection<Object, Object> connection,
                                     final ReadOnlyHttpClientConfig config,
                                     final HttpExecutionContext executionContext,
                                     final StreamingHttpRequestResponseFactory reqRespFactory) {
        super(new DefaultNettyPipelinedConnection<>(connection, config.maxPipelinedRequests()),
                config.maxPipelinedRequests(), executionContext, reqRespFactory, config.headersFactory());
        this.nettyConnection = connection;
    }

    @Override
    protected Publisher<Object> writeAndRead(Publisher<Object> requestStream,
                                             @Nullable final FlushStrategy flushStrategy) {
        if (flushStrategy == null) {
            return connection.request(requestStream);
        } else {
            return connection.request(() -> {
                final Cancellable cancellable = connection.updateFlushStrategy(
                        (prev, isOriginal) -> isOriginal ? flushStrategy : prev);
                return nettyConnection.write(requestStream).afterFinally(cancellable::cancel);
            });
        }
    }
}
