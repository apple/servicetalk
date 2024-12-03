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
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;
import static java.util.Objects.requireNonNull;

final class DefaultLoadBalancerBuilder<ResolvedAddress, C extends LoadBalancedConnection>
        implements LoadBalancerBuilder<ResolvedAddress, C> {

    private final String id;
    private int randomSubsetSize = Integer.MAX_VALUE;
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
    public LoadBalancerBuilder<ResolvedAddress, C> randomSubsetSize(int randomSubsetSize) {
        this.randomSubsetSize = ensurePositive(randomSubsetSize, "randomSubsetSize");
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> loadBalancerObserver(
            @Nullable LoadBalancerObserverFactory loadBalancerObserverFactory) {
        this.loadBalancerObserverFactory = loadBalancerObserverFactory;
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> outlierDetectorConfig(OutlierDetectorConfig outlierDetectorConfig) {
        this.outlierDetectorConfig = requireNonNull(outlierDetectorConfig, "outlierDetectorConfig");
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> connectionPoolPolicy(
            ConnectionPoolPolicy connectionPoolPolicy) {
        this.connectionPoolStrategyFactory = convertPoolStrategy(requireNonNull(connectionPoolPolicy,
                "connectionPoolPolicy"));
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> backgroundExecutor(Executor backgroundExecutor) {
        this.backgroundExecutor = new NormalizedTimeSourceExecutor(backgroundExecutor);
        return this;
    }

    @Override
    public LoadBalancerFactory<ResolvedAddress, C> build() {
        return new DefaultLoadBalancerFactory<>(id, loadBalancingPolicy, randomSubsetSize, loadBalancerObserverFactory,
                connectionPoolStrategyFactory, outlierDetectorConfig, getExecutor());
    }

    static final class DefaultLoadBalancerFactory<ResolvedAddress, C extends LoadBalancedConnection>
            implements LoadBalancerFactory<ResolvedAddress, C> {

        private final String id;
        private final LoadBalancingPolicy<ResolvedAddress, C> loadBalancingPolicy;
        private final int subsetSize;
        @Nullable
        private final LoadBalancerObserverFactory loadBalancerObserverFactory;
        private final ConnectionPoolStrategyFactory<C> connectionPoolStrategyFactory;
        private final OutlierDetectorConfig outlierDetectorConfig;
        private final Executor executor;

        DefaultLoadBalancerFactory(final String id, final LoadBalancingPolicy<ResolvedAddress, C> loadBalancingPolicy,
                                   final int subsetSize,
                                   @Nullable final LoadBalancerObserverFactory loadBalancerObserverFactory,
                                   final ConnectionPoolStrategyFactory<C> connectionPoolStrategyFactory,
                                   final OutlierDetectorConfig outlierDetectorConfig,
                                   final Executor executor) {
            this.id = requireNonNull(id, "id");
            this.loadBalancingPolicy = requireNonNull(loadBalancingPolicy, "loadBalancingPolicy");
            this.subsetSize = ensurePositive(subsetSize, "subsetSize");
            this.loadBalancerObserverFactory = loadBalancerObserverFactory;
            this.outlierDetectorConfig = requireNonNull(outlierDetectorConfig, "outlierDetectorConfig");
            this.connectionPoolStrategyFactory = requireNonNull(
                    connectionPoolStrategyFactory, "connectionPoolStrategyFactory");
            this.executor = requireNonNull(executor, "executor");
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
            final HealthCheckConfig healthCheckConfig;
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
                    this.loadBalancerObserverFactory : NoopLoadBalancerObserver.factory();
            final Function<String, OutlierDetector<ResolvedAddress, C>> outlierDetectorFactory;
            if (OutlierDetectorConfig.xDSDisabled(outlierDetectorConfig)) {
                outlierDetectorFactory = (lbDescription) -> new NoopOutlierDetector<>(outlierDetectorConfig, executor);
            } else {
                outlierDetectorFactory = (lbDescription) ->
                    new XdsOutlierDetector<>(executor, outlierDetectorConfig, lbDescription);
            }
            return new DefaultLoadBalancer<>(id, targetResource, eventPublisher,
                    DefaultHostPriorityStrategy::new, loadBalancingPolicy, subsetSize,
                    connectionPoolStrategyFactory, connectionFactory,
                    loadBalancerObserverFactory, healthCheckConfig, outlierDetectorFactory);
        }

        @Override
        public ExecutionStrategy requiredOffloads() {
            // We do not block
            return ExecutionStrategy.offloadNone();
        }

        @Override
        public String toString() {
            return "DefaultLoadBalancerFactory{" +
                    "id='" + id + '\'' +
                    ", loadBalancingPolicy=" + loadBalancingPolicy +
                    ", loadBalancerObserverFactory=" + loadBalancerObserverFactory +
                    ", connectionPoolStrategyFactory=" + connectionPoolStrategyFactory +
                    ", outlierDetectorConfig=" + outlierDetectorConfig +
                    ", executor=" + executor +
                    '}';
        }
    }

    private Executor getExecutor() {
        return backgroundExecutor ==
                null ? RoundRobinLoadBalancerFactory.SharedExecutor.getInstance() : backgroundExecutor;
    }

    private static <C extends LoadBalancedConnection> ConnectionPoolStrategyFactory<C> convertPoolStrategy(
            ConnectionPoolPolicy connectionPoolStrategyConfig) {
        if (connectionPoolStrategyConfig instanceof ConnectionPoolPolicy.P2CStrategy) {
            ConnectionPoolPolicy.P2CStrategy strategy = (ConnectionPoolPolicy.P2CStrategy) connectionPoolStrategyConfig;
            return P2CConnectionPoolStrategy.factory(strategy.maxEffort, strategy.corePoolSize, strategy.forceCorePool);
        } else if (connectionPoolStrategyConfig instanceof ConnectionPoolPolicy.CorePoolStrategy) {
            ConnectionPoolPolicy.CorePoolStrategy strategy =
                    (ConnectionPoolPolicy.CorePoolStrategy) connectionPoolStrategyConfig;
            return CorePoolConnectionPoolStrategy.factory(strategy.corePoolSize, strategy.forceCorePool);
        } else if (connectionPoolStrategyConfig instanceof ConnectionPoolPolicy.LinearSearchStrategy) {
            ConnectionPoolPolicy.LinearSearchStrategy strategy =
                    (ConnectionPoolPolicy.LinearSearchStrategy) connectionPoolStrategyConfig;
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
        return convertPoolStrategy(ConnectionPoolPolicy.linearSearch());
    }
}
