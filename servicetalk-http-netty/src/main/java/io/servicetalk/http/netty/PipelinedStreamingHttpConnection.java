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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.netty.internal.DefaultNettyPipelinedConnection;
import io.servicetalk.transport.netty.internal.NettyConnection;

final class PipelinedStreamingHttpConnection
        extends AbstractStreamingHttpConnection<DefaultNettyPipelinedConnection<Object, Object>> {

    PipelinedStreamingHttpConnection(final NettyConnection<Object, Object> connection,
                                     final ReadOnlyHttpClientConfig config,
                                     final ExecutionContext executionContext,
                                     final StreamingHttpRequestResponseFactory reqRespFactory,
                                     final HttpExecutionStrategy strategy) {
        super(new DefaultNettyPipelinedConnection<>(connection, config.maxPipelinedRequests()),
                config, executionContext, reqRespFactory, strategy);
    }

    @Override
    protected Publisher<Object> writeAndRead(Publisher<Object> requestStream) {
        return connection.request(requestStream);
    }
}
