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

import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.SslConfig;

import java.net.SocketOption;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * A builder of {@link StreamingHttpClient} instances which have a capacity to call any server based on the parsed absolute-form
 * URL address information from each {@link StreamingHttpRequest}.
 * <p>
 * It also provides a good set of default settings and configurations, which could be used by most users as-is or
 * could be overridden to address specific use cases.
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 * @see <a href="https://tools.ietf.org/html/rfc7230#section-5.3.2">absolute-form rfc7230#section-5.3.2</a>
 */
public interface MultiAddressHttpClientBuilder<U, R> extends HttpClientBuilder<U, R> {

    @Override
    MultiAddressHttpClientBuilder<U, R> executionContext(ExecutionContext context);

    @Override
    <T> MultiAddressHttpClientBuilder<U, R> socketOption(SocketOption<T> option, T value);

    @Override
    MultiAddressHttpClientBuilder<U, R> enableWireLogging(String loggerName);

    @Override
    MultiAddressHttpClientBuilder<U, R> disableWireLogging();

    @Override
    MultiAddressHttpClientBuilder<U, R> headersFactory(HttpHeadersFactory headersFactory);

    @Override
    MultiAddressHttpClientBuilder<U, R> maxInitialLineLength(int maxInitialLineLength);

    @Override
    MultiAddressHttpClientBuilder<U, R> maxHeaderSize(int maxHeaderSize);

    @Override
    MultiAddressHttpClientBuilder<U, R> headersEncodedSizeEstimate(int headersEncodedSizeEstimate);

    @Override
    MultiAddressHttpClientBuilder<U, R> trailersEncodedSizeEstimate(int trailersEncodedSizeEstimate);

    @Override
    MultiAddressHttpClientBuilder<U, R> maxPipelinedRequests(int maxPipelinedRequests);

    @Override
    MultiAddressHttpClientBuilder<U, R> appendConnectionFilter(HttpConnectionFilterFactory factory);

    @Override
    MultiAddressHttpClientBuilder<U, R> disableHostHeaderFallback();

    @Override
    MultiAddressHttpClientBuilder<U, R> disableWaitForLoadBalancer();

    @Override
    MultiAddressHttpClientBuilder<U, R> serviceDiscoverer(
            ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer);

    @Override
    MultiAddressHttpClientBuilder<U, R> loadBalancerFactory(
            LoadBalancerFactory<R, StreamingHttpConnection> loadBalancerFactory);

    /**
     * Automatically set the provided {@link HttpHeaderNames#HOST} on {@link StreamingHttpRequest}s when it's missing.
     * <p>
     * For known address types such as {@link HostAndPort} the {@link HttpHeaderNames#HOST} is inferred and
     * automatically set by default, if you have a custom address type or want to override the inferred value use this
     * method. Use {@link #disableHostHeaderFallback()} if you don't want any {@link HttpHeaderNames#HOST} manipulation
     * at all.
     * @param hostHeaderTransformer transforms the {@code UnresolvedAddress} for the {@link HttpHeaderNames#HOST}
     * @return {@code this}
     */
    MultiAddressHttpClientBuilder<U, R> enableHostHeaderFallback(Function<U, CharSequence> hostHeaderTransformer);

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
     * @param function {@link HttpClientGroupFilterFactory} to decorate a {@link StreamingHttpClient} for the purpose
     * of filtering.
     * @return {@code this}
     */
    MultiAddressHttpClientBuilder<U, R> appendClientFilter(HttpClientGroupFilterFactory<U> function);

    /**
     * Append the filter to the chain of filters used to decorate the {@link StreamingHttpClientGroup} created by this
     * builder.
     * <p>
     * Filtering allows you to wrap {@link StreamingHttpClientGroup} and modify behavior during request/response
     * processing.
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * Making a request to a client wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; client
     * </pre>
     * @param function A {@link UnaryOperator} to decorate {@link StreamingHttpClientGroup} for the purpose of
     * filtering.
     * @return {@code this}.
     */
    MultiAddressHttpClientBuilder<U, R> appendClientGroupFilter(ClientGroupFilterFunction<U> function);

    /**
     * Set a {@link SslConfigProvider} for appropriate {@link SslConfig}s.
     *
     * @param sslConfigProvider A {@link SslConfigProvider} to use.
     * @return {@code this}.
     */
    MultiAddressHttpClientBuilder<U, R> sslConfigProvider(SslConfigProvider sslConfigProvider);

    /**
     * Set a maximum number of redirects to follow.
     *
     * @param maxRedirects A maximum number of redirects to follow. Use a nonpositive number to disable redirects.
     * @return {@code this}.
     */
    MultiAddressHttpClientBuilder<U, R> maxRedirects(int maxRedirects);
}
