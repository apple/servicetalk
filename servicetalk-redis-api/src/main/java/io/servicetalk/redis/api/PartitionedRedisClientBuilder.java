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
package io.servicetalk.redis.api;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.client.api.partition.PartitionMapFactory;
import io.servicetalk.client.api.partition.PartitionedServiceDiscovererEvent;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.SslConfig;

import org.reactivestreams.Subscriber;

import java.io.InputStream;
import java.net.SocketOption;
import java.time.Duration;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A builder of {@link PartitionedRedisClient} objects.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
public interface PartitionedRedisClientBuilder<U, R> {

    /**
     * Sets the {@link IoExecutor} for all clients created from this {@link RedisClientBuilder}.
     *
     * @param ioExecutor {@link IoExecutor} to use.
     * @return {@code this}.
     */
    PartitionedRedisClientBuilder<U, R> ioExecutor(IoExecutor ioExecutor);

    /**
     * Sets the {@link RedisExecutionStrategy} for all clients created from this {@link PartitionedRedisClientBuilder}.
     *
     * @param strategy {@link RedisExecutionStrategy} to use.
     * @return {@code this}.
     */
    PartitionedRedisClientBuilder<U, R> executionStrategy(RedisExecutionStrategy strategy);

    /**
     * Sets the {@link BufferAllocator} for all clients created from this {@link RedisClientBuilder}.
     *
     * @param allocator {@link BufferAllocator} to use.
     * @return {@code this}.
     */
    PartitionedRedisClientBuilder<U, R> bufferAllocator(BufferAllocator allocator);

    /**
     * Enable SSL/TLS using the provided {@link SslConfig}. To disable SSL pass in {@code null}.
     *
     * @param config the {@link SslConfig}.
     * @return {@code this}.
     * @throws IllegalStateException if accessing the cert/key throws when {@link InputStream#close()} is called.
     */
    PartitionedRedisClientBuilder<U, R> sslConfig(@Nullable SslConfig config);

    /**
     * Add a {@link SocketOption} for all connections created by this builder.
     *
     * @param <T> the type of the value.
     * @param option the option to apply.
     * @param value the value.
     * @return {@code this}.
     */
    <T> PartitionedRedisClientBuilder<U, R> socketOption(SocketOption<T> option, T value);

    /**
     * Enable wire-logging for connections created by this builder. All wire events will be logged at trace level.
     *
     * @param loggerName The name of the logger to log wire events.
     * @return {@code this}.
     */
    PartitionedRedisClientBuilder<U, R> enableWireLogging(String loggerName);

    /**
     * Disable previously configured wire-logging for connections created by this builder.
     * If wire-logging has not been configured before, this method has no effect.
     *
     * @return {@code this}.
     * @see #enableWireLogging(String)
     */
    PartitionedRedisClientBuilder<U, R> disableWireLogging();

    /**
     * Sets maximum requests that can be pipelined on a connection created by this builder.
     *
     * @param maxPipelinedRequests Maximum number of pipelined requests per {@link RedisConnection}.
     * @return {@code this}.
     */
    PartitionedRedisClientBuilder<U, R> maxPipelinedRequests(int maxPipelinedRequests);

    /**
     * Sets the idle timeout for connections created by this builder.
     *
     * @param idleConnectionTimeout the timeout {@link Duration} or {@code null} if no timeout configured.
     * @return {@code this}.
     */
    PartitionedRedisClientBuilder<U, R> idleConnectionTimeout(@Nullable Duration idleConnectionTimeout);

    /**
     * Sets the ping period to keep alive connections created by this builder.
     *
     * @param pingPeriod the {@link Duration} between keep-alive pings or {@code null} to disable pings.
     * @return {@code this}.
     */
    PartitionedRedisClientBuilder<U, R> pingPeriod(@Nullable Duration pingPeriod);

    /**
     * Sets the maximum amount of {@link ServiceDiscovererEvent} objects that will be queued for each partition.
     * <p>It is assumed that the {@link Subscriber}s will process events in a timely manner (typically synchronously)
     * so this typically doesn't need to be very large.
     *
     * @param serviceDiscoveryMaxQueueSize the maximum amount of {@link ServiceDiscovererEvent} objects that will be
     * queued for each partition.
     * @return {@code this}.
     */
    PartitionedRedisClientBuilder<U, R> serviceDiscoveryMaxQueueSize(int serviceDiscoveryMaxQueueSize);

    /**
     * Defines a filter {@link RedisClientFilterFactory} to decorate {@link RedisClient} used by this builder.
     * <p>
     * Filtering allows you to wrap a {@link RedisClient} and modify behavior during request/response processing.
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * sending a request with a client wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; client
     * </pre>
     *
     * @param factory {@link RedisClientFilterFactory} to filter the used {@link RedisClient}.
     * @return {@code this}.
     */
    PartitionedRedisClientBuilder<U, R> appendClientFilter(RedisClientFilterFactory factory);

    /**
     * Defines a filter {@link RedisClientFilterFactory} to decorate {@link RedisClient} used by this builder,
     * for every request that passes the provided {@link Predicate}.
     * <p>
     * Filtering allows you to wrap a {@link RedisClient} and modify behavior during request/response processing.
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * sending a request with a client wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; client
     * </pre>
     *
     * @param predicate the {@link Predicate} to test if the filter must be applied.
     * @param factory {@link RedisClientFilterFactory} to filter the used {@link RedisClient}.
     * @return {@code this}.
     */
    default PartitionedRedisClientBuilder<U, R> appendClientFilter(Predicate<RedisRequest> predicate,
                                                                   RedisClientFilterFactory factory) {
        requireNonNull(predicate);
        requireNonNull(factory);

        return appendClientFilter((client, slbEvents, plbEvents) ->
                new ConditionalRedisClientFilter(predicate, factory.create(client, slbEvents, plbEvents), client));
    }

