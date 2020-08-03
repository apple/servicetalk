/*
 * Copyright © 2019-2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.TransportObserver;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A {@link ConnectionFactory} that delegates all methods to another {@link ConnectionFactory}.
 *
 * @param <ResolvedAddress> The type of a resolved address that can be used for connecting.
 * @param <C> The type of connections created by this factory.
 */
public class DelegatingConnectionFactory<ResolvedAddress, C extends ListenableAsyncCloseable>
        implements ConnectionFactory<ResolvedAddress, C> {
    private final ConnectionFactory<ResolvedAddress, C> delegate;

    /**
     * Create a new instance.
     *
     * @param delegate {@link ConnectionFactory} to which all methods are delegated.
     */
    public DelegatingConnectionFactory(final ConnectionFactory<ResolvedAddress, C> delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public Single<C> newConnection(final ResolvedAddress resolvedAddress, @Nullable final TransportObserver observer) {
        return delegate.newConnection(resolvedAddress, observer);
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

    /**
     * Returns the {@link ConnectionFactory} delegate.
     *
     * @return Delegate {@link ConnectionFactory}.
     */
    protected ConnectionFactory<ResolvedAddress, C> delegate() {
        return delegate;
    }
}
