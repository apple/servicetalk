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

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;

import java.net.SocketOption;
import java.util.function.Predicate;

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;

/**
 * A builder for {@link StreamingHttpConnection} objects.
 *
 * @param <ResolvedAddress> The type of resolved address that can be used for connecting.
 */
public abstract class HttpConnectionBuilder<ResolvedAddress> extends BaseHttpBuilder<ResolvedAddress> {
    /**
     * An {@link HttpExecutionStrategy} to use when there is none specifed on the {@link HttpConnectionBuilder}.
     */
    public static final HttpExecutionStrategy DEFAULT_BUILDER_STRATEGY = HttpClientBuilder.DEFAULT_BUILDER_STRATEGY;

    private HttpExecutionStrategy strategy = DEFAULT_BUILDER_STRATEGY;

    @Override
    public abstract HttpConnectionBuilder<ResolvedAddress> ioExecutor(IoExecutor ioExecutor);

    @Override
    public abstract HttpConnectionBuilder<ResolvedAddress> bufferAllocator(BufferAllocator allocator);

    @Override
    public final HttpConnectionBuilder<ResolvedAddress> executionStrategy(HttpExecutionStrategy strategy) {
        this.strategy = strategy;
        return this;
    }

    @Override
    public abstract <T> HttpConnectionBuilder<ResolvedAddress> socketOption(SocketOption<T> option, T value);

    @Override
    public abstract HttpConnectionBuilder<ResolvedAddress> enableWireLogging(String loggerName);

    @Override
    public abstract HttpConnectionBuilder<ResolvedAddress> disableWireLogging();

    @Override
    public abstract HttpConnectionBuilder<ResolvedAddress> headersFactory(HttpHeadersFactory headersFactory);

    @Override
    public abstract HttpConnectionBuilder<ResolvedAddress> maxInitialLineLength(int maxInitialLineLength);

    @Override
    public abstract HttpConnectionBuilder<ResolvedAddress> maxHeaderSize(int maxHeaderSize);

    @Override
    public abstract HttpConnectionBuilder<ResolvedAddress> headersEncodedSizeEstimate(
            int headersEncodedSizeEstimate);

    @Override
    public abstract HttpConnectionBuilder<ResolvedAddress> trailersEncodedSizeEstimate(
            int trailersEncodedSizeEstimate);

    @Override
    public abstract HttpConnectionBuilder<ResolvedAddress> maxPipelinedRequests(int maxPipelinedRequests);

    @Override
    public abstract HttpConnectionBuilder<ResolvedAddress> disableHostHeaderFallback();

    /**
     * Automatically set the provided {@link HttpHeaderNames#HOST} on {@link StreamingHttpRequest}s when it's missing.
     * <p>
     * For known address types such as {@link HostAndPort} the {@link HttpHeaderNames#HOST} is inferred and
     * automatically set by default, if you have a custom address type or want to override the inferred value use this
     * method. Use {@link #disableHostHeaderFallback()} if you don't want any {@link HttpHeaderNames#HOST} manipulation
     * at all.
     *
     * @param hostHeader the value for the {@link HttpHeaderNames#HOST}
     * @return {@code this}
     */
    public abstract HttpConnectionBuilder<ResolvedAddress> enableHostHeaderFallback(CharSequence hostHeader);

    /**
     * Create a new {@link StreamingHttpConnection}.
     *
     * @param resolvedAddress a resolved address to use when connecting
     * @return A single that will complete with the {@link StreamingHttpConnection}
     */
    public abstract Single<StreamingHttpConnection> buildStreaming(ResolvedAddress resolvedAddress);

    /**
     * Create a new {@link HttpConnection}, using a default {@link ExecutionContext}.
     *
     * @param resolvedAddress a resolved address to use when connecting
     * @return A single that will complete with the {@link HttpConnection}
     */
    public Single<HttpConnection> build(ResolvedAddress resolvedAddress) {
        return buildStreaming(resolvedAddress).map(StreamingHttpConnection::asConnection);
    }

    /**
     * Create a new {@link BlockingStreamingHttpConnection} and waits till it is created, using a default {@link
     * ExecutionContext}.
     *
     * @param resolvedAddress a resolved address to use when connecting
     * @return {@link BlockingStreamingHttpConnection}
     * @throws Exception If the connection can not be created.
     */
    public BlockingStreamingHttpConnection buildBlockingStreaming(ResolvedAddress resolvedAddress) throws Exception {
        return blockingInvocation(buildStreaming(resolvedAddress)).asBlockingStreamingConnection();
    }

    /**
     * Create a new {@link BlockingHttpConnection} and waits till it is created, using a default
     * {@link ExecutionContext}.
     *
     * @param resolvedAddress a resolved address to use when connecting
     * @return {@link BlockingHttpConnection}
     * @throws Exception If the connection can not be created.
     */
    public BlockingHttpConnection buildBlocking(ResolvedAddress resolvedAddress) throws Exception {
        return blockingInvocation(buildStreaming(resolvedAddress)).asBlockingConnection();
    }

    /**
     * Convert this {@link HttpConnectionBuilder} to a {@link ConnectionFactory}, using a default
     * {@link ExecutionContext}.
     * <p>
     * This can be useful to take advantage of connection filters targeted at the {@link ConnectionFactory} API.
     *
     * @return A {@link ConnectionFactory} that will use the {@link #buildStreaming(Object)}
     * method to create new {@link StreamingHttpConnection} objects.
     */
    public ConnectionFactory<ResolvedAddress, StreamingHttpConnection> asConnectionFactory() {
        return new EmptyCloseConnectionFactory<>(this::buildStreaming);
    }

    @Override
    public abstract HttpConnectionBuilder<ResolvedAddress> appendConnectionFilter(
            StreamingHttpConnectionFilterFactory factory);

    @Override
    public HttpConnectionBuilder<ResolvedAddress> appendConnectionFilter(
            Predicate<StreamingHttpRequest> predicate, StreamingHttpConnectionFilterFactory factory) {
        super.appendConnectionFilter(predicate, factory);
        return this;
    }

    /**
     * Returns the {@link HttpExecutionStrategy} used by this {@link HttpConnectionBuilder}.
     *
     * @return {@link HttpExecutionStrategy} used by this {@link HttpConnectionBuilder}.
     */
    protected final HttpExecutionStrategy executionStrategy() {
        return strategy;
    }
}
