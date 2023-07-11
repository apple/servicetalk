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
package io.servicetalk.client.api;

import io.servicetalk.transport.api.RetryableException;

import java.io.IOException;

/**
 * Thrown when no host is active to establish a new connection.
 */
public class NoActiveHostException extends IOException implements RetryableException {

    private static final long serialVersionUID = -4764627055167224323L;

    /**
     * Creates a new instance.
     *
     * @param message the detail message.
     */
    public NoActiveHostException(final String message) {
        super(message);
    }
}
