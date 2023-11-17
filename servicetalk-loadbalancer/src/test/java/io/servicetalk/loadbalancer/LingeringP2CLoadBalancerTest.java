package io.servicetalk.loadbalancer;

public class LingeringP2CLoadBalancerTest extends LingeringLoadBalancerTest {
    @Override
    protected LoadBalancerBuilder<String, TestLoadBalancedConnection> baseLoadBalancerBuilder() {
        return LoadBalancers.builder(getClass().getSimpleName());
    }
}
