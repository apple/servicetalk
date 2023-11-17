package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.LoadBalancedConnection;

/**
 * Definition of the selector mechanism used for load balancing.
 */
interface LoadBalancingPolicy {
    /**
     * The name of the load balancing policy
     * @return the name of the load balancing policy
     */
    String loadBalancerName();

    <ResolvedAddress, C extends LoadBalancedConnection> HostSelector<ResolvedAddress, C> buildSelector(String targetResource);
}