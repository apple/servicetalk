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

import static io.servicetalk.utils.internal.NumberUtils.ensureNonNegative;
import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;
import static java.util.Objects.requireNonNull;

final class DefaultLoadBalancerBuilder<ResolvedAddress, C extends LoadBalancedConnection>
        implements LoadBalancerBuilder<ResolvedAddress, C> {

    private final String id;

    @Nullable
    private Executor backgroundExecutor;
    @Nullable
    private LoadBalancerObserverFactory loadBalancerObserverFactory;
    private LoadBalancingPolicy<ResolvedAddress, C> loadBalancingPolicy = defaultLoadBalancingPolicy();
    private ConnectionSelectorPolicy<C> connectionSelectorPolicy = defaultConnectionSelectorPolicy();
    private OutlierDetectorConfig outlierDetectorConfig = OutlierDetectorConfig.DEFAULT_CONFIG;
    private Subsetter.SubsetterFactory subsetterFactory = new RandomSubsetter.RandomSubsetterFactory(Integer.MAX_VALUE);
    private int minConnectionsPerHost;

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
    public LoadBalancerBuilder<ResolvedAddress, C> connectionSelectorPolicy(
            ConnectionSelectorPolicy<C> connectionSelectorPolicy) {
        this.connectionSelectorPolicy = requireNonNull(connectionSelectorPolicy, "connectionSelectorPolicy");
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> maxRandomSubsetSize(int maxUsed) {
        subsetterFactory = new RandomSubsetter.RandomSubsetterFactory(ensurePositive(maxUsed, "maxUsed"));
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> minConnectionsPerHost(int minConnectionsPerHost) {
        this.minConnectionsPerHost = ensureNonNegative(minConnectionsPerHost, "minConnectionsPerHost");
        return this;
    }

    @Override
    public LoadBalancerBuilder<ResolvedAddress, C> backgroundExecutor(Executor backgroundExecutor) {
        this.backgroundExecutor = new NormalizedTimeSourceExecutor(backgroundExecutor);
        return this;
    }

    @Override
    public LoadBalancerFactory<ResolvedAddress, C> build() {
        return new DefaultLoadBalancerFactory<>(this);
    }

    static final class DefaultLoadBalancerFactory<ResolvedAddress, C extends LoadBalancedConnection>
            implements LoadBalancerFactory<ResolvedAddress, C> {

        private final String id;
        private final LoadBalancingPolicy<ResolvedAddress, C> loadBalancingPolicy;
        private final ConnectionSelectorPolicy<C> connectionSelectorPolicy;
        private final int minConnectionsPerHost;
        private final OutlierDetectorConfig outlierDetectorConfig;
        private final Subsetter.SubsetterFactory subsetterFactory;
        @Nullable
        private final LoadBalancerObserverFactory loadBalancerObserverFactory;
        private final Executor executor;

        DefaultLoadBalancerFactory(DefaultLoadBalancerBuilder<ResolvedAddress, C> loadBalancerBuilder) {
            this.id = loadBalancerBuilder.id;
            this.loadBalancingPolicy = loadBalancerBuilder.loadBalancingPolicy;
            this.loadBalancerObserverFactory = loadBalancerBuilder.loadBalancerObserverFactory;
            this.outlierDetectorConfig = loadBalancerBuilder.outlierDetectorConfig;
            this.subsetterFactory = loadBalancerBuilder.subsetterFactory;
            this.minConnectionsPerHost = loadBalancerBuilder.minConnectionsPerHost;
            this.connectionSelectorPolicy = loadBalancerBuilder.connectionSelectorPolicy;
            Executor builderExecutor = loadBalancerBuilder.backgroundExecutor;
            this.executor = builderExecutor ==
                    null ? RoundRobinLoadBalancerFactory.SharedExecutor.getInstance() : builderExecutor;
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
            requireNonNull(eventPublisher, "eventPublisher");
            requireNonNull(connectionFactory, "connectionFactory");
            requireNonNull(targetResource, "targetResource");
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
                    DefaultHostPriorityStrategy::new, loadBalancingPolicy, subsetterFactory,
                    connectionSelectorPolicy, connectionFactory, minConnectionsPerHost,
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
                    ", connectionSelectorPolicy=" + connectionSelectorPolicy +
                    ", outlierDetectorConfig=" + outlierDetectorConfig +
                    ", subsetterFactory=" + subsetterFactory +
                    ", loadBalancerObserverFactory=" + loadBalancerObserverFactory +
                    ", executor=" + executor +
                    '}';
        }
    }

    private static <ResolvedAddress, C extends LoadBalancedConnection>
    LoadBalancingPolicy<ResolvedAddress, C> defaultLoadBalancingPolicy() {
        return LoadBalancingPolicies.roundRobin().build();
    }

    private static <C extends LoadBalancedConnection>
    ConnectionSelectorPolicy<C> defaultConnectionSelectorPolicy() {
        return ConnectionSelectorPolicies.linearSearch();
    }
}
