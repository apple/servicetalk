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
import io.servicetalk.transport.api.ExecutionStrategy;

import java.util.Collection;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

final class DefaultLoadBalancerBuilder<ResolvedAddress, C extends LoadBalancedConnection>
        implements LoadBalancerBuilder<ResolvedAddress, C> {

    private final String id;

    @Nullable
    private Executor backgroundExecutor;
    @Nullable
    private LoadBalancerObserverFactory loadBalancerObserverFactory;
    private LoadBalancingPolicy<ResolvedAddress, C> loadBalancingPolicy = defaultLoadBalancingPolicy();
    private ConnectionPoolPolicy<C> connectionPoolPolicy = ConnectionPoolPolicies.defaultPolicy();;
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
            ConnectionPoolPolicy<C> connectionPoolPolicy) {
        this.connectionPoolPolicy = requireNonNull(connectionPoolPolicy, "connectionPoolPolicy");
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> backgroundExecutor(Executor backgroundExecutor) {
        this.backgroundExecutor = new NormalizedTimeSourceExecutor(backgroundExecutor);
        return this;
    }

    @Override
    public LoadBalancerFactory<ResolvedAddress, C> build() {
        return new DefaultLoadBalancerFactory<>(id, loadBalancingPolicy, loadBalancerObserverFactory,
                connectionPoolPolicy, outlierDetectorConfig, getExecutor());
    }

    static final class DefaultLoadBalancerFactory<ResolvedAddress, C extends LoadBalancedConnection>
            implements LoadBalancerFactory<ResolvedAddress, C> {

        private final String id;
        private final LoadBalancingPolicy<ResolvedAddress, C> loadBalancingPolicy;
        private final ConnectionPoolPolicy<C> connectionPoolPolicy;
        private final OutlierDetectorConfig outlierDetectorConfig;
        @Nullable
        private final LoadBalancerObserverFactory loadBalancerObserverFactory;
        private final Executor executor;

        DefaultLoadBalancerFactory(final String id, final LoadBalancingPolicy<ResolvedAddress, C> loadBalancingPolicy,
                                   @Nullable final LoadBalancerObserverFactory loadBalancerObserverFactory,
                                   final ConnectionPoolPolicy<C> connectionPoolPolicy,
                                   final OutlierDetectorConfig outlierDetectorConfig,
                                   final Executor executor) {
            this.id = requireNonNull(id, "id");
            this.loadBalancingPolicy = requireNonNull(loadBalancingPolicy, "loadBalancingPolicy");
            this.loadBalancerObserverFactory = loadBalancerObserverFactory;
            this.outlierDetectorConfig = requireNonNull(outlierDetectorConfig, "outlierDetectorConfig");
            this.connectionPoolPolicy = requireNonNull(connectionPoolPolicy, "connectionPoolPolicy");
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
                    DefaultHostPriorityStrategy::new, loadBalancingPolicy, new RandomSubsetter(Integer.MAX_VALUE),
                    connectionPoolPolicy, connectionFactory,
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
                    ", connectionPoolPolicy=" + connectionPoolPolicy +
                    ", outlierDetectorConfig=" + outlierDetectorConfig +
                    ", loadBalancerObserverFactory=" + loadBalancerObserverFactory +
                    ", executor=" + executor +
                    '}';
        }
    }

    private Executor getExecutor() {
        return backgroundExecutor ==
                null ? RoundRobinLoadBalancerFactory.SharedExecutor.getInstance() : backgroundExecutor;
    }

    private static <ResolvedAddress, C extends LoadBalancedConnection>
    LoadBalancingPolicy<ResolvedAddress, C> defaultLoadBalancingPolicy() {
        return LoadBalancingPolicies.roundRobin().build();
    }
}
