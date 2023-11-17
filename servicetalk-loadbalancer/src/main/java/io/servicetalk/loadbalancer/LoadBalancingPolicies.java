package io.servicetalk.loadbalancer;

final class LoadBalancingPolicies {

    public static RoundRobinLoadBalancingPolicy roundRobin() {
        return new RoundRobinLoadBalancingPolicy.Builder().build();
    }

    public static P2CLoadBalancingPolicy p2c() {
        return new P2CLoadBalancingPolicy.Builder().build();
    }


    private LoadBalancingPolicies() {
        // no instances
    }
}
