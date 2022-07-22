/*
 * Copyright © 2018, 2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.api.TransportObserver;

import javax.annotation.Nullable;

import static io.servicetalk.client.api.DeprecatedToNewConnectionFactoryFilter.CONNECTION_FACTORY_CONTEXT_MAP_KEY;
import static io.servicetalk.concurrent.api.Single.failed;

/**
 * A factory for creating new connections.
 *
 * @param <ResolvedAddress> The type of a resolved address that can be used for connecting.
 * @param <C> The type of connections created by this factory.
 *
 * @see ConnectionFactoryFilter
 */
public interface ConnectionFactory<ResolvedAddress, C extends ListenableAsyncCloseable>
        extends ListenableAsyncCloseable {

    /**
     * Creates and asynchronously returns a connection.
     *
     * @param address to connect.
     * @param observer {@link TransportObserver} for the newly created connection.
     * @return {@link Single} that emits the created connection.
     * @deprecated Use {@link #newConnection(Object, ContextMap, TransportObserver)}.
     */
    @Deprecated // FIXME: 0.43 - remove deprecated method
    default Single<C> newConnection(ResolvedAddress address, @Nullable TransportObserver observer) {
        return failed(new UnsupportedOperationException(
                "ConnectionFactory#newConnection(ResolvedAddress, TransportObserver) is not supported by " +
                        getClass()));
    }

    /**
     * Creates and asynchronously returns a connection.
     *
     * @param address to connect.
     * @param context {@link ContextMap context} of the caller (e.g. request context) or {@code null} if no context
     * provided. {@code null} context may also mean that a connection is created outside the normal request processing
     * (e.g. health-checking).
     * @param observer {@link TransportObserver} for the newly created connection or {@code null} if no observer
     * provided.
     * @return {@link Single} that emits the created connection.
     */
    default Single<C> newConnection(ResolvedAddress address, @Nullable ContextMap context,
                                    @Nullable TransportObserver observer) { // FIXME: 0.43 - remove default impl
        return Single.defer(() -> {
            if (context != null) {
                AsyncContext.put(CONNECTION_FACTORY_CONTEXT_MAP_KEY, context);
            }
            return newConnection(address, observer).shareContextOnSubscribe();
        });
    }
}
