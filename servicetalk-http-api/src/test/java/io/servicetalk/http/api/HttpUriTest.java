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

import static io.servicetalk.http.api.HttpSchemes.HTTP;
import static io.servicetalk.http.api.HttpSchemes.HTTPS;
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
        assertNull(hp.getScheme());
        assertEquals("a.apple.com:81", hp.getHostHeader());
        assertEquals("a.apple.com", hp.getHost());
        assertEquals("", hp.getRelativeReference());
        assertEquals("", hp.getRawPath());
        assertEquals("", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(81, hp.getPort());
    }

    @Test
    public void badAuthorityForm() {
        final HttpUri hp = new HttpUri("a.apple.com:81/should_be_ignored");
        assertNull(hp.getScheme());
        assertEquals("a.apple.com:81", hp.getHostHeader());
        assertEquals("a.apple.com", hp.getHost());
        assertEquals("", hp.getRelativeReference());
        assertEquals("", hp.getRawPath());
        assertEquals("", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(81, hp.getPort());
    }

    @Test
    public void hostLookAlikeInPath() {
        final HttpUri hp = new HttpUri("/@foo");
        assertNull(hp.getScheme());
        assertNull(hp.getHostHeader());
        assertNull(hp.getHost());
        assertEquals("/@foo", hp.getRelativeReference());
        assertEquals("/@foo", hp.getRawPath());
        assertEquals("/@foo", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(80, hp.getPort());
    }

    @Test
    public void doubleSlashWithPortLookAlike() {
        final HttpUri hp = new HttpUri("//81");
        assertNull(hp.getScheme());
        assertEquals("81", hp.getHostHeader());
        assertEquals("81", hp.getHost());
        assertEquals("", hp.getRelativeReference());
        assertEquals("", hp.getRawPath());
        assertEquals("", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(80, hp.getPort());
    }

    @Test
    public void doubleSlashWithoutScheme() {
        final HttpUri hp = new HttpUri("//foo.com/path?query#tag");
        assertNull(hp.getScheme());
        assertEquals("foo.com", hp.getHostHeader());
        assertEquals("foo.com", hp.getHost());
        assertEquals("/path?query#tag", hp.getRelativeReference());
        assertEquals("/path", hp.getRawPath());
        assertEquals("/path", hp.getPath());
        assertEquals("query", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(HTTP.defaultPort(), hp.getPort());
    }

    @Test
    public void doubleSlashAfterInitialPath() {
        final HttpUri hp = new HttpUri("f//foo.com/path?query#tag");
        assertNull(hp.getScheme());
        assertNull(hp.getHostHeader());
        assertNull(hp.getHost());
        assertEquals("f//foo.com/path?query#tag", hp.getRelativeReference());
        assertEquals("f//foo.com/path", hp.getRawPath());
        assertEquals("f//foo.com/path", hp.getPath());
        assertEquals("query", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(HTTP.defaultPort(), hp.getPort());
    }

    @Test
    public void userInfoAndHostAfterFirstPathComponent() {
        final HttpUri hp = new HttpUri("user/mode@apple.com:8080/path/is/here?queryname=value#tag");
        assertNull(hp.getScheme());
        assertNull(hp.getHostHeader());
        assertNull(hp.getHost());
        assertEquals("user/mode@apple.com:8080/path/is/here?queryname=value#tag", hp.getRelativeReference());
        assertEquals("user/mode@apple.com:8080/path/is/here", hp.getRawPath());
        assertEquals("user/mode@apple.com:8080/path/is/here", hp.getPath());
        assertEquals("queryname=value", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(HTTP.defaultPort(), hp.getPort());
    }

    @Test
    public void hostAfterFirstPathSegment() {
        final HttpUri hp = new HttpUri("user/apple.com/path/is/here?queryname=value#tag");
        assertNull(hp.getScheme());
        assertNull(hp.getHostHeader());
        assertNull(hp.getHost());
        assertEquals("user/apple.com/path/is/here?queryname=value#tag", hp.getRelativeReference());
        assertEquals("user/apple.com/path/is/here", hp.getRawPath());
        assertEquals("user/apple.com/path/is/here", hp.getPath());
        assertEquals("queryname=value", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(HTTP.defaultPort(), hp.getPort());
    }

    @Test
    public void poundEndsRequestTarget() {
        final HttpUri hp = new HttpUri("apple.com#tag");
        assertNull(hp.getScheme());
        assertNull(hp.getHostHeader());
        assertNull(hp.getHost());
        assertEquals("apple.com#tag", hp.getRelativeReference());
        assertEquals("apple.com", hp.getRawPath());
        assertEquals("apple.com", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(HTTP.defaultPort(), hp.getPort());
    }

    @Test
    public void schemeWithNoRequestTarget() {
        final HttpUri hp = new HttpUri("http:///");
        assertEquals(HTTP, hp.getScheme());
        assertNull(hp.getHostHeader());
        assertNull(hp.getHost());
        assertEquals("/", hp.getRelativeReference());
        assertEquals("/", hp.getRawPath());
        assertEquals("/", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(HTTP.defaultPort(), hp.getPort());
    }

    @Test
    public void schemeWithNoRequestTargetQuery() {
        final HttpUri hp = new HttpUri("http://?");
        assertEquals(HTTP, hp.getScheme());
        assertNull(hp.getHostHeader());
        assertNull(hp.getHost());
        assertEquals("?", hp.getRelativeReference());
        assertEquals("", hp.getRawPath());
        assertEquals("", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(HTTP.defaultPort(), hp.getPort());
    }

    @Test
    public void schemeWithNoRequestTargetPound() {
        final HttpUri hp = new HttpUri("http://#");
        assertEquals(HTTP, hp.getScheme());
        assertNull(hp.getHostHeader());
        assertNull(hp.getHost());
        assertEquals("#", hp.getRelativeReference());
        assertEquals("", hp.getRawPath());
        assertEquals("", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(HTTP.defaultPort(), hp.getPort());
    }

    @Test
    public void hostNameSupplierFallbackIsCalled() {
        final HttpUri hp = new HttpUri("example.com", () -> "example.com");
        assertNull(hp.getScheme());
        assertEquals("example.com", hp.getHostHeader());
        assertEquals("example.com", hp.getHost());
        assertEquals("example.com", hp.getRelativeReference());
        assertEquals("example.com", hp.getRawPath());
        assertEquals("example.com", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(HTTP.defaultPort(), hp.getPort());
    }

    @Test
    public void cssAndQueryIsParsed() {
        final HttpUri hp = new HttpUri("http://localhost:8080/app.css?v1");
        assertEquals(HTTP, hp.getScheme());
        assertEquals("localhost:8080", hp.getHostHeader());
        assertEquals("localhost", hp.getHost());
        assertEquals("/app.css?v1", hp.getRelativeReference());
        assertEquals("/app.css", hp.getRawPath());
        assertEquals("/app.css", hp.getPath());
        assertEquals("v1", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(8080, hp.getPort());
    }

    @Test
    public void userInfoDelimiterAfterTypicalHost() {
        final HttpUri hp = new HttpUri("https://apple.com:8080@user/path%20/is/here?queryname=value#tag%20");
        assertEquals(HTTPS, hp.getScheme());
        assertEquals("user", hp.getHostHeader());
        assertEquals("user", hp.getHost());
        assertEquals("/path%20/is/here?queryname=value#tag%20", hp.getRelativeReference());
        assertEquals("/path%20/is/here", hp.getRawPath());
        assertEquals("/path /is/here", hp.getPath());
        assertEquals("queryname=value", hp.getRawQuery());
        assertTrue(hp.isSsl());
        assertEquals(HTTPS.defaultPort(), hp.getPort());
    }

    @Test
    public void userInfoNoPort() {
        final HttpUri hp = new HttpUri("http://user:foo@apple.com/path/is/here?queryname=value#tag");
        assertEquals(HTTP, hp.getScheme());
        assertEquals("apple.com", hp.getHostHeader());
        assertEquals("apple.com", hp.getHost());
        assertEquals("/path/is/here?queryname=value#tag", hp.getRelativeReference());
        assertEquals("/path/is/here", hp.getRawPath());
        assertEquals("/path/is/here", hp.getPath());
        assertEquals("queryname=value", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(HTTP.defaultPort(), hp.getPort());
    }

    @Test
    public void justSlash() {
        final HttpUri hp = new HttpUri("/");
        assertNull(hp.getScheme());
        assertNull(hp.getHostHeader());
        assertNull(hp.getHost());
        assertEquals("/", hp.getRelativeReference());
        assertEquals("/", hp.getRawPath());
        assertEquals("/", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(HTTP.defaultPort(), hp.getPort());
    }

    @Test
    public void justAsterisk() {
        final HttpUri hp = new HttpUri("*");
        assertNull(hp.getScheme());
        assertNull(hp.getHostHeader());
        assertNull(hp.getHost());
        assertEquals("", hp.getRelativeReference());
        assertEquals("", hp.getRawPath());
        assertEquals("", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(HTTP.defaultPort(), hp.getPort());
    }

    @Test
    public void justPath() {
        final HttpUri hp = new HttpUri("/path/is/here?queryname=value#tag");
        assertNull(hp.getScheme());
        assertNull(hp.getHostHeader());
        assertNull(hp.getHost());
        assertEquals("/path/is/here?queryname=value#tag", hp.getRelativeReference());
        assertEquals("/path/is/here", hp.getRawPath());
        assertEquals("/path/is/here", hp.getPath());
        assertEquals("queryname=value", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(HTTP.defaultPort(), hp.getPort());
    }

    @Test
    public void schemeAuthority() {
        final HttpUri hp = new HttpUri("http://localhost:80");
        assertEquals(HTTP, hp.getScheme());
        assertEquals("localhost:80", hp.getHostHeader());
        assertEquals("localhost", hp.getHost());
        assertEquals("", hp.getRelativeReference());
        assertEquals("", hp.getRawPath());
        assertEquals("", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(80, hp.getPort());
    }

    @Test
    public void schemeAuthorityQuery() {
        final HttpUri hp = new HttpUri("http://localhost:8080?foo");
        assertEquals(HTTP, hp.getScheme());
        assertEquals("localhost:8080", hp.getHostHeader());
        assertEquals("localhost", hp.getHost());
        assertEquals("?foo", hp.getRelativeReference());
        assertEquals("", hp.getRawPath());
        assertEquals("", hp.getPath());
        assertEquals("foo", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(8080, hp.getPort());
    }

    @Test
    public void schemeAuthorityEmptyQuery() {
        final HttpUri hp = new HttpUri("http://localhost:8080?");
        assertEquals(HTTP, hp.getScheme());
        assertEquals("localhost:8080", hp.getHostHeader());
        assertEquals("localhost", hp.getHost());
        assertEquals("?", hp.getRelativeReference());
        assertEquals("", hp.getRawPath());
        assertEquals("", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(8080, hp.getPort());
    }

    @Test
    public void schemeAuthorityTag() {
        final HttpUri hp = new HttpUri("http://localhost:8080#foo");
        assertEquals(HTTP, hp.getScheme());
        assertEquals("localhost:8080", hp.getHostHeader());
        assertEquals("localhost", hp.getHost());
        assertEquals("#foo", hp.getRelativeReference());
        assertEquals("", hp.getRawPath());
        assertEquals("", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(8080, hp.getPort());
    }

    @Test
    public void schemeAuthorityEmptyTag() {
        final HttpUri hp = new HttpUri("http://localhost:8080#");
        assertEquals(HTTP, hp.getScheme());
        assertEquals("localhost:8080", hp.getHostHeader());
        assertEquals("localhost", hp.getHost());
        assertEquals("#", hp.getRelativeReference());
        assertEquals("", hp.getRawPath());
        assertEquals("", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(8080, hp.getPort());
    }

    @Test
    public void schemeAuthorityEmptyQueryEmptyTag() {
        final HttpUri hp = new HttpUri("http://localhost:8080?#");
        assertEquals(HTTP, hp.getScheme());
        assertEquals("localhost:8080", hp.getHostHeader());
        assertEquals("localhost", hp.getHost());
        assertEquals("?#", hp.getRelativeReference());
        assertEquals("", hp.getRawPath());
        assertEquals("", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertFalse(hp.isSsl());
        assertEquals(8080, hp.getPort());
    }

    @Test
    public void httpsNoPort() {
        final HttpUri hp = new HttpUri("https://tools.ietf.org/html/rfc3986#section-3");
        assertEquals(HTTPS, hp.getScheme());
        assertEquals("tools.ietf.org", hp.getHostHeader());
        assertEquals("tools.ietf.org", hp.getHost());
        assertEquals("/html/rfc3986#section-3", hp.getRelativeReference());
        assertEquals("/html/rfc3986", hp.getRawPath());
        assertEquals("/html/rfc3986", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertTrue(hp.isSsl());
        assertEquals(HTTPS.defaultPort(), hp.getPort());
    }

    @Test
    public void ipv4LiteralWithPort() {
        final HttpUri hp = new HttpUri("https://foo:goo@123.456.234.122:9");
        assertEquals(HTTPS, hp.getScheme());
        assertEquals("123.456.234.122:9", hp.getHostHeader());
        assertEquals("123.456.234.122", hp.getHost());
        assertEquals("", hp.getRelativeReference());
        assertEquals("", hp.getRawPath());
        assertEquals("", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertTrue(hp.isSsl());
        assertEquals(9, hp.getPort());
    }

    @Test
    public void ipv4LiteralWithOutPort() {
        final HttpUri hp = new HttpUri("https://foo:goo@123.456.234.122");
        assertEquals(HTTPS, hp.getScheme());
        assertEquals("123.456.234.122", hp.getHostHeader());
        assertEquals("123.456.234.122", hp.getHost());
        assertEquals("", hp.getRelativeReference());
        assertEquals("", hp.getRawPath());
        assertEquals("", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertTrue(hp.isSsl());
        assertEquals(HTTPS.defaultPort(), hp.getPort());
    }

    @Test
    public void ipv6LiteralWithPort() {
        final HttpUri hp = new HttpUri("https://foo:goo@[::1]:988");
        assertEquals(HTTPS, hp.getScheme());
        assertEquals("[::1]:988", hp.getHostHeader());
        assertEquals("[::1]", hp.getHost());
        assertEquals("", hp.getRelativeReference());
        assertEquals("", hp.getRawPath());
        assertEquals("", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertTrue(hp.isSsl());
        assertEquals(988, hp.getPort());
    }

    @Test
    public void maxPortTest() {
        final HttpUri hp = new HttpUri("https://foo:goo@[::1]:65535");
        assertEquals(HTTPS, hp.getScheme());
        assertEquals("[::1]:65535", hp.getHostHeader());
        assertEquals("[::1]", hp.getHost());
        assertEquals("", hp.getRelativeReference());
        assertEquals("", hp.getRawPath());
        assertEquals("", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertTrue(hp.isSsl());
        assertEquals(65535, hp.getPort());
    }

    @Test
    public void ipv6LiteralWithOutPort() {
        final HttpUri hp = new HttpUri("https://foo:goo@[::1]");
        assertEquals(HTTPS, hp.getScheme());
        assertEquals("[::1]", hp.getHostHeader());
        assertEquals("[::1]", hp.getHost());
        assertEquals("", hp.getRelativeReference());
        assertEquals("", hp.getRawPath());
        assertEquals("", hp.getPath());
        assertEquals("", hp.getRawQuery());
        assertTrue(hp.isSsl());
        assertEquals(HTTPS.defaultPort(), hp.getPort());
    }

    @Test
    public void ipv6HostWithScope() {
        final HttpUri hp = new HttpUri("https://[0:0:0:0:0:0:0:0%0]/path?param=value");
        assertEquals(HTTPS, hp.getScheme());
        assertEquals("[0:0:0:0:0:0:0:0%0]", hp.getHostHeader());
        assertEquals("[0:0:0:0:0:0:0:0%0]", hp.getHost());
        assertEquals(HTTPS.defaultPort(), hp.getPort());
        assertEquals("/path?param=value", hp.getRelativeReference());
        assertEquals("/path", hp.getRawPath());
        assertEquals("/path", hp.getPath());
        assertEquals("param=value", hp.getRawQuery());
        assertTrue(hp.isSsl());
    }

    @Test
    public void ipv6HostWithScopeAndPort() {
        final HttpUri hp = new HttpUri("https://[0:0:0:0:0:0:0:0%0]:49178/path?param=value");
        assertEquals(HTTPS, hp.getScheme());
        assertEquals("[0:0:0:0:0:0:0:0%0]:49178", hp.getHostHeader());
        assertEquals("[0:0:0:0:0:0:0:0%0]", hp.getHost());
        assertEquals(49178, hp.getPort());
        assertEquals("/path?param=value", hp.getRelativeReference());
        assertEquals("/path", hp.getRawPath());
        assertEquals("/path", hp.getPath());
        assertEquals("param=value", hp.getRawQuery());
        assertTrue(hp.isSsl());
    }

    @Test
    public void ipv4HostHeaderNoPort() {
        final HttpUri hp = new HttpUri("/path?param=value", () -> "1.2.3.4");
        assertNull(hp.getScheme());
        assertEquals("1.2.3.4", hp.getHostHeader());
        assertEquals("1.2.3.4", hp.getHost());
        assertEquals(80, hp.getPort());
        assertEquals("/path?param=value", hp.getRelativeReference());
        assertEquals("/path", hp.getRawPath());
        assertEquals("/path", hp.getPath());
        assertEquals("param=value", hp.getRawQuery());
        assertFalse(hp.isSsl());
    }

    @Test
    public void ipv4HostHeaderWithPort() {
        final HttpUri hp = new HttpUri("/path?param=value", () -> "1.2.3.4:2834");
        assertNull(hp.getScheme());
        assertEquals("1.2.3.4:2834", hp.getHostHeader());
        assertEquals("1.2.3.4", hp.getHost());
        assertEquals(2834, hp.getPort());
        assertEquals("/path?param=value", hp.getRelativeReference());
        assertEquals("/path", hp.getRawPath());
        assertEquals("/path", hp.getPath());
        assertEquals("param=value", hp.getRawQuery());
        assertFalse(hp.isSsl());
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv4HostHeaderWithInvalidPort() {
        new HttpUri("/path?param=value", () -> "1.2.3.4:65536");
    }

    @Test
    public void ipv6HostHeaderNoPort() {
        final HttpUri hp = new HttpUri("/path?param=value", () -> "[::1]");
        assertNull(hp.getScheme());
        assertEquals("[::1]", hp.getHostHeader());
        assertEquals("[::1]", hp.getHost());
        assertEquals(80, hp.getPort());
        assertEquals("/path?param=value", hp.getRelativeReference());
        assertEquals("/path", hp.getRawPath());
        assertEquals("/path", hp.getPath());
        assertEquals("param=value", hp.getRawQuery());
        assertFalse(hp.isSsl());
    }

    @Test
    public void ipv6HostHeaderWithPort() {
        final HttpUri hp = new HttpUri("/path?param=value", () -> "[::1]:8080");
        assertNull(hp.getScheme());
        assertEquals("[::1]:8080", hp.getHostHeader());
        assertEquals("[::1]", hp.getHost());
        assertEquals(8080, hp.getPort());
        assertEquals("/path?param=value", hp.getRelativeReference());
        assertEquals("/path", hp.getRawPath());
        assertEquals("/path", hp.getPath());
        assertEquals("param=value", hp.getRawQuery());
        assertFalse(hp.isSsl());
    }

    @Test
    public void ipv6HostHeaderWithScope() {
        final HttpUri hp = new HttpUri("/path?param=value", () -> "[12:3::1%2]:8081");
        assertNull(hp.getScheme());
        assertEquals("[12:3::1%2]:8081", hp.getHostHeader());
        assertEquals("[12:3::1%2]", hp.getHost());
        assertEquals(8081, hp.getPort());
        assertEquals("/path?param=value", hp.getRelativeReference());
        assertEquals("/path", hp.getRawPath());
        assertEquals("/path", hp.getPath());
        assertEquals("param=value", hp.getRawQuery());
        assertFalse(hp.isSsl());
    }

    @Test
    public void stringAddressHostHeader() {
        final HttpUri hp = new HttpUri("/path?param=value", () -> "apple.com:8081");
        assertNull(hp.getScheme());
        assertEquals("apple.com:8081", hp.getHostHeader());
        assertEquals("apple.com", hp.getHost());
        assertEquals(8081, hp.getPort());
        assertEquals("/path?param=value", hp.getRelativeReference());
        assertEquals("/path", hp.getRawPath());
        assertEquals("/path", hp.getPath());
        assertEquals("param=value", hp.getRawQuery());
        assertFalse(hp.isSsl());
    }

    @Test
    public void literalAddressHostHeader() {
        final HttpUri hp = new HttpUri("/path?param=value", () -> "apple:8081");
        assertNull(hp.getScheme());
        assertEquals("apple:8081", hp.getHostHeader());
        assertEquals("apple", hp.getHost());
        assertEquals(8081, hp.getPort());
        assertEquals("/path?param=value", hp.getRelativeReference());
        assertEquals("/path", hp.getRawPath());
        assertEquals("/path", hp.getPath());
        assertEquals("param=value", hp.getRawQuery());
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
        assertEquals(isSsl ? HTTPS : HTTP, hp.getScheme());
        assertEquals(userInfo, hp.getUserInfo());
        assertEquals("apple.com:8080", hp.getHostHeader());
        assertEquals("apple.com", hp.getHost());
        assertEquals("/path/is/here?queryname=value#tag", hp.getRelativeReference());
        assertEquals("/path/is/here", hp.getRawPath());
        assertEquals("/path/is/here", hp.getPath());
        assertEquals("queryname=value", hp.getRawQuery());
        assertEquals(isSsl, hp.isSsl());
        assertEquals(port, hp.getPort());
    }
}
