/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.encoding.api;

import io.servicetalk.serializer.api.SerializationException;

/**
 * A specialization of {@link SerializationException} used to indicate an encoding exception.
 */
public final class BufferEncodingException extends SerializationException {
    private static final long serialVersionUID = -7422215018667837872L;

    /**
     * Create a new instance.
     * @param message The message to use.
     */
    public BufferEncodingException(final String message) {
        super(message);
    }

    /**
     * New instance.
     *
     * @param message for the exception.
     * @param cause for this exception.
     */
    public BufferEncodingException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * New instance.
     *
     * @param cause for this exception.
     */
    public BufferEncodingException(final Throwable cause) {
        super(cause);
    }

    /**
     * New instance.
     * @param message for the exception.
     * @param cause for this exception.
     * @param enableSuppression whether or not suppression is enabled or disabled.
     * @param writableStackTrace whether or not the stack trace should be writable.
     */
    public BufferEncodingException(final String message, final Throwable cause, final boolean enableSuppression,
                                   final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
