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
import io.servicetalk.client.api.ReservableRequestConcurrencyController;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.HttpResponseMetaData;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SERVER_ERROR_5XX;
import static io.servicetalk.http.api.HttpResponseStatus.TOO_MANY_REQUESTS;
import static io.servicetalk.loadbalancer.DefaultHost.HOST_KEY;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_INTERVAL;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_JITTER;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL;
import static io.servicetalk.loadbalancer.HealthCheckConfig.validateHealthCheckIntervals;
import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;
import static java.time.Duration.ofSeconds;
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
    private LoadBalancerObserver<ResolvedAddress> loadBalancerObserver;
    @Nullable
    private HealthCheckerFactory<ResolvedAddress> healthCheckerFactory;
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
            @Nullable LoadBalancerObserver<ResolvedAddress> loadBalancerObserver) {
        this.loadBalancerObserver = loadBalancerObserver;
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> healthCheckerFactory(
            HealthCheckerFactory<ResolvedAddress> healthCheckerFactory) {
        this.healthCheckerFactory = healthCheckerFactory;
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
        final LoadBalancerObserver<ResolvedAddress> loadBalancerObserver = this.loadBalancerObserver != null ?
                this.loadBalancerObserver : NoopLoadBalancerObserver.instance();
        Supplier<HealthChecker<ResolvedAddress>> healthCheckerSupplier;
        if (healthCheckerFactory == null) {
            healthCheckerSupplier = null;
        } else {
            final Executor executor = getExecutor();
            healthCheckerSupplier = () -> healthCheckerFactory.newHealthChecker(executor,
                    loadBalancerObserver.hostObserver());
        }

        return new DefaultLoadBalancerFactory<>(id, loadBalancingPolicy, linearSearchSpace, healthCheckConfig,
                loadBalancerObserver, healthCheckerSupplier);
    }

    public static final class DefaultHttpLoadBalancerFactory<ResolvedAddress> implements HttpLoadBalancerFactory<ResolvedAddress> {
        private final Config<ResolvedAddress> config;

        private DefaultHttpLoadBalancerFactory(final Config<ResolvedAddress> config) {
            this.config = config;
        }

        @Override
        @SuppressWarnings("deprecation")
        public <T extends FilterableStreamingHttpLoadBalancedConnection> LoadBalancer<T> newLoadBalancer(
                String targetResource,
                Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
                ConnectionFactory<ResolvedAddress, T> connectionFactory) {
            return config.rawFactory.newLoadBalancer(targetResource, eventPublisher, connectionFactory);
        }

        @Override
        public LoadBalancer<FilterableStreamingHttpLoadBalancedConnection> newLoadBalancer(
                final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
                final ConnectionFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> connectionFactory,
                final String targetResource) {
            return config.rawFactory.newLoadBalancer(eventPublisher, connectionFactory, targetResource);
        }

        @Override
        public FilterableStreamingHttpLoadBalancedConnection toLoadBalancedConnection(
                final FilterableStreamingHttpConnection connection,
                final ReservableRequestConcurrencyController concurrencyController,
                final ContextMap context) {
            requireNonNull(context, "context is required for " +
                    DefaultHttpLoadBalancerFactory.class.getSimpleName() +
                    ":toLoadBalancedConnection to get access to the " + Host.class.getSimpleName() +
                    ", owner of this connection.");
            @SuppressWarnings("unchecked")
            final DefaultHost<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> address =
                    context.get(HOST_KEY);
            requireNonNull(address, "Context value for " + HOST_KEY + " is required for " +
                    DefaultHttpLoadBalancerFactory.class.getSimpleName() +
                    ":toLoadBalancedConnection to get access to the " + Host.class.getSimpleName() +
                    ", owner of this connection.");
            return new DefaultHttpLoadBalancedConnection(config.responseErrorMapper, connection, concurrencyController,
                    address, config.requestLatencyHalfLife);
        }
    }

    private static final class Config<Addr> {
        private final LoadBalancerFactory<Addr, FilterableStreamingHttpLoadBalancedConnection> rawFactory;
        private final Function<HttpResponseMetaData, ErrorClass> responseErrorMapper;

        private final Duration requestLatencyHalfLife;

        Config(final LoadBalancerFactory<Addr, FilterableStreamingHttpLoadBalancedConnection> rawFactory,
               final Function<HttpResponseMetaData, ErrorClass> responseErrorMapper,
               final Duration requestLatencyHalfLife) {
            this.rawFactory = rawFactory;
            this.responseErrorMapper = responseErrorMapper;
            this.requestLatencyHalfLife = requestLatencyHalfLife;
        }
    }

    /**
     * A builder for creating instances of {@link DefaultLoadBalancerFactory}.
     *
     * @param <ResolvedAddress> The type of address after resolution for the {@link HttpLoadBalancerFactory} built by
     * this builder.
     */
    public static final class DefaultHttpLoadBalancerFactoryBuilder<ResolvedAddress> {
        private final DefaultLoadBalancerFactory<ResolvedAddress,
                FilterableStreamingHttpLoadBalancedConnection> raw;
        private Function<HttpResponseMetaData, ErrorClass> responseErrorMapper = resp ->
                (resp.status().statusClass() == SERVER_ERROR_5XX || TOO_MANY_REQUESTS.equals(resp.status())) ?
                        ErrorClass.EXT_ORIGIN_REQUEST_FAILED : null;

        private Duration requestLatencyHalfLife = ofSeconds(1);

        private DefaultHttpLoadBalancerFactoryBuilder(
                final DefaultLoadBalancerFactory<ResolvedAddress,
                        FilterableStreamingHttpLoadBalancedConnection> raw) {
            this.raw = requireNonNull(raw);
        }

        /**
         * Builds a {@link DefaultLoadBalancerFactory} using the properties configured on this builder.
         *
         * @return A {@link DefaultLoadBalancerFactory}.
         */
        public DefaultHttpLoadBalancerFactory<ResolvedAddress> build() {
            return new DefaultHttpLoadBalancerFactory<>(
                    new Config<>(raw, responseErrorMapper, requestLatencyHalfLife));
        }

        /**
         * Sets a {@link Predicate} that is invoked for each received response to check whether the response should be
         * considered an error.
         *
         * @param responseErrorMapper A {@link Predicate} to check if a received response should be considered an error.
         * @return {@code this}.
         */
        public DefaultHttpLoadBalancerFactoryBuilder<ResolvedAddress> errorResponsePredicate(
                final Function<HttpResponseMetaData, ErrorClass> responseErrorMapper) {
            this.responseErrorMapper = responseErrorMapper;
            return this;
        }

        /**
         * Controls the half-life of the "request" latency figure. The half-life controls how fast the scoring of that
         * type will decay over time. Decaying is the process that will help the associated host/connection associated
         * with this measurement to be selective again in terms of load balancing.
         * The higher this duration is, the slower the decay will be, therefore it will have a prolonged score recovery,
         * leading to fewer chances of receiving traffic; The lower this duration is, the latency abnormalities will be
         * normalized faster, potentially hiding issues with the destination.
         * @param duration the half-life of the "request" latency figures.
         * @return {@code this}.
         */
        public DefaultHttpLoadBalancerFactoryBuilder<ResolvedAddress> requestLatencyHalfLife(final Duration duration) {
            this.requestLatencyHalfLife = requireNonNull(duration);
            return this;
        }
    }

    private static final class DefaultLoadBalancerFactory<ResolvedAddress, C extends LoadBalancedConnection>
            implements LoadBalancerFactory<ResolvedAddress, C> {

        private final String id;
        private final LoadBalancingPolicy<ResolvedAddress, C> loadBalancingPolicy;
        private final LoadBalancerObserver<ResolvedAddress> loadBalancerObserver;
        private final int linearSearchSpace;
        @Nullable
        private final Supplier<HealthChecker<ResolvedAddress>> healthCheckerFactory;
        @Nullable
        private final HealthCheckConfig healthCheckConfig;

        DefaultLoadBalancerFactory(final String id, final LoadBalancingPolicy<ResolvedAddress, C> loadBalancingPolicy,
                                   final int linearSearchSpace, final HealthCheckConfig healthCheckConfig,
                                   final LoadBalancerObserver<ResolvedAddress> loadBalancerObserver,
                                   final Supplier<HealthChecker<ResolvedAddress>> healthCheckerFactory) {
            this.id = requireNonNull(id, "id");
            this.loadBalancingPolicy = requireNonNull(loadBalancingPolicy, "loadBalancingPolicy");
            this.loadBalancerObserver = requireNonNull(loadBalancerObserver, "loadBalancerObserver");
            this.linearSearchSpace = linearSearchSpace;
            this.healthCheckConfig = healthCheckConfig;
            this.healthCheckerFactory = healthCheckerFactory;
        }

        @Override
        public <T extends C> LoadBalancer<T> newLoadBalancer(String targetResource,
             Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
             ConnectionFactory<ResolvedAddress, T> connectionFactory) {
            return new DefaultLoadBalancer<ResolvedAddress, T>(id, targetResource, eventPublisher,
                    loadBalancingPolicy.buildSelector(Collections.emptyList(), targetResource), connectionFactory,
                    linearSearchSpace, loadBalancerObserver, healthCheckConfig, healthCheckerFactory);
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
