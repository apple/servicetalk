/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.concurrent.api.Executor;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * A {@link RoundRobinLoadBalancerBuilder} that delegates all methods to another {@link RoundRobinLoadBalancerBuilder}.
 *
 * @param <ResolvedAddress> The resolved address type.
 * @param <C> The type of connection.
 * @deprecated use {@link DelegatingLoadBalancerBuilder} instead.
 */
@Deprecated // FIXME: 0.43 - Remove in favor of the DefaultLoadBalancer types
public class DelegatingRoundRobinLoadBalancerBuilder<ResolvedAddress, C extends LoadBalancedConnection>
        implements RoundRobinLoadBalancerBuilder<ResolvedAddress, C> {

    private RoundRobinLoadBalancerBuilder<ResolvedAddress, C> delegate;

    /**
     * Creates a new builder which delegates to the provided {@link RoundRobinLoadBalancerBuilder}.
     *
     * @param delegate the delegate builder.
     */
    public DelegatingRoundRobinLoadBalancerBuilder(final RoundRobinLoadBalancerBuilder<ResolvedAddress, C> delegate) {
        this.delegate = requireNonNull(delegate);
    }

    /**
     * Returns the {@link DelegatingRoundRobinLoadBalancerBuilder} delegate.
     *
     * @return Delegate {@link DelegatingRoundRobinLoadBalancerBuilder}.
     */
    protected final RoundRobinLoadBalancerBuilder<ResolvedAddress, C> delegate() {
        return delegate;
    }

    @Override
    public RoundRobinLoadBalancerBuilder<ResolvedAddress, C> linearSearchSpace(final int linearSearchSpace) {
        delegate = delegate.linearSearchSpace(linearSearchSpace);
        return this;
    }

    @Override
    public RoundRobinLoadBalancerBuilder<ResolvedAddress, C> backgroundExecutor(final Executor backgroundExecutor) {
        delegate = delegate.backgroundExecutor(backgroundExecutor);
        return this;
    }

    @Override
    public RoundRobinLoadBalancerBuilder<ResolvedAddress, C> healthCheckInterval(final Duration interval,
                                                                                 final Duration jitter) {
        delegate = delegate.healthCheckInterval(interval, jitter);
        return this;
    }

    @Override
    public RoundRobinLoadBalancerBuilder<ResolvedAddress, C> healthCheckResubscribeInterval(final Duration interval,
                                                                                            final Duration jitter) {
        delegate = delegate.healthCheckResubscribeInterval(interval, jitter);
        return this;
    }

    @Override
    public RoundRobinLoadBalancerBuilder<ResolvedAddress, C> healthCheckFailedConnectionsThreshold(
            final int threshold) {
        delegate = delegate.healthCheckFailedConnectionsThreshold(threshold);
        return this;
    }

    @Override
    public LoadBalancerFactory<ResolvedAddress, C> build() {
        return delegate.build();
    }
}
