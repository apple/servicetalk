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
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;

import java.net.SocketOption;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.StrategyInfluencerAwareConversions.toMultiAddressConditionalFilterFactory;
import static java.util.Objects.requireNonNull;

/**
 * A builder of {@link StreamingHttpClient} instances which have a capacity to call any server based on the parsed
 * absolute-form URL address information from each {@link StreamingHttpRequest}.
 * <p>
 * It also provides a good set of default settings and configurations, which could be used by most users as-is or
 * could be overridden to address specific use cases.
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 * @see <a href="https://tools.ietf.org/html/rfc7230#section-5.3.2">absolute-form rfc7230#section-5.3.2</a>
 */
public abstract class MultiAddressHttpClientBuilder<U, R>
        extends HttpClientBuilder<U, R, ServiceDiscovererEvent<R>> {
    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> ioExecutor(IoExecutor ioExecutor);

    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> executionStrategy(HttpExecutionStrategy strategy);

    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> bufferAllocator(BufferAllocator allocator);

    @Override
    public abstract <T> MultiAddressHttpClientBuilder<U, R> socketOption(SocketOption<T> option, T value);

    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> enableWireLogging(String loggerName);

    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> disableWireLogging();

    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> headersFactory(HttpHeadersFactory headersFactory);

    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> maxInitialLineLength(int maxInitialLineLength);

    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> maxHeaderSize(int maxHeaderSize);

    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> headersEncodedSizeEstimate(int headersEncodedSizeEstimate);

    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> trailersEncodedSizeEstimate(int trailersEncodedSizeEstimate);

    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> maxPipelinedRequests(int maxPipelinedRequests);

    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> disableHostHeaderFallback();

    /**
     * Sets a function that is used to determine the scheme of a request if the request-target is not absolute-form.
     *
     * @param effectiveSchemeFunction the function to use.
     * @return {@code this}
     */
    public abstract MultiAddressHttpClientBuilder<U, R> effectiveScheme(
            Function<HttpRequestMetaData, String> effectiveSchemeFunction);

    /**
     * Sets an operator that is used for configuring SSL/TLS for https requests. This is not required to enable SSL/TLS,
     * only to customize the configuration. Pass {@code null} for {@code sslConfigOperator} to un-set any previously set
     * function and use the default SSL/TLS configuration instead.
     * <p>
     * Note: Returning {@code null} from the operator will result in SSL/TLS <b>not</b> being configured.
     *
     * @param sslConfigOperator The operator to use for configuring SSL/TLS for https requests.
     * @return {@code this}
     */
    public abstract MultiAddressHttpClientBuilder<U, R> configureSsl(
            UnaryOperator<ClientSslConfigBuilder<?>> sslConfigOperator);

    /**
     * Sets a configurator that is called immediately before the {@link SingleAddressHttpClientBuilder} for any
     * {@link HostAndPort} is built, to configure the builder.
     *
     * @param clientConfiguratorForHost The configurator.
     * @return this.
     */
    public abstract MultiAddressHttpClientBuilder<U, R> clientConfiguratorForHost(
            @Nullable BiFunction<HostAndPort, SingleAddressHttpClientBuilder<U, R>,
                    SingleAddressHttpClientBuilder<U, R>> clientConfiguratorForHost);

    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> appendConnectionFilter(
            StreamingHttpConnectionFilterFactory factory);

    @Override
    public MultiAddressHttpClientBuilder<U, R> appendConnectionFilter(Predicate<StreamingHttpRequest> predicate,
                                                                      StreamingHttpConnectionFilterFactory factory) {
        return (MultiAddressHttpClientBuilder<U, R>) super.appendConnectionFilter(predicate, factory);
    }

    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> appendConnectionFactoryFilter(
            ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> factory);

    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> disableWaitForLoadBalancer();

    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> serviceDiscoverer(
            ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer);

    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> loadBalancerFactory(
            LoadBalancerFactory<R, StreamingHttpConnection> loadBalancerFactory);

    @Override
    public abstract MultiAddressHttpClientBuilder<U, R> unresolvedAddressToHost(
            Function<U, CharSequence> unresolvedAddressToHostFunction);

    /**
     * Append the filter to the chain of filters used to decorate the {@link StreamingHttpClient} created by this
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
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; client
     * </pre>
     * @param factory {@link MultiAddressHttpClientFilterFactory} to decorate a {@link StreamingHttpClient} for the
     * purpose of filtering.
     * @return {@code this}
     */
    public abstract MultiAddressHttpClientBuilder<U, R> appendClientFilter(
            MultiAddressHttpClientFilterFactory<U> factory);

    /**
     * Append the filter to the chain of filters used to decorate the {@link StreamingHttpClient} created by this
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
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; client
     * </pre>
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
     * Set a maximum number of redirects to follow.
     *
     * @param maxRedirects A maximum number of redirects to follow. Use a nonpositive number to disable redirects.
     * @return {@code this}.
     */
    public abstract MultiAddressHttpClientBuilder<U, R> maxRedirects(int maxRedirects);
}
