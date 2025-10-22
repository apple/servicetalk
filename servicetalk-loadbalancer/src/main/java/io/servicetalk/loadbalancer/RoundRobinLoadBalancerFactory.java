/*
 * Copyright © 2021-2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.ExecutionStrategy;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import javax.annotation.Nullable;

import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_INTERVAL;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_JITTER;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL;
import static io.servicetalk.loadbalancer.HealthCheckConfig.validateHealthCheckIntervals;
import static io.servicetalk.utils.internal.NumberUtils.ensureNonNegative;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * {@link LoadBalancerFactory} that creates {@link LoadBalancer} instances which use a round-robin strategy
 * for selecting connections from a pool of addresses.
 * <p>
 * For more information, see javadoc for {@link RoundRobinLoadBalancerBuilder}.
 *
 * @param <ResolvedAddress> The resolved address type.
 * @param <C> The type of connection.
 * @deprecated use {@link LoadBalancers} to create {@link LoadBalancerFactory} instances.
 * {@link RoundRobinLoadBalancerBuilder}.
 */
@Deprecated // FIXME: 0.43 - remove in favor of DefaultLoadBalancer types.
public final class RoundRobinLoadBalancerFactory<ResolvedAddress, C extends LoadBalancedConnection>
        implements LoadBalancerFactory<ResolvedAddress, C> {

    static final String ROUND_ROBIN_USER_DEFAULT_LOAD_BALANCER =
            "io.servicetalk.loadbalancer.roundRobinUsesDefaultLoadBalancer";

    private final String id;
    private final int linearSearchSpace;
    @Nullable
    private final HealthCheckConfig healthCheckConfig;

    private RoundRobinLoadBalancerFactory(final String id,
                                          final int linearSearchSpace,
                                          @Nullable final HealthCheckConfig healthCheckConfig) {
        this.id = id;
        this.linearSearchSpace = linearSearchSpace;
        this.healthCheckConfig = healthCheckConfig;
    }

    @Deprecated
    @Override
    public <T extends C> LoadBalancer<T> newLoadBalancer(
            final String targetResource,
            final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
            final ConnectionFactory<ResolvedAddress, T> connectionFactory) {
        requireNonNull(targetResource, "targetResource");
        requireNonNull(eventPublisher, "eventPublisher");
        requireNonNull(connectionFactory, "connectionFactory");
        // We have to indirect here instead of at the `Builder.build()` call because as it turns out
        // `Builder.build()` has a return type of RoundRobinLoadBalancerFactory and is public API.
        return useDefaultLoadBalancer() ?
                buildDefaultLoadBalancerFactory(targetResource, eventPublisher, connectionFactory) :
                new RoundRobinLoadBalancer<>(
                        id, targetResource, eventPublisher, connectionFactory, linearSearchSpace, healthCheckConfig);
    }

    @Override
    public LoadBalancer<C> newLoadBalancer(
            final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
            final ConnectionFactory<ResolvedAddress, C> connectionFactory,
            final String targetResource) {
        // For now, we forward to the deprecated method since it is more generic.
        return newLoadBalancer(targetResource, eventPublisher, connectionFactory);
    }

    @Override
    public ExecutionStrategy requiredOffloads() {
        // We do not block
        return ExecutionStrategy.offloadNone();
    }

    @Override
    public String toString() {
        return "RoundRobinLoadBalancerFactory{" +
                "id='" + id + '\'' +
                ", linearSearchSpace=" + linearSearchSpace +
                ", healthCheckConfig=" + healthCheckConfig +
                '}';
    }

    private <T extends C> LoadBalancer<T> buildDefaultLoadBalancerFactory(
            final String targetResource,
            final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
            final ConnectionFactory<ResolvedAddress, T> connectionFactory) {
        final int healthCheckFailedConnectionsThreshold;
        final Duration healthCheckInterval;
        final Duration healthCheckJitter;
        final Duration healthCheckResubscribeInterval;
        final Duration healthCheckResubscribeJitter;
        final Executor backgroundExecutor;
        if (healthCheckConfig == null) {
            healthCheckFailedConnectionsThreshold = -1; // disabled, the rest are fillers.
            healthCheckInterval = DEFAULT_HEALTH_CHECK_INTERVAL;
            healthCheckJitter = DEFAULT_HEALTH_CHECK_JITTER;
            healthCheckResubscribeInterval = DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL;
            healthCheckResubscribeJitter = DEFAULT_HEALTH_CHECK_JITTER;
            backgroundExecutor = null;
        } else {
            healthCheckFailedConnectionsThreshold = healthCheckConfig.failedThreshold;
            healthCheckInterval = healthCheckConfig.healthCheckInterval;
            healthCheckJitter = healthCheckConfig.jitter;
            healthCheckResubscribeInterval = healthCheckConfig.resubscribeInterval;
            healthCheckResubscribeJitter = healthCheckConfig.healthCheckResubscribeJitter;
            backgroundExecutor = healthCheckConfig.executor;
        }

        OutlierDetectorConfig outlierDetectorConfig =
                // Start with outlier detection disabled. When we set failedConnectionsThreshold it may turn
                // L4 outlier detection back on.
                new OutlierDetectorConfig.Builder(OutlierDetectorConfigs.disabled())
                .failedConnectionsThreshold(healthCheckFailedConnectionsThreshold)
                .failureDetectorInterval(healthCheckInterval, healthCheckJitter)
                .serviceDiscoveryResubscribeInterval(healthCheckResubscribeInterval, healthCheckResubscribeJitter)
                .build();
        LoadBalancingPolicy<ResolvedAddress, T> loadBalancingPolicy =
                LoadBalancingPolicies.roundRobin()
                        .failOpen(false)
                        .ignoreWeights(true)
                        .build();
        LoadBalancerBuilder<ResolvedAddress, T> builder = LoadBalancers.builder(id);
        if (backgroundExecutor != null) {
            builder = builder.backgroundExecutor(backgroundExecutor);
        }
        return builder.outlierDetectorConfig(outlierDetectorConfig)
                .loadBalancingPolicy(loadBalancingPolicy)
                .connectionSelectorPolicy(ConnectionSelectorPolicies.linearSearch(linearSearchSpace))
                .build()
                .newLoadBalancer(eventPublisher, connectionFactory, targetResource);
    }

    /**
     * Builder for {@link RoundRobinLoadBalancerFactory}.
     *
     * @param <ResolvedAddress> The resolved address type.
     * @param <C> The type of connection.
     * @deprecated rely on the {@link LoadBalancers#builder(String)} instead.
     */
    @Deprecated // FIXME: 0.43 - Remove in favor of the DefaultLoadBalancer types
    public static final class Builder<ResolvedAddress, C extends LoadBalancedConnection>
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

        /**
         * Creates a new instance with default settings.
         *
         * @deprecated use {@link LoadBalancers#builder(String)} instead.
         */
        @Deprecated
        public Builder() {
            this("undefined");
        }

        Builder(final String id) {
            if (id.isEmpty()) {
                throw new IllegalArgumentException("ID can not be empty");
            }
            this.id = id;
        }

        @Override
        public RoundRobinLoadBalancerFactory.Builder<ResolvedAddress, C> linearSearchSpace(int linearSearchSpace) {
            this.linearSearchSpace = ensureNonNegative(linearSearchSpace, "linearSearchSpace");
            return this;
        }

        @Override
        public RoundRobinLoadBalancerFactory.Builder<ResolvedAddress, C> backgroundExecutor(
                Executor backgroundExecutor) {
            this.backgroundExecutor = new NormalizedTimeSourceExecutor(backgroundExecutor);
            return this;
        }

        /**
         * Configure an interval for health checking a host that failed to open connections. If no interval is provided
         * using this method, a default value will be used.
         * <p>
         * {@link #healthCheckFailedConnectionsThreshold(int)} can be used to disable the health checking mechanism
         * and always consider all hosts for establishing new connections.
         *
         * @param interval interval at which a background health check will be scheduled.
         * @return {@code this}.
         * @see #healthCheckFailedConnectionsThreshold(int)
         * @deprecated Use {@link #healthCheckInterval(Duration, Duration)}.
         */
        @Deprecated
        public RoundRobinLoadBalancerFactory.Builder<ResolvedAddress, C> healthCheckInterval(Duration interval) {
            return healthCheckInterval(interval,
                    interval.compareTo(DEFAULT_HEALTH_CHECK_INTERVAL) < 0 ? interval.dividedBy(2) :
                            DEFAULT_HEALTH_CHECK_JITTER);
        }

        @Override
        public RoundRobinLoadBalancerFactory.Builder<ResolvedAddress, C> healthCheckInterval(Duration interval,
                                                                                             Duration jitter) {
            validateHealthCheckIntervals(interval, jitter);
            this.healthCheckInterval = interval;
            this.healthCheckJitter = jitter;
            return this;
        }

        @Override
        public RoundRobinLoadBalancerFactory.Builder<ResolvedAddress, C> healthCheckResubscribeInterval(
                Duration interval, Duration jitter) {
            validateHealthCheckIntervals(interval, jitter);
            this.healthCheckResubscribeInterval = interval;
            this.healthCheckResubscribeJitter = jitter;
            return this;
        }

        @Override
        public RoundRobinLoadBalancerFactory.Builder<ResolvedAddress, C> healthCheckFailedConnectionsThreshold(
                int threshold) {
            if (threshold == 0) {
                throw new IllegalArgumentException("Health check failed connections threshold should not be 0");
            }
            this.healthCheckFailedConnectionsThreshold = threshold;
            return this;
        }

        @Override
        public RoundRobinLoadBalancerFactory<ResolvedAddress, C> build() {
            if (this.healthCheckFailedConnectionsThreshold < 0) {
                return new RoundRobinLoadBalancerFactory<>(id, linearSearchSpace, null);
            }

            HealthCheckConfig healthCheckConfig = new HealthCheckConfig(
                            this.backgroundExecutor == null ? SharedExecutor.getInstance() : this.backgroundExecutor,
                    healthCheckInterval, healthCheckJitter, healthCheckFailedConnectionsThreshold,
                    healthCheckResubscribeInterval, healthCheckResubscribeJitter);
            return new RoundRobinLoadBalancerFactory<>(id, linearSearchSpace, healthCheckConfig);
        }
    }

    static final class SharedExecutor {
        private static final Executor INSTANCE = new NormalizedTimeSourceExecutor(Executors.from(
                new ThreadPoolExecutor(1, 1, 60, SECONDS,
                        new LinkedBlockingQueue<>(),
                        new DefaultThreadFactory("round-robin-load-balancer-executor"))));

        private SharedExecutor() {
        }

        static Executor getInstance() {
            return INSTANCE;
        }
    }

    private static boolean useDefaultLoadBalancer() {
        // Enabled by default.
        String propValue = System.getProperty(ROUND_ROBIN_USER_DEFAULT_LOAD_BALANCER);
        return propValue == null || Boolean.parseBoolean(propValue);
    }
}
