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
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnection;

import javax.annotation.Nullable;

final class NonPipelinedStreamingHttpConnection
        extends AbstractStreamingHttpConnection<NettyConnection<Object, Object>> {

    NonPipelinedStreamingHttpConnection(final NettyConnection<Object, Object> connection,
                                        final HttpExecutionContext executionContext,
                                        final StreamingHttpRequestResponseFactory reqRespFactory,
                                        final HttpHeadersFactory headersFactory,
                                        final boolean requireTrailerHeader) {
        super(connection, 1, executionContext, reqRespFactory, headersFactory, requireTrailerHeader);
    }

    @Override
    protected Publisher<Object> writeAndRead(final Publisher<Object> requestStream,
                                             @Nullable final FlushStrategy flushStrategy) {
        if (flushStrategy == null) {
            return connection.write(requestStream).mergeDelayError(connection.read());
        } else {
            return Publisher.defer(() -> {
                final Cancellable resetFlushStrategy = connection.updateFlushStrategy(
                        (prev, isOriginal) -> isOriginal ? flushStrategy : prev);
                return connection.write(requestStream).mergeDelayError(connection.read())
                        .afterFinally(resetFlushStrategy::cancel);
            });
        }
    }
}
