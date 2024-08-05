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
package io.servicetalk.capacity.limiter.api;

import io.servicetalk.context.api.ContextMap;

import javax.annotation.Nullable;

/**
 * A provider of capacity for a client or server.
 * <p/>
 * <h2>Capacity</h2>
 * Capacity for an entity is defined as the number of concurrent requests that it can process without significantly
 * affecting resource consumption or likelihood to successfully process in a timely manner given currently
 * available resources vs resources required to process the new request.
 * This capacity offered can be static or dynamic and the semantics of determination is left to implementations.
 * <p/>
 * <h2>Consumption</h2>
 * As the capacity is defined in terms of concurrent requests, as {@link #tryAcquire(Classification, ContextMap)
 * new requests are seen}, some portion of this capacity is deemed to be consumed till a subsequent callback marks
 * the end of processing for that request. Number of times that {@link #tryAcquire(Classification, ContextMap)}
 * is called without a corresponding termination callback is termed as demand.
 * <p/>
 * <h2>Request Lifetime</h2>
 * Request processing starts when {@link #tryAcquire(Classification, ContextMap)} is called and returns a non-null
 * {@link Ticket} and terminates when either one of the following occurs:
 * <ul>
 *     <li>When the request is successfully completed (Default: {@link Ticket#completed()})</li>
 *     <li>When the request fails due to an external capacity rejection i.e., dropped (Default:
 *     {@link Ticket#dropped()})</li>
 *     <li>When the request fails due to a local error (Default: {@link Ticket#failed(Throwable)})</li>
 *     <li>When the request is cancelled (Default: {@link Ticket#ignored()})</li>
 * </ul>
 * <p/>
 * <h2>Request Classifications</h2>
 * Requests can be classified with different classes, that can be taken into consideration when a
 * {@link CapacityLimiter} implementation supports this.
 * {@link Classification} is used as hint from the user of the importance of the incoming request, and are not
 * guaranteed to have an influence to the decision if the {@link CapacityLimiter} doesn't support them or chooses to
 * ignore them.
 */
public interface CapacityLimiter {

    /**
     * Identifying name for this {@link CapacityLimiter}.
     *
     * @return the name of this {@link CapacityLimiter}.
     */
    String name();

    /**
     * Evaluate whether there is enough capacity to allow the call for the given {@link Classification} and the
     * {@link ContextMap context}.
     *
     * @param classification A class tha represents the <i>importance</i> of a request, to be evaluated upon permit.
     * @param context Contextual metadata supported for evaluation from the call-site. This, in an HTTP context
     * would typically be the {@code HttpRequest#context()}.
     * @return {@link Ticket} when capacity is enough to satisfy the demand or {@code null} when not.
     * @see CapacityLimiter
     */
    @Nullable
    Ticket tryAcquire(Classification classification, @Nullable ContextMap context);

    /**
     * Representation of the state of the {@link CapacityLimiter} when the {@link Ticket} is issued.
     */
    interface LimiterState {
        /**
         * Returns the remaining allowance of the {@link CapacityLimiter} when the {@link Ticket} was issued.
         *
         * @return the remaining allowance of the {@link CapacityLimiter} when the {@link Ticket} was issued.
         */
        int remaining();

        /**
         * Returns the current pending (in use capacity) demand when the {@link Ticket} was issued.
         * <p>
         * If the pending is unknown a negative value i.e., -1 is allowed to express this.
         * @return the current pending (in use capacity) demand when the {@link Ticket} was issued.
         */
        int pending();
    }

    /**
     * Result of {@link #tryAcquire(Classification, ContextMap)} when capacity is enough to meet the demand.
     * A {@link Ticket} can terminate when either one of the following occurs:
     * <ul>
     *     <li>When the request is successfully completed {@link Ticket#completed()}
     *     <li>When the request fails due to an external capacity rejection i.e., dropped {@link Ticket#dropped()}</li>
     *     <li>When the request fails due to a local error{@link Ticket#failed(Throwable)}
     *     <li>When the request is cancelled {@link Ticket#ignored()}
     * </ul>
     */
    interface Ticket {

        /**
         * Representation of the state of the {@link CapacityLimiter} when this {@link Ticket} was issued.
         *
         * @return the {@link LimiterState state} of the limiter at the time this {@link Ticket} was issued.
         */
        LimiterState state();

        /**
         * Callback to indicate that a request was completed successfully.
         *
         * @return An integer as hint (if positive), that represents an estimated remaining capacity after
         * this {@link Ticket ticket's} terminal callback. If supported, a positive number means there is capacity.
         * Otherwise, a negative value is returned.
         */
        int completed();

        /**
         * Callback to indicate that a request was dropped externally (e.g. peer rejection) due to capacity
         * issues.
         * <p>
         * Note that loss-based algorithms tend to reduce the limit by a multiplier on such events.
         *
         * @return An integer as hint (if positive), that represents an estimated remaining capacity after
         * this {@link Ticket ticket's} terminal callback. If supported, a positive number means there is capacity.
         * Otherwise, a negative value is returned.
         */
        int dropped();

        /**
         * Callback to indicate that a request has failed.
         * <p>
         * Algorithms may choose to act upon failures (i.e. Circuit Breaking).
         *
         * @param error the failure cause.
         * @return An integer as hint (if positive), that represents an estimated remaining capacity after
         * this {@link Ticket ticket's} terminal callback. If supported, a positive number means there is capacity.
         * Otherwise, a negative value is returned.
         */
        int failed(Throwable error);

        /**
         * Callback to indicate that a request had not a capacity deterministic termination.
         * <p>
         * Ignoring a {@link Ticket} is a way to indicate to the {@link CapacityLimiter} that this operation's
         * termination should not be considered towards a decision for modifying the limits. e.g., An algorithm that
         * measures delays (time start - time end), can use that to ignore a particular result from the feedback loop.
         *
         * @return An integer as hint (if positive), that represents an estimated remaining capacity after
         * this {@link Ticket ticket's} terminal callback. If supported, a positive number means there is capacity.
         * Otherwise, a negative value is returned.
         */
        int ignored();
    }
}
