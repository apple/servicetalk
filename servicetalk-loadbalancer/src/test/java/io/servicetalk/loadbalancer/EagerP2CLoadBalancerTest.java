package io.servicetalk.loadbalancer;

public class EagerP2CLoadBalancerTest extends EagerLoadBalancerTest {

    @Override
    protected LoadBalancerBuilder<String, TestLoadBalancedConnection> baseLoadBalancerBuilder() {
        return LoadBalancers.builder(getClass().getSimpleName());
    }
}
