package io.servicetalk.loadbalancer;

/**
 * Definition of the selector mechanism used for load balancing.
 */
public interface LoadBalancingPolicy {
    /**
     * The name of the load balancing policy
     * @return the name of the load balancing policy
     */
    String loadBalancerName();
}