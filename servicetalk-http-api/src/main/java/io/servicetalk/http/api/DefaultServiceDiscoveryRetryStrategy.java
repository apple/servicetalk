/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.client.api.partition.PartitionedServiceDiscovererEvent;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoffAndJitter;
import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

/**
 * Default implementation for {@link ServiceDiscoveryRetryStrategy}.
 *
 * @param <ResolvedAddress> The type of address after resolution.
 * @param <E> Type of {@link ServiceDiscovererEvent}s published from {@link ServiceDiscoverer#discover(Object)}.
 */
public final class DefaultServiceDiscoveryRetryStrategy<ResolvedAddress,
        E extends ServiceDiscovererEvent<ResolvedAddress>>
        implements ServiceDiscoveryRetryStrategy<ResolvedAddress, E> {
    private final int retainTillReceivePercentage;
    private final UnaryOperator<E> flipAvailability;
    private final BiIntFunction<Throwable, ? extends Completable> retryStrategy;

    private DefaultServiceDiscoveryRetryStrategy(final int retainTillReceivePercentage,
                                                 final UnaryOperator<E> flipAvailability,
                                                 final BiIntFunction<Throwable, ? extends Completable> retryStrategy) {
        this.retainTillReceivePercentage = retainTillReceivePercentage;
        this.flipAvailability = requireNonNull(flipAvailability);
        this.retryStrategy = requireNonNull(retryStrategy);
    }

    @Override
    public Publisher<? extends E> apply(final Publisher<? extends E> sdEvents) {
        return defer(() -> {
            EventsCache<ResolvedAddress, E> eventsCache =
                    new EventsCache<>(retainTillReceivePercentage, flipAvailability);
            return sdEvents.flatMapConcatIterable(eventsCache::consume)
                    .beforeOnError(__ -> eventsCache.errorSeen())
                    .retryWhen(retryStrategy);
        });
    }

    /**
     * A builder to build instances of {@link DefaultServiceDiscoveryRetryStrategy}.
     *
     * @param <ResolvedAddress> The type of address after resolution.
     * @param <E> Type of {@link ServiceDiscovererEvent}s published from {@link ServiceDiscoverer#discover(Object)}.
     */
    public static final class Builder<ResolvedAddress, E extends ServiceDiscovererEvent<ResolvedAddress>> {
        // There is no reason for the choice of 75%, it is arbitrary and can be configured by users.
        private int retainTillReceivePercentage = 75;
        private BiIntFunction<Throwable, ? extends Completable> retryStrategy;
        private final UnaryOperator<E> flipAvailability;

        private Builder(final BiIntFunction<Throwable, ? extends Completable> retryStrategy,
                        final UnaryOperator<E> flipAvailability) {
            this.retryStrategy = retryStrategy;
            this.flipAvailability = requireNonNull(flipAvailability);
        }

        /**
         * A {@link Publisher} returned from {@link ServiceDiscoverer#discover(Object)} may fail transiently leaving the
         * consumer of these events with an option of either disposing the addresses that were provided before the
         * error or retain them till a retry succeeds. This option enables retention of addresses after an error is
         * observed till the total number of received addresses after an error is {@code retainTillReceivePercentage}
         * percent of the addresses that were available before the error was received.
         *
         * @param retainTillReceivePercentage A percentage of addresses that were received before an error that should
         * be received after a success, after which unreceived addresses are removed by sending appropriate
         * {@link ServiceDiscovererEvent}s. When set to {@code 0}, addresses are not retained.
         * @return {@code this}.
         */
        public Builder<ResolvedAddress, E> retainAddressesTillSuccess(final int retainTillReceivePercentage) {
            if (retainTillReceivePercentage < 0) {
                throw new IllegalArgumentException("retainTillReceivePercentage: " + retainTillReceivePercentage +
                        " (expected >= 0)");
            }
            this.retainTillReceivePercentage = retainTillReceivePercentage;
            return this;
        }

        /**
         * Specifies a {@link BiFunction} which is applied as-is using {@link Publisher#retryWhen(BiIntFunction)}
         * on the {@link Publisher} passed to {@link DefaultServiceDiscoveryRetryStrategy#apply(Publisher)}.
         *
         * @param retryStrategy A {@link BiFunction} which is applied as-is using
         * {@link Publisher#retryWhen(BiIntFunction)} on the {@link Publisher} passed to
         * {@link DefaultServiceDiscoveryRetryStrategy#apply(Publisher)}.
         * @return {@code this}.
         */
        public Builder<ResolvedAddress, E> retryStrategy(
                final BiIntFunction<Throwable, ? extends Completable> retryStrategy) {
            this.retryStrategy = requireNonNull(retryStrategy);
            return this;
        }

        /**
         * Creates a new {@link ServiceDiscoveryRetryStrategy}.
         *
         * @return A new {@link ServiceDiscoveryRetryStrategy}.
         */
        public ServiceDiscoveryRetryStrategy<ResolvedAddress, E> build() {
            return new DefaultServiceDiscoveryRetryStrategy<>(retainTillReceivePercentage, flipAvailability,
                    retryStrategy);
        }

        /**
         * Creates a new builder that uses default retries.
         *
         * @param executor {@link Executor} to use for retry backoffs.
         * @param initialDelay {@link Duration} to use as initial delay for backoffs.
         * @param <ResolvedAddress> The type of address after resolution.
         * @return A new {@link Builder}.
         */
        public static <ResolvedAddress> Builder<ResolvedAddress, ServiceDiscovererEvent<ResolvedAddress>>
        withDefaults(final Executor executor, final Duration initialDelay) {
            return new Builder<>(new IndefiniteRetryStrategy(executor, initialDelay),
                    evt -> new DefaultServiceDiscovererEvent<>(evt.address(), !evt.isAvailable()));
        }

        /**
         * Creates a new builder that uses default retries.
         *
         * @param executor {@link Executor} to use for retry backoffs.
         * @param initialDelay {@link Duration} to use as initial delay for backoffs.
         * @param <ResolvedAddress> The type of address after resolution.
         * @return A new {@link Builder}.
         */
        public static <ResolvedAddress> Builder<ResolvedAddress, PartitionedServiceDiscovererEvent<ResolvedAddress>>
        withDefaultsForPartitions(final Executor executor, final Duration initialDelay) {
            return new Builder<>(new IndefiniteRetryStrategy(executor, initialDelay),
                    evt -> new PartitionedServiceDiscovererEvent<ResolvedAddress>() {
                        @Override
                        public PartitionAttributes partitionAddress() {
                            return evt.partitionAddress();
                        }

                        @Override
                        public ResolvedAddress address() {
                            return evt.address();
                        }

                        @Override
                        public boolean isAvailable() {
                            return !evt.isAvailable();
                        }
                    });
        }

        /**
         * Creates a new builder that uses default retries.
         *
         * @param executor {@link Executor} to use for retry backoffs.
         * @param initialDelay {@link Duration} to use as initial delay for backoffs.
         * @param flipAvailability {@link UnaryOperator} that returns a new {@link ServiceDiscovererEvent} that is the
         * same as the passed {@link ServiceDiscovererEvent} but with {@link ServiceDiscovererEvent#isAvailable()} value
         * flipped.
         * @param <ResolvedAddress> The type of address after resolution.
         * @param <E> Type of {@link ServiceDiscovererEvent}s published from {@link ServiceDiscoverer#discover(Object)}.
         * @return A new {@link Builder}.
         */
        public static <ResolvedAddress, E extends ServiceDiscovererEvent<ResolvedAddress>> Builder<ResolvedAddress, E>
        withDefaults(final Executor executor, final Duration initialDelay, final UnaryOperator<E> flipAvailability) {
            return new Builder<>(new IndefiniteRetryStrategy(executor, initialDelay), flipAvailability);
        }
    }

    private static final class EventsCache<R, E extends ServiceDiscovererEvent<R>> {
        @SuppressWarnings("rawtypes")
        private static final Map NONE_RETAINED = emptyMap();

        private Map<R, E> retainedAddresses = noneRetained();
        private int targetSize;
        private final Map<R, E> activeAddresses = new HashMap<>();
        private final int retainTillReceivePercentage;
        private final UnaryOperator<E> flipAvailability;

        EventsCache(final int retainTillReceivePercentage, final UnaryOperator<E> flipAvailability) {
            this.retainTillReceivePercentage = retainTillReceivePercentage;
            this.flipAvailability = flipAvailability;
        }

        void errorSeen() {
            if (retainedAddresses == NONE_RETAINED) {
                retainedAddresses = new HashMap<>(activeAddresses);
            } else {
                retainedAddresses.putAll(activeAddresses);
            }
            targetSize = (int) (ceil(retainTillReceivePercentage / 100d) * activeAddresses.size());
            activeAddresses.clear();
        }

        Iterable<E> consume(final E event) {
            final R address = event.address();
            if (retainedAddresses == NONE_RETAINED) {
                if (event.isAvailable()) {
                    activeAddresses.put(address, event);
                } else {
                    activeAddresses.remove(address);
                }
                return singletonList(event);
            }

            // we have seen an error and have not fully drained the retained address list
            if (event.isAvailable()) {
                // new address after a retry
                activeAddresses.put(address, event);
                final boolean removed = retainedAddresses.remove(address) != null;
                if (activeAddresses.size() >= targetSize) {
                    final List<E> allEvents = new ArrayList<>(retainedAddresses.size() + (removed ? 0 : 1));
                    if (!removed) {
                        allEvents.add(event);
                    }
                    for (E removalEvent : retainedAddresses.values()) {
                        allEvents.add(flipAvailability.apply(removalEvent));
                    }
                    retainedAddresses = noneRetained();
                    targetSize = 0;
                    return allEvents;
                }
                // If we already had it in retained addresses, then the event can be ignored as the consumer of events
                // already knows about the address
                return removed ? emptyList() : singletonList(event);
            }
            // removal must be for a previously added address which would have already removed from the retained
            // address list, so we do not need to touch the retainedAddresses
            activeAddresses.remove(address);
            return singletonList(event);
        }

        @SuppressWarnings("unchecked")
        private static <R, E extends ServiceDiscovererEvent<R>> Map<R, E> noneRetained() {
            return NONE_RETAINED;
        }
    }

    private static final class IndefiniteRetryStrategy implements BiIntFunction<Throwable, Completable> {
        private final BiIntFunction<Throwable, Completable> delegate;
        private final int maxRetries;

        IndefiniteRetryStrategy(final Executor executor, final Duration initialDelay) {
            this(executor, initialDelay, 10);
        }

        IndefiniteRetryStrategy(final Executor executor, final Duration initialDelay, final int maxRetries) {
            delegate = retryWithExponentialBackoffAndJitter(maxRetries, __ -> true, initialDelay, executor);
            this.maxRetries = maxRetries;
        }

        @Override
        public Completable apply(final int count, final Throwable cause) {
            // As we are retrying indefinitely (unless closed), cap the backoff on MAX_RETRIES retries to avoid
            // impractical backoffs
            return delegate.apply(max(1, count % (maxRetries)), cause);
        }
    }
}
