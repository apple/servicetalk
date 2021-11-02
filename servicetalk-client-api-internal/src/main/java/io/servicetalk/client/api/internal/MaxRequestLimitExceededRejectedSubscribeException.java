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
package io.servicetalk.client.api.internal;

import io.servicetalk.client.api.MaxRequestLimitExceededException;
import io.servicetalk.concurrent.internal.RejectedSubscribeError;

/**
 * Exception raised when more concurrent requests have been issued on a connection than is allowed.
 *
 * @deprecated There is no code that can throw this exception. We will remove it and create a new exception type if
 * there is a use-case in future releases.
 */
@Deprecated
public final class MaxRequestLimitExceededRejectedSubscribeException extends MaxRequestLimitExceededException
        implements RejectedSubscribeError {

    private static final long serialVersionUID = -4289401663713189702L;

    /**
     * New instance.
     *
     * @param message The exception message.
     */
    public MaxRequestLimitExceededRejectedSubscribeException(final String message) {
        super(message);
    }
}
