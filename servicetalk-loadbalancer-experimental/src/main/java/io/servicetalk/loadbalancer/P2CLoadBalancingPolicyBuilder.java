package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.LoadBalancedConnection;

import javax.annotation.Nullable;
import java.util.Random;

import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;

/**
 * A builder for immutable {@link P2CLoadBalancingPolicy} instances.
 */
public final class P2CLoadBalancingPolicyBuilder {

    private static final boolean DEFAULT_IGNORE_WEIGHTS = false;
    private static final int DEFAULT_MAX_EFFORT = 5;
    private static final boolean DEFAULT_FAIL_OPEN_POLICY = LoadBalancingPolicy.DEFAULT_FAIL_OPEN_POLICY;

    private boolean ignoreWeights = DEFAULT_IGNORE_WEIGHTS;
    private int maxEffort = DEFAULT_MAX_EFFORT;
    private boolean failOpen = DEFAULT_FAIL_OPEN_POLICY;
    @Nullable
    private Random random;

    /**
     * Set the maximum number of attempts that P2C will attempt to select a pair with at least one
     * healthy host.
     * Defaults to {@value DEFAULT_MAX_EFFORT}.
     *
     * @param maxEffort the maximum number of attempts.
     * @return {@code this}
     */
    public P2CLoadBalancingPolicyBuilder maxEffort(final int maxEffort) {
        this.maxEffort = ensurePositive(maxEffort, "maxEffort");
        return this;
    }

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
    public P2CLoadBalancingPolicyBuilder failOpen(final boolean failOpen) {
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
    public P2CLoadBalancingPolicyBuilder ignoreWeights(final boolean ignoreWeights) {
        this.ignoreWeights = ignoreWeights;
        return this;
    }

    // For testing purposes only.
    P2CLoadBalancingPolicyBuilder random(Random random) {
        this.random = random;
        return this;
    }

    /**
     * Construct an immutable {@link P2CLoadBalancingPolicy}.
     *
     * @param <ResolvedAddress> the type of the resolved address.
     * @param <C>               the refined type of the {@link LoadBalancedConnection}.
     * @return the concrete {@link P2CLoadBalancingPolicy}.
     */
    public <ResolvedAddress, C extends LoadBalancedConnection> LoadBalancingPolicy<ResolvedAddress, C> build() {
        return new P2CLoadBalancingPolicy<>(ignoreWeights, maxEffort, failOpen, random);
    }
}
