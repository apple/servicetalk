/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.AutoRetryStrategyProvider;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.IoExecutor;

import java.net.SocketOption;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.servicetalk.http.api.StrategyInfluencerAwareConversions.toMultiAddressConditionalFilterFactory;
import static java.util.Objects.requireNonNull;

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
public abstract class MultiAddressHttpClientBuilder<U, R>
        extends HttpClientBuilder<U, R, ServiceDiscovererEvent<R>> {
    /**
     * Initializes the {@link SingleAddressHttpClientBuilder} for each new client.
     * @param <U> The unresolved address type.
     * @param <R> The resolved address type.
     */
    @FunctionalInterface
    public interface SingleAddressInitializer<U, R> {
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

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#ioExecutor(IoExecutor)} on the last argument of
     * {@link SingleAddressInitializer#initialize(String, Object, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> ioExecutor(IoExecutor ioExecutor);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#executionStrategy(HttpExecutionStrategy)} on the last argument of
     * {@link SingleAddressInitializer#initialize(String, Object, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> executionStrategy(HttpExecutionStrategy strategy);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#bufferAllocator(BufferAllocator)} on the last argument of
     * {@link SingleAddressInitializer#initialize(String, Object, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> bufferAllocator(BufferAllocator allocator);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#socketOption(SocketOption, Object)} on the last argument of
     * {@link SingleAddressInitializer#initialize(String, Object, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract <T> MultiAddressHttpClientBuilder<U, R> socketOption(SocketOption<T> option, T value);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#enableWireLogging(String, LogLevel, BooleanSupplier)} on the last argument
     * of {@link SingleAddressInitializer#initialize(String, Object, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> enableWireLogging(String loggerName,
                                                                          LogLevel logLevel,
                                                                          BooleanSupplier logUserData);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#protocols(HttpProtocolConfig...)} on the last argument of
     * {@link SingleAddressInitializer#initialize(String, Object, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> protocols(HttpProtocolConfig... protocols);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#disableHostHeaderFallback()} on the last argument of
     * {@link SingleAddressInitializer#initialize(String, Object, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> disableHostHeaderFallback();

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#allowDropResponseTrailers(boolean)} on the last argument of
     * {@link SingleAddressInitializer#initialize(String, Object, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> allowDropResponseTrailers(boolean allowDrop);

    /**
     * Set a function which can customize options for each {@link StreamingHttpClient} that is built.
     * @param initializer Initializes the {@link SingleAddressHttpClientBuilder} used to build new
     * {@link StreamingHttpClient}s.
     * @return {@code this}
     */
    public abstract MultiAddressHttpClientBuilder<U, R> initializer(SingleAddressInitializer<U, R> initializer);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#appendConnectionFilter(StreamingHttpConnectionFilterFactory)} on the last
     * argument of {@link SingleAddressInitializer#initialize(String, Object, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> appendConnectionFilter(
            StreamingHttpConnectionFilterFactory factory);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#appendConnectionFilter(Predicate, StreamingHttpConnectionFilterFactory)} on
     * the last argument of {@link SingleAddressInitializer#initialize(String, Object, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public MultiAddressHttpClientBuilder<U, R> appendConnectionFilter(Predicate<StreamingHttpRequest> predicate,
                                                                      StreamingHttpConnectionFilterFactory factory) {
        return (MultiAddressHttpClientBuilder<U, R>) super.appendConnectionFilter(predicate, factory);
    }

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#appendConnectionFactoryFilter(ConnectionFactoryFilter)} on the last
     * argument of {@link SingleAddressInitializer#initialize(String, Object, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> appendConnectionFactoryFilter(
            ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> factory);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#autoRetryStrategy(AutoRetryStrategyProvider)} on the last
     * argument of {@link SingleAddressInitializer#initialize(String, Object, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> autoRetryStrategy(
            AutoRetryStrategyProvider autoRetryStrategyProvider);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#serviceDiscoverer(ServiceDiscoverer)} on the last
     * argument of {@link SingleAddressInitializer#initialize(String, Object, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> serviceDiscoverer(
            ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#retryServiceDiscoveryErrors(ServiceDiscoveryRetryStrategy)} on the last
     * argument of {@link SingleAddressInitializer#initialize(String, Object, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> retryServiceDiscoveryErrors(
            ServiceDiscoveryRetryStrategy<R, ServiceDiscovererEvent<R>> retryStrategy);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#loadBalancerFactory(HttpLoadBalancerFactory)} on the last
     * argument of {@link SingleAddressInitializer#initialize(String, Object, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract
    MultiAddressHttpClientBuilder<U, R> loadBalancerFactory(HttpLoadBalancerFactory<R> loadBalancerFactory);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)} on the last
     * argument of {@link SingleAddressInitializer#initialize(String, Object, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> unresolvedAddressToHost(
            Function<U, CharSequence> unresolvedAddressToHostFunction);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#appendClientFilter(StreamingHttpClientFilterFactory)} on the last
     * argument of {@link SingleAddressInitializer#initialize(String, Object, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> appendClientFilter(StreamingHttpClientFilterFactory factory);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #initializer(SingleAddressInitializer)} and
     * {@link SingleAddressHttpClientBuilder#appendClientFilter(Predicate, StreamingHttpClientFilterFactory)} on the
     * last argument of {@link SingleAddressInitializer#initialize(String, Object, SingleAddressHttpClientBuilder)}.
     */
    @Deprecated
    @Override
    public MultiAddressHttpClientBuilder<U, R> appendClientFilter(final Predicate<StreamingHttpRequest> predicate,
                                                                  final StreamingHttpClientFilterFactory factory) {
        return (MultiAddressHttpClientBuilder<U, R>) super.appendClientFilter(predicate, factory);
    }

    /**
     * Appends the filter to the chain of filters used to decorate the {@link StreamingHttpClient} created by this
     * builder for a given {@code UnresolvedAddress}.
     * <p>
     * Note this method will be used to decorate the result of {@link #buildStreaming()} before it is
     * returned to the user.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * Making a request to a client wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ client
     * </pre>
     *
     * @param factory {@link MultiAddressHttpClientFilterFactory} to decorate a {@link StreamingHttpClient} for the
     * purpose of filtering.
     * @return {@code this}
     */
    public abstract MultiAddressHttpClientBuilder<U, R> appendClientFilter(
            MultiAddressHttpClientFilterFactory<U> factory);

    /**
     * Appends the filter to the chain of filters used to decorate the {@link StreamingHttpClient} created by this
     * builder for a given {@code UnresolvedAddress}, for every request that passes the provided {@link Predicate}.
     * <p>
     * Note this method will be used to decorate the result of {@link #buildStreaming()} before it is
     * returned to the user.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * Making a request to a client wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ client
     * </pre>
     *
     * @param predicate the {@link Predicate} to test if the filter must be applied.
     * @param factory {@link MultiAddressHttpClientFilterFactory} to decorate a {@link StreamingHttpClient} for the
     * purpose of filtering.
     * @return {@code this}
     */
    public MultiAddressHttpClientBuilder<U, R> appendClientFilter(Predicate<StreamingHttpRequest> predicate,
                                                                  MultiAddressHttpClientFilterFactory<U> factory) {
        requireNonNull(predicate);
        requireNonNull(factory);
        return appendClientFilter(toMultiAddressConditionalFilterFactory(predicate, factory));
    }

    /**
     * Sets a maximum number of redirects to follow.
     *
     * @param maxRedirects A maximum number of redirects to follow. {@code 0} disables redirects.
     * @return {@code this}.
     */
    public abstract MultiAddressHttpClientBuilder<U, R> maxRedirects(int maxRedirects);
}
