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
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.transport.api.IoExecutor;

import java.net.SocketOption;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;

interface SingleAddressGrpcClientBuilder<U, R,
        SDE extends ServiceDiscovererEvent<R>> extends BaseGrpcClientBuilder<U, R> {

    @Override
    SingleAddressGrpcClientBuilder<U, R, SDE> ioExecutor(IoExecutor ioExecutor);

    @Override
    SingleAddressGrpcClientBuilder<U, R, SDE> bufferAllocator(BufferAllocator allocator);

    @Override
    SingleAddressGrpcClientBuilder<U, R, SDE> executionStrategy(HttpExecutionStrategy strategy);

    @Override
    <T> SingleAddressGrpcClientBuilder<U, R, SDE> socketOption(SocketOption<T> option, T value);

    @Override
    SingleAddressGrpcClientBuilder<U, R, SDE> enableWireLogging(String loggerName);

    @Override
    SingleAddressGrpcClientBuilder<U, R, SDE> disableWireLogging();

    @Override
    SingleAddressGrpcClientBuilder<U, R, SDE> headersFactory(HttpHeadersFactory headersFactory);

    @Override
    SingleAddressGrpcClientBuilder<U, R, SDE> h2HeadersFactory(HttpHeadersFactory headersFactory);

    @Override
    SingleAddressGrpcClientBuilder<U, R, SDE> h2PriorKnowledge(boolean h2PriorKnowledge);

    @Override
    SingleAddressGrpcClientBuilder<U, R, SDE> h2FrameLogger(@Nullable String h2FrameLogger);

    @Override
    SingleAddressGrpcClientBuilder<U, R, SDE> maxInitialLineLength(int maxInitialLineLength);

    @Override
    SingleAddressGrpcClientBuilder<U, R, SDE> maxHeaderSize(int maxHeaderSize);

    @Override
    SingleAddressGrpcClientBuilder<U, R, SDE> headersEncodedSizeEstimate(int headersEncodedSizeEstimate);

    @Override
    SingleAddressGrpcClientBuilder<U, R, SDE> trailersEncodedSizeEstimate(int trailersEncodedSizeEstimate);

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
     * @return {@code this}.
     */
    SingleAddressGrpcClientBuilder<U, R, SDE> appendConnectionFactoryFilter(
            ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> factory);

    @Override
    SingleAddressGrpcClientBuilder<U, R, SDE> appendConnectionFilter(StreamingHttpConnectionFilterFactory factory);

    @Override
    SingleAddressGrpcClientBuilder<U, R, SDE> appendConnectionFilter(Predicate<StreamingHttpRequest> predicate,
                                                                     StreamingHttpConnectionFilterFactory factory);

    /**
     * Initiate security configuration for this client. Calling
     * {@link GrpcClientSecurityConfigurator#commit()} on the returned {@link GrpcClientSecurityConfigurator} will
     * commit the configuration.
     *
     * @return {@link GrpcClientSecurityConfigurator} to configure security for this client. It is
     * mandatory to call {@link GrpcClientSecurityConfigurator#commit() commit} after all configuration is
     * done.
     */
    GrpcClientSecurityConfigurator<U, R> secure();

    /**
     * Set a {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * @param serviceDiscoverer The {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * Lifecycle of the provided {@link ServiceDiscoverer} is managed externally and it should be
     * {@link ServiceDiscoverer#closeAsync() closed} after all built {@link GrpcClient}s are closed and
     * this {@link ServiceDiscoverer} is no longer needed.
     * @return {@code this}.
     */
    SingleAddressGrpcClientBuilder<U, R, SDE> serviceDiscoverer(
            ServiceDiscoverer<U, R, ? extends SDE> serviceDiscoverer);

    /**
     * Set a {@link LoadBalancerFactory} to generate {@link LoadBalancer} objects.
     *
     * @param loadBalancerFactory The {@link LoadBalancerFactory} which generates {@link LoadBalancer} objects.
     * @param protocolBinder The {@link Function} that bridges the HTTP protocol to the {@link LoadBalancedConnection}
     * which exposes a {@link LoadBalancedConnection#score()} function which may inform the {@link LoadBalancer} created
     * from the provided {@code loadBalancerFactory} while making connection selection decisions.
     * @param <FLC> the type of {@link LoadBalancedConnection} after binding to the {@link
     * FilterableStreamingHttpConnection}.
     * @return {@code this}.
     */
    <FLC extends FilterableStreamingHttpConnection & LoadBalancedConnection>
    SingleAddressGrpcClientBuilder<U, R, SDE> loadBalancerFactory(
            LoadBalancerFactory<R> loadBalancerFactory,
            Function<FilterableStreamingHttpConnection, FLC> protocolBinder);
}
