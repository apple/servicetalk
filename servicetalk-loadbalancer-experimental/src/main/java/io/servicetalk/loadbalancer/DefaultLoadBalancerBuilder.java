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
import io.servicetalk.transport.api.ExecutionStrategy;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_INTERVAL;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_JITTER;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL;
import static io.servicetalk.loadbalancer.HealthCheckConfig.validateHealthCheckIntervals;
import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;
import static java.util.Objects.requireNonNull;

final class DefaultLoadBalancerBuilder<ResolvedAddress, C extends LoadBalancedConnection>
        implements LoadBalancerBuilder<ResolvedAddress, C> {

    private static final int DEFAULT_LINEAR_SEARCH_SPACE = Integer.MAX_VALUE;

    private final String id;
    private LoadBalancingPolicy<ResolvedAddress, C> loadBalancingPolicy = defaultLoadBalancingPolicy();
    private int linearSearchSpace = DEFAULT_LINEAR_SEARCH_SPACE;

    @Nullable
    private Executor backgroundExecutor;
    @Nullable
    private LoadBalancerObserver loadBalancerObserver;
    @Nullable
    private OutlierDetectorFactory<ResolvedAddress, C> outlierDetectorFactory;
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
        this.linearSearchSpace = ensurePositive(linearSearchSpace, "linearSearchSpace");
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> loadBalancingPolicy(
            LoadBalancingPolicy<ResolvedAddress, C> loadBalancingPolicy) {
        this.loadBalancingPolicy = requireNonNull(loadBalancingPolicy, "loadBalancingPolicy");
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> loadBalancerObserver(
            @Nullable LoadBalancerObserver loadBalancerObserver) {
        this.loadBalancerObserver = loadBalancerObserver;
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> outlierDetectorFactory(
            OutlierDetectorFactory<ResolvedAddress, C> outlierDetectorFactory) {
        this.outlierDetectorFactory = outlierDetectorFactory;
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
            healthCheckConfig = new HealthCheckConfig(getExecutor(),
                    healthCheckInterval, healthCheckJitter, healthCheckFailedConnectionsThreshold,
                    healthCheckResubscribeInterval, healthCheckResubscribeJitter);
        }
        final LoadBalancerObserver loadBalancerObserver = this.loadBalancerObserver != null ?
                this.loadBalancerObserver : NoopLoadBalancerObserver.instance();
        Function<String, OutlierDetector<ResolvedAddress, C>> healthCheckerSupplier;
        if (outlierDetectorFactory == null) {
            healthCheckerSupplier = null;
        } else {
            final Executor executor = getExecutor();
            healthCheckerSupplier = (String lbDescrption) ->
                    outlierDetectorFactory.newHealthChecker(executor, lbDescrption);
        }

        return new DefaultLoadBalancerFactory<>(id, loadBalancingPolicy, linearSearchSpace, healthCheckConfig,
                loadBalancerObserver, healthCheckerSupplier);
    }

    private static final class DefaultLoadBalancerFactory<ResolvedAddress, C extends LoadBalancedConnection>
            implements LoadBalancerFactory<ResolvedAddress, C> {

        private final String id;
        private final LoadBalancingPolicy<ResolvedAddress, C> loadBalancingPolicy;
        private final LoadBalancerObserver loadBalancerObserver;
        private final int linearSearchSpace;
        @Nullable
        private final Function<String, OutlierDetector<ResolvedAddress, C>> outlierDetectorFactory;
        @Nullable
        private final HealthCheckConfig healthCheckConfig;

        DefaultLoadBalancerFactory(final String id, final LoadBalancingPolicy<ResolvedAddress, C> loadBalancingPolicy,
                                   final int linearSearchSpace, final HealthCheckConfig healthCheckConfig,
                                   final LoadBalancerObserver loadBalancerObserver,
                                   final Function<String, OutlierDetector<ResolvedAddress, C>> outlierDetectorFactory) {
            this.id = requireNonNull(id, "id");
            this.loadBalancingPolicy = requireNonNull(loadBalancingPolicy, "loadBalancingPolicy");
            this.loadBalancerObserver = requireNonNull(loadBalancerObserver, "loadBalancerObserver");
            this.linearSearchSpace = linearSearchSpace;
            this.healthCheckConfig = healthCheckConfig;
            this.outlierDetectorFactory = outlierDetectorFactory;
        }

        @Override
        public <T extends C> LoadBalancer<T> newLoadBalancer(String targetResource,
             Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
             ConnectionFactory<ResolvedAddress, T> connectionFactory) {
            throw new UnsupportedOperationException("Generic constructor not supported by " +
                    DefaultLoadBalancer.class.getSimpleName());
        }

        @Override
        public <T extends C> LoadBalancer<T> newLoadBalancer(
                Publisher<? extends ServiceDiscovererEvent<ResolvedAddress>> eventPublisher,
                ConnectionFactory<ResolvedAddress, T> connectionFactory) {
            throw new UnsupportedOperationException("Generic constructor not supported by " +
                    DefaultLoadBalancer.class.getSimpleName());
        }

        @Override
        public LoadBalancer<C> newLoadBalancer(
                Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
                ConnectionFactory<ResolvedAddress, C> connectionFactory, String targetResource) {
            return new DefaultLoadBalancer<>(id, targetResource, eventPublisher,
                    loadBalancingPolicy.buildSelector(Collections.emptyList(), targetResource), connectionFactory,
                    linearSearchSpace, loadBalancerObserver, healthCheckConfig, outlierDetectorFactory);
        }

        @Override
        public ExecutionStrategy requiredOffloads() {
            // We do not block
            return ExecutionStrategy.offloadNone();
        }
    }

    private Executor getExecutor() {
        return backgroundExecutor ==
                null ? RoundRobinLoadBalancerFactory.SharedExecutor.getInstance() : backgroundExecutor;
    }

    private static <ResolvedAddress, C extends LoadBalancedConnection>
    LoadBalancingPolicy<ResolvedAddress, C> defaultLoadBalancingPolicy() {
        return new RoundRobinLoadBalancingPolicy.Builder().build();
    }
}
