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
package io.servicetalk.dns.discovery.netty;

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;

/**
 * A {@link ServiceDiscoverer} that delegates all methods to a different {@link ServiceDiscoverer}.
 *
 * @param <UnresolvedAddress> The type of address before resolution.
 * @param <ResolvedAddress> The type of address after resolution.
 * @param <E> Type of {@link ServiceDiscovererEvent}s published from {@link ServiceDiscoverer#discover(Object)}.
 */
public class ServiceDiscovererFilter<UnresolvedAddress, ResolvedAddress,
        E extends ServiceDiscovererEvent<ResolvedAddress>> implements ServiceDiscoverer<UnresolvedAddress, ResolvedAddress,
        E> {

    private final ServiceDiscoverer<UnresolvedAddress, ResolvedAddress, E> delegate;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link ServiceDiscoverer} to delegate all calls to.
     */
    public ServiceDiscovererFilter(final ServiceDiscoverer<UnresolvedAddress, ResolvedAddress, E> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Publisher<E> discover(final UnresolvedAddress unresolvedAddress) {
        return delegate.discover(unresolvedAddress);
    }

    @Override
    public Completable onClose() {
        return delegate.onClose();
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return delegate.closeAsyncGracefully();
    }
}
