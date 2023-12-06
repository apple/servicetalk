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

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;

import java.time.Duration;
import java.util.Collection;
import javax.annotation.Nullable;

import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_INTERVAL;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_JITTER;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL;
import static io.servicetalk.loadbalancer.HealthCheckConfig.validateHealthCheckIntervals;
import static java.util.Objects.requireNonNull;

final class DefaultLoadBalancerBuilder<ResolvedAddress, C extends LoadBalancedConnection>
        implements LoadBalancerBuilder<ResolvedAddress, C> {

    private static final int DEFAULT_LINEAR_SEARCH_SPACE = Integer.MAX_VALUE;
    private static final LoadBalancingPolicy DEFAULT_LOAD_BALANCING_POLICY =
            RoundRobinLoadBalancingPolicy.DEFAULT_POLICY;

    private final String id;
    private LoadBalancingPolicy loadBalancingPolicy = DEFAULT_LOAD_BALANCING_POLICY;
    private int linearSearchSpace = DEFAULT_LINEAR_SEARCH_SPACE;

    @Nullable
    private Executor backgroundExecutor;
    private Duration healthCheckInterval = DEFAULT_HEALTH_CHECK_INTERVAL;
    private Duration healthCheckJitter = DEFAULT_HEALTH_CHECK_JITTER;
    private int healthCheckFailedConnectionsThreshold = DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD;
    private Duration healthCheckResubscribeInterval = DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL;
    private Duration healthCheckResubscribeJitter = DEFAULT_HEALTH_CHECK_JITTER;

    // package private constructor so users must funnel through providers in `LoadBalancers`
    DefaultLoadBalancerBuilder(final String id) {
        this.id = requireNonNull(id, "id");
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> linearSearchSpace(int linearSearchSpace) {
        if (linearSearchSpace <= 0) {
            throw new IllegalArgumentException("Invalid linear search space: "
                    + linearSearchSpace + " (expected > 0)");
        }
        this.linearSearchSpace = linearSearchSpace;
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> loadBalancingPolicy(LoadBalancingPolicy loadBalancingPolicy) {
        this.loadBalancingPolicy = requireNonNull(loadBalancingPolicy, "loadBalancingPolicy");
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> backgroundExecutor(Executor backgroundExecutor) {
        this.backgroundExecutor = new NormalizedTimeSourceExecutor(backgroundExecutor);
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> healthCheckInterval(Duration interval, Duration jitter) {
        validateHealthCheckIntervals(interval, jitter);
        this.healthCheckInterval = interval;
        this.healthCheckJitter = jitter;
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> healthCheckResubscribeInterval(
            Duration interval, Duration jitter) {
        validateHealthCheckIntervals(interval, jitter);
        this.healthCheckResubscribeInterval = interval;
        this.healthCheckResubscribeJitter = jitter;
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> healthCheckFailedConnectionsThreshold(
            int threshold) {
        if (threshold == 0) {
            throw new IllegalArgumentException("Invalid health-check failed connections (expected != 0)");
        }
        this.healthCheckFailedConnectionsThreshold = threshold;
        return this;
    }

    @Override
    public LoadBalancerFactory<ResolvedAddress, C> build() {
        final HealthCheckConfig healthCheckConfig;
        if (this.healthCheckFailedConnectionsThreshold < 0) {
            healthCheckConfig = null;
        } else {
            healthCheckConfig = new HealthCheckConfig(this.backgroundExecutor == null ?
                    RoundRobinLoadBalancerFactory.SharedExecutor.getInstance() : this.backgroundExecutor,
                    healthCheckInterval, healthCheckJitter, healthCheckFailedConnectionsThreshold,
                    healthCheckResubscribeInterval, healthCheckResubscribeJitter);
        }
        return new DefaultLoadBalancerFactory<>(id, loadBalancingPolicy, linearSearchSpace, healthCheckConfig);
    }

    private static final class DefaultLoadBalancerFactory<ResolvedAddress, C extends LoadBalancedConnection>
            implements LoadBalancerFactory<ResolvedAddress, C> {

        private final String id;
        private final LoadBalancingPolicy loadBalancingPolicy;
        private final int linearSearchSpace;
        @Nullable
        private final HealthCheckConfig healthCheckConfig;

        DefaultLoadBalancerFactory(final String id, final LoadBalancingPolicy loadBalancingPolicy,
        final int linearSearchSpace, final HealthCheckConfig healthCheckConfig) {
            this.id = requireNonNull(id, "id");
            this.loadBalancingPolicy = requireNonNull(loadBalancingPolicy, "loadBalancingPolicy");
            this.linearSearchSpace = linearSearchSpace;
            this.healthCheckConfig = healthCheckConfig;
        }

        @Override
        public <T extends C> LoadBalancer<T> newLoadBalancer(String targetResource,
             Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
             ConnectionFactory<ResolvedAddress, T> connectionFactory) {
            return new DefaultLoadBalancer<>(id, targetResource, eventPublisher,
                    loadBalancingPolicy.buildSelector(targetResource), connectionFactory, linearSearchSpace,
                    healthCheckConfig);
        }
    }
}
