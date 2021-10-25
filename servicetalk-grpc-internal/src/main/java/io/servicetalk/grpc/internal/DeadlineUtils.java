/*
 * Copyright © 2019-2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.internal;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequestMetaData;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.utils.internal.DurationUtils.isInfinite;

/**
 * Constants and utilities related to
 * <a href="https://grpc.io/docs/what-is-grpc/core-concepts/#deadlines">gRPC deadlines</a>.
 *
 * @see <a href="https://grpc.io/blog/deadlines/">gRPC and Deadlines</a>
 */
public final class DeadlineUtils {

    /**
     * HTTP header name for gRPC deadline/timeout.
     */
    public static final CharSequence GRPC_TIMEOUT_HEADER_KEY = newAsciiString("grpc-timeout");

    /**
     * gRPC timeout is stored in context as a deadline so that when propagated to a new request the remaining time to be
     * included in the request can be calculated.
     *
     * @deprecated Use {@link #GRPC_DEADLINE_CONTEXT_KEY}
     */
    @Deprecated
    public static final AsyncContextMap.Key<Long> GRPC_DEADLINE_KEY =
            AsyncContextMap.Key.newKey("grpc-deadline");

    /**
     * gRPC timeout is stored in a context as a deadline so that when propagated to a new request the remaining time to
     * be included in the request can be calculated.
     */
    public static final ContextMap.Key<Long> GRPC_DEADLINE_CONTEXT_KEY =
            ContextMap.Key.newKey("grpc-deadline", Long.class);

    /**
     * <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md#requests">gRPC spec</a> requires timeout
     * value to be 8 or fewer ASCII integer digits.
     */
    public static final long EIGHT_NINES = 99_999_999L;

    /**
     * Maximum timeout which can be specified for a <a href="https://www.grpc.io">gRPC</a>
     * <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md#requests">request</a>. Note that this
     * maximum is effectively infinite as the duration is more than 11,000 years.
     */
    public static final Duration GRPC_MAX_TIMEOUT = Duration.ofHours(EIGHT_NINES);

    /**
     * Duration of {@link Long#MAX_VALUE} nanoseconds, the maximum duration that
     * can be represented by a single {@code long}.
     */
    private static final Duration LONG_MAX_NANOS = Duration.ofNanos(Long.MAX_VALUE);

    /**
     * Conversions between time units for gRPC timeout
     */
    private static final LongUnaryOperator[] CONVERTERS = {
            TimeUnit.NANOSECONDS::toMicros,
            TimeUnit.MICROSECONDS::toMillis,
            TimeUnit.MILLISECONDS::toSeconds,
            TimeUnit.SECONDS::toMinutes,
            TimeUnit.MINUTES::toHours,
    };

    /**
     * Conversions between time units for gRPC timeout
     */
    private static final long[] PER_NEXT_UNIT = {
            TimeUnit.MICROSECONDS.toNanos(1),
            TimeUnit.MILLISECONDS.toMicros(1),
            TimeUnit.SECONDS.toMillis(1),
            TimeUnit.MINUTES.toSeconds(1),
            TimeUnit.HOURS.toMinutes(1),
            100_000_000,
    };

    /**
     * Allowed time units for gRPC timeout
     */
    private static final char[] TIMEOUT_UNIT_CHARS = "numSMH".toCharArray();

    static {
        AsyncContext.newKeyMapping(GRPC_DEADLINE_KEY, GRPC_DEADLINE_CONTEXT_KEY);
    }

    private DeadlineUtils() {
        // No instances.
    }

