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
package io.servicetalk.apple.capacity.limiter.api;

import io.servicetalk.context.api.ContextMap;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

/**
 * A composable {@link CapacityLimiter} for the purposes of creating hierarchies of providers to allow practises
 * such as overall capacity control along with "specific" (i.e. customer based) partitioned quotas.
 * The order of the {@link CapacityLimiter} is the same as provided by the user, and the same order is applied
 * when tickets acquired are released back to their owner.
 */
final class CompositeCapacityLimiter implements CapacityLimiter {

    private final List<CapacityLimiter> providers;
    private final String name;

    CompositeCapacityLimiter(final List<CapacityLimiter> providers) {
        if (requireNonNull(providers).isEmpty()) {
            throw new IllegalArgumentException("Empty capacity limiters.");
        }
        this.providers = new ArrayList<>(providers);
        this.name = CompositeCapacityLimiter.class.getSimpleName() + "[ " +
                providers.stream().map(CapacityLimiter::name).collect(joining(", ")) + " ]";
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Ticket tryAcquire(final Classification classification, @Nullable final ContextMap context) {
        Ticket[] results = null;
        int idx = 0;
        for (CapacityLimiter provider : providers) {
            Ticket ticket = provider.tryAcquire(classification, context);
            if (ticket != null) {
                if (results == null) {
                    results = new Ticket[providers.size()];
                }

                results[idx++] = ticket;
                continue;
            }

            if (results != null) {
                completed(results);
                return null;
            }
        }

        assert results != null;
        return compositeResult(results);
    }

    private static int completed(Ticket[] results) {
        int remaining = 1;
        for (Ticket ticket : results) {
            if (ticket == null) {
                break;
            }
            int res = ticket.completed();
            if (res <= 0) {
                remaining = res;
            }
        }
        return remaining;
    }

    private static int failed(Throwable cause, Ticket[] results) {
        int remaining = 1;
        for (Ticket ticket : results) {
            if (ticket == null) {
                break;
            }
            int res = ticket.failed(cause);
            if (res <= 0) {
                remaining = res;
            }
        }
        return remaining;
    }

    private static int dropped(Ticket[] results) {
        int remaining = 1;
        for (Ticket ticket : results) {
            if (ticket == null) {
                break;
            }
            int res = ticket.dropped();
            if (res <= 0) {
                remaining = res;
            }
        }
        return remaining;
    }

    private static int cancelled(Ticket[] results) {
        int remaining = 1;
        for (Ticket ticket : results) {
            if (ticket == null) {
                break;
            }
            int res = ticket.ignored();
            if (res <= 0) {
                remaining = res;
            }
        }
        return remaining;
    }

    private static Ticket compositeResult(final Ticket[] tickets) {
        return new Ticket() {
            @Override
            public LimiterState state() {
                // Targeting the most specific one (assuming an order of rate-limiter, customer-quota-limiter).
                // In the future we could make this configurable if proven useful.
                return tickets[tickets.length - 1].state();
            }

            @Override
            public int completed() {
                return CompositeCapacityLimiter.completed(tickets);
            }

            @Override
            public int failed(final Throwable cause) {
                return CompositeCapacityLimiter.failed(cause, tickets);
            }

            @Override
            public int dropped() {
                return CompositeCapacityLimiter.dropped(tickets);
            }

            @Override
            public int ignored() {
                return CompositeCapacityLimiter.cancelled(tickets);
            }
        };
    }
}
