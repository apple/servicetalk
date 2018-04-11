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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.HttpQuery;

import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Before;
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
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.Arrays.asList;
import static java.util.Collections.addAll;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;

public class NettyToServiceTalkHttpRequestMetaDataTest {

    @Rule
    public final ExpectedException expected = ExpectedException.none();

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    private final HttpRequest nettyHttpRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "temp");

    private NettyToServiceTalkHttpRequestMetaData fixture;

    final Map<String, List<String>> params = new LinkedHashMap<>();

    @Before
    public void setUp() {
        // Must be created here to receive the mock
        fixture = new NettyToServiceTalkHttpRequestMetaData(nettyHttpRequest);
    }

    // https://tools.ietf.org/html/rfc7230#section-5.3.1
    @Test
    public void testParseUriOriginForm() {
        nettyHttpRequest.setUri("/some/path?foo=bar&abc=def&foo=baz");

        assertEquals("/some/path", fixture.getPath());
        assertEquals("/some/path", fixture.getRawPath());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.getRawQuery());
        assertEquals("/some/path?foo=bar&abc=def&foo=baz", fixture.getRequestTarget());
    }

    // https://tools.ietf.org/html/rfc7230#section-5.3.2
    @Test
    public void testParseUriAbsoluteForm() {
        // TODO: need to include user-info
        nettyHttpRequest.setUri("http://my.site.com/some/path?foo=bar&abc=def&foo=baz");

        assertEquals("/some/path", fixture.getPath());
        assertEquals("/some/path", fixture.getRawPath());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.getRawQuery());
        assertEquals("http://my.site.com/some/path?foo=bar&abc=def&foo=baz", fixture.getRequestTarget());
    }

    @Test
    public void testSetPathWithoutLeadingSlash() {
        fixture.setPath("foo");
        assertEquals("/foo", fixture.getRequestTarget());
    }

    @Test
    public void testSetRawPathWithoutLeadingSlash() {
        expected.expect(IllegalArgumentException.class);
        expected.expectMessage("Path must be empty or start with '/'");
        fixture.setRawPath("foo");
    }

    @Test
    public void testReplacePathInAbsoluteForm() {
        nettyHttpRequest.setUri("http://my.site.com/some/path?foo=bar&abc=def&foo=baz");
        fixture.setPath("/new/$path$");

        assertEquals("http://my.site.com/new/%24path%24?foo=bar&abc=def&foo=baz", fixture.getRequestTarget());
    }

    @Test
    public void testSetEmptyPathInAbsoluteForm() {
        nettyHttpRequest.setUri("http://my.site.com/some/path?foo=bar&abc=def&foo=baz");
        fixture.setPath("");

        assertEquals("http://my.site.com?foo=bar&abc=def&foo=baz", fixture.getRequestTarget());
    }

    @Test
    public void testReplacePathInOriginForm() {
        nettyHttpRequest.setUri("/some/path?foo=bar&abc=def&foo=baz");
        fixture.setPath("/new/$path$");

        assertEquals("/new/%24path%24?foo=bar&abc=def&foo=baz", fixture.getRequestTarget());
    }

    @Test
    public void testSetEmptyPathInOriginForm() {
        nettyHttpRequest.setUri("/some/path?foo=bar&abc=def&foo=baz");
        fixture.setPath("");

        assertEquals("?foo=bar&abc=def&foo=baz", fixture.getRequestTarget());
    }

    @Test
    public void testReplaceExistingQuery() {
        nettyHttpRequest.setUri("/some/path?foo=bar&abc=def&foo=baz");
        fixture.setRawQuery("new=query");

        assertEquals("/some/path?new=query", fixture.getRequestTarget());
    }

    @Test
    public void testSetRemoveExistingQuery() {
        nettyHttpRequest.setUri("/some/path?foo=bar&abc=def&foo=baz");
        fixture.setRawQuery("");

        assertEquals("/some/path", fixture.getRequestTarget());
    }

    @Test
    public void testAddQuery() {
        nettyHttpRequest.setUri("/some/path");
        fixture.setRawQuery("new=query");

        assertEquals("/some/path?new=query", fixture.getRequestTarget());
    }

    @Test
    public void testParseQuery() {
        nettyHttpRequest.setUri("/some/path?foo=bar&abc=def&foo=baz");
        final HttpQuery query = fixture.parseQuery();

        assertEquals(iteratorAsList(query.getKeys().iterator()), asList("foo", "abc"));
        assertEquals(query.get("foo"), "bar");
        assertEquals(iteratorAsList(query.getAll("foo")), asList("bar", "baz"));
        assertEquals(query.get("abc"), "def");
        assertEquals(iteratorAsList(query.getAll("abc")), singletonList("def"));
    }

    @Test
    public void testReencodeQuery() {
        nettyHttpRequest.setUri("/some/path?foo=bar&abc=def&foo=baz");
        final HttpQuery query = fixture.parseQuery();

        query.encodeToRequestTarget();
        assertEquals("/some/path?foo=bar&foo=baz&abc=def", fixture.getRequestTarget());
    }

    @Test
    public void testUriDoesNotChangeUntilReencode() {
        nettyHttpRequest.setUri("/some/path?foo=bar&abc=def&foo=baz");
        final HttpQuery query = fixture.parseQuery();
        query.set("abc", "new");

        assertEquals("/some/path?foo=bar&abc=def&foo=baz", fixture.getRequestTarget());

        query.encodeToRequestTarget();

        assertEquals("/some/path?foo=bar&foo=baz&abc=new", fixture.getRequestTarget());
    }

    @Test
    public void testSetPathAndReparse() {
        nettyHttpRequest.setUri("/some/path?foo=bar&abc=def&foo=baz");

        // parse it
        assertEquals("/some/path?foo=bar&abc=def&foo=baz", fixture.getRequestTarget());
        assertEquals("/some/path", fixture.getRawPath());
        assertEquals("/some/path", fixture.getPath());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.getRawQuery());
        final HttpQuery query = fixture.parseQuery();
        assertEquals(iteratorAsList(query.getAll("foo")), asList("bar", "baz"));
        assertEquals(iteratorAsList(query.getAll("abc")), singletonList("def"));

        // change it
        fixture.setPath("/new/$path$");

        // parse it again
        assertEquals("/new/%24path%24?foo=bar&abc=def&foo=baz", fixture.getRequestTarget());
        assertEquals("/new/%24path%24", fixture.getRawPath());
        assertEquals("/new/$path$", fixture.getPath());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.getRawQuery());
        final HttpQuery newQuery = fixture.parseQuery();
        assertEquals(iteratorAsList(newQuery.getAll("foo")), asList("bar", "baz"));
        assertEquals(iteratorAsList(newQuery.getAll("abc")), singletonList("def"));
    }

    @Test
    public void testSetQueryAndReparse() {
        nettyHttpRequest.setUri("/some/path?foo=bar&abc=def&foo=baz");

        // parse it
        assertEquals("/some/path?foo=bar&abc=def&foo=baz", fixture.getRequestTarget());
        assertEquals("/some/path", fixture.getRawPath());
        assertEquals("/some/path", fixture.getPath());
        assertEquals("foo=bar&abc=def&foo=baz", fixture.getRawQuery());
        final HttpQuery query = fixture.parseQuery();
        assertEquals(iteratorAsList(query.getAll("foo")), asList("bar", "baz"));
        assertEquals(iteratorAsList(query.getAll("abc")), singletonList("def"));

        // change it
        fixture.setRawQuery("abc=new");

        // parse it again
        assertEquals("/some/path?abc=new", fixture.getRequestTarget());
        assertEquals("/some/path", fixture.getRawPath());
        assertEquals("/some/path", fixture.getPath());
        assertEquals("abc=new", fixture.getRawQuery());
        final HttpQuery newQuery = fixture.parseQuery();
        assertEquals(newQuery.getKeys(), singleton("abc"));
        assertEquals(iteratorAsList(newQuery.getAll("abc")), singletonList("new"));
    }

    @Test
    public void testEncodeToRequestTargetWithNoParams() {
        nettyHttpRequest.setUri("/some/path");
        fixture.setQueryParams(params);

        assertEquals("/some/path", fixture.getRequestTarget());
    }

    @Test
    public void testEncodeToRequestTargetWithParam() {
        nettyHttpRequest.setUri("/some/path");
        params.put("foo", newList("bar", "baz"));
        fixture.setQueryParams(params);

        assertEquals("/some/path?foo=bar&foo=baz", fixture.getRequestTarget());
    }

    @Test
    public void testEncodeToRequestTargetWithMultipleParams() {
        nettyHttpRequest.setUri("/some/path");
        params.put("foo", newList("bar", "baz"));
        params.put("abc", newList("123", "456"));
        fixture.setQueryParams(params);

        assertEquals("/some/path?foo=bar&foo=baz&abc=123&abc=456", fixture.getRequestTarget());
    }

    @Test
    public void testEncodeToRequestTargetWithSpecialCharacters() {
        nettyHttpRequest.setUri("/some/path");
        params.put("pair", newList("key1=value1", "key2=value2"));
        fixture.setQueryParams(params);

        assertEquals("/some/path?pair=key1%3Dvalue1&pair=key2%3Dvalue2", fixture.getRequestTarget());
    }

    @Test
    public void testEncodeToRequestTargetWithAbsoluteForm() {
        nettyHttpRequest.setUri("http://my.site.com/some/path?foo=bar&abc=def&foo=baz");
        params.put("foo", newList("new"));
        fixture.setQueryParams(params);

        assertEquals("http://my.site.com/some/path?foo=new", fixture.getRequestTarget());
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> newList(final T... elements) {
        final List<T> list = new ArrayList<>(elements.length);
        addAll(list, elements);
        return list;
    }

    private <T> List<T> iteratorAsList(final Iterator<T> iterator) {
        return StreamSupport
                .stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
                .collect(Collectors.toList());
    }
}
