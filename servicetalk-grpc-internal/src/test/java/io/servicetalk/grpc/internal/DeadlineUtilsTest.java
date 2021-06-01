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
package io.servicetalk.grpc.internal;

import io.servicetalk.buffer.api.CharSequences;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.stream.Stream;

import static io.servicetalk.grpc.internal.DeadlineUtils.EIGHT_NINES;
import static io.servicetalk.grpc.internal.DeadlineUtils.GRPC_MAX_TIMEOUT;
import static io.servicetalk.grpc.internal.DeadlineUtils.makeTimeoutHeader;
import static io.servicetalk.grpc.internal.DeadlineUtils.parseTimeoutHeader;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadlineUtilsTest {

    private static final Duration EXCESSIVE_TIMEOUT = GRPC_MAX_TIMEOUT.plusNanos(1);
    private static final Duration INFINITE_TIMEOUT = Duration.ofHours(EIGHT_NINES + 1);

    @SuppressWarnings("unused")
    private static Stream<Arguments> testMakeTimeoutHeader() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of(EXCESSIVE_TIMEOUT, null),
                Arguments.of(INFINITE_TIMEOUT, null),
                Arguments.of(Duration.ofNanos(-1), "0n"),
                Arguments.of(Duration.ZERO, "0n"),
                Arguments.of(Duration.ofNanos(1), "1n"),
                Arguments.of(Duration.ofNanos(MICROSECONDS.toNanos(1)), "1u"),
                Arguments.of(Duration.ofMillis(1), "1m"),
                Arguments.of(Duration.ofSeconds(1), "1S"),
                Arguments.of(Duration.ofMinutes(1), "1M"),
                Arguments.of(Duration.ofHours(1), "1H"),
                Arguments.of(GRPC_MAX_TIMEOUT, EIGHT_NINES + "H"));
    }

    @ParameterizedTest
    @MethodSource
    void testMakeTimeoutHeader(Duration duration, CharSequence expected) {
        CharSequence result = makeTimeoutHeader(duration);
        assertTrue(CharSequences.contentEquals(expected, result),
                () -> "Expected: '" + expected + "' Found: '" + result + "'");
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> testParseTimeoutHeader() {
        return Stream.of(
                Arguments.of(Duration.ZERO, "0n"),
                Arguments.of(Duration.ofNanos(1), "1n"),
                Arguments.of(Duration.ofNanos(MICROSECONDS.toNanos(1)), "1u"),
                Arguments.of(Duration.ofMillis(1), "1m"),
                Arguments.of(Duration.ofSeconds(1), "1S"),
                Arguments.of(Duration.ofMinutes(1), "1M"),
                Arguments.of(Duration.ofHours(1), "1H"),
                Arguments.of(GRPC_MAX_TIMEOUT, EIGHT_NINES + "H"));
    }

    @ParameterizedTest
    @MethodSource
    void testParseTimeoutHeader(Duration expected, CharSequence headerValue) {
        Duration timeout = parseTimeoutHeader(headerValue);
        assertEquals(expected, timeout);
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> testRoundTripping() {
        return Stream.of(
                Arguments.of("0n"),
                Arguments.of("1n"),
                Arguments.of("1u"),
                Arguments.of("1m"),
                Arguments.of("1S"),
                Arguments.of("1M"),
                Arguments.of("1H"),
                Arguments.of(EIGHT_NINES + "H"));
    }

    @ParameterizedTest
    @MethodSource
    void testRoundTripping(CharSequence expected) {
        Duration parsed = parseTimeoutHeader(expected);
        CharSequence result = makeTimeoutHeader(parsed);
        assertTrue(CharSequences.contentEquals(expected, result),
                () -> "Expected: '" + expected + "' Found: '" + result + "'");
    }
}
