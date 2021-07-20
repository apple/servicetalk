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
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;

import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.servicetalk.http.api.StrategyInfluencerAwareConversions.toConditionalConnectionFilterFactory;

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
     * Adds a {@link SocketOption} for all connections created by this builder.
     *
     * @param option the option to apply.
     * @param value the value.
     * @param <T> the type of the value.
     * @return {@code this}.
     * @see StandardSocketOptions
     * @see ServiceTalkSocketOptions
     */
    public abstract <T> BaseHttpBuilder<ResolvedAddress> socketOption(SocketOption<T> option, T value);

    /**
     * Enables wire-logging for connections created by this builder.
     *
     * @param loggerName The name of the logger to log wire events.
     * @param logLevel The level to log at.
     * @param logUserData {@code true} to include user data (e.g. data, headers, etc.). {@code false} to exclude user
     * data and log only network events.
     * @return {@code this}.
     */
    public abstract BaseHttpBuilder<ResolvedAddress> enableWireLogging(String loggerName,
                                                                       LogLevel logLevel,
                                                                       BooleanSupplier logUserData);

    /**
     * Configurations of various HTTP protocol versions.
     * <p>
     * <b>Note:</b> the order of specified protocols will reflect on priorities for
     * <a href="https://tools.ietf.org/html/rfc7301">ALPN</a> in case the connections use TLS.
     *
     * @param protocols {@link HttpProtocolConfig} for each protocol that should be supported.
     * @return {@code this}.
     */
    public abstract BaseHttpBuilder<ResolvedAddress> protocols(HttpProtocolConfig... protocols);

    /**
     * Disables automatically setting {@code Host} headers by inferring from the address or {@link HttpMetaData}.
     * <p>
     * This setting disables the default filter such that no {@code Host} header will be manipulated.
     *
     * @return {@code this}
     * @see MultiAddressHttpClientBuilder#unresolvedAddressToHost(Function)
     */
    public abstract BaseHttpBuilder<ResolvedAddress> disableHostHeaderFallback();

    /**
     * Provide a hint if response <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailers</a> are allowed
     * to be dropped. This hint maybe ignored if the transport can otherwise infer that
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailers</a> should be preserved. For example, if the
     * response headers contain <a href="https://tools.ietf.org/html/rfc7230#section-4.4">Trailer</a> then this hint
     * maybe ignored.
     * @param allowDrop {@code true} if response
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailers</a> are allowed to be dropped.
     * @return {@code this}
     */
    public abstract BaseHttpBuilder<ResolvedAddress> allowDropResponseTrailers(boolean allowDrop);

    /**
     * Appends the filter to the chain of filters used to decorate the {@link StreamingHttpConnection} created by this
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
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ connection
     * </pre>
     * @param factory {@link StreamingHttpConnectionFilterFactory} to decorate a {@link StreamingHttpConnection} for the
     * purpose of filtering.
     * @return {@code this}
     */
    public abstract BaseHttpBuilder<ResolvedAddress> appendConnectionFilter(
            StreamingHttpConnectionFilterFactory factory);

    /**
     * Appends the filter to the chain of filters used to decorate the {@link StreamingHttpConnection} created by this
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
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ connection
     * </pre>
     * @param predicate the {@link Predicate} to test if the filter must be applied.
     * @param factory {@link StreamingHttpConnectionFilterFactory} to decorate a {@link StreamingHttpConnection} for the
     * purpose of filtering.
     * @return {@code this}
     */
    // We don't want the user to be able to override but it cannot be final because we need to override the type.
    // However this class is package private so the user will not be able to override this method.
    public /* final */ BaseHttpBuilder<ResolvedAddress> appendConnectionFilter(
            Predicate<StreamingHttpRequest> predicate, StreamingHttpConnectionFilterFactory factory) {
        return appendConnectionFilter(toConditionalConnectionFilterFactory(predicate, factory));
    }
}
