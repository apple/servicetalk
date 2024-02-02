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
 * Enumeration of the main failure classes.
 */
public enum ErrorClass {

    /**
     * Failures related to locally enforced timeouts that prevent session establishment with the peer.
     */
    LOCAL_ORIGIN_TIMEOUT(true),
    /**
     * Failures related to connection establishment.
     */
    LOCAL_ORIGIN_CONNECT_FAILED(true),

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
