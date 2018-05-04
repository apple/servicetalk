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

package io.servicetalk.http.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
public class HttpUriTest {
    @Test
    public void fullHttpURI() {
        final HttpUri hp = new HttpUri("http://apple.com:8080/path/is/here?queryname=value#tag");
        HttpUriTest.verifyAppleString(hp, false, 8080);
    }

    @Test
    public void fullHttpsURI() {
        final HttpUri hp = new HttpUri("https://apple.com:8080/path/is/here?queryname=value#tag");
        HttpUriTest.verifyAppleString(hp, true, 8080);
    }

    @Test
    public void ignoreUserInfo() {
        final HttpUri hp = new HttpUri("http://user:password@apple.com:8080/path/is/here?queryname=value#tag");
        HttpUriTest.verifyAppleString(hp, false, 8080);
    }

    @Test
    public void ignoreUserInfoPctEncoded() {
        final HttpUri hp = new HttpUri("http://user%20:passwo%2Frd@apple.com:8080/path/is/here?queryname=value#tag");
        HttpUriTest.verifyAppleString(hp, false, 8080);
    }

    @Test(expected = IllegalArgumentException.class)
    public void ignoreUserInfoPctEncodedIllegalReservedCharacterWithColon() {
        new HttpUri("http://user:passwo/rd@apple.com:8080/path/is/here?queryname=value#tag");
    }