    /**
     * Make a timeout header value from the specified duration.
     *
     * @param timeout the timeout {@link Duration}
     * @return The timeout header text value or null for infinite timeouts
     */
    public static @Nullable CharSequence makeTimeoutHeader(@Nullable Duration timeout) {
        if (isInfinite(timeout, GRPC_MAX_TIMEOUT)) {
            return null;
        }

        if (timeout.isNegative()) {
            // Assume negative timeout is the result of an already missed deadline.
            // Ideally this should have been noticed before now but a zero nanosecond timeout
            // will have the same effect.
            timeout = Duration.ZERO;
        }

        int units = 0;
        // Reduce before nanos conversion to ensure we don't overflow long. (>292 years)
        while (isInfinite(timeout, LONG_MAX_NANOS)) {
            timeout = timeout.dividedBy(PER_NEXT_UNIT[units++]);
        }
        long timeoutValue = timeout.toNanos();
        // determine most appropriate units
        while (timeoutValue > EIGHT_NINES || (timeoutValue > 0 && timeoutValue % PER_NEXT_UNIT[units] == 0)) {
            timeoutValue = CONVERTERS[units].applyAsLong(timeoutValue);
            units++; // cannot go past end of units array as we have already range checked
        }
        return newAsciiString(Long.toString(timeoutValue) + TIMEOUT_UNIT_CHARS[units]);
    }

    /**
     * Extract the timeout duration from the request HTTP headers if present.
     *
     * @param request The HTTP request to be used as source of the GRPC timeout header
     * @return The non-negative timeout duration which may null if not present
     * @throws IllegalArgumentException if the timeout value is malformed
     */
    public static @Nullable Duration readTimeoutHeader(HttpRequestMetaData request) {
        return readTimeoutHeader(request.headers());
    }

    /**
     * Extract the timeout duration from the HTTP headers if present.
     *
     * @param headers The HTTP headers to be used as source of the GRPC timeout header
     * @return The non-negative timeout duration which may null if not present
     * @throws IllegalArgumentException if the timeout value is malformed
     */
    public static @Nullable Duration readTimeoutHeader(HttpHeaders headers) {
        CharSequence grpcTimeoutValue = headers.get(GRPC_TIMEOUT_HEADER_KEY);
        return null == grpcTimeoutValue ? null : parseTimeoutHeader(grpcTimeoutValue);
    }

    /**
     * Parse a gRPC timeout header value as a duration.
     *
     * @param grpcTimeoutValue the text value of {@link #GRPC_TIMEOUT_HEADER_KEY} header
     * header to be parsed to a timeout duration
     * @return The non-negative timeout duration
     * @throws IllegalArgumentException if the timeout value is malformed
     */
    public static Duration parseTimeoutHeader(CharSequence grpcTimeoutValue) throws IllegalArgumentException {
        if (grpcTimeoutValue.length() < 2 || grpcTimeoutValue.length() > 9) {
            throw new IllegalArgumentException("grpcTimeoutValue: " + grpcTimeoutValue +
                    " (expected 2-9 characters)");
        }

        // parse ASCII decimal number
        long runningTotal = 0;
        for (int digitIdx = 0; digitIdx < grpcTimeoutValue.length() - 1; digitIdx++) {
            char digitChar = grpcTimeoutValue.charAt(digitIdx);
            if (digitChar < '0' || digitChar > '9') {
                // Bad digit
                throw new NumberFormatException("grpcTimeoutValue: " + grpcTimeoutValue +
                        " (Bad digit '" + digitChar + "')");
            } else {
                runningTotal = runningTotal * 10L + (long) (digitChar - '0');
            }
        }

        // Determine timeout units for conversion to duration
        LongFunction<Duration> toDuration;
        char unitChar = grpcTimeoutValue.charAt(grpcTimeoutValue.length() - 1);
        switch (unitChar) {
            case 'n' :
                toDuration = Duration::ofNanos;
                break;
            case 'u' :
                toDuration = (long micros) -> Duration.of(micros, ChronoUnit.MICROS);
                break;
            case 'm' :
                toDuration = Duration::ofMillis;
                break;
            case 'S' :
                toDuration = Duration::ofSeconds;
                break;
            case 'M' :
                toDuration = Duration::ofMinutes;
                break;
            case 'H' :
                toDuration = Duration::ofHours;
                break;
            default:
                // Unrecognized units or malformed header
                throw new IllegalArgumentException("grpcTimeoutValue: " + grpcTimeoutValue +
                        " (Bad time unit '" + unitChar + "')");
        }

        return toDuration.apply(runningTotal);
    }
}
