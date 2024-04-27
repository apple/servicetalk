/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.apple.traffic.resilience.http;

import io.servicetalk.apple.capacity.limiter.api.RequestRejectedException;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.netty.RetryingHttpRequesterFilter;

import java.time.Duration;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A {@link RuntimeException} to indicate that a request was rejected by a server due to capacity constraints.
 * This error reflects the client side application logic and its interpretation of a service response; meaning that
 * its up to the application to declare whether a {@link HttpResponseStatus#TOO_MANY_REQUESTS} is a safe-to-retry
 * response, and if so after how much {@link #delay()}.
 */
public final class DelayedRetryRequestRejectedException extends RequestRejectedException
        implements RetryingHttpRequesterFilter.DelayedRetry {

    private static final long serialVersionUID = -7933994513110803151L;
    private final Duration delay;

    /**
     * Creates a new instance.
     *
     * @param delay The delay to be provided as input to a retry mechanism.
     */
    public DelayedRetryRequestRejectedException(final Duration delay) {
        this.delay = requireNonNull(delay);
    }

    /**
     * Creates a new instance.
     *
     * @param delay The delay to be provided as input to a retry mechanism.
     * @param message the detail message.
     */
    public DelayedRetryRequestRejectedException(final Duration delay, @Nullable final String message) {
        super(message);
        this.delay = requireNonNull(delay);
    }

    /**
     * Creates a new instance.
     *
     * @param delay The delay to be provided as input to a retry mechanism.
     * @param message the detail message.
     * @param cause of this exception.
     */
    public DelayedRetryRequestRejectedException(final Duration delay,
                                                @Nullable final String message, @Nullable final Throwable cause) {
        super(message, cause);
        this.delay = requireNonNull(delay);
    }

    /**
     * Creates a new instance.
     *
     * @param delay The delay to be provided as input to a retry mechanism.
     * @param cause of this exception.
     */
    public DelayedRetryRequestRejectedException(final Duration delay, @Nullable final Throwable cause) {
        super(cause);
        this.delay = requireNonNull(delay);
    }

    /**
     * Creates a new instance.
     *
     * @param delay The delay to be provided as input to a retry mechanism.
     * @param message the detail message.
     * @param cause of this exception.
     * @param enableSuppression {@code true} if suppression should be enabled.
     * @param writableStackTrace {@code true} if the stack trace should be writable
     */
    public DelayedRetryRequestRejectedException(final Duration delay,
                                                @Nullable final String message, @Nullable final Throwable cause,
                                                final boolean enableSuppression, final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.delay = requireNonNull(delay);
    }

    @Override
    public Duration delay() {
        return delay;
    }
}
