/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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

import java.util.Collection;

import static java.util.Objects.requireNonNull;

/**
 * A {@link ServiceDiscoverer} that delegates all methods to another {@link ServiceDiscoverer}.
 *
 * @param <UnresolvedAddress> The type of address before resolution.
 * @param <ResolvedAddress> The type of address after resolution.
 * @param <E> Type of {@link ServiceDiscovererEvent}s published from {@link #discover(Object)}.
 */
public class DelegatingServiceDiscoverer<UnresolvedAddress, ResolvedAddress,
        E extends ServiceDiscovererEvent<ResolvedAddress>>
        implements ServiceDiscoverer<UnresolvedAddress, ResolvedAddress, E> {
    private final ServiceDiscoverer<UnresolvedAddress, ResolvedAddress, E> delegate;

    /**
     * Creates a new instance.
     *
     * @param delegate {@link ServiceDiscoverer} to which all methods are delegated.
     */
    public DelegatingServiceDiscoverer(final ServiceDiscoverer<UnresolvedAddress, ResolvedAddress, E> delegate) {
        this.delegate = requireNonNull(delegate);
    }

    /**
     * Returns the {@link ServiceDiscoverer} delegate.
     *
     * @return Delegate {@link ServiceDiscoverer}.
     */
    protected final ServiceDiscoverer<UnresolvedAddress, ResolvedAddress, E> delegate() {
        return delegate;
    }

    @Override
    public Publisher<Collection<E>> discover(final UnresolvedAddress address) {
        return delegate.discover(address);
    }

    @Override
    public Completable onClose() {
        return delegate.onClose();
    }

    @Override
    public Completable onClosing() {
        return delegate.onClosing();
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return delegate.closeAsyncGracefully();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{delegate=" + delegate() + '}';
    }
}
