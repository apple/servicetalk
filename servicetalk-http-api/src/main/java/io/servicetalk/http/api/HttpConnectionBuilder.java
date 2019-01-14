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
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.IoExecutor;

import java.util.function.Predicate;

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static java.util.Objects.requireNonNull;

/**
 * A builder for {@link StreamingHttpConnection} objects.
 *
 * @param <ResolvedAddress> The type of resolved address that can be used for connecting.
 */
public interface HttpConnectionBuilder<ResolvedAddress> {

    /**
     * Sets the {@link IoExecutor} for all connections created from this {@link HttpClientBuilder}.
     *
     * @param ioExecutor {@link IoExecutor} to use.
     * @return {@code this}.
     */
    HttpConnectionBuilder<ResolvedAddress> ioExecutor(IoExecutor ioExecutor);

    /**
     * Sets the {@link Executor} for all connections created from this {@link HttpClientBuilder}.
     *
     * @param executor {@link Executor} to use.
     * @return {@code this}.
     */
    HttpConnectionBuilder<ResolvedAddress> executor(Executor executor);

    /**
     * Sets the {@link BufferAllocator} for all connections created from this {@link HttpClientBuilder}.
     *
     * @param allocator {@link BufferAllocator} to use.
     * @return {@code this}.
     */
    HttpConnectionBuilder<ResolvedAddress> bufferAllocator(BufferAllocator allocator);

    /**
     * Create a new {@link StreamingHttpConnection}, using a default {@link ExecutionContext}.
     *
     * @param resolvedAddress a resolved address to use when connecting
     * @return A single that will complete with the {@link StreamingHttpConnection}
     */
    Single<StreamingHttpConnection> buildStreaming(ResolvedAddress resolvedAddress);

    /**
     * Create a new {@link HttpConnection}, using a default {@link ExecutionContext}.
     *
     * @param resolvedAddress a resolved address to use when connecting
     * @return A single that will complete with the {@link HttpConnection}
     */
    default Single<HttpConnection> build(ResolvedAddress resolvedAddress) {
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
    default BlockingStreamingHttpConnection buildBlockingStreaming(ResolvedAddress resolvedAddress) throws Exception {
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
    default BlockingHttpConnection buildBlocking(ResolvedAddress resolvedAddress) throws Exception {
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
    default ConnectionFactory<ResolvedAddress, StreamingHttpConnection> asConnectionFactory() {
        return new EmptyCloseConnectionFactory<>(this::buildStreaming);
    }

    /**
     * Append the filter to the chain of filters used to decorate the {@link StreamingHttpConnection} created by this
     * builder.
     * <p>
     * Filtering allows you to wrap a {@link StreamingHttpConnection} and modify behavior during request/response
     * processing
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * making a request to a connection wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; connection
     * </pre>
     * @param factory {@link HttpConnectionFilterFactory} to decorate a {@link StreamingHttpConnection} for the purpose
     * of filtering.
     * @return {@code this}
     */
    HttpConnectionBuilder<ResolvedAddress> appendConnectionFilter(HttpConnectionFilterFactory factory);

    /**
     * Append the filter to the chain of filters used to decorate the {@link StreamingHttpConnection} created by this
     * builder, for every request that passes the provided {@link Predicate}.
     * <p>
     * Note this method will be used to decorate the result of {@link #buildStreaming(Object)} before it is
     * returned to the user.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * making a request to a connection wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; client
     * </pre>
     * @param predicate the {@link Predicate} to test if the filter must be applied.
     * @param factory {@link HttpConnectionFilterFactory} to decorate a {@link StreamingHttpConnection} for the purpose
     * of filtering.
     * @return {@code this}
     */
    default HttpConnectionBuilder<ResolvedAddress> appendConnectionFilter(
            Predicate<StreamingHttpRequest> predicate, HttpConnectionFilterFactory factory) {
        requireNonNull(predicate);
        requireNonNull(factory);

        return appendConnectionFilter((connection) ->
                new ConditionalHttpConnectionFilter(predicate, factory.create(connection), connection));
    }
}
