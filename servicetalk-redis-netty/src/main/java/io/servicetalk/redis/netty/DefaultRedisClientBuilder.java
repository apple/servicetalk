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

import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.dns.discovery.netty.DefaultDnsServiceDiscovererBuilder;
import io.servicetalk.redis.api.LoadBalancerReadyRedisClient;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisClientBuilder;
import io.servicetalk.redis.api.RedisClientFilterFactory;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisConnectionFilterFactory;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.SslConfig;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.time.Duration;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.loadbalancer.RoundRobinLoadBalancer.newRoundRobinFactory;
import static io.servicetalk.redis.netty.RedisUtils.isSubscribeModeCommand;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static java.util.Objects.requireNonNull;

/**
 * A builder for instances of {@link RedisClient}.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
final class DefaultRedisClientBuilder<U, R> implements RedisClientBuilder<U, R> {

    public static final Function<LoadBalancedRedisConnection, LoadBalancedRedisConnection> SELECTOR_FOR_REQUEST =
            conn -> conn.tryRequest() ? conn : null;
    public static final Function<LoadBalancedRedisConnection, LoadBalancedRedisConnection> SELECTOR_FOR_RESERVE =
            conn -> conn.tryReserve() ? conn : null;
    private static final RedisClientFilterFactory LB_READY_FILTER =
            // We ignore the sdEvents because currently the SD stream is multiplexed and the pipelined LB will get
            // events after the pubsub LB. If there is async behavior in the load balancer folks can override this.
            (client, pubsubEvents, pipelinedEvents) -> new LoadBalancerReadyRedisClient(4, pipelinedEvents, client);

    private final U address;
    private final RedisClientConfig config;
    private ExecutionContext executionContext = globalExecutionContext();
    private LoadBalancerFactory<R, RedisConnection> loadBalancerFactory;
    private ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer;
    private RedisConnectionFilterFactory connectionFilterFactory = RedisConnectionFilterFactory.identity();
    private RedisClientFilterFactory clientFilterFactory = RedisClientFilterFactory.identity();
    private RedisClientFilterFactory lbReadyFilter = LB_READY_FILTER;
    private ConnectionFactoryFilter<R, RedisConnection> connectionFactoryFilter = ConnectionFactoryFilter.identity();

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
        executionContext = from.executionContext;
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
        // We assume here that there is no need to explicitly close the ServiceDiscoverer if the Publisher returned
        // from it is correctly cancelled. This means that a ServiceDiscoverer does not keep state outside the scope
        // of Publishers returned from their discover() methods. Hence, we do not need to explicitly close this
        // ServiceDiscoverer created here.
        return new DefaultRedisClientBuilder<>(new DefaultDnsServiceDiscovererBuilder(globalExecutionContext())
                .build(), address);
    }

    @Override
    public RedisClientBuilder<U, R> executionContext(final ExecutionContext context) {
        this.executionContext = context;
        return this;
    }

    @Override
    public DefaultRedisClientBuilder<U, R> sslConfig(@Nullable SslConfig config) {
        this.config.getTcpClientConfig().setSslConfig(config);
        return this;
    }

    @Override
    public <T> DefaultRedisClientBuilder<U, R> socketOption(SocketOption<T> option, T value) {
        config.getTcpClientConfig().setSocketOption(option, value);
        return this;
    }

    @Override
    public DefaultRedisClientBuilder<U, R> enableWireLogging(String loggerName) {
        config.getTcpClientConfig().enableWireLogging(loggerName);
        return this;
    }

    @Override
    public DefaultRedisClientBuilder<U, R> disableWireLogging() {
        config.getTcpClientConfig().disableWireLogging();
        return this;
    }

    @Override
    public DefaultRedisClientBuilder<U, R> maxPipelinedRequests(int maxPipelinedRequests) {
        config.setMaxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    @Override
    public DefaultRedisClientBuilder<U, R> idleConnectionTimeout(@Nullable Duration idleConnectionTimeout) {
        config.setIdleConnectionTimeout(idleConnectionTimeout);
        return this;
    }

    @Override
    public DefaultRedisClientBuilder<U, R> pingPeriod(@Nullable final Duration pingPeriod) {
        config.setPingPeriod(pingPeriod);
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
        config.setDeferSubscribeTillConnect(defer);
        return this;
    }

    @Override
    public RedisClient build() {
        return build0(serviceDiscoverer);
    }

    RedisClient build(ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> sd) {
        return build0(sd);
    }

    ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer() {
        return serviceDiscoverer;
    }

    U address() {
        return address;
    }

    ExecutionContext executionContext() {
        return executionContext;
    }

    @Nonnull
    private RedisClient build0(ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> sd) {
        Publisher<? extends ServiceDiscovererEvent<R>> multicastAddressEventStream =
                sd.discover(address).multicast(2);
        ReadOnlyRedisClientConfig roConfig = config.asReadOnly();

        // Track resources that potentially need to be closed when an exception is thrown during buildStreaming
        final CompositeCloseable closeOnException = newCompositeCloseable();

        try {
            LoadBalancer<? extends RedisConnection> lbfUntypedForCast =
                    loadBalancerFactory.newLoadBalancer(multicastAddressEventStream,
                            connectionFactoryFilter.apply(new SubscribedLBRedisConnectionFactory<>(roConfig,
                                    executionContext, connectionFilterFactory)));
            closeOnException.append(lbfUntypedForCast);
            @SuppressWarnings("unchecked")
            LoadBalancer<LoadBalancedRedisConnection> subscribeLb =
                    (LoadBalancer<LoadBalancedRedisConnection>) lbfUntypedForCast;

            lbfUntypedForCast = loadBalancerFactory.newLoadBalancer(multicastAddressEventStream,
                    connectionFactoryFilter.apply(new PipelinedLBRedisConnectionFactory<>(roConfig, executionContext,
                            connectionFilterFactory)));
            closeOnException.append(lbfUntypedForCast);
            @SuppressWarnings("unchecked")
            LoadBalancer<LoadBalancedRedisConnection> pipelineLb =
                    (LoadBalancer<LoadBalancedRedisConnection>) lbfUntypedForCast;

            return clientFilterFactory.append(lbReadyFilter)
                    .apply(closeOnException.append(new DefaultRedisClient(executionContext, subscribeLb, pipelineLb)),
                            subscribeLb.eventStream(), pipelineLb.eventStream());
        } catch (Throwable t) {
            closeOnException.closeAsync().subscribe();
            throw t;
        }
    }

    private static final class DefaultRedisClient extends RedisClient {
        private final ExecutionContext executionContext;
        private final LoadBalancer<LoadBalancedRedisConnection> subscribeLb;
        private final LoadBalancer<LoadBalancedRedisConnection> pipelineLb;

        DefaultRedisClient(ExecutionContext executionContext,
                           LoadBalancer<LoadBalancedRedisConnection> subscribeLb,
                           LoadBalancer<LoadBalancedRedisConnection> pipelineLb) {
            this.executionContext = requireNonNull(executionContext);
            this.subscribeLb = requireNonNull(subscribeLb);
            this.pipelineLb = requireNonNull(pipelineLb);
        }

        @Override
        public Single<? extends ReservedRedisConnection> reserveConnection(Command command) {
            return lbForCommand(command).selectConnection(SELECTOR_FOR_RESERVE);
        }

        @Override
        public Publisher<RedisData> request(RedisRequest request) {
            // We have to do the incrementing/decrementing in the Client instead of LoadBalancedRedisConnection because
            // it is possible that someone can use the ConnectionFactory exported by this Client before the LoadBalancer
            // takes ownership of it (e.g. connection initialization) and in that case they will not be following the
            // LoadBalancer API which this Client depends upon to ensure the concurrent request count state is correct.
            return lbForCommand(request.command()).selectConnection(SELECTOR_FOR_REQUEST)
                    .flatMapPublisher(conn -> conn.request(request).doBeforeFinally(conn::requestFinished));
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
