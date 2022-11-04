/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.HostAndPort;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Spliterator;
import java.util.stream.StreamSupport;

import static io.servicetalk.http.api.HttpHeaderNames.AUTHORIZATION;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.utils.internal.NetworkUtils.isValidIpV6Address;
import static java.lang.System.lineSeparator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.addAll;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractHttpRequestMetaDataTest<T extends HttpRequestMetaData> {

    protected T fixture;

    protected abstract void createFixture(String uri);

    protected abstract void createFixture(String uri, HttpRequestMethod method);

    // https://tools.ietf.org/html/rfc7230#section-5.3.1
    @Test
    void testParseUriOriginForm() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");

        assertNull(fixture.scheme());
        assertNull(fixture.userInfo());
        assertNull(fixture.host());
        assertThat(fixture.port(), lessThan(0));
        assertEquals("/some/path", fixture.path());
        assertEquals("/some/path", fixture.rawPath());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.rawQuery());
        assertEquals("/some/path?foo=bar&abc=def&foo=baz", fixture.requestTarget());

        assertNull(fixture.effectiveHostAndPort());

        // Host header provides effective host and port
        fixture.headers().set(HOST, "other.site.com:8080");
        assertEffectiveHostAndPort("other.site.com", 8080);
    }

    // https://tools.ietf.org/html/rfc7230#section-5.3.2
    @Test
    void testParseHttpUriAbsoluteForm() {
        createFixture("http://my.site.com/some/path?foo=bar&abc=def&foo=baz");

        assertEquals("http", fixture.scheme());
        assertNull(fixture.userInfo());
        assertEquals("my.site.com", fixture.host());
        assertThat(fixture.port(), lessThan(0));
        assertEquals("/some/path", fixture.path());
        assertEquals("/some/path", fixture.rawPath());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.rawQuery());
        assertEquals("http://my.site.com/some/path?foo=bar&abc=def&foo=baz", fixture.requestTarget());

        assertEffectiveHostAndPort("my.site.com");

        // Host header ignored when request-target is absolute.
        fixture.headers().set(HOST, "other.site.com:8080");
        assertEffectiveHostAndPort("my.site.com");
    }

    @Test
    void testParseHttpsUriAbsoluteForm() {
        createFixture("https://jdoe@my.site.com/some/path?foo=bar&abc=def&foo=baz");

        assertEquals("https", fixture.scheme());
        assertEquals("jdoe", fixture.userInfo());
        assertEquals("my.site.com", fixture.host());
        assertThat(fixture.port(), lessThan(0));
        assertEquals("/some/path", fixture.path());
        assertEquals("/some/path", fixture.rawPath());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.rawQuery());
        assertEquals("https://jdoe@my.site.com/some/path?foo=bar&abc=def&foo=baz", fixture.requestTarget());

        assertEffectiveHostAndPort("my.site.com");

        // Host header ignored when request-target is absolute
        fixture.headers().set(HOST, "other.site.com:8080");
        assertEffectiveHostAndPort("my.site.com");
    }

    // https://tools.ietf.org/html/rfc7230#section-5.3.3
    @Test
    void testParseHttpUriAuthorityForm() {
        createFixture("my.site.com:80", CONNECT);

        assertNull(fixture.scheme());
        assertNull(fixture.userInfo());
        assertEquals("my.site.com", fixture.host());
        assertEquals(80, fixture.port());
        assertEquals("", fixture.path());
        assertEquals("", fixture.rawPath());
        assertNull(fixture.rawQuery());
        assertEquals("my.site.com:80", fixture.requestTarget());

        assertEffectiveHostAndPort("my.site.com", 80);

        // Host header ignored when request-target has authority form
        fixture.headers().set(HOST, "other.site.com:8080");
        assertEffectiveHostAndPort("my.site.com", 80);
    }

    // https://tools.ietf.org/html/rfc7230#section-5.3.4
    @Test
    void testParseHttpUriAsteriskForm() {
        createFixture("*");

        assertNull(fixture.scheme());
        assertNull(fixture.userInfo());
        assertNull(fixture.host());
        assertThat(fixture.port(), lessThan(0));
        assertEquals("*", fixture.path());
        assertEquals("*", fixture.rawPath());
        assertNull(fixture.rawQuery());
        assertEquals("*", fixture.requestTarget());

        assertNull(fixture.effectiveHostAndPort());

        // Host header provides effective host and port
        fixture.headers().set(HOST, "other.site.com:8080");
        assertEffectiveHostAndPort("other.site.com", 8080);
    }

    @Test
    void testParseUriOriginFormWithHostHeader() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");
        fixture.headers().set(HOST, "host.header.com");

        assertNull(fixture.scheme());
        assertNull(fixture.userInfo());
        assertNull(fixture.host());
        assertThat(fixture.port(), lessThan(0));
        assertEquals("/some/path", fixture.path());
        assertEquals("/some/path", fixture.rawPath());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.rawQuery());
        assertEquals("/some/path?foo=bar&abc=def&foo=baz", fixture.requestTarget());

        assertEffectiveHostAndPort("host.header.com");

        // Host header provides effective host and port
        fixture.headers().set(HOST, "other.site.com:8080");
        assertEffectiveHostAndPort("other.site.com", 8080);

        // Test host header changes port, but keeps the same hostname.
        fixture.headers().set(HOST, "other.site.com:8081");
        assertEffectiveHostAndPort("other.site.com", 8081);
    }

    @Test
    void testParseHttpUriAbsoluteFormWithHost() {
        createFixture("http://my.site.com/some/path?foo=bar&abc=def&foo=baz");
        fixture.headers().set(HOST, "host.header.com");

        assertEquals("http", fixture.scheme());
        assertNull(fixture.userInfo());
        assertEquals("my.site.com", fixture.host());
        assertThat(fixture.port(), lessThan(0));
        assertEquals("/some/path", fixture.path());
        assertEquals("/some/path", fixture.rawPath());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.rawQuery());
        assertEquals("http://my.site.com/some/path?foo=bar&abc=def&foo=baz", fixture.requestTarget());

        assertEffectiveHostAndPort("my.site.com");

        // Host header ignored when request-target is absolute.
        fixture.headers().set(HOST, "other.site.com:8080");
        assertEffectiveHostAndPort("my.site.com");
    }

    @Test
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    void testEffectiveHostIPv6NoPort() {
        createFixture("some/path?foo=bar&abc=def&foo=baz");
        fixture.headers().set(HOST, "[1:2:3::5]");

        assertEffectiveHostAndPort("1:2:3::5");
    }

    @Test
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    void testEffectiveHostIPv6WithPort() {
        createFixture("some/path?foo=bar&abc=def&foo=baz");
        fixture.headers().set(HOST, "[1:2:3::5]:8080");

        assertEffectiveHostAndPort("1:2:3::5", 8080);
    }

    @Test
    void testEffectiveHostIPv6EmptyPort() {
        createFixture("some/path?foo=bar&abc=def&foo=baz");
        fixture.headers().set(HOST, "[1:2:3::5]:");
        assertThrows(IllegalArgumentException.class, () -> fixture.effectiveHostAndPort());
    }

    @Test
    void testEffectiveHostIPv6BeforePort() {
        createFixture("some/path?foo=bar&abc=def&foo=baz");
        fixture.headers().set(HOST, "[1:2:3::5]:f8080");

        assertThrows(IllegalArgumentException.class, () -> fixture.effectiveHostAndPort());
    }

    @Test
    void testEffectiveHostIPv6AfterPort() {
        createFixture("some/path?foo=bar&abc=def&foo=baz");
        fixture.headers().set(HOST, "[1:2:3::5]:8080f");

        assertThrows(IllegalArgumentException.class, () -> fixture.effectiveHostAndPort());
    }

    @Test
    void testEffectiveHostIPv6InvalidPortDelimiter() {
        createFixture("some/path?foo=bar&abc=def&foo=baz");
        fixture.headers().set(HOST, "[1:2:3::5]f8080");

        assertThrows(IllegalArgumentException.class, () -> fixture.effectiveHostAndPort());
    }

    @Test
    void testEffectiveHostIPv6NoClosingBrace() {
        createFixture("some/path?foo=bar&abc=def&foo=baz");
        fixture.headers().set(HOST, "[1:2:3::5");

        assertThrows(IllegalArgumentException.class, () -> fixture.effectiveHostAndPort());
    }

    @Test
    void testSlashAddedToPath() {
        createFixture("//authority");
        fixture.path("path");
        assertEquals("//authority/path", fixture.requestTarget());
    }

    @Test
    void testSlashNotAddedToEmptyPath() {
        createFixture("//authority");
        fixture.path("");
        assertEquals("//authority", fixture.requestTarget());
    }

    @Test
    void testSetPathWithoutLeadingSlash() {
        createFixture("temp");
        fixture.path("foo");
        assertEquals("foo", fixture.requestTarget());
    }

    @Test
    void testAppendSegmentsToPath() {
        createFixture("base");
        fixture.appendPathSegments("foo", "/$", "bar");
        assertEquals("base/foo/%2F$/bar", fixture.requestTarget());
    }

    @Test
    void testAppendSegmentsToPathEncoded() {
        createFixture("/base/");
        fixture.appendPathSegments("foo", "/ ", "bar");
        assertEquals("/base/foo/%2F%20/bar", fixture.requestTarget());
    }

    @Test
    void testAppendSegmentsToPathWithQueryParam() {
        createFixture("/base/?baz=123");
        fixture.appendPathSegments("foo", "/$", "bar");
        assertEquals("/base/foo/%2F$/bar?baz=123", fixture.requestTarget());
    }

    @Test
    void testAppendSegmentsToPathWithoutTrailingSlash() {
        createFixture("/base?baz=123");
        fixture.appendPathSegments("foo", "/$", "bar");
        assertEquals("/base/foo/%2F$/bar?baz=123", fixture.requestTarget());
    }

    @Test
    void testAppendAuthorityInFirstSegmentIsEscaped() {
        createFixture("");
        fixture.appendPathSegments("//authority");
        assertEquals("%2F%2Fauthority", fixture.requestTarget());
    }

    @Test
    void testNoPathAppendColonInFirstSegment() {
        createFixture("");
        assertThrows(IllegalArgumentException.class, () -> fixture.appendPathSegments("foo:"));
    }

    @Test
    void testEmptyPathAppendColonInFirstSegment() {
        createFixture("/");
        assertThrows(IllegalArgumentException.class, () -> fixture.appendPathSegments("foo:"));
    }

    @Test
    void testAppendNoSegmentToPath() {
        createFixture("base/");
        assertThrows(IllegalArgumentException.class, () -> fixture.appendPathSegments());
    }

    @Test
    void testSetRawPathWithoutLeadingSlash() {
        createFixture("temp");
        fixture.rawPath("foo");
        assertEquals("foo", fixture.requestTarget());
    }

    @Test
    void testRawPathCanOverrideQueryComponent() {
        // asserting current behavior, rawPath doesn't attempt full validation and assumes the path(..) method is used
        // to encode if necessary.
        createFixture("/temp");
        fixture.rawPath("foo?bar");
        assertEquals("foo?bar", fixture.requestTarget());
        assertEquals("bar", fixture.rawQuery());
    }

    @Test
    void testRawPathCanOverrideFragmentComponent() {
        // asserting current behavior, rawPath doesn't attempt full validation and assumes the path(..) method is used
        // to encode if necessary.
        createFixture("/temp");
        fixture.rawPath("foo#bar");
        assertEquals("foo#bar", fixture.requestTarget());
    }

    @Test
    void testEncodedPathCanNotOverrideQueryComponent() {
        createFixture("/temp");
        fixture.path("foo?bar");
        assertEquals("foo%3Fbar", fixture.requestTarget());
    }

    @Test
    void testEncodedPathCanNotOverrideFragmentComponent() {
        createFixture("/temp");
        fixture.path("foo#bar");
        assertEquals("foo%23bar", fixture.requestTarget());
    }

    @Test
    void testRelativePathCannotContainColonInFirstSegment() {
        createFixture("temp");
        assertThrows(IllegalArgumentException.class, () -> fixture.rawPath("foo:bar"));
    }

    @Test
    void testPathCannotInjectAuthority() {
        createFixture("/temp");
        assertThrows(IllegalArgumentException.class, () -> fixture.rawPath("//authorityinjection"));
    }

    @Test
    void testAuthoritySetAllowsPathAuthorityLookalike() {
        createFixture("//realauthority");
        fixture.rawPath("//authorityinjection");
        assertEquals("//realauthority//authorityinjection", fixture.requestTarget());
        assertEquals("realauthority", fixture.host());
        assertEquals("//authorityinjection", fixture.path());
    }

    @Test
    void testEmptyPathWithQueryAndFragment() {
        createFixture("/temp?foo#bar");
        fixture.rawPath("");
        assertEquals("?foo#bar", fixture.requestTarget());
        assertEquals("", fixture.path());
        assertEquals("foo", fixture.query());
    }

    @Test
    void testNullQueryWithPathAndFragment() {
        createFixture("/temp?foo#bar");
        fixture.rawQuery(null);
        assertEquals("/temp#bar", fixture.requestTarget());
        assertEquals("/temp", fixture.path());
        assertNull(fixture.query());
    }

    @Test
    void testReplacePathInAbsoluteForm() {
        createFixture("http://my.site.com/some/path?foo=bar&abc=def&foo=baz");
        fixture.path("/new/$path$");

        assertEquals("http://my.site.com/new/$path$?foo=bar&abc=def&foo=baz", fixture.requestTarget());
    }

    @Test
    void testSetEmptyPathInAbsoluteForm() {
        createFixture("http://my.site.com/some/path?foo=bar&abc=def&foo=baz");
        fixture.path("");

        assertEquals("http://my.site.com?foo=bar&abc=def&foo=baz", fixture.requestTarget());
    }

    @Test
    void testReplacePathInOriginForm() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");
        fixture.path("/new/$path ");

        assertEquals("/new/$path%20?foo=bar&abc=def&foo=baz", fixture.requestTarget());
    }

    @Test
    void testSetEmptyPathInOriginForm() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");
        fixture.path("");

        assertEquals("?foo=bar&abc=def&foo=baz", fixture.requestTarget());
    }

    @Test
    void testReplaceExistingQuery() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");
        fixture.rawQuery("new=query");

        assertEquals("/some/path?new=query", fixture.requestTarget());
    }

    @Test
    void testSetRemoveExistingQuery() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");
        fixture.rawQuery(null);

        assertEquals("/some/path", fixture.requestTarget());
    }

    @Test
    void testSetClearExistingQuery() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");
        fixture.rawQuery("");

        assertEquals("/some/path?", fixture.requestTarget());
    }

    @Test
    void testAddQuery() {
        createFixture("/some/path");
        fixture.rawQuery("new=query");

        assertEquals("/some/path?new=query", fixture.requestTarget());
    }

    @Test
    void testParseQuery() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");

        assertEquals(asList("foo", "abc"), iteratorAsList(fixture.queryParametersKeys().iterator()));
        assertEquals("bar", fixture.queryParameter("foo"));
        assertEquals(asList("bar", "baz"), iteratorAsList(fixture.queryParametersIterator("foo")));
        assertEquals("def", fixture.queryParameter("abc"));
        assertEquals(singletonList("def"), iteratorAsList(fixture.queryParametersIterator("abc")));
    }

    @Test
    void testAddQueryEquals() {
        createFixture("/some/path");
        fixture.addQueryParameter("foo", "bar");
        T oldFixture = fixture;
        createFixture("/some/path");
        assertNotEquals(fixture, oldFixture);
    }

    @Test
    void testAddQueryDecodeRequestTarget() {
        createFixture("/some/path");
        fixture.addQueryParameter("foo", "bar");
        assertEquals("/some/path?foo=bar", fixture.requestTarget(UTF_8));
    }

    @Test
    void testAddQueryEncodeRequestTarget() {
        createFixture("/some/path");
        fixture.addQueryParameter("foo", "bar");
        fixture.requestTarget("/some/new/path");
        assertEquals("/some/new/path", fixture.requestTarget(UTF_8));
    }

    @Test
    void testParseEmptyAndEncodeQuery() {
        createFixture("/some/path");
        fixture.addQueryParameter("foo", "bar");
        assertEquals("/some/path?foo=bar", fixture.requestTarget());
    }

    @Test
    void testQueryDoesntChangeState() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");
        fixture.queryParameters();

        assertEquals("/some/path?foo=bar&abc=def&foo=baz", fixture.requestTarget());
    }

    @Test
    void testUpdateQueryParameters() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");
        fixture.setQueryParameter("abc", "new");
        assertEquals("/some/path?foo=bar&foo=baz&abc=new", fixture.requestTarget());

        for (Iterator<String> i = fixture.queryParametersIterator("foo"); i.hasNext();) {
            i.next();
            i.remove();
        }
        assertEquals("/some/path?abc=new", fixture.requestTarget());

        fixture.addQueryParameters("foo", asList("bar", "baz"));
        assertEquals("/some/path?abc=new&foo=bar&foo=baz", fixture.requestTarget());
        assertEquals("abc=new&foo=bar&foo=baz", fixture.query());

        assertTrue(fixture.removeQueryParameters("foo"));
        assertEquals("/some/path?abc=new", fixture.requestTarget());
        assertFalse(fixture.removeQueryParameters("foo"));

        fixture.addQueryParameters("foo", "bar", "baz");
        assertEquals("/some/path?abc=new&foo=bar&foo=baz", fixture.requestTarget());
        assertEquals("abc=new&foo=bar&foo=baz", fixture.query());

        assertTrue(fixture.removeQueryParameters("foo", "baz"));
        assertEquals("/some/path?abc=new&foo=bar", fixture.requestTarget());
        assertEquals("abc=new&foo=bar", fixture.query());

        fixture.setQueryParameters("foo", singletonList("baz"));
        fixture.setQueryParameters("abc", "ghi", "jkl");
        assertEquals("/some/path?abc=ghi&abc=jkl&foo=baz", fixture.requestTarget());
        assertTrue(fixture.hasQueryParameter("foo"));
        assertTrue(fixture.hasQueryParameter("abc", "jkl"));
        assertEquals(3, fixture.queryParametersSize());

        for (Iterator<Entry<String, String>> i = fixture.queryParameters().iterator(); i.hasNext();) {
            i.next();
            i.remove();
        }
        assertEquals("/some/path", fixture.requestTarget());
        assertNull(fixture.query());
        assertEquals(0, fixture.queryParametersSize());
        assertTrue(fixture.queryParametersKeys().isEmpty());
    }

    @Test
    void testSetRequestTargetAndReparse() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");

        // parse it
        assertEquals("/some/path?foo=bar&abc=def&foo=baz", fixture.requestTarget());
        assertEquals("/some/path", fixture.rawPath());
        assertEquals("/some/path", fixture.path());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.rawQuery());
        assertEquals(asList("bar", "baz"), iteratorAsList(fixture.queryParametersIterator("foo")));
        assertEquals(singletonList("def"), iteratorAsList(fixture.queryParametersIterator("abc")));

        // change it
        fixture.requestTarget("/new/%24path%24?another=bar");

        // parse it again
        assertEquals("/new/%24path%24?another=bar", fixture.requestTarget());
        assertEquals("/new/%24path%24", fixture.rawPath());
        assertEquals("/new/$path$", fixture.path());
        assertEquals("another=bar", fixture.rawQuery());
        assertEquals(singletonList("bar"), iteratorAsList(fixture.queryParametersIterator("another")));
    }

    @Test
    void testSetPathAndReparse() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");

        // parse it
        assertEquals("/some/path?foo=bar&abc=def&foo=baz", fixture.requestTarget());
        assertEquals("/some/path", fixture.rawPath());
        assertEquals("/some/path", fixture.path());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.rawQuery());
        assertEquals(asList("bar", "baz"), iteratorAsList(fixture.queryParametersIterator("foo")));
        assertEquals(singletonList("def"), iteratorAsList(fixture.queryParametersIterator("abc")));

        // change it
        fixture.path("/new/$path ");

        // parse it again
        assertEquals("/new/$path%20?foo=bar&abc=def&foo=baz", fixture.requestTarget());
        assertEquals("/new/$path%20", fixture.rawPath());
        assertEquals("/new/$path ", fixture.path());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.rawQuery());
        assertEquals(asList("bar", "baz"), iteratorAsList(fixture.queryParametersIterator("foo")));
        assertEquals(singletonList("def"), iteratorAsList(fixture.queryParametersIterator("abc")));
    }

    @Test
    void testSetQueryAndReparse() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");

        // parse it
        assertEquals("/some/path?foo=bar&abc=def&foo=baz", fixture.requestTarget());
        assertEquals("/some/path", fixture.rawPath());
        assertEquals("/some/path", fixture.path());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.rawQuery());
        assertEquals(asList("bar", "baz"), iteratorAsList(fixture.queryParametersIterator("foo")));
        assertEquals(singletonList("def"), iteratorAsList(fixture.queryParametersIterator("abc")));

        // change it
        fixture.query("abc=new &abc=new2");

        // parse it again
        assertEquals("/some/path?abc=new%20&abc=new2", fixture.requestTarget());
        assertEquals("/some/path", fixture.rawPath());
        assertEquals("/some/path", fixture.path());
        assertEquals("abc=new%20&abc=new2", fixture.rawQuery());
        assertEquals(singleton("abc"), fixture.queryParametersKeys());
        assertEquals(asList("new ", "new2"), iteratorAsList(fixture.queryParametersIterator("abc")));
    }

    @Test
    void testOneEmptyQueryParam() {
        createFixture("/foo?bar");
        assertEquals("/foo?bar", fixture.requestTarget());
        assertEquals("bar", fixture.rawQuery());
        assertEquals("", fixture.queryParameter("bar"));
        assertNull(fixture.queryParameter("nothing"));

        assertEquals(singletonList("bar"), iteratorAsList(fixture.queryParametersKeys().iterator()));
        Iterator<Entry<String, String>> itr = fixture.queryParameters().iterator();
        assertNext(itr, "bar", "");
        assertFalse(itr.hasNext());

        List<Entry<String, String>> entries = iteratorAsList(fixture.queryParameters().iterator());
        assertThat(entries, hasSize(1));
        assertEntry(entries.get(0), "bar", "");
    }

    @Test
    void testTwoEmptyQueryParams() {
        testTwoEmptyQueryParams("", "");
        testTwoEmptyQueryParams("", "x");
        testTwoEmptyQueryParams("x", "");
    }

    private void testTwoEmptyQueryParams(String v1, String v2) {
        String rawQuery = "bar" + queryValue(v1) + "&baz" + queryValue(v2);
        String requestTarget = "/foo?" + rawQuery;
        createFixture(requestTarget);
        assertEquals(requestTarget, fixture.requestTarget());
        assertEquals(rawQuery, fixture.rawQuery());
        assertEquals(v1, fixture.queryParameter("bar"));
        assertEquals(v2, fixture.queryParameter("baz"));
        assertNull(fixture.queryParameter("nothing"));

        assertEquals(asList("bar", "baz"), iteratorAsList(fixture.queryParametersKeys().iterator()));
        Iterator<Entry<String, String>> itr = fixture.queryParameters().iterator();
        assertNext(itr, "bar", v1);
        assertNext(itr, "baz", v2);
        assertFalse(itr.hasNext());

        List<Entry<String, String>> entries = iteratorAsList(fixture.queryParameters().iterator());
        assertThat(entries, hasSize(2));
        assertEntry(entries.get(0), "bar", v1);
        assertEntry(entries.get(1), "baz", v2);
    }

    @Test
    void testThreeEmptyQueryParams() {
        testThreeEmptyQueryParams("", "", "");
        testThreeEmptyQueryParams("x", "", "");
        testThreeEmptyQueryParams("x", "x", "");
        testThreeEmptyQueryParams("x", "", "x");
        testThreeEmptyQueryParams("", "x", "");
        testThreeEmptyQueryParams("", "x", "x");
        testThreeEmptyQueryParams("", "", "x");
        testThreeEmptyQueryParams("x", "x", "x");
    }

    private void testThreeEmptyQueryParams(String v1, String v2, String v3) {
        String rawQuery = "bar" + queryValue(v1) + "&baz" + queryValue(v2) + "&zap" + queryValue(v3);
        String requestTarget = "/foo?" + rawQuery;
        createFixture(requestTarget);
        assertEquals(requestTarget, fixture.requestTarget());
        assertEquals(rawQuery, fixture.rawQuery());
        assertEquals(v1, fixture.queryParameter("bar"));
        assertEquals(v2, fixture.queryParameter("baz"));
        assertEquals(v3, fixture.queryParameter("zap"));
        assertNull(fixture.queryParameter("nothing"));

        assertEquals(asList("bar", "baz", "zap"), iteratorAsList(fixture.queryParametersKeys().iterator()));
        Iterator<Entry<String, String>> itr = fixture.queryParameters().iterator();
        assertNext(itr, "bar", v1);
        assertNext(itr, "baz", v2);
        assertNext(itr, "zap", v3);
        assertFalse(itr.hasNext());

        List<Entry<String, String>> entries = iteratorAsList(fixture.queryParameters().iterator());
        assertThat(entries, hasSize(3));
        assertEntry(entries.get(0), "bar", v1);
        assertEntry(entries.get(1), "baz", v2);
        assertEntry(entries.get(2), "zap", v3);
    }

    @Test
    void testEncodeToRequestTargetWithNoParams() {
        createFixture("/some/path#fragment");
        fixture.addQueryParameters("", emptyList());

        assertEquals("/some/path?#fragment", fixture.requestTarget());
    }

    @Test
    void testEncodeToRequestTargetWithParam() {
        createFixture("/some/path");
        fixture.addQueryParameters("foo", newList("bar", "baz"));

        assertEquals("/some/path?foo=bar&foo=baz", fixture.requestTarget());
    }

    @Test
    void testEncodeToRequestTargetWithMultipleParams() {
        createFixture("/some/path#fragment");
        fixture.addQueryParameters("foo", newList("bar", "baz"));
        fixture.addQueryParameters("abc", newList("123", "456"));

        assertEquals("/some/path?foo=bar&foo=baz&abc=123&abc=456#fragment", fixture.requestTarget());
    }

    @Test
    void testEncodeToRequestTargetWithSpecialCharacters() {
        createFixture("/some/path#fragment");
        fixture.addQueryParameters("pair", newList("key1=value1", "key2=value2"));

        assertEquals("/some/path?pair=key1%3Dvalue1&pair=key2%3Dvalue2#fragment", fixture.requestTarget());
    }

    @Test
    void testEncodeToRequestTargetWithAbsoluteForm() {
        createFixture("http://my.site.com/some/path?foo=bar&abc=def&foo=baz#fragment");
        fixture.setQueryParameters("foo", newList("new"));

        assertEquals("http://my.site.com/some/path?foo=new&abc=def#fragment", fixture.requestTarget());

        assertEffectiveHostAndPort("my.site.com");
    }

    @Test
    void testEncodeToRequestTargetWithAbsoluteFormWithPort() {
        createFixture("http://my.site.com:8080/some/path?foo=bar&abc=def&foo=baz#fragment");
        fixture.setQueryParameters("foo", newList("new"));

        assertEquals("http://my.site.com:8080/some/path?foo=new&abc=def#fragment", fixture.requestTarget());

        assertEffectiveHostAndPort("my.site.com", 8080);
    }

    @Test
    void testEncodeToRequestTargetWithAbsoluteFormWithUserInfo() {
        createFixture("http://user@my.site.com/some/path?foo=bar&abc=def&foo=baz#fragment");
        fixture.setQueryParameters("foo", newList("new"));

        assertEquals("http://user@my.site.com/some/path?foo=new&abc=def#fragment", fixture.requestTarget());
        fixture.path("");
        assertEquals("http://user@my.site.com?foo=new&abc=def#fragment", fixture.requestTarget());
        assertEquals("", fixture.path());
        assertEquals("", fixture.path()); // check cached value
        assertEquals("foo=new&abc=def", fixture.query());
        assertEquals("foo=new&abc=def", fixture.query()); // check cached value
    }

    @Test
    void testToString() {
        createFixture("/some/path?a=query");
        fixture.headers().set(HOST, "some.site.com");
        fixture.headers().set(AUTHORIZATION, "some auth info");

        assertEquals("GET /some/path?a=query HTTP/1.1", fixture.toString());
    }

    @Test
    void testToStringWithPassFilter() {
        createFixture("/some/path?a=query");
        fixture.headers().set(HOST, "some.site.com");
        fixture.headers().set(AUTHORIZATION, "some auth info");

        assertEquals("GET /some/path?a=query HTTP/1.1" + lineSeparator() +
                                "DefaultHttpHeaders[authorization: some auth info" + lineSeparator() +
                                "host: some.site.com]", fixture.toString((k, v) -> v));
    }

    @Test
    void testToStringWithRedactFilter() {
        createFixture("/some/path?a=query");
        fixture.headers().set(HOST, "some.site.com");
        fixture.headers().set(AUTHORIZATION, "some auth info");

        assertEquals("GET /some/path?a=query HTTP/1.1" + lineSeparator() +
                                "DefaultHttpHeaders[authorization: redacted" + lineSeparator() +
                                "host: redacted]", fixture.toString((k, v) -> "redacted"));
    }

    private void assertEffectiveHostAndPort(String hostName) {
        HostAndPort effectiveHostAndPort = fixture.effectiveHostAndPort();
        assertNotNull(effectiveHostAndPort);
        assertEquals(hostName, effectiveHostAndPort.hostName());
        assertThat(effectiveHostAndPort.port(), lessThan(0));
    }

    private void assertEffectiveHostAndPort(String hostName, int port) {
        HostAndPort effectiveHostAndPort = fixture.effectiveHostAndPort();
        assertNotNull(effectiveHostAndPort);
        assertEquals(hostName, effectiveHostAndPort.hostName());
        assertEquals(port, effectiveHostAndPort.port());
        assertThat(effectiveHostAndPort.toString(), isValidIpV6Address(hostName) ?
                equalTo('[' + hostName + "]:" + port) :
                equalTo(hostName + ':' + port));
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> newList(final T... elements) {
        final List<T> list = new ArrayList<>(elements.length);
        addAll(list, elements);
        return list;
    }

    private static <T> List<T> iteratorAsList(final Iterator<T> iterator) {
        return StreamSupport
                .stream(spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
                .collect(toList());
    }

    private static void assertNext(Iterator<Entry<String, String>> itr, String key, String value) {
        assertTrue(itr.hasNext());
        assertEntry(itr.next(), key, value);
    }

    private static void assertEntry(Entry<String, String> next, String key, String value) {
        assertEquals(key, next.getKey());
        assertEquals(value, next.getValue());
    }

    private static String queryValue(String v) {
        return v.isEmpty() ? "" : "=" + v;
    }
}
