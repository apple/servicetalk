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
import io.servicetalk.capacity.limiter.api.CapacityLimiter.LimiterState;
import io.servicetalk.capacity.limiter.api.Classification;
import io.servicetalk.circuit.breaker.api.CircuitBreaker;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.StreamingHttpRequest;

import javax.annotation.Nullable;

/**
 * A {@link TrafficResilienceHttpServiceFilter} or {@link TrafficResilienceHttpClientFilter} observer.
 * Tracks interactions with {@link CapacityLimiter}s and/or {@link CircuitBreaker}s, and exposes a transactional
 * {@link TicketObserver} for each request that was let-through.
 * <p>
 * <strong>Note:</strong> All interactions with callbacks in the file below, are executed within the flow
 * of each request, and are expected to be **non-blocking**. Any blocking calls within the implementation may impact
 * negatively the performance of your application.
 *
 */
public interface TrafficResiliencyObserver {

    /**
     * Transactional observer for the requests that were let-through.
     * Allows the caller to track the result of the ticket.
     */
    interface TicketObserver {
        /**
         * Called when the request was completed successfully.
         */
        void onComplete();

        /**
         * Called when the request flow was cancelled.
         */
        void onCancel();

        /**
         * Called when the request flow terminated erroneously.
         * @param throwable the {@link Throwable} that caused the request to fail.
         */
        void onError(Throwable throwable);
    }

    /**
     * Called when a request was "soft-rejected" due to unmatched partition.
     * Note: The decision of whether the request was let through or rejected depends on the configuration of the filter.
     * @param request the {@link StreamingHttpRequest} correlating to this rejection.
     */
    void onRejectedUnmatchedPartition(StreamingHttpRequest request);

    /**
     * Called when a request was "hard-rejected" due to a {@link CapacityLimiter} reaching its limit.
     *
     * @param request the {@link StreamingHttpRequest} correlates to this rejection.
     * @param capacityLimiter the {@link CapacityLimiter}'s name that correlates to this traffic flow.
     * @param meta the {@link ContextMap} that correlates to this request (if any).
     * @param classification the {@link Classification} that correlates to this request (if any).
     */
    void onRejectedLimit(StreamingHttpRequest request, String capacityLimiter, ContextMap meta,
                         Classification classification);

    /**
     * Called when a request was "hard-rejected" due to a {@link CircuitBreaker} open state.
     *
     * @param request the {@link StreamingHttpRequest} correlates to this rejection.
     * @param circuitBreaker the {@link CircuitBreaker}'s name that correlates to this traffic flow.
     * @param meta the {@link ContextMap} that correlates to this request (if any).
     * @param classification the {@link Classification} that correlates to this request (if any).
     */
    void onRejectedOpenCircuit(StreamingHttpRequest request, String circuitBreaker,
                                    ContextMap meta, Classification classification);

    /**
     * Called when a request was let through.
     *
     * @param request the {@link StreamingHttpRequest} correlates to this rejection.
     * @param state the {@link LimiterState} that correlates to this accepted request.
     * @return A {@link TicketObserver} to track the state of the allowed request.
     */
    TicketObserver onAllowedThrough(StreamingHttpRequest request, @Nullable LimiterState state);
}
