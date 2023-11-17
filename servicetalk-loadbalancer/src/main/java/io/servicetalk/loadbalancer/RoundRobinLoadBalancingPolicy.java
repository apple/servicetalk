package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.LoadBalancedConnection;

final class RoundRobinLoadBalancingPolicy implements LoadBalancingPolicy {

    private RoundRobinLoadBalancingPolicy() {
    }

    @Override
    public <ResolvedAddress, C extends LoadBalancedConnection> HostSelector<ResolvedAddress, C>
    buildSelector(final String targetResource) {
        return new RoundRobinSelector<>(targetResource);
    }

    @Override
    public String loadBalancerName() {
        return "RoundRobin";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Builder() {
        }
        RoundRobinLoadBalancingPolicy build() {
            return new RoundRobinLoadBalancingPolicy();
        }
    }
}