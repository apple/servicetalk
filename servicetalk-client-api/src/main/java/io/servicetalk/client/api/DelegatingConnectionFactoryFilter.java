/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.transport.api.ExecutionStrategy;

import static java.util.Objects.requireNonNull;

/**
 * A {@link ConnectionFactoryFilter} that delegates all methods to another {@link ConnectionFactoryFilter}.
 *
 * @param <ResolvedAddress> The type of a resolved address that can be used for connecting.
 * @param <C> The type of connections created by this factory.
 */
public class DelegatingConnectionFactoryFilter<ResolvedAddress, C extends ListenableAsyncCloseable>
        implements ConnectionFactoryFilter<ResolvedAddress, C> {
    private final ConnectionFactoryFilter<ResolvedAddress, C> delegate;

    /**
     * Create a new instance.
     *
     * @param delegate {@link ConnectionFactory} to which all methods are delegated.
     */
    public DelegatingConnectionFactoryFilter(final ConnectionFactoryFilter<ResolvedAddress, C> delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public ConnectionFactory<ResolvedAddress, C> create(final ConnectionFactory<ResolvedAddress, C> original) {
        return delegate.create(original);
    }

    @Override
    public ExecutionStrategy requiredOffloads() {
        return delegate.requiredOffloads();
    }

    /**
     * Returns the {@link ConnectionFactoryFilter} delegate.
     *
     * @return Delegate {@link ConnectionFactoryFilter}.
     */
    protected final ConnectionFactoryFilter<ResolvedAddress, C> delegate() {
        return delegate;
    }
}
