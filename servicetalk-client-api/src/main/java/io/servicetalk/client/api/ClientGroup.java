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

import java.util.function.Function;

/**
 * A data-structure capable of holding arbitrary number of clients and bridging lifecycle events across all clients
 * managed by the group.
 *
 * @param <Key> the type of key used for client lookup
 * @param <Client> the type of client stored in the group
 */
public interface ClientGroup<Key, Client extends ListenableAsyncCloseable> extends ListenableAsyncCloseable {

    /**
     * Return the {@link Client} identified by the provided {@code key} or create a new one when none exists.
     *
     * @param key the key identifying the client to return or create, this key may contain attributes about the
     * requested client in order to create new instances
     * @return a client assigned to the provided {@code key}, either by looking up an existing or creating a new
     * instance if non-existent
     */
    Client get(Key key);

    /**
     * Creates a {@link ClientGroup} based on a {@code factory} of clients of type {@code Client}.
     *
     * @param factory {@link Function} will be called every time {@link #get(Object)} is called with a non-existent
     * {@link Key}.
     * @param <Key> the type of key used for client lookup and creation
     * @param <Client> the type of client stored in the group
     * @return a {@link ClientGroup} based on a @{code factory} of clients of type {@link Client}.
     */
    static <Key, Client extends ListenableAsyncCloseable> ClientGroup<Key, Client> from(Function<Key, Client> factory) {
        return new DefaultClientGroup<>(factory);
    }
}
