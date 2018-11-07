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
import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.client.api.partition.PartitionMap;
import io.servicetalk.client.api.partition.PartitionMapFactory;
import io.servicetalk.client.api.partition.PartitionedServiceDiscovererEvent;
import io.servicetalk.client.api.partition.UnknownPartitionException;
import io.servicetalk.client.internal.partition.PowerSetPartitionMapFactory;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.GroupedPublisher;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.redis.api.PartitionedRedisClient;
import io.servicetalk.redis.api.PartitionedRedisClientBuilder;
import io.servicetalk.redis.api.PartitionedRedisClientFilterFactory;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisClient.ReservedRedisConnection;
import io.servicetalk.redis.api.RedisClientFilterFactory;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisConnectionFilterFactory;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisPartitionAttributesBuilder;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.SslConfig;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.Publisher.error;
import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.redis.api.PartitionedRedisClientFilterFactory.identity;
import static java.util.Objects.requireNonNull;

/**
 * A builder for instances of {@link PartitionedRedisClient}.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
final class DefaultPartitionedRedisClientBuilder<U, R> implements PartitionedRedisClientBuilder<U, R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPartitionedRedisClientBuilder.class);

    private final DefaultRedisClientBuilder<U, R> builderTemplate;
    private final Function<Command, RedisPartitionAttributesBuilder> partitionAttributesBuilderFactory;
    private PartitionMapFactory partitionMapFactory = PowerSetPartitionMapFactory.INSTANCE;
    private int serviceDiscoveryMaxQueueSize = 32;
    private PartitionedRedisClientFilterFactory partitionedClientFilterFactory = identity();

    private DefaultPartitionedRedisClientBuilder(DefaultRedisClientBuilder<U, R> builderTemplate,
                                                 Function<Command, RedisPartitionAttributesBuilder> factory) {
        this.builderTemplate = builderTemplate;
        this.partitionAttributesBuilderFactory = requireNonNull(factory);
    }

    @Override
    public PartitionedRedisClientBuilder<U, R> ioExecutor(final IoExecutor ioExecutor) {
        builderTemplate.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public PartitionedRedisClientBuilder<U, R> executor(final Executor executor) {
        builderTemplate.executor(executor);
        return this;
    }

    @Override
    public PartitionedRedisClientBuilder<U, R> bufferAllocator(final BufferAllocator allocator) {
        builderTemplate.bufferAllocator(allocator);
        return this;
    }

    @Override
    public DefaultPartitionedRedisClientBuilder<U, R> sslConfig(@Nullable SslConfig config) {
        builderTemplate.sslConfig(config);
        return this;
    }

    @Override
    public <T> DefaultPartitionedRedisClientBuilder<U, R> socketOption(SocketOption<T> option, T value) {
        builderTemplate.socketOption(option, value);
        return this;
    }

    @Override
    public DefaultPartitionedRedisClientBuilder<U, R> enableWireLogging(String loggerName) {
        builderTemplate.enableWireLogging(loggerName);
        return this;
    }

    @Override
    public DefaultPartitionedRedisClientBuilder<U, R> disableWireLogging() {
        builderTemplate.disableWireLogging();
        return this;
    }

    @Override
    public DefaultPartitionedRedisClientBuilder<U, R> maxPipelinedRequests(int maxPipelinedRequests) {
        builderTemplate.maxPipelinedRequests(maxPipelinedRequests);
        return this;
    }

    @Override
    public DefaultPartitionedRedisClientBuilder<U, R> idleConnectionTimeout(
            @Nullable Duration idleConnectionTimeout) {
        builderTemplate.idleConnectionTimeout(idleConnectionTimeout);
        return this;
    }

    @Override
    public DefaultPartitionedRedisClientBuilder<U, R> pingPeriod(@Nullable final Duration pingPeriod) {
        builderTemplate.pingPeriod(pingPeriod);
        return this;
    }

    @Override
    public DefaultPartitionedRedisClientBuilder<U, R> serviceDiscoveryMaxQueueSize(
            int serviceDiscoveryMaxQueueSize) {
        if (serviceDiscoveryMaxQueueSize <= 0) {
            throw new IllegalArgumentException("serviceDiscoveryMaxQueueSize: " + serviceDiscoveryMaxQueueSize
                    + " (expected >0)");
        }
        this.serviceDiscoveryMaxQueueSize = serviceDiscoveryMaxQueueSize;
        return this;
    }

    @Override
    public DefaultPartitionedRedisClientBuilder<U, R> appendClientFilter(RedisClientFilterFactory factory) {
        builderTemplate.appendClientFilter(factory);
        return this;
    }

    @Override
    public DefaultPartitionedRedisClientBuilder<U, R> appendConnectionFilter(RedisConnectionFilterFactory factory) {
        builderTemplate.appendConnectionFilter(factory);
        return this;
    }

    @Override
    public DefaultPartitionedRedisClientBuilder<U, R> appendConnectionFactoryFilter(
            final ConnectionFactoryFilter<R, RedisConnection> factory) {
        builderTemplate.appendConnectionFactoryFilter(factory);
        return this;
    }

    @Override
    public DefaultPartitionedRedisClientBuilder<U, R> appendPartitionedFilter(
            PartitionedRedisClientFilterFactory factory) {
        partitionedClientFilterFactory = partitionedClientFilterFactory.append(factory);
        return this;
    }

    @Override
    public DefaultPartitionedRedisClientBuilder<U, R> disableWaitForLoadBalancer() {
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
        return partitionedClientFilterFactory.apply(new DefaultPartitionedRedisClient<>(builderTemplate,
                partitionAttributesBuilderFactory, partitionMapFactory.newPartitionMap(Partition::new),
                serviceDiscoveryMaxQueueSize));
    }

    static <U, R> DefaultPartitionedRedisClientBuilder<U, R> forAddress(
            final U address,
            final ServiceDiscoverer<U, R, ? extends PartitionedServiceDiscovererEvent<R>> sd,
            final Function<Command, RedisPartitionAttributesBuilder> partitionAttributesBuilderFactory) {
        DefaultRedisClientBuilder<U, R> clientBuilder = new DefaultRedisClientBuilder<>(sd, address);
        return new DefaultPartitionedRedisClientBuilder<>(clientBuilder, partitionAttributesBuilderFactory);
    }

    private static final class DefaultPartitionedRedisClient<U, R> extends PartitionedRedisClient {

        private final Function<Command, RedisPartitionAttributesBuilder> partitionAttributesBuilderFactory;
        private final SequentialCancellable sequentialCancellable;
        private final PartitionMap<Partition> partitionMap;
        private final ExecutionContext executionContext;

        DefaultPartitionedRedisClient(DefaultRedisClientBuilder<U, R> builderTemplate,
                                      Function<Command, RedisPartitionAttributesBuilder> factory,
                                      PartitionMap<Partition> partitionMap,
                                      int serviceDiscoveryMaxQueueSize) {
            this.partitionAttributesBuilderFactory = factory;
            sequentialCancellable = new SequentialCancellable();
            this.partitionMap = partitionMap;
            final DefaultRedisClientBuilder<U, R> builder = builderTemplate.copy();
            executionContext = builder.buildExecutionContext();
            @SuppressWarnings("unchecked")
            ServiceDiscoverer<U, R, PartitionedServiceDiscovererEvent<R>> sd =
                    (ServiceDiscoverer<U, R, PartitionedServiceDiscovererEvent<R>>) builder.serviceDiscoverer();
            sd.discover(builder.address()).groupByMulti(event -> event.available() ?
                            partitionMap.add(event.partitionAddress()).iterator() :
                            partitionMap.remove(event.partitionAddress()).iterator(),
                    serviceDiscoveryMaxQueueSize)
                    .subscribe(new Subscriber<GroupedPublisher<Partition, PartitionedServiceDiscovererEvent<R>>>() {
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
                        public void onNext(GroupedPublisher<Partition, PartitionedServiceDiscovererEvent<R>> newGroup) {
                            final Partition partition = newGroup.getKey();
                            partition.setClient(builder.build(new PartitionServiceDiscoverer<>(newGroup, partition),
                                    executionContext));
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

        @Nullable
        private RedisClient lookupPartitionedClientOrFailSubscriber(PartitionAttributes selector,
                                                                    Single.Subscriber<?> subscriber) {
            final Partition partition = partitionMap.get(selector);
            final RedisClient client;
            if (partition == null || (client = partition.getClient()) == null) {
                subscriber.onSubscribe(IGNORE_CANCEL);
                subscriber.onError(newUnknownPartitionException(selector, partition));
                return null;
            }
            return client;
        }

        @Override
        public Single<? extends ReservedRedisConnection> reserveConnection(PartitionAttributes partitionSelector,
                                                                           Command command) {
            return new Single<ReservedRedisConnection>() {
                @Override
                protected void handleSubscribe(Subscriber<? super ReservedRedisConnection> subscriber) {
                    RedisClient client = lookupPartitionedClientOrFailSubscriber(partitionSelector, subscriber);
                    if (client != null) {
                        client.reserveConnection(command).subscribe(subscriber);
                    }
                }
            };
        }

        @Override
        public ExecutionContext executionContext() {
            return executionContext;
        }

        @Override
        protected Function<Command, RedisPartitionAttributesBuilder> redisPartitionAttributesBuilderFunction() {
            return partitionAttributesBuilderFactory;
        }

        @Override
        public Publisher<RedisData> request(PartitionAttributes partitionSelector, RedisRequest request) {
            return new Publisher<RedisData>() {
                @Override
                protected void handleSubscribe(Subscriber<? super RedisData> subscriber) {
                    final Partition partition = partitionMap.get(partitionSelector);
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
        public <Resp> Single<Resp> request(PartitionAttributes partitionSelector, RedisRequest request,
                                     Class<Resp> responseType) {
            return new Single<Resp>() {
                @Override
                protected void handleSubscribe(Subscriber<? super Resp> subscriber) {
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
            return partitionMap.closeAsync().doBeforeSubscribe(__ -> sequentialCancellable.cancel());
        }

        @Override
        public Completable closeAsyncGracefully() {
            // Cancel doesn't provide any status and is assumed to complete immediately so we just cancel when subscribe
            // is called.
            return partitionMap.closeAsyncGracefully().doBeforeSubscribe(__ -> sequentialCancellable.cancel());
        }
    }

    private static final class PartitionServiceDiscoverer<U, R>
            implements ServiceDiscoverer<U, R, PartitionedServiceDiscovererEvent<R>> {
        private final ListenableAsyncCloseable close;
        private final GroupedPublisher<Partition, PartitionedServiceDiscovererEvent<R>> newGroup;
        private final Partition partition;

        PartitionServiceDiscoverer(final GroupedPublisher<Partition, PartitionedServiceDiscovererEvent<R>> newGroup,
                                   final Partition partition) {
            this.newGroup = newGroup;
            this.partition = partition;
            close = emptyAsyncCloseable();
        }

        @Override
        public Publisher<PartitionedServiceDiscovererEvent<R>> discover(final U ignored) {
            return newGroup.filter(new Predicate<PartitionedServiceDiscovererEvent<R>>() {
                // Use a mutable Count to avoid boxing-unboxing and put on each call.
                private final Map<R, MutableInteger> addressesToCount = new HashMap<>();

                @Override
                public boolean test(PartitionedServiceDiscovererEvent<R> evt) {
                    MutableInteger count = addressesToCount.computeIfAbsent(evt.address(),
                            addr -> new MutableInteger());
                    boolean acceptEvent;
                    if (evt.available()) {
                        acceptEvent = ++count.count == 1;
                    } else {
                        acceptEvent = --count.count == 0;
                        if (acceptEvent) {
                            // If address is unavailable and no more add events are pending stop tracking and
                            // close partition.
                            addressesToCount.remove(evt.address());
                            if (addressesToCount.isEmpty()) {
                                // closeNow will subscribe to closeAsync() so we do not have to here.
                                partition.closeNow();
                            }
                        }
                    }
                    return acceptEvent;
                }
            }).doBeforeFinally(partition::closeNow);
        }

        @Override
        public Completable onClose() {
            return close.onClose();
        }

        @Override
        public Completable closeAsync() {
            return close.closeAsync();
        }
    }

    private static final class Partition implements AsyncCloseable {

        private static final AtomicReferenceFieldUpdater<Partition, RedisClient> clientUpdater =
                AtomicReferenceFieldUpdater.newUpdater(Partition.class, RedisClient.class, "client");

        private final PartitionAttributes attributes;

        @SuppressWarnings("unused")
        @Nullable
        private volatile RedisClient client;

        Partition(final PartitionAttributes attributes) {
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
                    RedisClient oldClient = clientUpdater.getAndSet(Partition.this, ClosedClient.CLOSED_CLIENT);
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

    private static final class ClosedClient extends RedisClient {

        private static final ClosedClient CLOSED_CLIENT = new ClosedClient();

        private ClosedClient() {
            // Singleton
        }

        @Override
        public Single<? extends ReservedRedisConnection> reserveConnection(Command command) {
            return Single.error(new IllegalStateException("Partition is closed."));
        }

        @Override
        public Publisher<RedisData> request(RedisRequest request) {
            return error(new IllegalStateException("Partition is closed."));
        }

        @Override
        public ExecutionContext executionContext() {
            throw new IllegalStateException("Partition is closed.");
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
