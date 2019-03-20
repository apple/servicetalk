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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.transport.api.IoExecutor;

import java.net.SocketOption;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * A builder of {@link HttpClient} or {@link HttpConnection} objects.
 *
 * @param <ResolvedAddress> the type of address after resolution (resolved address)
 */
abstract class BaseHttpBuilder<ResolvedAddress> {

    /**
     * Sets the {@link IoExecutor} for all connections created from this builder.
     *
     * @param ioExecutor {@link IoExecutor} to use.
     * @return {@code this}.
     */
    public abstract BaseHttpBuilder<ResolvedAddress> ioExecutor(IoExecutor ioExecutor);

    /**
     * Sets the {@link BufferAllocator} for all connections created from this builder.
     *
     * @param allocator {@link BufferAllocator} to use.
     * @return {@code this}.
     */
    public abstract BaseHttpBuilder<ResolvedAddress> bufferAllocator(BufferAllocator allocator);

    /**
     * Sets the {@link HttpExecutionStrategy} for all connections created from this builder.
     *
     * @param strategy {@link HttpExecutionStrategy} to use.
     * @return {@code this}.
     */
    public abstract BaseHttpBuilder<ResolvedAddress> executionStrategy(HttpExecutionStrategy strategy);

    /**
     * Add a {@link SocketOption} for all connections created by this builder.
     *
     * @param option the option to apply.
     * @param value the value.
     * @param <T> the type of the value.
     * @return {@code this}.
     */
    public abstract <T> BaseHttpBuilder<ResolvedAddress> socketOption(SocketOption<T> option, T value);

    /**
     * Enable wire-logging for connections created by this builder. All wire events will be logged at trace level.
     *
     * @param loggerName The name of the logger to log wire events.
     * @return {@code this}.
     */
    public abstract BaseHttpBuilder<ResolvedAddress> enableWireLogging(String loggerName);

    /**
     * Disable previously configured wire-logging for connections created by this builder.
     * If wire-logging has not been configured before, this method has no effect.
     *
     * @return {@code this}.
     * @see #enableWireLogging(String)
     */
    public abstract BaseHttpBuilder<ResolvedAddress> disableWireLogging();

    /**
     * Set the {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding responses.
     *
     * @param headersFactory the {@link HttpHeadersFactory} to use.
     * @return {@code this}.
     */
    public abstract BaseHttpBuilder<ResolvedAddress> headersFactory(HttpHeadersFactory headersFactory);

    /**
     * Set the maximum size of the initial HTTP line for created connections.
     *
     * @param maxInitialLineLength The {@link StreamingHttpConnection} will throw TooLongFrameException if the initial
     * HTTP line exceeds this length.
     * @return {@code this}.
     */
    public abstract BaseHttpBuilder<ResolvedAddress> maxInitialLineLength(int maxInitialLineLength);

    /**
     * Set the maximum total size of HTTP headers, which could be sent by created connections.
     *
     * @param maxHeaderSize The {@link StreamingHttpConnection} will throw TooLongFrameException if the total size of
     * all HTTP headers exceeds this length.
     * @return {@code this}.
     */
    public abstract BaseHttpBuilder<ResolvedAddress> maxHeaderSize(int maxHeaderSize);

    /**
     * Set the value used to calculate an exponential moving average of the encoded size of the initial line and the
     * headers for a guess for future buffer allocations.
     *
     * @param headersEncodedSizeEstimate An estimated size of encoded initial line and headers.
     * @return {@code this}.
     */
    public abstract BaseHttpBuilder<ResolvedAddress> headersEncodedSizeEstimate(
            int headersEncodedSizeEstimate);

    /**
     * Set the value used to calculate an exponential moving average of the encoded size of the trailers for a guess for
     * future buffer allocations.
     *
     * @param trailersEncodedSizeEstimate An estimated size of encoded trailers.
     * @return {@code this}.
     */
    public abstract BaseHttpBuilder<ResolvedAddress> trailersEncodedSizeEstimate(
            int trailersEncodedSizeEstimate);

    /**
     * Set the maximum number of pipelined HTTP requests to queue up, anything above this will be rejected,
     * 1 means pipelining is disabled and requests and responses are processed sequentially.
     * <p>
     * Request pipelining requires HTTP 1.1.
     *
     * @param maxPipelinedRequests number of pipelined requests to queue up
     * @return {@code this}.
     */
    public abstract BaseHttpBuilder<ResolvedAddress> maxPipelinedRequests(int maxPipelinedRequests);

    /**
     * Disable automatically setting {@code Host} headers by inferring from the address or {@link StreamingHttpRequest}.
     * <p>
     * This setting disables the default filter such that no {@code Host} header will be manipulated.
     *
     * @return {@code this}
     * @see SingleAddressHttpClientBuilder#enableHostHeaderFallback(CharSequence)
     * @see MultiAddressHttpClientBuilder#enableHostHeaderFallback(Function)
     * @see HttpConnectionBuilder#enableHostHeaderFallback(CharSequence)
     */
    public abstract BaseHttpBuilder<ResolvedAddress> disableHostHeaderFallback();

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
    public abstract BaseHttpBuilder<ResolvedAddress> appendConnectionFilter(
            HttpConnectionFilterFactory factory);

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
     * @param factory {@link HttpConnectionFilterFactory} to decorate a {@link StreamingHttpConnection} for the purpose
     * of filtering.
     * @return {@code this}
     */
    // We don't want the user to be able to override but it cannot be final because we need to override the type.
    // However this class is package private so the user will not be able to override this method.
    public /* final */ BaseHttpBuilder<ResolvedAddress> appendConnectionFilter(
            Predicate<StreamingHttpRequest> predicate, HttpConnectionFilterFactory factory) {
        requireNonNull(predicate);
        requireNonNull(factory);

        return appendConnectionFilter((connection) ->
                new ConditionalHttpConnectionFilter(predicate, factory.create(connection), connection));
    }
}
