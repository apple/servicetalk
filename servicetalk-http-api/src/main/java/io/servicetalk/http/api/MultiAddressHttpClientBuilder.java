/*
 * Copyright © 2018-2019, 2021-2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.transport.api.IoExecutor;

import javax.annotation.Nullable;

/**
 * A builder of {@link StreamingHttpClient} instances which have a capacity to call any server based on the parsed
 * absolute-form URL address information from each {@link StreamingHttpRequest}.
 * <p>
 * It also provides a good set of default settings and configurations, which could be used by most users as-is or
 * could be overridden to address specific use cases.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 * @see <a href="https://tools.ietf.org/html/rfc7230#section-5.3.2">absolute-form rfc7230#section-5.3.2</a>
 */
public interface MultiAddressHttpClientBuilder<U, R> extends HttpClientBuilder<U, R, ServiceDiscovererEvent<R>> {
    /**
     * Initializes the {@link SingleAddressHttpClientBuilder} for each new client.
     * @param <U> The unresolved address type.
     * @param <R> The resolved address type.
     */
    @FunctionalInterface
    interface SingleAddressInitializer<U, R> {
        /**
         * Configures the passed {@link SingleAddressHttpClientBuilder} for the given {@code scheme} and
         * {@code address}.
         * @param scheme The scheme parsed from the request URI.
         * @param address The unresolved address.
         * @param builder The builder to customize and build a {@link StreamingHttpClient}.
         */
        void initialize(String scheme, U address, SingleAddressHttpClientBuilder<U, R> builder);

        /**
         * Appends the passed {@link SingleAddressInitializer} to this {@link SingleAddressInitializer} such that this
         * {@link SingleAddressInitializer} is applied first and then the passed {@link SingleAddressInitializer}.
         *
         * @param toAppend {@link SingleAddressInitializer} to append
         * @return A composite {@link SingleAddressInitializer} after the append operation.
         */
        default SingleAddressInitializer<U, R> append(SingleAddressInitializer<U, R> toAppend) {
            return (scheme, address, builder) -> {
                initialize(scheme, address, builder);
                toAppend.initialize(scheme, address, builder);
            };
        }
    }

    @Override
    MultiAddressHttpClientBuilder<U, R> ioExecutor(IoExecutor ioExecutor);

    @Override
    MultiAddressHttpClientBuilder<U, R> executor(Executor executor);

    /**
     * Sets the {@link HttpExecutionStrategy} to be used for client callbacks when executing client requests for all
     * clients created from this builder.
     * <p>
     * Provides the base execution strategy for all clients created from this builder and the default strategy for
     * the {@link SingleAddressHttpClientBuilder} used to construct client instances. The
     * {@link #initializer(SingleAddressInitializer)} may be used for some customization of the execution strategy for a
     * specific single address client instance, but may not reduce the offloading to be performed. Specifically, the
     * initializer may introduce additional offloading via
     * {@link SingleAddressHttpClientBuilder#executionStrategy(HttpExecutionStrategy)} and may add filters which
     * influence the computed execution strategy.
     * <p>
     * Specifying an execution strategy will affect the offloading used during the execution of client requests:
     * <dl>
     *     <dt>Unspecified or {@link HttpExecutionStrategies#defaultStrategy()}
     *     <dd>The resulting client instances will use the default safe strategy for each API variant and
     *     {@link SingleAddressHttpClientBuilder} instances generated will also have default strategy.
     *
     *     <dt>{@link HttpExecutionStrategies#offloadNone()}
     *     (or deprecated {@link HttpExecutionStrategies#offloadNever()})
     *     <dd>{@link SingleAddressHttpClientBuilder} instances created by the client will have a strategy of
     *     {@link HttpExecutionStrategies#offloadNone()}. {@link HttpExecutionStrategies#offloadNone()} execution
     *     strategy requires that filters and asynchronous callbacks
     *     <strong style="text-transform: uppercase;">must not</strong> ever block during the execution of client
     *     requests. An {@link #initializer(SingleAddressInitializer) initializer} may override to add offloads using
     *     {@link SingleAddressHttpClientBuilder#executionStrategy(HttpExecutionStrategy)}.
     *
     *     <dt>A custom execution strategy ({@link HttpExecutionStrategies#customStrategyBuilder()}) or
     *     {@link HttpExecutionStrategies#offloadAll()}
     *     <dd>{@link SingleAddressHttpClientBuilder} instances created by the client will start with the provided
     *     strategy and may add additional offloading as required by added filters.
     * </dl>
     * @param strategy {@link HttpExecutionStrategy} to use. If callbacks to the application code may block then those
     * callbacks must request to be offloaded.
     * @return {@code this}.
     * @see HttpExecutionStrategies
     */
    @Override
    MultiAddressHttpClientBuilder<U, R> executionStrategy(HttpExecutionStrategy strategy);

    @Override
    MultiAddressHttpClientBuilder<U, R> bufferAllocator(BufferAllocator allocator);

    /**
     * Sets the {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} for new requests.
     *
     * @param headersFactory {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} for new requests
     * @return {@code this}
     */
    default MultiAddressHttpClientBuilder<U, R> headersFactory(HttpHeadersFactory headersFactory) {
        // FIXME: 0.43 - remove default implementation
        throw new UnsupportedOperationException(
                "MultiAddressHttpClientBuilder#headersFactory(HttpHeadersFactory) is not supported by " + getClass());
    }

    /**
     * Set a function which can customize options for each {@link StreamingHttpClient} that is built.
     * @param initializer Initializes the {@link SingleAddressHttpClientBuilder} used to build new
     * {@link StreamingHttpClient}s. See {@link #executionStrategy(HttpExecutionStrategy)} for discussion of
     * restrictions on the use of {@link SingleAddressHttpClientBuilder#executionStrategy(HttpExecutionStrategy)}
     * within an initializer.
     *
     * @return {@code this}
     */
    MultiAddressHttpClientBuilder<U, R> initializer(SingleAddressInitializer<U, R> initializer);

    /**
     * Configures <a href="https://datatracker.ietf.org/doc/html/rfc7231#section-6.4">redirection</a> behavior.
     *
     * @param config {@link RedirectConfig} to configure redirection behavior. It can be used to tune what requests
     * should follow redirects and which parts of the original request (headers/payload body/trailers) should be
     * redirected to non-relative locations. Use {@code null} to disable redirects.
     * @return {@code this}.
     * @see RedirectConfigBuilder
     */
    MultiAddressHttpClientBuilder<U, R> followRedirects(@Nullable RedirectConfig config);

    /**
     * Configures the default port for the HTTP scheme if not explicitly provided as part of the
     * {@link HttpRequestMetaData#requestTarget()}.
     *
     * @param port the port that should be used if not explicitly provided for HTTP requests.
     * @return {@code this}.
     */
    default MultiAddressHttpClientBuilder<U, R> defaultHttpPort(int port) { // FIXME: 0.43 - remove default impl
        throw new UnsupportedOperationException("Setting defaultHttpPort is not yet supported by "
                + getClass().getName());
    }

    /**
     * Configures the default port for the HTTPS scheme if not explicitly provided as part of the
     * {@link HttpRequestMetaData#requestTarget()}.
     *
     * @param port the port that should be used if not explicitly provided for HTTPS requests.
     * @return {@code this}.
     */
    default MultiAddressHttpClientBuilder<U, R> defaultHttpsPort(int port) { // FIXME: 0.43 - remove default impl
        throw new UnsupportedOperationException("Setting defaultHttpsPort is not yet supported by "
                + getClass().getName());
    }
}
