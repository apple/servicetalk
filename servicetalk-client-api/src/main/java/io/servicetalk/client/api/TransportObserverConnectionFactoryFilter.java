/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.TransportObserver;

import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.transport.api.TransportObservers.combine;
import static java.util.Objects.requireNonNull;

/**
 * A {@link ConnectionFactoryFilter} that configures a {@link TransportObserver} for new connections.
 *
 * @param <ResolvedAddress> The type of a resolved address that can be used for connecting.
 * @param <C> The type of connections created by the {@link ConnectionFactory} decorated by this filter.
 */
public final class TransportObserverConnectionFactoryFilter<ResolvedAddress, C extends ListenableAsyncCloseable>
        implements ConnectionFactoryFilter<ResolvedAddress, C> {

    private final Function<ResolvedAddress, TransportObserver> observerFactory;

    /**
     * Creates a new instance.
     *
     * @param observer {@link TransportObserver} to use for new connections
     */
    public TransportObserverConnectionFactoryFilter(final TransportObserver observer) {
        requireNonNull(observer);
        observerFactory = __ -> observer;
    }

    /**
     * Creates a new instance.
     *
     * @param observerFactory a factory to create a {@link TransportObserver} for new connections per
     * {@link ResolvedAddress}. May return {@code null} to avoid configuring {@link TransportObserver} for some
     * addresses.
     */
    public TransportObserverConnectionFactoryFilter(
            final Function<ResolvedAddress, TransportObserver> observerFactory) {
        this.observerFactory = requireNonNull(observerFactory);
    }

    @Override
    public ConnectionFactory<ResolvedAddress, C> create(final ConnectionFactory<ResolvedAddress, C> original) {
        return new DelegatingConnectionFactory<ResolvedAddress, C>(original) {
            @Override
            public Single<C> newConnection(final ResolvedAddress resolvedAddress,
                                           @Nullable final TransportObserver originalObserver) {
                final TransportObserver newObserver;
                try {
                    newObserver = observerFactory.apply(resolvedAddress);
                } catch (Throwable t) {
                    return failed(t);
                }
                return delegate().newConnection(resolvedAddress, originalObserver == null ? newObserver :
                       newObserver == null ? originalObserver : combine(originalObserver, newObserver));
            }
        };
    }

    @Override
    public ExecutionStrategy requiredOffloads() {
        return ExecutionStrategy.offloadNone();
    }
}
