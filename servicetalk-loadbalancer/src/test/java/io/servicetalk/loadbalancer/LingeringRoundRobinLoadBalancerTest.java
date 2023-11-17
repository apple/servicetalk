package io.servicetalk.loadbalancer;

class LingeringRoundRobinLoadBalancerTest extends LingeringLoadBalancerTest {
    @Override
    protected LoadBalancerBuilder<String, TestLoadBalancedConnection> baseLoadBalancerBuilder() {
        return RoundRobinLoadBalancerBuilderAdapter.baseLoadBalancerBuilder(this.getClass());
    }
}
