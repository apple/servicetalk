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
package io.servicetalk.http.api;

import io.servicetalk.client.api.GroupKey;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Function;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Completable.completed;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

final class DefaultHttpClientGroup<UnresolvedAddress, I, O> extends HttpClientGroup<UnresolvedAddress, I, O> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHttpClientGroup.class);

    public static final String PLACEHOLDER_EXCEPTION_MSG = "Not supported by placeholder";
    public static final String CLOSED_EXCEPTION_MSG = "This group has been closed";

    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<DefaultHttpClientGroup> closedUpdater =
            AtomicIntegerFieldUpdater.newUpdater(DefaultHttpClientGroup.class, "closed");
    @SuppressWarnings("unused")
    private volatile int closed;

    // Placeholder should not leak outside of the scope of existing class
    private final HttpClient<I, O> placeholder = new HttpClient<I, O>() {
        @Override
        public Single<ReservedHttpConnection<I, O>> reserveConnection(final HttpRequest<I> request) {
            return Single.error(new UnsupportedOperationException(PLACEHOLDER_EXCEPTION_MSG));
        }

        @Override
        public Single<UpgradableHttpResponse<I, O>> upgradeConnection(final HttpRequest<I> request) {
            return Single.error(new UnsupportedOperationException(PLACEHOLDER_EXCEPTION_MSG));
        }

        @Override
        public Executor getExecutor() {
            throw new UnsupportedOperationException(PLACEHOLDER_EXCEPTION_MSG);
        }

        @Override
        public Single<HttpResponse<O>> request(final HttpRequest<I> request) {
            return Single.error(new UnsupportedOperationException(PLACEHOLDER_EXCEPTION_MSG));
        }

        @Override
        public Completable onClose() {
            return Completable.error(new UnsupportedOperationException(PLACEHOLDER_EXCEPTION_MSG));
        }

        @Override
        public Completable closeAsync() {
            return Completable.error(new UnsupportedOperationException(PLACEHOLDER_EXCEPTION_MSG));
        }
    };

    private final CompletableProcessor onClose = new CompletableProcessor();
    private final ConcurrentMap<GroupKey<UnresolvedAddress>, HttpClient<I, O>> clientMap = new ConcurrentHashMap<>();
    private final Function<GroupKey<UnresolvedAddress>, HttpClient<I, O>> clientFactory;

    DefaultHttpClientGroup(final Function<GroupKey<UnresolvedAddress>, HttpClient<I, O>> clientFactory) {
        this.clientFactory = requireNonNull(clientFactory);
    }

    @Override
    public Single<HttpResponse<O>> request(final GroupKey<UnresolvedAddress> key, final HttpRequest<I> request) {
        requireNonNull(key);
        requireNonNull(request);
        return new Single<HttpResponse<O>>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super HttpResponse<O>> subscriber) {
                final Single<HttpResponse<O>> response;
                try {
                    response = selectClient(key).request(request);
                } catch (final Throwable t) {
                    subscriber.onSubscribe(IGNORE_CANCEL);
                    subscriber.onError(t);
                    return;
                }
                response.subscribe(subscriber);
            }
        };
    }

    @Override
    public Single<? extends ReservedHttpConnection<I, O>> reserveConnection(final GroupKey<UnresolvedAddress> key,
                                                                  final HttpRequest<I> request) {
        requireNonNull(key);
        requireNonNull(request);
        return new Single<ReservedHttpConnection<I, O>>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super ReservedHttpConnection<I, O>> subscriber) {
                final Single<? extends ReservedHttpConnection<I, O>> reservedHttpConnection;
                try {
                    reservedHttpConnection = selectClient(key).reserveConnection(request);
                } catch (final Throwable t) {
                    subscriber.onSubscribe(IGNORE_CANCEL);
                    subscriber.onError(t);
                    return;
                }
                reservedHttpConnection.subscribe(subscriber);
            }
        };
    }

    private HttpClient<I, O> selectClient(final GroupKey<UnresolvedAddress> key) {
        // It is assumed that clientFactory will not acquire synchronization primitives which may be held by threads
        // in the spin/wait loop below to avoid livelock. This allows us to avoid acquiring locks/monitors
        // for the expected steady state where the key will already exist in the map.
        HttpClient<I, O> client;
        for (;;) {
            // It is expected that the majority of the time the key will already exist in the map, and so we try the
            // less expensive "get" operation first because "computeIfAbsent" may incur extra synchronization, while it
            // checks existence of the key in the concurrent hash map.
            client = clientMap.get(key);
            if (client != null && client != placeholder) {
                return client;
            }
            if (client == placeholder) {
                continue;
            }

            // Attempt to "reserve" this key with a placeholder so we can later create a new client and insert the
            // "real" client instead of the placeholder. Placeholder will make sure that we call factory only once.
            // This is necessary to avoid execution of the user code while holding a wide lock in "computeIfAbsent".
            // Basically, we are also holding a "per-key lock" here with the placeholder as a subsequent select with
            // the same key does a spin-loop. The difference between "computeIfAbsent" and here is that
            // "computeIfAbsent" will lock the bin/bucket for the key but here we just lock the key.
            client = clientMap.putIfAbsent(key, placeholder);
            if (client == null) {
                break;
            }
            if (client != placeholder) {
                return client;
            }
        }

        try {
            if (closed != 0) {
                throw new IllegalStateException(CLOSED_EXCEPTION_MSG);
            }

            client = clientFactory.apply(key);
            if (client == null) {
                throw new IllegalStateException("Newly created client can not be null");
            }
            // Overwrite the placeholder which was temporarily put in the map
            clientMap.put(key, client);
            LOGGER.debug("A new {} was created", client);

            if (closed != 0) {
                throw new IllegalStateException(CLOSED_EXCEPTION_MSG);
            }
        } catch (final Throwable t) {
            final HttpClient<I, O> closeCandidate = clientMap.remove(key);
            if (closeCandidate != null && closeCandidate == client) {
                // It will happen only if the group has been closed after a new client was created
                closeCandidate.closeAsync().subscribe();
                LOGGER.debug(
                        "Recently created {} was removed and closed, because current {} had been closed", client, this);
            }
            throw t;
        }
        return client;
    }

    @Override
    public Completable onClose() {
        return onClose;
    }

    @Override
    public Completable closeAsync() {
        return new Completable() {
            @Override
            protected void handleSubscribe(final Subscriber subscriber) {
                if (closedUpdater.compareAndSet(DefaultHttpClientGroup.this, 0, 1)) {
                    closeAllValues(clientMap).subscribe(onClose);
                    LOGGER.debug("Current {} was closed", this);
                }

                onClose.subscribe(subscriber);
            }
        };
    }

    private Completable closeAllValues(final Map<GroupKey<UnresolvedAddress>, HttpClient<I, O>> clientMap) {
        return completed().mergeDelayError(clientMap.keySet().stream()
                .map(clientMap::remove)
                .filter(client -> client != null && client != placeholder)
                .map(AsyncCloseable::closeAsync)
                .collect(toList()));
    }
}
