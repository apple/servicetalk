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

import io.servicetalk.transport.api.RetryableException;

import java.net.ConnectException;

/**
 * Throws when a connect operations failed.
 */
public class RetryableConnectException extends ConnectException implements RetryableException {
    private static final long serialVersionUID = -1023700497112242644L;

    /**
     * New instance.
     *
     * @param message for the exception.
     */
    public RetryableConnectException(final String message) {
        super(message);
    }

    /**
     * Create a new instance.
     * @param cause the original cause.
     */
    public RetryableConnectException(final ConnectException cause) {
        super(cause.getMessage());
        initCause(cause);
    }
}
