package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.LoadBalancedConnection;

// TODO: this has to be public for the service loading to work.
interface LoadBalancerBuilderProvider {
    <ResolvedAddress, C extends LoadBalancedConnection> LoadBalancerBuilder<ResolvedAddress, C>
    newBuilder(String id, LoadBalancerBuilder<ResolvedAddress, C> builder);
}
