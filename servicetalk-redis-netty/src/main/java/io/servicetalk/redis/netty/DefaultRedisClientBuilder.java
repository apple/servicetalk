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

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.redis.api.LoadBalancerReadyRedisClient;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisClientBuilder;
import io.servicetalk.redis.api.RedisClientFilterFactory;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisConnectionFilterFactory;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisExecutionStrategy;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.ExecutionContextBuilder;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.time.Duration;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.client.api.internal.RequestConcurrencyController.Result.Accepted;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.loadbalancer.RoundRobinLoadBalancer.newRoundRobinFactory;
import static io.servicetalk.redis.api.RedisExecutionStrategies.defaultStrategy;
import static io.servicetalk.redis.netty.GlobalDnsServiceDiscoverer.globalDnsServiceDiscoverer;
import static io.servicetalk.redis.netty.RedisUtils.isSubscribeModeCommand;
import static java.util.Objects.requireNonNull;

/**
 * A builder for instances of {@link RedisClient}.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
final class DefaultRedisClientBuilder<U, R> implements RedisClientBuilder<U, R> {

    public static final Function<LoadBalancedRedisConnection, LoadBalancedRedisConnection> SELECTOR_FOR_REQUEST =
            conn -> conn.tryRequest() == Accepted ? conn : null;
    public static final Function<LoadBalancedRedisConnection, LoadBalancedRedisConnection> SELECTOR_FOR_RESERVE =
            conn -> conn.tryReserve() ? conn : null;
    private static final RedisClientFilterFactory LB_READY_FILTER =
            // We ignore the sdEvents because currently the SD stream is multiplexed and the pipelined LB will get
            // events after the pubsub LB. If there is async behavior in the load balancer folks can override this.
            (client, pubsubEvents, pipelinedEvents) -> new LoadBalancerReadyRedisClient(4, pipelinedEvents, client);

    private final U address;
    private final RedisClientConfig config;
    private final ExecutionContextBuilder executionContextBuilder = new ExecutionContextBuilder();
    private LoadBalancerFactory<R, RedisConnection> loadBalancerFactory;
    private ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer;
    private RedisConnectionFilterFactory connectionFilterFactory = RedisConnectionFilterFactory.identity();
    private RedisClientFilterFactory clientFilterFactory = RedisClientFilterFactory.identity();
    private RedisClientFilterFactory lbReadyFilter = LB_READY_FILTER;
    private ConnectionFactoryFilter<R, RedisConnection> connectionFactoryFilter = ConnectionFactoryFilter.identity();
    private RedisExecutionStrategy strategy = defaultStrategy();

    DefaultRedisClientBuilder(final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer,
                              final U address) {
        this.address = requireNonNull(address);
        config = new RedisClientConfig(new TcpClientConfig(false));
        loadBalancerFactory = newRoundRobinFactory();
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
    }

    private DefaultRedisClientBuilder(U address, DefaultRedisClientBuilder<U, R> from) {
        this.address = address;
        config = from.config;
        loadBalancerFactory = from.loadBalancerFactory;
        serviceDiscoverer = from.serviceDiscoverer;
        connectionFilterFactory = from.connectionFilterFactory;
        clientFilterFactory = from.clientFilterFactory;
        connectionFactoryFilter = from.connectionFactoryFilter;
        lbReadyFilter = from.lbReadyFilter;
    }

    DefaultRedisClientBuilder<U, R> copy() {
        return new DefaultRedisClientBuilder<>(address, this);
    }

    DefaultRedisClientBuilder<U, R> copy(U address) {
        return new DefaultRedisClientBuilder<>(address, this);
    }

    static DefaultRedisClientBuilder<HostAndPort, InetSocketAddress> forHostAndPort(final HostAndPort address) {
        return new DefaultRedisClientBuilder<>(globalDnsServiceDiscoverer(), address);
    }

    @Override
    public RedisClientBuilder<U, R> ioExecutor(final IoExecutor ioExecutor) {
        executionContextBuilder.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public RedisClientBuilder<U, R> executionStrategy(final RedisExecutionStrategy strategy) {
        this.strategy = requireNonNull(strategy);
        Executor executor = strategy.executor();
        if (executor != null) {
            executionContextBuilder.executor(executor);
        }
        return this;
    }

    @Override
    public RedisClientBuilder<U, R> bufferAllocator(final BufferAllocator allocator) {
        executionContextBuilder.bufferAllocator(allocator);
        return this;
    }

    @Override
    public DefaultRedisClientBuilder<U, R> sslConfig(@Nullable SslConfig config) {
        this.config.tcpClientConfig().sslConfig(config);
        return this;
    }

    @Override
    public <T> DefaultRedisClientBuilder<U, R> socketOption(SocketOption<T> option, T value) {
        config.tcpClientConfig().socketOption(option, value);
        return this;
    }

    @Override
    public DefaultRedisClientBuilder<U, R> enableWireLogging(String loggerName) {
        config.tcpClientConfig().enableWireLogging(loggerName);
        return this;
    }

    @Override
    public DefaultRedisClientBuilder<U, R> disableWireLogging() {
        config.tcpClientConfig().disableWireLogging();
        return this;
    }

    @Override
    public DefaultRedisClientBuilder<U, R> maxPipelinedRequests(int maxPipelinedRequests) {
        config.maxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    @Override
    public DefaultRedisClientBuilder<U, R> idleConnectionTimeout(@Nullable Duration idleConnectionTimeout) {
        config.idleConnectionTimeout(idleConnectionTimeout);
        return this;
    }

    @Override
    public DefaultRedisClientBuilder<U, R> pingPeriod(@Nullable final Duration pingPeriod) {
        config.pingPeriod(pingPeriod);
        return this;
    }

    @Override
    public DefaultRedisClientBuilder<U, R> appendConnectionFilter(RedisConnectionFilterFactory factory) {
        connectionFilterFactory = connectionFilterFactory.append(factory);
        return this;
    }

    @Override
    public DefaultRedisClientBuilder<U, R> appendConnectionFactoryFilter(
            final ConnectionFactoryFilter<R, RedisConnection> factory) {
        connectionFactoryFilter = connectionFactoryFilter.append(factory);
        return this;
    }

    @Override
    public DefaultRedisClientBuilder<U, R> appendClientFilter(RedisClientFilterFactory factory) {
        clientFilterFactory = clientFilterFactory.append(factory);
        return this;
    }

    @Override
    public RedisClientBuilder<U, R> disableWaitForLoadBalancer() {
        lbReadyFilter = RedisClientFilterFactory.identity();
        return this;
    }

    @Override
    public RedisClientBuilder<U, R> serviceDiscoverer(
            final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer) {
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
        return this;
    }

    @Override
    public RedisClientBuilder<U, R> loadBalancerFactory(final LoadBalancerFactory<R, RedisConnection> factory) {
        loadBalancerFactory = requireNonNull(factory);
        return this;
    }

    /**
     * Should the subscribe signal be deferred until the Redis PubSub subscribe ack or not (default).
     *
     * WARNING: internal API not to be exposed outside the package.
     * @param defer {@code true} to defer the subscribe
     * @return {@code this}
     */
    RedisClientBuilder<U, R> deferSubscribeTillConnect(boolean defer) {
        config.deferSubscribeTillConnect(defer);
        return this;
    }

    @Override
    public RedisClient build() {
        return build0(serviceDiscoverer, buildExecutionContext());
    }

    RedisClient build(final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> sd,
                      final ExecutionContext executionContext) {
        return build0(sd, executionContext);
    }

    ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer() {
        return serviceDiscoverer;
    }

    U address() {
        return address;
    }

    ExecutionContext buildExecutionContext() {
        return executionContextBuilder.build();
    }

    @Nonnull
    private RedisClient build0(final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> sd,
                               final ExecutionContext executionContext) {
        Publisher<? extends ServiceDiscovererEvent<R>> multicastAddressEventStream = sd.discover(address).multicast(2);
        ReadOnlyRedisClientConfig roConfig = config.asReadOnly();

        // Track resources that potentially need to be closed when an exception is thrown during buildStreaming
        final CompositeCloseable closeOnException = newCompositeCloseable();

        try {
            LoadBalancer<? extends RedisConnection> lbfUntypedForCast =
                    loadBalancerFactory.newLoadBalancer(multicastAddressEventStream,
                            connectionFactoryFilter.create(new SubscribedLBRedisConnectionFactory<>(roConfig,
                                    executionContext, connectionFilterFactory)));
            closeOnException.append(lbfUntypedForCast);
            @SuppressWarnings("unchecked")
            LoadBalancer<LoadBalancedRedisConnection> subscribeLb =
                    (LoadBalancer<LoadBalancedRedisConnection>) lbfUntypedForCast;

            lbfUntypedForCast = loadBalancerFactory.newLoadBalancer(multicastAddressEventStream,
                    connectionFactoryFilter.create(new PipelinedLBRedisConnectionFactory<>(roConfig, executionContext,
                            connectionFilterFactory)));
            closeOnException.append(lbfUntypedForCast);
            @SuppressWarnings("unchecked")
            LoadBalancer<LoadBalancedRedisConnection> pipelineLb =
                    (LoadBalancer<LoadBalancedRedisConnection>) lbfUntypedForCast;

            return clientFilterFactory.append(lbReadyFilter)
                    .create(closeOnException.append(new DefaultRedisClient(executionContext, strategy, subscribeLb,
                                    pipelineLb)),
                            subscribeLb.eventStream(), pipelineLb.eventStream());
        } catch (Throwable t) {
            closeOnException.closeAsync().subscribe();
            throw t;
        }
    }

    private static final class DefaultRedisClient extends RedisClient {
        private final ExecutionContext executionContext;
        private final RedisExecutionStrategy strategy;
        private final LoadBalancer<LoadBalancedRedisConnection> subscribeLb;
        private final LoadBalancer<LoadBalancedRedisConnection> pipelineLb;

        DefaultRedisClient(ExecutionContext executionContext, RedisExecutionStrategy strategy,
                           LoadBalancer<LoadBalancedRedisConnection> subscribeLb,
                           LoadBalancer<LoadBalancedRedisConnection> pipelineLb) {
            this.executionContext = requireNonNull(executionContext);
            this.strategy = strategy;
            this.subscribeLb = requireNonNull(subscribeLb);
            this.pipelineLb = requireNonNull(pipelineLb);
        }

        @Override
        public Single<? extends ReservedRedisConnection> reserveConnection(final Command command) {
            return reserveConnection(strategy, command);
        }

        @Override
        public Publisher<RedisData> request(final RedisRequest request) {
            return request(strategy, request);
        }

        @Override
        public Single<? extends ReservedRedisConnection> reserveConnection(RedisExecutionStrategy strategy,
                                                                           Command command) {
            return strategy.offloadReceive(executionContext.executor(),
                    lbForCommand(command).selectConnection(SELECTOR_FOR_RESERVE));
        }

        @Override
        public Publisher<RedisData> request(RedisExecutionStrategy strategy, RedisRequest request) {
            // We have to do the incrementing/decrementing in the Client instead of LoadBalancedRedisConnection because
            // it is possible that someone can use the ConnectionFactory exported by this Client before the LoadBalancer
            // takes ownership of it (e.g. connection initialization) and in that case they will not be following the
            // LoadBalancer API which this Client depends upon to ensure the concurrent request count state is correct.
            return lbForCommand(request.command()).selectConnection(SELECTOR_FOR_REQUEST)
                    .flatMapPublisher(conn -> conn.request(strategy, request).doBeforeFinally(conn::requestFinished));
        }

        @Override
        public ExecutionContext executionContext() {
            return executionContext;
        }

        @Override
        public Completable onClose() {
            return subscribeLb.onClose().mergeDelayError(pipelineLb.onClose());
        }

        @Override
        public Completable closeAsync() {
            return subscribeLb.closeAsync().mergeDelayError(pipelineLb.closeAsync());
        }

        @Override
        public Completable closeAsyncGracefully() {
            return subscribeLb.closeAsyncGracefully().mergeDelayError(pipelineLb.closeAsyncGracefully());
        }

        private LoadBalancer<LoadBalancedRedisConnection> lbForCommand(Command cmd) {
            return isSubscribeModeCommand(cmd) ? subscribeLb : pipelineLb;
        }
    }
}
