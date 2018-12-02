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
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.partition.ClosedPartitionException;
import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.client.api.partition.PartitionMapFactory;
import io.servicetalk.client.api.partition.PartitionedServiceDiscovererEvent;
import io.servicetalk.client.api.partition.UnknownPartitionException;
import io.servicetalk.client.internal.DefaultPartitionedClientGroup;
import io.servicetalk.client.internal.DefaultPartitionedClientGroup.PartitionedClientFactory;
import io.servicetalk.client.internal.partition.PowerSetPartitionMapFactory;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.redis.api.PartitionedRedisClient;
import io.servicetalk.redis.api.PartitionedRedisClientBuilder;
import io.servicetalk.redis.api.PartitionedRedisClientFilterFactory;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisClientFilterFactory;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisConnectionFilterFactory;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisExecutionStrategy;
import io.servicetalk.redis.api.RedisPartitionAttributesBuilder;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.SslConfig;

import java.net.SocketOption;
import java.time.Duration;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.error;
import static io.servicetalk.concurrent.api.Single.deferShareContext;
import static io.servicetalk.redis.api.PartitionedRedisClientFilterFactory.identity;
import static java.util.Objects.requireNonNull;

/**
 * A builder for instances of {@link PartitionedRedisClient}.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
final class DefaultPartitionedRedisClientBuilder<U, R> implements PartitionedRedisClientBuilder<U, R> {

    private static final Function<PartitionAttributes, RedisClient> PARTITION_CLOSED = pa -> new NoopPartitionClient(
            new ClosedPartitionException(pa, "Partition closed "));
    private static final Function<PartitionAttributes, RedisClient> PARTITION_UNKNOWN = pa -> new NoopPartitionClient(
            new UnknownPartitionException(pa, "Partition unknown"));

    private final DefaultRedisClientBuilder<U, R> builderTemplate;
    private final Function<Command, RedisPartitionAttributesBuilder> partitionAttributesBuilderFactory;
    private final U address;
    private PartitionMapFactory partitionMapFactory = PowerSetPartitionMapFactory.INSTANCE;
    private int serviceDiscoveryMaxQueueSize = 32;
    private PartitionedRedisClientFilterFactory partitionedClientFilterFactory = identity();

    private DefaultPartitionedRedisClientBuilder(final U address,
                                                 final DefaultRedisClientBuilder<U, R> builderTemplate,
                                                 final Function<Command, RedisPartitionAttributesBuilder> factory) {
        this.builderTemplate = builderTemplate;
        this.partitionAttributesBuilderFactory = requireNonNull(factory);
        this.address = address;
    }

    public PartitionedRedisClientBuilder<U, R> ioExecutor(final IoExecutor ioExecutor) {
        builderTemplate.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public PartitionedRedisClientBuilder<U, R> executionStrategy(final RedisExecutionStrategy strategy) {
        builderTemplate.executionStrategy(strategy);
        return this;
    }

    @Override
    public PartitionedRedisClientBuilder<U, R> bufferAllocator(final BufferAllocator allocator) {
        builderTemplate.bufferAllocator(allocator);
        return this;
    }

    @Override
    public PartitionedRedisClientBuilder<U, R> sslConfig(@Nullable SslConfig config) {
        builderTemplate.sslConfig(config);
        return this;
    }

    @Override
    public <T> PartitionedRedisClientBuilder<U, R> socketOption(SocketOption<T> option, T value) {
        builderTemplate.socketOption(option, value);
        return this;
    }

    @Override
    public PartitionedRedisClientBuilder<U, R> enableWireLogging(String loggerName) {
        builderTemplate.enableWireLogging(loggerName);
        return this;
    }

    @Override
    public PartitionedRedisClientBuilder<U, R> disableWireLogging() {
        builderTemplate.disableWireLogging();
        return this;
    }

    @Override
    public PartitionedRedisClientBuilder<U, R> maxPipelinedRequests(int maxPipelinedRequests) {
        builderTemplate.maxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    @Override
    public PartitionedRedisClientBuilder<U, R> idleConnectionTimeout(
            @Nullable Duration idleConnectionTimeout) {
        builderTemplate.idleConnectionTimeout(idleConnectionTimeout);
        return this;
    }

    @Override
    public PartitionedRedisClientBuilder<U, R> pingPeriod(@Nullable final Duration pingPeriod) {
        builderTemplate.pingPeriod(pingPeriod);
        return this;
    }

    @Override
    public PartitionedRedisClientBuilder<U, R> serviceDiscoveryMaxQueueSize(
            int serviceDiscoveryMaxQueueSize) {
        if (serviceDiscoveryMaxQueueSize <= 0) {
            throw new IllegalArgumentException("serviceDiscoveryMaxQueueSize: " + serviceDiscoveryMaxQueueSize
                    + " (expected >0)");
        }
        this.serviceDiscoveryMaxQueueSize = serviceDiscoveryMaxQueueSize;
        return this;
    }

    @Override
    public PartitionedRedisClientBuilder<U, R> appendClientFilter(RedisClientFilterFactory factory) {
        builderTemplate.appendClientFilter(factory);
        return this;
    }

    @Override
    public PartitionedRedisClientBuilder<U, R> appendConnectionFilter(RedisConnectionFilterFactory factory) {
        builderTemplate.appendConnectionFilter(factory);
        return this;
    }

    @Override
    public PartitionedRedisClientBuilder<U, R> appendConnectionFactoryFilter(
            final ConnectionFactoryFilter<R, RedisConnection> factory) {
        builderTemplate.appendConnectionFactoryFilter(factory);
        return this;
    }

    @Override
    public PartitionedRedisClientBuilder<U, R> appendPartitionedFilter(
            PartitionedRedisClientFilterFactory factory) {
        partitionedClientFilterFactory = partitionedClientFilterFactory.append(factory);
        return this;
    }

    @Override
    public PartitionedRedisClientBuilder<U, R> disableWaitForLoadBalancer() {
        builderTemplate.disableWaitForLoadBalancer();
        return this;
    }

    @Override
    public PartitionedRedisClientBuilder<U, R> serviceDiscoverer(
            final ServiceDiscoverer<U, R, ? extends PartitionedServiceDiscovererEvent<R>> serviceDiscoverer) {
        builderTemplate.serviceDiscoverer(serviceDiscoverer);
        return this;
    }

    @Override
    public PartitionedRedisClientBuilder<U, R> loadBalancerFactory(
            final LoadBalancerFactory<R, RedisConnection> factory) {
        builderTemplate.loadBalancerFactory(factory);
        return this;
    }

    @Override
    public PartitionedRedisClientBuilder<U, R> partitionMapFactory(final PartitionMapFactory partitionMapFactory) {
        this.partitionMapFactory = requireNonNull(partitionMapFactory);
        return this;
    }

    @Override
    public PartitionedRedisClient build() {
        final DefaultRedisClientBuilder<U, R> copy = builderTemplate.copy();

        final PartitionedClientFactory<U, R, RedisClient> clientFactory =
                (pa, sd) -> copy.copy().serviceDiscoverer(sd).build();

        @SuppressWarnings("unchecked")
        final Publisher<? extends PartitionedServiceDiscovererEvent<R>> psdEvents =
                (Publisher<? extends PartitionedServiceDiscovererEvent<R>>) copy.serviceDiscoverer().discover(address);

        return partitionedClientFilterFactory.create(
                new DefaultPartitionedRedisClient<>(psdEvents, serviceDiscoveryMaxQueueSize, clientFactory,
                        partitionAttributesBuilderFactory, copy.buildExecutionContext(), partitionMapFactory));
    }

    static <U, R> DefaultPartitionedRedisClientBuilder<U, R> forAddress(
            final U address,
            final ServiceDiscoverer<U, R, ? extends PartitionedServiceDiscovererEvent<R>> sd,
            final Function<Command, RedisPartitionAttributesBuilder> partitionAttributesBuilderFactory) {
        DefaultRedisClientBuilder<U, R> clientBuilder = new DefaultRedisClientBuilder<>(sd, address);
        return new DefaultPartitionedRedisClientBuilder<>(address, clientBuilder, partitionAttributesBuilderFactory);
    }

    private static final class DefaultPartitionedRedisClient<U, R> extends PartitionedRedisClient {

        private final Function<Command, RedisPartitionAttributesBuilder> pabf;
        private final ExecutionContext executionContext;
        private final DefaultPartitionedClientGroup<U, R, RedisClient> group;

        DefaultPartitionedRedisClient(
                final Publisher<? extends PartitionedServiceDiscovererEvent<R>> psdEvents,
                final int psdMaxQueueSize,
                final PartitionedClientFactory<U, R, RedisClient> clientFactory,
                final Function<Command, RedisPartitionAttributesBuilder> pabf,
                final ExecutionContext executionContext, final PartitionMapFactory partitionMapFactory) {
            this.pabf = pabf;
            this.executionContext = executionContext;
            this.group = new DefaultPartitionedClientGroup<>(PARTITION_CLOSED, PARTITION_UNKNOWN, clientFactory,
                    partitionMapFactory, psdEvents, psdMaxQueueSize);
        }

        @Override
        public Publisher<RedisData> request(final RedisExecutionStrategy strategy,
                                            final PartitionAttributes partitionSelector, final RedisRequest request) {
            return Publisher.defer(true, () -> group.get(partitionSelector).request(strategy, request));
        }

        @Override
        public <Resp> Single<Resp> request(final RedisExecutionStrategy strategy,
                                           final PartitionAttributes partitionSelector,
                                           final RedisRequest request, final Class<Resp> responseType) {
            return deferShareContext(() -> group.get(partitionSelector).request(strategy, request, responseType));
        }

        @Override
        public Single<? extends RedisClient.ReservedRedisConnection> reserveConnection(
                final RedisExecutionStrategy strategy, final PartitionAttributes partitionSelector,
                final Command command) {
            return deferShareContext(() -> group.get(partitionSelector).reserveConnection(strategy, command));
        }

        @Override
        public ExecutionContext executionContext() {
            return executionContext;
        }

        @Override
        public Function<Command, RedisPartitionAttributesBuilder> redisPartitionAttributesBuilderFunction() {
            return pabf;
        }

        @Override
        public Completable onClose() {
            return group.onClose();
        }

        @Override
        public Completable closeAsync() {
            return group.closeAsync();
        }
    }

    private static final class NoopPartitionClient extends RedisClient {

        private final RuntimeException ex;

        private NoopPartitionClient(RuntimeException ex) {
            this.ex = ex;
        }

        @Override
        public Single<? extends ReservedRedisConnection> reserveConnection(final RedisExecutionStrategy strategy,
                                                                           final Command command) {
            return Single.error(ex);
        }

        @Override
        public Publisher<RedisData> request(final RedisExecutionStrategy strategy, final RedisRequest request) {
            return error(ex);
        }

        @Override
        public ExecutionContext executionContext() {
            throw ex;
        }

        @Override
        public Completable onClose() {
            return Completable.completed();
        }

        @Override
        public Completable closeAsync() {
            return Completable.completed();
        }
    }
}
