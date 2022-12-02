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
package io.servicetalk.http.utils;

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.transport.api.TransportObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;

final class CacheConnectionFactory<ResolvedAddress, C extends ListenableAsyncCloseable>
        extends DelegatingConnectionFactory<ResolvedAddress, C> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheConnectionFactory.class);
    private final Map<ResolvedAddress, Item<C>> map = new HashMap<>();
    private final ToIntFunction<ResolvedAddress> maxConcurrencyFunc;

    CacheConnectionFactory(final ConnectionFactory<ResolvedAddress, C> delegate,
                           final ToIntFunction<ResolvedAddress> maxConcurrencyFunc) {
        super(delegate);
        this.maxConcurrencyFunc = maxConcurrencyFunc;
    }

    @SuppressWarnings("deprecation")
    @Deprecated
    @Override
    public Single<C> newConnection(final ResolvedAddress resolvedAddress,
                                   @Nullable final TransportObserver observer) {
        return newConnection(resolvedAddress, null, observer);
    }

    @Override
    public Single<C> newConnection(final ResolvedAddress resolvedAddress, @Nullable final ContextMap context,
                                   @Nullable final TransportObserver observer) {
        return Single.defer(() -> {
            final int maxConcurrency = maxConcurrencyFunc.applyAsInt(resolvedAddress);
            if (maxConcurrency <= 1) {
                // If the concurrency is <=1 there is no use in caching the connection because we won't re-use it.
                return delegate().newConnection(resolvedAddress, context, observer);
            }

            Single<C> result;
            synchronized (map) {
                final Item<C> item1 = map.get(resolvedAddress);
                if (item1 == null || (result = item1.addSubscriber(maxConcurrency)) == null) {
                    final Item<C> item2 = new Item<>();
                    map.put(resolvedAddress, item2);
                    result = item2.single = delegate().newConnection(resolvedAddress, context, observer)
                            // Remove from map in a cancel above cache operator. Cache will only cancel upstream
                            // after all subscribers have cancelled.
                            .<C>liftSync(subscriber -> new Subscriber<C>() {
                                @Override
                                public void onSubscribe(final Cancellable cancellable) {
                                    subscriber.onSubscribe(() -> {
                                        try {
                                            assert Thread.holdsLock(map);
                                            map.remove(resolvedAddress, item2);
                                        } finally {
                                            cancellable.cancel();
                                        }
                                    });
                                }

                                @Override
                                public void onSuccess(@Nullable final C result1) {
                                    try {
                                        if (result1 == null) {
                                            lockRemoveFromMap();
                                        } else {
                                            result1.onClosing().whenFinally(this::lockRemoveFromMap).subscribe();
                                        }
                                    } catch (Throwable cause) {
                                        if (result1 != null) {
                                            LOGGER.debug("Unexpected error, closing connection={}", result1, cause);
                                            result1.closeAsync().subscribe();
                                        }
                                        subscriber.onError(cause);
                                        return;
                                    }
                                    subscriber.onSuccess(result1);
                                }

                                @Override
                                public void onError(final Throwable t) {
                                    lockRemoveFromMap();
                                    subscriber.onError(t);
                                }

                                private void lockRemoveFromMap() {
                                    synchronized (map) {
                                        map.remove(resolvedAddress, item2);
                                    }
                                }
                            })
                            .cache()
                            .liftSync(subscriber -> new Subscriber<C>() {
                                @Override
                                public void onSubscribe(final Cancellable cancellable) {
                                    subscriber.onSubscribe(() -> {
                                        // Acquire the lock before cache operator processes cancel, so if it results
                                        // in an upstream cancel we will be holding the lock and able to remove the
                                        // map entry safely.
                                        synchronized (map) {
                                            cancellable.cancel();
                                        }
                                    });
                                }

                                @Override
                                public void onSuccess(@Nullable final C result1) {
                                    try {
                                        subscriber.onSuccess(result1);
                                    } finally {
                                        lockRemoveFromMap();
                                    }
                                }

                                @Override
                                public void onError(final Throwable t) {
                                    try {
                                        subscriber.onError(t);
                                    } finally {
                                        lockRemoveFromMap();
                                    }
                                }

                                private void lockRemoveFromMap() {
                                    // The map will hold the latest re-usable connection attempt for this address and
                                    // will remove upon connection closure, or when the available concurrency for the
                                    // pending connection attempt is exceeded. When the single completes we don't need
                                    // to cache the connection anymore because the LoadBalancer above will cache the
                                    // connection. This also help keep memory down from the map.
                                    synchronized (map) {
                                        map.remove(resolvedAddress, item2);
                                    }
                                }
                            });
                }
            }

            return result.shareContextOnSubscribe();
        });
    }

    private static final class Item<C> {
        @Nullable
        Single<C> single;
        private int subscriberCount = 1;

        @Nullable
        Single<C> addSubscriber(int maxSubscriberCount) {
            if (subscriberCount >= maxSubscriberCount) {
                return null;
            }
            ++subscriberCount;
            return single;
        }
    }
}
