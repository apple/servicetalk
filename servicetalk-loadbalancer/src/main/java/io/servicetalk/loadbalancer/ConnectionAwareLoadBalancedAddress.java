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
import java.util.List;

import static io.servicetalk.concurrent.api.AsyncCloseables.toListenableAsyncCloseable;
import static java.lang.System.nanoTime;

class ConnectionAwareLoadBalancedAddress<R, C extends LoadBalancedConnection, SDE extends ServiceDiscovererEvent<R>>
        implements LoadBalancedAddress<C>, ServiceDiscoveryAwareLoadBalancedAddress<C, R, SDE> {

    private final CowList<C> connections = new CowList<>();
    // TODO(jayv) Configuration of the decay parameters should be externalized
    private final UnevenExpWeightedMovingAvg availability =
            new UnevenExpWeightedMovingAvg(Duration.ofSeconds(15));
    private final UnevenExpWeightedMovingAvg latency =
            new UnevenExpWeightedMovingAvg(Duration.ofSeconds(15));
    private final R address;
    private final ConnectionFactory<R, C> cf;
    private final float cancelValue;
    private final ListenableAsyncCloseable closeable = toListenableAsyncCloseable(connections);

    ConnectionAwareLoadBalancedAddress(final R address, final ConnectionFactory<R, C> cf) {
        this(address, cf, 0.5f);
    }

    ConnectionAwareLoadBalancedAddress(final R address, final ConnectionFactory<R, C> cf, final float cancelValue) {
        this.address = address;
        this.cf = cf;
        this.cancelValue = cancelValue;
    }

    @Override
    public float score() {
        float avgConnScore = 0;
        List<C> entries = connections.currentEntries();
        for (C entry : entries) {
            avgConnScore += entry.score();
        }
        float meanConnScore = avgConnScore / entries.size();
        // TODO(jayv) probably want to weigh both components differently
        // TODO(jayv) in addition to the mean we may be interested in sumsquares and stddev, to penalize higher variance
        // TODO(jayv) always calculate the mean over all connections? this is unbounded, having the computation be
        // encapsulated doesn't give visibility to the component requesting the calculation to put a bound on effort
        // TODO(jayv) what to do with connect() latency score
        return availability.value() * meanConnScore; // * f(latency) => latency to score
    }

    @Override
    public Single<C> newConnection() {
        final Timer connectTimer = new Timer(); // RS provides happens-before
        return cf.newConnection(address)
                .beforeOnSubscribe(__ -> connectTimer.start())
                .beforeOnSuccess(lbc -> {
                    if (!connections.add(lbc)) {
                        // Failed to add connection, already closed
                        lbc.closeAsync().subscribe();
                        return;
                    }
                    latency.observe((float) connectTimer.stop() / 1_000_000);
                    lbc.onClose().beforeFinally(() -> connections.remove(lbc)).subscribe();
                    availability.observe(1);
                })
                .beforeOnError(__ -> availability.observe(0))
                .beforeCancel(() -> availability.observe(cancelValue));
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
        availability.set(event.isAvailable() ? 1 : 0);
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

    private static final class Timer {
        private long startTime = -1;

        void start() {
            startTime = nanoTime();
        }

        long stop() {
            return nanoTime() - startTime;
        }
    }
}
