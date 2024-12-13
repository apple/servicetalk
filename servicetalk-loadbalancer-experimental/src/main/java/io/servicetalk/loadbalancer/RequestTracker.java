/*
 * Copyright © 2023-2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.loadbalancer;

import io.servicetalk.context.api.ContextMap;

/**
 * A tracker of latency of an action over time.
 * <p>
 * The usage of the RequestTracker is intended to follow the simple workflow:
 * <ul>
 *     <li>At initiation of an action for which a request is must call {@link RequestTracker#beforeRequestStart()} and
 *     save the timestamp much like would be done when using a stamped lock.</li>
 *     <li>Once the request event is complete only one of the {@link RequestTracker#onRequestSuccess(long)} or
 *     {@link RequestTracker#onRequestError(long, ErrorClass)} methods must be called and called exactly once</li>
 * </ul>
 * In other words, every call to {@link RequestTracker#beforeRequestStart()} must be followed by exactly one call to
 * either of the completion methods {@link RequestTracker#onRequestSuccess(long)} or
 * {@link RequestTracker#onRequestError(long, ErrorClass)}. Failure to do so can cause state corruption in the
 * {@link RequestTracker} implementations which may track not just latency but also the outstanding requests.
 */
public interface RequestTracker {

    ContextMap.Key<RequestTracker> REQUEST_TRACKER_KEY =
            ContextMap.Key.newKey("request_tracker", RequestTracker.class);

    /**
     * Invoked before each start of the action for which latency is to be tracked.
     *
     * @return Current time in nanoseconds.
     */
    long beforeRequestStart();

    /**
     * Records a successful completion of the action for which latency is to be tracked.
     *
     * @param beforeStartTimeNs return value from {@link #beforeRequestStart()}.
     */
    void onRequestSuccess(long beforeStartTimeNs);

    /**
     * Records a failed completion of the action for which latency is to be tracked.
     *
     * @param beforeStartTimeNs return value from {@link #beforeRequestStart()}.
     * @param errorClass the class of error that triggered this method.
     */
    void onRequestError(long beforeStartTimeNs, ErrorClass errorClass);

    /**
     * Enumeration of the main failure classes.
     */
    enum ErrorClass {
        /**
         * Failures caused locally, these would be things that failed due to an exception locally.
         */
        LOCAL_ORIGIN_REQUEST_FAILED(true),

        /**
         * Failures related to locally enforced timeouts waiting for responses from the peer.
         */
        EXT_ORIGIN_TIMEOUT(false),

        /**
         * Failures returned from the remote peer. This will be things like 5xx responses.
         */
        EXT_ORIGIN_REQUEST_FAILED(false),

        /**
         * Failure due to cancellation.
         */
        CANCELLED(true);

        private final boolean isLocal;

        ErrorClass(boolean isLocal) {
            this.isLocal = isLocal;
        }

        public boolean isLocal() {
            return isLocal;
        }
    }
}
