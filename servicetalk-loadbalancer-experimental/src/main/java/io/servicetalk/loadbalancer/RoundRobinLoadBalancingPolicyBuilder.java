package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.LoadBalancedConnection;

/**
 * A builder for immutable {@link RoundRobinLoadBalancingPolicy} instances.
 */
public final class RoundRobinLoadBalancingPolicyBuilder {

    private static final boolean DEFAULT_IGNORE_WEIGHTS = false;
    private static final boolean DEFAULT_FAIL_OPEN_POLICY = LoadBalancingPolicy.DEFAULT_FAIL_OPEN_POLICY;

    private boolean failOpen = DEFAULT_FAIL_OPEN_POLICY;
    private boolean ignoreWeights = DEFAULT_IGNORE_WEIGHTS;

    /**
     * Set whether the selector should fail-open in the event no healthy hosts are found.
     * When a load balancing policy is configured to fail-open and is unable to find a healthy host, it will attempt
     * to select or establish a connection from an arbitrary host even if it is unlikely to return a healthy
     * session.
     * Defaults to {@value DEFAULT_FAIL_OPEN_POLICY}.
     *
     * @param failOpen if true, will attempt  to select or establish a connection from an arbitrary host even if it
     *                 is unlikely to return a healthy  session.
     * @return {@code this}
     */
    public RoundRobinLoadBalancingPolicyBuilder failOpen(final boolean failOpen) {
        this.failOpen = failOpen;
        return this;
    }

    /**
     * Set whether the host selector should ignore {@link Host}s weight.
     * Host weight influences the probability it will be selected to serve a request. The host weight can come
     * from many sources including known host capacity, priority groups, and others, so ignoring weight
     * information can lead to other features not working properly and should be used with care.
     * Defaults to {@value DEFAULT_IGNORE_WEIGHTS}.
     *
     * @param ignoreWeights whether the host selector should ignore host weight information.
     * @return {@code this}
     */
    public RoundRobinLoadBalancingPolicyBuilder ignoreWeights(final boolean ignoreWeights) {
        this.ignoreWeights = ignoreWeights;
        return this;
    }

    /**
     * Construct the immutable {@link RoundRobinLoadBalancingPolicy}.
     *
     * @param <ResolvedAddress> the type of the resolved address.
     * @param <C>               the refined type of the {@link LoadBalancedConnection}.
     * @return the concrete {@link RoundRobinLoadBalancingPolicy}.
     */
    public <ResolvedAddress, C extends LoadBalancedConnection> LoadBalancingPolicy<ResolvedAddress, C>
    build() {
        return new RoundRobinLoadBalancingPolicy<>(failOpen, ignoreWeights);
    }
}
