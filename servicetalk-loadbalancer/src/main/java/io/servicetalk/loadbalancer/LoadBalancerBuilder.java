package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.concurrent.api.Executor;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

public final class LoadBalancerBuilder<ResolvedAddress, C extends LoadBalancedConnection> {

    private final String id;

    // package private constructor so users must funnel through providers in `LoadBalancers`
    LoadBalancerBuilder(final String id) {
        this.id = requireNonNull(id, "id");
    }

    /**
     * Set the {@code loadBalancingPolicy} to use with this load balancer.
     * @param loadBalancingPolicy the policy to use
     * @return {@code this}
     */
    public LoadBalancerBuilder<ResolvedAddress, C> loadBalancingPolicy(LoadBalancingPolicy loadBalancingPolicy) {
        throw new RuntimeException("Not implemented.");
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
    public LoadBalancerBuilder<ResolvedAddress, C> backgroundExecutor(Executor backgroundExecutor) {
        throw new RuntimeException("Not implemented.");
    }

    // TODO: these healthCheck* methods should be moved into their own OutlierDetection configuration instance
    //  and much like the LoadBalancingPolicy, we should be able to add `OutlierDetectionPolicy`s
    /**
     * Configure an interval for health checking a host that failed to open connections. If no interval is provided
     * using this method, a default value will be used.
     * <p>
     * {@link #healthCheckFailedConnectionsThreshold(int)} can be used to disable the health checking mechanism
     * and always consider all hosts for establishing new connections.
     *
     * @param interval interval at which a background health check will be scheduled.
     * @param jitter the amount of jitter to apply to each retry {@code interval}.
     * @return {@code this}.
     * @see #healthCheckFailedConnectionsThreshold(int)
     */
    public LoadBalancerBuilder<ResolvedAddress, C> healthCheckInterval(Duration interval, Duration jitter) {
        throw new RuntimeException("Not implemented.");
    }

    /**
     * Configure an interval for re-subscribing to the original events stream in case all existing hosts become
     * unhealthy.
     * <p>
     * In situations when there is a latency between {@link ServiceDiscoverer} propagating the updated state and all
     * known hosts become unhealthy, which could happen due to intermediate caching layers, re-subscribe to the
     * events stream can help to exit from a dead state.
     * <p>
     * {@link #healthCheckFailedConnectionsThreshold(int)} can be used to disable the health checking mechanism
     * and always consider all hosts for establishing new connections.
     *
     * @param interval interval at which re-subscribes will be scheduled.
     * @param jitter the amount of jitter to apply to each re-subscribe {@code interval}.
     * @return {@code this}.
     * @see #healthCheckFailedConnectionsThreshold(int)
     */
    LoadBalancerBuilder<ResolvedAddress, C> healthCheckResubscribeInterval(Duration interval, Duration jitter) {
        throw new RuntimeException("Not implemented.");
    }

    /**
     * Configure a threshold for consecutive connection failures to a host. When the {@link LoadBalancer}
     * consecutively fails to open connections in the amount greater or equal to the specified value,
     * the host will be marked as unhealthy and connection establishment will take place in the background
     * repeatedly until a connection is established. During that time, the host will not take part in
     * load balancing selection.
     * <p>
     * Use a negative value of the argument to disable health checking.
     *
     * @param threshold number of consecutive connection failures to consider a host unhealthy and eligible for
     * background health checking. Use negative value to disable the health checking mechanism.
     * @return {@code this}.
     * @see #backgroundExecutor(Executor)
     */
    LoadBalancerBuilder<ResolvedAddress, C> healthCheckFailedConnectionsThreshold(int threshold) {
        throw new RuntimeException("Not implemented.");
    }

    /**
     * Builds the {@link LoadBalancerFactory} configured by this builder.
     *
     * @return a new instance of {@link LoadBalancerFactory} with settings from this builder.
     */
    LoadBalancerFactory<ResolvedAddress, C> build() {
        throw new RuntimeException("Not implemented.");
    }
}
