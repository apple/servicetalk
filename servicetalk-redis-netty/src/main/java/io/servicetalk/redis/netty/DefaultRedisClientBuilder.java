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

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer.Event;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.redis.api.LoadBalancerReadyRedisClient;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisClientBuilder;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisProtocolSupport;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.SslConfig;

import java.io.InputStream;
import java.net.SocketOption;
import java.time.Duration;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.redis.netty.RedisConnectionFilterFactory.identity;
import static io.servicetalk.redis.netty.RedisUtils.isSubscribeModeCommand;
import static java.util.Objects.requireNonNull;

/**
 * A builder for instances of {@link RedisClient}.
 * @param <ResolvedAddress> the type of address after resolution.
 */
public final class DefaultRedisClientBuilder<ResolvedAddress>
        implements RedisClientBuilder<ResolvedAddress, Event<ResolvedAddress>> {

    public static final Function<LoadBalancedRedisConnection, LoadBalancedRedisConnection> SELECTOR_FOR_REQUEST =
            conn -> conn.tryRequest() ? conn : null;
    public static final Function<LoadBalancedRedisConnection, LoadBalancedRedisConnection> SELECTOR_FOR_RESERVE =
            conn -> conn.tryReserve() ? conn : null;
    static final RedisClientFilterFactory DEFAULT_CLIENT_FILTER_FACTORY =
            // We ignore the sdEvents because currently the SD stream is multiplexed and the pipelined LB will get
            // events after the pubsub LB. If there is async behavior in the load balancer folks can override this.
            (client, pubsubEvents, pipelinedEvents) -> new LoadBalancerReadyRedisClient(4, pipelinedEvents, client);

    private final LoadBalancerFactory<ResolvedAddress, RedisConnection> loadBalancerFactory;
    private final RedisClientConfig config;
    private RedisConnectionFilterFactory connectionFilterFactory = identity();
    private RedisClientFilterFactory clientFilterFactory = DEFAULT_CLIENT_FILTER_FACTORY;

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
     * Enable SSL/TLS using the provided {@link SslConfig}. To disable SSL pass in {@code null}.
     * @param config the {@link SslConfig}.
     * @return {@code this}.
     * @throws IllegalStateException if accessing the cert/key throws when {@link InputStream#close()} is called.
     */
    public DefaultRedisClientBuilder<ResolvedAddress> ssl(@Nullable SslConfig config) {
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
    public <T> DefaultRedisClientBuilder<ResolvedAddress> socketOption(SocketOption<T> option, T value) {
        config.getTcpClientConfig().setSocketOption(option, value);
        return this;
    }

    /**
     * Enable wire-logging for connections created by this builder. All wire events will be logged at trace level.
     *
     * @param loggerName The name of the logger to log wire events.
     * @return {@code this}.
     */
    public DefaultRedisClientBuilder<ResolvedAddress> enableWireLogging(String loggerName) {
        config.getTcpClientConfig().enableWireLogging(loggerName);
        return this;
    }

    /**
     * Disable previously configured wire-logging for connections created by this builder.
     * If wire-logging has not been configured before, this method has no effect.
     *
     * @return {@code this}.
     * @see #enableWireLogging(String)
     */
    public DefaultRedisClientBuilder<ResolvedAddress> disableWireLogging() {
        config.getTcpClientConfig().disableWireLogging();
        return this;
    }

    /**
     * Sets maximum requests that can be pipelined on a connection created by this builder.
     *
     * @param maxPipelinedRequests Maximum number of pipelined requests per {@link RedisConnection}.
     * @return {@code this}.
     */
    public DefaultRedisClientBuilder<ResolvedAddress> maxPipelinedRequests(int maxPipelinedRequests) {
        config.setMaxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    /**
     * Sets the idle timeout for connections created by this builder.
     *
     * @param idleConnectionTimeout the timeout {@link Duration} or {@code null} if no timeout configured.
     * @return {@code this}.
     */
    public DefaultRedisClientBuilder<ResolvedAddress> idleConnectionTimeout(@Nullable Duration idleConnectionTimeout) {
        config.setIdleConnectionTimeout(idleConnectionTimeout);
        return this;
    }

    /**
     * Sets the ping period to keep alive connections created by this builder.
     *
     * @param pingPeriod the {@link Duration} between keep-alive pings or {@code null} to disable pings.
     * @return {@code this}.
     */
    public DefaultRedisClientBuilder<ResolvedAddress> pingPeriod(@Nullable final Duration pingPeriod) {
        config.setPingPeriod(pingPeriod);
        return this;
    }

    /**
     * Set the {@link Function} which is used as a factory to filter/decorate {@link RedisConnection} created by this
     * builder.
     * <p>
     * Filtering allows you to wrap a {@link RedisConnection} and modify behavior during request/response processing.
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     * @param connectionFilterFactory {@link RedisConnectionFilterFactory} to decorate a {@link RedisConnection} for the
     * purpose of filtering.
     * @return {@code this}.
     */
    public DefaultRedisClientBuilder<ResolvedAddress> appendConnectionFilter(
            RedisConnectionFilterFactory connectionFilterFactory) {
        this.connectionFilterFactory = requireNonNull(connectionFilterFactory);
        return this;
    }

    /**
     * Set the filter factory that is used to decorate {@link RedisClient} created by this builder.
     * <p>
     * Note this method will be used to decorate the result of {@link #build(ExecutionContext, Publisher)} before it is
     * returned to the user.
     * @param clientFilterFactory factory to decorate a {@link RedisClient} for the purpose of filtering.
     * @return {@code this}
     */
    public DefaultRedisClientBuilder<ResolvedAddress> appendClientFilter(
            RedisClientFilterFactory clientFilterFactory) {
        this.clientFilterFactory = requireNonNull(clientFilterFactory);
        return this;
    }

    @Override
    public RedisClient build(ExecutionContext executionContext,
                             Publisher<Event<ResolvedAddress>> addressEventStream) {
        return newRedisClient(executionContext, addressEventStream, config.asReadOnly(), connectionFilterFactory,
                clientFilterFactory, loadBalancerFactory);
    }

    static <ResolvedAddress, EventType extends Event<ResolvedAddress>> RedisClient newRedisClient(
            ExecutionContext executionContext, Publisher<EventType> addressEventStream,
            ReadOnlyRedisClientConfig roConfig, RedisConnectionFilterFactory connectionFilterFactory,
            RedisClientFilterFactory clientFilterFactory,
            LoadBalancerFactory<ResolvedAddress, RedisConnection> loadBalancerFactory) {
        final Publisher<EventType> multicastAddressEventStream = addressEventStream.multicast(2);

        LoadBalancer<? extends RedisConnection> lbfUntypedForCast =
                loadBalancerFactory.newLoadBalancer(multicastAddressEventStream,
                        new SubscribedLBRedisConnectionFactory<>(roConfig, executionContext, connectionFilterFactory));
        @SuppressWarnings("unchecked")
        LoadBalancer<LoadBalancedRedisConnection> subscribeLb =
                (LoadBalancer<LoadBalancedRedisConnection>) lbfUntypedForCast;
        lbfUntypedForCast = loadBalancerFactory.newLoadBalancer(multicastAddressEventStream,
                new PipelinedLBRedisConnectionFactory<>(roConfig, executionContext, connectionFilterFactory));
        @SuppressWarnings("unchecked")
        LoadBalancer<LoadBalancedRedisConnection> pipelineLb =
                (LoadBalancer<LoadBalancedRedisConnection>) lbfUntypedForCast;

        return clientFilterFactory.apply(new DefaultRedisClient(executionContext, subscribeLb, pipelineLb),
                subscribeLb.getEventStream(), pipelineLb.getEventStream());
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
        public Single<? extends ReservedRedisConnection> reserveConnection(RedisRequest request) {
            return getLbForCommand(request.command()).selectConnection(SELECTOR_FOR_RESERVE);
        }

        @Override
        public Publisher<RedisData> request(RedisRequest request) {
            // We have to do the incrementing/decrementing in the Client instead of LoadBalancedRedisConnection because
            // it is possible that someone can use the ConnectionFactory exported by this Client before the LoadBalancer
            // takes ownership of it (e.g. connection initialization) and in that case they will not be following the
            // LoadBalancer API which this Client depends upon to ensure the concurrent request count state is correct.
            return getLbForCommand(request.command()).selectConnection(SELECTOR_FOR_REQUEST)
                    .flatMapPublisher(selectedConnection -> selectedConnection.request(request)
                            .doBeforeFinally(selectedConnection::requestFinished));
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

        private LoadBalancer<LoadBalancedRedisConnection> getLbForCommand(RedisProtocolSupport.Command cmd) {
            return isSubscribeModeCommand(cmd) ? subscribeLb : pipelineLb;
        }
    }
}
