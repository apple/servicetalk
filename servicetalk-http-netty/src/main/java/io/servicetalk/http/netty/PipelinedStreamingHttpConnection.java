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
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
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
        assert connectionContext().protocol().major() <= 1 : "Unexpected protocol version";
        return (flushStrategy == null ? connection.write(requestStream) :
            Publisher.defer(() -> {
                final Cancellable resetFlushStrategy = connection.updateFlushStrategy(
                        (prev, isOriginal) -> isOriginal ? flushStrategy : prev);
                return connection.write(requestStream, connection::defaultFlushStrategy,
                        WriteDemandEstimators::newDefaultEstimator).afterFinally(resetFlushStrategy::cancel);
            })).beforeFinally(new TerminalSignalConsumer() {
                @Override
                public void onComplete() {
                    // noop
                }

                @Override
                public void onError(final Throwable throwable) {
                    // noop
                }

                @Override
                public void cancel() {
                    // If the HTTP/1.x request gets cancelled, we pessimistically assume that the transport will close
                    // the connection since the Subscriber did not read the entire response and cancelled. This reduces
                    // the time window during which a connection is eligible for selection by the load balancer post
                    // cancel and the connection being closed by the transport.
                    // Transport MAY not close the connection if cancel raced with completion and completion was seen by
                    // the transport before cancel. We have no way of knowing at this layer if this indeed happen.
                    closeAsync().subscribe();
                    // Not necessary to do anything for HTTP/2 at the similar level
                    // (NonPipelinedStreamingHttpConnection) because NettyChannelPublisher#cancel0 will be scheduled on
                    // the EventLoop prior marking the request as finished. Therefore, any new attempt to open a stream
                    // on the same h2-connection will see the current stream as already cancelled and won't result in
                    // "max-concurrent-streams" error.
                    // For all other cases, LoadBalancedStreamingHttpClient already has logic to handle streams.
                }
            });
    }
}
