/*
 * Copyright © 2018-2026 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnection;
import io.servicetalk.transport.netty.internal.NettyConnectionContext.FlushStrategyProvider;
import io.servicetalk.transport.netty.internal.NettyPipelinedConnection;
import io.servicetalk.transport.netty.internal.WriteDemandEstimators;

import java.util.function.LongConsumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;

final class PipelinedStreamingHttpConnection
        extends AbstractStreamingHttpConnection<NettyPipelinedConnection<Object, Object>> {
    PipelinedStreamingHttpConnection(final NettyConnection<Object, Object> connection,
                                     final H1ProtocolConfig config,
                                     final StreamingHttpRequestResponseFactory reqRespFactory,
                                     final boolean allowDropTrailersReadFromTransport,
                                     final LongConsumer payloadSizeLimiter,
                                     final boolean expectContinueNoWait) {
        super(new NettyPipelinedConnection<>(connection, config.maxPipelinedRequests()),
                config.maxPipelinedRequests(), reqRespFactory, config.headersFactory(),
                allowDropTrailersReadFromTransport, payloadSizeLimiter, expectContinueNoWait);
    }

    @Override
    protected Publisher<Object> writeAndRead(Publisher<Object> requestStream,
                                             @Nullable final FlushStrategy flushStrategy) {
        if (flushStrategy == null) {
            return connection.write(requestStream);
        } else {
            // TODO(scott): if we can remove the flush state on the connection we can simplify the control flow here.
            return Publisher.defer(() -> {
                final FlushStrategyOverride override = new FlushStrategyOverride(connection, flushStrategy);
                return connection.write(requestStream.beforeFinally(override), override,
                        WriteDemandEstimators::newDefaultEstimator);
            });
        }
    }

    /**
     * Overrides the connection's {@link FlushStrategy} only when this request's write starts. Pipelined writes are
     * queued and share the connection's strategy, so overriding it earlier would also affect requests queued before.
     */
    private static final class FlushStrategyOverride
            implements Supplier<FlushStrategy>, FlushStrategyProvider, Runnable {
        private final NettyPipelinedConnection<Object, Object> connection;
        private final FlushStrategy flushStrategy;
        private Cancellable resetFlushStrategy = IGNORE_CANCEL;

        FlushStrategyOverride(final NettyPipelinedConnection<Object, Object> connection,
                              final FlushStrategy flushStrategy) {
            this.connection = connection;
            this.flushStrategy = flushStrategy;
        }

        @Override
        public FlushStrategy get() {
            resetFlushStrategy = connection.updateFlushStrategy(this);
            return connection.defaultFlushStrategy();
        }

        @Override
        public FlushStrategy computeFlushStrategy(final FlushStrategy current, final boolean isCurrentOriginal) {
            return isCurrentOriginal ? flushStrategy : current;
        }

        @Override
        public void run() {
            resetFlushStrategy.cancel();
        }
    }
}
