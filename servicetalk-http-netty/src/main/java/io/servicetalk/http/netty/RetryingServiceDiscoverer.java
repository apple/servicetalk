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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.DelegatingServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpExecutionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.UNAVAILABLE;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoffFullJitter;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyMap;

final class RetryingServiceDiscoverer<U, R, E extends ServiceDiscovererEvent<R>>
        extends DelegatingServiceDiscoverer<U, R, E> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RetryingServiceDiscoverer.class);

    private static final Duration SD_RETRY_STRATEGY_INIT_DURATION = ofSeconds(2);
    private static final Duration SD_RETRY_STRATEGY_MAX_DELAY = ofSeconds(128);

    private final String targetResource;
    private final BiIntFunction<Throwable, ? extends Completable> retryStrategy;
    private final UnaryOperator<E> makeUnavailable;

    RetryingServiceDiscoverer(final String targetResource,
                              final ServiceDiscoverer<U, R, E> delegate,
                              @Nullable BiIntFunction<Throwable, ? extends Completable> retryStrategy,
                              final HttpExecutionContext executionContext,
                              final UnaryOperator<E> makeUnavailable) {
        super(delegate);
        this.targetResource = targetResource;
        if (retryStrategy == null) {
            retryStrategy = retryWithExponentialBackoffFullJitter(__ -> true, SD_RETRY_STRATEGY_INIT_DURATION,
                    SD_RETRY_STRATEGY_MAX_DELAY, executionContext.executor());
        }
        this.retryStrategy = retryStrategy;
        this.makeUnavailable = makeUnavailable;
    }

    @Override
    public Publisher<Collection<E>> discover(final U address) {
        // The returned publisher is guaranteed to never fail because we retry all errors here. However, LoadBalancer
        // can still cancel and re-subscribe in attempt to recover from unhealthy state. In this case, we need to
        // re-initialize the ServiceDiscovererEventsCache and restart from an empty state.
        return Publisher.defer(() -> {
            final ServiceDiscovererEventsCache<R, E> eventsCache =
                    new ServiceDiscovererEventsCache<>(targetResource, makeUnavailable);
            return delegate().discover(address)
                    .map(eventsCache::consumeAndFilter)
                    .beforeOnError(eventsCache::errorSeen)
                    // terminateOnNextException false -> LB is after this operator, if LB throws do best effort retry
                    .retryWhen(false, retryStrategy);
        });
    }

    private static final class ServiceDiscovererEventsCache<R, E extends ServiceDiscovererEvent<R>> {
        @SuppressWarnings("rawtypes")
        private static final Map NONE_RETAINED = emptyMap();

        private final String targetResource;
        private final UnaryOperator<E> makeUnavailable;
        private final Map<R, E> currentState = new HashMap<>();
        private Map<R, E> retainedState = noneRetained();

        private ServiceDiscovererEventsCache(final String targetResource, final UnaryOperator<E> makeUnavailable) {
            this.targetResource = targetResource;
            this.makeUnavailable = makeUnavailable;
        }

        void errorSeen(final Throwable t) {
            if (retainedState == NONE_RETAINED) {
                retainedState = new HashMap<>(currentState);
                currentState.clear();
            }
            LOGGER.debug("{} observed an error from ServiceDiscoverer", targetResource, t);
        }

        Collection<E> consumeAndFilter(final Collection<E> events) {
            if (retainedState == NONE_RETAINED) {
                for (E event : events) {
                    if (UNAVAILABLE.equals(event.status())) {
                        currentState.remove(event.address());
                    } else {
                        currentState.put(event.address(), event);
                    }
                }
                return events;
            }

            // We have seen an error and re-subscribed upon retry. Based on the Publisher rule 1.10
            // (https://github.com/reactive-streams/reactive-streams-jvm?tab=readme-ov-file#1.10), each subscribe
            // expects a different Subscriber. Therefore, discovery Publisher suppose to start from a fresh state. We
            // should populate currentState with new addresses and deactivate the ones which are not present in the new
            // collection, but were left in retainedState.
            assert currentState.isEmpty();
            final List<E> toReturn = new ArrayList<>(events.size() + retainedState.size());
            for (E event : events) {
                final R address = event.address();
                toReturn.add(event);
                retainedState.remove(address);
                if (!UNAVAILABLE.equals(event.status())) {
                    currentState.put(address, event);
                }
            }

            for (E event : retainedState.values()) {
                assert event.status() != UNAVAILABLE;
                toReturn.add(makeUnavailable.apply(event));
            }

            retainedState = noneRetained();
            return toReturn;
        }

        @SuppressWarnings("unchecked")
        private static <R, E extends ServiceDiscovererEvent<R>> Map<R, E> noneRetained() {
            return NONE_RETAINED;
        }
    }
}
