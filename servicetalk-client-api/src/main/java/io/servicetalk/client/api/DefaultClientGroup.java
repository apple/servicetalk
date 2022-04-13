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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.AsyncCloseables.toAsyncCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * Default implementation for {@link ClientGroup} as returned from {@link ClientGroup#from(Function)}.
 *
 * @param <Key> the type of key used for client lookup
 * @param <Client> the type of client stored in the group
 */
final class DefaultClientGroup<Key, Client extends ListenableAsyncCloseable> implements ClientGroup<Key, Client> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultClientGroup.class);
    private static final String CLOSED_EXCEPTION_MSG = "This group has been closed";

    private static final ListenableAsyncCloseable PLACEHOLDER_CLIENT = new ListenableAsyncCloseable() {
        private static final String PLACEHOLDER_EXCEPTION_MSG =
                "This placeholder Client should never be returned from the ClientGroup)";
        @Override
        public Completable onClose() {
            return failed(new UnsupportedOperationException(PLACEHOLDER_EXCEPTION_MSG));
        }

        @Override
        public Completable closeAsync() {
            return failed(new UnsupportedOperationException(PLACEHOLDER_EXCEPTION_MSG));
        }
    };

    private volatile boolean closed;
    private final ConcurrentMap<Key, ListenableAsyncCloseable> clientMap = new ConcurrentHashMap<>();
    private final Function<Key, Client> clientFactory;
    private final ListenableAsyncCloseable asyncCloseable = toAsyncCloseable(graceful -> {
                closed = true;
                return completed().mergeDelayError(
                        clientMap.keySet().stream()
                                .map(clientMap::remove)
                                .filter(client -> client != null && client != PLACEHOLDER_CLIENT)
                                .map(closeable -> graceful ? closeable.closeAsyncGracefully() : closeable.closeAsync())
                                .collect(toList())
                );
            }
    );

    DefaultClientGroup(final Function<Key, Client> factory) {
        clientFactory = requireNonNull(factory);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Client get(final Key key) {
        // It is assumed that clientFactory will not acquire synchronization primitives which may be held by threads
        // in the spin/wait loop below to avoid livelock. This allows us to avoid acquiring locks/monitors
        // for the expected steady state where the key will already exist in the map.
        ListenableAsyncCloseable client;
        for (;;) {
            // It is expected that the majority of the time the key will already exist in the map, and so we try the
            // less expensive "get" operation first because "computeIfAbsent" may incur extra synchronization, while it
            // checks existence of the key in the concurrent hash map.
            client = clientMap.get(key);
            if (client != null && client != PLACEHOLDER_CLIENT) {
                return (Client) client;
            }
            if (client == PLACEHOLDER_CLIENT) {
                continue;
            }

            // Attempt to "reserve" this key with a PLACEHOLDER_CLIENT so we can later create a new client and insert
            // the "real" client instead of the PLACEHOLDER_CLIENT. Placeholder will make sure that we call factory only
            // once. This is necessary to avoid execution of the user code while holding a wide lock in
            // "computeIfAbsent". Basically, we are also holding a "per-key lock" here with the PLACEHOLDER_CLIENT as a
            // subsequent select with the same key does a spin-loop. The difference between "computeIfAbsent" and here
            // is that "computeIfAbsent" will lock the bin/bucket for the key but here we just lock the key.
            client = clientMap.putIfAbsent(key, PLACEHOLDER_CLIENT);
            if (client == null) {
                break; // Create new client using clientFactory below
            }
            if (client != PLACEHOLDER_CLIENT) {
                return (Client) client;
            }
        }

        // Initialize new client while other requests are spinning until PLACEHOLDER_CLIENT is swapped out.

        if (closed) {
            final boolean removed = clientMap.remove(key, PLACEHOLDER_CLIENT);
            assert removed : "Expected to remove PLACEHOLDER_CLIENT";
            throw new IllegalStateException(CLOSED_EXCEPTION_MSG);
        }

        try {
            client = requireNonNull(clientFactory.apply(key), "Newly created client can not be null");
        } catch (Throwable t) {
            clientMap.remove(key); // PLACEHOLDER_CLIENT
            throw new IllegalArgumentException("Failed to create new client", t);
        }

        clientMap.put(key, client); // Overwrite PLACEHOLDER_CLIENT
        toSource(client.onClose()).subscribe(new RemoveClientOnClose(key, client));
        LOGGER.debug("A new client {} was created", client);

        if (closed) {
            // group has been closed after a new client was created
            if (clientMap.remove(key) != null) { // not closed by closing thread
                client.closeAsync().subscribe();
                LOGGER.debug("Recently created client {} was removed and closed, group {} closed", client, this);
            }
            throw new IllegalStateException(CLOSED_EXCEPTION_MSG);
        }

        return (Client) client;
    }

    private final class RemoveClientOnClose implements Subscriber {
        private final Key key;
        private final ListenableAsyncCloseable newClient;

        RemoveClientOnClose(final Key key, final ListenableAsyncCloseable newClient) {
            this.key = key;
            this.newClient = newClient;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            // NOOP
        }

        @Override
        public void onComplete() {
            clientMap.remove(key, newClient);
        }

        @Override
        public void onError(final Throwable t) {
            clientMap.remove(key, newClient);
        }
    }

    @Override
    public Completable onClose() {
        return asyncCloseable.onClose();
    }

    @Override
    public Completable closeAsync() {
        return asyncCloseable.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return asyncCloseable.closeAsyncGracefully();
    }
}
