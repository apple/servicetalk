/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.loadbalancer.ConnectionPoolStrategy.ConnectionPoolStrategyFactory;
import io.servicetalk.transport.api.ExecutionStrategy;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

final class DefaultLoadBalancerBuilder<ResolvedAddress, C extends LoadBalancedConnection>
        implements LoadBalancerBuilder<ResolvedAddress, C> {

    private final String id;
    private LoadBalancingPolicy<ResolvedAddress, C> loadBalancingPolicy = defaultLoadBalancingPolicy();

    @Nullable
    private Executor backgroundExecutor;
    @Nullable
    private LoadBalancerObserverFactory loadBalancerObserverFactory;
    private ConnectionPoolStrategyFactory<C> connectionPoolStrategyFactory = defaultConnectionPoolStrategyFactory();
    private OutlierDetectorConfig outlierDetectorConfig = OutlierDetectorConfig.DEFAULT_CONFIG;

    // package private constructor so users must funnel through providers in `LoadBalancers`
    DefaultLoadBalancerBuilder(final String id) {
        this.id = requireNonNull(id, "id");
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> loadBalancingPolicy(
            LoadBalancingPolicy<ResolvedAddress, C> loadBalancingPolicy) {
        this.loadBalancingPolicy = requireNonNull(loadBalancingPolicy, "loadBalancingPolicy");
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> loadBalancerObserver(
            @Nullable LoadBalancerObserver loadBalancerObserver) {
        return loadBalancerObserver(ignored -> loadBalancerObserver);
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> loadBalancerObserver(
            @Nullable LoadBalancerObserverFactory loadBalancerObserverFactory) {
        this.loadBalancerObserverFactory = loadBalancerObserverFactory;
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> outlierDetectorConfig(OutlierDetectorConfig outlierDetectorConfig) {
        this.outlierDetectorConfig = outlierDetectorConfig == null ?
                OutlierDetectorConfig.DEFAULT_CONFIG : outlierDetectorConfig;
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> connectionPoolConfig(
            ConnectionPoolConfig connectionPoolConfig) {
        this.connectionPoolStrategyFactory = convertPoolStrategy(requireNonNull(connectionPoolConfig,
                "connectionPoolConfig"));
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> backgroundExecutor(Executor backgroundExecutor) {
        this.backgroundExecutor = new NormalizedTimeSourceExecutor(backgroundExecutor);
        return this;
    }

    @Override
    public LoadBalancerFactory<ResolvedAddress, C> build() {
        final HealthCheckConfig healthCheckConfig;
        final Executor executor = getExecutor();
        if (OutlierDetectorConfig.allDisabled(outlierDetectorConfig)) {
            healthCheckConfig = null;
        } else {
            healthCheckConfig = new HealthCheckConfig(
                    executor,
                    outlierDetectorConfig.failureDetectorInterval(),
                    outlierDetectorConfig.failureDetectorIntervalJitter(),
                    outlierDetectorConfig.failedConnectionsThreshold(),
                    outlierDetectorConfig.serviceDiscoveryResubscribeInterval(),
                    outlierDetectorConfig.serviceDiscoveryResubscribeJitter());
        }
        final LoadBalancerObserverFactory loadBalancerObserverFactory = this.loadBalancerObserverFactory != null ?
                this.loadBalancerObserverFactory : ignored -> NoopLoadBalancerObserver.instance();
        final Function<String, OutlierDetector<ResolvedAddress, C>> outlierDetectorFactory;
        if (OutlierDetectorConfig.xDSDisabled(outlierDetectorConfig)) {
            outlierDetectorFactory = (lbDescription) -> new NoopOutlierDetector<>(outlierDetectorConfig, executor);
        } else {
            outlierDetectorFactory = (lbDescription) ->
                new XdsOutlierDetector<>(executor, outlierDetectorConfig, lbDescription);
        }
        return new DefaultLoadBalancerFactory<>(id, loadBalancingPolicy, healthCheckConfig,
                loadBalancerObserverFactory, outlierDetectorFactory, connectionPoolStrategyFactory);
    }

    static final class DefaultLoadBalancerFactory<ResolvedAddress, C extends LoadBalancedConnection>
            implements LoadBalancerFactory<ResolvedAddress, C> {

        private final String id;
        private final LoadBalancingPolicy<ResolvedAddress, C> loadBalancingPolicy;
        private final LoadBalancerObserverFactory loadBalancerObserverFactory;
        @Nullable
        private final Function<String, OutlierDetector<ResolvedAddress, C>> outlierDetectorFactory;
        @Nullable
        private final HealthCheckConfig healthCheckConfig;
        private final ConnectionPoolStrategyFactory<C> connectionPoolStrategyFactory;

        DefaultLoadBalancerFactory(final String id, final LoadBalancingPolicy<ResolvedAddress, C> loadBalancingPolicy,
                                   @Nullable final HealthCheckConfig healthCheckConfig,
                                   final LoadBalancerObserverFactory loadBalancerObserverFactory,
                                   final Function<String, OutlierDetector<ResolvedAddress, C>> outlierDetectorFactory,
                                   final ConnectionPoolStrategyFactory<C> connectionPoolStrategyFactory) {
            this.id = requireNonNull(id, "id");
            this.loadBalancingPolicy = requireNonNull(loadBalancingPolicy, "loadBalancingPolicy");
            this.loadBalancerObserverFactory = requireNonNull(loadBalancerObserverFactory,
                    "loadBalancerObserverFactory");
            this.connectionPoolStrategyFactory = requireNonNull(
                    connectionPoolStrategyFactory, "connectionPoolStrategyFactory");
            this.healthCheckConfig = healthCheckConfig;
            this.outlierDetectorFactory = outlierDetectorFactory;
        }

        @Override
        public <T extends C> LoadBalancer<T> newLoadBalancer(String targetResource,
             Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
             ConnectionFactory<ResolvedAddress, T> connectionFactory) {
            throw new UnsupportedOperationException("Generic constructor not supported by " +
                    DefaultLoadBalancer.class.getSimpleName());
        }

        @Override
        public <T extends C> LoadBalancer<T> newLoadBalancer(
                Publisher<? extends ServiceDiscovererEvent<ResolvedAddress>> eventPublisher,
                ConnectionFactory<ResolvedAddress, T> connectionFactory) {
            throw new UnsupportedOperationException("Generic constructor not supported by " +
                    DefaultLoadBalancer.class.getSimpleName());
        }

        @Override
        public LoadBalancer<C> newLoadBalancer(
                Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
                ConnectionFactory<ResolvedAddress, C> connectionFactory, String targetResource) {
            return new DefaultLoadBalancer<>(id, targetResource, eventPublisher,
                    DefaultHostPriorityStrategy::new,
                    loadBalancingPolicy.buildSelector(Collections.emptyList(), targetResource),
                    connectionPoolStrategyFactory.buildStrategy(targetResource), connectionFactory,
                    loadBalancerObserverFactory, healthCheckConfig, outlierDetectorFactory);
        }

        @Override
        public ExecutionStrategy requiredOffloads() {
            // We do not block
            return ExecutionStrategy.offloadNone();
        }
    }

    private Executor getExecutor() {
        return backgroundExecutor ==
                null ? RoundRobinLoadBalancerFactory.SharedExecutor.getInstance() : backgroundExecutor;
    }

    private static <C extends LoadBalancedConnection> ConnectionPoolStrategyFactory<C> convertPoolStrategy(
            ConnectionPoolConfig connectionPoolStrategyConfig) {
        if (connectionPoolStrategyConfig instanceof ConnectionPoolConfig.P2CStrategy) {
            ConnectionPoolConfig.P2CStrategy strategy = (ConnectionPoolConfig.P2CStrategy) connectionPoolStrategyConfig;
            return P2CConnectionPoolStrategy.factory(strategy.maxEffort, strategy.corePoolSize, strategy.forceCorePool);
        } else if (connectionPoolStrategyConfig instanceof ConnectionPoolConfig.CorePoolStrategy) {
            ConnectionPoolConfig.CorePoolStrategy strategy =
                    (ConnectionPoolConfig.CorePoolStrategy) connectionPoolStrategyConfig;
            return CorePoolConnectionPoolStrategy.factory(strategy.corePoolSize, strategy.forceCorePool);
        } else if (connectionPoolStrategyConfig instanceof ConnectionPoolConfig.LinearSearchStrategy) {
            ConnectionPoolConfig.LinearSearchStrategy strategy =
                    (ConnectionPoolConfig.LinearSearchStrategy) connectionPoolStrategyConfig;
            return LinearSearchConnectionPoolStrategy.factory(strategy.linearSearchSpace);
        } else {
            throw new IllegalStateException("Unexpected ConnectionPoolConfig: " +
                    connectionPoolStrategyConfig.getClass().getName());
        }
    }

    private static <ResolvedAddress, C extends LoadBalancedConnection>
    LoadBalancingPolicy<ResolvedAddress, C> defaultLoadBalancingPolicy() {
        return LoadBalancingPolicies.roundRobin().build();
    }

    private static <C extends LoadBalancedConnection> ConnectionPoolStrategyFactory<C>
    defaultConnectionPoolStrategyFactory() {
        return convertPoolStrategy(ConnectionPoolConfig.linearSearch());
    }
}
