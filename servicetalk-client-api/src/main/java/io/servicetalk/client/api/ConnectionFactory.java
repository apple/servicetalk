/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.api.TransportObserver;

import javax.annotation.Nullable;

/**
 * A factory for creating new connections.
 *
 * @param <ResolvedAddress> The type of a resolved address that can be used for connecting.
 * @param <C> The type of connections created by this factory.
 */
public interface ConnectionFactory<ResolvedAddress, C extends ListenableAsyncCloseable>
        extends ListenableAsyncCloseable {

    /**
     * Creates and asynchronously returns a connection.
     *
     * @param address to connect.
     * @param observer {@link TransportObserver} that provides visibility into transport events associated with a new
     * connection.
     * @return {@link Single} that emits the created connection.
     */
    Single<C> newConnection(ResolvedAddress address, @Nullable TransportObserver observer);
}
