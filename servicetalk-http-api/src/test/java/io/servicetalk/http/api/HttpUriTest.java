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

import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpUri.DEFAULT_PORT_HTTP;
import static io.servicetalk.http.api.HttpUri.DEFAULT_PORT_HTTPS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
public class HttpUriTest {
    @Test
    public void fullHttpURI() {
        final HttpUri hp = new HttpUri("http://apple.com:8080/path/is/here?queryname=value#tag");
        verifyAppleString(hp, false, 8080, null);
    }

    @Test
    public void fullHttpsURI() {
        final HttpUri hp = new HttpUri("https://apple.com:8080/path/is/here?queryname=value#tag");
        verifyAppleString(hp, true, 8080, null);
    }

    @Test
    public void ignoreUserInfo() {
        final HttpUri hp = new HttpUri("http://user:password@apple.com:8080/path/is/here?queryname=value#tag");
        verifyAppleString(hp, false, 8080, "user:password");
    }

    @Test
    public void ignoreUserInfoPctEncoded() {
        final HttpUri hp = new HttpUri("http://user%20:passwo%2Frd@apple.com:8080/path/is/here?queryname=value#tag");
        verifyAppleString(hp, false, 8080, "user%20:passwo%2Frd");
    }

    @Test
    public void schemeIsParsedCaseInsensitively() {
        HttpUri hp = new HttpUri("HTTP://apple.com:8080/path/is/here?queryname=value#tag");
        verifyAppleString(hp, false, 8080, null);

        hp = new HttpUri("HTTPS://apple.com:8080/path/is/here?queryname=value#tag");
        verifyAppleString(hp, true, 8080, null);
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
    public void authorityForm() {
        final HttpUri hp = new HttpUri("a.apple.com:81");
        assertNull(hp.scheme());
        assertEquals("a.apple.com:81", hp.hostHeader());
        assertEquals("a.apple.com", hp.host());
        assertEquals("", hp.relativeReference());
        assertEquals("", hp.rawPath());
        assertEquals("", hp.path());
        assertEquals("", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(81, hp.port());
    }

    @Test
    public void badAuthorityForm() {
        final HttpUri hp = new HttpUri("a.apple.com:81/should_be_ignored");
        assertNull(hp.scheme());
        assertEquals("a.apple.com:81", hp.hostHeader());
        assertEquals("a.apple.com", hp.host());
        assertEquals("", hp.relativeReference());
        assertEquals("", hp.rawPath());
        assertEquals("", hp.path());
        assertEquals("", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(81, hp.port());
    }

    @Test
    public void hostLookAlikeInPath() {
        final HttpUri hp = new HttpUri("/@foo");
        assertNull(hp.scheme());
        assertNull(hp.hostHeader());
        assertNull(hp.host());
        assertEquals("/@foo", hp.relativeReference());
        assertEquals("/@foo", hp.rawPath());
        assertEquals("/@foo", hp.path());
        assertEquals("", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(80, hp.port());
    }

    @Test
    public void doubleSlashWithPortLookAlike() {
        final HttpUri hp = new HttpUri("//81");
        assertNull(hp.scheme());
        assertEquals("81", hp.hostHeader());
        assertEquals("81", hp.host());
        assertEquals("", hp.relativeReference());
        assertEquals("", hp.rawPath());
        assertEquals("", hp.path());
        assertEquals("", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(80, hp.port());
    }

    @Test
    public void doubleSlashWithoutScheme() {
        final HttpUri hp = new HttpUri("//foo.com/path?query#tag");
        assertNull(hp.scheme());
        assertEquals("foo.com", hp.hostHeader());
        assertEquals("foo.com", hp.host());
        assertEquals("/path?query#tag", hp.relativeReference());
        assertEquals("/path", hp.rawPath());
        assertEquals("/path", hp.path());
        assertEquals("query", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(DEFAULT_PORT_HTTP, hp.port());
    }

    @Test
    public void doubleSlashAfterInitialPath() {
        final HttpUri hp = new HttpUri("f//foo.com/path?query#tag");
        assertNull(hp.scheme());
        assertNull(hp.hostHeader());
        assertNull(hp.host());
        assertEquals("f//foo.com/path?query#tag", hp.relativeReference());
        assertEquals("f//foo.com/path", hp.rawPath());
        assertEquals("f//foo.com/path", hp.path());
        assertEquals("query", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(DEFAULT_PORT_HTTP, hp.port());
    }

    @Test
    public void userInfoAndHostAfterFirstPathComponent() {
        final HttpUri hp = new HttpUri("user/mode@apple.com:8080/path/is/here?queryname=value#tag");
        assertNull(hp.scheme());
        assertNull(hp.hostHeader());
        assertNull(hp.host());
        assertEquals("user/mode@apple.com:8080/path/is/here?queryname=value#tag", hp.relativeReference());
        assertEquals("user/mode@apple.com:8080/path/is/here", hp.rawPath());
        assertEquals("user/mode@apple.com:8080/path/is/here", hp.path());
        assertEquals("queryname=value", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(DEFAULT_PORT_HTTP, hp.port());
    }

    @Test
    public void hostAfterFirstPathSegment() {
        final HttpUri hp = new HttpUri("user/apple.com/path/is/here?queryname=value#tag");
        assertNull(hp.scheme());
        assertNull(hp.hostHeader());
        assertNull(hp.host());
        assertEquals("user/apple.com/path/is/here?queryname=value#tag", hp.relativeReference());
        assertEquals("user/apple.com/path/is/here", hp.rawPath());
        assertEquals("user/apple.com/path/is/here", hp.path());
        assertEquals("queryname=value", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(DEFAULT_PORT_HTTP, hp.port());
    }

    @Test
    public void poundEndsRequestTarget() {
        final HttpUri hp = new HttpUri("apple.com#tag");
        assertNull(hp.scheme());
        assertNull(hp.hostHeader());
        assertNull(hp.host());
        assertEquals("apple.com#tag", hp.relativeReference());
        assertEquals("apple.com", hp.rawPath());
        assertEquals("apple.com", hp.path());
        assertEquals("", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(DEFAULT_PORT_HTTP, hp.port());
    }

    @Test
    public void schemeWithNoRequestTarget() {
        final HttpUri hp = new HttpUri("http:///");
        assertEquals("http", hp.scheme());
        assertNull(hp.hostHeader());
        assertNull(hp.host());
        assertEquals("/", hp.relativeReference());
        assertEquals("/", hp.rawPath());
        assertEquals("/", hp.path());
        assertEquals("", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(DEFAULT_PORT_HTTP, hp.port());
    }

    @Test
    public void schemeWithNoRequestTargetQuery() {
        final HttpUri hp = new HttpUri("http://?");
        assertEquals("http", hp.scheme());
        assertNull(hp.hostHeader());
        assertNull(hp.host());
        assertEquals("?", hp.relativeReference());
        assertEquals("", hp.rawPath());
        assertEquals("", hp.path());
        assertEquals("", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(DEFAULT_PORT_HTTP, hp.port());
    }

    @Test
    public void schemeWithNoRequestTargetPound() {
        final HttpUri hp = new HttpUri("http://#");
        assertEquals("http", hp.scheme());
        assertNull(hp.hostHeader());
        assertNull(hp.host());
        assertEquals("#", hp.relativeReference());
        assertEquals("", hp.rawPath());
        assertEquals("", hp.path());
        assertEquals("", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(DEFAULT_PORT_HTTP, hp.port());
    }

    @Test
    public void hostNameSupplierFallbackIsCalled() {
        final HttpUri hp = new HttpUri("example.com", () -> "example.com");
        assertNull(hp.scheme());
        assertEquals("example.com", hp.hostHeader());
        assertEquals("example.com", hp.host());
        assertEquals("example.com", hp.relativeReference());
        assertEquals("example.com", hp.rawPath());
        assertEquals("example.com", hp.path());
        assertEquals("", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(DEFAULT_PORT_HTTP, hp.port());
    }

    @Test
    public void cssAndQueryIsParsed() {
        final HttpUri hp = new HttpUri("http://localhost:8080/app.css?v1");
        assertEquals("http", hp.scheme());
        assertEquals("localhost:8080", hp.hostHeader());
        assertEquals("localhost", hp.host());
        assertEquals("/app.css?v1", hp.relativeReference());
        assertEquals("/app.css", hp.rawPath());
        assertEquals("/app.css", hp.path());
        assertEquals("v1", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(8080, hp.port());
    }

    @Test
    public void userInfoDelimiterAfterTypicalHost() {
        final HttpUri hp = new HttpUri("https://apple.com:8080@user/path%20/is/here?queryname=value#tag%20");
        assertEquals("https", hp.scheme());
        assertEquals("user", hp.hostHeader());
        assertEquals("user", hp.host());
        assertEquals("/path%20/is/here?queryname=value#tag%20", hp.relativeReference());
        assertEquals("/path%20/is/here", hp.rawPath());
        assertEquals("/path /is/here", hp.path());
        assertEquals("queryname=value", hp.rawQuery());
        assertTrue(hp.isSsl());
        assertEquals(DEFAULT_PORT_HTTPS, hp.port());
    }

    @Test
    public void userInfoNoPort() {
        final HttpUri hp = new HttpUri("http://user:foo@apple.com/path/is/here?queryname=value#tag");
        assertEquals("http", hp.scheme());
        assertEquals("apple.com", hp.hostHeader());
        assertEquals("apple.com", hp.host());
        assertEquals("/path/is/here?queryname=value#tag", hp.relativeReference());
        assertEquals("/path/is/here", hp.rawPath());
        assertEquals("/path/is/here", hp.path());
        assertEquals("queryname=value", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(DEFAULT_PORT_HTTP, hp.port());
    }

    @Test
    public void justSlash() {
        final HttpUri hp = new HttpUri("/");
        assertNull(hp.scheme());
        assertNull(hp.hostHeader());
        assertNull(hp.host());
        assertEquals("/", hp.relativeReference());
        assertEquals("/", hp.rawPath());
        assertEquals("/", hp.path());
        assertEquals("", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(DEFAULT_PORT_HTTP, hp.port());
    }

    @Test
    public void justAsterisk() {
        final HttpUri hp = new HttpUri("*");
        assertNull(hp.scheme());
        assertNull(hp.hostHeader());
        assertNull(hp.host());
        assertEquals("", hp.relativeReference());
        assertEquals("", hp.rawPath());
        assertEquals("", hp.path());
        assertEquals("", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(DEFAULT_PORT_HTTP, hp.port());
    }

    @Test
    public void justPath() {
        final HttpUri hp = new HttpUri("/path/is/here?queryname=value#tag");
        assertNull(hp.scheme());
        assertNull(hp.hostHeader());
        assertNull(hp.host());
        assertEquals("/path/is/here?queryname=value#tag", hp.relativeReference());
        assertEquals("/path/is/here", hp.rawPath());
        assertEquals("/path/is/here", hp.path());
        assertEquals("queryname=value", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(DEFAULT_PORT_HTTP, hp.port());
    }

    @Test
    public void schemeAuthority() {
        final HttpUri hp = new HttpUri("http://localhost:80");
        assertEquals("http", hp.scheme());
        assertEquals("localhost:80", hp.hostHeader());
        assertEquals("localhost", hp.host());
        assertEquals("", hp.relativeReference());
        assertEquals("", hp.rawPath());
        assertEquals("", hp.path());
        assertEquals("", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(80, hp.port());
    }

    @Test
    public void schemeAuthorityQuery() {
        final HttpUri hp = new HttpUri("http://localhost:8080?foo");
        assertEquals("http", hp.scheme());
        assertEquals("localhost:8080", hp.hostHeader());
        assertEquals("localhost", hp.host());
        assertEquals("?foo", hp.relativeReference());
        assertEquals("", hp.rawPath());
        assertEquals("", hp.path());
        assertEquals("foo", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(8080, hp.port());
    }

    @Test
    public void schemeAuthorityEmptyQuery() {
        final HttpUri hp = new HttpUri("http://localhost:8080?");
        assertEquals("http", hp.scheme());
        assertEquals("localhost:8080", hp.hostHeader());
        assertEquals("localhost", hp.host());
        assertEquals("?", hp.relativeReference());
        assertEquals("", hp.rawPath());
        assertEquals("", hp.path());
        assertEquals("", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(8080, hp.port());
    }

    @Test
    public void schemeAuthorityTag() {
        final HttpUri hp = new HttpUri("http://localhost:8080#foo");
        assertEquals("http", hp.scheme());
        assertEquals("localhost:8080", hp.hostHeader());
        assertEquals("localhost", hp.host());
        assertEquals("#foo", hp.relativeReference());
        assertEquals("", hp.rawPath());
        assertEquals("", hp.path());
        assertEquals("", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(8080, hp.port());
    }

    @Test
    public void schemeAuthorityEmptyTag() {
        final HttpUri hp = new HttpUri("http://localhost:8080#");
        assertEquals("http", hp.scheme());
        assertEquals("localhost:8080", hp.hostHeader());
        assertEquals("localhost", hp.host());
        assertEquals("#", hp.relativeReference());
        assertEquals("", hp.rawPath());
        assertEquals("", hp.path());
        assertEquals("", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(8080, hp.port());
    }

    @Test
    public void schemeAuthorityEmptyQueryEmptyTag() {
        final HttpUri hp = new HttpUri("http://localhost:8080?#");
        assertEquals("http", hp.scheme());
        assertEquals("localhost:8080", hp.hostHeader());
        assertEquals("localhost", hp.host());
        assertEquals("?#", hp.relativeReference());
        assertEquals("", hp.rawPath());
        assertEquals("", hp.path());
        assertEquals("", hp.rawQuery());
        assertFalse(hp.isSsl());
        assertEquals(8080, hp.port());
    }

    @Test
    public void httpsNoPort() {
        final HttpUri hp = new HttpUri("https://tools.ietf.org/html/rfc3986#section-3");
        assertEquals("https", hp.scheme());
        assertEquals("tools.ietf.org", hp.hostHeader());
        assertEquals("tools.ietf.org", hp.host());
        assertEquals("/html/rfc3986#section-3", hp.relativeReference());
        assertEquals("/html/rfc3986", hp.rawPath());
        assertEquals("/html/rfc3986", hp.path());
        assertEquals("", hp.rawQuery());
        assertTrue(hp.isSsl());
        assertEquals(DEFAULT_PORT_HTTPS, hp.port());
    }

    @Test
    public void ipv4LiteralWithPort() {
        final HttpUri hp = new HttpUri("https://foo:goo@123.456.234.122:9");
        assertEquals("https", hp.scheme());
        assertEquals("123.456.234.122:9", hp.hostHeader());
        assertEquals("123.456.234.122", hp.host());
        assertEquals("", hp.relativeReference());
        assertEquals("", hp.rawPath());
        assertEquals("", hp.path());
        assertEquals("", hp.rawQuery());
        assertTrue(hp.isSsl());
        assertEquals(9, hp.port());
    }

    @Test
    public void ipv4LiteralWithOutPort() {
        final HttpUri hp = new HttpUri("https://foo:goo@123.456.234.122");
        assertEquals("https", hp.scheme());
        assertEquals("123.456.234.122", hp.hostHeader());
        assertEquals("123.456.234.122", hp.host());
        assertEquals("", hp.relativeReference());
        assertEquals("", hp.rawPath());
        assertEquals("", hp.path());
        assertEquals("", hp.rawQuery());
        assertTrue(hp.isSsl());
        assertEquals(DEFAULT_PORT_HTTPS, hp.port());
    }

    @Test
    public void ipv6LiteralWithPort() {
        final HttpUri hp = new HttpUri("https://foo:goo@[::1]:988");
        assertEquals("https", hp.scheme());
        assertEquals("[::1]:988", hp.hostHeader());
        assertEquals("[::1]", hp.host());
        assertEquals("", hp.relativeReference());
        assertEquals("", hp.rawPath());
        assertEquals("", hp.path());
        assertEquals("", hp.rawQuery());
        assertTrue(hp.isSsl());
        assertEquals(988, hp.port());
    }

    @Test
    public void maxPortTest() {
        final HttpUri hp = new HttpUri("https://foo:goo@[::1]:65535");
        assertEquals("https", hp.scheme());
        assertEquals("[::1]:65535", hp.hostHeader());
        assertEquals("[::1]", hp.host());
        assertEquals("", hp.relativeReference());
        assertEquals("", hp.rawPath());
        assertEquals("", hp.path());
        assertEquals("", hp.rawQuery());
        assertTrue(hp.isSsl());
        assertEquals(65535, hp.port());
    }

    @Test
    public void ipv6LiteralWithOutPort() {
        final HttpUri hp = new HttpUri("https://foo:goo@[::1]");
        assertEquals("https", hp.scheme());
        assertEquals("[::1]", hp.hostHeader());
        assertEquals("[::1]", hp.host());
        assertEquals("", hp.relativeReference());
        assertEquals("", hp.rawPath());
        assertEquals("", hp.path());
        assertEquals("", hp.rawQuery());
        assertTrue(hp.isSsl());
        assertEquals(DEFAULT_PORT_HTTPS, hp.port());
    }

    @Test
    public void ipv6HostWithScope() {
        final HttpUri hp = new HttpUri("https://[0:0:0:0:0:0:0:0%0]/path?param=value");
        assertEquals("https", hp.scheme());
        assertEquals("[0:0:0:0:0:0:0:0%0]", hp.hostHeader());
        assertEquals("[0:0:0:0:0:0:0:0%0]", hp.host());
        assertEquals(DEFAULT_PORT_HTTPS, hp.port());
        assertEquals("/path?param=value", hp.relativeReference());
        assertEquals("/path", hp.rawPath());
        assertEquals("/path", hp.path());
        assertEquals("param=value", hp.rawQuery());
        assertTrue(hp.isSsl());
    }

    @Test
    public void ipv6HostWithScopeAndPort() {
        final HttpUri hp = new HttpUri("https://[0:0:0:0:0:0:0:0%0]:49178/path?param=value");
        assertEquals("https", hp.scheme());
        assertEquals("[0:0:0:0:0:0:0:0%0]:49178", hp.hostHeader());
        assertEquals("[0:0:0:0:0:0:0:0%0]", hp.host());
        assertEquals(49178, hp.port());
        assertEquals("/path?param=value", hp.relativeReference());
        assertEquals("/path", hp.rawPath());
        assertEquals("/path", hp.path());
        assertEquals("param=value", hp.rawQuery());
        assertTrue(hp.isSsl());
    }

    @Test
    public void ipv4HostHeaderNoPort() {
        final HttpUri hp = new HttpUri("/path?param=value", () -> "1.2.3.4");
        assertNull(hp.scheme());
        assertEquals("1.2.3.4", hp.hostHeader());
        assertEquals("1.2.3.4", hp.host());
        assertEquals(80, hp.port());
        assertEquals("/path?param=value", hp.relativeReference());
        assertEquals("/path", hp.rawPath());
        assertEquals("/path", hp.path());
        assertEquals("param=value", hp.rawQuery());
        assertFalse(hp.isSsl());
    }

    @Test
    public void ipv4HostHeaderWithPort() {
        final HttpUri hp = new HttpUri("/path?param=value", () -> "1.2.3.4:2834");
        assertNull(hp.scheme());
        assertEquals("1.2.3.4:2834", hp.hostHeader());
        assertEquals("1.2.3.4", hp.host());
        assertEquals(2834, hp.port());
        assertEquals("/path?param=value", hp.relativeReference());
        assertEquals("/path", hp.rawPath());
        assertEquals("/path", hp.path());
        assertEquals("param=value", hp.rawQuery());
        assertFalse(hp.isSsl());
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv4HostHeaderWithInvalidPort() {
        new HttpUri("/path?param=value", () -> "1.2.3.4:65536");
    }

    @Test
    public void ipv6HostHeaderNoPort() {
        final HttpUri hp = new HttpUri("/path?param=value", () -> "[::1]");
        assertNull(hp.scheme());
        assertEquals("[::1]", hp.hostHeader());
        assertEquals("[::1]", hp.host());
        assertEquals(80, hp.port());
        assertEquals("/path?param=value", hp.relativeReference());
        assertEquals("/path", hp.rawPath());
        assertEquals("/path", hp.path());
        assertEquals("param=value", hp.rawQuery());
        assertFalse(hp.isSsl());
    }

    @Test
    public void ipv6HostHeaderWithPort() {
        final HttpUri hp = new HttpUri("/path?param=value", () -> "[::1]:8080");
        assertNull(hp.scheme());
        assertEquals("[::1]:8080", hp.hostHeader());
        assertEquals("[::1]", hp.host());
        assertEquals(8080, hp.port());
        assertEquals("/path?param=value", hp.relativeReference());
        assertEquals("/path", hp.rawPath());
        assertEquals("/path", hp.path());
        assertEquals("param=value", hp.rawQuery());
        assertFalse(hp.isSsl());
    }

    @Test
    public void ipv6HostHeaderWithScope() {
        final HttpUri hp = new HttpUri("/path?param=value", () -> "[12:3::1%2]:8081");
        assertNull(hp.scheme());
        assertEquals("[12:3::1%2]:8081", hp.hostHeader());
        assertEquals("[12:3::1%2]", hp.host());
        assertEquals(8081, hp.port());
        assertEquals("/path?param=value", hp.relativeReference());
        assertEquals("/path", hp.rawPath());
        assertEquals("/path", hp.path());
        assertEquals("param=value", hp.rawQuery());
        assertFalse(hp.isSsl());
    }

    @Test
    public void stringAddressHostHeader() {
        final HttpUri hp = new HttpUri("/path?param=value", () -> "apple.com:8081");
        assertNull(hp.scheme());
        assertEquals("apple.com:8081", hp.hostHeader());
        assertEquals("apple.com", hp.host());
        assertEquals(8081, hp.port());
        assertEquals("/path?param=value", hp.relativeReference());
        assertEquals("/path", hp.rawPath());
        assertEquals("/path", hp.path());
        assertEquals("param=value", hp.rawQuery());
        assertFalse(hp.isSsl());
    }

    @Test
    public void literalAddressHostHeader() {
        final HttpUri hp = new HttpUri("/path?param=value", () -> "apple:8081");
        assertNull(hp.scheme());
        assertEquals("apple:8081", hp.hostHeader());
        assertEquals("apple", hp.host());
        assertEquals(8081, hp.port());
        assertEquals("/path?param=value", hp.relativeReference());
        assertEquals("/path", hp.rawPath());
        assertEquals("/path", hp.path());
        assertEquals("param=value", hp.rawQuery());
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

    @Test(expected = IllegalArgumentException.class)
    public void onlyPortInHostHeader() {
        new HttpUri("/path?param=value", () -> ":8080");
    }

    private static void verifyAppleString(final HttpUri hp, final boolean isSsl, final int port, @Nullable final String userInfo) {
        assertEquals(isSsl ? "https" : "http", hp.scheme());
        assertEquals(userInfo, hp.userInfo());
        assertEquals("apple.com:8080", hp.hostHeader());
        assertEquals("apple.com", hp.host());
        assertEquals("/path/is/here?queryname=value#tag", hp.relativeReference());
        assertEquals("/path/is/here", hp.rawPath());
        assertEquals("/path/is/here", hp.path());
        assertEquals("queryname=value", hp.rawQuery());
        assertEquals(isSsl, hp.isSsl());
        assertEquals(port, hp.port());
    }
}
