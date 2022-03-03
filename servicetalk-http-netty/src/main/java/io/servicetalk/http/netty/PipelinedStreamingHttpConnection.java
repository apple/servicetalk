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
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnection;
import io.servicetalk.transport.netty.internal.WriteDemandEstimators;

import javax.annotation.Nullable;

final class PipelinedStreamingHttpConnection
        extends AbstractStreamingHttpConnection<NettyPipelinedConnection<Object, Object>> {
    PipelinedStreamingHttpConnection(final NettyConnection<Object, Object> connection,
                                     final H1ProtocolConfig config,
                                     final HttpExecutionContext executionContext,
                                     final StreamingHttpRequestResponseFactory reqRespFactory,
                                     final boolean allowDropTrailersReadFromTransport) {
        super(new NettyPipelinedConnection<>(connection, config.maxPipelinedRequests()),
                config.maxPipelinedRequests(), executionContext, reqRespFactory, config.headersFactory(),
                allowDropTrailersReadFromTransport);
    }

    @Override
    protected Publisher<Object> writeAndRead(Publisher<Object> requestStream,
                                             @Nullable final FlushStrategy flushStrategy) {
        if (flushStrategy == null) {
            return connection.write(requestStream);
        } else {
            // TODO(scott): if we can remove the flush state on the connection we can simplify the control flow here.
            return Publisher.defer(() -> {
                final Cancellable resetFlushStrategy = connection.updateFlushStrategy(
                        (prev, isOriginal) -> isOriginal ? flushStrategy : prev);
                return connection.write(requestStream, connection::defaultFlushStrategy,
                        WriteDemandEstimators::newDefaultEstimator).afterFinally(resetFlushStrategy::cancel);
            });
        }
    }
}
