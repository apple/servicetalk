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
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.SslConfig;

import java.io.InputStream;
import java.net.SocketOption;
import java.time.Duration;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A builder of {@link RedisClient} objects.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
public interface RedisClientBuilder<U, R> {

    /**
     * Sets the {@link IoExecutor} for all clients created from this {@link RedisClientBuilder}.
     *
     * @param ioExecutor {@link IoExecutor} to use.
     * @return {@code this}.
     */
    RedisClientBuilder<U, R> ioExecutor(IoExecutor ioExecutor);

    /**
     * Sets the {@link RedisExecutionStrategy} for all clients created from this {@link RedisClientBuilder}.
     *
     * @param strategy {@link RedisExecutionStrategy} to use.
     * @return {@code this}.
     */
    RedisClientBuilder<U, R> executionStrategy(RedisExecutionStrategy strategy);

    /**
     * Sets the {@link BufferAllocator} for all clients created from this {@link RedisClientBuilder}.
     *
     * @param allocator {@link BufferAllocator} to use.
     * @return {@code this}.
     */
    RedisClientBuilder<U, R> bufferAllocator(BufferAllocator allocator);

    /**
     * Enable SSL/TLS using the provided {@link SslConfig}. To disable SSL pass in {@code null}.
     * @param config the {@link SslConfig}.
     * @return {@code this}.
     * @throws IllegalStateException if accessing the cert/key throws when {@link InputStream#close()} is called.
     */
    RedisClientBuilder<U, R> sslConfig(@Nullable SslConfig config);

    /**
     * Add a {@link SocketOption} for all connections created by this builder.
     *
     * @param <T> the type of the value.
     * @param option the option to apply.
     * @param value the value.
     * @return {@code this}.
     */
    <T> RedisClientBuilder<U, R> socketOption(SocketOption<T> option, T value);

    /**
     * Enable wire-logging for connections created by this builder. All wire events will be logged at trace level.
     *
     * @param loggerName The name of the logger to log wire events.
     * @return {@code this}.
     */
    RedisClientBuilder<U, R> enableWireLogging(String loggerName);

    /**
     * Disable previously configured wire-logging for connections created by this builder.
     * If wire-logging has not been configured before, this method has no effect.
     *
     * @return {@code this}.
     * @see #enableWireLogging(String)
     */
    RedisClientBuilder<U, R> disableWireLogging();

    /**
     * Sets maximum requests that can be pipelined on a connection created by this builder.
     *
     * @param maxPipelinedRequests Maximum number of pipelined requests per {@link RedisConnection}.
     * @return {@code this}.
     */
    RedisClientBuilder<U, R> maxPipelinedRequests(int maxPipelinedRequests);

    /**
     * Sets the idle timeout for connections created by this builder.
     *
     * @param idleConnectionTimeout the timeout {@link Duration} or {@code null} if no timeout configured.
     * @return {@code this}.
     */
    RedisClientBuilder<U, R> idleConnectionTimeout(@Nullable Duration idleConnectionTimeout);

    /**
     * Sets the ping period to keep alive connections created by this builder.
     *
     * @param pingPeriod the {@link Duration} between keep-alive pings or {@code null} to disable pings.
     * @return {@code this}.
     */
    RedisClientBuilder<U, R> pingPeriod(@Nullable Duration pingPeriod);

    /**
     * Set the {@link RedisConnectionFilterFactory} which is used filter/decorate {@link RedisConnection} created by
     * this builder.
     * <p>
     * Filtering allows you to wrap a {@link RedisConnection} and modify behavior during request/response processing.
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     * @param factory {@link RedisConnectionFilterFactory} to decorate a {@link RedisConnection} for the
     * purpose of filtering.
     * @return {@code this}.
     */
    RedisClientBuilder<U, R> appendConnectionFilter(RedisConnectionFilterFactory factory);

    /**
     * Set the {@link RedisConnectionFilterFactory} which is used filter/decorate {@link RedisConnection} created by
     * this builder, for every request that passes the provided {@link Predicate}.
     * <p>
     * Filtering allows you to wrap a {@link RedisConnection} and modify behavior during request/response processing.
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     * @param predicate the {@link Predicate} to test if the filter must be applied.
     * @param factory {@link RedisConnectionFilterFactory} to decorate a {@link RedisConnection} for the
     * purpose of filtering.
     * @return {@code this}.
     */
    default RedisClientBuilder<U, R> appendConnectionFilter(Predicate<RedisRequest> predicate,
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
    RedisClientBuilder<U, R> appendConnectionFactoryFilter(ConnectionFactoryFilter<R, RedisConnection> factory);

    /**
     * Set the filter factory that is used to decorate {@link RedisClient} created by this builder.
     * <p>
     * Note this method will be used to decorate the result of {@link #build()} before it is
     * returned to the user.
     * @param factory factory to decorate a {@link RedisClient} for the purpose of filtering.
     * @return {@code this}
     */
    RedisClientBuilder<U, R> appendClientFilter(RedisClientFilterFactory factory);

    /**
     * Set the filter factory that is used to decorate {@link RedisClient} created by this builder,
     * for every request that passes the provided {@link Predicate}.
     * <p>
     * Note this method will be used to decorate the result of {@link #build()} before it is
     * returned to the user.
     * @param predicate the {@link Predicate} to test if the filter must be applied.
     * @param factory factory to decorate a {@link RedisClient} for the purpose of filtering.
     * @return {@code this}
     */
    default RedisClientBuilder<U, R> appendClientFilter(Predicate<RedisRequest> predicate,
                                                        RedisClientFilterFactory factory) {
        requireNonNull(predicate);
        requireNonNull(factory);

        return appendClientFilter((client, slbEvents, plbEvents) ->
                new ConditionalRedisClientFilter(predicate, factory.create(client, slbEvents, plbEvents), client));
    }

    /**
     * Disables automatically delaying {@link RedisRequest}s until the {@link LoadBalancer} is ready.
     *
     * @return {@code this}
     */
    RedisClientBuilder<U, R> disableWaitForLoadBalancer();

    /**
     * Set a {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * @param serviceDiscoverer The {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * Lifecycle of the provided {@link ServiceDiscoverer} is managed externally and it should be
     * {@link ServiceDiscoverer#closeAsync() closed} after all built {@link RedisClient}s will be closed and this
     * {@link ServiceDiscoverer} is no longer needed.
     * @return {@code this}.
     */
    RedisClientBuilder<U, R> serviceDiscoverer(
            ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer);

    /**
     * Set a {@link LoadBalancerFactory} to generate {@link LoadBalancer} objects.
     *
     * @param loadBalancerFactory The {@link LoadBalancerFactory} which generates {@link LoadBalancer} objects.
     * @return {@code this}.
     */
    RedisClientBuilder<U, R> loadBalancerFactory(LoadBalancerFactory<R, RedisConnection> loadBalancerFactory);

    /**
     * Build a new {@link RedisClient}.
     *
     * @return A new {@link RedisClient}.
     */
    RedisClient build();
}
