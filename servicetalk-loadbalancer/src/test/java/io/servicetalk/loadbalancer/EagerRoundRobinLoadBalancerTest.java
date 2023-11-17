package io.servicetalk.loadbalancer;

class EagerRoundRobinLoadBalancerTest extends EagerLoadBalancerTest {
    @Override
    protected LoadBalancerBuilder<String, TestLoadBalancedConnection> baseLoadBalancerBuilder() {
        return RoundRobinLoadBalancerBuilderAdapter.baseLoadBalancerBuilder(this.getClass());
    }
}
