/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.api;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.transport.api.IoExecutor;

import java.net.SocketOption;
import java.util.function.Predicate;
import javax.annotation.Nullable;

interface BaseGrpcClientBuilder<U, R> {

    /**
     * Sets the {@link IoExecutor} for all clients created from this builder.
     *
     * @param ioExecutor {@link IoExecutor} to use.
     * @return {@code this}.
     */
    BaseGrpcClientBuilder<U, R> ioExecutor(IoExecutor ioExecutor);

    /**
     * Sets the {@link BufferAllocator} for all clients created from this builder.
     *
     * @param allocator {@link BufferAllocator} to use.
     * @return {@code this}.
     */
    BaseGrpcClientBuilder<U, R> bufferAllocator(BufferAllocator allocator);

    /**
     * Sets the {@link HttpExecutionStrategy} for all clients created from this builder.
     *
     * @param strategy {@link HttpExecutionStrategy} to use.
     * @return {@code this}.
     */
    BaseGrpcClientBuilder<U, R> executionStrategy(HttpExecutionStrategy strategy);

    /**
     * Add a {@link SocketOption} for all clients created by this builder.
     *
     * @param option the option to apply.
     * @param value the value.
     * @param <T> the type of the value.
     * @return {@code this}.
     */
    <T> BaseGrpcClientBuilder<U, R> socketOption(SocketOption<T> option, T value);

    /**
     * Enable wire-logging for clients created by this builder. All wire events will be logged at trace level.
     *
     * @param loggerName The name of the logger to log wire events.
     * @return {@code this}.
     */
    BaseGrpcClientBuilder<U, R> enableWireLogging(String loggerName);

    /**
     * Disable previously configured wire-logging for clients created by this builder.
     * If wire-logging has not been configured before, this method has no effect.
     *
     * @return {@code this}.
     * @see #enableWireLogging(String)
     */
    BaseGrpcClientBuilder<U, R> disableWireLogging();

    /**
     * Set the {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding responses.
     *
     * @param headersFactory the {@link HttpHeadersFactory} to use.
     * @return {@code this}.
     */
    BaseGrpcClientBuilder<U, R> headersFactory(HttpHeadersFactory headersFactory);

    /**
     * Set the {@link HttpHeadersFactory} to use when HTTP/2 is used.
     *
     * @param headersFactory the {@link HttpHeadersFactory} to use when HTTP/2 is used.
     * @return {@code this.}
     */
    BaseGrpcClientBuilder<U, R> h2HeadersFactory(HttpHeadersFactory headersFactory);

    /**
     * Enable HTTP/2 via
     * <a href="https://tools.ietf.org/html/rfc7540#section-3.4">Prior Knowledge</a>.
     * @param h2PriorKnowledge {@code true} to enable HTTP/2 via
     * <a href="https://tools.ietf.org/html/rfc7540#section-3.4">Prior Knowledge</a>.
     *
     * @return {@code this}.
     */
    BaseGrpcClientBuilder<U, R> h2PriorKnowledge(boolean h2PriorKnowledge);

    /**
     * Set the name of the frame logger when HTTP/2 is used.
     *
     * @param h2FrameLogger the name of the frame logger, or {@code null} to disable.
     * @return {@code this}.
     */
    BaseGrpcClientBuilder<U, R> h2FrameLogger(@Nullable String h2FrameLogger);

    /**
     * Set the maximum size of the initial HTTP line for created connections.
     *
     * @param maxInitialLineLength The {@link StreamingHttpConnection} will throw TooLongFrameException if the initial
     * HTTP line exceeds this length.
     * @return {@code this}.
     */
    BaseGrpcClientBuilder<U, R> maxInitialLineLength(int maxInitialLineLength);

    /**
     * Set the maximum total size of HTTP headers, which could be sent by created connections.
     *
     * @param maxHeaderSize The {@link StreamingHttpConnection} will throw TooLongFrameException if the total size of
     * all HTTP headers exceeds this length.
     * @return {@code this}.
     */
    BaseGrpcClientBuilder<U, R> maxHeaderSize(int maxHeaderSize);

    /**
     * Set the value used to calculate an exponential moving average of the encoded size of the initial line and the
     * headers for a guess for future buffer allocations.
     *
     * @param headersEncodedSizeEstimate An estimated size of encoded initial line and headers.
     * @return {@code this}.
     */
    BaseGrpcClientBuilder<U, R> headersEncodedSizeEstimate(int headersEncodedSizeEstimate);

    /**
     * Set the value used to calculate an exponential moving average of the encoded size of the trailers for a guess for
     * future buffer allocations.
     *
     * @param trailersEncodedSizeEstimate An estimated size of encoded trailers.
     * @return {@code this}.
     */
    BaseGrpcClientBuilder<U, R> trailersEncodedSizeEstimate(int trailersEncodedSizeEstimate);

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
     * @param factory {@link StreamingHttpConnectionFilterFactory} to decorate a {@link StreamingHttpConnection} for the
     * purpose of filtering.
     * @return {@code this}
     */
    BaseGrpcClientBuilder<U, R> appendConnectionFilter(StreamingHttpConnectionFilterFactory factory);

    /**
     * Append the filter to the chain of filters used to decorate the {@link StreamingHttpConnection} created by this
     * builder, for every request that passes the provided {@link Predicate}.
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
     * @param predicate the {@link Predicate} to test if the filter must be applied.
     * @param factory {@link StreamingHttpConnectionFilterFactory} to decorate a {@link StreamingHttpConnection} for the
     * purpose of filtering.
     * @return {@code this}
     */
    // We don't want the user to be able to override but it cannot be final because we need to override the type.
    // However this class is package private so the user will not be able to override this method.
    BaseGrpcClientBuilder<U, R> appendConnectionFilter(
            Predicate<StreamingHttpRequest> predicate, StreamingHttpConnectionFilterFactory factory);
}
