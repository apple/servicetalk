/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Spliterator;
import java.util.stream.StreamSupport;

import static io.servicetalk.http.api.HttpHeaderNames.AUTHORIZATION;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static java.lang.System.lineSeparator;
import static java.util.Arrays.asList;
import static java.util.Collections.addAll;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public abstract class AbstractHttpRequestMetaDataTest<T extends HttpRequestMetaData> {

    @Rule
    public final ExpectedException expected = ExpectedException.none();

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    protected T fixture;

    protected abstract void createFixture(String uri);

    protected abstract void createFixture(String uri, HttpRequestMethod method);

    // https://tools.ietf.org/html/rfc7230#section-5.3.1
    @Test
    public void testParseUriOriginForm() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");

        assertNull(fixture.scheme());
        assertNull(fixture.userInfo());
        assertNull(fixture.host());
        assertThat(fixture.port(), lessThan(0));
        assertEquals("/some/path", fixture.path());
        assertEquals("/some/path", fixture.rawPath());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.rawQuery());
        assertEquals("/some/path?foo=bar&abc=def&foo=baz", fixture.requestTarget());

        assertNull(fixture.effectiveHost());
        assertThat(fixture.effectivePort(), lessThan(0));

        // Host header provides effective host and port
        fixture.headers().set(HOST, "other.site.com:8080");
        assertEquals("other.site.com", fixture.effectiveHost());
        assertEquals(8080, fixture.effectivePort());
    }

    // https://tools.ietf.org/html/rfc7230#section-5.3.2
    @Test
    public void testParseHttpUriAbsoluteForm() {
        createFixture("http://my.site.com/some/path?foo=bar&abc=def&foo=baz");

        assertEquals("http", fixture.scheme());
        assertNull(fixture.userInfo());
        assertEquals("my.site.com", fixture.host());
        assertThat(fixture.port(), lessThan(0));
        assertEquals("/some/path", fixture.path());
        assertEquals("/some/path", fixture.rawPath());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.rawQuery());
        assertEquals("http://my.site.com/some/path?foo=bar&abc=def&foo=baz", fixture.requestTarget());

        assertEquals("my.site.com", fixture.effectiveHost());
        assertThat(fixture.effectivePort(), lessThan(0));

        // Host header ignored when request-target is absolute.
        fixture.headers().set(HOST, "other.site.com:8080");
        assertEquals("my.site.com", fixture.effectiveHost());
        assertThat(fixture.effectivePort(), lessThan(0));
    }

    @Test
    public void testParseHttpsUriAbsoluteForm() {
        createFixture("https://jdoe@my.site.com/some/path?foo=bar&abc=def&foo=baz");

        assertEquals("https", fixture.scheme());
        assertEquals("jdoe", fixture.userInfo());
        assertEquals("my.site.com", fixture.host());
        assertThat(fixture.port(), lessThan(0));
        assertEquals("/some/path", fixture.path());
        assertEquals("/some/path", fixture.rawPath());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.rawQuery());
        assertEquals("https://jdoe@my.site.com/some/path?foo=bar&abc=def&foo=baz", fixture.requestTarget());

        assertEquals("my.site.com", fixture.effectiveHost());
        assertThat(fixture.effectivePort(), lessThan(0));

        // Host header ignored when request-target is absolute
        fixture.headers().set(HOST, "other.site.com:8080");
        assertEquals("my.site.com", fixture.effectiveHost());
        assertThat(fixture.effectivePort(), lessThan(0));
    }

    // https://tools.ietf.org/html/rfc7230#section-5.3.3
    @Test
    public void testParseHttpUriAuthorityForm() {
        createFixture("my.site.com:80", CONNECT);

        assertNull(fixture.scheme());
        assertNull(fixture.userInfo());
        assertEquals("my.site.com", fixture.host());
        assertEquals(80, fixture.port());
        assertEquals("", fixture.path());
        assertEquals("", fixture.rawPath());
        assertNull(fixture.rawQuery());
        assertEquals("my.site.com:80", fixture.requestTarget());

        assertEquals("my.site.com", fixture.effectiveHost());
        assertEquals(80, fixture.effectivePort());

        // Host header ignored when request-target has authority form
        fixture.headers().set(HOST, "other.site.com:8080");
        assertEquals("my.site.com", fixture.effectiveHost());
        assertEquals(80, fixture.effectivePort());
    }

    // https://tools.ietf.org/html/rfc7230#section-5.3.4
    @Test
    public void testParseHttpUriAsteriskForm() {
        createFixture("*");

        assertNull(fixture.scheme());
        assertNull(fixture.userInfo());
        assertNull(fixture.host());
        assertThat(fixture.port(), lessThan(0));
        assertEquals("*", fixture.path());
        assertEquals("*", fixture.rawPath());
        assertNull(fixture.rawQuery());
        assertEquals("*", fixture.requestTarget());

        assertNull(fixture.effectiveHost());
        assertThat(fixture.effectivePort(), lessThan(0));

        // Host header provides effective host and port
        fixture.headers().set(HOST, "other.site.com:8080");
        assertEquals("other.site.com", fixture.effectiveHost());
        assertEquals(8080, fixture.effectivePort());
    }

    @Test
    public void testParseUriOriginFormWithHostHeader() {
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

        assertEquals("host.header.com", fixture.effectiveHost());
        assertThat(fixture.effectivePort(), lessThan(0));

        // Host header provides effective host and port
        fixture.headers().set(HOST, "other.site.com:8080");
        assertEquals("other.site.com", fixture.effectiveHost());
        assertEquals(8080, fixture.effectivePort());

        // Test host header changes port, but keeps the same hostname.
        fixture.headers().set(HOST, "other.site.com:8081");
        assertEquals("other.site.com", fixture.effectiveHost());
        assertEquals(8081, fixture.effectivePort());
    }

    @Test
    public void testParseHttpUriAbsoluteFormWithHost() {
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

        assertEquals("my.site.com", fixture.effectiveHost());
        assertThat(fixture.effectivePort(), lessThan(0));

        // Host header ignored when request-target is absolute.
        fixture.headers().set(HOST, "other.site.com:8080");
        assertEquals("my.site.com", fixture.effectiveHost());
        assertThat(fixture.effectivePort(), lessThan(0));
    }

    @Test
    public void testEffectiveHostIPv6NoPort() {
        createFixture("some/path?foo=bar&abc=def&foo=baz");
        fixture.headers().set(HOST, "[1:2:3::5]");

        HostAndPort hostAndPort = fixture.effectiveHostAndPort();
        assertNotNull(hostAndPort);
        assertEquals("[1:2:3::5]", hostAndPort.hostName());
        assertThat(hostAndPort.port(), lessThan(0));
    }

    @Test
    public void testEffectiveHostIPv6WithPort() {
        createFixture("some/path?foo=bar&abc=def&foo=baz");
        fixture.headers().set(HOST, "[1:2:3::5]:8080");

        HostAndPort hostAndPort = fixture.effectiveHostAndPort();
        assertNotNull(hostAndPort);
        assertEquals("[1:2:3::5]", hostAndPort.hostName());
        assertEquals(8080, hostAndPort.port());
    }

    @Test
    public void testEffectiveHostIPv6EmptyPort() {
        createFixture("some/path?foo=bar&abc=def&foo=baz");
        fixture.headers().set(HOST, "[1:2:3::5]:");
        expected.expect(IllegalArgumentException.class);
        fixture.effectiveHostAndPort();
    }

    @Test
    public void testEffectiveHostIPv6BeforePort() {
        createFixture("some/path?foo=bar&abc=def&foo=baz");
        fixture.headers().set(HOST, "[1:2:3::5]:f8080");

        expected.expect(IllegalArgumentException.class);
        fixture.effectiveHostAndPort();
    }

    @Test
    public void testEffectiveHostIPv6AfterPort() {
        createFixture("some/path?foo=bar&abc=def&foo=baz");
        fixture.headers().set(HOST, "[1:2:3::5]:8080f");

        expected.expect(IllegalArgumentException.class);
        fixture.effectiveHostAndPort();
    }

    @Test
    public void testEffectiveHostIPv6InvalidPortDelimiter() {
        createFixture("some/path?foo=bar&abc=def&foo=baz");
        fixture.headers().set(HOST, "[1:2:3::5]f8080");

        expected.expect(IllegalArgumentException.class);
        fixture.effectiveHostAndPort();
    }

    @Test
    public void testEffectiveHostIPv6NoClosingBrace() {
        createFixture("some/path?foo=bar&abc=def&foo=baz");
        fixture.headers().set(HOST, "[1:2:3::5");

        expected.expect(IllegalArgumentException.class);
        fixture.effectiveHostAndPort();
    }

    @Test
    public void testSlashAddedToPath() {
        createFixture("//authority");
        fixture.path("path");
        assertEquals("//authority/path", fixture.requestTarget());
    }

    @Test
    public void testSlashNotAddedToEmptyPath() {
        createFixture("//authority");
        fixture.path("");
        assertEquals("//authority", fixture.requestTarget());
    }

    @Test
    public void testSetPathWithoutLeadingSlash() {
        createFixture("temp");
        fixture.path("foo");
        assertEquals("foo", fixture.requestTarget());
    }

    @Test
    public void testAppendSegmentsToPath() {
        createFixture("base");
        fixture.appendPathSegments("foo", "/$", "bar");
        assertEquals("base/foo/%2F$/bar", fixture.requestTarget());
    }

    @Test
    public void testAppendSegmentsToPathEncoded() {
        createFixture("/base/");
        fixture.appendPathSegments("foo", "/ ", "bar");
        assertEquals("/base/foo/%2F%20/bar", fixture.requestTarget());
    }

    @Test
    public void testAppendSegmentsToPathWithQueryParam() {
        createFixture("/base/?baz=123");
        fixture.appendPathSegments("foo", "/$", "bar");
        assertEquals("/base/foo/%2F$/bar?baz=123", fixture.requestTarget());
    }

    @Test
    public void testAppendSegmentsToPathWithoutTrailingSlash() {
        createFixture("/base?baz=123");
        fixture.appendPathSegments("foo", "/$", "bar");
        assertEquals("/base/foo/%2F$/bar?baz=123", fixture.requestTarget());
    }

    @Test
    public void testAppendAuthorityInFirstSegmentIsEscaped() {
        createFixture("");
        fixture.appendPathSegments("//authority");
        assertEquals("%2F%2Fauthority", fixture.requestTarget());
    }

    @Test
    public void testNoPathAppendColonInFirstSegment() {
        createFixture("");
        expected.expect(IllegalArgumentException.class);
        fixture.appendPathSegments("foo:");
    }

    @Test
    public void testEmptyPathAppendColonInFirstSegment() {
        createFixture("/");
        expected.expect(IllegalArgumentException.class);
        fixture.appendPathSegments("foo:");
    }

    @Test
    public void testAppendNoSegmentToPath() {
        createFixture("base/");
        expected.expect(IllegalArgumentException.class);
        fixture.appendPathSegments();
    }

    @Test
    public void testSetRawPathWithoutLeadingSlash() {
        createFixture("temp");
        fixture.rawPath("foo");
        assertEquals("foo", fixture.requestTarget());
    }

    @Test
    public void testRawPathCanOverrideQueryComponent() {
        // asserting current behavior, rawPath doesn't attempt full validation and assumes the path(..) method is used
        // to encode if necessary.
        createFixture("/temp");
        fixture.rawPath("foo?bar");
        assertEquals("foo?bar", fixture.requestTarget());
        assertEquals("bar", fixture.rawQuery());
    }

    @Test
    public void testRawPathCanOverrideFragmentComponent() {
        // asserting current behavior, rawPath doesn't attempt full validation and assumes the path(..) method is used
        // to encode if necessary.
        createFixture("/temp");
        fixture.rawPath("foo#bar");
        assertEquals("foo#bar", fixture.requestTarget());
    }

    @Test
    public void testEncodedPathCanNotOverrideQueryComponent() {
        createFixture("/temp");
        fixture.path("foo?bar");
        assertEquals("foo%3Fbar", fixture.requestTarget());
    }

    @Test
    public void testEncodedPathCanNotOverrideFragmentComponent() {
        createFixture("/temp");
        fixture.path("foo#bar");
        assertEquals("foo%23bar", fixture.requestTarget());
    }

    @Test
    public void testRelativePathCannotContainColonInFirstSegment() {
        createFixture("temp");
        expected.expect(IllegalArgumentException.class);
        fixture.rawPath("foo:bar");
    }

    @Test
    public void testPathCannotInjectAuthority() {
        createFixture("/temp");
        expected.expect(IllegalArgumentException.class);
        fixture.rawPath("//authorityinjection");
    }

    @Test
    public void testAuthoritySetAllowsPathAuthorityLookalike() {
        createFixture("//realauthority");
        fixture.rawPath("//authorityinjection");
        assertEquals("//realauthority//authorityinjection", fixture.requestTarget());
        assertEquals("realauthority", fixture.host());
        assertEquals("//authorityinjection", fixture.path());
    }

    @Test
    public void testEmptyPathWithQueryAndFragment() {
        createFixture("/temp?foo#bar");
        fixture.rawPath("");
        assertEquals("?foo#bar", fixture.requestTarget());
        assertEquals("", fixture.path());
        assertEquals("foo", fixture.query());
    }

    @Test
    public void testNullQueryWithPathAndFragment() {
        createFixture("/temp?foo#bar");
        fixture.rawQuery(null);
        assertEquals("/temp#bar", fixture.requestTarget());
        assertEquals("/temp", fixture.path());
        assertNull(fixture.query());
    }

    @Test
    public void testReplacePathInAbsoluteForm() {
        createFixture("http://my.site.com/some/path?foo=bar&abc=def&foo=baz");
        fixture.path("/new/$path$");

        assertEquals("http://my.site.com/new/$path$?foo=bar&abc=def&foo=baz", fixture.requestTarget());
    }

    @Test
    public void testSetEmptyPathInAbsoluteForm() {
        createFixture("http://my.site.com/some/path?foo=bar&abc=def&foo=baz");
        fixture.path("");

        assertEquals("http://my.site.com?foo=bar&abc=def&foo=baz", fixture.requestTarget());
    }

    @Test
    public void testReplacePathInOriginForm() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");
        fixture.path("/new/$path ");

        assertEquals("/new/$path%20?foo=bar&abc=def&foo=baz", fixture.requestTarget());
    }

    @Test
    public void testSetEmptyPathInOriginForm() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");
        fixture.path("");

        assertEquals("?foo=bar&abc=def&foo=baz", fixture.requestTarget());
    }

    @Test
    public void testReplaceExistingQuery() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");
        fixture.rawQuery("new=query");

        assertEquals("/some/path?new=query", fixture.requestTarget());
    }

    @Test
    public void testSetRemoveExistingQuery() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");
        fixture.rawQuery(null);

        assertEquals("/some/path", fixture.requestTarget());
    }

    @Test
    public void testSetClearExistingQuery() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");
        fixture.rawQuery("");

        assertEquals("/some/path?", fixture.requestTarget());
    }

    @Test
    public void testAddQuery() {
        createFixture("/some/path");
        fixture.rawQuery("new=query");

        assertEquals("/some/path?new=query", fixture.requestTarget());
    }

    @Test
    public void testParseQuery() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");

        assertEquals(asList("foo", "abc"), iteratorAsList(fixture.queryParametersKeys().iterator()));
        assertEquals("bar", fixture.queryParameter("foo"));
        assertEquals(asList("bar", "baz"), iteratorAsList(fixture.queryParametersIterator("foo")));
        assertEquals("def", fixture.queryParameter("abc"));
        assertEquals(singletonList("def"), iteratorAsList(fixture.queryParametersIterator("abc")));
    }

    @Test
    public void testParseEmptyAndEncodeQuery() {
        createFixture("/some/path");
        fixture.addQueryParameter("foo", "bar");

        assertEquals("/some/path?foo=bar", fixture.requestTarget());
    }

    @Test
    public void testQueryDoesntChangeState() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");
        fixture.queryParameters();

        assertEquals("/some/path?foo=bar&abc=def&foo=baz", fixture.requestTarget());
    }

    @Test
    public void testUpdateQueryParameters() {
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
        assertEquals(fixture.queryParametersSize(), 3);

        for (Iterator<Entry<String, String>> i = fixture.queryParameters().iterator(); i.hasNext();) {
            i.next();
            i.remove();
        }
        assertEquals("/some/path", fixture.requestTarget());
        assertNull(fixture.query());
        assertEquals(fixture.queryParametersSize(), 0);
        assertTrue(fixture.queryParametersKeys().isEmpty());
    }

    @Test
    public void testSetRequestTargetAndReparse() {
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
    public void testSetPathAndReparse() {
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
    public void testSetQueryAndReparse() {
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
    public void testEncodeToRequestTargetWithNoParams() {
        createFixture("/some/path#fragment");
        fixture.addQueryParameters("", emptyList());

        assertEquals("/some/path?#fragment", fixture.requestTarget());
    }

    @Test
    public void testEncodeToRequestTargetWithParam() {
        createFixture("/some/path");
        fixture.addQueryParameters("foo", newList("bar", "baz"));

        assertEquals("/some/path?foo=bar&foo=baz", fixture.requestTarget());
    }

    @Test
    public void testEncodeToRequestTargetWithMultipleParams() {
        createFixture("/some/path#fragment");
        fixture.addQueryParameters("foo", newList("bar", "baz"));
        fixture.addQueryParameters("abc", newList("123", "456"));

        assertEquals("/some/path?foo=bar&foo=baz&abc=123&abc=456#fragment", fixture.requestTarget());
    }

    @Test
    public void testEncodeToRequestTargetWithSpecialCharacters() {
        createFixture("/some/path#fragment");
        fixture.addQueryParameters("pair", newList("key1=value1", "key2=value2"));

        assertEquals("/some/path?pair=key1%3Dvalue1&pair=key2%3Dvalue2#fragment", fixture.requestTarget());
    }

    @Test
    public void testEncodeToRequestTargetWithAbsoluteForm() {
        createFixture("http://my.site.com/some/path?foo=bar&abc=def&foo=baz#fragment");
        fixture.setQueryParameters("foo", newList("new"));

        assertEquals("http://my.site.com/some/path?foo=new&abc=def#fragment", fixture.requestTarget());

        assertEquals("my.site.com", fixture.effectiveHost());
        assertThat(fixture.effectivePort(), lessThan(0));
    }

    @Test
    public void testEncodeToRequestTargetWithAbsoluteFormWithPort() {
        createFixture("http://my.site.com:8080/some/path?foo=bar&abc=def&foo=baz#fragment");
        fixture.setQueryParameters("foo", newList("new"));

        assertEquals("http://my.site.com:8080/some/path?foo=new&abc=def#fragment", fixture.requestTarget());

        HostAndPort hostAndPort = fixture.effectiveHostAndPort();
        assertNotNull(hostAndPort);
        assertEquals("my.site.com", hostAndPort.hostName());
        assertEquals(8080, hostAndPort.port());
    }

    @Test
    public void testEncodeToRequestTargetWithAbsoluteFormWithUserInfo() {
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
    public void testToString() {
        createFixture("/some/path?a=query");
        fixture.headers().set(HOST, "some.site.com");
        fixture.headers().set(AUTHORIZATION, "some auth info");

        assertEquals("GET /some/path?a=query HTTP/1.1", fixture.toString());
    }

    @Test
    public void testToStringWithPassFilter() {
        createFixture("/some/path?a=query");
        fixture.headers().set(HOST, "some.site.com");
        fixture.headers().set(AUTHORIZATION, "some auth info");

        assertEquals("GET /some/path?a=query HTTP/1.1" + lineSeparator() +
                "DefaultHttpHeaders[authorization: some auth info" + lineSeparator() +
                "host: some.site.com]", fixture.toString((k, v) -> v));
    }

    @Test
    public void testToStringWithRedactFilter() {
        createFixture("/some/path?a=query");
        fixture.headers().set(HOST, "some.site.com");
        fixture.headers().set(AUTHORIZATION, "some auth info");

        assertEquals("GET /some/path?a=query HTTP/1.1" + lineSeparator() +
                "DefaultHttpHeaders[authorization: redacted" + lineSeparator() +
                "host: redacted]", fixture.toString((k, v) -> "redacted"));
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
}