    /**
     * Defines a filter {@link RedisConnectionFilterFactory} to decorate {@link RedisConnection} used by this builder.
     * <p>
     * Filtering allows you to wrap a {@link RedisConnection} and modify behavior during request/response processing.
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * sending a request through a connection wrapped by this filter chain, the order of invocation of these filters
     * will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; connection
     * </pre>
     *
     * @param factory {@link RedisConnectionFilterFactory} to filter the used {@link RedisConnection}.
     * @return {@code this}.
     */
    PartitionedRedisClientBuilder<U, R> appendConnectionFilter(RedisConnectionFilterFactory factory);

    /**
     * Defines a filter {@link RedisConnectionFilterFactory} to decorate {@link RedisConnection} used by this builder,
     * for every request that passes the provided {@link Predicate}.
     * <p>
     * Filtering allows you to wrap a {@link RedisConnection} and modify behavior during request/response processing.
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * sending a request through a connection wrapped by this filter chain, the order of invocation of these filters
     * will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; connection
     * </pre>
     *
     * @param predicate the {@link Predicate} to test if the filter must be applied.
     * @param factory {@link RedisConnectionFilterFactory} to filter the used {@link RedisConnection}.
     * @return {@code this}.
     */
    default PartitionedRedisClientBuilder<U, R> appendConnectionFilter(Predicate<RedisRequest> predicate,
                                                                       RedisConnectionFilterFactory factory) {
        requireNonNull(predicate);
        requireNonNull(factory);

        return appendConnectionFilter(cnx ->
                new ConditionalRedisConnectionFilter(predicate, factory.create(cnx), cnx));
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
    PartitionedRedisClientBuilder<U, R> appendConnectionFactoryFilter(
            ConnectionFactoryFilter<R, RedisConnection> factory);

    /**
     * Set the filter factory that is used to decorate {@link PartitionedRedisClient} created by this builder.
     * <p>
     * Note this method will be used to decorate the result of {@link #build()} before it is returned to the user.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * sending a request with a client wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; client
     * </pre>
     *
     * @param factory {@link PartitionedRedisClientFilterFactory} to filter the used
     * {@link PartitionedRedisClient}.
     * @return {@code this}.
     */
    PartitionedRedisClientBuilder<U, R> appendPartitionedFilter(PartitionedRedisClientFilterFactory factory);

    /**
     * Set the filter factory that is used to decorate {@link PartitionedRedisClient} created by this builder,
     * for every request that passes the provided {@link Predicate}.
     * <p>
     * Note this method will be used to decorate the result of {@link #build()} before it is returned to the user.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * sending a request with a client wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; client
     * </pre>
     *
     * @param predicate the {@link Predicate} to test if the filter must be applied.
     * @param factory {@link PartitionedRedisClientFilterFactory} to filter the used
     * {@link PartitionedRedisClient}.
     * @return {@code this}.
     */
    default PartitionedRedisClientBuilder<U, R> appendPartitionedFilter(Predicate<RedisRequest> predicate,
                                                                        PartitionedRedisClientFilterFactory factory) {
        requireNonNull(predicate);
        requireNonNull(factory);

        return appendPartitionedFilter(client ->
                new ConditionalPartitionedRedisClientFilter(predicate, factory.create(client), client));
    }

    /**
     * Disables automatically delaying {@link RedisRequest}s until the {@link LoadBalancer} is ready.
     *
     * @return {@code this}
     */
    PartitionedRedisClientBuilder<U, R> disableWaitForLoadBalancer();

    /**
     * Set a {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * @param serviceDiscoverer The {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * Lifecycle of the provided {@link ServiceDiscoverer} is managed externally and it should be
     * {@link ServiceDiscoverer#closeAsync() closed} after all built {@link RedisClient}s will be closed and this
     * {@link ServiceDiscoverer} is no longer needed.
     * @return {@code this}.
     */
    PartitionedRedisClientBuilder<U, R> serviceDiscoverer(
            ServiceDiscoverer<U, R, ? extends PartitionedServiceDiscovererEvent<R>> serviceDiscoverer);

    /**
     * Set a {@link LoadBalancerFactory} to generate {@link LoadBalancer} objects.
     *
     * @param factory The {@link LoadBalancerFactory} which generates {@link LoadBalancer} objects.
     * @return {@code this}.
     */
    PartitionedRedisClientBuilder<U, R> loadBalancerFactory(LoadBalancerFactory<R, RedisConnection> factory);

    /**
     * Set {@link PartitionMapFactory} to use by all {@link PartitionedRedisClient}s created by this builder.
     *
     * @param partitionMapFactory {@link PartitionMapFactory} to use.
     * @return {@code this}.
     */
    PartitionedRedisClientBuilder<U, R> partitionMapFactory(PartitionMapFactory partitionMapFactory);

    /**
     * Build a new {@link PartitionedRedisClient}.
     *
     * @return A new {@link PartitionedRedisClient}.
     */
    PartitionedRedisClient build();
}
