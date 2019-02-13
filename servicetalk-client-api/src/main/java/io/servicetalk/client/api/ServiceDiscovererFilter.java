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
package io.servicetalk.client.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;

import static java.util.Objects.requireNonNull;

/**
 * A {@link ServiceDiscoverer} that delegates all methods to a different {@link ServiceDiscoverer}.
 *
 * @param <UnresolvedAddress> The type of address before resolution.
 * @param <ResolvedAddress> The type of address after resolution.
 * @param <Event> Type of {@link ServiceDiscovererEvent}s published from {@link ServiceDiscoverer#discover(Object)}.
 */
public class ServiceDiscovererFilter<UnresolvedAddress, ResolvedAddress,
        Event extends ServiceDiscovererEvent<ResolvedAddress>>
        implements ServiceDiscoverer<UnresolvedAddress, ResolvedAddress, Event> {

    private final ServiceDiscoverer<UnresolvedAddress, ResolvedAddress, Event> delegate;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link ServiceDiscoverer} to delegate all calls to.
     */
    public ServiceDiscovererFilter(final ServiceDiscoverer<UnresolvedAddress, ResolvedAddress, Event> delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public Publisher<Event> discover(final UnresolvedAddress unresolvedAddress) {
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
