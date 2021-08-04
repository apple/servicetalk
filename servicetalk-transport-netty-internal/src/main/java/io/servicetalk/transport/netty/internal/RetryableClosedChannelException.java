/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.transport.api.RetryableException;

import java.nio.channels.ClosedChannelException;

/**
 * Indicates that an error happened due to connection closure, but is retryable.
 */
final class RetryableClosedChannelException extends ClosedChannelException implements RetryableException {
    private static final long serialVersionUID = 2006969744518089407L;

    RetryableClosedChannelException(final ClosedChannelException cause) {
        initCause(cause);
    }

    @Override
    public Throwable fillInStackTrace() {
        // We don't need stack trace because it's just a retryable wrapper for original ClosedChannelException
        return this;
    }
}
