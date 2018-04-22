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
package io.servicetalk.redis.netty;

import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer.Event;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisClientBuilder;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisProtocolSupport;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.SslConfig;

import java.io.InputStream;
import java.net.SocketOption;
import java.time.Duration;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.redis.netty.RedisUtils.isSubscribeModeCommand;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

/**
 * A builder for instances of {@link RedisClient}.
 * @param <ResolvedAddress> the type of address after resolution.
 */
public final class DefaultRedisClientBuilder<ResolvedAddress>
        implements RedisClientBuilder<ResolvedAddress, Event<ResolvedAddress>> {

    public static final Function<RedisConnection, RedisConnection> SELECTOR_FOR_REQUEST =
            conn -> ((LoadBalancedRedisConnection) conn).reserveForRequest() ? conn : null;
    public static final Function<RedisConnection, RedisConnection> SELECTOR_FOR_RESERVE =
            conn -> ((LoadBalancedRedisConnection) conn).tryReserve() ? conn : null;

    private final LoadBalancerFactory<ResolvedAddress, RedisConnection> loadBalancerFactory;
    private final RedisClientConfig config;
    private Function<RedisConnection, RedisConnection> connectionFilterFunction = identity();

    /**
     * Create a new instance.
     * @param loadBalancerFactory A factory which generates {@link LoadBalancer} objects.
     */
    public DefaultRedisClientBuilder(LoadBalancerFactory<ResolvedAddress, RedisConnection> loadBalancerFactory) {
        this(loadBalancerFactory, new RedisClientConfig(new TcpClientConfig(false)));
    }

    /**
     * Create a new instance.
     * @param loadBalancerFactory A factory which generates {@link LoadBalancer} objects.
     * @param config the {@link RedisClientConfig} to use as basis
     */
    DefaultRedisClientBuilder(LoadBalancerFactory<ResolvedAddress, RedisConnection> loadBalancerFactory,
                              RedisClientConfig config) {
        this.loadBalancerFactory = requireNonNull(loadBalancerFactory);
        this.config = requireNonNull(config);
    }

    /**
     * Sets {@link BufferAllocator} to use for all {@link RedisClient}s built by this builder.
     * @param allocator {@link BufferAllocator} to use.
     * @return {@code this}.
     */
    public DefaultRedisClientBuilder<ResolvedAddress> setBufferAllocator(BufferAllocator allocator) {
        config.getTcpClientConfig().setAllocator(allocator);
        return this;
    }

    /**
     * Enable SSL/TLS using the provided {@link SslConfig}. To disable SSL pass in {@code null}.
     * @param config the {@link SslConfig}.
     * @return {@code this}.
     * @throws IllegalStateException if accessing the cert/key throws when {@link InputStream#close()} is called.
     */
    public DefaultRedisClientBuilder<ResolvedAddress> setSsl(@Nullable SslConfig config) {
        this.config.getTcpClientConfig().setSslConfig(config);
        return this;
    }

    /**
     * Add a {@link SocketOption} for all connections created by this builder.
     *
     * @param <T> the type of the value.
     * @param option the option to apply.
     * @param value the value.
     * @return {@code this}.
     */
    public <T> DefaultRedisClientBuilder<ResolvedAddress> setOption(SocketOption<T> option, T value) {
        config.getTcpClientConfig().setOption(option, value);
        return this;
    }

    /**
     * Enables wire-logging for the connections created by this builder.
     *
     * @param loggerName Name of the logger.
     * @return {@code this}.
     */
    public DefaultRedisClientBuilder<ResolvedAddress> enableWireLog(String loggerName) {
        config.getTcpClientConfig().setWireLoggerName(loggerName);
        return this;
    }

    /**
     * Disabled wire-logging for the connections created by this builder.
     *
     * @return {@code this}.
     */
    public DefaultRedisClientBuilder<ResolvedAddress> disableWireLog() {
        config.getTcpClientConfig().disableWireLog();
        return this;
    }

    /**
     * Sets maximum requests that can be pipelined on a connection created by this builder.
     *
     * @param maxPipelinedRequests Maximum number of pipelined requests per {@link RedisConnection}.
     * @return {@code this}.
     */
    public DefaultRedisClientBuilder<ResolvedAddress> setMaxPipelinedRequests(int maxPipelinedRequests) {
        config.setMaxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    /**
     * Sets the idle timeout for connections created by this builder.
     *
     * @param idleConnectionTimeout the timeout {@link Duration} or {@code null} if no timeout configured.
     * @return {@code this}.
     */
    public DefaultRedisClientBuilder<ResolvedAddress> setIdleConnectionTimeout(@Nullable Duration idleConnectionTimeout) {
        config.setIdleConnectionTimeout(idleConnectionTimeout);
        return this;
    }

    /**
     * Sets the ping period to keep alive connections created by this builder.
     *
     * @param pingPeriod the {@link Duration} between keep-alive pings or {@code null} to disable pings.
     * @return {@code this}.
     */
    public DefaultRedisClientBuilder<ResolvedAddress> setPingPeriod(@Nullable final Duration pingPeriod) {
        config.setPingPeriod(pingPeriod);
        return this;
    }

    /**
     * Defines a filter {@link Function} to decorate {@link RedisConnection} created by this builder.
     * <p>
     * Filtering allows you to wrap a {@link RedisConnection} and modify behavior during request/response processing.
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     * @param connectionFilterFunction {@link Function} to decorate a {@link RedisConnection} for the purpose of filtering.
     * @return {@code this}.
     */
    public DefaultRedisClientBuilder<ResolvedAddress> setConnectionFilterFactory(Function<RedisConnection, RedisConnection> connectionFilterFunction) {
        this.connectionFilterFunction = requireNonNull(connectionFilterFunction);
        return this;
    }

    @Override
    public RedisClient build(IoExecutor ioExecutor, Executor executor,
                             Publisher<Event<ResolvedAddress>> addressEventStream) {
        return new DefaultRedisClient<>(ioExecutor, executor, config.asReadOnly(), addressEventStream,
                connectionFilterFunction, loadBalancerFactory);
    }

    static final class DefaultRedisClient<ResolvedAddress, EventType extends Event<ResolvedAddress>>
            extends RedisClient {
        private final BufferAllocator allocator;
        private final LoadBalancer<RedisConnection> subscribeLb;
        private final LoadBalancer<RedisConnection> pipelineLb;

        DefaultRedisClient(IoExecutor ioExecutor, Executor executor, ReadOnlyRedisClientConfig roConfig,
                           Publisher<EventType> addressEventStream,
                           Function<RedisConnection, RedisConnection> connectionFilter,
                           LoadBalancerFactory<ResolvedAddress, RedisConnection> loadBalancerFactory) {
            requireNonNull(ioExecutor);
            this.allocator = roConfig.getTcpClientConfig().getAllocator();
            final Publisher<EventType> multicastAddressEventStream = addressEventStream.multicast(2);
            DefaultRedisConnectionFactory<ResolvedAddress> subscribeFactory =
                    new DefaultRedisConnectionFactory<>(roConfig, ioExecutor, executor, true, connectionFilter);
            DefaultRedisConnectionFactory<ResolvedAddress> pipelineFactory =
                    new DefaultRedisConnectionFactory<>(roConfig, ioExecutor, executor, false, connectionFilter);
            subscribeLb = loadBalancerFactory.newLoadBalancer(multicastAddressEventStream, subscribeFactory);
            pipelineLb = loadBalancerFactory.newLoadBalancer(multicastAddressEventStream, pipelineFactory);
        }

        @Override
        public Single<ReservedRedisConnection> reserveConnection(RedisRequest request) {
            RedisProtocolSupport.Command cmd = request.getCommand();
            LoadBalancer<RedisConnection> loadBalancer = getLbForCommand(cmd);
            return loadBalancer.selectConnection(SELECTOR_FOR_RESERVE).map(conn -> (ReservedRedisConnection) conn);
        }

        @Override
        public BufferAllocator getBufferAllocator() {
            return allocator;
        }

        @Override
        public Publisher<RedisData> request(RedisRequest request) {
            RedisProtocolSupport.Command cmd = request.getCommand();
            LoadBalancer<RedisConnection> loadBalancer = getLbForCommand(cmd);
            return loadBalancer.selectConnection(SELECTOR_FOR_REQUEST)
                    .flatmapPublisher(selectedConnection -> selectedConnection.request(request));
        }

        @Override
        public Completable onClose() {
            return subscribeLb.onClose().mergeDelayError(pipelineLb.onClose());
        }

        @Override
        public Completable closeAsync() {
            return subscribeLb.closeAsync().mergeDelayError(pipelineLb.closeAsync());
        }

        private LoadBalancer<RedisConnection> getLbForCommand(RedisProtocolSupport.Command cmd) {
            if (isSubscribeModeCommand(cmd)) {
                return subscribeLb;
            }
            return pipelineLb;
        }
    }
}
