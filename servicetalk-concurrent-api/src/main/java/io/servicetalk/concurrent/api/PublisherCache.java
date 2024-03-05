/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * A cache of publishers, Keys must correctly implement Object::hashCode and Object::equals.
 * Publishers can be created as either multicast or with a replay configuration. Subscriptions
 * are tracked by the cache and Publishers are removed when there are no more subscriptions.
 *
 * @param <K> a key type suitable for use as a Map key, that correctly implements hashCode/equals.
 * @param <T> the type of the publisher that is cached.
 */
public final class PublisherCache<K, T> {
    private final MulticastStrategy<T> multicastStrategy;
    private final Function<K, Publisher<T>> publisherSupplier;
    private final Map<K, Holder<T>> publisherCache;

    /**
     * Create a new PublisherCache using a factory function and a multicast strategy to use after {@link Publisher}
     * creation. New publishers must be wrapped in a multicast or replay strategy in order to be shared.
     * VisibleForTesting
     *
     * @param publisherSupplier a function that takes the key and returns a new {@link Publisher} for that key.
     * @param multicastStrategy a strategy used for wrapping new cache values.
     */
    PublisherCache(
            final Function<K, Publisher<T>> publisherSupplier,
            final MulticastStrategy<T> multicastStrategy) {
        this.publisherSupplier = publisherSupplier;
        this.multicastStrategy = multicastStrategy;
        this.publisherCache = new HashMap<>();
    }

    /**
     * Create a new PublisherCache where the cached publishers will be configured for multicast for all
     * consumers of a specific key. The publisherSupplier will be invoked when the cache does not contain a
     * publisher for the requested key.
     *
     * @param publisherSupplier a function that takes the key and returns a new {@link Publisher} for that key.
     * @param <K> a key type suitable for use as a {@link Map} key.
     * @param <T> the type of the {@link Publisher} contained in the cache.
     * @return a new PublisherCache that will use the publisherSupplier to create new entries upon request.
     */
    public static <K, T> PublisherCache<K, T> multicast(final Function<K, Publisher<T>> publisherSupplier) {
        return new PublisherCache<>(publisherSupplier, MulticastStrategy.wrapMulticast());
    }

    /**
     * Create a new PublisherCache where the cached publishers will be configured for multicast for all
     * consumers of a specific key. The publisherSupplier will be invoked when the cache does not contain a
     * publisher for the requested key.
     *
     * @param publisherSupplier a function that takes the key and returns a new {@link Publisher} for that key.
     * @param minSubscribers The upstream subscribe operation will not happen until after this many {@link Subscriber}
     * subscribe to the return value.
     * @param <K> a key type suitable for use as a {@link Map} key.
     * @param <T> the type of the {@link Publisher} contained in the cache.
     * @return a new PublisherCache that will use the publisherSupplier to create new entries upon request.
     */
    public static <K, T> PublisherCache<K, T> multicast(
            final Function<K, Publisher<T>> publisherSupplier, final int minSubscribers) {
        return new PublisherCache<>(publisherSupplier, MulticastStrategy.wrapMulticast(minSubscribers));
    }

    /**
     * Create a new PublisherCache where the cached publishers will be configured for multicast for all
     * consumers of a specific key. The publisherSupplier will be invoked when the cache does not contain a
     * publisher for the requested key.
     *
     * @param publisherSupplier a function that takes the key and returns a new {@link Publisher} for that key.
     * @param minSubscribers The upstream subscribe operation will not happen until after this many {@link Subscriber}
     * subscribe to the return value.
     * @param queueLimit The number of elements which will be queued for each {@link Subscriber} in order to compensate
     * for unequal demand.
     * @param <K> a key type suitable for use as a {@link Map} key.
     * @param <T> the type of the {@link Publisher} contained in the cache.
     * @return a new PublisherCache that will use the publisherSupplier to create new entries upon request.
     */
    public static <K, T> PublisherCache<K, T> multicast(
            final Function<K, Publisher<T>> publisherSupplier, final int minSubscribers, final int queueLimit) {
        return new PublisherCache<>(publisherSupplier, MulticastStrategy.wrapMulticast(minSubscribers, queueLimit));
    }

