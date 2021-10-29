/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.api;

import io.servicetalk.serializer.api.SerializationException;

/**
 * Exception thrown when a message was encoded with an unsupported encoder.
 *
 * @deprecated This exception type was thrown only by {@code ProtoBufSerializationProviderBuilder} that was deprecated
 * and will be removed in future releases. Use {@link SerializationException} instead that can be thrown by a new
 * implementation.
 */
@Deprecated
public final class MessageEncodingException extends RuntimeException {
    private static final long serialVersionUID = 4146293629657567572L;

    private final String encoding;

    /**
     * New instance.
     *
     * @param encoding the name of the encoding used
     */
    public MessageEncodingException(String encoding) {
        super("Compression " + encoding + " not supported");
        this.encoding = encoding;
    }

    /**
     * New instance.
     *
     * @param cause for this exception.
     * @param encoding the name of the encoding used
     */
    public MessageEncodingException(String encoding, Throwable cause) {
        super("Compression " + encoding + " not supported", cause);
        this.encoding = encoding;
    }

    /**
     * New instance.
     *
     * @param message for the exception.
     * @param cause for this exception.
     * @param enableSuppression whether or not suppression is enabled or disabled.
     * @param writableStackTrace whether or not the stack trace should be writable.
     * @param encoding the name of the encoding used
     */
    public MessageEncodingException(final String message, final Throwable cause, final boolean enableSuppression,
                                    final boolean writableStackTrace, final String encoding) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.encoding = encoding;
    }

    /**
     * The name of the encoding used when the Exception was thrown.
     * @return the name of the encoding used when the Exception was thrown
     */
    public String encoding() {
        return encoding;
    }
}
