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
import io.servicetalk.client.api.LoadBalancedAddress;
import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.loadbalancer.StatUtils.UnevenExpWeightedMovingAvg;

import java.time.Duration;

class ConnectionAwareLoadBalancedAddress<R, C extends LoadBalancedConnection, SDE extends ServiceDiscovererEvent<R>>
        implements LoadBalancedAddress<C>, ServiceDiscoveryAwareLoadBalancedAddress<C, R, SDE> {

    // Probably want this to be a low value, to notice spikes in load quickly
    public static final long SCORE_CACHE_TTL = Duration.ofSeconds(1).toNanos();
    private final CowList<C> connections = new CowList<>();
    private final UnevenExpWeightedMovingAvg ewma =
            new UnevenExpWeightedMovingAvg(Duration.ofSeconds(15));
    private final R address;
    private final ConnectionFactory<R, C> cf;
    private final float cancelValue;
    private volatile long lastComputed;
    private final ListenableAsyncCloseable closeable;

    ConnectionAwareLoadBalancedAddress(final R address, final ConnectionFactory<R, C> cf) {
        this(address, cf, 0.5f);
    }

    ConnectionAwareLoadBalancedAddress(final R address, final ConnectionFactory<R, C> cf, final float cancelValue) {
        this.address = address;
        this.cf = cf;
        this.cancelValue = cancelValue;
        closeable = LoadBalancerUtils.newCloseable(connections::close);
    }

    @Override
    public float score() {
        long now = System.nanoTime();
        if ((lastComputed - now) > SCORE_CACHE_TTL) {
            for (C entry : connections.currentEntries()) {
                ewma.observe(entry.score());
            }
            lastComputed = now;
            return ewma.value();
        }
        return ewma.valueDecayed();
    }

    @Override
    public Single<C> newConnection() {
        return cf.newConnection(address)
                .beforeOnSuccess(lbc -> {
                    if (!connections.add(lbc)) {
                        // Failed to add connection, already closed
                        lbc.closeAsync().subscribe();
                        return;
                    }
                    lbc.onClose().beforeFinally(() -> connections.remove(lbc)).subscribe();
                    ewma.observe(1);
                })
                .beforeOnError(__ -> ewma.observe(0))
                .beforeCancel(() -> ewma.observe(cancelValue));
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

    @Override
    public void onEvent(final SDE event) {
        ewma.set(event.isAvailable() ? 1 : 0);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final ConnectionAwareLoadBalancedAddress<?, ?, ?> that = (ConnectionAwareLoadBalancedAddress<?, ?, ?>) o;

        return address.equals(that.address);
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }
}