    /**
     * Create a new PublisherCache where the cacehed publishers will be configured for replay given the privided
     * ReplayStrategy. The publisherSupplier will be invoked when the cache does not contain a publisher for
     * the requested key.
     *
     * @param publisherSupplier a function that takes the key and returns a new {@link Publisher} for that key.
     * @param replayStrategy a {@link ReplayStrategy} that determines the replay behavior and history retention logic.
     * @param <K> a key type suitable for use as a {@link Map} key.
     * @param <T> the type of the {@link Publisher} contained in the cache.
     * @return a new PublisherCache that will use the publisherSupplier to create new entries upon request.
     */
    public static <K, T> PublisherCache<K, T> replay(
            Function<K, Publisher<T>> publisherSupplier,
            ReplayStrategy<T> replayStrategy) {
        return new PublisherCache<>(publisherSupplier, MulticastStrategy.wrapReplay(replayStrategy));
    }

    /**
     * Retrieve a the value for the given key, if no value exists in the cache it will be synchronously created.
     *
     * @param key a key corresponding to the requested {@link Publisher}.
     * @return a new {@link Publisher} from the publisherSupplier if not contained in the cache, otherwise
     * the cached publisher.
     */
    public Publisher<T> get(K key) {
        return Publisher.defer(() -> {
            synchronized (publisherCache) {
                if (publisherCache.containsKey(key)) {
                    return publisherCache.get(key).publisher;
                }

                final Holder<T> item2 = new Holder<>();
                publisherCache.put(key, item2);

                final Publisher<T> newPublisher = publisherSupplier.apply(key)
                        .liftSync(subscriber -> new Subscriber<T>() {
                            @Override
                            public void onSubscribe(Subscription subscription) {
                                subscriber.onSubscribe(new Subscription() {
                                    @Override
                                    public void request(long n) {
                                        subscription.request(n);
                                    }

                                    @Override
                                    public void cancel() {
                                        try {
                                            assert Thread.holdsLock(publisherCache);
                                            publisherCache.remove(key, item2);
                                        } finally {
                                            subscription.cancel();
                                        }
                                    }
                                });
                            }

                            @Override
                            public void onNext(@Nullable T next) {
                                subscriber.onNext(next);
                            }

                            @Override
                            public void onError(Throwable t) {
                                lockRemoveFromMap();
                                subscriber.onError(t);
                            }

                            @Override
                            public void onComplete() {
                                lockRemoveFromMap();
                                subscriber.onComplete();
                            }

                            private void lockRemoveFromMap() {
                                synchronized (publisherCache) {
                                    publisherCache.remove(key, item2);
                                }
                            }
                        });

                item2.publisher = multicastStrategy.apply(newPublisher)
                        .liftSync(subscriber -> new Subscriber<T>() {
                            @Override
                            public void onSubscribe(Subscription subscription) {
                                subscriber.onSubscribe(new Subscription() {
                                    @Override
                                    public void request(long n) {
                                        subscription.request(n);
                                    }

                                    @Override
                                    public void cancel() {
                                        synchronized (publisherCache) {
                                            subscription.cancel();
                                        }
                                    }
                                });
                            }

                            @Override
                            public void onNext(@Nullable T next) {
                                subscriber.onNext(next);
                            }

                            @Override
                            public void onError(Throwable t) {
                                try {
                                    subscriber.onError(t);
                                } finally {
                                    lockRemoveFromMap();
                                }
                            }

                            @Override
                            public void onComplete() {
                                try {
                                    subscriber.onComplete();
                                } finally {
                                    lockRemoveFromMap();
                                }
                            }

                            private void lockRemoveFromMap() {
                                synchronized (publisherCache) {
                                    publisherCache.remove(key, item2);
                                }
                            }
                        });
                return item2.publisher;
            }
        });
    }

    private static final class Holder<T> {
        @Nullable
        Publisher<T> publisher;
    }

    /**
     * A series of strategies used to make cached {@link Publisher}s "shareable".
     *
     * @param <T> the type of the {@link Publisher}.
     */
    @FunctionalInterface
    interface MulticastStrategy<T> {
        Publisher<T> apply(Publisher<T> cached);

        static <T> MulticastStrategy<T> identity() {
            return cached -> cached;
        }

        static <T> MulticastStrategy<T> wrapMulticast() {
            return cached -> cached.multicast(1, true);
        }

        static <T> MulticastStrategy<T> wrapMulticast(int minSubscribers) {
            return cached -> cached.multicast(minSubscribers, true);
        }

        static <T> MulticastStrategy<T> wrapMulticast(int minSubscribers, int queueLimit) {
            return cached -> cached.multicast(minSubscribers, queueLimit);
        }

        static <T> MulticastStrategy<T> wrapReplay(final ReplayStrategy<T> strategy) {
            return cached -> cached.replay(strategy);
        }
    }
}
