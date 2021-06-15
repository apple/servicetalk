/*
 * Copyright © 2018-2021 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.Uri3986.decode;
import static io.servicetalk.http.api.Uri3986.encode;
import static java.lang.Character.forDigit;
import static java.lang.Character.toUpperCase;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.regex.Pattern.compile;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
class Uri3986Test {
    /**
     * <a href="https://tools.ietf.org/html/rfc3986#appendix-B">Parsing a URI Reference with a Regular Expression</a>
     */
    private static final Pattern VALID_PATTERN = compile("^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?");

    @Test
    void fullHttpURI() {
        verifyAppleString("http://apple.com:8080/path/is/here?queryname=value#tag", false, 8080, null);
    }

    @Test
    void fullHttpsURI() {
        verifyAppleString("https://apple.com:8080/path/is/here?queryname=value#tag", true, 8080, null);
    }

    @Test
    void ignoreUserInfo() {
        verifyAppleString("http://user:password@apple.com:8080/path/is/here?queryname=value#tag", false, 8080,
                "user:password");
    }

    @Test
    void ignoreUserInfoPctEncoded() {
        verifyAppleString(
                "http://user%20:passwo%2Frd@apple.com:12/path/is/here?queryname=value#tag", false, 12,
                "user%20:passwo%2Frd");
    }

    @Test
    void ignoreUserInfoPctEncodedIllegalReservedCharacterWithColon() {
        assertThrows(IllegalArgumentException.class,
                () -> new Uri3986("http://user:passwo/rd@apple.com:8080/path/is/here?queryname=value#tag"));
    }

    @Test
    void duplicateSchema() {
        assertThrows(IllegalArgumentException.class, () -> new Uri3986("http://foo://test.com"));
    }

    @Test
    void emptyScheme() {
        assertThrows(IllegalArgumentException.class, () -> new Uri3986("://test.com"));
    }

    @Test
    void portTooBig() {
        assertThrows(IllegalArgumentException.class, () -> new Uri3986("http://foo.com:65536"));
    }

    @Test
    void portNegative() {
        assertThrows(IllegalArgumentException.class, () -> new Uri3986("http://foo.com:-1"));
    }

    @Test
    void portInvalidAtEnd() {
        assertThrows(IllegalArgumentException.class, () -> new Uri3986("http://foo.com:6553a"));
    }

    @Test
    void portInvalidBegin() {
        assertThrows(IllegalArgumentException.class, () -> new Uri3986("http://foo.com:a"));
    }

    @Test
    void portInvalidMiddle() {
        assertThrows(IllegalArgumentException.class, () -> new Uri3986("http://foo.com:1ab2"));
    }

    @Test
    void emptyIpLiteral() {
        assertThrows(IllegalArgumentException.class, () -> new Uri3986("http://[]"));
    }

    @Test
    void unexpectedIpLiteralClose() {
        assertThrows(IllegalArgumentException.class, () -> new Uri3986("http://]"));
    }

    @Test
    void unexpectedIpLiteralOpen() {
        assertThrows(IllegalArgumentException.class, () -> new Uri3986("http://[["));
    }

    @Test
    void duplicateUserInfo() {
        assertThrows(IllegalArgumentException.class, () -> new Uri3986("http://foo@bar@apple.com"));
    }

    @Test
    void malformedAuthority() {
        assertThrows(IllegalArgumentException.class, () -> new Uri3986("http://blah@apple.com:80@apple.com"));
    }

    @Test
    void emptyUserInfoAndAuthority() {
        verifyUri3986("@", null, null, null, -1, "@", "@", null, null, null);
    }

    @Test
    void emptyAuthority() {
        verifyUri3986("@foo", null, null, null, -1, "@foo", "@foo", null, null, null);
    }

    @Test
    void doubleSlashWithEmptyAuthorityThrows() {
        verifyUri3986("//@foo", null, "", "foo", -1, "", "", null, null, null);
    }

    @Test
    void nonHttpSchema() {
        verifyUri3986("foo://test.com", "foo", null, "test.com", -1, "", "", null, null, null);
    }

    @Test
    void hostWithNoSlash() {
        verifyUri3986("user@host", null, null, null, -1, "user@host", "user@host", null, null, null);
    }

    @Test
    void emptyUserInfo() {
        verifyUri3986("user@", null, null, null, -1, "user@", "user@", null, null, null);
    }

    @Test
    void schemeParsedCaseSensative() {
        verifyUri3986("HTTP://test.com", "HTTP", null, "test.com", -1, "", "", null, null, null);
    }

    @Test
    void invalidSchemaToken() {
        verifyUri3986("http:/test.com", "http", null, null, -1, "/test.com", "/test.com", null, null, null);
    }

    @Test
    void invalidSchemaToken2() {
        verifyUri3986("http:test.com", "http", null, null, -1, "test.com", "test.com", null, null, null);
    }

    @Test
    void authorityForm() {
        // https://tools.ietf.org/html/rfc3986#section-3
        // urn:example:animal:ferret:nose
        verifyUri3986("a.apple.com:81", "a.apple.com", null, null, -1, "81", "81", null, null, null);
    }

    @Test
    void unusualSchemeAuthorityForm() {
        verifyUri3986("a.apple.com:81/path", "a.apple.com", null, null, -1, "81/path", "81/path", null, null, null);
    }

    @Test
    void hostLookAlikeInPath() {
        verifyUri3986("/@foo", null, null, null, -1, "/@foo", "/@foo", null, null, null);
    }

    @Test
    void doubleSlashWithPortLookAlike() {
        verifyUri3986("//81", null, null, "81", -1, "", "", null, null, null);
    }

    @Test
    void doubleSlashWithoutScheme() {
        verifyUri3986("//foo.com/path?query#tag", null, null, "foo.com", -1, "/path", "/path", "query", "query", "tag");
    }

    @Test
    void doubleSlashAfterInitialPath() {
        verifyUri3986("f//foo.com/path?query#tag", null, null, null, -1, "f//foo.com/path", "f//foo.com/path",
                "query", "query", "tag");
    }

    @Test
    void pathWithAuthorityEmbedded() {
        verifyUri3986("http://user/mode@apple.com:8080/path/is/here?queryname=value#tag", "http", null, "user", -1,
                "/mode@apple.com:8080/path/is/here", "/mode@apple.com:8080/path/is/here", "queryname=value",
                "queryname=value", "tag");
    }

    @Test
    void userInfoAndHostAfterFirstPathComponent() {
        verifyUri3986("user/mode@apple.com:8080/path/is/here?queryname=value#tag", null, null, null, -1,
                "user/mode@apple.com:8080/path/is/here", "user/mode@apple.com:8080/path/is/here", "queryname=value",
                "queryname=value", "tag");
    }

    @Test
    void hostAfterFirstPathSegment() {
        verifyUri3986("user/apple.com/path/is/here?queryname=value#tag", null, null, null, -1,
                "user/apple.com/path/is/here", "user/apple.com/path/is/here", "queryname=value", "queryname=value",
                "tag");
    }

    @Test
    void poundEndsRequestTarget() {
        verifyUri3986("apple.com#tag", null, null, null, -1, "apple.com", "apple.com", null, null, "tag");
    }

    @Test
    void doubleSlashWithEmptyUserInfoAndAuthority() {
        verifyUri3986("//", null, null, "", -1, "", "", null, null, null);
    }

    @Test
    void doubleSlashWithUserInfo() {
        verifyUri3986("//user@", null, "user", "", -1, "", "", null, null, null);
    }

    @Test
    void schemeWithNoRequestTargetQuery() {
        verifyUri3986("http://?", "http", null, "", -1, "", "", "", "", null);
    }

    @Test
    void schemeWithNoRequestTargetPound() {
        verifyUri3986("http://#", "http", null, "", -1, "", "", null, null, "");
    }

    @Test
    void schemeWithNoRequestTarget() {
        verifyUri3986("http:///", "http", null, "", -1, "/", "/", null, null, null);
    }

    @Test
    void justHostName() {
        verifyUri3986("example.com", null, null, null, -1, "example.com", "example.com", null, null, null);
    }

    @Test
    void cssAndQueryIsParsed() {
        verifyUri3986("http://localhost:8080/app.css?v1", "http", null, "localhost", 8080, "/app.css", "/app.css",
                "v1", "v1", null);
    }

    @Test
    void userInfoDelimiterAfterTypicalHost() {
        verifyUri3986("https://apple.com:8080@user/path%20/is/here?queryname=value#tag%20", "https", "apple.com:8080",
                "user", -1, "/path%20/is/here", "/path /is/here", "queryname=value", "queryname=value", "tag%20");
    }

    @Test
    void userInfoNoPort() {
        verifyUri3986("http://user:foo@apple.com/path/is/here?query%20name=value#tag", "http", "user:foo", "apple.com",
                -1, "/path/is/here", "/path/is/here", "query%20name=value", "query name=value", "tag");
    }

    @Test
    void justSlash() {
        verifyUri3986("/", null, null, null, -1, "/", "/", null, null, null);
    }

    @Test
    void absolutePath() {
        verifyUri3986("/foo", null, null, null, -1, "/foo", "/foo", null, null, null);
    }

    @Test
    void dotSegmentPath() {
        verifyUri3986("./this:that", null, null, null, -1, "./this:that", "./this:that", null, null, null);
    }

    @Test
    void nonDotSegmentScheme() {
        verifyUri3986("this:that", "this", null, null, -1, "that", "that", null, null, null);
    }

    @Test
    void justAsterisk() {
        verifyUri3986("*", null, null, null, -1, "*", "*", null, null, null);
    }

    @Test
    void justPath() {
        verifyUri3986("/path/is/here?queryname=value#tag", null, null, null, -1, "/path/is/here", "/path/is/here",
                "queryname=value", "queryname=value", "tag");
    }

    @Test
    void justQuery() {
        verifyUri3986("?queryname", null, null, null, -1, "", "", "queryname", "queryname", null);
    }

    @Test
    void justFragment() {
        verifyUri3986("#tag", null, null, null, -1, "", "", null, null, "tag");
    }

    @Test
    void schemeAuthority() {
        verifyUri3986("http://localhost:80", "http", null, "localhost", 80, "", "", null, null, null);
    }

    @Test
    void schemeAuthorityQuery() {
        verifyUri3986("http://localhost:8080?foo", "http", null, "localhost", 8080, "", "", "foo", "foo", null);
    }

    @Test
    void schemeAuthorityEmptyQuery() {
        verifyUri3986("http://localhost:8081?", "http", null, "localhost", 8081, "", "", "", "", null);
    }

    @Test
    void schemeAuthorityTag() {
        verifyUri3986("http://localhost:8080#foo", "http", null, "localhost", 8080, "", "", null, null, "foo");
    }

    @Test
    void schemeAuthorityEmptyTag() {
        verifyUri3986("http://localhost:8080#", "http", null, "localhost", 8080, "", "", null, null, "");
    }

    @Test
    void schemeAuthorityEmptyQueryEmptyTag() {
        verifyUri3986("http://localhost:8080?#", "http", null, "localhost", 8080, "", "", "", "", "");
    }

    @Test
    void httpsNoPort() {
        verifyUri3986("https://tools.ietf.org/html/rfc3986#section-3", "https", null, "tools.ietf.org", -1,
                "/html/rfc3986", "/html/rfc3986", null, null, "section-3");
    }

    @Test
    void ipv4LiteralWithPort() {
        verifyUri3986("https://foo:goo@123.456.234.122:9", "https", "foo:goo", "123.456.234.122", 9, "", "", null, null,
                null);
    }

    @Test
    void ipv4LiteralWithOutPort() {
        verifyUri3986("https://foo:goo@123.456.234.122:9", "https", "foo:goo", "123.456.234.122", 9, "", "", null, null,
                null);
    }

    @Test
    void ipv6LiteralWithPort() {
        verifyUri3986("https://foo:goo@[::1]:988", "https", "foo:goo", "[::1]", 988, "", "", null, null, null);
    }

    @Test
    void ipv6maxPortTest() {
        verifyUri3986("https://foo:goo@[::1]:65535", "https", "foo:goo", "[::1]", 65535, "", "", null, null, null);
    }

    @Test
    void ipv6LiteralUserInfoWithOutPort() {
        verifyUri3986("https://foo:goo@[::1]", "https", "foo:goo", "[::1]", -1, "", "", null, null, null);
    }

    @Test
    void ipv6HostWithScopeAndPort() {
        verifyUri3986("https://[0:0:0:0:0:0:0:0%0]:49178/path?param=value", "https", null, "[0:0:0:0:0:0:0:0%0]",
                49178, "/path", "/path", "param=value", "param=value", null);
    }

    @Test
    void ipv6HostCompressedWithScope() {
        verifyUri3986("http://[12:3::1%2]:8081/path?param=value#tag", "http", null, "[12:3::1%2]", 8081, "/path",
                "/path", "param=value", "param=value", "tag");
    }

    @Test
    void ipv6HostWithScopeNoPort() {
        verifyUri3986("https://[0:0:0:0:0:0:0:0%0]/path", "https", null, "[0:0:0:0:0:0:0:0%0]", -1, "/path", "/path",
                null, null, null);
    }

    @Test
    void ipv6HostNoUserInfoNoPort() {
        verifyUri3986("http://[::1]/path#tag", "http", null, "[::1]", -1, "/path", "/path", null, null, "tag");
    }

    @Test
    void ipv6HostWithPortAndQuery() {
        verifyUri3986("http://[::1]:8080/path?param=value", "http", null, "[::1]", 8080, "/path", "/path",
                "param=value", "param=value", null);
    }

    @Test
    void ipv4HostNoPort() {
        verifyUri3986("http://1.2.3.4/path?param=value", "http", null, "1.2.3.4", -1, "/path", "/path", "param=value",
                "param=value", null);
    }

    @Test
    void ipv4HostWithPort() {
        verifyUri3986("http://1.2.3.4:2834/path?param=value", "http", null, "1.2.3.4", 2834, "/path", "/path",
                "param=value", "param=value", null);
    }

    @Test
    void ipv4HostWithInvalidPort() {
        assertThrows(IllegalArgumentException.class, () -> new Uri3986("http://1.2.3.4:65536/path?param=value"));
    }

    @Test
    void stringAddressHostHeader() {
        verifyUri3986("http://apple.com:8081/path?param=value", "http", null, "apple.com", 8081, "/path", "/path",
                "param=value", "param=value", null);
    }

    @Test
    void onlyPortInHost() {
        // Browsers treat empty host as localhost
        verifyUri3986("http://:8080/path?param=value", "http", null, "", 8080, "/path", "/path", "param=value",
                "param=value", null);
    }

    @Test
    void encodeTouchesAllComponents() {
        verifyEncodeDecode("http://foo bar@servicetalk.io:8080/path1 space/path2?param =value/? #anchor ",
                "http://foo%20bar@servicetalk.io:8080/path1%20space/path2?param%20=value/?%20#anchor%20");
    }

    @Test
    void encodePreservesExistingEncoded() {
        String encoded = "http://foo%20bar@servicetalk.io:8080/path1%20space/path2?param%20=value/?%20#anchor%20";
        assertEquals(encoded, encode(encoded, US_ASCII, true));
    }

    @Test
    void encodeCanReEncodePercent() {
        String encoded = "http://foo%20bar@foo.com:8080/path1%20space/path2?param%20=value/?%20#anchor%20";
        String reEncoded = "http://foo%2520bar@foo.com:8080/path1%2520space/path2?param%2520=value/?%2520#anchor%2520";
        assertEquals(reEncoded, encode(encoded, US_ASCII, false));
    }

    @Test
    void encodePathQueryFragment() {
        verifyEncodeDecode("/path1 space/path2?param =value/? #anchor ",
                "/path1%20space/path2?param%20=value/?%20#anchor%20");
    }

    @Test
    void encodePathQuery() {
        verifyEncodeDecode("/path1 space/path2?param =value/? ", "/path1%20space/path2?param%20=value/?%20");
    }

    @Test
    void encodeUpToSpaceEscaped() {
        StringBuilder decodedBuilder = new StringBuilder();
        StringBuilder encodedBuilder = new StringBuilder();
        decodedBuilder.append("/path?");
        encodedBuilder.append("/path?");
        for (int i = 0; i < 32; ++i) {
            decodedBuilder.append((char) i);
            encodedBuilder.append('%')
                    .append(toUpperCase(forDigit((i >>> 4) & 0xF, 16)))
                    .append(toUpperCase(forDigit(i & 0xF, 16)));
        }
        verifyEncodeDecode(decodedBuilder.toString(), encodedBuilder.toString());
    }

    @Test
    void encodePathFragment() {
        verifyEncodeDecode("/path1 space/path2#anchor ", "/path1%20space/path2#anchor%20");
    }

    @Test
    void encodePathQueryPlusSign() {
        verifyEncodeDecode("/path1 space+/path2?name=+value", "/path1%20space+/path2?name=+value");
    }

    @Test
    void encodeQueryQuotes() {
        verifyEncodeDecode("/path1?q=\"asdf\"", "/path1?q=%22asdf%22");
    }

    @Test
    void encodeQueryChineseChar() {
        verifyEncodeDecode("/path1?q=\u4F60\u597D", "/path1?q=%E4%BD%A0%E5%A5%BD");
    }

    @Test
    void encodeQueryQuestionMarkPreserved() {
        verifyEncodeDecode("/path1?q=as?df");
    }

    @Test
    void encodeQueryPercentEncoded() {
        verifyEncodeDecode("/path1?q=%value%", "/path1?q=%25value%25");
    }

    @Test
    void encodeQueryNull() {
        verifyEncodeDecode("/path1?q=\0", "/path1?q=%00");
    }

    @Test
    void encodeQueryUTF8() {
        verifyEncodeDecode("/path1?q=❤", "/path1?q=%E2%9D%A4");
    }

    @Test
    void encodeQueryLatin1AsUTF8() {
        verifyEncodeDecode("/path1?q=åäö", "/path1?q=%C3%A5%C3%A4%C3%B6");
    }

    @Test
    void encodeHostName() {
        verifyEncodeDecode("http://foo bar.com/path1", "http://foo%20bar.com/path1");
    }

    @Test
    void encodeIPv6() {
        verifyEncodeDecode("http://[::1]:80/path1");
    }

    @Test
    void encodeIPv6WithScope() {
        verifyEncodeDecode("http://[::1%29]:80/path1");
    }

    @Test
    void encodeIPv4() {
        verifyEncodeDecode("http://1.2.3.4:80/path1");
    }

    @Test
    void encodeHostRegName() {
        verifyEncodeDecode("http://www.foo .com:80/path1", "http://www.foo%20.com:80/path1");
    }

    private static void verifyEncodeDecode(String decoded) {
        verifyEncodeDecode(decoded, decoded);
    }

    private static void verifyEncodeDecode(String decoded, String encoded) {
        assertEquals(encoded, encode(decoded, UTF_8, true));
        assertEquals(decoded, decode(encoded, UTF_8));
    }

    @Test
    void ipv6HostHeaderNoPortTrailingColon() {
        assertThrows(IllegalArgumentException.class, () -> new Uri3986("http://[12:3::1%2]:/path?param=value"));
    }

    @Test
    void ipv6NonBracketWithScope() {
        // https://tools.ietf.org/html/rfc3986#section-3.2.2
        // IPv6 + future must be enclosed in []
        assertThrows(IllegalArgumentException.class,
                () -> new Uri3986("http://0:0:0:0:0:0:0:0%0:49178/path?param=value"));
    }

    @Test
    void ipv6NonBracket() {
        assertThrows(IllegalArgumentException.class, () -> new Uri3986("http://::1/path?param=value"));
    }

    @Test
    void ipv6NoClosingBracketPath() {
        assertThrows(IllegalArgumentException.class, () -> new Uri3986("http://[::1/foo"));
    }

    @Test
    void ipv6NoClosingBracketQuery() {
        assertThrows(IllegalArgumentException.class, () -> new Uri3986("http://[::1?foo"));
    }

    @Test
    void ipv6NoClosingBracket() {
        assertThrows(IllegalArgumentException.class, () -> new Uri3986("http://[::1"));
    }

    @Test
    void ipv6NoClosingBracketUserInfo() {
        assertThrows(IllegalArgumentException.class, () -> new Uri3986("http://foo@[::1"));
    }

    @Test
    void ipv6ContentBeforePort() {
        assertThrows(IllegalArgumentException.class, () -> new Uri3986("http://[::1]foo:8080"));
    }

    @Test
    void ipv6ContentAfterPort() {
        assertThrows(IllegalArgumentException.class, () -> new Uri3986("http://[::1]:8080foo"));
    }

    private static void verifyAppleString(final String expectedUri, final boolean isSsl, final int port,
                                          @Nullable final String userInfo) {
        verifyUri3986(expectedUri, isSsl ? "https" : "http", userInfo, "apple.com", port, "/path/is/here",
                "/path/is/here", "queryname=value", "queryname=value", "tag");
    }

    private static void verifyUri3986(String expectedUri, @Nullable String expectedScheme,
                                      @Nullable String expectedUserInfo, @Nullable String expectedHost, int port,
                                      String expectedPath, String expectedPathDecoded, @Nullable String expectedQuery,
                                      @Nullable String expectedQueryDecoded, @Nullable String expectedFragment) {
        // Manual validation
        Uri uri = new Uri3986(expectedUri);
        verifyUri(uri, expectedUri, expectedScheme, expectedUserInfo, expectedHost, port,
                expectedPath, expectedPathDecoded, expectedQuery, expectedQueryDecoded,
                expectedFragment);

        // Validate against the RFC's regex
        Matcher matcher = VALID_PATTERN.matcher(expectedUri);
        assertThat(true, is(matcher.matches()));
        assertThat("invalid scheme()", uri.scheme(), is(matcher.group(2)));
        assertThat("invalid authority()", uri.authority(), is(matcher.group(4)));
        assertThat("invalid path()", uri.path(), is(matcher.group(5)));
        assertThat("invalid query()", uri.query(), is(matcher.group(7)));
        assertThat("invalid fragment()", uri.fragment(), is(matcher.group(9)));
    }

    static void verifyUri(Uri uri, String expectedUri, @Nullable String expectedScheme,
                          @Nullable String expectedUserInfo, @Nullable String expectedHost, int port,
                          String expectedPath, String expectedPathDecoded, @Nullable String expectedQuery,
                          @Nullable String expectedQueryDecoded, @Nullable String expectedFragment) {
        assertThat("unexpected uri()", uri.uri(), is(expectedUri));
        assertThat("unexpected scheme()", uri.scheme(), is(expectedScheme));
        assertThat("unexpected userInfo()", uri.userInfo(), is(expectedUserInfo));
        assertThat("unexpected host()", uri.host(), is(expectedHost));
        assertThat("unexpected port()", uri.port(), port < 0 ? lessThan(0) : is(port));
        assertThat("unexpected authority()", uri.authority(), is(expectedAuthority(uri)));
        assertThat("unexpected path(Charset)", uri.path(UTF_8), is(expectedPathDecoded));
        assertThat("unexpected path()", uri.path(), is(expectedPath));
        assertThat("unexpected query(Charset)", uri.query(UTF_8), is(expectedQueryDecoded));
        assertThat("unexpected query()", uri.query(), is(expectedQuery));
        assertThat("unexpected fragment()", uri.fragment(), is(expectedFragment));
    }

    @Nullable
    private static String expectedAuthority(Uri uri) {
        String host = uri.host();
        if (host == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        String userInfo = uri.userInfo();
        if (userInfo != null) {
            sb.append(userInfo).append('@');
        }
        sb.append(host);
        if (uri.port() >= 0) {
            sb.append(':').append(uri.port());
        }
        return sb.toString();
    }
}
