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
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.toListenableAsyncCloseable;

class DefaultAddressFactory<R, C extends LoadBalancedConnection> implements LoadBalancedAddressFactory<R,
        ServiceDiscovererEvent<R>, ServiceDiscovererEvent<
        ServiceDiscoveryAwareLoadBalancedAddress<C, R, ServiceDiscovererEvent<R>>>> {

    // We currently make a best effort to eagerly clean up the CHM, a better approach may be to make ClientGroup more
    // flexible. We can't use it here because the factory function needs to capture the SDE which is not part of the Key
    // ClientGroup doesn't provide an overloaded lookup with Key+factory so we should consider extending that or an
    // alternative implementation that does.
    private final ConcurrentHashMap<R, ServiceDiscoveryAwareLoadBalancedAddress<C, R, ServiceDiscovererEvent<R>>>
            addresses = new ConcurrentHashMap<>();
    private final ConnectionFactory<R, C> connectionFactory;
    private final BiFunction<ServiceDiscovererEvent<R>, ConnectionFactory<R, C>,
            ServiceDiscoveryAwareLoadBalancedAddress<C, R, ServiceDiscovererEvent<R>>> mapper;
    private final ListenableAsyncCloseable lac;
    private volatile boolean closed;

    DefaultAddressFactory(final ConnectionFactory<R, C> connectionFactory,
                          final BiFunction<ServiceDiscovererEvent<R>, ConnectionFactory<R, C>,
                            ServiceDiscoveryAwareLoadBalancedAddress<C, R, ServiceDiscovererEvent<R>>> mapper) {
        this.connectionFactory = connectionFactory;
        this.mapper = mapper;
        lac = toListenableAsyncCloseable(connectionFactory, completable -> completable.mergeDelayError(
                Completable.defer(() -> {
                    closed = true;
                    addresses.clear(); // best effort to cleanup eagerly, addresses remove themselves when closed
                    return Completable.completed();
                })
        ));
    }

    @Nullable
    @Override
    public ServiceDiscovererEvent<ServiceDiscoveryAwareLoadBalancedAddress<C, R, ServiceDiscovererEvent<R>>>
    apply(final ServiceDiscovererEvent<R> event) {

        ServiceDiscoveryAwareLoadBalancedAddress<C, R, ServiceDiscovererEvent<R>> address =
                addresses.computeIfAbsent(event.address(), addr -> {
                    ServiceDiscoveryAwareLoadBalancedAddress<C, R, ServiceDiscovererEvent<R>> newAddr =
                            mapper.apply(event, connectionFactory);
                    newAddr.onClose().subscribe(() -> addresses.remove(addr));
                    // best effort, some may slip through while shutting down, but they'll eventually remove themselves
                    // when added to a closed AddressSelector or when the AddressSelector itself closes
                    if (closed) {
                        newAddr.closeAsync().subscribe();
                        return null;
                    }
                    return newAddr;
                });

        if (address != null) {
            address.onEvent(event);
            return new DefaultServiceDiscovererEvent<>(address, event.isAvailable());
        }
        return null;
    }

    @Override
    public Completable onClose() {
        return lac.onClose();
    }

    @Override
    public Completable closeAsync() {
        return lac.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return lac.closeAsyncGracefully();
    }
}
