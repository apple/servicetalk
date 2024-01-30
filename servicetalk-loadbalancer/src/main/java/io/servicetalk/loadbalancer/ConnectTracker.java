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
package io.servicetalk.loadbalancer;

/**
 * An interface for tracking connection establishment measurements.
 * This has an intended usage similar to the {@link RequestTracker} but with a focus on connection establishment
 * metrics.
 */
interface ConnectTracker {

    /**
     * Get the current time in nanoseconds.
     * Note: this must not be a stateful API. Eg, it does not necessarily have a correlation with any other method call
     * and such shouldn't be used as a method of counting in the same way that {@link RequestTracker} is used.
     * @return the current time in nanoseconds.
     */
    long beforeConnectStart();

    /**
     * Callback to notify the parent {@link HealthChecker} that an attempt to connect to this host has succeeded.
     * @param beforeConnectStart the time that the connection attempt was initiated.
     */
    void onConnectSuccess(long beforeConnectStart);

    /**
     * Callback to notify the parent {@link HealthChecker} that an attempt to connect to this host has failed.
     * @param beforeConnectStart the time that the connection attempt was initiated.
     */
    void onConnectError(long beforeConnectStart);
}
