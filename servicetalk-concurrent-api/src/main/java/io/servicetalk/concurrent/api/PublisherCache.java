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
     * @param publisherSupplier a function that takes the key and returns a new publisher corresponding to that key.
     * @return a new publisher from the publisherSupplier if not contained in the cache, otherwise the cached publisher.
     * @param <K> a key type suitable for use as a Map key.
     * @param <T> the type of the Publisher contained in the cache.
     */
    public static <K, T> PublisherCache<K, T> multicast(Function<K, Publisher<T>> publisherSupplier) {
        return new PublisherCache<>(publisherSupplier, MulticastStrategy.wrapMulticast());
    }

    /**
     * Create a new PublisherCache where the cacehed publishers will be configured for replay given the privided
     * ReplayStrategy. The publisherSupplier will be invoked when the cache does not contain a publisher for
     * the requested key.
     *
     * @param publisherSupplier a function that takes the key and returns a new publisher corresponding to that key.
     * @param replayStrategy a replay strategy to be used by a newly cached publisher.
     * @return a new publisher from the publisherSupplier if not contained in the cache, otherwise the cached publisher.
     * @param <K> a key type suitable for use as a Map key.
     * @param <T> the type of the Publisher contained in the cache.
     */
    public static <K, T> PublisherCache<K, T> replay(
            Function<K, Publisher<T>> publisherSupplier,
            ReplayStrategy<T> replayStrategy) {
        return new PublisherCache<>(publisherSupplier, MulticastStrategy.wrapReplay(replayStrategy));
    }

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

    @FunctionalInterface
    private interface MulticastStrategy<T> {
        Publisher<T> apply(Publisher<T> cached);

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
