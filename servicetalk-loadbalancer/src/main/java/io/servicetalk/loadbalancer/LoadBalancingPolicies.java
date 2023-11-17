package io.servicetalk.loadbalancer;

final class LoadBalancingPolicies {

    public static RoundRobinLoadBalancingPolicy roundRobin() {
        return RoundRobinLoadBalancingPolicy.builder().build();
    }

    public static P2CLoadBalancingPolicy p2c() {
        return P2CLoadBalancingPolicy.builder().build();
    }


    private LoadBalancingPolicies() {
        // no instances
    }
}
