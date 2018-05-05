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
import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.client.api.partition.PartitionMap;
import io.servicetalk.client.api.partition.PartitionMapFactory;
import io.servicetalk.client.api.partition.PartitionedEvent;
import io.servicetalk.client.api.partition.UnknownPartitionException;
import io.servicetalk.client.internal.partition.PowerSetPartitionMapFactory;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Publisher.Group;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.redis.api.BufferRedisCommander;
import io.servicetalk.redis.api.PartitionedRedisClient;
import io.servicetalk.redis.api.PartitionedRedisClientBuilder;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisClient.ReservedRedisConnection;
import io.servicetalk.redis.api.RedisClientBuilder;
import io.servicetalk.redis.api.RedisCommander;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisPartitionAttributesBuilder;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.redis.netty.DefaultRedisClientBuilder.DefaultRedisClient;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.SslConfig;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.SocketOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Publisher.error;
import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

public class DefaultPartitionedRedisClientBuilder<ResolvedAddress>
        implements PartitionedRedisClientBuilder<ResolvedAddress> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPartitionedRedisClientBuilder.class);

    @Nullable
    private final Function<Command, RedisPartitionAttributesBuilder> redisPartitionAttributesBuilderFactory;
    private final LoadBalancerFactory<ResolvedAddress, RedisConnection> loadBalancerFactory;
    private final PartitionMapFactory partitionMapFactory;
    private final RedisClientConfig config;
    private int serviceDiscoveryMaxQueueSize = 32;
    private Function<RedisClient, RedisClient> clientFilterFunction = identity();
    private Function<RedisConnection, RedisConnection> connectionFilterFunction = identity();

    /**
     * Create a new instance.
     * @param loadBalancerFactory {@link LoadBalancerFactory} to use for creating new {@link LoadBalancer} instances.
     * @param redisPartitionAttributesBuilderFactory Supplies new {@link RedisPartitionAttributesBuilder} objects which
     * is used in {@link RedisCommander} and {@link BufferRedisCommander} when making requests.
     */
    public DefaultPartitionedRedisClientBuilder(
            LoadBalancerFactory<ResolvedAddress, RedisConnection> loadBalancerFactory,
            Function<Command, RedisPartitionAttributesBuilder> redisPartitionAttributesBuilderFactory) {
        this.redisPartitionAttributesBuilderFactory = requireNonNull(redisPartitionAttributesBuilderFactory);
        this.loadBalancerFactory = requireNonNull(loadBalancerFactory);
        this.partitionMapFactory = PowerSetPartitionMapFactory.INSTANCE;
        config = new RedisClientConfig(new TcpClientConfig(false));
    }

    /**
     * Create a new instance.
     * @param loadBalancerFactory A factory which generates {@link LoadBalancer} objects.
     */
    protected DefaultPartitionedRedisClientBuilder(
            LoadBalancerFactory<ResolvedAddress, RedisConnection> loadBalancerFactory) {
        redisPartitionAttributesBuilderFactory = null;
        this.loadBalancerFactory = requireNonNull(loadBalancerFactory);
        this.partitionMapFactory = PowerSetPartitionMapFactory.INSTANCE;
        config = new RedisClientConfig(new TcpClientConfig(false));
    }

    /**
     * Enable SSL/TLS using the provided {@link SslConfig}. To disable SSL pass in {@code null}.
     * @param config the {@link SslConfig}.
     * @return {@code this}.
     * @throws IllegalStateException if accessing the cert/key throws when {@link InputStream#close()} is called.
     */
    public DefaultPartitionedRedisClientBuilder<ResolvedAddress> setSsl(@Nullable SslConfig config) {
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
    public <T> DefaultPartitionedRedisClientBuilder<ResolvedAddress> setOption(SocketOption<T> option, T value) {
        config.getTcpClientConfig().setOption(option, value);
        return this;
    }

    /**
     * Enables wire-logging for the connections created by this builder.
     *
     * @param loggerName Name of the logger.
     * @return {@code this}.
     */
    public DefaultPartitionedRedisClientBuilder<ResolvedAddress> enableWireLog(String loggerName) {
        config.getTcpClientConfig().setWireLoggerName(loggerName);
        return this;
    }

    /**
     * Disabled wire-logging for the connections created by this builder.
     *
     * @return {@code this}.
     */
    public DefaultPartitionedRedisClientBuilder<ResolvedAddress> disableWireLog() {
        config.getTcpClientConfig().disableWireLog();
        return this;
    }

    /**
     * Sets maximum requests that can be pipelined on a connection created by this builder.
     *
     * @param maxPipelinedRequests Maximum number of pipelined requests per {@link RedisConnection}.
     * @return {@code this}.
     */
    public DefaultPartitionedRedisClientBuilder<ResolvedAddress> setMaxPipelinedRequests(int maxPipelinedRequests) {
        config.setMaxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    /**
     * Sets the idle timeout for connections created by this builder.
     *
     * @param idleConnectionTimeout the timeout {@link Duration} or {@code null} if no timeout configured.
     * @return {@code this}.
     */
    public DefaultPartitionedRedisClientBuilder<ResolvedAddress> setIdleConnectionTimeout(
            @Nullable Duration idleConnectionTimeout) {
        config.setIdleConnectionTimeout(idleConnectionTimeout);
        return this;
    }

    /**
     * Sets the ping period to keep alive connections created by this builder.
     *
     * @param pingPeriod the {@link Duration} between keep-alive pings or {@code null} to disable pings.
     * @return {@code this}.
     */
    public DefaultPartitionedRedisClientBuilder<ResolvedAddress> setPingPeriod(@Nullable final Duration pingPeriod) {
        config.setPingPeriod(pingPeriod);
        return this;
    }

    /**
     * Sets the maximum amount of {@link Event} objects that will be queued for each partition.
     * <p>It is assumed that the {@link Subscriber}s will process events in a timely manner (typically synchronously)
     * so this typically doesn't need to be very large.
     * @param serviceDiscoveryMaxQueueSize the maximum amount of {@link Event} objects that will be queued for each
     * partition.
     * @return {@code this}.
     */
    public DefaultPartitionedRedisClientBuilder<ResolvedAddress> setServiceDiscoveryMaxQueueSize(
            int serviceDiscoveryMaxQueueSize) {
        if (serviceDiscoveryMaxQueueSize <= 0) {
            throw new IllegalArgumentException("serviceDiscoveryMaxQueueSize: " + serviceDiscoveryMaxQueueSize
                    + " (expected >0)");
        }
        this.serviceDiscoveryMaxQueueSize = serviceDiscoveryMaxQueueSize;
        return this;
    }

    /**
     * Defines a filter {@link Function} to decorate {@link RedisClient} used by this builder.
     * <p>
     * Filtering allows you to wrap a {@link RedisClient} and modify behavior during request/response processing.
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     * @param clientFilterFunction {@link Function} to filter the used {@link RedisClient}.
     * @return {@code this}.
     */
    public DefaultPartitionedRedisClientBuilder<ResolvedAddress> setClientFilterFunction(
            Function<RedisClient, RedisClient> clientFilterFunction) {
        this.clientFilterFunction = requireNonNull(clientFilterFunction);
        return this;
    }

    /**
     * Defines a filter {@link Function} to decorate {@link RedisConnection} used by this builder.
     * <p>
     * Filtering allows you to wrap a {@link RedisConnection} and modify behavior during request/response processing.
     * Some potential candidates for filtering include logging, metrics, and decorating responses.
     * @param connectionFilterFunction {@link Function} to filter the used {@link RedisConnection}.
     * @return {@code this}.
     */
    public DefaultPartitionedRedisClientBuilder<ResolvedAddress> setConnectionFilterFunction(
            Function<RedisConnection, RedisConnection> connectionFilterFunction) {
        this.connectionFilterFunction = requireNonNull(connectionFilterFunction);
        return this;
    }

    @Override
    public PartitionedRedisClient build(ExecutionContext executionContext,
                                        Publisher<PartitionedEvent<ResolvedAddress>> addressEventStream) {
        return build(executionContext, addressEventStream, requireNonNull(redisPartitionAttributesBuilderFactory));
    }

    protected PartitionedRedisClient build(ExecutionContext executionContext,
              Publisher<PartitionedEvent<ResolvedAddress>> addressEventStream,
              Function<Command, RedisPartitionAttributesBuilder> redisPartitionAttributesBuilderFactory) {
        return new DefaultPartitionedRedisClient<>(executionContext, addressEventStream, clientFilterFunction,
                config, connectionFilterFunction, loadBalancerFactory,
                requireNonNull(redisPartitionAttributesBuilderFactory),
                partitionMapFactory.newPartitionMap(Partition::new), serviceDiscoveryMaxQueueSize);
    }

    private static final class DefaultPartitionedRedisClient<ResolvedAddress> extends PartitionedRedisClient {
        private final Function<Command, RedisPartitionAttributesBuilder> redisPartitionAttributesBuilderFactory;
        private final SequentialCancellable sequentialCancellable;
        private final PartitionMap<Partition> partitionMap;
        private final ExecutionContext executionContext;

        DefaultPartitionedRedisClient(ExecutionContext executionContext,
                                      Publisher<PartitionedEvent<ResolvedAddress>> addressEventStream,
                                      Function<RedisClient, RedisClient> clientFilterFactory,
                                      RedisClientConfig config,
                                      Function<RedisConnection, RedisConnection> connectionFilterFactory,
                                      LoadBalancerFactory<ResolvedAddress, RedisConnection> loadBalancerFactory,
                                      Function<Command, RedisPartitionAttributesBuilder> redisPartitionAttributesBuilderFactory,
                                      PartitionMap<Partition> partitionMap,
                                      int serviceDiscoveryMaxQueueSize) {
            this.executionContext = executionContext;
            this.redisPartitionAttributesBuilderFactory = redisPartitionAttributesBuilderFactory;
            this.partitionMap = partitionMap;
            ReadOnlyRedisClientConfig roConfig = config.asReadOnly();
            RedisClientBuilder<ResolvedAddress, PartitionedEvent<ResolvedAddress>> redisClientBuilder =
                    (executionContext1, address) ->
                            clientFilterFactory.apply(new DefaultRedisClient<>(executionContext1, roConfig,
                                    address, connectionFilterFactory, loadBalancerFactory));
            sequentialCancellable = new SequentialCancellable();

            addressEventStream.groupByMulti(event -> event.isAvailable() ?
                            partitionMap.addPartition(event.getPartitionAddress()).iterator() :
                            partitionMap.removePartition(event.getPartitionAddress()).iterator(),
                    serviceDiscoveryMaxQueueSize)
                    .subscribe(new Subscriber<Group<Partition, PartitionedEvent<ResolvedAddress>>>() {
                        @Override
                        public void onSubscribe(Subscription s) {
                            // We request max value here to make sure we do not access Subscription concurrently
                            // (requestN here and cancel from discoveryCancellable). If we request-1 in onNext we would
                            // have to wrap the Subscription in a ConcurrentSubscription which is costly.
                            // Since, we synchronously process onNexts we do not really care about flow control.
                            s.request(Long.MAX_VALUE);
                            sequentialCancellable.setNextCancellable(s::cancel);
                        }

                        @Override
                        public void onNext(Group<Partition, PartitionedEvent<ResolvedAddress>> newGroup) {
                            final Partition partition = newGroup.getKey();
                            partition.setClient(newRedisClient(executionContext, redisClientBuilder, newGroup,
                                    partition));
                        }

                        @Override
                        public void onError(Throwable t) {
                            LOGGER.info("Unexpected error in partitioned client group subscriber {}", this, t);
                            // Don't force close the client if SD has an error, just make a best effort to keep going.
                        }

                        @Override
                        public void onComplete() {
                            // Don't force close the client if SD has an error, just make a best effort to keep going.
                            LOGGER.debug("partitioned client group subscriber {} terminated", this);
                        }
                    });
        }

        private static <ResolvedAddress> RedisClient newRedisClient(ExecutionContext executionContext,
                RedisClientBuilder<ResolvedAddress, PartitionedEvent<ResolvedAddress>> redisClientBuilder,
                Group<Partition, PartitionedEvent<ResolvedAddress>> newGroup, Partition partition) {
            return redisClientBuilder.build(executionContext,
                    newGroup.getPublisher().filter(new Predicate<PartitionedEvent<ResolvedAddress>>() {
                        // Use a mutable Count to avoid boxing-unboxing and put on each call.
                        private final Map<ResolvedAddress, MutableInteger> addressesToCount = new HashMap<>();

                        @Override
                        public boolean test(PartitionedEvent<ResolvedAddress> evt) {
                            MutableInteger count = addressesToCount.computeIfAbsent(evt.getAddress(),
                                    addr -> new MutableInteger());
                            boolean acceptEvent;
                            if (evt.isAvailable()) {
                                acceptEvent = ++count.count == 1;
                            } else {
                                acceptEvent = --count.count == 0;
                                if (acceptEvent) {
                                    // If address is unavailable and no more add events are pending stop tracking and
                                    // close partition.
                                    addressesToCount.remove(evt.getAddress());
                                    if (addressesToCount.isEmpty()) {
                                        // closeNow will subscribe to closeAsync() so we do not have to here.
                                        partition.closeNow();
                                    }
                                }
                            }
                            return acceptEvent;
                        }
                    }).doBeforeFinally(partition::closeNow)
            );
        }

        @Nullable
        private RedisClient lookupPartitionedClientOrFailSubscriber(PartitionAttributes selector,
                                                                    Single.Subscriber<?> subscriber) {
            final Partition partition = partitionMap.getPartition(selector);
            final RedisClient client;
            if (partition == null || (client = partition.getClient()) == null) {
                subscriber.onSubscribe(IGNORE_CANCEL);
                subscriber.onError(newUnknownPartitionException(selector, partition));
                return null;
            }
            return client;
        }

        @Override
        public Single<ReservedRedisConnection> reserveConnection(PartitionAttributes partitionSelector,
                                                                 RedisRequest request) {
            return new Single<ReservedRedisConnection>() {
                @Override
                protected void handleSubscribe(Subscriber<? super ReservedRedisConnection> subscriber) {
                    RedisClient client = lookupPartitionedClientOrFailSubscriber(partitionSelector, subscriber);
                    if (client != null) {
                        client.reserveConnection(request).subscribe(subscriber);
                    }
                }
            };
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return executionContext;
        }

        @Override
        protected Function<Command, RedisPartitionAttributesBuilder> getRedisPartitionAttributesBuilderFactory() {
            return redisPartitionAttributesBuilderFactory;
        }

        @Override
        public Publisher<RedisData> request(PartitionAttributes partitionSelector, RedisRequest request) {
            return new Publisher<RedisData>(executionContext.getExecutor()) {
                @Override
                protected void handleSubscribe(Subscriber<? super RedisData> subscriber) {
                    final Partition partition = partitionMap.getPartition(partitionSelector);
                    final RedisClient client;
                    if (partition == null || (client = partition.getClient()) == null) {
                        subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                        subscriber.onError(newUnknownPartitionException(partitionSelector, partition));
                        return;
                    }
                    client.request(request).subscribe(subscriber);
                }
            };
        }

        @Override
        public <R> Single<R> request(PartitionAttributes partitionSelector, RedisRequest request,
                                     Class<R> responseType) {
            return new Single<R>() {
                @Override
                protected void handleSubscribe(Subscriber<? super R> subscriber) {
                    RedisClient client = lookupPartitionedClientOrFailSubscriber(partitionSelector, subscriber);
                    if (client != null) {
                        client.request(request, responseType).subscribe(subscriber);
                    }
                }
            };
        }

        private static UnknownPartitionException newUnknownPartitionException(PartitionAttributes partitionSelector,
                                                                              @Nullable Partition partition) {
            return new UnknownPartitionException(partitionSelector,
                    (partition == null ? "partition not found"
                            : "no client for partition") + " [" + partitionSelector + ']');
        }

        @Override
        public Completable onClose() {
            return partitionMap.onClose();
        }

        @Override
        public Completable closeAsync() {
            // Cancel doesn't provide any status and is assumed to complete immediately so we just cancel when subscribe
            // is called.
            return partitionMap.closeAsync().doBeforeSubscribe($ -> sequentialCancellable.cancel());
        }
    }

    private static final class Partition implements AsyncCloseable {
        private static final AtomicReferenceFieldUpdater<Partition, RedisClient> clientUpdater =
                AtomicReferenceFieldUpdater.newUpdater(Partition.class, RedisClient.class, "client");

        private final PartitionAttributes attributes;

        @SuppressWarnings("unused")
        @Nullable
        private volatile RedisClient client;

        Partition(PartitionAttributes attributes) {
            this.attributes = attributes;
        }

        void setClient(RedisClient client) {
            if (!clientUpdater.compareAndSet(this, null, client)) {
                client.closeAsync().subscribe();
            }
        }

        void closeNow() {
            closeAsync().subscribe();
        }

        @Nullable
        RedisClient getClient() {
            return client;
        }

        @Override
        public Completable closeAsync() {
            return new Completable() {
                @Override
                protected void handleSubscribe(Subscriber subscriber) {
                    RedisClient oldClient = clientUpdater.getAndSet(Partition.this,
                            new NoopRedisClient(client.getExecutionContext()));
                    if (oldClient != null) {
                        oldClient.closeAsync().subscribe(subscriber);
                    } else {
                        subscriber.onSubscribe(IGNORE_CANCEL);
                        subscriber.onComplete();
                    }
                }
            };
        }

        @Override
        public String toString() {
            return attributes.toString();
        }
    }

    private static final class NoopRedisClient extends RedisClient {
        private final ExecutionContext executionContext;

        private NoopRedisClient(ExecutionContext executionContext) {
            this.executionContext = requireNonNull(executionContext);
        }

        @Override
        public Single<ReservedRedisConnection> reserveConnection(RedisRequest request) {
            return Single.error(new UnsupportedOperationException());
        }

        @Override
        public Publisher<RedisData> request(RedisRequest request) {
            return error(new UnsupportedOperationException(), executionContext.getExecutor());
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return executionContext;
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

    /**
     * A mutable count.
     */
    private static final class MutableInteger {
        int count;
    }
}
