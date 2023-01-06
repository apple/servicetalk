/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.TransportObserver;

import javax.annotation.Nullable;

import static io.servicetalk.context.api.ContextMap.Key.newKey;

@Deprecated // FIXME: 0.43 - remove as no longer required
final class DeprecatedToNewConnectionFactoryFilter<ResolvedAddress, C extends ListenableAsyncCloseable>
        implements ConnectionFactoryFilter<ResolvedAddress, C> {

    /**
     * Key that propagates {@link ContextMap} argument between new and deprecated
     * {@code ConnectionFactory#newConnection(...)} methods.
     */
    public static final ContextMap.Key<ContextMap> CONNECTION_FACTORY_CONTEXT_MAP_KEY =
            newKey("CONNECTION_FACTORY_CONTEXT_MAP_KEY", ContextMap.class);

    @Override
    public ConnectionFactory<ResolvedAddress, C> create(final ConnectionFactory<ResolvedAddress, C> original) {
        return new DelegatingConnectionFactory<ResolvedAddress, C>(original) {
            @Override
            public Single<C> newConnection(final ResolvedAddress address, @Nullable final TransportObserver observer) {
                return Single.defer(() -> {
                    final ContextMap context = AsyncContext.get(CONNECTION_FACTORY_CONTEXT_MAP_KEY);
                    return delegate().newConnection(address, context, observer).shareContextOnSubscribe();
                });
            }

            @Override
            public Single<C> newConnection(final ResolvedAddress resolvedAddress,
                                           @Nullable final ContextMap context,
                                           @Nullable final TransportObserver observer) {
                return delegate().newConnection(resolvedAddress, context, observer);
            }
        };
    }

    @Override
    public ExecutionStrategy requiredOffloads() {
        return ExecutionStrategy.offloadNone();
    }
}
