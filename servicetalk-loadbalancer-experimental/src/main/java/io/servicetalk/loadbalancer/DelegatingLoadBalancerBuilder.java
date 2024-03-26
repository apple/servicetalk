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
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.concurrent.api.Executor;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A {@link LoadBalancerBuilder} that delegates all methods to another {@link LoadBalancerBuilder}.
 *
 * @param <ResolvedAddress> The resolved address type.
 * @param <C> The type of connection.
 */
public class DelegatingLoadBalancerBuilder<ResolvedAddress, C extends LoadBalancedConnection>
        implements LoadBalancerBuilder<ResolvedAddress, C> {

    private LoadBalancerBuilder<ResolvedAddress, C> delegate;

    /**
     * Creates a new builder which delegates to the provided {@link LoadBalancerBuilder}.
     *
     * @param delegate the delegate builder.
     */
    public DelegatingLoadBalancerBuilder(final LoadBalancerBuilder<ResolvedAddress, C> delegate) {
        this.delegate = requireNonNull(delegate, "delegate");
    }

    /**
     * Returns the {@link DelegatingLoadBalancerBuilder} delegate.
     *
     * @return Delegate {@link DelegatingLoadBalancerBuilder}.
     */
    protected final LoadBalancerBuilder<ResolvedAddress, C> delegate() {
        return delegate;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> loadBalancingPolicy(LoadBalancingPolicy loadBalancingPolicy) {
        delegate = delegate.loadBalancingPolicy(loadBalancingPolicy);
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> loadBalancerObserver(
            @Nullable LoadBalancerObserver loadBalancerObserver) {
        delegate = delegate.loadBalancerObserver(loadBalancerObserver);
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> outlierDetectorConfig(OutlierDetectorConfig outlierDetectorConfig) {
        delegate = delegate.outlierDetectorConfig(outlierDetectorConfig);
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> backgroundExecutor(Executor backgroundExecutor) {
        delegate = delegate.backgroundExecutor(backgroundExecutor);
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> linearSearchSpace(int linearSearchSpace) {
        delegate = delegate.linearSearchSpace(linearSearchSpace);
        return this;
    }

    @Override
    public LoadBalancerFactory<ResolvedAddress, C> build() {
        return delegate.build();
    }
}
