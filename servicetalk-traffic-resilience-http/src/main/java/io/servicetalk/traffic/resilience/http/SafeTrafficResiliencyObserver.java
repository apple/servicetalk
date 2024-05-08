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
package io.servicetalk.traffic.resilience.http;

import io.servicetalk.capacity.limiter.api.CapacityLimiter;
import io.servicetalk.capacity.limiter.api.Classification;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.StreamingHttpRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static io.servicetalk.traffic.resilience.http.NoOpTrafficResiliencyObserver.NO_OP_TICKET_OBSERVER;
import static java.util.Objects.requireNonNull;

final class SafeTrafficResiliencyObserver implements TrafficResiliencyObserver {

    private final TrafficResiliencyObserver original;
    private static final Logger LOGGER = LoggerFactory.getLogger(SafeTrafficResiliencyObserver.class);

    SafeTrafficResiliencyObserver(final TrafficResiliencyObserver original) {
        this.original = requireNonNull(original);
    }

    @Override
    public void onRejectedUnmatchedPartition(final StreamingHttpRequest request) {
        try {
            this.original.onRejectedUnmatchedPartition(request);
        } catch (Throwable t) {
            LOGGER.error("Error during onRejectedUnmatchedPartition", t);
        }
    }

    @Override
    public void onRejectedLimit(final StreamingHttpRequest request, final String limiter,
                                final ContextMap meta, final Classification classification) {
        try {
            this.original.onRejectedLimit(request, limiter, meta, classification);
        } catch (Throwable t) {
            LOGGER.error("Error during onRejectedLimit", t);
        }
    }

    @Override
    public void onRejectedOpenCircuit(final StreamingHttpRequest request, final String breaker,
                                      final ContextMap meta, final Classification classification) {
        try {
            this.original.onRejectedOpenCircuit(request, breaker, meta, classification);
        } catch (Throwable t) {
            LOGGER.error("Error during onRejectedOpenCircuit", t);
        }
    }

    @Override
    public TicketObserver onAllowedThrough(final StreamingHttpRequest request,
                                           @Nullable final CapacityLimiter.LimiterState state) {
        try {
            return this.original.onAllowedThrough(request, state);
        } catch (Throwable t) {
            LOGGER.error("Error during onAllowedThrough", t);
            return NO_OP_TICKET_OBSERVER;
        }
    }
}
