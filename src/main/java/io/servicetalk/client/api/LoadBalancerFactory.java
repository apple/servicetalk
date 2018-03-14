/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.client.api;

import io.servicetalk.client.api.ServiceDiscoverer.Event;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;

/**
 * A factory for creating {@link LoadBalancer} instances.
 * @param <ResolvedAddress> the type of address after resolution.
 */
@FunctionalInterface
public interface LoadBalancerFactory<ResolvedAddress, C extends ListenableAsyncCloseable> {

    /**
     * Create a new {@link LoadBalancer}.
     * @param eventPublisher A stream of {@link Event}s which the {@link LoadBalancer} can use to connect to physical hosts.
     *                       Typically generated from a {@link ServiceDiscoverer}.
     * @param connectionFactory {@link ConnectionFactory} that the returned {@link LoadBalancer} will use to generate new connections.
     *                          Returned {@link LoadBalancer} will own the responsibility for this {@link ConnectionFactory} and hence
     *                          will call {@link ConnectionFactory#closeAsync()} when {@link LoadBalancer#closeAsync()} is called.
     * @return a new {@link LoadBalancer}.
     */
    LoadBalancer<C> newLoadBalancer(Publisher<? extends Event<ResolvedAddress>> eventPublisher,
                                    ConnectionFactory<ResolvedAddress, ? extends C> connectionFactory);
}
