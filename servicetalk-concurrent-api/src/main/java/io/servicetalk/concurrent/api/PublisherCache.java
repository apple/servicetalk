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
 * A cache of {@link Publisher}s. Keys must correctly implement Object::hashCode and Object::equals.
 * The cache can be created with either multicast or replay configurations. Subscriptions
 * are tracked by the cache and Publishers are removed when there are no more subscriptions.
 *
 * @param <K> a key type suitable for use as a Map key, that correctly implements hashCode/equals.
 * @param <T> the type of the publisher that is cached.
 */
public final class PublisherCache<K, T> {
    private final MulticastStrategy<T> multicastStrategy;
    private final Map<K, Holder<T>> publisherCache;

    PublisherCache(final MulticastStrategy<T> multicastStrategy) {
        this.multicastStrategy = multicastStrategy;
        this.publisherCache = new HashMap<>();
    }

    /**
     * Create a new PublisherCache where the cached publishers must be configured with a multicast or replay operator
     * by the multicastSupplier function.
     *
     * @return a new PublisherCache that will not wrap cached values.
     */
    public static <K, T> PublisherCache<K, T> create() {
        return new PublisherCache<>(MulticastStrategy.identity());
    }

    /**
     * Create a new PublisherCache where the cached publishers will be configured for multicast for all
     * consumers of a specific key.
     *
     * @param <K> a key type suitable for use as a {@link Map} key.
     * @param <T> the type of the {@link Publisher} contained in the cache.
     * @return a new PublisherCache that will wrap cached values with multicast operator.
     */
    public static <K, T> PublisherCache<K, T> multicast() {
        return new PublisherCache<>(MulticastStrategy.wrapMulticast());
    }

    /**
     * Create a new PublisherCache where the cached publishers will be configured for multicast for all
     * consumers of a specific key.
     *
     * @param minSubscribers The upstream subscribe operation will not happen until after this many {@link Subscriber}
     * subscribe to the return value.
     * @param <K> a key type suitable for use as a {@link Map} key.
     * @param <T> the type of the {@link Publisher} contained in the cache.
     * @return a new PublisherCache that will wrap cached values with multicast operator.
     */
    public static <K, T> PublisherCache<K, T> multicast(final int minSubscribers) {
        return new PublisherCache<>(MulticastStrategy.wrapMulticast(minSubscribers));
    }

    /**
     * Create a new PublisherCache where the cached publishers will be configured for multicast for all
     * consumers of a specific key.
     *
     * @param minSubscribers The upstream subscribe operation will not happen until after this many {@link Subscriber}
     * subscribe to the return value.
     * @param queueLimit The number of elements which will be queued for each {@link Subscriber} in order to compensate
     * for unequal demand.
     * @param <K> a key type suitable for use as a {@link Map} key.
     * @param <T> the type of the {@link Publisher} contained in the cache.
     * @return aa new PublisherCache that will wrap cached values with multicast operator.
     */
    public static <K, T> PublisherCache<K, T> multicast(final int minSubscribers, final int queueLimit) {
        return new PublisherCache<>(MulticastStrategy.wrapMulticast(minSubscribers, queueLimit));
    }

    /**
     * Create a new PublisherCache where the cacehed publishers will be configured for replay given the privided
     * ReplayStrategy.
     *
     * @param replayStrategy a {@link ReplayStrategy} that determines the replay behavior and history retention logic.
     * @param <K> a key type suitable for use as a {@link Map} key.
     * @param <T> the type of the {@link Publisher} contained in the cache.
     * @return a new PublisherCache that will wrap cached values with replay operator.
     */
    public static <K, T> PublisherCache<K, T> replay(ReplayStrategy<T> replayStrategy) {
        return new PublisherCache<>(MulticastStrategy.wrapReplay(replayStrategy));
    }

    /**
     * Retrieve the value for the given key, if no value exists in the cache it will be synchronously created.
     *
     * @param key a key corresponding to the requested {@link Publisher}.
     * @return a new {@link Publisher} from the publisherSupplier if not contained in the cache, otherwise
     * the cached publisher.
     */
    public Publisher<T> get(final K key, final Function<K, Publisher<T>> publisherSupplier) {
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
    private interface MulticastStrategy<T> {
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
