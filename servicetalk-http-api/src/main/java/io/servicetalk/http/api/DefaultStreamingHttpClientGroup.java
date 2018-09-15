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
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient.UpgradableStreamingHttpResponse;
import io.servicetalk.transport.api.ExecutionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.AsyncCloseables.toAsyncCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

final class DefaultStreamingHttpClientGroup<UnresolvedAddress> extends StreamingHttpClientGroup<UnresolvedAddress> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultStreamingHttpClientGroup.class);

    public static final String PLACEHOLDER_EXCEPTION_MSG = "Not supported by PLACEHOLDER_CLIENT";
    public static final String CLOSED_EXCEPTION_MSG = "This group has been closed";

    // Placeholder should not leak outside of the scope of existing class
    private static final StreamingHttpClient PLACEHOLDER_CLIENT = new StreamingHttpClient() {
        @Override
        public Single<ReservedStreamingHttpConnection> reserveConnection(final StreamingHttpRequest request) {
            return Single.error(new UnsupportedOperationException(PLACEHOLDER_EXCEPTION_MSG));
        }

        @Override
        public Single<UpgradableStreamingHttpResponse> upgradeConnection(
                final StreamingHttpRequest request) {
            return Single.error(new UnsupportedOperationException(PLACEHOLDER_EXCEPTION_MSG));
        }

        @Override
        public ExecutionContext getExecutionContext() {
            throw new UnsupportedOperationException(PLACEHOLDER_EXCEPTION_MSG);
        }

        @Override
        public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
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

    private volatile boolean closed;
    private final ConcurrentMap<GroupKey<UnresolvedAddress>, StreamingHttpClient> clientMap = new ConcurrentHashMap<>();
    private final ExecutionContext executionContext;
    private final BiFunction<GroupKey<UnresolvedAddress>, HttpRequestMetaData, StreamingHttpClient> clientFactory;
    private final ListenableAsyncCloseable asyncCloseable = toAsyncCloseable(() -> {
                closed = true;
                return completed().mergeDelayError(clientMap.keySet().stream()
                        .map(clientMap::remove)
                        .filter(client -> client != null && client != PLACEHOLDER_CLIENT)
                        .map(AsyncCloseable::closeAsync)
                        .collect(toList()));
            }
    );

    DefaultStreamingHttpClientGroup(
            final StreamingHttpRequestFactory requestFactory,
            final StreamingHttpResponseFactory responseFactory,
            final ExecutionContext executionContext,
            final BiFunction<GroupKey<UnresolvedAddress>, HttpRequestMetaData, StreamingHttpClient> clientFactory) {
        super(requestFactory, responseFactory);
        this.clientFactory = requireNonNull(clientFactory);
        this.executionContext = requireNonNull(executionContext);
    }

    @Override
    public Single<? extends StreamingHttpResponse> request(final GroupKey<UnresolvedAddress> key,
                                                 final StreamingHttpRequest request) {
        requireNonNull(key);
        requireNonNull(request);
        return new Single<StreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                final Single<? extends StreamingHttpResponse> response;
                try {
                    response = selectClient(key, request).request(request);
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
    public Single<? extends ReservedStreamingHttpConnection> reserveConnection(final GroupKey<UnresolvedAddress> key,
                                                                               final StreamingHttpRequest request) {
        requireNonNull(key);
        requireNonNull(request);
        return new Single<ReservedStreamingHttpConnection>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super ReservedStreamingHttpConnection> subscriber) {
                final Single<? extends ReservedStreamingHttpConnection> reservedHttpConnection;
                try {
                    reservedHttpConnection = selectClient(key, request).reserveConnection(request);
                } catch (final Throwable t) {
                    subscriber.onSubscribe(IGNORE_CANCEL);
                    subscriber.onError(t);
                    return;
                }
                reservedHttpConnection.subscribe(subscriber);
            }
        };
    }

    @Override
    public Single<? extends StreamingHttpClient.UpgradableStreamingHttpResponse> upgradeConnection(
            final GroupKey<UnresolvedAddress> key, final StreamingHttpRequest request) {
        requireNonNull(key);
        requireNonNull(request);
        return new Single<StreamingHttpClient.UpgradableStreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(
                    final Subscriber<? super UpgradableStreamingHttpResponse> subscriber) {
                final Single<? extends UpgradableStreamingHttpResponse> upgradedHttpConnection;
                try {
                    upgradedHttpConnection = selectClient(key, request).upgradeConnection(request);
                } catch (final Throwable t) {
                    subscriber.onSubscribe(IGNORE_CANCEL);
                    subscriber.onError(t);
                    return;
                }
                upgradedHttpConnection.subscribe(subscriber);
            }
        };
    }

    private StreamingHttpClient selectClient(final GroupKey<UnresolvedAddress> key, final HttpRequestMetaData requestMetaData) {
        // It is assumed that clientFactory will not acquire synchronization primitives which may be held by threads
        // in the spin/wait loop below to avoid livelock. This allows us to avoid acquiring locks/monitors
        // for the expected steady state where the key will already exist in the map.
        StreamingHttpClient client;
        for (;;) {
            // It is expected that the majority of the time the key will already exist in the map, and so we try the
            // less expensive "get" operation first because "computeIfAbsent" may incur extra synchronization, while it
            // checks existence of the key in the concurrent hash map.
            client = clientMap.get(key);
            if (client != null && client != PLACEHOLDER_CLIENT) {
                return client;
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
                break;
            }
            if (client != PLACEHOLDER_CLIENT) {
                return client;
            }
        }

        try {
            throwIfClosed();

            client = clientFactory.apply(key, requestMetaData);
            if (client == null) {
                throw new IllegalStateException("Newly created client can not be null");
            }
            // Overwrite the PLACEHOLDER_CLIENT which was temporarily put in the map
            clientMap.put(key, client);
            LOGGER.debug("A new {} was created", client);

            throwIfClosed();
        } catch (final Throwable t) {
            final StreamingHttpClient closeCandidate = clientMap.remove(key);
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

    private void throwIfClosed() {
        if (closed) {
            throw new IllegalStateException(CLOSED_EXCEPTION_MSG);
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
