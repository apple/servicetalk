/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

import static java.lang.Character.forDigit;
import static java.lang.Character.toUpperCase;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.regex.Pattern.compile;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
public class Uri3986Test {
    /**
     * <a href="https://tools.ietf.org/html/rfc3986#appendix-B">Parsing a URI Reference with a Regular Expression</a>
     */
    private static final Pattern VALID_PATTERN = compile("^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?");

    @Test
    public void fullHttpURI() {
        verifyAppleString("http://apple.com:8080/path/is/here?queryname=value#tag", false, 8080, null);
    }

    @Test
    public void fullHttpsURI() {
        verifyAppleString("https://apple.com:8080/path/is/here?queryname=value#tag", true, 8080, null);
    }

    @Test
    public void ignoreUserInfo() {
        verifyAppleString("http://user:password@apple.com:8080/path/is/here?queryname=value#tag", false, 8080,
                "user:password");
    }

    @Test
    public void ignoreUserInfoPctEncoded() {
        verifyAppleString("http://user%20:passwo%2Frd@apple.com:12/path/is/here?queryname=value#tag", false, 12,
                "user%20:passwo%2Frd");
    }

    @Test(expected = IllegalArgumentException.class)
    public void ignoreUserInfoPctEncodedIllegalReservedCharacterWithColon() {
        new Uri3986("http://user:passwo/rd@apple.com:8080/path/is/here?queryname=value#tag");
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateSchema() {
        new Uri3986("http://foo://test.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyScheme() {
        new Uri3986("://test.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void portTooBig() {
        new Uri3986("http://foo.com:65536");
    }

    @Test(expected = IllegalArgumentException.class)
    public void portNegative() {
        new Uri3986("http://foo.com:-1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void portInvalidAtEnd() {
        new Uri3986("http://foo.com:6553a");
    }

    @Test(expected = IllegalArgumentException.class)
    public void portInvalidBegin() {
        new Uri3986("http://foo.com:a");
    }

    @Test(expected = IllegalArgumentException.class)
    public void portInvalidMiddle() {
        new Uri3986("http://foo.com:1ab2");
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyIpLiteral() {
        new Uri3986("http://[]");
    }

    @Test(expected = IllegalArgumentException.class)
    public void unexpectedIpLiteralClose() {
        new Uri3986("http://]");
    }

    @Test(expected = IllegalArgumentException.class)
    public void unexpectedIpLiteralOpen() {
        new Uri3986("http://[[");
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateUserInfo() {
        new Uri3986("http://foo@bar@apple.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void malformedAuthority() {
        new Uri3986("http://blah@apple.com:80@apple.com");
    }

    @Test
    public void emptyUserInfoAndAuthority() {
        verifyUri3986("@", null, null, null, -1, "@", "@", null, null, null);
    }

    @Test
    public void emptyAuthority() {
        verifyUri3986("@foo", null, null, null, -1, "@foo", "@foo", null, null, null);
    }

    @Test
    public void doubleSlashWithEmptyAuthorityThrows() {
        verifyUri3986("//@foo", null, "", "foo", -1, "", "", null, null, null);
    }

    @Test
    public void nonHttpSchema() {
        verifyUri3986("foo://test.com", "foo", null, "test.com", -1, "", "", null, null, null);
    }

    @Test
    public void hostWithNoSlash() {
        verifyUri3986("user@host", null, null, null, -1, "user@host", "user@host", null, null, null);
    }

    @Test
    public void emptyUserInfo() {
        verifyUri3986("user@", null, null, null, -1, "user@", "user@", null, null, null);
    }

    @Test
    public void schemeParsedCaseSensative() {
        verifyUri3986("HTTP://test.com", "HTTP", null, "test.com", -1, "", "", null, null, null);
    }

    @Test
    public void invalidSchemaToken() {
        verifyUri3986("http:/test.com", "http", null, null, -1, "/test.com", "/test.com", null, null, null);
    }

    @Test
    public void invalidSchemaToken2() {
        verifyUri3986("http:test.com", "http", null, null, -1, "test.com", "test.com", null, null, null);
    }

    @Test
    public void authorityForm() {
        // https://tools.ietf.org/html/rfc3986#section-3
        // urn:example:animal:ferret:nose
        verifyUri3986("a.apple.com:81", "a.apple.com", null, null, -1, "81", "81", null, null, null);
    }

    @Test
    public void unusualSchemeAuthorityForm() {
        verifyUri3986("a.apple.com:81/path", "a.apple.com", null, null, -1, "81/path", "81/path", null, null, null);
    }

    @Test
    public void hostLookAlikeInPath() {
        verifyUri3986("/@foo", null, null, null, -1, "/@foo", "/@foo", null, null, null);
    }

    @Test
    public void doubleSlashWithPortLookAlike() {
        verifyUri3986("//81", null, null, "81", -1, "", "", null, null, null);
    }

    @Test
    public void doubleSlashWithoutScheme() {
        verifyUri3986("//foo.com/path?query#tag", null, null, "foo.com", -1, "/path", "/path", "query", "query", "tag");
    }

    @Test
    public void doubleSlashAfterInitialPath() {
        verifyUri3986("f//foo.com/path?query#tag", null, null, null, -1, "f//foo.com/path", "f//foo.com/path",
                "query", "query", "tag");
    }

    @Test
    public void pathWithAuthorityEmbedded() {
        verifyUri3986("http://user/mode@apple.com:8080/path/is/here?queryname=value#tag", "http", null, "user", -1,
                "/mode@apple.com:8080/path/is/here", "/mode@apple.com:8080/path/is/here", "queryname=value",
                "queryname=value", "tag");
    }

    @Test
    public void userInfoAndHostAfterFirstPathComponent() {
        verifyUri3986("user/mode@apple.com:8080/path/is/here?queryname=value#tag", null, null, null, -1,
                "user/mode@apple.com:8080/path/is/here", "user/mode@apple.com:8080/path/is/here", "queryname=value",
                "queryname=value", "tag");
    }

    @Test
    public void hostAfterFirstPathSegment() {
        verifyUri3986("user/apple.com/path/is/here?queryname=value#tag", null, null, null, -1,
                "user/apple.com/path/is/here", "user/apple.com/path/is/here", "queryname=value", "queryname=value",
                "tag");
    }

    @Test
    public void poundEndsRequestTarget() {
        verifyUri3986("apple.com#tag", null, null, null, -1, "apple.com", "apple.com", null, null, "tag");
    }

    @Test
    public void doubleSlashWithEmptyUserInfoAndAuthority() {
        verifyUri3986("//", null, null, "", -1, "", "", null, null, null);
    }

    @Test
    public void doubleSlashWithUserInfo() {
        verifyUri3986("//user@", null, "user", "", -1, "", "", null, null, null);
    }

    @Test
    public void schemeWithNoRequestTargetQuery() {
        verifyUri3986("http://?", "http", null, "", -1, "", "", "", "", null);
    }

    @Test
    public void schemeWithNoRequestTargetPound() {
        verifyUri3986("http://#", "http", null, "", -1, "", "", null, null, "");
    }

    @Test
    public void schemeWithNoRequestTarget() {
        verifyUri3986("http:///", "http", null, "", -1, "/", "/", null, null, null);
    }

    @Test
    public void justHostName() {
        verifyUri3986("example.com", null, null, null, -1, "example.com", "example.com", null, null, null);
    }

    @Test
    public void cssAndQueryIsParsed() {
        verifyUri3986("http://localhost:8080/app.css?v1", "http", null, "localhost", 8080, "/app.css", "/app.css",
                "v1", "v1", null);
    }

    @Test
    public void userInfoDelimiterAfterTypicalHost() {
        verifyUri3986("https://apple.com:8080@user/path%20/is/here?queryname=value#tag%20", "https", "apple.com:8080",
                "user", -1, "/path%20/is/here", "/path /is/here", "queryname=value", "queryname=value", "tag%20");
    }

    @Test
    public void userInfoNoPort() {
        verifyUri3986("http://user:foo@apple.com/path/is/here?query%20name=value#tag", "http", "user:foo", "apple.com",
                -1, "/path/is/here", "/path/is/here", "query%20name=value", "query name=value", "tag");
    }

    @Test
    public void justSlash() {
        verifyUri3986("/", null, null, null, -1, "/", "/", null, null, null);
    }

    @Test
    public void absolutePath() {
        verifyUri3986("/foo", null, null, null, -1, "/foo", "/foo", null, null, null);
    }

    @Test
    public void dotSegmentPath() {
        verifyUri3986("./this:that", null, null, null, -1, "./this:that", "./this:that", null, null, null);
    }

    @Test
    public void nonDotSegmentScheme() {
        verifyUri3986("this:that", "this", null, null, -1, "that", "that", null, null, null);
    }

    @Test
    public void justAsterisk() {
        verifyUri3986("*", null, null, null, -1, "*", "*", null, null, null);
    }

    @Test
    public void justPath() {
        verifyUri3986("/path/is/here?queryname=value#tag", null, null, null, -1, "/path/is/here", "/path/is/here",
                "queryname=value", "queryname=value", "tag");
    }

    @Test
    public void justQuery() {
        verifyUri3986("?queryname", null, null, null, -1, "", "", "queryname", "queryname", null);
    }

    @Test
    public void justFragment() {
        verifyUri3986("#tag", null, null, null, -1, "", "", null, null, "tag");
    }

    @Test
    public void schemeAuthority() {
        verifyUri3986("http://localhost:80", "http", null, "localhost", 80, "", "", null, null, null);
    }

    @Test
    public void schemeAuthorityQuery() {
        verifyUri3986("http://localhost:8080?foo", "http", null, "localhost", 8080, "", "", "foo", "foo", null);
    }

    @Test
    public void schemeAuthorityEmptyQuery() {
        verifyUri3986("http://localhost:8081?", "http", null, "localhost", 8081, "", "", "", "", null);
    }

    @Test
    public void schemeAuthorityTag() {
        verifyUri3986("http://localhost:8080#foo", "http", null, "localhost", 8080, "", "", null, null, "foo");
    }

    @Test
    public void schemeAuthorityEmptyTag() {
        verifyUri3986("http://localhost:8080#", "http", null, "localhost", 8080, "", "", null, null, "");
    }

    @Test
    public void schemeAuthorityEmptyQueryEmptyTag() {
        verifyUri3986("http://localhost:8080?#", "http", null, "localhost", 8080, "", "", "", "", "");
    }

    @Test
    public void httpsNoPort() {
        verifyUri3986("https://tools.ietf.org/html/rfc3986#section-3", "https", null, "tools.ietf.org", -1,
                "/html/rfc3986", "/html/rfc3986", null, null, "section-3");
    }

    @Test
    public void ipv4LiteralWithPort() {
        verifyUri3986("https://foo:goo@123.456.234.122:9", "https", "foo:goo", "123.456.234.122", 9, "", "", null, null,
                null);
    }

    @Test
    public void ipv4LiteralWithOutPort() {
        verifyUri3986("https://foo:goo@123.456.234.122:9", "https", "foo:goo", "123.456.234.122", 9, "", "", null, null,
                null);
    }

    @Test
    public void ipv6LiteralWithPort() {
        verifyUri3986("https://foo:goo@[::1]:988", "https", "foo:goo", "[::1]", 988, "", "", null, null, null);
    }

    @Test
    public void ipv6maxPortTest() {
        verifyUri3986("https://foo:goo@[::1]:65535", "https", "foo:goo", "[::1]", 65535, "", "", null, null, null);
    }

    @Test
    public void ipv6LiteralUserInfoWithOutPort() {
        verifyUri3986("https://foo:goo@[::1]", "https", "foo:goo", "[::1]", -1, "", "", null, null, null);
    }

    @Test
    public void ipv6HostWithScopeAndPort() {
        verifyUri3986("https://[0:0:0:0:0:0:0:0%0]:49178/path?param=value", "https", null, "[0:0:0:0:0:0:0:0%0]",
                49178, "/path", "/path", "param=value", "param=value", null);
    }

    @Test
    public void ipv6HostCompressedWithScope() {
        verifyUri3986("http://[12:3::1%2]:8081/path?param=value#tag", "http", null, "[12:3::1%2]", 8081, "/path",
                "/path", "param=value", "param=value", "tag");
    }

    @Test
    public void ipv6HostWithScopeNoPort() {
        verifyUri3986("https://[0:0:0:0:0:0:0:0%0]/path", "https", null, "[0:0:0:0:0:0:0:0%0]", -1, "/path", "/path",
                null, null, null);
    }

    @Test
    public void ipv6HostNoUserInfoNoPort() {
        verifyUri3986("http://[::1]/path#tag", "http", null, "[::1]", -1, "/path", "/path", null, null, "tag");
    }

    @Test
    public void ipv6HostWithPortAndQuery() {
        verifyUri3986("http://[::1]:8080/path?param=value", "http", null, "[::1]", 8080, "/path", "/path",
                "param=value", "param=value", null);
    }

    @Test
    public void ipv4HostNoPort() {
        verifyUri3986("http://1.2.3.4/path?param=value", "http", null, "1.2.3.4", -1, "/path", "/path", "param=value",
                "param=value", null);
    }

    @Test
    public void ipv4HostWithPort() {
        verifyUri3986("http://1.2.3.4:2834/path?param=value", "http", null, "1.2.3.4", 2834, "/path", "/path",
                "param=value", "param=value", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv4HostWithInvalidPort() {
        new Uri3986("http://1.2.3.4:65536/path?param=value");
    }

    @Test
    public void stringAddressHostHeader() {
        verifyUri3986("http://apple.com:8081/path?param=value", "http", null, "apple.com", 8081, "/path", "/path",
                "param=value", "param=value", null);
    }

    @Test
    public void onlyPortInHost() {
        // Browsers treat empty host as localhost
        verifyUri3986("http://:8080/path?param=value", "http", null, "", 8080, "/path", "/path", "param=value",
                "param=value", null);
    }

    @Test
    public void encodeTouchesAllComponents() {
        verifyEncodeDecode("http://foo bar@servicetalk.io:8080/path1 space/path2?param =value/? #anchor ",
                "http://foo%20bar@servicetalk.io:8080/path1%20space/path2?param%20=value/?%20#anchor%20");
    }

    @Test
    public void encodePreservesExistingEncoded() {
        String encoded = "http://foo%20bar@servicetalk.io:8080/path1%20space/path2?param%20=value/?%20#anchor%20";
        assertEquals(encoded, Uri3986.encode(encoded, US_ASCII, true));
    }

    @Test
    public void encodeCanReEncodePercent() {
        String encoded = "http://foo%20bar@foo.com:8080/path1%20space/path2?param%20=value/?%20#anchor%20";
        String reEncoded = "http://foo%2520bar@foo.com:8080/path1%2520space/path2?param%2520=value/?%2520#anchor%2520";
        assertEquals(reEncoded, Uri3986.encode(encoded, US_ASCII, false));
    }

    @Test
    public void encodePathQueryFragment() {
        verifyEncodeDecode("/path1 space/path2?param =value/? #anchor ",
                "/path1%20space/path2?param%20=value/?%20#anchor%20");
    }

    @Test
    public void encodePathQuery() {
        verifyEncodeDecode("/path1 space/path2?param =value/? ", "/path1%20space/path2?param%20=value/?%20");
    }

    @Test
    public void encodeUpToSpaceEscaped() {
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
    public void encodePathFragment() {
        verifyEncodeDecode("/path1 space/path2#anchor ", "/path1%20space/path2#anchor%20");
    }

    @Test
    public void encodePathQueryPlusSign() {
        verifyEncodeDecode("/path1 space+/path2?name=+value", "/path1%20space+/path2?name=+value");
    }

    @Test
    public void encodeQueryQuotes() {
        verifyEncodeDecode("/path1?q=\"asdf\"", "/path1?q=%22asdf%22");
    }

    @Test
    public void encodeQueryChineseChar() {
        verifyEncodeDecode("/path1?q=\u4F60\u597D", "/path1?q=%E4%BD%A0%E5%A5%BD");
    }

    @Test
    public void encodeQueryQuestionMarkPreserved() {
        verifyEncodeDecode("/path1?q=as?df");
    }

    @Test
    public void encodeQueryPercentEncoded() {
        verifyEncodeDecode("/path1?q=%value%", "/path1?q=%25value%25");
    }

    @Test
    public void encodeQueryNull() {
        verifyEncodeDecode("/path1?q=\0", "/path1?q=%00");
    }

    @Test
    public void encodeQueryUTF8() {
        verifyEncodeDecode("/path1?q=❤", "/path1?q=%E2%9D%A4");
    }

    @Test
    public void encodeQueryLatin1AsUTF8() {
        verifyEncodeDecode("/path1?q=åäö", "/path1?q=%C3%A5%C3%A4%C3%B6");
    }

    @Test
    public void encodeHostName() {
        verifyEncodeDecode("http://foo bar.com/path1", "http://foo%20bar.com/path1");
    }

    @Test
    public void encodeIPv6() {
        verifyEncodeDecode("http://[::1]:80/path1");
    }

    @Test
    public void encodeIPv6WithScope() {
        verifyEncodeDecode("http://[::1%29]:80/path1");
    }

    @Test
    public void encodeIPv4() {
        verifyEncodeDecode("http://1.2.3.4:80/path1");
    }

    @Test
    public void encodeHostRegName() {
        verifyEncodeDecode("http://www.foo .com:80/path1", "http://www.foo%20.com:80/path1");
    }

    private static void verifyEncodeDecode(String decoded) {
        verifyEncodeDecode(decoded, decoded);
    }

    private static void verifyEncodeDecode(String decoded, String encoded) {
        assertEquals(encoded, Uri3986.encode(decoded, UTF_8, true));
        assertEquals(decoded, Uri3986.decode(encoded, UTF_8));
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv6HostHeaderNoPortTrailingColon() {
        new Uri3986("http://[12:3::1%2]:/path?param=value");
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv6NonBracketWithScope() {
        // https://tools.ietf.org/html/rfc3986#section-3.2.2
        // IPv6 + future must be enclosed in []
        new Uri3986("http://0:0:0:0:0:0:0:0%0:49178/path?param=value");
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv6NonBracket() {
        new Uri3986("http://::1/path?param=value");
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv6NoClosingBracketPath() {
        new Uri3986("http://[::1/foo");
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv6NoClosingBracketQuery() {
        new Uri3986("http://[::1?foo");
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv6NoClosingBracket() {
        new Uri3986("http://[::1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv6NoClosingBracketUserInfo() {
        new Uri3986("http://foo@[::1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv6ContentBeforePort() {
        new Uri3986("http://[::1]foo:8080");
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv6ContentAfterPort() {
        new Uri3986("http://[::1]:8080foo");
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
