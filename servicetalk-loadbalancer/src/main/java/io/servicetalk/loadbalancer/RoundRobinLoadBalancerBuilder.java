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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.context.api.ContextMap;

import java.util.function.Predicate;

/**
 * Builder for {@link LoadBalancerFactory} that creates {@link LoadBalancer} instances which use a round-robin strategy
 * for selecting connections from a pool of addresses.
 * <p>
 * The addresses are provided via the {@link Publisher published}
 * {@link ServiceDiscovererEvent events} that signal the host's {@link ServiceDiscovererEvent.Status status}.
 * Instances returned handle {@link ServiceDiscovererEvent.Status#AVAILABLE},
 * {@link ServiceDiscovererEvent.Status#EXPIRED}, and {@link ServiceDiscovererEvent.Status#UNAVAILABLE} event statuses.
 * <p>The created instances have the following behaviour:
 * <ul>
 *     <li>Round robining is done at address level.</li>
 *     <li>Connections are created lazily, without any concurrency control on their creation. This can lead to
 *     over-provisioning connections when dealing with a requests surge.</li>
 *     <li>Existing connections are reused unless a selector passed to
 *     {@link LoadBalancer#selectConnection(Predicate, ContextMap)} suggests otherwise. This can lead to situations
 *     where connections will be used to their maximum capacity (for example in the context of pipelining) before new
 *     connections are created.</li>
 *     <li>Closed connections are automatically pruned.</li>
 *     <li>When {@link Publisher}&lt;{@link ServiceDiscovererEvent}&gt; delivers events with
 *     {@link ServiceDiscovererEvent#status()} of value {@link ServiceDiscovererEvent.Status#UNAVAILABLE}, connections
 *     are immediately closed for the associated {@link ServiceDiscovererEvent#address()}. In case of
 *     {@link ServiceDiscovererEvent.Status#EXPIRED}, already established connections to
 *     {@link ServiceDiscovererEvent#address()} are used for requests, but no new connections are created. In case the
 *     address' connections are busy, another host is tried. If all hosts are busy, selection fails with a
 *     {@link io.servicetalk.client.api.ConnectionRejectedException}.</li>
 *     <li>For hosts to which consecutive connection attempts fail, a background health checking task is created and the
 *     host is not considered for opening new connections until the background check succeeds to create a connection.
 *     Upon such event, the connection can immediately be reused and future attempts will again consider this host. This
 *     behaviour can be disabled using a negative argument for
 *     {@link RoundRobinLoadBalancerBuilder#healthCheckFailedConnectionsThreshold(int)} and the failing host will take
 *     part in the regular round robin cycle for trying to establish a connection on the request path.</li>
 * </ul>
 *
 * @param <ResolvedAddress> The resolved address type.
 * @param <C> The type of connection.
 * @see RoundRobinLoadBalancers
 */
public interface RoundRobinLoadBalancerBuilder<ResolvedAddress, C extends LoadBalancedConnection>
        extends LoadBalancerBuilder<ResolvedAddress, C, RoundRobinLoadBalancerBuilder<ResolvedAddress, C>> {

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
    RoundRobinLoadBalancerBuilder<ResolvedAddress, C> linearSearchSpace(int linearSearchSpace);
}
