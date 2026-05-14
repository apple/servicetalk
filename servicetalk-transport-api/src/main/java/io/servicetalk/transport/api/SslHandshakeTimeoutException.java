/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.api;

import javax.net.ssl.SSLHandshakeException;

/**
 * Thrown when an SSL/TLS handshake fails with a timeout.
 */
public final class SslHandshakeTimeoutException extends SSLHandshakeException implements RetryableException {
    private static final long serialVersionUID = -747038432277405001L;

    /**
     * Creates a new instance.
     *
     * @param cause original cause of the failed SSL/TLS handshake
     */
    public SslHandshakeTimeoutException(final SSLHandshakeException cause) {
        super(cause.getMessage());
        initCause(cause);
    }

    @Override
    public Throwable fillInStackTrace() {
        // We don't need stack trace because it's just a retryable wrapper for original SslHandshakeTimeoutException
        return this;
    }
}
