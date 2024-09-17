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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import javax.annotation.Nullable;

import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_INTERVAL;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_JITTER;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL;
import static io.servicetalk.loadbalancer.HealthCheckConfig.validateHealthCheckIntervals;
import static io.servicetalk.utils.internal.NumberUtils.ensureNonNegative;

public final class RoundRobinToDefaultLBMigrationProvider implements RoundRobinLoadBalancerBuilderProvider {

    static final String PROPERTY_NAME = "io.servicetalk.loadbalancer.roundRobinUsesDefaultLoadBalancer";

    private static final Logger LOGGER = LoggerFactory.getLogger(RoundRobinToDefaultLBMigrationProvider.class);

    @Override
    public <ResolvedAddress, C extends LoadBalancedConnection>
    RoundRobinLoadBalancerBuilder<ResolvedAddress, C> newBuilder(
            String id, RoundRobinLoadBalancerBuilder<ResolvedAddress, C> builder) {
        if (isEnabled()) {
            LOGGER.info("Enabling DefaultLoadBalancer in place of RoundRobinLoadBalancer for load balancer id {}", id);
            return new DefaultLoadBalancerRoundRobinBuilder<>(id);
        } else {
            LOGGER.debug(
                    "Not enabling DefaultLoadBalancer in place of RoundRobinLoadBalancer for load balancer id {}", id);
            return builder;
        }
    }

    private static boolean isEnabled() {
        return Boolean.getBoolean(PROPERTY_NAME);
    }

    private static final class DefaultLoadBalancerRoundRobinBuilder<ResolvedAddress, C extends LoadBalancedConnection>
            implements RoundRobinLoadBalancerBuilder<ResolvedAddress, C> {

        private final String id;
        private int linearSearchSpace = 16;
        @Nullable
        private Executor backgroundExecutor;
        private Duration healthCheckInterval = DEFAULT_HEALTH_CHECK_INTERVAL;
        private Duration healthCheckJitter = DEFAULT_HEALTH_CHECK_JITTER;
        private int healthCheckFailedConnectionsThreshold = DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD;
        private Duration healthCheckResubscribeInterval = DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL;
        private Duration healthCheckResubscribeJitter = DEFAULT_HEALTH_CHECK_JITTER;

        DefaultLoadBalancerRoundRobinBuilder(final String id) {
            this.id = id;
        }

        @Override
        public RoundRobinLoadBalancerBuilder<ResolvedAddress, C> linearSearchSpace(int linearSearchSpace) {
            this.linearSearchSpace = ensureNonNegative(linearSearchSpace, "linearSearchSpace");
            return this;
        }

        @Override
        public RoundRobinLoadBalancerBuilder<ResolvedAddress, C> backgroundExecutor(Executor backgroundExecutor) {
            this.backgroundExecutor = backgroundExecutor;
            return this;
        }

        @Override
        public RoundRobinLoadBalancerBuilder<ResolvedAddress, C> healthCheckInterval(
                Duration interval, Duration jitter) {
            validateHealthCheckIntervals(interval, jitter);
            this.healthCheckInterval = interval;
            this.healthCheckJitter = jitter;
            return this;
        }

        @Override
        public RoundRobinLoadBalancerBuilder<ResolvedAddress, C> healthCheckResubscribeInterval(
                Duration interval, Duration jitter) {
            validateHealthCheckIntervals(interval, jitter);
            this.healthCheckResubscribeInterval = interval;
            this.healthCheckResubscribeJitter = jitter;
            return this;
        }

        @Override
        public RoundRobinLoadBalancerBuilder<ResolvedAddress, C> healthCheckFailedConnectionsThreshold(int threshold) {
            if (threshold == 0) {
                throw new IllegalArgumentException("Health check failed connections threshold should not be 0");
            }
            this.healthCheckFailedConnectionsThreshold = threshold;
            return this;
        }

        @Override
        public LoadBalancerFactory<ResolvedAddress, C> build() {
            OutlierDetectorConfig outlierDetectorConfig = new OutlierDetectorConfig.Builder()
                    .ewmaHalfLife(Duration.ZERO)
                    .enforcingFailurePercentage(0)
                    .enforcingSuccessRate(0)
                    .enforcingConsecutive5xx(0)
                    .failedConnectionsThreshold(healthCheckFailedConnectionsThreshold)
                    .failureDetectorInterval(healthCheckInterval, healthCheckJitter)
                    .serviceDiscoveryResubscribeInterval(healthCheckResubscribeInterval, healthCheckResubscribeJitter)
                    .build();

            LoadBalancingPolicy<ResolvedAddress, C> loadBalancingPolicy =
                    LoadBalancingPolicies.roundRobin()
                        .failOpen(false)
                        .ignoreWeights(true)
                        .build();

            LoadBalancerBuilder<ResolvedAddress, C> builder = LoadBalancers.builder(id);
            if (backgroundExecutor != null) {
                builder = builder.backgroundExecutor(backgroundExecutor);
            }
            return builder.outlierDetectorConfig(outlierDetectorConfig)
                    .loadBalancingPolicy(loadBalancingPolicy)
                    .connectionPoolConfig(ConnectionPoolConfig.linearSearch(linearSearchSpace))
                    .build();
        }
    }
}
