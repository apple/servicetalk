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

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.transport.api.ExecutionContext;

import java.net.SocketOption;
import java.util.function.Function;

/**
 * A builder of {@link HttpClient} objects.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
public interface HttpClientBuilder<U, R> {

    /**
     * Sets an {@link ExecutionContext} for all clients created from this {@link HttpClientBuilder}.
     *
     * @param context {@link ExecutionContext} to use.
     * @return {@code this}.
     */
    HttpClientBuilder<U, R> executionContext(ExecutionContext context);

    /**
     * Add a {@link SocketOption} for all connections created by this client.
     *
     * @param option the option to apply.
     * @param value the value.
     * @param <T> the type of the value.
     * @return {@code this}.
     */
    <T> HttpClientBuilder<U, R> socketOption(SocketOption<T> option, T value);

    /**
     * Enable wire-logging for connections created by this builder. All wire events will be logged at trace level.
     *
     * @param loggerName The name of the logger to log wire events.
     * @return {@code this}.
     */
    HttpClientBuilder<U, R> enableWireLogging(String loggerName);

    /**
     * Disable previously configured wire-logging for connections created by this builder.
     * If wire-logging has not been configured before, this method has no effect.
     *
     * @return {@code this}.
     * @see #enableWireLogging(String)
     */
    HttpClientBuilder<U, R> disableWireLogging();

    /**
     * Set the {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding responses.
     *
     * @param headersFactory the {@link HttpHeadersFactory} to use.
     * @return {@code this}.
     */
    HttpClientBuilder<U, R> headersFactory(HttpHeadersFactory headersFactory);

    /**
     * Set the maximum size of the initial HTTP line for created {@link StreamingHttpClient}.
     *
     * @param maxInitialLineLength The {@link StreamingHttpClient} will throw TooLongFrameException if the initial HTTP
     * line exceeds this length.
     * @return {@code this}.
     */
    HttpClientBuilder<U, R> maxInitialLineLength(int maxInitialLineLength);

    /**
     * Set the maximum total size of HTTP headers, which could be send be created {@link StreamingHttpClient}.
     *
     * @param maxHeaderSize The {@link StreamingHttpClient} will throw TooLongFrameException if the total size of all
     * HTTP headers exceeds this length.
     * @return {@code this}.
     */
    HttpClientBuilder<U, R> maxHeaderSize(int maxHeaderSize);

    /**
     * Set the value used to calculate an exponential moving average of the encoded size of the initial line and the
     * headers for a guess for future buffer allocations.
     *
     * @param headersEncodedSizeEstimate An estimated size of encoded initial line and headers.
     * @return {@code this}.
     */
    HttpClientBuilder<U, R> headersEncodedSizeEstimate(int headersEncodedSizeEstimate);

    /**
     * Set the value used to calculate an exponential moving average of the encoded size of the trailers for a guess for
     * future buffer allocations.
     *
     * @param trailersEncodedSizeEstimate An estimated size of encoded trailers.
     * @return {@code this}.
     */
    HttpClientBuilder<U, R> trailersEncodedSizeEstimate(int trailersEncodedSizeEstimate);

    /**
     * Set the maximum number of pipelined HTTP requests to queue up, anything above this will be rejected,
     * 1 means pipelining is disabled and requests and responses are processed sequentially.
     * <p>
     * Request pipelining requires HTTP 1.1.
     *
     * @param maxPipelinedRequests number of pipelined requests to queue up
     * @return {@code this}.
     */
    HttpClientBuilder<U, R> maxPipelinedRequests(int maxPipelinedRequests);

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
    HttpClientBuilder<U, R> appendConnectionFilter(HttpConnectionFilterFactory factory);

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
    HttpClientBuilder<U, R> appendConnectionFactoryFilter(ConnectionFactoryFilter<R, StreamingHttpConnection> factory);

    /**
     * Disable automatically setting {@code Host} headers by inferring from the address or {@link StreamingHttpRequest}.
     * <p>
     * This setting disables the default filter such that no {@code Host} header will be manipulated.
     * @return {@code this}
     * @see SingleAddressHttpClientBuilder#enableHostHeaderFallback(CharSequence)
     * @see MultiAddressHttpClientBuilder#enableHostHeaderFallback(Function)
     */
    HttpClientBuilder<U, R> disableHostHeaderFallback();

    /**
     * Disable automatically delaying {@link StreamingHttpRequest}s until the {@link LoadBalancer} is ready.
     *
     * @return {@code this}
     */
    HttpClientBuilder<U, R> disableWaitForLoadBalancer();

    /**
     * Set a {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * @param serviceDiscoverer The {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * Lifecycle of the provided {@link ServiceDiscoverer} is managed externally and it should be
     * {@link ServiceDiscoverer#closeAsync() closed} after all built {@link StreamingHttpClient}s will be closed and
     * this {@link ServiceDiscoverer} is no longer needed.
     * @return {@code this}.
     */
    HttpClientBuilder<U, R> serviceDiscoverer(
            ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer);

    /**
     * Set a {@link LoadBalancerFactory} to generate {@link LoadBalancer} objects.
     *
     * @param loadBalancerFactory The {@link LoadBalancerFactory} which generates {@link LoadBalancer} objects.
     * @return {@code this}.
     */
    HttpClientBuilder<U, R> loadBalancerFactory(LoadBalancerFactory<R, StreamingHttpConnection> loadBalancerFactory);

    /**
     * Build a new {@link StreamingHttpClient}, using a default {@link ExecutionContext}.
     *
     * @return A new {@link StreamingHttpClient}
     */
    StreamingHttpClient buildStreaming();

    /**
     * Build a new {@link HttpClient}, using a default {@link ExecutionContext}.
     *
     * @return A new {@link HttpClient}
     */
    default HttpClient build() {
        return buildStreaming().asClient();
    }

    /**
     * Create a new {@link BlockingStreamingHttpClient}, using a default {@link ExecutionContext}.
     *
     * @return {@link BlockingStreamingHttpClient}
     */
    default BlockingStreamingHttpClient buildBlockingStreaming() {
        return buildStreaming().asBlockingStreamingClient();
    }

    /**
     * Create a new {@link BlockingHttpClient}, using a default {@link ExecutionContext}.
     *
     * @return {@link BlockingHttpClient}
     */
    default BlockingHttpClient buildBlocking() {
        return buildStreaming().asBlockingClient();
    }
}
