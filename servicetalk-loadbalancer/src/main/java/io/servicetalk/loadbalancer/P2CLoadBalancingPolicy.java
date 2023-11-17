package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.LoadBalancedConnection;

import javax.annotation.Nullable;
import java.util.Random;

final class P2CLoadBalancingPolicy implements LoadBalancingPolicy {

    private final int maxEffort;
    @Nullable
    private final Random random;

    private P2CLoadBalancingPolicy(final int maxEffort, @Nullable final Random random) {
        this.maxEffort = maxEffort;
        this.random = random;
    }

    @Override
    public <ResolvedAddress, C extends LoadBalancedConnection> HostSelector<ResolvedAddress, C>
    buildSelector(String targetResource) {
        return new P2CSelector<>(targetResource, maxEffort, random);
    }

    @Override
    public String loadBalancerName() {
        return "P2C";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Builder() {
        }

        private static final int DEFAULT_MAX_EFFORT = 5;
        private int maxEffort = DEFAULT_MAX_EFFORT;
        @Nullable
        private Random random;
        public Builder maxEffort(final int maxEffort) {
            if (maxEffort <= 0) {
                throw new IllegalArgumentException("Illegal maxEffort: " + maxEffort +
                        ". maxEffort must be a positive value");
            }
            this.maxEffort = maxEffort;
            return this;
        }

        public Builder random(Random random) {
            this.random = random;
            return this;
        }

        P2CLoadBalancingPolicy build() {
            return new P2CLoadBalancingPolicy(maxEffort, random);
        }
    }
}