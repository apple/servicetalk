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
package io.servicetalk.serializer.api;

/**
 * A {@link SerializationException} indicating that a message exceeded the maximum size allowed for
 * (de)serialization. It is distinct from other {@link SerializationException}s so that transports can map it to a
 * transport-specific "too large" status (e.g. HTTP {@code 413 Payload Too Large}) rather than a generic
 * (de)serialization failure.
 */
public class MaxMessageSizeExceededException extends SerializationException {
    private static final long serialVersionUID = 5346649275L;

    /**
     * New instance.
     *
     * @param message for the exception.
     */
    public MaxMessageSizeExceededException(final String message) {
        super(message);
    }

    /**
     * New instance.
     *
     * @param message for the exception.
     * @param cause for this exception.
     */
    public MaxMessageSizeExceededException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
