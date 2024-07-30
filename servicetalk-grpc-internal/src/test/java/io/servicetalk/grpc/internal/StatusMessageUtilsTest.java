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
package io.servicetalk.grpc.internal;

import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpHeaders;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class StatusMessageUtilsTest {

    private HttpHeaders headers;

    @BeforeEach
    void beforeEach() {
        headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
    }

    @ParameterizedTest
    @MethodSource("messageSamples")
    void testMessageEncoding(String decoded, String encoded) {
        StatusMessageUtils.setStatusMessage(headers, decoded);
        assertEquals(encoded, headers.get(StatusMessageUtils.GRPC_STATUS_MESSAGE));
    }

    @ParameterizedTest
    @MethodSource("messageSamples")
    void testMessageDecoding(String decoded, String encoded) {
        headers.set(StatusMessageUtils.GRPC_STATUS_MESSAGE, encoded);
        assertEquals(decoded, StatusMessageUtils.getStatusMessage(headers));
    }

    @Test
    void testNullMessageDecoding() {
        assertNull(StatusMessageUtils.getStatusMessage(headers));
    }

    /**
     * With a proper encoder this should not happen, but similar to io.grpc if there is a % at the end
     * it is not considered encoded and returned as-is (also see {@link #testInvalidNumberDecoding()} for
     * similar behavior).
     */
    @Test
    void testPercentAtEndMessageDecoding() {
        headers.set(StatusMessageUtils.GRPC_STATUS_MESSAGE, "aa%");
        assertEquals("aa%", StatusMessageUtils.getStatusMessage(headers));

        headers.set(StatusMessageUtils.GRPC_STATUS_MESSAGE, "aa% ");
        assertEquals("aa% ", StatusMessageUtils.getStatusMessage(headers));
    }

    /**
     * According to the spec, invalid values are not discarded:
     * <p>
     * "When decoding invalid values, implementations MUST NOT error or throw away the message. At worst, the
     * implementation can abort decoding the status message altogether such that the user would received the
     * raw percent-encoded form."
     */
    @Test
    void testInvalidNumberDecoding() {
        headers.set(StatusMessageUtils.GRPC_STATUS_MESSAGE, "%z0");
        assertEquals("%z0", StatusMessageUtils.getStatusMessage(headers));

        headers.set(StatusMessageUtils.GRPC_STATUS_MESSAGE, "%7E%z0%7e");
        assertEquals("~%z0~", StatusMessageUtils.getStatusMessage(headers));
    }

    static Stream<Arguments> messageSamples() {
        return Stream.of(
                Arguments.of("abc", "abc"),
                Arguments.of("Hello, World!", "Hello, World!"),
                Arguments.of("a\r\nbc", "a%0D%0Abc"),
                Arguments.of("a%bc", "a%25bc"),
                Arguments.of("~ what? ~", "%7E what? %7E")
        );
    }

}
