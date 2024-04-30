/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;

import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.utils.internal.NumberUtils.ensureNonNegative;
import static java.util.Objects.requireNonNull;

// Really just decorates a Host with additional data. So far it's just priority but in the future it could also
// be weight information.
final class EndpointHost<ResolvedAddress, C extends LoadBalancedConnection> implements Host<ResolvedAddress, C>,
        PrioritizedHost {
    private final Host<ResolvedAddress, C> delegate;
    private int priority;
    private double intrinsicWeight;
    private double loadBalancedWeight;

    EndpointHost(final Host<ResolvedAddress, C> delegate, final double intrinsicWeight, final int priority) {
        this.delegate = requireNonNull(delegate, "delegate");
        this.priority = ensureNonNegative(priority, "priority");
        this.intrinsicWeight = intrinsicWeight;
    }

    Host<ResolvedAddress, C> delegate() {
        return delegate;
    }

    @Override
    public int priority() {
        return priority;
    }

    void priority(final int priority) {
        this.priority = priority;
    }

    // Set the intrinsic weight of the endpoint. This is the information from service discovery.
    void intrinsicWeight(final double weight) {
        this.intrinsicWeight = weight;
    }

    // Set the weight to use in load balancing. This includes derived weight information such as prioritization
    // and is what the host selectors will use when picking endpoints.
    @Override
    public void loadBalancedWeight(final double weight) {
        this.loadBalancedWeight = weight;
    }

    @Override
    public double weight() {
        return loadBalancedWeight;
    }

    @Override
    public double intrinsicWeight() {
        return intrinsicWeight;
    }

    @Override
    public int score() {
        return delegate.score();
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return delegate.closeAsyncGracefully();
    }

    @Override
    public Completable onClose() {
        return delegate.onClose();
    }

    @Override
    public Completable onClosing() {
        return delegate.onClosing();
    }

    @Nullable
    @Override
    public C pickConnection(Predicate<C> selector, @Nullable ContextMap context) {
        return delegate.pickConnection(selector, context);
    }

    @Override
    public Single<C> newConnection(Predicate<C> selector, boolean forceNewConnectionAndReserve,
                                   @Nullable ContextMap context) {
        return delegate.newConnection(selector, forceNewConnectionAndReserve, context);
    }

    @Override
    public ResolvedAddress address() {
        return delegate.address();
    }

    @Override
    public boolean isHealthy() {
        return delegate.isHealthy();
    }

    @Override
    public boolean canMakeNewConnections() {
        return delegate.canMakeNewConnections();
    }

    @Override
    public boolean markActiveIfNotClosed() {
        return delegate.markActiveIfNotClosed();
    }

    @Override
    public boolean markExpired() {
        return delegate.markExpired();
    }
}
