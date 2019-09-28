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

import io.servicetalk.client.api.LoadBalancedAddress;
import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.loadbalancer.LoadBalancerUtils.LB_CLOSED_SELECT_CNX_EXCEPTION;
import static io.servicetalk.loadbalancer.LoadBalancerUtils.NO_ACTIVE_HOSTS_SELECT_CNX_EXCEPTION;
import static io.servicetalk.loadbalancer.LoadBalancerUtils.noAvailableAddressesSelectMatchCnxException;
import static io.servicetalk.loadbalancer.LoadBalancerUtils.selectAddressFailedSelectCnxException;

final class DynamicApertureListBasedAddressSelector<C extends LoadBalancedConnection> implements AddressSelector<C> {

    private static final AtomicLongFieldUpdater<DynamicApertureListBasedAddressSelector> apertureRefreshTimeUpdater =
            AtomicLongFieldUpdater.newUpdater(DynamicApertureListBasedAddressSelector.class, "apertureRefreshTime");

    private final long apertureRefreshNs;
    private final float bottomScore;
    private final float upperScore;
    private volatile long apertureRefreshTime;
    private final BiFunction<List<LoadBalancedAddress<C>>,
            Predicate<LoadBalancedAddress<C>>, LoadBalancedAddress<C>> selector;
    private final CowList<LoadBalancedAddress<C>> activeAddresses =
            new CowList<>();
    private final CowList<LoadBalancedAddress<C>> availableAddresses =
            new CowList<>();
    private final ListenableAsyncCloseable closeable;

    /**
     * @param bottomScore target scoreband, bottom of the band, trigger point to increase aperture
     * @param topScore target scoreband, top of the band, trigger point to reduce aperture
     * @param apertureRefreshTime time betweem refreshes of the aperture
     * @param selector strategy for selecting adresses for new connections or when expanding the aperture
     */
    DynamicApertureListBasedAddressSelector(
            final float bottomScore,
            // TODO(jayv) not sure about this, as you'd likely want to always optimize for the best scores, in other
            // implementations of similar algorithms folks depend on optimizing for 1:1 pending requests/capacity, but
            // this may be tricky when multiplexing is at play
            final float topScore,
            final Duration apertureRefreshTime,
            final BiFunction<List<LoadBalancedAddress<C>>,
                    Predicate<LoadBalancedAddress<C>>, LoadBalancedAddress<C>> selector) {
        // TODO(jayv) these will change from static scores to inferring direction of the score up/down to drive resize
        assert bottomScore < topScore;
        this.bottomScore = bottomScore;
        this.upperScore = topScore;
        this.apertureRefreshNs = apertureRefreshTime.toNanos();
        this.selector = selector;
        closeable = AsyncCloseables.toListenableAsyncCloseable(newCompositeCloseable()
                .mergeAll(availableAddresses)
                .mergeAll(activeAddresses));
    }

    @Override
    public Single<LoadBalancedAddress<C>> select() {
        return defer(() -> select0().subscribeShareContext());
    }

    private Single<LoadBalancedAddress<C>> select0() {
        optimizeAperture(false);
        try {
            List<LoadBalancedAddress<C>> entries = activeAddresses.currentEntries();
            LoadBalancedAddress<C> addr = selector.apply(entries, __ -> true);
            return addr == null ?
                    failed(activeAddresses.isClosed() ? LB_CLOSED_SELECT_CNX_EXCEPTION :
                            entries.isEmpty() ?
                                    NO_ACTIVE_HOSTS_SELECT_CNX_EXCEPTION :
                                    noAvailableAddressesSelectMatchCnxException()) :
                    succeeded(addr);
        } catch (Throwable t) {
            return failed(selectAddressFailedSelectCnxException(t));
        }
    }

    void optimizeAperture(boolean forced) {
        int newAperture = 0;
        long now = System.nanoTime();
        long last = apertureRefreshTime;
        if ((forced || last + apertureRefreshNs < now) &&
                apertureRefreshTimeUpdater.compareAndSet(this, last, now)) {
            // assumes compute time will be generally lower than apertureRefreshNs

            List<LoadBalancedAddress<C>> availables = availableAddresses.currentEntries();
            List<LoadBalancedAddress<C>> actives = activeAddresses.currentEntries();
            int activeSize = actives.size();
            if (activeSize > 0 && activeSize != availables.size()) {

                LoadBalancedAddress<C> worstAddress = actives.get(0);
                float worstScore = worstAddress.score();
                float avgScore = worstScore;

                for (int i = 2; i < activeSize; i++) {
                    LoadBalancedAddress<C> address = actives.get(i);
                    float score = address.score();
                    avgScore += score;
                    if (score < worstScore) {
                        worstAddress = address;
                        worstScore = score;
                    }
                }

                if (avgScore < bottomScore) {
                    newAperture = 1;
                } else if (avgScore > upperScore) {
                    newAperture = -1;
                }

                switch (newAperture) {
                    case -1:
                        activeAddresses.remove(worstAddress);
                        // TODO(jayv) timeout?
                        worstAddress.closeAsyncGracefully().subscribe();
                        break;
                    case 0:
                        break;
                    case 1:
                        // grab the first random one that isn't already in the set
                        LoadBalancedAddress<C> toAdd = selector.apply(availables,
                                // TODO(jayv) CowList.contains() could leverage BinarySearch
                                candidate -> candidate.score() > bottomScore && !actives.contains(candidate));
                        if (toAdd != null && !activeAddresses.add(toAdd)) {
                            toAdd.closeAsync().subscribe();
                        }
                        break;
                    default:
                        throw new IllegalArgumentException();
                }
            } else {
                // grab the first random one
                LoadBalancedAddress<C> toAdd = selector.apply(availables,
                        candidate -> candidate.score() > bottomScore);
                if (toAdd != null && !activeAddresses.add(toAdd)) {
                    toAdd.closeAsync().subscribe();
                }
            }
        }
    }

    @Override
    public void add(final LoadBalancedAddress<C> address) {
        if (!availableAddresses.addIfAbsent(address)) {
            address.closeAsync().subscribe();
        }
    }

    @Override
    public void remove(final LoadBalancedAddress<C> address) {
        availableAddresses.remove(address);
        activeAddresses.remove(address);
    }

    // For use by the LB SD mutator thread only
    boolean isReady() {
        return !activeAddresses.currentEntries().isEmpty();
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
}
