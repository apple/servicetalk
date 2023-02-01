/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import javax.net.ssl.SSLException;

/**
 * When thrown contains information about a failure during TLS certificate compression.
 */
public final class CertificateCompressionException extends SSLException {
    private static final long serialVersionUID = -8127714610191317316L;

    /**
     * Create a new {@link CertificateCompressionException} with just a message.
     *
     * @param message the message for the exception.
     */
    public CertificateCompressionException(final String message) {
        super(message);
    }

    /**
     * Create a new {@link CertificateCompressionException} with message and cause.
     *
     * @param message the message for the exception.
     * @param cause the cause of this exception.
     */
    public CertificateCompressionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
