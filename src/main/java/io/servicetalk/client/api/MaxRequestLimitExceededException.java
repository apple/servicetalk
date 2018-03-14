/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.client.api;

/**
 * Exception raised when more concurrent requests have been issued on a connection than is allowed.
 */
public final class MaxRequestLimitExceededException extends RuntimeException {
    private static final long serialVersionUID = 3442285141858696983L;

    private final int limit;

    /**
     * New instance.
     *
     * @param connection The connection object.
     * @param limit Saturation limit for the connection.
     * @param cause {@link Throwable} cause for this exception.
     */
    public MaxRequestLimitExceededException(Object connection, int limit, Throwable cause) {
        super("Connection " + connection + " exceeded max requests limit: " + limit, cause);
        this.limit = limit;
    }

    /**
     * New instance.
     *
     * @param connection The connection object.
     * @param limit Saturation limit for the connection.
     */
    public MaxRequestLimitExceededException(Object connection, int limit) {
        super("Connection " + connection + " exceeded max requests limit: " + limit);
        this.limit = limit;
    }

    /**
     * The configured limit which was breached.
     *
     * @return max request limit.
     */
    public int getLimit() {
        return limit;
    }
}
