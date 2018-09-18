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

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.http.api.BlockingStreamingHttpResponseFactory;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.net.SocketAddress;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

final class DefaultHttpServiceContext extends HttpServiceContext {
    private final StreamingHttpResponseFactory streamingFactory;
    private final ConnectionContext connectionContext;

    private DefaultHttpServiceContext(final HttpResponseFactory factory,
                                      final StreamingHttpResponseFactory streamingFactory,
                                      final BlockingStreamingHttpResponseFactory blockingFactory,
                                      final ConnectionContext connectionContext) {
        super(factory, streamingFactory, blockingFactory);
        this.streamingFactory = streamingFactory;
        this.connectionContext = connectionContext;
    }

    static DefaultHttpServiceContext newInstance(ConnectionContext ctx,
                                                 HttpHeadersFactory headersFactory) {
        final BufferAllocator allocator = ctx.getExecutionContext().getBufferAllocator();
        return new DefaultHttpServiceContext(new DefaultHttpResponseFactory(headersFactory, allocator),
                new DefaultStreamingHttpResponseFactory(headersFactory, allocator),
                new DefaultBlockingStreamingHttpResponseFactory(headersFactory, allocator),
                ctx);
    }

    @Override
    public SocketAddress getLocalAddress() {
        return connectionContext.getLocalAddress();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return connectionContext.getRemoteAddress();
    }

    @Nullable
    @Override
    public SSLSession getSslSession() {
        return connectionContext.getSslSession();
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return connectionContext.getExecutionContext();
    }

    @Override
    public Completable onClose() {
        return connectionContext.onClose();
    }

    @Override
    public Completable closeAsync() {
        return connectionContext.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return connectionContext.closeAsyncGracefully();
    }

    StreamingHttpResponseFactory getStreamingFactory() {
        return streamingFactory;
    }

    @Override
    public String toString() {
        return connectionContext.toString();
    }
}