    @Test(expected = IllegalArgumentException.class)
    public void ignoreUserInfoPctEncodedIllegalReservedCharacter() {
        new HttpUri("http://user/mode@apple.com:8080/path/is/here?queryname=value#tag");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidSchema() {
        new HttpUri("foo://test.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateSchema() {
        new HttpUri("http://foo://test.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptySchema() {
        new HttpUri("://test.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidSchemaToken() {
        new HttpUri("http:/test.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidSchemaToken2() {
        new HttpUri("http:test.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void portTooBig() {
        new HttpUri("foo.com:65536");
    }

    @Test(expected = IllegalArgumentException.class)
    public void portInvalidAtEnd() {
        new HttpUri("foo.com:6553a");
    }

    @Test(expected = IllegalArgumentException.class)
    public void portInvalidBegin() {
        new HttpUri("foo.com:a");
    }

    @Test(expected = IllegalArgumentException.class)
    public void portInvalidMiddle() {
        new HttpUri("foo.com:1ab");
    }

    @Test(expected = IllegalArgumentException.class)
    public void colonInFirstPathComponent() {
        new HttpUri("a.apple.com:81");
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyUserInfoAndAuthorityThrows() {
        new HttpUri("@");
    }

    @Test(expected = IllegalArgumentException.class)
    public void doubleSlashWithEmptyUserInfoAndAuthorityThrows() {
        new HttpUri("//");
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyUserInfoThrows() {
        new HttpUri("user@");
    }

    @Test(expected = IllegalArgumentException.class)
    public void doubleSlashWithEmptyUserInfoThrows() {
        new HttpUri("//user@");
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyAuthorityThrows() {
        new HttpUri("@foo");
    }

    @Test(expected = IllegalArgumentException.class)
    public void doubleSlashWithEmptyAuthorityThrows() {
        new HttpUri("//@foo");
    }

    @Test(expected = IllegalArgumentException.class)
    public void hostWithNoSlashThrows() {
        new HttpUri("user@host");
    }

    @Test
    public void hostLookAlikeInPath() {
        final HttpUri hp = new HttpUri("/@foo");
        assertNull(hp.getHostHeader());
        assertNull(hp.getHost());
        assertEquals("/@foo", hp.getRequestTarget());
        assertEquals("/@foo", hp.getPath());
        assertFalse(hp.isSsl());
        assertEquals(80, hp.getPort());
    }

    @Test
    public void doubleSlashWithPortLookAlike() {
        final HttpUri hp = new HttpUri("//81");
        assertEquals("81", hp.getHostHeader());
        assertEquals("81", hp.getHost());
        assertEquals("", hp.getRequestTarget());
        assertEquals("", hp.getPath());
        assertFalse(hp.isSsl());
        assertEquals(80, hp.getPort());
    }

    @Test
    public void doubleSlashWithoutScheme() {
        final HttpUri hp = new HttpUri("//foo.com/path?query#tag");
        assertEquals("foo.com", hp.getHostHeader());
        assertEquals("foo.com", hp.getHost());
        assertEquals("/path?query#tag", hp.getRequestTarget());
        assertEquals("/path", hp.getPath());
        assertFalse(hp.isSsl());
        assertEquals(HttpUri.DEFAULT_PORT_HTTP, hp.getPort());
    }

    @Test
    public void doubleSlashAfterInitialPath() {
        final HttpUri hp = new HttpUri("f//foo.com/path?query#tag");
        assertNull(hp.getHostHeader());
        assertNull(hp.getHost());
        assertEquals("f//foo.com/path?query#tag", hp.getRequestTarget());
        assertEquals("f//foo.com/path", hp.getPath());
        assertFalse(hp.isSsl());
        assertEquals(HttpUri.DEFAULT_PORT_HTTP, hp.getPort());
    }

    @Test
    public void userInfoAndHostAfterFirstPathComponent() {
        final HttpUri hp = new HttpUri("user/mode@apple.com:8080/path/is/here?queryname=value#tag");
        assertNull(hp.getHostHeader());
        assertNull(hp.getHost());
        assertEquals("user/mode@apple.com:8080/path/is/here?queryname=value#tag", hp.getRequestTarget());
        assertEquals("user/mode@apple.com:8080/path/is/here", hp.getPath());
        assertFalse(hp.isSsl());
        assertEquals(HttpUri.DEFAULT_PORT_HTTP, hp.getPort());
    }

    @Test
    public void hostAfterFirstPathSegment() {
        final HttpUri hp = new HttpUri("user/apple.com/path/is/here?queryname=value#tag");
        assertNull(hp.getHostHeader());
        assertNull(hp.getHost());
        assertEquals("user/apple.com/path/is/here?queryname=value#tag", hp.getRequestTarget());
        assertEquals("user/apple.com/path/is/here", hp.getPath());
        assertFalse(hp.isSsl());
        assertEquals(HttpUri.DEFAULT_PORT_HTTP, hp.getPort());
    }

    @Test
    public void poundEndsRequestTarget() {
        final HttpUri hp = new HttpUri("apple.com#tag");
        assertNull(hp.getHostHeader());
        assertNull(hp.getHost());
        assertEquals("apple.com#tag", hp.getRequestTarget());
        assertEquals("apple.com", hp.getPath());
        assertFalse(hp.isSsl());
        assertEquals(HttpUri.DEFAULT_PORT_HTTP, hp.getPort());
    }

    @Test
    public void schemeWithNoRequestTarget() {
        final HttpUri hp = new HttpUri("http:///");
        assertNull(hp.getHostHeader());
        assertNull(hp.getHost());
        assertEquals("/", hp.getRequestTarget());
        assertEquals("/", hp.getPath());
        assertFalse(hp.isSsl());
        assertEquals(HttpUri.DEFAULT_PORT_HTTP, hp.getPort());
    }

    @Test
    public void schemeWithNoRequestTargetQuery() {
        final HttpUri hp = new HttpUri("http://?");
        assertNull(hp.getHostHeader());
        assertNull(hp.getHost());
        assertEquals("?", hp.getRequestTarget());
        assertEquals("", hp.getPath());
        assertFalse(hp.isSsl());
        assertEquals(HttpUri.DEFAULT_PORT_HTTP, hp.getPort());
    }

    @Test
    public void schemeWithNoRequestTargetPound() {
        final HttpUri hp = new HttpUri("http://#");
        assertNull(hp.getHostHeader());
        assertNull(hp.getHost());
        assertEquals("#", hp.getRequestTarget());
        assertEquals("", hp.getPath());
        assertFalse(hp.isSsl());
        assertEquals(HttpUri.DEFAULT_PORT_HTTP, hp.getPort());
    }

    @Test
    public void hostNameSupplierFallbackIsCalled() {
        final HttpUri hp = new HttpUri("example.com", () -> "example.com");
        assertEquals("example.com", hp.getHostHeader());
        assertEquals("example.com", hp.getHost());
        assertEquals("example.com", hp.getRequestTarget());
        assertEquals("example.com", hp.getPath());
        assertFalse(hp.isSsl());
        assertEquals(HttpUri.DEFAULT_PORT_HTTP, hp.getPort());
    }

    @Test
    public void cssAndQueryIsParsed() {
        final HttpUri hp = new HttpUri("http://localhost:8080/app.css?v1");
        assertEquals("localhost:8080", hp.getHostHeader());
        assertEquals("localhost", hp.getHost());
        assertEquals("/app.css?v1", hp.getRequestTarget());
        assertEquals("/app.css", hp.getPath());
        assertFalse(hp.isSsl());
        assertEquals(8080, hp.getPort());
    }

    @Test
    public void userInfoDelimiterAfterTypicalHost() {
        final HttpUri hp = new HttpUri("https://apple.com:8080@user/path/is/here?queryname=value#tag");
        assertEquals("user", hp.getHostHeader());
        assertEquals("user", hp.getHost());
        assertEquals("/path/is/here?queryname=value#tag", hp.getRequestTarget());
        assertEquals("/path/is/here", hp.getPath());
        assertTrue(hp.isSsl());
        assertEquals(HttpUri.DEFAULT_PORT_HTTPS, hp.getPort());
    }

    @Test
    public void userInfoNoPort() {
        final HttpUri hp = new HttpUri("http://user:foo@apple.com/path/is/here?queryname=value#tag");
        assertEquals("apple.com", hp.getHostHeader());
        assertEquals("apple.com", hp.getHost());
        assertEquals("/path/is/here?queryname=value#tag", hp.getRequestTarget());
        assertEquals("/path/is/here", hp.getPath());
        assertFalse(hp.isSsl());
        assertEquals(HttpUri.DEFAULT_PORT_HTTP, hp.getPort());
    }

    @Test
    public void justSlash() {
        final HttpUri hp = new HttpUri("/");
        assertNull(hp.getHostHeader());
        assertNull(hp.getHost());
        assertEquals("/", hp.getRequestTarget());
        assertEquals("/", hp.getPath());
        assertFalse(hp.isSsl());
        assertEquals(HttpUri.DEFAULT_PORT_HTTP, hp.getPort());
    }

    @Test
    public void justPath() {
        final HttpUri hp = new HttpUri("/path/is/here?queryname=value#tag");
        assertNull(hp.getHostHeader());
        assertNull(hp.getHost());
        assertEquals("/path/is/here?queryname=value#tag", hp.getRequestTarget());
        assertEquals("/path/is/here", hp.getPath());
        assertFalse(hp.isSsl());
        assertEquals(HttpUri.DEFAULT_PORT_HTTP, hp.getPort());
    }

    @Test
    public void schemeAuthority() {
        final HttpUri hp = new HttpUri("http://localhost:80");
        assertEquals("localhost:80", hp.getHostHeader());
        assertEquals("localhost", hp.getHost());
        assertEquals("", hp.getRequestTarget());
        assertEquals("", hp.getPath());
        assertFalse(hp.isSsl());
        assertEquals(80, hp.getPort());
    }

    @Test
    public void schemeAuthorityQuery() {
        final HttpUri hp = new HttpUri("http://localhost:8080?foo");
        assertEquals("localhost:8080", hp.getHostHeader());
        assertEquals("localhost", hp.getHost());
        assertEquals("?foo", hp.getRequestTarget());
        assertEquals("", hp.getPath());
        assertFalse(hp.isSsl());
        assertEquals(8080, hp.getPort());
    }

    @Test
    public void schemeAuthorityEmptyQuery() {
        final HttpUri hp = new HttpUri("http://localhost:8080?");
        assertEquals("localhost:8080", hp.getHostHeader());
        assertEquals("localhost", hp.getHost());
        assertEquals("?", hp.getRequestTarget());
        assertEquals("", hp.getPath());
        assertFalse(hp.isSsl());
        assertEquals(8080, hp.getPort());
    }

    @Test
    public void schemeAuthorityTag() {
        final HttpUri hp = new HttpUri("http://localhost:8080#foo");
        assertEquals("localhost:8080", hp.getHostHeader());
        assertEquals("localhost", hp.getHost());
        assertEquals("#foo", hp.getRequestTarget());
        assertEquals("", hp.getPath());
        assertFalse(hp.isSsl());
        assertEquals(8080, hp.getPort());
    }

    @Test
    public void schemeAuthorityEmptyTag() {
        final HttpUri hp = new HttpUri("http://localhost:8080#");
        assertEquals("localhost:8080", hp.getHostHeader());
        assertEquals("localhost", hp.getHost());
        assertEquals("#", hp.getRequestTarget());
        assertEquals("", hp.getPath());
        assertFalse(hp.isSsl());
        assertEquals(8080, hp.getPort());
    }

    @Test
    public void schemeAuthorityEmptyQueryEmptyTag() {
        final HttpUri hp = new HttpUri("http://localhost:8080?#");
        assertEquals("localhost:8080", hp.getHostHeader());
        assertEquals("localhost", hp.getHost());
        assertEquals("?#", hp.getRequestTarget());
        assertEquals("", hp.getPath());
        assertFalse(hp.isSsl());
        assertEquals(8080, hp.getPort());
    }

    @Test
    public void httpsNoPort() {
        final HttpUri hp = new HttpUri("https://tools.ietf.org/html/rfc3986#section-3");
        assertEquals("tools.ietf.org", hp.getHostHeader());
        assertEquals("tools.ietf.org", hp.getHost());
        assertEquals("/html/rfc3986#section-3", hp.getRequestTarget());
        assertEquals("/html/rfc3986", hp.getPath());
        assertTrue(hp.isSsl());
        assertEquals(HttpUri.DEFAULT_PORT_HTTPS, hp.getPort());
    }

    @Test
    public void ipv4LiteralWithPort() {
        final HttpUri hp = new HttpUri("https://foo:goo@123.456.234.122:9");
        assertEquals("123.456.234.122:9", hp.getHostHeader());
        assertEquals("123.456.234.122", hp.getHost());
        assertEquals("", hp.getRequestTarget());
        assertEquals("", hp.getPath());
        assertTrue(hp.isSsl());
        assertEquals(9, hp.getPort());
    }

    @Test
    public void ipv4LiteralWithOutPort() {
        final HttpUri hp = new HttpUri("https://foo:goo@123.456.234.122");
        assertEquals("123.456.234.122", hp.getHostHeader());
        assertEquals("123.456.234.122", hp.getHost());
        assertEquals("", hp.getRequestTarget());
        assertEquals("", hp.getPath());
        assertTrue(hp.isSsl());
        assertEquals(HttpUri.DEFAULT_PORT_HTTPS, hp.getPort());
    }

    @Test
    public void ipv6LiteralWithPort() {
        final HttpUri hp = new HttpUri("https://foo:goo@[::1]:988");
        assertEquals("[::1]:988", hp.getHostHeader());
        assertEquals("[::1]", hp.getHost());
        assertEquals("", hp.getRequestTarget());
        assertEquals("", hp.getPath());
        assertTrue(hp.isSsl());
        assertEquals(988, hp.getPort());
    }

    @Test
    public void maxPortTest() {
        final HttpUri hp = new HttpUri("https://foo:goo@[::1]:65535");
        assertEquals("[::1]:65535", hp.getHostHeader());
        assertEquals("[::1]", hp.getHost());
        assertEquals("", hp.getRequestTarget());
        assertEquals("", hp.getPath());
        assertTrue(hp.isSsl());
        assertEquals(65535, hp.getPort());
    }

    @Test
    public void ipv6LiteralWithOutPort() {
        final HttpUri hp = new HttpUri("https://foo:goo@[::1]");
        assertEquals("[::1]", hp.getHostHeader());
        assertEquals("[::1]", hp.getHost());
        assertEquals("", hp.getRequestTarget());
        assertEquals("", hp.getPath());
        assertTrue(hp.isSsl());
        assertEquals(HttpUri.DEFAULT_PORT_HTTPS, hp.getPort());
    }

    @Test
    public void ipv4HostHeaderNoPort() {
        final HttpUri hp = new HttpUri("/path?param=value", () -> "1.2.3.4");
        assertEquals("1.2.3.4", hp.getHostHeader());
        assertEquals("1.2.3.4", hp.getHost());
        assertEquals(80, hp.getPort());
        assertEquals("/path?param=value", hp.getRequestTarget());
        assertEquals("/path", hp.getPath());
        assertFalse(hp.isSsl());
    }

    @Test
    public void ipv4HostHeaderWithPort() {
        final HttpUri hp = new HttpUri("/path?param=value", () -> "1.2.3.4:2834");
        assertEquals("1.2.3.4:2834", hp.getHostHeader());
        assertEquals("1.2.3.4", hp.getHost());
        assertEquals(2834, hp.getPort());
        assertEquals("/path?param=value", hp.getRequestTarget());
        assertEquals("/path", hp.getPath());
        assertFalse(hp.isSsl());
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv4HostHeaderWithInvalidPort() {
        new HttpUri("/path?param=value", () -> "1.2.3.4:65536");
    }

    @Test
    public void ipv6HostHeaderNoPort() {
        final HttpUri hp = new HttpUri("/path?param=value", () -> "[::1]");
        assertEquals("[::1]", hp.getHostHeader());
        assertEquals("[::1]", hp.getHost());
        assertEquals(80, hp.getPort());
        assertEquals("/path?param=value", hp.getRequestTarget());
        assertEquals("/path", hp.getPath());
        assertFalse(hp.isSsl());
    }

    @Test
    public void ipv6HostHeaderWithPort() {
        final HttpUri hp = new HttpUri("/path?param=value", () -> "[::1]:8080");
        assertEquals("[::1]:8080", hp.getHostHeader());
        assertEquals("[::1]", hp.getHost());
        assertEquals(8080, hp.getPort());
        assertEquals("/path?param=value", hp.getRequestTarget());
        assertEquals("/path", hp.getPath());
        assertFalse(hp.isSsl());
    }

    @Test
    public void ipv6HostHeaderWithScope() {
        final HttpUri hp = new HttpUri("/path?param=value", () -> "[12:3::1%2]:8081");
        assertEquals("[12:3::1%2]:8081", hp.getHostHeader());
        assertEquals("[12:3::1%2]", hp.getHost());
        assertEquals(8081, hp.getPort());
        assertEquals("/path?param=value", hp.getRequestTarget());
        assertEquals("/path", hp.getPath());
        assertFalse(hp.isSsl());
    }

    @Test
    public void stringAddressHostHeader() {
        final HttpUri hp = new HttpUri("/path?param=value", () -> "apple.com:8081");
        assertEquals("apple.com:8081", hp.getHostHeader());
        assertEquals("apple.com", hp.getHost());
        assertEquals(8081, hp.getPort());
        assertEquals("/path?param=value", hp.getRequestTarget());
        assertEquals("/path", hp.getPath());
        assertFalse(hp.isSsl());
    }

    @Test
    public void literalAddressHostHeader() {
        final HttpUri hp = new HttpUri("/path?param=value", () -> "apple:8081");
        assertEquals("apple:8081", hp.getHostHeader());
        assertEquals("apple", hp.getHost());
        assertEquals(8081, hp.getPort());
        assertEquals("/path?param=value", hp.getRequestTarget());
        assertEquals("/path", hp.getPath());
        assertFalse(hp.isSsl());
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv6HostHeaderNoPortTrailingColon() {
        new HttpUri("/path?param=value", () -> "[12:3::1%2]:");
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv6NonBracketWithScopeHostHeader() {
        // https://tools.ietf.org/html/rfc3986#section-3.2.2
        // IPv6 + future must be enclosed in []
        new HttpUri("/path?param=value", () -> "0:0:0:0:0:0:0:0%0:49178");
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv6NonBracketHostHeader() {
        new HttpUri("/path?param=value", () -> "::1");
    }

    private static void verifyAppleString(final HttpUri hp, final boolean isSsl, final int port) {
        assertEquals("apple.com:8080", hp.getHostHeader());
        assertEquals("apple.com", hp.getHost());
        assertEquals("/path/is/here?queryname=value#tag", hp.getRequestTarget());
        assertEquals("/path/is/here", hp.getPath());
        assertEquals(isSsl, hp.isSsl());
        assertEquals(port, hp.getPort());
    }
}
