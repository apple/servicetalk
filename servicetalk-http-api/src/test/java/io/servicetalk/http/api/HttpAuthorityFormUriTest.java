/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import org.junit.Test;

import static io.servicetalk.http.api.Uri3986Test.verifyUri;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
public class HttpAuthorityFormUriTest {
    @Test
    public void regNameAndPort() {
        verifyAuthForm("www.example.com:80", "www.example.com", 80);
    }

    @Test
    public void regName() {
        verifyAuthForm("www.example.com", "www.example.com", -1);
    }

    @Test
    public void ipv6AndPort() {
        verifyAuthForm("[::1]:8080", "[::1]", 8080);
    }

    @Test
    public void ipv6() {
        verifyAuthForm("[af::98]", "[af::98]", -1);
    }

    @Test
    public void ipv4AndPort() {
        verifyAuthForm("1.2.3.4:8080", "1.2.3.4", 8080);
    }

    @Test
    public void ipv4() {
        verifyAuthForm("244.244.244.244", "244.244.244.244", -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv6InvalidNegativePort() {
        new HttpAuthorityFormUri("[::1]:-1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv6InvalidLargePort() {
        new HttpAuthorityFormUri("[::1]:65536");
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv6InvalidNoCloseBracketNoPort() {
        new HttpAuthorityFormUri("[::1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv6NonBracketWithScope() {
        // https://tools.ietf.org/html/rfc3986#section-3.2.2
        // IPv6 + future must be enclosed in []
        new HttpAuthorityFormUri("0:0:0:0:0:0:0:0%0:49178");
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv6InvalidNoCloseBracketWithPort() {
        new HttpAuthorityFormUri("[::1:65536");
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv6ContentBeforePort() {
        new HttpAuthorityFormUri("[::1]foo:8080");
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv6ContentAfterPort() {
        new HttpAuthorityFormUri("[::1]:8080foo");
    }

    @Test
    public void encodeTouchesAllComponents() {
        verifyEncodeDecode("www.foo bar.com:8080", "www.foo%20bar.com:8080");
    }

    @Test
    public void encodeIPv6() {
        verifyEncodeDecode("[::1]:8080");
    }

    @Test
    public void encodeIPv6WithScope() {
        verifyEncodeDecode("[::1%29]:8080");
    }

    @Test
    public void encodeIPv4() {
        verifyEncodeDecode("1.2.3.4:8080");
    }

    private static void verifyEncodeDecode(String decoded) {
        verifyEncodeDecode(decoded, decoded);
    }

    private static void verifyEncodeDecode(String decoded, String encoded) {
        assertEquals(encoded, HttpAuthorityFormUri.encode(decoded, UTF_8));
        assertEquals(decoded, HttpAuthorityFormUri.decode(encoded, UTF_8));
    }

    private static void verifyAuthForm(String expectedUri, String expectedHost, int port) {
        verifyUri(new HttpAuthorityFormUri(expectedUri), expectedUri, null, null, expectedHost,
                port, "", "", null, null, null);
    }
}
