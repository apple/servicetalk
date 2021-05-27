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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoffDeltaJitter;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * Default implementation for {@link ServiceDiscoveryRetryStrategy}.
 *
 * @param <ResolvedAddress> The type of address after resolution.
 * @param <E> Type of {@link ServiceDiscovererEvent}s published from {@link ServiceDiscoverer#discover(Object)}.
 */
public final class DefaultServiceDiscoveryRetryStrategy<ResolvedAddress,
        E extends ServiceDiscovererEvent<ResolvedAddress>>
        implements ServiceDiscoveryRetryStrategy<ResolvedAddress, E> {
    private final UnaryOperator<E> flipAvailability;
    private final boolean retainAddressesTillSuccess;
    private final BiIntFunction<Throwable, ? extends Completable> retryStrategy;

    private DefaultServiceDiscoveryRetryStrategy(final UnaryOperator<E> flipAvailability,
                                                 final boolean retainAddressesTillSuccess,
                                                 final BiIntFunction<Throwable, ? extends Completable> retryStrategy) {
        this.flipAvailability = requireNonNull(flipAvailability);
        this.retainAddressesTillSuccess = retainAddressesTillSuccess;
        this.retryStrategy = requireNonNull(retryStrategy);
    }

    @Override
    public Publisher<Collection<E>> apply(final Publisher<Collection<E>> sdEvents) {
        return defer(() -> {
            EventsCache<ResolvedAddress, E> eventsCache = new EventsCache<>(flipAvailability);
            if (retainAddressesTillSuccess) {
                return sdEvents.map(eventsCache::consumeAndFilter)
                        .beforeOnError(__ -> eventsCache.errorSeen())
                        .retryWhen(retryStrategy);
            }

            return sdEvents.map(eventsCache::consumeAndFilter)
                    .onErrorResume(cause -> {
                        final Collection<E> events = eventsCache.errorSeen();
                        return events == null ? failed(cause) : Publisher.from(events.stream()
                                .map(flipAvailability).collect(toList()))
                                .concat(failed(cause));
                    })
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
        private BiIntFunction<Throwable, ? extends Completable> retryStrategy;
        private final UnaryOperator<E> flipAvailability;
        private boolean retainAddressesTillSuccess = true;

        private Builder(final BiIntFunction<Throwable, ? extends Completable> retryStrategy,
                        final UnaryOperator<E> flipAvailability) {
            this.retryStrategy = retryStrategy;
            this.flipAvailability = requireNonNull(flipAvailability);
        }

        /**
         * A {@link Publisher} returned from {@link ServiceDiscoverer#discover(Object)} may fail transiently leaving the
         * consumer of these events with an option of either disposing the addresses that were provided before the
         * error or retain them till a retry succeeds. This option enables/disables retention of addresses after an
         * error is observed till a subsequent success is observed.
         *
         * @param retainAddressesTillSuccess Enables retention of addresses when {@code true}.
         * @return {@code this}.
         */
        public Builder<ResolvedAddress, E> retainAddressesTillSuccess(final boolean retainAddressesTillSuccess) {
            this.retainAddressesTillSuccess = retainAddressesTillSuccess;
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
            return new DefaultServiceDiscoveryRetryStrategy<>(flipAvailability, retainAddressesTillSuccess,
                    retryStrategy);
        }

        /**
         * Creates a new builder that uses default retries.
         *
         * @param executor {@link Executor} to use for retry backoffs.
         * @param initialDelay {@link Duration} to use as initial delay for backoffs.
         * @param jitter {@link Duration} of jitter to apply to each backoff delay.
         * @param <ResolvedAddress> The type of address after resolution.
         * @return A new {@link Builder}.
         */
        public static <ResolvedAddress> Builder<ResolvedAddress, ServiceDiscovererEvent<ResolvedAddress>>
        withDefaults(final Executor executor, final Duration initialDelay, final Duration jitter) {
            return new Builder<>(defaultRetryStrategy(executor, initialDelay, jitter),
                    evt -> new DefaultServiceDiscovererEvent<>(evt.address(), !evt.isAvailable()));
        }

        /**
         * Creates a new builder that uses default retries.
         *
         * @param executor {@link Executor} to use for retry backoffs.
         * @param initialDelay {@link Duration} to use as initial delay for backoffs.
         * @param jitter {@link Duration} of jitter to apply to each backoff delay.
         * @param <ResolvedAddress> The type of address after resolution.
         * @return A new {@link Builder}.
         */
        public static <ResolvedAddress> Builder<ResolvedAddress, PartitionedServiceDiscovererEvent<ResolvedAddress>>
        withDefaultsForPartitions(final Executor executor, final Duration initialDelay, final Duration jitter) {
            return new Builder<>(defaultRetryStrategy(executor, initialDelay, jitter),
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
         * @param jitter {@link Duration} of jitter to apply to each backoff delay.
         * @param flipAvailability {@link UnaryOperator} that returns a new {@link ServiceDiscovererEvent} that is the
         * same as the passed {@link ServiceDiscovererEvent} but with {@link ServiceDiscovererEvent#isAvailable()} value
         * flipped.
         * @param <ResolvedAddress> The type of address after resolution.
         * @param <E> Type of {@link ServiceDiscovererEvent}s published from {@link ServiceDiscoverer#discover(Object)}.
         * @return A new {@link Builder}.
         */
        public static <ResolvedAddress, E extends ServiceDiscovererEvent<ResolvedAddress>> Builder<ResolvedAddress, E>
        withDefaults(final Executor executor, final Duration initialDelay, final Duration jitter,
                     final UnaryOperator<E> flipAvailability) {
            return new Builder<>(defaultRetryStrategy(executor, initialDelay, jitter), flipAvailability);
        }
    }

    private static BiIntFunction<Throwable, ? extends Completable> defaultRetryStrategy(
            final Executor executor, final Duration initialDelay, final Duration jitter) {
        return retryWithConstantBackoffDeltaJitter(__ -> true, initialDelay, jitter, executor);
    }

    private static final class EventsCache<R, E extends ServiceDiscovererEvent<R>> {
        @SuppressWarnings("rawtypes")
        private static final Map NONE_RETAINED = emptyMap();

        private Map<R, E> retainedAddresses = noneRetained();
        private final Map<R, E> activeAddresses = new HashMap<>();
        private final UnaryOperator<E> flipAvailability;

        EventsCache(final UnaryOperator<E> flipAvailability) {
            this.flipAvailability = flipAvailability;
        }

        @Nullable
        Collection<E> errorSeen() {
            if (retainedAddresses == NONE_RETAINED) {
                retainedAddresses = new HashMap<>(activeAddresses);
                activeAddresses.clear();
                return retainedAddresses.isEmpty() ? null : retainedAddresses.values();
            }
            return null;    // We already returned retainedAddresses for previous error, return null to avoid duplicates
        }

        Collection<E> consumeAndFilter(final Collection<E> events) {
            if (retainedAddresses == NONE_RETAINED) {
                List<E> toReturn = new ArrayList<>(events.size());
                for (E e : events) {
                    if (e.isAvailable()) {
                        if (activeAddresses.putIfAbsent(e.address(), e) == null) {
                            // FIXME: what if it's a custom E that has more fields?
                            toReturn.add(e);    // filter out duplicated addresses
                        }
                    } else {
                        if (activeAddresses.remove(e.address()) != null) {
                            toReturn.add(e);   // filter out addresses that were already unavailable
                        }
                    }
                    // FIXME: remove addresses that are available and unavailable in the same events collection
                }
                return toReturn;
            }

            // we have seen an error, replace cache with new addresses and deactivate the ones which are not present
            // in the new list.
            for (E event : events) {
                if (event.isAvailable()) {
                    activeAddresses.put(event.address(), event);
                } else {
                    activeAddresses.remove(event.address());
                }
            }

            // Worst case size is a sum of activeAddresses and retainedAddresses
            List<E> toReturn = new ArrayList<>(activeAddresses.size() + retainedAddresses.size());
            for (Map.Entry<R, E> entry : activeAddresses.entrySet()) {
                if (retainedAddresses.remove(entry.getKey()) == null) {
                    toReturn.add(entry.getValue()); // Add only those new addresses that were not retained before
                }
            }

            if (!retainedAddresses.isEmpty()) {
                for (E evt : retainedAddresses.values()) {
                    toReturn.add(flipAvailability.apply(evt));
                }
            }
            retainedAddresses = noneRetained();
            return toReturn;
        }

        @SuppressWarnings("unchecked")
        private static <R, E extends ServiceDiscovererEvent<R>> Map<R, E> noneRetained() {
            return NONE_RETAINED;
        }
    }
}
