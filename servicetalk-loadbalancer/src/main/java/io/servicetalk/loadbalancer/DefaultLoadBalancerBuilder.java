package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Collection;

import static io.servicetalk.loadbalancer.L4HealthCheck.DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD;
import static io.servicetalk.loadbalancer.L4HealthCheck.DEFAULT_HEALTH_CHECK_INTERVAL;
import static io.servicetalk.loadbalancer.L4HealthCheck.DEFAULT_HEALTH_CHECK_JITTER;
import static io.servicetalk.loadbalancer.L4HealthCheck.DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL;
import static io.servicetalk.loadbalancer.L4HealthCheck.validateHealthCheckIntervals;
import static java.util.Objects.requireNonNull;

final class DefaultLoadBalancerBuilder<ResolvedAddress, C extends LoadBalancedConnection>
        implements LoadBalancerBuilder<ResolvedAddress, C> {

    private static final int DEFAULT_LINEAR_SEARCH_SPACE = Integer.MAX_VALUE;
    private static final LoadBalancingPolicy DEFAULT_LOAD_BALANCING_POLICY = LoadBalancingPolicies.roundRobin();

    private final String id;
    private LoadBalancingPolicy loadBalancingPolicy = DEFAULT_LOAD_BALANCING_POLICY;
    private int linearSearchSpace = DEFAULT_LINEAR_SEARCH_SPACE;

    @Nullable
    private Executor backgroundExecutor;
    private Duration healthCheckInterval = DEFAULT_HEALTH_CHECK_INTERVAL;
    private Duration healthCheckJitter = DEFAULT_HEALTH_CHECK_JITTER;
    private int healthCheckFailedConnectionsThreshold = DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD;
    private long healthCheckResubscribeLowerBound =
            DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL.minus(DEFAULT_HEALTH_CHECK_JITTER).toNanos();
    private long healthCheckResubscribeUpperBound =
            DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL.plus(DEFAULT_HEALTH_CHECK_JITTER).toNanos();;

    // package private constructor so users must funnel through providers in `LoadBalancers`
    DefaultLoadBalancerBuilder(final String id) {
        this.id = requireNonNull(id, "id");
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> linearSearchSpace(int linearSearchSpace) {
        if (linearSearchSpace <= 0) {
            throw new IllegalArgumentException("Illegal linear search space: " + linearSearchSpace +
                    ".Search space must be a positive number.");
        }
        this.linearSearchSpace = linearSearchSpace;
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> loadBalancingPolicy(LoadBalancingPolicy loadBalancingPolicy) {
        this.loadBalancingPolicy = requireNonNull(loadBalancingPolicy, "loadBalancingPolicy");
        return this;
    }

    public LoadBalancerBuilder<ResolvedAddress, C> backgroundExecutor(Executor backgroundExecutor) {
        this.backgroundExecutor = requireNonNull(backgroundExecutor, "backgroundExecutor");
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
        this.healthCheckResubscribeLowerBound = interval.minus(jitter).toNanos();
        this.healthCheckResubscribeUpperBound = interval.plus(jitter).toNanos();
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> healthCheckFailedConnectionsThreshold(
            int threshold) {
        if (threshold == 0) {
            throw new IllegalArgumentException("Health check failed connections threshold should not be 0");
        }
        this.healthCheckFailedConnectionsThreshold = threshold;
        return this;
    }

    public LoadBalancerFactory<ResolvedAddress, C> build() {
        HealthCheckConfig healthCheckConfig = new HealthCheckConfig(
                this.backgroundExecutor == null ? RoundRobinLoadBalancerFactory.SharedExecutor.getInstance() : this.backgroundExecutor,
                healthCheckInterval, healthCheckJitter, healthCheckFailedConnectionsThreshold,
                healthCheckResubscribeLowerBound, healthCheckResubscribeUpperBound);
        return new DefaultLoadBalancerFactory<>(id, loadBalancingPolicy, linearSearchSpace, healthCheckConfig);
    }

    private static final class DefaultLoadBalancerFactory<ResolvedAddress, C extends LoadBalancedConnection>
            implements LoadBalancerFactory<ResolvedAddress, C> {

        private final String id;
        private final LoadBalancingPolicy loadBalancingPolicy;
        private final int linearSearchSpace;
        private final HealthCheckConfig healthCheckConfig;

        // TODO: this is awkward because LoadBalancingPolicy isn't immutable. We may need them to be immutable and build
        //  a builder interface around them.
        DefaultLoadBalancerFactory(final String id, final LoadBalancingPolicy loadBalancingPolicy,
        final int linearSearchSpace, final HealthCheckConfig healthCheckConfig) {
            this.id = requireNonNull(id, "id");
            this.loadBalancingPolicy = requireNonNull(loadBalancingPolicy, "loadBalancingPolicy");
            this.linearSearchSpace = linearSearchSpace;
            this.healthCheckConfig = requireNonNull(healthCheckConfig, "healthCheckConfig");
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
