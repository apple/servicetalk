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
import io.servicetalk.client.api.NoAvailableHostException;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.BiFunction;
import java.util.function.Predicate;

class DynamicApertureListBasedAddressSelector<C extends LoadBalancedConnection> implements AddressSelector<C> {

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
        this.bottomScore = bottomScore;
        this.upperScore = topScore;
        assert bottomScore < topScore;
        this.apertureRefreshNs = apertureRefreshTime.toNanos();
        this.selector = selector;
        closeable = LoadBalancerUtils.newCloseable(() -> {
            HashSet<AsyncCloseable> addresses = new HashSet<>();
            addresses.addAll(availableAddresses.close());
            addresses.addAll(activeAddresses.close());
            return addresses;
        });
    }

    @Override
    public Single<LoadBalancedAddress<C>> select() {
        List<LoadBalancedAddress<C>> actives = activeAddresses.currentEntries();
        if (actives.size() == 0) {
            if (activeAddresses.isClosed()) {
                return Single.failed(LoadBalancerUtils.LB_CLOSED_SELECT_CNX_EXCEPTION);
            }
            // TODO(jayv): this could delay completion until active addresses have been observed
            return Single.failed(LoadBalancerUtils.NO_ACTIVE_HOSTS_SELECT_CNX_EXCEPTION);
        }
        optimizeAperture(false);
        LoadBalancedAddress<C> addr = selector.apply(actives, __ -> true);
        if (addr == null) {
            return Single.failed(new NoAvailableHostException("No Available connection matching predicate"));
        }
        return Single.succeeded(addr);
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
        if (!availableAddresses.add(address)) {
            address.closeAsync().subscribe();
        }
    }

    @Override
    public void remove(final LoadBalancedAddress<C> address) {
        availableAddresses.remove(address);
        activeAddresses.remove(address);
    }

    @Override
    public boolean isReady() {
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
