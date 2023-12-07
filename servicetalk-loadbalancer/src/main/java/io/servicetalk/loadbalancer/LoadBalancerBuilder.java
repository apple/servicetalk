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
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.context.api.ContextMap;

import java.time.Duration;
import java.util.function.Predicate;

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
 *     the default policy is round-robin.</li>
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
 *     <li>If health checking is configured, for hosts to which consecutive connection attempts fail, a background
 *     health checking task is created and the host is not considered for opening new connections until the background
 *     check succeeds to create a connection. Upon such event, the connection can immediately be reused and future
 *     attempts will again consider this host. This behaviour can be disabled using a negative argument for
 *     {@link LoadBalancerBuilder#healthCheckFailedConnectionsThreshold(int)} and the failing host will take
 *     part in the regular host selection cycle for trying to establish a connection on the request path.</li>
 * </ul>
 *
 * @param <ResolvedAddress> The resolved address type.
 * @param <C> The type of connection.
 */
interface LoadBalancerBuilder<ResolvedAddress, C extends LoadBalancedConnection> {
    /**
     * Set the {@code loadBalancingPolicy} to use with this load balancer.
     * @param loadBalancingPolicy the policy to use
     * @return {@code this}
     */
    LoadBalancerBuilder<ResolvedAddress, C> loadBalancingPolicy(LoadBalancingPolicy loadBalancingPolicy);

    /**
     * This {@link LoadBalancer} may monitor hosts to which connection establishment has failed
     * using health checks that run in the background. The health check tries to establish a new connection
     * and if it succeeds, the host is returned to the load balancing pool. As long as the connection
     * establishment fails, the host is not considered for opening new connections for processed requests.
     * If an {@link Executor} is not provided using this method, a default shared instance is used
     * for all {@link LoadBalancer LoadBalancers} created by this factory.
     * <p>
     * {@link #healthCheckFailedConnectionsThreshold(int)} can be used to disable this mechanism and always
     * consider all hosts for establishing new connections.
     *
     * @param backgroundExecutor {@link Executor} on which to schedule health checking.
     * @return {@code this}.
     * @see #healthCheckFailedConnectionsThreshold(int)
     */
    LoadBalancerBuilder<ResolvedAddress, C> backgroundExecutor(Executor backgroundExecutor);

    /**
     * Sets the linear search space to find an available connection for the next host.
     * <p>
     * When the next host has already opened connections, this {@link LoadBalancer} will perform a linear search for
     * a connection that can serve the next request up to a specified number of attempts. If there are more open
     * connections, selection of remaining connections will be attempted randomly.
     * <p>
     * Higher linear search space may help to better identify excess connections in highly concurrent environments,
     * but may result in slightly increased selection time.
     *
     * @param linearSearchSpace the number of attempts for a linear search space, {@code 0} enforces random
     * selection all the time.
     * @return {@code this}.
     */
    LoadBalancerBuilder<ResolvedAddress, C> linearSearchSpace(int linearSearchSpace);

    // TODO: these healthCheck* methods should be moved into their own OutlierDetection configuration instance
    //  and much like the LoadBalancingPolicy, we should be able to add `OutlierDetectionPolicy`s
    /**
     * Configure an interval for health checking a host that failed to open connections. If no interval is provided
     * using this method, a default value will be used.
     * <p>
     * {@link #healthCheckFailedConnectionsThreshold(int)} can be used to disable the health checking mechanism
     * and always consider all hosts for establishing new connections.
     *
     * @param interval interval at which a background health check will be scheduled.
     * @param jitter the amount of jitter to apply to each retry {@code interval}.
     * @return {@code this}.
     * @see #healthCheckFailedConnectionsThreshold(int)
     */
    LoadBalancerBuilder<ResolvedAddress, C> healthCheckInterval(Duration interval, Duration jitter);

    /**
     * Configure an interval for re-subscribing to the original events stream in case all existing hosts become
     * unhealthy.
     * <p>
     * In situations when there is a latency between {@link ServiceDiscoverer} propagating the updated state and all
     * known hosts become unhealthy, which could happen due to intermediate caching layers, re-subscribe to the
     * events stream can help to exit from a dead state.
     * <p>
     * {@link #healthCheckFailedConnectionsThreshold(int)} can be used to disable the health checking mechanism
     * and always consider all hosts for establishing new connections.
     *
     * @param interval interval at which re-subscribes will be scheduled.
     * @param jitter the amount of jitter to apply to each re-subscribe {@code interval}.
     * @return {@code this}.
     * @see #healthCheckFailedConnectionsThreshold(int)
     */
    LoadBalancerBuilder<ResolvedAddress, C> healthCheckResubscribeInterval(Duration interval, Duration jitter);

    /**
     * Configure a threshold for consecutive connection failures to a host. When the {@link LoadBalancer}
     * consecutively fails to open connections in the amount greater or equal to the specified value,
     * the host will be marked as unhealthy and connection establishment will take place in the background
     * repeatedly until a connection is established. During that time, the host will not take part in
     * load balancing selection.
     * <p>
     * Use a negative value of the argument to disable health checking.
     *
     * @param threshold number of consecutive connection failures to consider a host unhealthy and eligible for
     * background health checking. Use negative value to disable the health checking mechanism.
     * @return {@code this}.
     * @see #backgroundExecutor(Executor)
     */
    LoadBalancerBuilder<ResolvedAddress, C> healthCheckFailedConnectionsThreshold(int threshold);

    /**
     * Builds the {@link LoadBalancerFactory} configured by this builder.
     *
     * @return a new instance of {@link LoadBalancerFactory} with settings from this builder.
     */
    LoadBalancerFactory<ResolvedAddress, C> build();
}
