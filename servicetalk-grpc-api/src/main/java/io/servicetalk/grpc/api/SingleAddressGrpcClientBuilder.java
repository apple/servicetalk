/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.AutoRetryStrategyProvider;
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.BiIntPredicate;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.TransportObserver;

import java.net.SocketOption;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

interface SingleAddressGrpcClientBuilder<U, R,
        SDE extends ServiceDiscovererEvent<R>> extends BaseGrpcClientBuilder<U, R> {

    @Override
    SingleAddressGrpcClientBuilder<U, R, SDE> ioExecutor(IoExecutor ioExecutor);

    @Override
    SingleAddressGrpcClientBuilder<U, R, SDE> bufferAllocator(BufferAllocator allocator);

    @Override
    SingleAddressGrpcClientBuilder<U, R, SDE> executionStrategy(GrpcExecutionStrategy strategy);

    @Override
    <T> SingleAddressGrpcClientBuilder<U, R, SDE> socketOption(SocketOption<T> option, T value);

    @Override
    SingleAddressGrpcClientBuilder<U, R, SDE> enableWireLogging(String loggerName, LogLevel logLevel,
                                                                BooleanSupplier logUserData);

    @Override
    SingleAddressGrpcClientBuilder<U, R, SDE> protocols(HttpProtocolConfig... protocols);

    @Override
    SingleAddressGrpcClientBuilder<U, R, SDE> defaultTimeout(Duration defaultTimeout);

    /**
     * Append the filter to the chain of filters used to decorate the {@link ConnectionFactory} used by this
     * builder.
     * <p>
     * Filtering allows you to wrap a {@link ConnectionFactory} and modify behavior of
     * {@link ConnectionFactory#newConnection(Object, TransportObserver)}.
     * Some potential candidates for filtering include logging and metrics.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * Calling {@link ConnectionFactory} wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ original connection factory
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
     * Set the SSL/TLS configuration.
     * @param sslConfig The configuration to use.
     * @return {@code this}.
     */
    SingleAddressGrpcClientBuilder<U, R, SDE> sslConfig(ClientSslConfig sslConfig);

    /**
     * Toggle inference of value to use instead of {@link ClientSslConfig#peerHost()}
     * from client's address when peer host is not specified. By default, inference is enabled.
     * @param shouldInfer value indicating whether inference is on ({@code true}) or off ({@code false}).
     * @return {@code this}
     */
    SingleAddressGrpcClientBuilder<U, R, SDE> inferPeerHost(boolean shouldInfer);

    /**
     * Toggle inference of value to use instead of {@link ClientSslConfig#peerPort()}
     * from client's address when peer port is not specified (equals {@code -1}). By default, inference is enabled.
     * @param shouldInfer value indicating whether inference is on ({@code true}) or off ({@code false}).
     * @return {@code this}
     */
    SingleAddressGrpcClientBuilder<U, R, SDE> inferPeerPort(boolean shouldInfer);

    /**
     * Toggle <a href="https://datatracker.ietf.org/doc/html/rfc6066#section-3">SNI</a>
     * hostname inference from client's address if not explicitly specified
     * via {@link #sslConfig(ClientSslConfig)}. By default, inference is enabled.
     * @param shouldInfer value indicating whether inference is on ({@code true}) or off ({@code false}).
     * @return {@code this}
     */
    SingleAddressGrpcClientBuilder<U, R, SDE> inferSniHostname(boolean shouldInfer);

    /**
     * Updates the automatic retry strategy for the clients generated by this builder. Automatic retries are done by
     * the clients automatically when allowed by the passed {@link AutoRetryStrategyProvider}. These retries are not a
     * substitute for user level retries which are designed to infer retry decisions based on request/error information.
     * Typically such user level retries are done using filters but can also be done differently per request
     * (eg: by using {@link Single#retry(BiIntPredicate)}).
     *
     * @param autoRetryStrategyProvider {@link AutoRetryStrategyProvider} for the automatic retry strategy.
     * @return {@code this}
     */
    SingleAddressGrpcClientBuilder<U, R, SDE> autoRetryStrategy(AutoRetryStrategyProvider autoRetryStrategyProvider);

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
    SingleAddressGrpcClientBuilder<U, R, SDE> unresolvedAddressToHost(
            Function<U, CharSequence> unresolvedAddressToHostFunction);

    /**
     * Disables automatically setting {@code Host} headers by inferring from the address or {@link HttpMetaData}.
     * <p>
     * This setting disables the default filter such that no {@code Host} header will be manipulated.
     *
     * @return {@code this}
     * @see #unresolvedAddressToHost(Function)
     */
    SingleAddressGrpcClientBuilder<U, R, SDE> disableHostHeaderFallback();

    /**
     * Set a {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * @param serviceDiscoverer The {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * Lifecycle of the provided {@link ServiceDiscoverer} is managed externally and it should be
     * {@link ServiceDiscoverer#closeAsync() closed} after all built {@link GrpcClient}s are closed and
     * this {@link ServiceDiscoverer} is no longer needed.
     * @return {@code this}.
     */
    SingleAddressGrpcClientBuilder<U, R, SDE> serviceDiscoverer(ServiceDiscoverer<U, R, SDE> serviceDiscoverer);

    /**
     * Set a {@link HttpLoadBalancerFactory} to create {@link LoadBalancer} instances.
     *
     * @param loadBalancerFactory {@link HttpLoadBalancerFactory} to create {@link LoadBalancer} instances.
     * @return {@code this}.
     */
    SingleAddressGrpcClientBuilder<U, R, SDE> loadBalancerFactory(HttpLoadBalancerFactory<R> loadBalancerFactory);
}
