/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.client.api.internal.LoadBalancerReadyEvent;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SpScPublisherProcessor;
import io.servicetalk.concurrent.internal.SequentialCancellable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.loadbalancer.LoadBalancerUtils.noAvailableConnectionSelectMatchCnxException;

public final class DynamicApertureLoadBalancer<R, C extends LoadBalancedConnection> implements LoadBalancer<C> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicApertureLoadBalancer.class);

    private final DynamicApertureListBasedAddressSelector<C> addressSelector;
    private final DefaultListBasedConnectionSelector<C> connSelector;
    private final SpScPublisherProcessor<Object> eventStream = new SpScPublisherProcessor<>(32);
    private final ListenableAsyncCloseable closeable;

    public DynamicApertureLoadBalancer(final Publisher<? extends ServiceDiscovererEvent<R>> sdePublisher,
                                       final ConnectionFactory<R, C> connectionFactory) {
        this(sdePublisher, connectionFactory, 0.5f, 0.95f, Duration.ofSeconds(30), 3, 5);
    }

    public DynamicApertureLoadBalancer(final Publisher<? extends ServiceDiscovererEvent<R>> sdePublisher,
                                       final ConnectionFactory<R, C> connectionFactory,
                                       final float bottomScore,
                                       final float topScore,
                                       final Duration apertureRefreshTime,
                                       final int maxEffortAddress,
                                       final int maxEffortConnection) {

        final List<AsyncCloseable> closeables = new ArrayList<>();
        final DefaultAddressFactory<R, C> addressFactory = new DefaultAddressFactory<>(connectionFactory,
                (evt, cf) -> new ConnectionAwareLoadBalancedAddress<>(evt.address(), cf));
        closeables.add(addressFactory);

        addressSelector = new DynamicApertureListBasedAddressSelector<>(bottomScore, topScore,
                apertureRefreshTime, new P2CSelector<>(maxEffortAddress));
        closeables.add(addressSelector);

        connSelector = new DefaultListBasedConnectionSelector<>(new P2CSelector<>(maxEffortConnection));
        closeables.add(connSelector);

        final Cancellable cancellable = subscribeToServiceDiscovery(
                sdePublisher.map(addressFactory).filter(Objects::nonNull), // NULL when addressFactory closed
                addressSelector::add,
                address -> {
                    addressSelector.remove(address);
                    // TODO(jayv) timeout?
                    address.closeAsyncGracefully().subscribe();
                },
                addressSelector::isReady,
                eventStream);

        closeable = LoadBalancerUtils.newCloseable(() -> {
            cancellable.cancel();
            return closeables;
        });
    }

    @Override
    public Single<C> selectConnection(final Predicate<C> selector) {
        return Single.defer(() -> select0(selector).subscribeShareContext());
    }

    private Single<C> select0(final Predicate<C> selector) {
        addressSelector.optimizeAperture(false);
        return connSelector.select(selector)
                .recoverWith(ex -> {
                    addressSelector.optimizeAperture(true);
                    return addressSelector.select().flatMap(addr ->
                            addr.newConnection()
                                    .beforeOnSuccess(connSelector::add)
                                    .flatMap(lc -> selector.test(lc) ?
                                            Single.succeeded(lc) :
                                            Single.failed(noAvailableConnectionSelectMatchCnxException()))
                    );
                });
    }

    @Override
    public Publisher<Object> eventStream() {
        return eventStream;
    }

    @Override
    public Completable onClose() {
        return closeable.onClose();
    }

    @Override
    public Completable closeAsync() {
        return closeable.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return closeable.closeAsyncGracefully();
    }

    static <A, SDE extends ServiceDiscovererEvent<A>>
    Cancellable subscribeToServiceDiscovery(final Publisher<SDE> sdePublisher,
                                            final Consumer<A> addressAdded,
                                            final Consumer<A> addressRemoved,
                                            final BooleanSupplier addressesReady,
                                            final SpScPublisherProcessor<Object> eventStream) {

        final SequentialCancellable discoveryCancellable = new SequentialCancellable();

        toSource(sdePublisher.beforeCancel(eventStream::sendOnComplete))
                .subscribe(new PublisherSource.Subscriber<SDE>() {

                    private boolean currentReadyState;

                    @Override
                    public void onSubscribe(final PublisherSource.Subscription subscription) {
                        subscription.request(Long.MAX_VALUE);
                        discoveryCancellable.nextCancellable(subscription);
                    }

                    @Override
                    public void onNext(@Nullable final SDE event) {
                        assert event != null;
                        if (event.isAvailable()) {
                            addressAdded.accept(event.address());
                        } else {
                            addressRemoved.accept(event.address());
                        }

                        // TODO(jayv): not convinced this is a good API, addressSelector
                        //  could do the wait/fail fast/timeout?
                        final boolean newReadyState = addressesReady.getAsBoolean();
                        if (currentReadyState != newReadyState) {
                            boolean ready = (currentReadyState = newReadyState);
                            if (ready) {
                                eventStream.sendOnNext(LoadBalancerReadyEvent.LOAD_BALANCER_READY_EVENT);
                            } else {
                                eventStream.sendOnNext(LoadBalancerReadyEvent.LOAD_BALANCER_NOT_READY_EVENT);
                            }
                        }
                    }

                    @Override
                    public void onError(final Throwable t) {
                        eventStream.sendOnError(t);
                        LOGGER.error("Load balancer Service discoverer emitted an error.", t);
                    }

                    @Override
                    public void onComplete() {
                        eventStream.sendOnComplete();
                        LOGGER.error("Load balancer Service discoverer completed.");
                    }
                });

        return discoveryCancellable;
    }
}
