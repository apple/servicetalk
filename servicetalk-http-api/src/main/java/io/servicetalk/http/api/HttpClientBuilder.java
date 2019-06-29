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
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.IoExecutor;

import java.net.SocketOption;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.StrategyInfluencerAwareConversions.toConditionalClientFilterFactory;

/**
 * A builder of {@link HttpClient} objects.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 * @param <SDE> the type of {@link ServiceDiscovererEvent}
 */
abstract class HttpClientBuilder<U, R, SDE extends ServiceDiscovererEvent<R>> extends BaseHttpBuilder<R> {

    @Override
    public abstract HttpClientBuilder<U, R, SDE> ioExecutor(IoExecutor ioExecutor);

    @Override
    public abstract HttpClientBuilder<U, R, SDE> executionStrategy(HttpExecutionStrategy strategy);

    @Override
    public abstract HttpClientBuilder<U, R, SDE> bufferAllocator(BufferAllocator allocator);

    @Override
    public abstract <T> HttpClientBuilder<U, R, SDE> socketOption(SocketOption<T> option, T value);

    @Override
    public abstract HttpClientBuilder<U, R, SDE> enableWireLogging(String loggerName);

    @Override
    public abstract HttpClientBuilder<U, R, SDE> disableWireLogging();

    @Override
    public abstract HttpClientBuilder<U, R, SDE> headersFactory(HttpHeadersFactory headersFactory);

    @Override
    protected abstract HttpClientBuilder<U, R, SDE> h2HeadersFactory(HttpHeadersFactory headersFactory);

    @Override
    protected abstract HttpClientBuilder<U, R, SDE> h2PriorKnowledge(boolean h2PriorKnowledge);

    @Override
    protected abstract HttpClientBuilder<U, R, SDE> h2FrameLogger(@Nullable String h2FrameLogger);

    @Override
    public abstract HttpClientBuilder<U, R, SDE> maxInitialLineLength(int maxInitialLineLength);

    @Override
    public abstract HttpClientBuilder<U, R, SDE> maxHeaderSize(int maxHeaderSize);

    @Override
    public abstract HttpClientBuilder<U, R, SDE> headersEncodedSizeEstimate(int headersEncodedSizeEstimate);

    @Override
    public abstract HttpClientBuilder<U, R, SDE> trailersEncodedSizeEstimate(int trailersEncodedSizeEstimate);

    @Override
    public abstract HttpClientBuilder<U, R, SDE> maxPipelinedRequests(int maxPipelinedRequests);

    @Override
    public abstract HttpClientBuilder<U, R, SDE> appendConnectionFilter(StreamingHttpConnectionFilterFactory factory);

    @Override
    public HttpClientBuilder<U, R, SDE> appendConnectionFilter(Predicate<StreamingHttpRequest> predicate,
                                                               StreamingHttpConnectionFilterFactory factory) {
        super.appendConnectionFilter(predicate, factory);
        return this;
    }

    /**
     * Append the filter to the chain of filters used to decorate the {@link ConnectionFactory} used by this
     * builder.
     * <p>
     * Filtering allows you to wrap a {@link ConnectionFactory} and modify behavior of
     * {@link ConnectionFactory#newConnection(Object)}.
     * Some potential candidates for filtering include logging and metrics.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * Calling {@link ConnectionFactory} wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; original connection factory
     * </pre>
     * @param factory {@link ConnectionFactoryFilter} to use.
     * @return {@code this}
     */
    public abstract HttpClientBuilder<U, R, SDE> appendConnectionFactoryFilter(
            ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> factory);

    /**
     * Append the filter to the chain of filters used to decorate the {@link HttpClient} created by this
     * builder.
     * <p>
     * Note this method will be used to decorate the result of {@link #build()} before it is
     * returned to the user.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * making a request to a client wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; client
     * </pre>
     *
     * @param factory {@link StreamingHttpClientFilterFactory} to decorate a {@link HttpClient} for the purpose of
     * filtering.
     * @return {@code this}
     */
    public abstract HttpClientBuilder<U, R, SDE> appendClientFilter(StreamingHttpClientFilterFactory factory);

    /**
     * Append the filter to the chain of filters used to decorate the {@link HttpClient} created by this
     * builder, for every request that passes the provided {@link Predicate}.
     * <p>
     * Note this method will be used to decorate the result of {@link #build()} before it is
     * returned to the user.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * making a request to a client wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; client
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

    @Override
    public abstract HttpClientBuilder<U, R, SDE> disableHostHeaderFallback();

    /**
     * Provides a means to convert {@link U} unresolved address type into a {@link CharSequence}.
     * An example of where this maybe used is to convert the {@link U} to a default host header. It may also
     * be used in the event of proxying.
     *
     * @param unresolvedAddressToHostFunction invoked to convert the {@link U} unresolved address type into a
     * {@link CharSequence} suitable for use in
     * <a href="https://tools.ietf.org/html/rfc7230#section-5.4">Host Header</a> format.
     * @return {@code this}
     */
    public abstract HttpClientBuilder<U, R, SDE> unresolvedAddressToHost(
            Function<U, CharSequence> unresolvedAddressToHostFunction);

    /**
     * Disable automatically delaying {@link StreamingHttpRequest}s until the {@link LoadBalancer} is ready.
     *
     * @return {@code this}
     */
    public abstract HttpClientBuilder<U, R, SDE> disableWaitForLoadBalancer();

    /**
     * Set a {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * @param serviceDiscoverer The {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * Lifecycle of the provided {@link ServiceDiscoverer} is managed externally and it should be
     * {@link ServiceDiscoverer#closeAsync() closed} after all built {@link StreamingHttpClient}s will be closed and
     * this {@link ServiceDiscoverer} is no longer needed.
     * @return {@code this}.
     */
    public abstract HttpClientBuilder<U, R, SDE> serviceDiscoverer(
            ServiceDiscoverer<U, R, ? extends SDE> serviceDiscoverer);

    /**
     * Set a {@link LoadBalancerFactory} to generate {@link LoadBalancer} objects.
     *
     * @param loadBalancerFactory The {@link LoadBalancerFactory} which generates {@link LoadBalancer} objects.
     * @return {@code this}.
     */
    public abstract HttpClientBuilder<U, R, SDE> loadBalancerFactory(
            LoadBalancerFactory<R, StreamingHttpConnection> loadBalancerFactory);

    /**
     * Build a new {@link StreamingHttpClient}, using a default {@link ExecutionContext}.
     *
     * @return A new {@link StreamingHttpClient}
     */
    public abstract StreamingHttpClient buildStreaming();

    /**
     * Build a new {@link HttpClient}, using a default {@link ExecutionContext}.
     *
     * @return A new {@link HttpClient}
     */
    public final HttpClient build() {
        return buildStreaming().asClient();
    }

    /**
     * Create a new {@link BlockingStreamingHttpClient}, using a default {@link ExecutionContext}.
     *
     * @return {@link BlockingStreamingHttpClient}
     */
    public final BlockingStreamingHttpClient buildBlockingStreaming() {
        return buildStreaming().asBlockingStreamingClient();
    }

    /**
     * Create a new {@link BlockingHttpClient}, using a default {@link ExecutionContext}.
     *
     * @return {@link BlockingHttpClient}
     */
    public final BlockingHttpClient buildBlocking() {
        return buildStreaming().asBlockingClient();
    }
}
