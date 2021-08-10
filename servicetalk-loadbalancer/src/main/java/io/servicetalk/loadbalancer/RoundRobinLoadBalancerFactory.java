/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.Executor;
import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Publisher;

import java.time.Duration;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * {@link LoadBalancerFactory} that creates {@link LoadBalancer} instances which use a round robin strategy
 * for selecting addresses. The created instances have the following behaviour:
 * <ul>
 * <li>Round robining is done at address level.</li>
 * <li>Connections are created lazily, without any concurrency control on their creation.
 * This can lead to over-provisioning connections when dealing with a requests surge.</li>
 * <li>Existing connections are reused unless a selector passed to {@link LoadBalancer#selectConnection(Predicate)}
 * suggests otherwise. This can lead to situations where connections will be used to their maximum capacity
 * (for example in the context of pipelining) before new connections are created.</li>
 * <li>Closed connections are automatically pruned.</li>
 * <li>If {@link #eagerConnectionShutdown} is set to {@code true}, connections are immediately closed
 * for an {@link ServiceDiscovererEvent#isAvailable() unavailable} address. When {@code false} is used, connections
 * to addresses marked as {@link ServiceDiscovererEvent#isAvailable() unavailable} are used for requests,
 * but no new connections are created for them. In case the address' connections are busy, another host is tried.
 * If all hosts are busy, selection fails with a {@link io.servicetalk.client.api.ConnectionRejectedException}.</li>
 * <li>For hosts to which consecutive connection attempts fail, a background health checking task is created and
 * the host is not considered for opening new connections until the background check succeeds to create a connection.
 * Upon such event, the connection can immediately be reused and future attempts will again consider this host.
 * This behaviour can be disabled using a negative argument for
 * {@link Builder#healthCheckFailedConnectionsThreshold(int)}</li>
 * </ul>
 *
 * @param <ResolvedAddress> The resolved address type.
 * @param <C> The type of connection.
 */
public final class RoundRobinLoadBalancerFactory<ResolvedAddress, C extends LoadBalancedConnection>
        implements LoadBalancerFactory<ResolvedAddress, C> {

    static final boolean EAGER_CONNECTION_SHUTDOWN_ENABLED = true;
    static final int DEFAULT_HEALTH_CHECK_INTERVAL_MILLIS = 1000; // 1 second
    static final int DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD = 5; // higher than default for AutoRetryStrategy

    private final boolean eagerConnectionShutdown;

    @Nullable
    private final RoundRobinLoadBalancer.HealthCheckConfig healthCheckConfig;

    private RoundRobinLoadBalancerFactory(boolean eagerConnectionShutdown,
                                          @Nullable RoundRobinLoadBalancer.HealthCheckConfig healthCheckConfig) {
        this.eagerConnectionShutdown = eagerConnectionShutdown;
        this.healthCheckConfig = healthCheckConfig;
    }

    @Override
    public <T extends C> LoadBalancer<T> newLoadBalancer(
            final Publisher<? extends ServiceDiscovererEvent<ResolvedAddress>> eventPublisher,
            final ConnectionFactory<ResolvedAddress, T> connectionFactory) {
        return new RoundRobinLoadBalancer<>(
                eventPublisher, connectionFactory, eagerConnectionShutdown, healthCheckConfig);
    }

    /**
     * Builder for {@link RoundRobinLoadBalancerFactory}.
     *
     * @param <ResolvedAddress> The resolved address type.
     * @param <C> The type of connection.
     */
    public static final class Builder<ResolvedAddress, C extends LoadBalancedConnection> {
        private boolean eagerConnectionShutdown = EAGER_CONNECTION_SHUTDOWN_ENABLED;
        @Nullable
        private Executor backgroundExecutor;
        private Duration healthCheckInterval = Duration.ZERO;
        private int healthCheckFailedConnectionsThreshold;

        /**
         * Creates a new instance with default settings.
         */
        public Builder() {
        }

        /**
         * Configures the {@link RoundRobinLoadBalancerFactory} to produce a {@link LoadBalancer} with
         * a setting driving eagerness of connection shutdown. When configured with {@code false} as the argument,
         * the created {@link LoadBalancer} does not close connections when a host becomes
         * {@link ServiceDiscovererEvent#isAvailable() unavailable}. If the value is {@code true},
         * the connections will be closed gracefully on such event.
         *
         * @param eagerConnectionShutdown when {@code true}, connections will be shut down upon receiving
         * {@link ServiceDiscovererEvent#isAvailable() unavailable} events for a particular host. Value of {@code false}
         * preserves connections and routes requests through them but no new connections are opened for such host.
         * @return {@code this}.
         */
        public RoundRobinLoadBalancerFactory.Builder<ResolvedAddress, C> eagerConnectionShutdown(
                boolean eagerConnectionShutdown) {
            this.eagerConnectionShutdown = eagerConnectionShutdown;
            return this;
        }

        /**
         * This {@link LoadBalancer} monitors hosts to which connection establishment has failed
         * using health checks that run in the background. The health check tries to establish a new connection
         * and if it succeeds, the host is returned to the load balancing pool. As long as the connection
         * establishment fails, the host is not considered for opening new connections for processed requests.
         *
         * @param backgroundExecutor {@link Executor} on which to schedule health checking.
         * @return {@code this}.
         */
        public RoundRobinLoadBalancerFactory.Builder<ResolvedAddress, C> backgroundExecutor(
                Executor backgroundExecutor) {
            this.backgroundExecutor = requireNonNull(backgroundExecutor);
            return this;
        }

        /**
         * Configure an interval for health checking a host that failed to open connections.
         * @param interval interval at which a background health check will be scheduled.
         * @return {@code this}.
         */
        public RoundRobinLoadBalancerFactory.Builder<ResolvedAddress, C> healthCheckInterval(Duration interval) {
            this.healthCheckInterval = requireNonNull(interval);
            if (interval.isNegative()) {
                throw new IllegalArgumentException("Health check interval can't be negative");
            }
            return this;
        }

        /**
         * Configure a threshold for consecutive connection failures to a host. When the {@link LoadBalancer} fails
         * to open a connection in more consecutive attempts than the specified value, the host will be marked as
         * unhealthy and a connection establishment will take place in the background. Until finished, the host will
         * not take part in load balancing selection.
         * Use a negative value of the argument to disable health checking.
         * @param threshold number of consecutive connection failures to consider a host unhealthy and eligible for
         * background health checking. Use negative value to disable the health checking mechanism.
         * @return {@code this}.
         */
        public RoundRobinLoadBalancerFactory.Builder<ResolvedAddress, C> healthCheckFailedConnectionsThreshold(
                int threshold) {
            this.healthCheckFailedConnectionsThreshold = threshold;
            return this;
        }

        /**
         * Builds the {@link RoundRobinLoadBalancerFactory} configured by this builder.
         *
         * @return a new instance of {@link RoundRobinLoadBalancerFactory} with settings from this builder.
         */
        public RoundRobinLoadBalancerFactory<ResolvedAddress, C> build() {
            if (this.healthCheckFailedConnectionsThreshold < 0) {
                return new RoundRobinLoadBalancerFactory<>(eagerConnectionShutdown, null);
            }
            assert !healthCheckInterval.isNegative();

            if (this.backgroundExecutor == null && this.healthCheckInterval.isZero()
                    && this.healthCheckFailedConnectionsThreshold == 0) {
                return new RoundRobinLoadBalancerFactory<>(eagerConnectionShutdown,
                        SharedHealthCheckConfig.getInstance());
            }

            Executor backgroundExecutor = this.backgroundExecutor == null ?
                    SharedExecutor.getInstance() : this.backgroundExecutor;
            Duration healthCheckInterval = this.healthCheckInterval.isZero() ?
                    Duration.ofMillis(DEFAULT_HEALTH_CHECK_INTERVAL_MILLIS) : this.healthCheckInterval;
            int healthCheckFailedConnectionsThreshold = this.healthCheckFailedConnectionsThreshold == 0 ?
                    DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD : this.healthCheckFailedConnectionsThreshold;

            RoundRobinLoadBalancer.HealthCheckConfig healthCheckConfig = new RoundRobinLoadBalancer.HealthCheckConfig(
                            backgroundExecutor, healthCheckInterval, healthCheckFailedConnectionsThreshold);

            return new RoundRobinLoadBalancerFactory<>(eagerConnectionShutdown, healthCheckConfig);
        }
    }

    private static final class SharedExecutor {
        private static final String BACKGROUND_PROCESSING_EXECUTOR_NAME = "round-robin-load-balancer-executor";
        private static final Executor INSTANCE = Executors.newFixedSizeExecutor(1,
                new DefaultThreadFactory(BACKGROUND_PROCESSING_EXECUTOR_NAME));

        static Executor getInstance() {
            return INSTANCE;
        }
    }

    static final class SharedHealthCheckConfig {
        private static final RoundRobinLoadBalancer.HealthCheckConfig INSTANCE =
                new RoundRobinLoadBalancer.HealthCheckConfig(SharedExecutor.getInstance(),
                        Duration.ofMillis(DEFAULT_HEALTH_CHECK_INTERVAL_MILLIS),
                        DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD);

        static RoundRobinLoadBalancer.HealthCheckConfig getInstance() {
            return INSTANCE;
        }
    }
}
