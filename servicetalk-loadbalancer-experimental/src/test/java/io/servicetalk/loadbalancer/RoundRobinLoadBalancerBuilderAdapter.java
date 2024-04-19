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

import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.concurrent.api.Executor;

import java.time.Duration;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

final class RoundRobinLoadBalancerBuilderAdapter implements LoadBalancerBuilder<String, TestLoadBalancedConnection> {

    private RoundRobinLoadBalancerBuilder<String, TestLoadBalancedConnection> underlying;

    RoundRobinLoadBalancerBuilderAdapter(Class<?> clazz) {
        underlying = RoundRobinLoadBalancers.builder(requireNonNull(clazz, "clazz").getSimpleName());
    }

    @Override
    public LoadBalancerBuilder<String, TestLoadBalancedConnection> loadBalancingPolicy(
            LoadBalancingPolicy<String, TestLoadBalancedConnection> loadBalancingPolicy) {
        throw new IllegalStateException("Cannot set new policy for old round robin");
    }

    @Override
    public LoadBalancerBuilder<String, TestLoadBalancedConnection> loadBalancerObserver(
            @Nullable LoadBalancerObserver loadBalancerObserver) {
        throw new IllegalStateException("Cannot set a load balancer observer for old round robin");
    }

    @Override
    public LoadBalancerBuilder<String, TestLoadBalancedConnection> connectionPoolConfig(
            ConnectionPoolConfig connectionPoolConfig) {
        throw new IllegalStateException("Cannot set a connection pool strategy for old round robin");
    }

    @Override
    public LoadBalancerBuilder<String, TestLoadBalancedConnection> healthCheckerFactory(
            HealthCheckerFactory healthCheckerFactory) {
        throw new IllegalStateException("Method is deprecated and shouldn't be used.");
    }

    @Override
    public LoadBalancerBuilder<String, TestLoadBalancedConnection> healthCheckFailedConnectionsThreshold(
            int threshold) {
        throw new IllegalStateException("Method is deprecated and shouldn't be used.");
    }

    @Override
    public LoadBalancerBuilder<String, TestLoadBalancedConnection> healthCheckInterval(
            Duration interval, Duration jitter) {
        throw new IllegalStateException("Method is deprecated and shouldn't be used.");
    }

    @Override
    public LoadBalancerBuilder<String, TestLoadBalancedConnection> healthCheckResubscribeInterval(
            Duration interval, Duration jitter) {
        throw new IllegalStateException("Method is deprecated and shouldn't be used.");
    }

    @Override
    public LoadBalancerBuilder<String, TestLoadBalancedConnection> linearSearchSpace(int searchSpace) {
        throw new IllegalStateException("Method is deprecated and shouldn't be used.");
    }

    @Override
    public LoadBalancerBuilder<String, TestLoadBalancedConnection> backgroundExecutor(Executor backgroundExecutor) {
        underlying = underlying.backgroundExecutor(backgroundExecutor);
        return this;
    }

    @Override
    public LoadBalancerBuilder<String, TestLoadBalancedConnection> outlierDetectorConfig(
            OutlierDetectorConfig config) {
        underlying = underlying
                .healthCheckInterval(config.failureDetectorInterval(), config.failureDetectorIntervalJitter())
                .healthCheckFailedConnectionsThreshold(config.failedConnectionsThreshold())
                .healthCheckResubscribeInterval(
                        config.serviceDiscoveryResubscribeInterval(), config.serviceDiscoveryResubscribeJitter());
        return this;
    }

    @Override
    public LoadBalancerFactory<String, TestLoadBalancedConnection> build() {
        return underlying.build();
    }
}
