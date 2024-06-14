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

import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.context.api.ContextMap;

import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Builder for {@link LoadBalancerFactory} that creates {@link LoadBalancer} instances based upon the configuration.
 * <p>
 * The addresses are provided via the {@link Publisher published}
 * {@link ServiceDiscovererEvent events} that signal the host's {@link ServiceDiscovererEvent.Status status}.
 * Instances returned handle {@link ServiceDiscovererEvent.Status#AVAILABLE},
 * {@link ServiceDiscovererEvent.Status#EXPIRED}, and {@link ServiceDiscovererEvent.Status#UNAVAILABLE} event statuses.
 * <p>
 * The created instances have the following behaviour:
 * <ul>
 *     <li>Host selection is performed based upon the provided {@link LoadBalancingPolicy}. If no policy is provided
 *     the default policy is round-robin. While it can be policy specific, most policies will avoid unhealthy
 *     hosts.</li>
 *     <li>Host health is determined by the configured {@link OutlierDetectorConfig}. This is inferred from the
 *     results of requests and from actively probing for connectivity in accordance with the config.</li>
 *     <li>Connections are created lazily, without any concurrency control on their creation. This can lead to
 *     over-provisioning connections when dealing with a requests surge.</li>
 *     <li>Existing connections are reused unless a selector passed to
 *     {@link LoadBalancer#selectConnection(Predicate, ContextMap)} suggests otherwise. This can lead to situations
 *     where connections will be used to their maximum capacity (for example in the context of pipelining) before new
 *     connections are created.</li>
 *     <li>Closed connections are automatically pruned.</li>
 *     <li>When {@link Publisher}&lt;{@link ServiceDiscovererEvent}&gt; delivers events with
 *     {@link ServiceDiscovererEvent#status()} of value {@link ServiceDiscovererEvent.Status#UNAVAILABLE}, connections
 *     are immediately closed (gracefully) for the associated {@link ServiceDiscovererEvent#address()}. In case of
 *     {@link ServiceDiscovererEvent.Status#EXPIRED}, already established connections to
 *     {@link ServiceDiscovererEvent#address()} continue to be used for requests, but no new connections are created.
 *     In case the address' connections are busy, another host is tried. If all hosts are busy based on the selection
 *     mechanism of the {@link LoadBalancingPolicy}, selection fails with a
 *     {@link io.servicetalk.client.api.NoActiveHostException}.</li>
 * </ul>
 *
 * @param <ResolvedAddress> The resolved address type.
 * @param <C> The type of connection.
 */
public interface LoadBalancerBuilder<ResolvedAddress, C extends LoadBalancedConnection> {
    /**
     * Set the {@code loadBalancingPolicy} to use with this load balancer.
     * @param loadBalancingPolicy the {@code loadBalancingPolicy} to use
     * @return {@code this}
     */
    LoadBalancerBuilder<ResolvedAddress, C> loadBalancingPolicy(
            LoadBalancingPolicy<ResolvedAddress, C> loadBalancingPolicy);

    /**
     * Set the {@link LoadBalancerObserverFactory} to use with this load balancer.
     * @param loadBalancerObserverFactory the {@link LoadBalancerObserverFactory} to use, or {@code null} to not use an
     *                                    observer.
     * @return {code this}
     */
    LoadBalancerBuilder<ResolvedAddress, C> loadBalancerObserver(
            @Nullable LoadBalancerObserverFactory loadBalancerObserverFactory);

    /**
     * Set the {@link OutlierDetectorConfig} to use with this load balancer.
     * The outlier detection system works in conjunction with the load balancing policy to attempt to avoid hosts
     * that have been determined to be unhealthy or slow. The details of the selection process are determined by the
     * {@link LoadBalancingPolicy} while the health status is determined by the outlier detection configuration.
     * @param outlierDetectorConfig the {@link OutlierDetectorConfig} to use, or {@code null} to use the default
     *                              outlier detection.
     * @return {code this}
     * @see #loadBalancingPolicy(LoadBalancingPolicy)
     */
    LoadBalancerBuilder<ResolvedAddress, C> outlierDetectorConfig(OutlierDetectorConfig outlierDetectorConfig);

    /**
     * Set the {@link ConnectionPoolStrategy} to use with this load balancer.
     * @param connectionPoolConfig the factory of connection pooling strategies to use.
     * @return {@code this}
     */
    LoadBalancerBuilder<ResolvedAddress, C> connectionPoolConfig(ConnectionPoolConfig connectionPoolConfig);

    /**
     * Set the background {@link Executor} to use for determining time and scheduling background tasks such
     * as those associated with outlier detection.
     *
     * @param backgroundExecutor {@link Executor} to use as a time source and for scheduling background tasks.
     * @return {@code this}.
     */
    LoadBalancerBuilder<ResolvedAddress, C> backgroundExecutor(Executor backgroundExecutor);

    /**
     * Builds the {@link LoadBalancerFactory} configured by this builder.
     *
     * @return a new instance of {@link LoadBalancerFactory} with settings from this builder.
     */
    LoadBalancerFactory<ResolvedAddress, C> build();
}
