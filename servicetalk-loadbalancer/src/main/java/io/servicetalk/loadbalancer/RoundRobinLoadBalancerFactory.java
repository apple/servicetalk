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
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.loadbalancer.RoundRobinLoadBalancer.HealthCheckConfig;
import io.servicetalk.transport.api.ExecutionStrategy;

import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.utils.internal.DurationUtils.ensureNonNegative;
import static io.servicetalk.utils.internal.DurationUtils.ensurePositive;
import static io.servicetalk.utils.internal.DurationUtils.isPositive;
import static java.time.Duration.ofSeconds;
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
 * <li>Existing connections are reused unless a selector passed to
 * {@link LoadBalancer#selectConnection(Predicate, ContextMap)} suggests otherwise. This can lead to situations where
 * connections will be used to their maximum capacity (for example in the context of pipelining) before new connections
 * are created.</li>
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
 * @deprecated this class will be made package-private in the future, rely on {@link RoundRobinLoadBalancers} instead.
 */
@Deprecated // FIXME: 0.43 - make package private
public final class RoundRobinLoadBalancerFactory<ResolvedAddress, C extends LoadBalancedConnection>
        implements LoadBalancerFactory<ResolvedAddress, C> {

    private static final Duration DEFAULT_HEALTH_CHECK_INTERVAL = ofSeconds(5);
    private static final Duration DEFAULT_HEALTH_CHECK_JITTER = ofSeconds(3);
    static final Duration DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL = ofSeconds(10);
    static final int DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD = 5; // higher than default for AutoRetryStrategy

    private final int linearSearchSpace;
    @Nullable
    private final HealthCheckConfig healthCheckConfig;

    private RoundRobinLoadBalancerFactory(final int linearSearchSpace,
                                          @Nullable final HealthCheckConfig healthCheckConfig) {
        this.linearSearchSpace = linearSearchSpace;
        this.healthCheckConfig = healthCheckConfig;
    }

    @Deprecated
    @Override
    public <T extends C> LoadBalancer<T> newLoadBalancer(
            final String targetResource,
            final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
            final ConnectionFactory<ResolvedAddress, T> connectionFactory) {
        return new RoundRobinLoadBalancer<>(targetResource, eventPublisher, connectionFactory,
                linearSearchSpace, healthCheckConfig);
    }

    @Override
    public LoadBalancer<C> newLoadBalancer(
            final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
            final ConnectionFactory<ResolvedAddress, C> connectionFactory,
            final String targetResource) {
        return new RoundRobinLoadBalancer<>(targetResource, eventPublisher, connectionFactory,
                linearSearchSpace, healthCheckConfig);
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
     * @deprecated this class will be made package-private in the future, rely on the
     * {@link RoundRobinLoadBalancerBuilder} instead.
     */
    @Deprecated // FIXME: 0.43 - make package private
    public static final class Builder<ResolvedAddress, C extends LoadBalancedConnection>
            implements RoundRobinLoadBalancerBuilder<ResolvedAddress, C> {
        private final String id;
        private int linearSearchSpace = 16;
        @Nullable
        private Executor backgroundExecutor;
        private Duration healthCheckInterval = DEFAULT_HEALTH_CHECK_INTERVAL;
        private Duration healthCheckJitter = DEFAULT_HEALTH_CHECK_JITTER;
        private int healthCheckFailedConnectionsThreshold = DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD;
        private long healthCheckResubscribeLowerBound =
                DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL.minus(DEFAULT_HEALTH_CHECK_JITTER).toNanos();
        private long healthCheckResubscribeUpperBound =
                DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL.plus(DEFAULT_HEALTH_CHECK_JITTER).toNanos();;

        /**
         * Creates a new instance with default settings.
         *
         * @deprecated use {@link RoundRobinLoadBalancers#builder(String)} instead.
         */
        @Deprecated // FIXME: 0.43 - remove deprecated constructor
        public Builder() {
            this(UUID.randomUUID().toString());
        }

        Builder(final String id) {
            this.id = requireNonNull(id);
        }

        @Override
        public RoundRobinLoadBalancerFactory.Builder<ResolvedAddress, C> linearSearchSpace(int linearSearchSpace) {
            if (linearSearchSpace < 0) {
                throw new IllegalArgumentException("linearSearchSpace: " + linearSearchSpace + " (expected >=0)");
            }
            this.linearSearchSpace = linearSearchSpace;
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
            validate(interval, jitter);
            this.healthCheckInterval = interval;
            this.healthCheckJitter = jitter;
            return this;
        }

        @Override
        public RoundRobinLoadBalancerFactory.Builder<ResolvedAddress, C> healthCheckResubscribeInterval(
                Duration interval, Duration jitter) {
            validate(interval, jitter);
            this.healthCheckResubscribeLowerBound = interval.minus(jitter).toNanos();
            this.healthCheckResubscribeUpperBound = interval.plus(jitter).toNanos();
            return this;
        }

        private static void validate(Duration interval, Duration jitter) {
            ensurePositive(interval, "interval");
            ensureNonNegative(jitter, "jitter");
            final Duration lowerBound = interval.minus(jitter);
            if (!isPositive(lowerBound)) {
                throw new IllegalArgumentException("interval (" + interval + ") minus jitter (" + jitter +
                        ") must be greater than 0, current=" + lowerBound);
            }
            final Duration upperBound = interval.plus(jitter);
            if (!isPositive(upperBound)) {
                throw new IllegalArgumentException("interval (" + interval + ") plus jitter (" + jitter +
                        ") must not overflow, current=" + upperBound);
            }
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
                return new RoundRobinLoadBalancerFactory<>(linearSearchSpace, null);
            }

            HealthCheckConfig healthCheckConfig = new HealthCheckConfig(
                            this.backgroundExecutor == null ? SharedExecutor.getInstance() : this.backgroundExecutor,
                    healthCheckInterval, healthCheckJitter, healthCheckFailedConnectionsThreshold,
                    healthCheckResubscribeLowerBound, healthCheckResubscribeUpperBound);

            return new RoundRobinLoadBalancerFactory<>(linearSearchSpace, healthCheckConfig);
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
}
