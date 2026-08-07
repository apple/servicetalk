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
package io.servicetalk.http.netty;

import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * HTTP/2 field-name validation is split across two layers (see the comments on
 * {@code ServiceTalkHttp2Headers.HTTP2_NAME_VALIDATOR}): the wrapper enforces the HTTP/2 lower-case rule and the
 * underlying {@code HttpHeaders} enforces the RFC 7230 token grammar. These tests pin which layer rejects what;
 * over the wire both are normalised to a PROTOCOL_ERROR by Netty's HpackDecoder (see
 * {@code H2PriorKnowledgeFeatureParityTest#h2ServerResetsStreamForInvalidHeaderName}).
 */
class ServiceTalkHttp2HeadersTest {

    // Mirror the inbound decode path, where the wrapper and its underlying HttpHeaders share one validateNames flag.
    private static Http2Headers newHeaders(final boolean validateNames) {
        return new ServiceTalkHttp2Headers(new H2HeadersFactory(validateNames, false, false).newHeaders(),
                false, validateNames, false);
    }

    @Test
    void wrapperRejectsUpperCaseName() {
        // Layer 1: the HTTP/2 lower-case rule is enforced by ServiceTalkHttp2Headers itself (Http2Exception).
        assertThrows(Http2Exception.class, () -> newHeaders(true).add("X-Uppercase", "v"));
        assertThrows(Http2Exception.class, () -> newHeaders(true).add(new AsciiString("X-Uppercase"), "v"));
    }

    @ParameterizedTest(name = "{displayName} [{index}] name={0}")
    @ValueSource(strings = {"invalid name", "invalid;name", "invalid/name", "invalid@name", "invalid(name)"})
    void underlyingRejectsNonTokenName(final String name) {
        // Layer 2: non-token characters (SP, separators, controls) are rejected by the underlying HttpHeaders via
        // HeaderUtils.validateToken, surfacing as IllegalCharacterException (an IllegalArgumentException).
        assertThrows(IllegalArgumentException.class, () -> newHeaders(true).add(name, "v"));
    }

    @Test
    void underlyingRejectsNonAsciiName() {
        assertThrows(IllegalArgumentException.class,
                () -> newHeaders(true).add(new AsciiString(new byte[]{(byte) 0xF0}), "v"));
    }

    @Test
    void acceptsValidLowerCaseTokenName() {
        assertDoesNotThrow(() -> newHeaders(true).add("x-custom-header", "v"));
        // Every special token character permitted by RFC 7230.
        assertDoesNotThrow(() -> newHeaders(true).add("!#$%&'*+-.^_`|~", "v"));
    }

    @Test
    void skipsNameValidationWhenDisabled() {
        // With validation off on both layers, malformed names are accepted (the opt-out used on the outbound path,
        // where names were already validated at the HTTP API layer).
        assertDoesNotThrow(() -> newHeaders(false).add("Uppercase-And-Space Name", "v"));
        assertDoesNotThrow(() -> newHeaders(false).add(new AsciiString(new byte[]{(byte) 0xF0}), "v"));
    }

    /**
     * Pseudo-header values live in raw fields rather than in the underlying {@code HttpHeaders}, so they skip its
     * validation, and the container's own check is gated on {@code validateValues}, which is {@code false} on both
     * real paths. That is why {@code :path} validation lives in the duplex handlers.
     */
    @Test
    void pseudoHeaderValuesAreNotValidatedByTheContainer() {
        assertDoesNotThrow(() -> newHeaders(true).path("/a" + (char) 0x0d + (char) 0x0a + "X: y"));
        assertDoesNotThrow(() -> newHeaders(true).method("GET" + (char) 0x00));
    }

    /**
     * Turning {@code validateValues} on would still not cover {@code :path}: it enforces the generic
     * {@code field-value} grammar of RFC 9110, 5.5, which permits obs-text and interior SP. Both values below are
     * legal field values yet illegal in the RFC 9113, 8.3.1 {@code absolute-path [ "?" query ]} production.
     */
    @Test
    void valueValidationIsTooWeakForPath() {
        final Http2Headers validating = new ServiceTalkHttp2Headers(
                new H2HeadersFactory(true, false, true).newHeaders(), false, true, true);
        // Sanity check that the flag is on: CR is prohibited even by the generic grammar.
        assertThrows(IllegalArgumentException.class, () -> validating.path("/a" + (char) 0x0d + "b"));
        assertDoesNotThrow(() -> validating.path("/a b"));
        assertDoesNotThrow(() -> validating.path("/a" + (char) 0xe9 + "b"));
    }
}
