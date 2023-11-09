package io.servicetalk.loadbalancer;

public final class LoadBalancingPolicies {

    RoundRobinLoadBalancingPolicy roundRobin() {
        return new RoundRobinLoadBalancingPolicy();
    }



    private LoadBalancingPolicies() {
        // no instances
    }

    public final class P2CLoadBalancingPolicy implements LoadBalancingPolicy {
        private int maxEffort = 5;

        public P2CLoadBalancingPolicy maxEffort(final int maxEffort) {
            this.maxEffort = maxEffort;
            return this;
        }

        @Override
        public String loadBalancerName() {
            return "P2C";
        }
    }

    public final class RoundRobinLoadBalancingPolicy implements LoadBalancingPolicy {
        private int linearSearchSpace = Integer.MAX_VALUE;

        public RoundRobinLoadBalancingPolicy linearSearchSpace(final int linearSearchSpace) {
            this.linearSearchSpace = linearSearchSpace;
            return this;
        }

        @Override
        public String loadBalancerName() {
            return "RoundRobin";
        }
    }
}
