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
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.Arrays.asList;
import static java.util.Collections.addAll;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HttpQueryTest {

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();
    @Rule
    public final ExpectedException expected = ExpectedException.none();

    private final Map<String, List<String>> params = new LinkedHashMap<>();

    @Test
    public void testGetFirstValue() {
        params.put("foo", newList("bar", "baz"));
        final HttpQuery query = new HttpQuery(params);

        assertEquals("bar", query.get("foo"));
    }

    @Test
    public void testGetFirstValueNone() {
        final HttpQuery query = new HttpQuery(params);

        assertNull(query.get("foo"));
    }

    @Test
    public void testGetAll() {
        params.put("foo", newList("bar", "baz"));
        final HttpQuery query = new HttpQuery(params);

        assertEquals(asList("bar", "baz"), iteratorAsList(query.valuesIterator("foo")));
    }

    @Test
    public void testGetAllEmpty() {
        final HttpQuery query = new HttpQuery(params);

        assertFalse(query.valuesIterator("foo").hasNext());
    }

    @Test
    public void testGetKeys() {
        params.put("foo", newList("bar", "baz"));
        final HttpQuery query = new HttpQuery(params);

        assertEquals(asList("bar", "baz"), iteratorAsList(query.valuesIterator("foo")));
    }

    @Test
    public void testSet() {
        params.put("foo", newList("bar", "baz"));
        params.put("abc", newList("def"));
        final HttpQuery query = new HttpQuery(params);
        query.set("abc", "new");

        assertEquals(singletonList("new"), iteratorAsList(query.valuesIterator("abc")));
        assertEquals(asList("bar", "baz"), iteratorAsList(query.valuesIterator("foo")));
    }

    @Test
    public void testSetValues() {
        params.put("foo", newList("bar", "baz"));
        params.put("abc", newList("def"));
        final HttpQuery query = new HttpQuery(params);
        query.set("abc", newList("new1", "new2"));

        assertEquals(asList("new1", "new2"), iteratorAsList(query.valuesIterator("abc")));
        assertEquals(asList("bar", "baz"), iteratorAsList(query.valuesIterator("foo")));
    }

    @Test
    public void testAdd() {
        params.put("foo", newList("bar", "baz"));
        params.put("abc", newList("def"));
        final HttpQuery query = new HttpQuery(params);
        query.add("abc", "new");

        assertEquals(asList("def", "new"), iteratorAsList(query.valuesIterator("abc")));
        assertEquals(asList("bar", "baz"), iteratorAsList(query.valuesIterator("foo")));
    }

    @Test
    public void testAddValues() {
        params.put("foo", newList("bar", "baz"));
        params.put("abc", newList("def"));
        final HttpQuery query = new HttpQuery(params);
        query.add("abc", newList("new1", "new2"));

        assertEquals(asList("def", "new1", "new2"), iteratorAsList(query.valuesIterator("abc")));
        assertEquals(asList("bar", "baz"), iteratorAsList(query.valuesIterator("foo")));
    }

    @Test
    public void testContainsKey() {
        params.put("foo", newList("bar", "baz"));
        final HttpQuery query = new HttpQuery(params);

        assertTrue(query.keys().contains("foo"));
        assertFalse(query.keys().contains("abc"));
    }

    @Test
    public void testContainsKeyAndValue() {
        params.put("foo", newList("bar", "baz"));
        final HttpQuery query = new HttpQuery(params);

        assertTrue(query.contains("foo", "bar"));
        assertFalse(query.contains("foo", "new"));
    }

    @Test
    public void testRemoveKey() {
        params.put("foo", newList("bar", "baz"));
        final HttpQuery query = new HttpQuery(params);
        assertTrue(query.remove("foo"));
        assertFalse(query.keys().contains("foo"));
        assertFalse(query.remove("foo"));

        query.add("foo", "bar");
        assertTrue(query.remove("foo", "bar"));
        assertFalse(query.remove("foo"));

        assertFalse(query.keys().contains("foo"));
    }

    @Test
    public void testRemoveKeyAndValue() {
        params.put("foo", newList("bar", "baz"));
        final HttpQuery query = new HttpQuery(params);
        assertTrue(query.remove("foo", "bar"));
        assertFalse(query.remove("foo", "bar"));

        assertEquals("baz", query.get("foo"));
        assertEquals(singletonList("baz"), iteratorAsList(query.valuesIterator("foo")));
    }

    @Test
    public void testSize() {
        params.put("foo", newList("bar", "baz"));
        params.put("abc", newList("def", "123"));
        final HttpQuery query = new HttpQuery(params);

        assertEquals(4, query.size());
        query.remove("abc", "123");
        assertEquals(3, query.size());
        query.remove("abc", "def");
        assertEquals(2, query.size());
        query.remove("abc");
        assertEquals(2, query.size());
        query.add("foo", "456");
        assertEquals(3, query.size());
    }

    @Test
    public void testIterator() {
        params.put("foo", newList("bar", "baz"));
        params.put("abc", newList("def", "123"));
        final HttpQuery query = new HttpQuery(params);

        final Iterator<Map.Entry<String, String>> iterator = query.iterator();

        assertTrue(iterator.hasNext());
        Map.Entry<String, String> entry = iterator.next();
        assertEquals("foo", entry.getKey());
        assertEquals("bar", entry.getValue());

        assertTrue(iterator.hasNext());
        entry = iterator.next();
        assertEquals("foo", entry.getKey());
        assertEquals("baz", entry.getValue());

        assertTrue(iterator.hasNext());
        entry = iterator.next();
        assertEquals("abc", entry.getKey());
        assertEquals("def", entry.getValue());

        assertTrue(iterator.hasNext());
        entry = iterator.next();
        assertEquals("abc", entry.getKey());
        assertEquals("123", entry.getValue());

        assertFalse(iterator.hasNext());

        expected.expect(NoSuchElementException.class);
        iterator.next();
    }

    @Test
    public void testIteratorAfterRemoval() {
        params.put("foo", newList("bar", "baz"));
        params.put("abc", newList("def", "123"));
        final HttpQuery query = new HttpQuery(params);

        query.remove("abc", "def");
        query.remove("abc", "123");

        final Iterator<Map.Entry<String, String>> iterator = query.iterator();

        assertTrue(iterator.hasNext());
        Map.Entry<String, String> entry = iterator.next();
        assertEquals("foo", entry.getKey());
        assertEquals("bar", entry.getValue());

        assertTrue(iterator.hasNext());
        entry = iterator.next();
        assertEquals("foo", entry.getKey());
        assertEquals("baz", entry.getValue());

        assertFalse(iterator.hasNext());

        expected.expect(NoSuchElementException.class);
        iterator.next();
    }

    @Test
    public void testIteratorRemove() {
        params.put("foo", newList("bar", "baz"));
        params.put("abc", newList("def", "123"));
        final HttpQuery query = new HttpQuery(params);

        final Iterator<Map.Entry<String, String>> iterator = query.iterator();

        assertTrue(iterator.hasNext());
        Map.Entry<String, String> entry = iterator.next();
        assertEquals("foo", entry.getKey());
        assertEquals("bar", entry.getValue());
        iterator.remove();

        assertTrue(iterator.hasNext());
        entry = iterator.next();
        assertEquals("foo", entry.getKey());
        assertEquals("baz", entry.getValue());
        iterator.remove();

        assertTrue(iterator.hasNext());
        entry = iterator.next();
        assertEquals("abc", entry.getKey());
        assertEquals("def", entry.getValue());

        assertTrue(iterator.hasNext());
        entry = iterator.next();
        assertEquals("abc", entry.getKey());
        assertEquals("123", entry.getValue());

        assertFalse(iterator.hasNext());

        assertEquals(2, query.size());
        assertEquals(asList("def", "123"), iteratorAsList(query.valuesIterator("abc")));
        assertNull(query.get("foo"));

        expected.expect(NoSuchElementException.class);
        iterator.next();
    }

    @Test
    public void testIteratorRemoveTwice() {
        params.put("foo", newList("bar", "baz"));
        params.put("abc", newList("def", "123"));
        final HttpQuery query = new HttpQuery(params);

        final Iterator<Map.Entry<String, String>> iterator = query.iterator();

        iterator.next();
        iterator.remove();

        expected.expect(IllegalStateException.class);
        iterator.remove();
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
