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
package io.servicetalk.concurrent.internal;

/**
 * Used in scenarios where a subscribe to an asynchronous source is attempted, but no "real" subscription is
 * established. In other words the subscribe operation was rejected.
 */
public class RejectedSubscribeException extends RuntimeException implements RejectedSubscribeError {
    private static final long serialVersionUID = 8816644436486094573L;

    /**
     * Create a new instance.
     *
     * @param message The exception message.
     */
    public RejectedSubscribeException(final String message) {
        super(message);
    }

    /**
     * Create a new instance.
     *
     * @param cause The cause of the exception.
     */
    public RejectedSubscribeException(final Throwable cause) {
        super(cause);
    }
}
