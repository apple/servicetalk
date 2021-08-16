/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.BiIntPredicate;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;
import io.servicetalk.transport.api.TransportObserver;

import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.servicetalk.http.api.StrategyInfluencerAwareConversions.toConditionalClientFilterFactory;
import static io.servicetalk.http.api.StrategyInfluencerAwareConversions.toConditionalConnectionFilterFactory;

/**
 * A builder of {@link HttpClient} objects.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 * @param <SDE> the type of {@link ServiceDiscovererEvent}
 */
abstract class HttpClientBuilder<U, R, SDE extends ServiceDiscovererEvent<R>>
        implements ExecutionContextAwareHttpBuilder, HttpClientBuildFinalizer {

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
    public abstract <T> HttpClientBuilder<U, R, SDE> socketOption(SocketOption<T> option, T value);

    /**
     * Enables wire-logging for connections created by this builder.
     *
     * @param loggerName The name of the logger to log wire events.
     * @param logLevel The level to log at.
     * @param logUserData {@code true} to include user data (e.g. data, headers, etc.). {@code false} to exclude user
     * data and log only network events.
     * @return {@code this}.
     */
    public abstract HttpClientBuilder<U, R, SDE> enableWireLogging(String loggerName,
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
    public abstract HttpClientBuilder<U, R, SDE> protocols(HttpProtocolConfig... protocols);

    /**
     * Disables automatically setting {@code Host} headers by inferring from the address or {@link HttpMetaData}.
     * <p>
     * This setting disables the default filter such that no {@code Host} header will be manipulated.
     *
     * @return {@code this}
     * @see SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)
     */
    public abstract HttpClientBuilder<U, R, SDE> disableHostHeaderFallback();

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
    public abstract HttpClientBuilder<U, R, SDE> allowDropResponseTrailers(boolean allowDrop);

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
     *     builder.appendConnectionFilter(filter1).appendConnectionFilter(filter2).appendConnectionFilter(filter3)
     * </pre>
     * making a request to a connection wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ connection
     * </pre>
     * @param factory {@link StreamingHttpConnectionFilterFactory} to decorate a {@link StreamingHttpConnection} for the
     * purpose of filtering.
     * @return {@code this}
     */
    public abstract HttpClientBuilder<U, R, SDE> appendConnectionFilter(
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
     *     builder.appendConnectionFilter(filter1).appendConnectionFilter(filter2).appendConnectionFilter(filter3)
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
    public /* final */ HttpClientBuilder<U, R, SDE> appendConnectionFilter(
            Predicate<StreamingHttpRequest> predicate, StreamingHttpConnectionFilterFactory factory) {
        return appendConnectionFilter(toConditionalConnectionFilterFactory(predicate, factory));
    }

    @Override
    public abstract HttpClientBuilder<U, R, SDE> ioExecutor(IoExecutor ioExecutor);

    @Override
    public abstract HttpClientBuilder<U, R, SDE> executionStrategy(HttpExecutionStrategy strategy);

    @Override
    public abstract HttpClientBuilder<U, R, SDE> bufferAllocator(BufferAllocator allocator);

    /**
     * Appends the filter to the chain of filters used to decorate the {@link ConnectionFactory} used by this
     * builder.
     * <p>
     * Filtering allows you to wrap a {@link ConnectionFactory} and modify behavior of
     * {@link ConnectionFactory#newConnection(Object, TransportObserver)}.
     * Some potential candidates for filtering include logging and metrics.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder
     *         .appendConnectionFactoryFilter(filter1)
     *         .appendConnectionFactoryFilter(filter2)
     *         .appendConnectionFactoryFilter(filter3)
     * </pre>
     * Calling {@link ConnectionFactory} wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ original connection factory
     * </pre>
     * @param factory {@link ConnectionFactoryFilter} to use.
     * @return {@code this}
     */
    public abstract HttpClientBuilder<U, R, SDE> appendConnectionFactoryFilter(
            ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> factory);

    /**
     * Appends the filter to the chain of filters used to decorate the {@link HttpClient} created by this
     * builder.
     * <p>
     * Note this method will be used to decorate the result of {@link #build()} before it is returned to the user.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.appendClientFilter(filter1).appendClientFilter(filter2).appendClientFilter(filter3)
     * </pre>
     * making a request to a client wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ client
     * </pre>
     *
     * @param factory {@link StreamingHttpClientFilterFactory} to decorate a {@link HttpClient} for the purpose of
     * filtering.
     * @return {@code this}
     */
    public abstract HttpClientBuilder<U, R, SDE> appendClientFilter(StreamingHttpClientFilterFactory factory);

    /**
     * Appends the filter to the chain of filters used to decorate the {@link HttpClient} created by this
     * builder, for every request that passes the provided {@link Predicate}.
     * <p>
     * Note this method will be used to decorate the result of {@link #build()} before it is
     * returned to the user.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.appendClientFilter(filter1).appendClientFilter(filter2).appendClientFilter(filter3)
     * </pre>
     * making a request to a client wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ client
     * </pre>
     *
     * @param predicate the {@link Predicate} to test if the filter must be applied.
     * @param factory {@link StreamingHttpClientFilterFactory} to decorate a {@link HttpClient} for the purpose of
     * filtering.
     * @return {@code this}
     */
    public HttpClientBuilder<U, R, SDE> appendClientFilter(Predicate<StreamingHttpRequest> predicate,
                                                           StreamingHttpClientFilterFactory factory) {
        return appendClientFilter(toConditionalClientFilterFactory(predicate, factory));
    }

    /**
     * Provides a means to convert {@link U} unresolved address type into a {@link CharSequence}.
     * An example of where this maybe used is to convert the {@link U} to a default host header. It may also
     * be used in the event of proxying.
     * @deprecated Will be moved to {@link SingleAddressHttpClientBuilder} otherwise use
     * {@link MultiAddressHttpClientBuilder.SingleAddressInitializer} or
     * {@link PartitionedHttpClientBuilder.SingleAddressInitializer}.
     * @param unresolvedAddressToHostFunction invoked to convert the {@link U} unresolved address type into a
     * {@link CharSequence} suitable for use in
     * <a href="https://tools.ietf.org/html/rfc7230#section-5.4">Host Header</a> format.
     * @return {@code this}
     */
    @Deprecated
    public abstract HttpClientBuilder<U, R, SDE> unresolvedAddressToHost(
            Function<U, CharSequence> unresolvedAddressToHostFunction);

    /**
     * Updates the automatic retry strategy for the clients generated by this builder. Automatic retries are done by
     * the clients automatically when allowed by the passed {@link AutoRetryStrategyProvider}. These retries are not a
     * substitute for user level retries which are designed to infer retry decisions based on request/error information.
     * Typically such user level retries are done using filters (eg:
     * {@link #appendClientFilter(StreamingHttpClientFilterFactory)}) but can also be done differently per request
     * (eg: by using {@link Single#retry(BiIntPredicate)}).
     *
     * @param autoRetryStrategyProvider {@link AutoRetryStrategyProvider} for the automatic retry strategy.
     * @return {@code this}
     */
    public abstract HttpClientBuilder<U, R, SDE> autoRetryStrategy(
            AutoRetryStrategyProvider autoRetryStrategyProvider);

    /**
     * Sets a {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     *
     * @param serviceDiscoverer The {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * Lifecycle of the provided {@link ServiceDiscoverer} is managed externally and it should be
     * {@link ServiceDiscoverer#closeAsync() closed} after all built {@link StreamingHttpClient}s will be closed and
     * this {@link ServiceDiscoverer} is no longer needed.
     * @return {@code this}.
     */
    public abstract HttpClientBuilder<U, R, SDE> serviceDiscoverer(
            ServiceDiscoverer<U, R, SDE> serviceDiscoverer);

    /**
     * Sets a retry strategy to retry errors emitted by {@link ServiceDiscoverer}.
     *
     * @param retryStrategy a retry strategy to retry errors emitted by {@link ServiceDiscoverer}.
     * @return {@code this}.
     * @see DefaultServiceDiscoveryRetryStrategy.Builder
     */
    public abstract HttpClientBuilder<U, R, SDE> retryServiceDiscoveryErrors(
            ServiceDiscoveryRetryStrategy<R, SDE> retryStrategy);

    /**
     * Sets a {@link HttpLoadBalancerFactory} to create {@link LoadBalancer} instances.
     *
     * @param loadBalancerFactory {@link HttpLoadBalancerFactory} to create {@link LoadBalancer} instances.
     * @return {@code this}.
     */
    public abstract HttpClientBuilder<U, R, SDE> loadBalancerFactory(HttpLoadBalancerFactory<R> loadBalancerFactory);
}
