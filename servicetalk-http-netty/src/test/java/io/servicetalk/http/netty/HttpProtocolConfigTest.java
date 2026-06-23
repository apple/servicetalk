/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.transport.api.HostAndPort;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.function.BiPredicate;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpProtocolConfigTest {

    private static final HttpProtocolConfig UNKNOWN_CONFIG = new HttpProtocolConfig() {

        @Override
        public String alpnId() {
            return "unknown";
        }

        @Override
        public HttpHeadersFactory headersFactory() {
            return DefaultHttpHeadersFactory.INSTANCE;
        }
    };

    @Test
    void clientDoesNotSupportH2cUpgrade() {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("localhost", 8080)
                        .protocols(h2Default(), h1Default());

        Exception e = assertThrows(IllegalStateException.class, builder::build);
        assertEquals("Cleartext HTTP/1.1 -> HTTP/2 (h2c) upgrade is not supported", e.getMessage());
    }

    @Test
    void serverDoesNotSupportH2cUpgrade() {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0))
                .protocols(h2Default(), h1Default());

        Exception e = assertThrows(IllegalStateException.class, () ->
            builder.listenBlocking((ctx, request, responseFactory) -> responseFactory.noContent()));
        assertEquals("Cleartext HTTP/1.1 -> HTTP/2 (h2c) upgrade is not supported", e.getMessage());
    }

    @Test
    void clientWithNullProtocolConfig() {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("localhost", 8080);

        assertThrows(NullPointerException.class, () -> builder.protocols((HttpProtocolConfig) null));
    }

    @Test
    void serverWithNullProtocolConfig() {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0));

        assertThrows(NullPointerException.class, () -> builder.protocols((HttpProtocolConfig) null));
    }

    @Test
    void clientWithEmptyProtocolConfig() {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("localhost", 8080);

        Exception e = assertThrows(IllegalArgumentException.class, () ->
                builder.protocols(new HttpProtocolConfig[0]));
        assertEquals("No protocols specified", e.getMessage());
    }

    @Test
    void serverWithEmptyProtocolConfig() {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0));

        Exception e = assertThrows(IllegalArgumentException.class, () ->
            builder.protocols(new HttpProtocolConfig[0]));
        assertEquals("No protocols specified", e.getMessage());
    }

    @Test
    void clientWithUnsupportedProtocolConfig() {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("localhost", 8080);

        Exception e = assertThrows(IllegalArgumentException.class, () ->
            builder.protocols(UNKNOWN_CONFIG));
        assertThat(e.getMessage(), startsWith("Unsupported HttpProtocolConfig"));
    }

    @Test
    void serverWithUnsupportedProtocolConfig() {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0));

        Exception e = assertThrows(IllegalArgumentException.class, () ->
            builder.protocols(UNKNOWN_CONFIG));
        assertThat(e.getMessage(), startsWith("Unsupported HttpProtocolConfig"));
    }

    @Test
    void clientWithDuplicatedH1ProtocolConfig() {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("localhost", 8080);

        Exception e = assertThrows(IllegalArgumentException.class, () ->
            builder.protocols(h1Default(), h1Default()));
        assertThat(e.getMessage(), startsWith("Duplicated configuration"));
    }

    @Test
    void clientWithDuplicatedH2ProtocolConfig() {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("localhost", 8080);

        Exception e = assertThrows(IllegalArgumentException.class, () ->
            builder.protocols(h2Default(), h2Default()));
        assertThat(e.getMessage(), startsWith("Duplicated configuration"));
    }

    @Test
    void serverWithDuplicatedH1ProtocolConfig() {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0));

        Exception e = assertThrows(IllegalArgumentException.class, () ->
            builder.protocols(h1Default(), h1Default()));
        assertThat(e.getMessage(), startsWith("Duplicated configuration"));
    }

    @Test
    void serverWithDuplicatedH2ProtocolConfig() {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0));

        Exception e = assertThrows(IllegalArgumentException.class, () ->
            builder.protocols(h2Default(), h2Default()));
        assertThat(e.getMessage(), startsWith("Duplicated configuration"));
    }

    @Test
    void maxTotalHeaderFieldsLengthAdjustedWhenFieldLengthIsHigher() {
        H1ProtocolConfig config = h1()
                .maxHeaderFieldLength(64 * 1024)
                .build();

        assertEquals(64 * 1024, config.maxHeaderFieldLength());
        assertEquals(64 * 1024, config.maxTotalHeaderFieldsLength());
    }

    @Test
    void maxTotalHeaderFieldsLengthAdjustedWhenExplicitlySetLower() {
        H1ProtocolConfig config = h1()
                .maxHeaderFieldLength(64 * 1024)
                .maxTotalHeaderFieldsLength(16 * 1024)
                .build();

        assertEquals(64 * 1024, config.maxHeaderFieldLength());
        assertEquals(64 * 1024, config.maxTotalHeaderFieldsLength());
    }

    @Test
    void maxTotalHeaderFieldsLengthNotAdjustedWhenAlreadySufficient() {
        H1ProtocolConfig config = h1()
                .maxHeaderFieldLength(16 * 1024)
                .maxTotalHeaderFieldsLength(64 * 1024)
                .build();

        assertEquals(16 * 1024, config.maxHeaderFieldLength());
        assertEquals(64 * 1024, config.maxTotalHeaderFieldsLength());
    }

    @Test
    void maxTotalHeaderFieldsLengthWorksWhenEqual() {
        H1ProtocolConfig config = h1()
                .maxHeaderFieldLength(32 * 1024)
                .maxTotalHeaderFieldsLength(32 * 1024)
                .build();

        assertEquals(32 * 1024, config.maxHeaderFieldLength());
        assertEquals(32 * 1024, config.maxTotalHeaderFieldsLength());
    }

    @Test
    void defaultDetectorMarksSensitiveHeaders() {
        BiPredicate<CharSequence, CharSequence> detector = bothCharSequenceTypes(
                h2Default().headersSensitivityDetector());
        assertTrue(detector.test("authorization", "Bearer abc"));
        assertTrue(detector.test("cookie", "sid=1"));
        assertTrue(detector.test("set-cookie", "sid=1; Path=/"));
        assertTrue(detector.test("set-cookie2", "sid=1; Path=/"));
        assertTrue(detector.test("proxy-authorization", "Basic xyz"));
        assertTrue(detector.test("x-original-authorization", "Basic xyz"));
        assertTrue(detector.test("x-forwarded-authorization", "Basic xyz"));
        assertTrue(detector.test("x-auth-token", "tkn"));
        assertTrue(detector.test("x-access-token", "tkn"));
        assertTrue(detector.test("token", "x"));
        assertTrue(detector.test("x-token-id", "tid"));
        assertTrue(detector.test("x-csrf-token", "csrf-tkn"));
        assertTrue(detector.test("www-authenticate", "abc"));
        assertTrue(detector.test("proxy-authenticate", "abc"));
        assertTrue(detector.test("abc-signature", "abc"));
        assertTrue(detector.test("x-api-key", "k"));
        assertTrue(detector.test("api-key", "k"));
        // Canonical DPoP proof JWT from RFC 9449 §4.2 (Figure 2).
        assertTrue(detector.test("DPoP", "eyJ0eXAiOiJkcG9wK2p3dCIsImFsZyI6IkVTMjU2IiwiandrIjp7Imt0eSI6Ik" +
                "VDIiwieCI6Imw4dEZyaHgtMzR0VjNoUklDUkRZOXpDa0RscEJoRjQyVVFVZldWQVdCR" +
                "nMiLCJ5IjoiOVZFNGpmX09rX282NHpiVFRsY3VOSmFqSG10NnY5VERWclUwQ2R2R1JE" +
                "QSIsImNydiI6IlAtMjU2In19.eyJqdGkiOiItQndDM0VTYzZhY2MybFRjIiwiaHRtIj" +
                "oiUE9TVCIsImh0dSI6Imh0dHBzOi8vc2VydmVyLmV4YW1wbGUuY29tL3Rva2VuIiwia" +
                "WF0IjoxNTYyMjY1Mjk2fQ.pAqut2IRDm_De6PR93SYmGBPXpwrAk90e8cP2hjiaG5Qs" +
                "GSuKDYW7_X620BxqhvYC8ynrrvZLTk41mSRroapUA"));
        assertTrue(detector.test("dpop", "abc"));
        assertTrue(detector.test("dpop-nonce", "abc"));
    }

    @Test
    void defaultDetectorIgnoresNonSensitiveHeaders() {
        BiPredicate<CharSequence, CharSequence> detector = bothCharSequenceTypes(
                h2Default().headersSensitivityDetector());
        assertFalse(detector.test("", ""));
        assertFalse(detector.test("content-type", "application/json"));
        assertFalse(detector.test("user-agent", "ServiceTalk"));
        assertFalse(detector.test("accept", "*/*"));
        assertFalse(detector.test("x-request-id", "req-1"));
        assertFalse(detector.test("authorization-extra", "x"));
        assertFalse(detector.test("abc-dpop-nonce", "abc"));
        assertFalse(detector.test("abc-dpop-nonce-abc", "abc"));
    }

    @Test
    void defaultDetectorIsCaseInsensitive() {
        BiPredicate<CharSequence, CharSequence> detector = bothCharSequenceTypes(
                h2Default().headersSensitivityDetector());
        assertTrue(detector.test("AUTHORIZATION", "Bearer abc"));
        assertTrue(detector.test("Authorization", "Bearer abc"));
        assertTrue(detector.test("proxy-authorization", "abc"));
        assertTrue(detector.test("x-original-authorization", "abc"));
        assertTrue(detector.test("x-forwarded-authorization", "abc"));
        assertTrue(detector.test("Set-Cookie", "sid=1; Path=/"));
        assertTrue(detector.test("X-Auth-Token", "tkn"));
        assertTrue(detector.test("X-TOKEN-ID", "tid"));
        assertTrue(detector.test("DPOP", "abc"));
    }

    @Test
    void customDetectorOverridesDefault() {
        BiPredicate<CharSequence, CharSequence> custom = (name, value) -> false;
        BiPredicate<CharSequence, CharSequence> configured = h2()
                .headersSensitivityDetector(custom)
                .build()
                .headersSensitivityDetector();

        assertThat(configured, is(sameInstance(custom)));
        assertFalse(configured.test("authorization", "Bearer abc"));
        assertFalse(configured.test("cookie", "sid=1"));
    }

    private static BiPredicate<CharSequence, CharSequence> bothCharSequenceTypes(
            final BiPredicate<CharSequence, CharSequence> detector) {
        return (name, value) -> {
            final boolean asIs = detector.test(name, value);
            final boolean asAscii = detector.test(
                    newAsciiString(name.toString()), newAsciiString(value.toString()));
            assertEquals(asIs, asAscii,
                    () -> "detector disagreed for String vs AsciiString: name=" + name + ", value=" + value);
            return asIs;
        };
    }
}
