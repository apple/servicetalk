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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Spliterator;
import java.util.stream.StreamSupport;

import static io.servicetalk.http.api.HttpHeaderNames.AUTHORIZATION;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static java.lang.System.lineSeparator;
import static java.util.Arrays.asList;
import static java.util.Collections.addAll;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public abstract class AbstractHttpRequestMetaDataTest<T extends HttpRequestMetaData> {

    @Rule
    public final ExpectedException expected = ExpectedException.none();

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    protected T fixture;

    private final Map<String, List<String>> params = new LinkedHashMap<>();

    protected abstract void createFixture(String uri);

    protected abstract void setFixtureQueryParams(Map<String, List<String>> params);

    // https://tools.ietf.org/html/rfc7230#section-5.3.1
    @Test
    public void testParseUriOriginForm() {
        createFixture("/some/path?foo=bar&abc=def&foo=baz");

        assertNull(fixture.scheme());
        assertNull(fixture.userInfo());
        assertNull(fixture.host());
        assertEquals(-1, fixture.port());
        assertEquals("/some/path", fixture.path());
        assertEquals("/some/path", fixture.rawPath());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.rawQuery());
        assertEquals("/some/path?foo=bar&abc=def&foo=baz", fixture.requestTarget());

        assertNull(fixture.effectiveHost());
        assertEquals(-1, fixture.effectivePort());

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
        assertEquals(-1, fixture.port());
        assertEquals("/some/path", fixture.path());
        assertEquals("/some/path", fixture.rawPath());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.rawQuery());
        assertEquals("http://my.site.com/some/path?foo=bar&abc=def&foo=baz", fixture.requestTarget());

        assertEquals("my.site.com", fixture.effectiveHost());
        assertEquals(-1, fixture.effectivePort());

        // Host header ignored when request-target is absolute.
        fixture.headers().set(HOST, "other.site.com:8080");
        assertEquals("my.site.com", fixture.effectiveHost());
        assertEquals(-1, fixture.effectivePort());
    }

    @Test
    public void testParseHttpsUriAbsoluteForm() {
        createFixture("https://jdoe@my.site.com/some/path?foo=bar&abc=def&foo=baz");

        assertEquals("https", fixture.scheme());
        assertEquals("jdoe", fixture.userInfo());
        assertEquals("my.site.com", fixture.host());
        assertEquals(-1, fixture.port());
        assertEquals("/some/path", fixture.path());
        assertEquals("/some/path", fixture.rawPath());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.rawQuery());
        assertEquals("https://jdoe@my.site.com/some/path?foo=bar&abc=def&foo=baz", fixture.requestTarget());

        assertEquals("my.site.com", fixture.effectiveHost());
        assertEquals(-1, fixture.effectivePort());

        // Host header ignored when request-target is absolute
        fixture.headers().set(HOST, "other.site.com:8080");
        assertEquals("my.site.com", fixture.effectiveHost());
        assertEquals(-1, fixture.port());
    }

    // https://tools.ietf.org/html/rfc7230#section-5.3.3
    @Test
    public void testParseHttpUriAuthorityForm() {
        createFixture("my.site.com:80");

        assertNull(fixture.scheme());
        assertNull(fixture.userInfo());
        assertEquals("my.site.com", fixture.host());
        assertEquals(80, fixture.port());
        assertEquals("", fixture.path());
        assertEquals("", fixture.rawPath());
        assertEquals("", fixture.rawQuery());
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
        assertEquals(-1, fixture.port());
        assertEquals("", fixture.path());
        assertEquals("", fixture.rawPath());
        assertEquals("", fixture.rawQuery());
        assertEquals("*", fixture.requestTarget());

        assertNull(fixture.effectiveHost());
        assertEquals(-1, fixture.effectivePort());

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
        assertEquals(-1, fixture.port());
        assertEquals("/some/path", fixture.path());
        assertEquals("/some/path", fixture.rawPath());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.rawQuery());
        assertEquals("/some/path?foo=bar&abc=def&foo=baz", fixture.requestTarget());

        assertEquals("host.header.com", fixture.effectiveHost());
        assertEquals(-1, fixture.effectivePort());

        // Host header provides effective host and port
        fixture.headers().set(HOST, "other.site.com:8080");
        assertEquals("other.site.com", fixture.effectiveHost());
        assertEquals(8080, fixture.effectivePort());
    }

    @Test
    public void testParseHttpUriAbsoluteFormWithHost() {
        createFixture("http://my.site.com/some/path?foo=bar&abc=def&foo=baz");
        fixture.headers().set(HOST, "host.header.com");

        assertEquals("http", fixture.scheme());
        assertNull(fixture.userInfo());
        assertEquals("my.site.com", fixture.host());
        assertEquals(-1, fixture.port());
        assertEquals("/some/path", fixture.path());
        assertEquals("/some/path", fixture.rawPath());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.rawQuery());
        assertEquals("http://my.site.com/some/path?foo=bar&abc=def&foo=baz", fixture.requestTarget());

        assertEquals("my.site.com", fixture.effectiveHost());
        assertEquals(-1, fixture.effectivePort());

        // Host header ignored when request-target is absolute.
        fixture.headers().set(HOST, "other.site.com:8080");
        assertEquals("my.site.com", fixture.effectiveHost());
        assertEquals(-1, fixture.effectivePort());
    }

    @Test
    public void testSetPathWithoutLeadingSlash() {
        createFixture("temp");
        fixture.path("foo");
        assertEquals("/foo", fixture.requestTarget());
    }

    @Test
    public void testAppendSegmentsToPath() {
        createFixture("base/");
        fixture.appendPathSegments("foo", "/$", "bar");
        assertEquals("base/foo/%2F%24/bar", fixture.requestTarget());
    }

    @Test
    public void testAppendSegmentsToPathWithQueryParam() {
        createFixture("base/");
        fixture.appendPathSegments("foo", "/$", "bar");
        assertEquals("base/foo/%2F%24/bar", fixture.requestTarget());
    }

    @Test
    public void testAppendSegmentsToPathWithoutTrailingSlash() {
        createFixture("base?baz=123");
        fixture.appendPathSegments("foo", "/$", "bar");
        assertEquals("base/foo/%2F%24/bar?baz=123", fixture.requestTarget());
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
        expected.expect(IllegalArgumentException.class);
        expected.expectMessage("Path must be empty or start with '/'");
        fixture.rawPath("foo");
    }

    @Test
    public void testReplacePathInAbsoluteForm() {
        createFixture("http://my.site.com/some/path?foo=bar&abc=def&foo=baz");
        fixture.path("/new/$path$");

        assertEquals("http://my.site.com/new/%24path%24?foo=bar&abc=def&foo=baz", fixture.requestTarget());
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
        fixture.path("/new/$path$");

        assertEquals("/new/%24path%24?foo=bar&abc=def&foo=baz", fixture.requestTarget());
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
        fixture.rawQuery("");

        assertEquals("/some/path", fixture.requestTarget());
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

        assertTrue(fixture.removeQueryParameters("foo"));
        assertEquals("/some/path?abc=new", fixture.requestTarget());
        assertFalse(fixture.removeQueryParameters("foo"));

        fixture.addQueryParameters("foo", "bar", "baz");
        assertEquals("/some/path?abc=new&foo=bar&foo=baz", fixture.requestTarget());

        assertTrue(fixture.removeQueryParameters("foo", "baz"));
        assertEquals("/some/path?abc=new&foo=bar", fixture.requestTarget());

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
        fixture.path("/new/$path$");

        // parse it again
        assertEquals("/new/%24path%24?foo=bar&abc=def&foo=baz", fixture.requestTarget());
        assertEquals("/new/%24path%24", fixture.rawPath());
        assertEquals("/new/$path$", fixture.path());
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
        fixture.rawQuery("abc=new");

        // parse it again
        assertEquals("/some/path?abc=new", fixture.requestTarget());
        assertEquals("/some/path", fixture.rawPath());
        assertEquals("/some/path", fixture.path());
        assertEquals("abc=new", fixture.rawQuery());
        assertEquals(singleton("abc"), fixture.queryParametersKeys());
        assertEquals(singletonList("new"), iteratorAsList(fixture.queryParametersIterator("abc")));
    }

    @Test
    public void testEncodeToRequestTargetWithNoParams() {
        createFixture("/some/path");
        setFixtureQueryParams(params);

        assertEquals("/some/path", fixture.requestTarget());
    }

    @Test
    public void testEncodeToRequestTargetWithParam() {
        createFixture("/some/path");
        params.put("foo", newList("bar", "baz"));
        setFixtureQueryParams(params);

        assertEquals("/some/path?foo=bar&foo=baz", fixture.requestTarget());
    }

    @Test
    public void testEncodeToRequestTargetWithMultipleParams() {
        createFixture("/some/path");
        params.put("foo", newList("bar", "baz"));
        params.put("abc", newList("123", "456"));
        setFixtureQueryParams(params);

        assertEquals("/some/path?foo=bar&foo=baz&abc=123&abc=456", fixture.requestTarget());
    }

    @Test
    public void testEncodeToRequestTargetWithSpecialCharacters() {
        createFixture("/some/path");
        params.put("pair", newList("key1=value1", "key2=value2"));
        setFixtureQueryParams(params);

        assertEquals("/some/path?pair=key1%3Dvalue1&pair=key2%3Dvalue2", fixture.requestTarget());
    }

    @Test
    public void testEncodeToRequestTargetWithAbsoluteForm() {
        createFixture("http://my.site.com/some/path?foo=bar&abc=def&foo=baz");
        params.put("foo", newList("new"));
        setFixtureQueryParams(params);

        assertEquals("http://my.site.com/some/path?foo=new", fixture.requestTarget());

        assertEquals("my.site.com", fixture.effectiveHost());
        assertEquals(-1, fixture.effectivePort());
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
