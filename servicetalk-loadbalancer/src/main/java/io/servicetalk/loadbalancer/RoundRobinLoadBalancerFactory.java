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
import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.loadbalancer.RoundRobinLoadBalancer.HealthCheckConfig;
import io.servicetalk.transport.api.ExecutionStrategy;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * {@link LoadBalancerFactory} that creates {@link LoadBalancer} instances which use a round robin strategy
 * for selecting connections from a pool of addresses. The addresses are provided via the {@link Publisher published}
 * {@link ServiceDiscovererEvent events} that signal the host's {@link ServiceDiscovererEvent.Status status}.
 * Instances returned handle {@link ServiceDiscovererEvent.Status#AVAILABLE},
 * {@link ServiceDiscovererEvent.Status#EXPIRED}, and {@link ServiceDiscovererEvent.Status#UNAVAILABLE} event statuses.
 * <p>The created instances have the following behaviour:
 * <ul>
 * <li>Round robining is done at address level.</li>
 * <li>Connections are created lazily, without any concurrency control on their creation.
 * This can lead to over-provisioning connections when dealing with a requests surge.</li>
 * <li>Existing connections are reused unless a selector passed to {@link LoadBalancer#selectConnection(Predicate)}
 * suggests otherwise. This can lead to situations where connections will be used to their maximum capacity
 * (for example in the context of pipelining) before new connections are created.</li>
 * <li>Closed connections are automatically pruned.</li>
 * <li>When {@link Publisher}&lt;{@link ServiceDiscovererEvent}&gt; delivers events with
 * {@link ServiceDiscovererEvent#status()} of value {@link ServiceDiscovererEvent.Status#UNAVAILABLE}, connections
 * are immediately closed for the associated {@link ServiceDiscovererEvent#address()}. In case of
 * {@link ServiceDiscovererEvent.Status#EXPIRED}, already established connections to
 * {@link ServiceDiscovererEvent#address()} are used for requests, but no new connections are created.
 * In case the address' connections are busy, another host is tried. If all hosts are busy, selection fails with a
 * {@link io.servicetalk.client.api.ConnectionRejectedException}.</li>
 * <li>For hosts to which consecutive connection attempts fail, a background health checking task is created and
 * the host is not considered for opening new connections until the background check succeeds to create a connection.
 * Upon such event, the connection can immediately be reused and future attempts will again consider this host.
 * This behaviour can be disabled using a negative argument for
 * {@link Builder#healthCheckFailedConnectionsThreshold(int)} and the failing host will take part in the regular
 * round robin cycle for trying to establish a connection on the request path.</li>
 * </ul>
 *
 * @param <ResolvedAddress> The resolved address type.
 * @param <C> The type of connection.
 */
public final class RoundRobinLoadBalancerFactory<ResolvedAddress, C extends LoadBalancedConnection>
        implements LoadBalancerFactory<ResolvedAddress, C> {

    static final AtomicInteger FACTORY_COUNT = new AtomicInteger();
    static final Duration DEFAULT_HEALTH_CHECK_INTERVAL = Duration.ofSeconds(1);
    static final int DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD = 5; // higher than default for AutoRetryStrategy

    @Nullable
    private final HealthCheckConfig healthCheckConfig;

    private RoundRobinLoadBalancerFactory(@Nullable HealthCheckConfig healthCheckConfig) {
        this.healthCheckConfig = healthCheckConfig;
    }

    @Override
    public <T extends C> LoadBalancer<T> newLoadBalancer(
            final String targetResource,
            final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
            final ConnectionFactory<ResolvedAddress, T> connectionFactory) {
        return new RoundRobinLoadBalancer<>(requireNonNull(targetResource) + '#' + FACTORY_COUNT.incrementAndGet(),
                eventPublisher, connectionFactory, healthCheckConfig);
    }

    @Override
    public ExecutionStrategy requiredOffloads() {
        // We do not block
        return ExecutionStrategy.offloadNone();
    }

    /**
     * Builder for {@link RoundRobinLoadBalancerFactory}.
     *
     * @param <ResolvedAddress> The resolved address type.
     * @param <C> The type of connection.
     */
    public static final class Builder<ResolvedAddress, C extends LoadBalancedConnection> {
        @Nullable
        private Executor backgroundExecutor;
        private Duration healthCheckInterval = DEFAULT_HEALTH_CHECK_INTERVAL;
        private int healthCheckFailedConnectionsThreshold = DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD;

        /**
         * Creates a new instance with default settings.
         */
        public Builder() {
        }

        /**
         * This {@link LoadBalancer} may monitor hosts to which connection establishment has failed
         * using health checks that run in the background. The health check tries to establish a new connection
         * and if it succeeds, the host is returned to the load balancing pool. As long as the connection
         * establishment fails, the host is not considered for opening new connections for processed requests.
         * If an {@link Executor} is not provided using this method, a default shared instance is used
         * for all {@link LoadBalancer LoadBalancers} created by this factory.
         * <p>
         * {@link #healthCheckFailedConnectionsThreshold(int)} can be used to disable this mechanism and always
         * consider all hosts for establishing new connections.
         *
         * @param backgroundExecutor {@link Executor} on which to schedule health checking.
         * @return {@code this}.
         * @see #healthCheckFailedConnectionsThreshold(int)
         */
        public RoundRobinLoadBalancerFactory.Builder<ResolvedAddress, C> backgroundExecutor(
                Executor backgroundExecutor) {
            this.backgroundExecutor = requireNonNull(backgroundExecutor);
            return this;
        }

        /**
         * Configure an interval for health checking a host that failed to open connections. If no interval is provided
         * using this method, a default value will be used.
         * <p>
         * {@link #healthCheckFailedConnectionsThreshold(int)} can be used to disable the health checking mechanism
         * and always consider all hosts for establishing new connections.
         * @param interval interval at which a background health check will be scheduled.
         * @return {@code this}.
         * @see #healthCheckFailedConnectionsThreshold(int)
         */
        public RoundRobinLoadBalancerFactory.Builder<ResolvedAddress, C> healthCheckInterval(Duration interval) {
            if (interval.isNegative() || interval.isZero()) {
                throw new IllegalArgumentException("Health check interval should be greater than 0");
            }
            this.healthCheckInterval = interval;
            return this;
        }

        /**
         * Configure a threshold for consecutive connection failures to a host. When the {@link LoadBalancer}
         * consecutively fails to open connections in the amount greater or equal to the specified value,
         * the host will be marked as unhealthy and connection establishment will take place in the background
         * repeatedly until a connection is established. During that time, the host will not take part in
         * load balancing selection.
         * <p>
         * Use a negative value of the argument to disable health checking.
         * @param threshold number of consecutive connection failures to consider a host unhealthy and eligible for
         * background health checking. Use negative value to disable the health checking mechanism.
         * @return {@code this}.
         * @see #backgroundExecutor(Executor)
         * @see #healthCheckInterval(Duration)
         */
        public RoundRobinLoadBalancerFactory.Builder<ResolvedAddress, C> healthCheckFailedConnectionsThreshold(
                int threshold) {
            if (threshold == 0) {
                throw new IllegalArgumentException("Health check failed connections threshold should not be 0");
            }
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
                return new RoundRobinLoadBalancerFactory<>(null);
            }

            HealthCheckConfig healthCheckConfig = new HealthCheckConfig(
                            this.backgroundExecutor == null ? SharedExecutor.getInstance() : this.backgroundExecutor,
                    healthCheckInterval, healthCheckFailedConnectionsThreshold);

            return new RoundRobinLoadBalancerFactory<>(healthCheckConfig);
        }
    }

    static final class SharedExecutor {
        private static final Executor INSTANCE = Executors.from(
                new ThreadPoolExecutor(1, 1, 60, SECONDS,
                        new LinkedBlockingQueue<>(),
                        new DefaultThreadFactory("round-robin-load-balancer-executor")));

        private SharedExecutor() {
        }

        static Executor getInstance() {
            return INSTANCE;
        }
    }
}
