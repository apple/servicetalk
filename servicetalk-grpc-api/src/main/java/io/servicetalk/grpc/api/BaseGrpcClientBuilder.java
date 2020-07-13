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
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;
import io.servicetalk.transport.api.TransportObserver;

import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.function.Predicate;

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
     * Sets the {@link GrpcExecutionStrategy} for all clients created from this builder.
     *
     * @param strategy {@link GrpcExecutionStrategy} to use.
     * @return {@code this}.
     */
    BaseGrpcClientBuilder<U, R> executionStrategy(GrpcExecutionStrategy strategy);

    /**
     * Add a {@link SocketOption} for all clients created by this builder.
     *
     * @param option the option to apply.
     * @param value the value.
     * @param <T> the type of the value.
     * @return {@code this}.
     * @see StandardSocketOptions
     * @see ServiceTalkSocketOptions
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
     * Sets a {@link TransportObserver} that provides visibility into transport events.
     *
     * @param transportObserver A {@link TransportObserver} that provides visibility into transport events.
     * @return {@code this}.
     */
    BaseGrpcClientBuilder<U, R> transportObserver(TransportObserver transportObserver);

    /**
     * Configurations of various underlying protocol versions.
     * <p>
     * <b>Note:</b> the order of specified protocols will reflect on priorities for ALPN in case the connections are
     * secured.
     *
     * @param protocols {@link HttpProtocolConfig} for each protocol that should be supported.
     * @return {@code this}.
     */
    BaseGrpcClientBuilder<U, R> protocols(HttpProtocolConfig... protocols);

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
     *
     * @param factory {@link StreamingHttpConnectionFilterFactory} to decorate a {@link StreamingHttpConnection} for the
     * purpose of filtering.
     * @return {@code this}.
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
     *
     * @param predicate the {@link Predicate} to test if the filter must be applied.
     * @param factory {@link StreamingHttpConnectionFilterFactory} to decorate a {@link StreamingHttpConnection} for the
     * purpose of filtering.
     * @return {@code this}.
     */
    // We don't want the user to be able to override but it cannot be final because we need to override the type.
    // However this class is package private so the user will not be able to override this method.
    BaseGrpcClientBuilder<U, R> appendConnectionFilter(
            Predicate<StreamingHttpRequest> predicate, StreamingHttpConnectionFilterFactory factory);
}
