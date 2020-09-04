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
package io.servicetalk.client.api.internal;

import io.servicetalk.client.api.ClientGroup;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.client.api.partition.PartitionMap;
import io.servicetalk.client.api.partition.PartitionMapFactory;
import io.servicetalk.client.api.partition.PartitionedServiceDiscovererEvent;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.GroupedPublisher;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.internal.SubscribableCompletable;
import io.servicetalk.concurrent.internal.SequentialCancellable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverCompleteFromSource;
import static java.util.Objects.requireNonNull;

/**
 * An implementation of {@link ClientGroup} that can be used for partitioned client use-cases where {@link
 * PartitionAttributes} are discovered through {@link PartitionedServiceDiscovererEvent}s.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 * @param <Client> the type of client to connect to the partitions
 */
public final class DefaultPartitionedClientGroup<U, R, Client extends ListenableAsyncCloseable>
        implements ClientGroup<PartitionAttributes, Client> {

    /**
     * Factory for building partitioned clients.
     * @param <U> the type of address before resolution (unresolved address)
     * @param <R> the type of address after resolution (resolved address)
     * @param <Client> the type of client to connect to the partitions
     */
    @FunctionalInterface
    public interface PartitionedClientFactory<U, R, Client> {
        /**
         * Create a partitioned client.
         *
         * @param pa the {@link PartitionAttributes} for this client
         * @param psd the partitioned {@link ServiceDiscoverer}
         * @return new client for the given arguments
         */
        Client apply(PartitionAttributes pa, ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> psd);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPartitionedClientGroup.class);

    private final PartitionMap<Partition<Client>> partitionMap;
    private final SequentialCancellable sequentialCancellable = new SequentialCancellable();
    private final Function<PartitionAttributes, Client> unknownPartitionClient;

    /**
     * Creates a new instance.
     *
     * @param closedPartitionClient factory for clients that handle requests for a closed partition
     * @param unknownPartitionClient factory for clients that handles requests for an unknown partition
     * @param clientFactory used to create clients for newly discovered partitions
     * @param partitionMapFactory factory to provide a {@link PartitionMap} implementation appropriate for the use-case
     * @param psdEvents the stream of {@link PartitionedServiceDiscovererEvent}s
     * @param psdMaxQueueSize max number of new partitions to queue up
     */
    public DefaultPartitionedClientGroup(final Function<PartitionAttributes, Client> closedPartitionClient,
                                         final Function<PartitionAttributes, Client> unknownPartitionClient,
                                         final PartitionedClientFactory<U, R, Client> clientFactory,
                                         final PartitionMapFactory partitionMapFactory,
                                         final Publisher<? extends PartitionedServiceDiscovererEvent<R>> psdEvents,
                                         final int psdMaxQueueSize) {

        this.unknownPartitionClient = unknownPartitionClient;
        this.partitionMap = partitionMapFactory.newPartitionMap(event ->
                new Partition<>(event, closedPartitionClient.apply(event)));
        toSource(psdEvents.groupToMany(event -> event.isAvailable() ?
                partitionMap.add(event.partitionAddress()).iterator() :
                partitionMap.remove(event.partitionAddress()).iterator(), psdMaxQueueSize))
                .subscribe(new GroupedByPartitionSubscriber(clientFactory));
    }

    @Override
    public Completable onClose() {
        return partitionMap.onClose();
    }

    @Override
    public Completable closeAsync() {
        // Cancel doesn't provide any status and is assumed to complete immediately so we just cancel when subscribe
        // is called.
        return partitionMap.closeAsync().whenFinally(sequentialCancellable::cancel);
    }

    @Override
    public Completable closeAsyncGracefully() {
        // Cancel doesn't provide any status and is assumed to complete immediately so we just cancel when subscribe
        // is called.
        return partitionMap.closeAsyncGracefully().whenFinally(sequentialCancellable::cancel);
    }

    @Override
    public Client get(final PartitionAttributes partitionAttributes) {
        final Partition<Client> partition = partitionMap.get(partitionAttributes);
        final Client client;
        if (partition == null || (client = partition.client()) == null) {
            return unknownPartitionClient.apply(partitionAttributes);
        }
        return client;
    }

    private static final class PartitionServiceDiscoverer<U, R, C extends AsyncCloseable,
            PSDE extends PartitionedServiceDiscovererEvent<R>>
            implements ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> {
        private final ListenableAsyncCloseable close;
        private final GroupedPublisher<Partition<C>, PSDE> newGroup;
        private final Partition<C> partition;

        PartitionServiceDiscoverer(final GroupedPublisher<Partition<C>, PSDE> newGroup) {
            this.newGroup = newGroup;
            this.partition = newGroup.key();
            close = emptyAsyncCloseable();
        }

        /**
         * @param ignoredAddress the address is ignored since discovery already happened
         * @return stream of {@link PartitionedServiceDiscovererEvent}s for this partitions with valid addresses
         */
        @Override
        public Publisher<ServiceDiscovererEvent<R>> discover(final U ignoredAddress) {
            return newGroup.filter(new Predicate<PSDE>() {
                // Use a mutable Count to avoid boxing-unboxing and put on each call.
                private final Map<R, MutableInt> addressCount = new HashMap<>();

                @Override
                public boolean test(PSDE evt) {
                    MutableInt counter = addressCount.computeIfAbsent(evt.address(), __ -> new MutableInt());
                    boolean acceptEvent;
                    if (evt.isAvailable()) {
                        acceptEvent = ++counter.value == 1;
                    } else {
                        acceptEvent = --counter.value == 0;
                        if (acceptEvent) {
                            // If address is unavailable and no more add events are pending stop tracking and
                            // close partition.
                            addressCount.remove(evt.address());
                            if (addressCount.isEmpty()) {
                                // closeNow will subscribe to closeAsync() so we do not have to here.
                                partition.closeNow();
                            }
                        }
                    }
                    return acceptEvent;
                }
            }).beforeFinally(partition::closeNow).map(psde -> psde);
        }

        @Override
        public Completable onClose() {
            return close.onClose();
        }

        @Override
        public Completable closeAsync() {
            return close.closeAsync();
        }

        static final class MutableInt {
            int value;
        }
    }

    private static final class Partition<C extends AsyncCloseable> implements AsyncCloseable {
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<Partition, Object> clientUpdater =
                AtomicReferenceFieldUpdater.newUpdater(Partition.class, Object.class, "client");

        private final PartitionAttributes attributes;
        private final C closed;

        @Nullable
        private volatile Object client;

        Partition(PartitionAttributes attributes, C closed) {
            this.attributes = requireNonNull(attributes, "PartitionAttributes for partition is null");
            this.closed = requireNonNull(closed, "Closed Client for partition is null");
        }

        void client(C client) {
            if (!clientUpdater.compareAndSet(this, null, client)) {
                client.closeAsync().subscribe();
            }
        }

        void closeNow() {
            closeAsync().subscribe();
        }

        @Nullable
        @SuppressWarnings("unchecked")
        C client() {
            return (C) client;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Completable closeAsync() {
            return new SubscribableCompletable() {
                @Override
                protected void handleSubscribe(CompletableSource.Subscriber subscriber) {
                    Object oldClient = clientUpdater.getAndSet(DefaultPartitionedClientGroup.Partition.this, closed);
                    if (oldClient != null && oldClient != closed) {
                        toSource(((C) oldClient).closeAsync()).subscribe(subscriber);
                    } else {
                        deliverCompleteFromSource(subscriber);
                    }
                }
            };
        }

        @Override
        public String toString() {
            return attributes.toString();
        }
    }

    private final class GroupedByPartitionSubscriber
            implements PublisherSource.Subscriber<GroupedPublisher<Partition<Client>,
            ? extends PartitionedServiceDiscovererEvent<R>>> {

        private final PartitionedClientFactory<U, R, Client> clientFactory;

        GroupedByPartitionSubscriber(final PartitionedClientFactory<U, R, Client> clientFactory) {
            this.clientFactory = clientFactory;
        }

        @Override
        public void onSubscribe(final Subscription s) {
            // We request max value here to make sure we do not access Subscription concurrently
            // (requestN here and cancel from discoveryCancellable). If we request-1 in onNext we would
            // have to wrap the Subscription in a ConcurrentSubscription which is costly.
            // Since, we synchronously process onNexts we do not really care about flow control.
            s.request(Long.MAX_VALUE);
            sequentialCancellable.nextCancellable(s);
        }

        @Override
        public void onNext(@Nonnull final GroupedPublisher<Partition<Client>,
                        ? extends PartitionedServiceDiscovererEvent<R>> newGroup) {
            requireNonNull(newGroup);
            Client newClient = requireNonNull(clientFactory.apply(newGroup.key().attributes,
                    new PartitionServiceDiscoverer<>(newGroup)), "<null> Client created for partition");
            newGroup.key().client(newClient);
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
    }
}
