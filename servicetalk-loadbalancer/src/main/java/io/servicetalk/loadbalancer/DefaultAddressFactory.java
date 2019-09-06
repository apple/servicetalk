/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Completable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

class DefaultAddressFactory<R, C extends LoadBalancedConnection> implements LoadBalancedAddressFactory<R,
        ServiceDiscovererEvent<R>, ServiceDiscovererEvent<
        ServiceDiscoveryAwareLoadBalancedAddress<C, R, ServiceDiscovererEvent<R>>>> {

    private final ConcurrentHashMap<R, ServiceDiscoveryAwareLoadBalancedAddress<C, R, ServiceDiscovererEvent<R>>>
            addresses = new ConcurrentHashMap<>();
    private final ConnectionFactory<R, C> connectionFactory;
    private final Function<ServiceDiscovererEvent<R>, ServiceDiscoveryAwareLoadBalancedAddress<C, R,
            ServiceDiscovererEvent<R>>> mapper;

    DefaultAddressFactory(
            final ConnectionFactory<R, C> connectionFactory,
            final Function<ServiceDiscovererEvent<R>,
                    ServiceDiscoveryAwareLoadBalancedAddress<C, R, ServiceDiscovererEvent<R>>> mapper) {
        this.connectionFactory = connectionFactory;
        this.mapper = mapper;
    }

    @Override
    public ServiceDiscovererEvent<ServiceDiscoveryAwareLoadBalancedAddress<C, R, ServiceDiscovererEvent<R>>>
    apply(final ServiceDiscovererEvent<R> event) {
        ServiceDiscoveryAwareLoadBalancedAddress<C, R, ServiceDiscovererEvent<R>> address =
                addresses.computeIfAbsent(event.address(), addr -> mapper.apply(event));
        address.onEvent(event);
        return new DefaultServiceDiscovererEvent<>(address, event.isAvailable());
    }

    @Override
    public Completable onClose() {
        return connectionFactory.onClose();
    }

    @Override
    public Completable closeAsync() {
        return connectionFactory.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return connectionFactory.closeAsyncGracefully();
    }
}
