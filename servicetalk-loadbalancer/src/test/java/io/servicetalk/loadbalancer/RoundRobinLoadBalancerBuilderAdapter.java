package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.concurrent.api.Executor;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

final class RoundRobinLoadBalancerBuilderAdapter {

    static LoadBalancerBuilder<String, TestLoadBalancedConnection> baseLoadBalancerBuilder(Class<?> clazz) {
        return new OldRoundRobinAdapter(requireNonNull(clazz, "clazz"));
    }

    private static final class OldRoundRobinAdapter implements LoadBalancerBuilder<String, TestLoadBalancedConnection> {

        private RoundRobinLoadBalancerBuilder<String, TestLoadBalancedConnection> underlying;

        private OldRoundRobinAdapter(Class<?> clazz) {
            underlying = RoundRobinLoadBalancers.builder(clazz.getSimpleName());
        }

        @Override
        public LoadBalancerBuilder<String, TestLoadBalancedConnection> loadBalancingPolicy(
                LoadBalancingPolicy loadBalancingPolicy) {
            throw new IllegalStateException("Cannot set new policy for old round robin");
        }

        @Override
        public LoadBalancerBuilder<String, TestLoadBalancedConnection> backgroundExecutor(Executor backgroundExecutor) {
            underlying = underlying.backgroundExecutor(backgroundExecutor);
            return this;
        }

        @Override
        public LoadBalancerBuilder<String, TestLoadBalancedConnection> linearSearchSpace(int linearSearchSpace) {
            underlying = underlying.linearSearchSpace(linearSearchSpace);
            return this;
        }

        @Override
        public LoadBalancerBuilder<String, TestLoadBalancedConnection> healthCheckInterval(
                Duration interval, Duration jitter) {
            underlying = underlying.healthCheckInterval(interval, jitter);
            return this;
        }

        @Override
        public LoadBalancerBuilder<String, TestLoadBalancedConnection> healthCheckResubscribeInterval(
                Duration interval, Duration jitter) {
            underlying = underlying.healthCheckResubscribeInterval(interval, jitter);
            return this;
        }

        @Override
        public LoadBalancerBuilder<String, TestLoadBalancedConnection> healthCheckFailedConnectionsThreshold(
                int threshold) {
            underlying = underlying.healthCheckFailedConnectionsThreshold(threshold);
            return this;
        }

        @Override
        public LoadBalancerFactory<String, TestLoadBalancedConnection> build() {
            return underlying.build();
        }
    }
}
