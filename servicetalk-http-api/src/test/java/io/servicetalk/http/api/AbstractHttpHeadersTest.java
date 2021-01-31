/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;

import static io.servicetalk.buffer.internal.CharSequences.newAsciiString;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public abstract class AbstractHttpHeadersTest {

    protected abstract HttpHeaders newHeaders();

    protected abstract HttpHeaders newHeaders(int initialSizeHint);

    @Test
    public void minimalBucketsIterationOrder() {
        final HttpHeaders headers = newHeaders(1);
        headers.add("name1", "value1");
        headers.add("name2", "value2");
        headers.add("name3", "value3");
        headers.add("name4", "value4");
        headers.add("name1", "value5");
        headers.add("name2", "value6");
        headers.add("name3", "value7");
        headers.add("name4", "value8");
        assertEquals(8, headers.size());

        assertIteratorIs(headers.valuesIterator("name1"), "value1", "value5");
        assertIteratorIs(headers.valuesIterator("name2"), "value2", "value6");
        assertIteratorIs(headers.valuesIterator("name3"), "value3", "value7");
        assertIteratorIs(headers.valuesIterator("name4"), "value4", "value8");
        Set<Entry<CharSequence, CharSequence>> entries = new HashSet<>();
        entries.add(new SimpleEntry<>("name1", "value1"));
        entries.add(new SimpleEntry<>("name2", "value2"));
        entries.add(new SimpleEntry<>("name3", "value3"));
        entries.add(new SimpleEntry<>("name4", "value4"));
        entries.add(new SimpleEntry<>("name1", "value5"));
        entries.add(new SimpleEntry<>("name2", "value6"));
        entries.add(new SimpleEntry<>("name3", "value7"));
        entries.add(new SimpleEntry<>("name4", "value8"));
        for (Entry<CharSequence, CharSequence> header : headers) {
            assertTrue(entries.remove(header));
        }
        assertTrue(entries.isEmpty());
    }

    @Test(expected = ConcurrentModificationException.class)
    public void removalAndInsertionConcurrentModification() {
        final HttpHeaders headers = newHeaders(0);
        headers.add("name1", "value1");
        headers.add("name2", "value2");
        headers.add("name3", "value3");
        headers.add("name4", "value4");
        headers.add("name1", "value5");
        headers.add("name2", "value6");
        headers.add("name3", "value7");
        headers.add("name4", "value8");
        assertEquals(8, headers.size());

        final Iterator<? extends CharSequence> name2Itr = headers.valuesIterator("name2");
        final Iterator<Entry<CharSequence, CharSequence>> itr = headers.iterator();
        while (itr.hasNext()) {
            Entry<CharSequence, CharSequence> entry = itr.next();
            if (entry.getKey().toString().compareToIgnoreCase("name2") == 0) {
                itr.remove();
                break;
            }
        }
        headers.add("name2", "value9");
        headers.add("name5", "value10");
        headers.remove("name4");

        assertTrue(name2Itr.hasNext());
        assertEquals("value2", name2Itr.next()); // The first value is eagerly loaded.
        name2Itr.remove(); // this value has already been removed!
    }

    @Test
    public void removalAndInsertionDuringIteration() {
        final HttpHeaders headers = newHeaders(1);
        headers.add("name1", "value1");
        headers.add("name2", "value2");
        headers.add("name3", "value3");
        headers.add("name4", "value4");
        headers.add("name1", "value5");
        headers.add("name2", "value6");
        headers.add("name3", "value7");
        headers.add("name4", "value8");
        assertEquals(8, headers.size());

        final Iterator<Entry<CharSequence, CharSequence>> itr = headers.iterator();
        assertTrue(itr.hasNext());
        while (itr.hasNext()) {
            Entry<CharSequence, CharSequence> entry = itr.next();
            if (entry.getKey().toString().compareToIgnoreCase("name2") == 0) {
                itr.remove();
                break;
            }
        }
        headers.add("name2", "value9");
        headers.add("name5", "value10");
        headers.remove("name4");

        final Iterator<? extends CharSequence> nameItr = headers.valuesIterator("name2");
        assertTrue(nameItr.hasNext());
        assertEquals("value6", nameItr.next());
        nameItr.remove();
        assertIteratorIs(nameItr, "value9");
        assertIteratorIs(headers.valuesIterator("name1"), "value1", "value5");
        assertIteratorIs(headers.valuesIterator("name3"), "value3", "value7");
        assertFalse(headers.contains("name4"));
        assertIteratorIs(headers.valuesIterator("name5"), "value10");
    }

    @Test
    public void caseInsensitiveContains() {
        final HttpHeaders headers = newHeaders();
        headers.add("name1", "value1");
        assertTrue(headers.containsIgnoreCase("name1", "Value1"));
        assertFalse(headers.contains("name1", "Value1"));
    }

    @Test
    public void addIterableShouldIncreaseAndRemoveShouldDecreaseTheSize() {
        final HttpHeaders headers = newHeaders();
        assertEquals(0, headers.size());
        headers.add("name1", asList("value1", "value2"));
        assertEquals(2, headers.size());
        headers.add("name2", asList("value3", "value4"));
        assertEquals(4, headers.size());
        headers.add("name3", "value5");
        assertEquals(5, headers.size());

        headers.remove("name3");
        assertEquals(4, headers.size());
        headers.remove("name1");
        assertEquals(2, headers.size());
        headers.remove("name2");
        assertEquals(0, headers.size());
        assertTrue(headers.isEmpty());
    }

    @Test
    public void addShouldIncreaseAndRemoveShouldDecreaseTheSize() {
        final HttpHeaders headers = newHeaders();
        assertEquals(0, headers.size());
        headers.add("name1", "value1", "value2");
        assertEquals(2, headers.size());
        headers.add("name2", "value3", "value4");
        assertEquals(4, headers.size());
        headers.add("name3", "value5");
        assertEquals(5, headers.size());

        headers.remove("name3");
        assertEquals(4, headers.size());
        headers.remove("name1");
        assertEquals(2, headers.size());
        headers.remove("name2");
        assertEquals(0, headers.size());
        assertTrue(headers.isEmpty());
    }

    @Test
    public void afterClearHeadersShouldBeEmpty() {
        final HttpHeaders headers = newHeaders();
        headers.add("name1", "value1");
        headers.add("name2", "value2");
        assertEquals(2, headers.size());
        headers.clear();
        assertEquals(0, headers.size());
        assertTrue(headers.isEmpty());
        assertFalse(headers.contains("name1"));
        assertFalse(headers.contains("name2"));
    }

    @Test
    public void removingANameForASecondTimeShouldReturnFalse() {
        final HttpHeaders headers = newHeaders();
        headers.add("name1", "value1");
        headers.add("name2", "value2");
        assertTrue(headers.remove("name2"));
        assertFalse(headers.remove("name2"));
    }

    @Test
    public void multipleValuesPerNameShouldBeAllowed() {
        final HttpHeaders headers = newHeaders();
        headers.add("name", "value1");
        headers.add("name", "value2");
        headers.add("name", "value3");
        assertEquals(3, headers.size());

        assertIteratorIs(headers.valuesIterator("name"), "value1", "value2", "value3");
    }

    @Test
    public void absentHeaderIteratorEmpty() {
        final HttpHeaders headers = newHeaders();

        assertIteratorIs(headers.valuesIterator("name"), new String[]{});
    }

    @Test
    public void testContains() {
        final HttpHeaders headers = newHeaders();
        headers.add("name", "value");
        assertTrue(headers.contains("name", "value"));
        assertFalse(headers.contains("name", "value1"));
    }

    @Test
    public void testAddHeaders() {
        final HttpHeaders headers = newHeaders();
        headers.add("name", "value");

        final HttpHeaders headers2 = newHeaders().add(headers);

        assertTrue(headers2.contains("name", "value"));
        assertFalse(headers2.contains("name", "value1"));
        assertEquals(headers, headers2);
    }

    @Test
    public void testAddHeadersSlowPath() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders("name", "value");

        final HttpHeaders headers2 = newHeaders().add(headers);

        assertTrue(headers2.contains("name", "value"));
        assertFalse(headers2.contains("name", "value1"));
        assertEquals(headers, headers2);
    }

    @Test
    public void testCopy() {
        final HttpHeaders headers = newHeaders();
        headers.add("name", "value");

        final HttpHeaders copy = newHeaders().add(headers);

        assertNotSame(headers, copy);
        assertTrue(copy.contains("name", "value"));
        assertFalse(copy.contains("name", "value1"));
        assertEquals(headers, copy);
    }

    @Test
    public void testGetAndRemove() {
        final HttpHeaders headers = newHeaders();
        headers.add("name1", "value1");
        headers.add("name2", "value2", "value3");
        headers.add("name3", "value4", "value5", "value6");

        assertEquals("value1", headers.getAndRemove("name1", "defaultvalue"));
        assertEquals("value2", headers.getAndRemove("name2"));
        assertNull(headers.getAndRemove("name2"));
        final Iterator<? extends CharSequence> valueItr = headers.valuesIterator("name3");
        assertTrue(valueItr.hasNext());
        assertEquals("value4", valueItr.next());
        valueItr.remove();
        assertTrue(valueItr.hasNext());
        assertEquals("value5", valueItr.next());
        valueItr.remove();
        assertTrue(valueItr.hasNext());
        assertEquals("value6", valueItr.next());
        valueItr.remove();
        assertEquals(0, headers.size());
        assertNull(headers.getAndRemove("noname"));
        assertEquals("defaultvalue", headers.getAndRemove("noname", "defaultvalue"));
    }

    @Test
    public void whenNameContainsMultipleValuesGetShouldReturnTheFirst() {
        final HttpHeaders headers = newHeaders();
        headers.add("name1", "value1", "value2");
        assertEquals("value1", headers.get("name1"));
    }

    @Test
    public void getWithDefaultValueWorks() {
        final HttpHeaders headers = newHeaders();
        headers.add("name1", "value1");

        assertEquals("value1", headers.get("name1", "defaultvalue"));
        assertEquals("defaultvalue", headers.get("noname", "defaultvalue"));
    }

    @Test
    public void setShouldOverWritePreviousValue() {
        final HttpHeaders headers = newHeaders();
        headers.set("name", "value1");
        headers.set("name", "value2");
        assertEquals(1, headers.size());
        assertEquals("value2", headers.valuesIterator("name").next());
        assertEquals("value2", headers.get("name"));
    }

    @Test
    public void setIterableShouldOverWritePreviousValue() {
        final HttpHeaders headers = newHeaders();
        headers.set("name", "value1");
        headers.set("name", asList("value2", "value3"));
        assertEquals(2, headers.size());
        assertIteratorIs(headers.valuesIterator("name"), "value2", "value3");
        assertEquals("value2", headers.get("name"));
    }

    @Test
    public void setArrayShouldOverWritePreviousValue() {
        final HttpHeaders headers = newHeaders();
        headers.set("name", "value1");
        headers.set("name", "value2", "value3");
        assertEquals(2, headers.size());
        assertIteratorIs(headers.valuesIterator("name"), "value2", "value3");
        assertEquals("value2", headers.get("name"));
    }

    @Test
    public void setAllShouldOverwriteSomeAndLeaveOthersUntouched() {
        final HttpHeaders h1 = newHeaders();

        h1.add("name1", "value1");
        h1.add("name2", "value2");
        h1.add("name2", "value3");
        h1.add("name3", "value4");

        final HttpHeaders h2 = newHeaders();
        h2.add("name1", "value5");
        h2.add("name2", "value6");
        h2.add("name1", "value7");

        final HttpHeaders expected = newHeaders();
        expected.add("name1", "value5");
        expected.add("name2", "value6");
        expected.add("name1", "value7");
        expected.add("name3", "value4");

        h1.replace(h2);

        assertEquals(expected, h1);
    }

    @Test
    public void setHeadersShouldClear() {
        final HttpHeaders h1 = newHeaders();

        h1.add("name1", "value1");
        h1.add("name2", "value2");
        h1.add("name2", "value3");
        h1.add("name3", "value4");

        final HttpHeaders h2 = newHeaders();
        h2.add("name1", "value5");
        h2.add("name2", "value6");
        h2.add("name1", "value7");

        h1.set(h2);

        assertEquals(h2, h1);
    }

    @Test
    public void headersWithSameNamesAndValuesShouldBeEquivalent() {
        final HttpHeaders headers1 = newHeaders();
        headers1.add("name1", "value1");
        headers1.add("name2", "value2");
        headers1.add("name2", "value3");

        final HttpHeaders headers2 = newHeaders();
        headers2.add("name1", "value1");
        headers2.add("name2", "value2");
        headers2.add("name2", "value3");

        assertEquals(headers1, headers2);
        assertEquals(headers2, headers1);
        assertEquals(headers1, headers1);
        assertEquals(headers2, headers2);
        assertEquals(headers1.hashCode(), headers2.hashCode());
        assertEquals(headers1.hashCode(), headers1.hashCode());
        assertEquals(headers2.hashCode(), headers2.hashCode());
    }

    @Test
    public void emptyHeadersShouldBeEqual() {
        final HttpHeaders headers1 = newHeaders();
        final HttpHeaders headers2 = newHeaders();
        assertNotSame(headers1, headers2);
        assertEquals(headers1, headers2);
        assertEquals(headers1.hashCode(), headers2.hashCode());
    }

    @Test
    public void headersWithSameNamesButDifferentValuesShouldNotBeEquivalent() {
        final HttpHeaders headers1 = newHeaders();
        headers1.add("name1", "value1");
        final HttpHeaders headers2 = newHeaders();
        headers1.add("name1", "value2");
        assertNotEquals(headers1, headers2);
    }

    @Test
    public void subsetOfHeadersShouldNotBeEquivalent() {
        final HttpHeaders headers1 = newHeaders();
        headers1.add("name1", "value1");
        headers1.add("name2", "value2");
        final HttpHeaders headers2 = newHeaders();
        headers1.add("name1", "value1");
        assertNotEquals(headers1, headers2);
    }

    @Test
    public void headersWithDifferentNamesAndValuesShouldNotBeEquivalent() {
        final HttpHeaders h1 = newHeaders();
        h1.set("name1", "value1");
        final HttpHeaders h2 = newHeaders();
        h2.set("name2", "value2");
        assertNotEquals(h1, h2);
        assertNotEquals(h2, h1);
        assertEquals(h1, h1);
        assertEquals(h2, h2);
    }

    @Test(expected = IllegalStateException.class)
    public void entryIteratorThrowsIfNoNextCall() {
        final HttpHeaders headers = newHeaders();
        headers.add("name1", "value1");
        final Iterator<Entry<CharSequence, CharSequence>> itr = headers.iterator();
        assertTrue(itr.hasNext());
        itr.remove();
    }

    @Test(expected = IllegalStateException.class)
    public void entryIteratorThrowsIfDoubleRemove() {
        final HttpHeaders headers = newHeaders();
        headers.add("name1", "value1");
        final Iterator<Entry<CharSequence, CharSequence>> itr = headers.iterator();
        assertTrue(itr.hasNext());
        final Entry<CharSequence, CharSequence> next = itr.next();
        assertEquals("name1", next.getKey());
        assertEquals("value1", next.getValue());
        itr.remove();
        assertTrue(headers.isEmpty());
        assertEquals(0, headers.size());
        itr.remove();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidHeaderNameOutOfRangeCharacter() {
        final HttpHeaders headers = newHeaders();
        headers.add(String.valueOf((char) -1), "foo");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidHeaderNameOutOfRangeCharacterAsciiString() {
        final HttpHeaders headers = newHeaders();
        headers.add(newAsciiString(String.valueOf((char) -1)), "foo");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidHeaderNameCharacter() {
        final HttpHeaders headers = newHeaders();
        headers.add("=", "foo");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidHeaderNameCharacterAsciiString() {
        final HttpHeaders headers = newHeaders();
        headers.add(newAsciiString("="), "foo");
    }

    @Test(expected = NoSuchElementException.class)
    public void iterateEmptyHeadersShouldThrow() {
        final Iterator<Entry<CharSequence, CharSequence>> iterator = newHeaders().iterator();
        assertFalse(iterator.hasNext());
        iterator.next();
    }

    @Test
    public void iteratorShouldReturnAllNameValuePairs() {
        final HttpHeaders headers1 = newHeaders();
        headers1.add("name1", "value1", "value2");
        headers1.add("name2", "value3");
        headers1.add("name3", "value4", "value5", "value6");
        headers1.add("name1", "value7", "value8");
        assertEquals(8, headers1.size());

        final HttpHeaders headers2 = newHeaders();
        for (final Entry<CharSequence, CharSequence> entry : headers1) {
            headers2.add(entry.getKey(), entry.getValue());
        }

        assertEquals(headers1, headers2);
    }

    @Test
    public void iteratorSetValueShouldChangeHeaderValue() {
        final HttpHeaders headers = newHeaders();
        headers.add("name1", "value1", "value2", "value3");
        headers.add("name2", "value4");
        assertEquals(4, headers.size());

        for (final Entry<CharSequence, CharSequence> header : headers) {
            if ("name1".contentEquals(header.getKey()) && "value2".contentEquals(header.getValue())) {
                header.setValue("updatedvalue2");
                assertEquals("updatedvalue2", header.getValue());
            }
            if ("name1".contentEquals(header.getKey()) && "value3".contentEquals(header.getValue())) {
                header.setValue("updatedvalue3");
                assertEquals("updatedvalue3", header.getValue());
            }
        }

        assertEquals(4, headers.size());
        assertTrue(headers.contains("name1", "updatedvalue2"));
        assertFalse(headers.contains("name1", "value2"));
        assertTrue(headers.contains("name1", "updatedvalue3"));
        assertFalse(headers.contains("name1", "value3"));
    }

    @Test
    public void testEntryEquals() {
        final Entry<CharSequence, CharSequence> same1 = newHeaders().add("name", "value").iterator().next();
        final Entry<CharSequence, CharSequence> same2 = newHeaders().add("name", "value").iterator().next();
        assertEquals(same1, same2);
        assertEquals(same1.hashCode(), same2.hashCode());

        final Entry<CharSequence, CharSequence> nameDifferent1 = newHeaders().add("name1", "value").iterator().next();
        final Entry<CharSequence, CharSequence> nameDifferent2 = newHeaders().add("name2", "value").iterator().next();
        assertNotEquals(nameDifferent1, nameDifferent2);
        assertNotEquals(nameDifferent1.hashCode(), nameDifferent2.hashCode());

        final Entry<CharSequence, CharSequence> valueDifferent1 = newHeaders().add("name", "value1").iterator().next();
        final Entry<CharSequence, CharSequence> valueDifferent2 = newHeaders().add("name", "value2").iterator().next();
        assertNotEquals(valueDifferent1, valueDifferent2);
        assertNotEquals(valueDifferent1.hashCode(), valueDifferent2.hashCode());
    }

    @Test
    public void getAllReturnsEmptyListForUnknownName() {
        final HttpHeaders headers = newHeaders();
        assertFalse(headers.valuesIterator("noname").hasNext());
    }

    @Test
    public void setHeadersShouldClearAndOverwrite() {
        final HttpHeaders headers1 = newHeaders();
        headers1.add("name", "value");

        final HttpHeaders headers2 = newHeaders();
        headers2.add("name", "newvalue");
        headers2.add("name1", "value1");

        headers1.set(headers2);
        assertEquals(headers1, headers2);
    }

    @Test
    public void setAllHeadersShouldOnlyOverwriteHeaders() {
        final HttpHeaders headers1 = newHeaders();
        headers1.add("name", "value");
        headers1.add("name1", "value1");

        final HttpHeaders headers2 = newHeaders();
        headers2.add("name", "newvalue");
        headers2.add("name2", "value2");

        final HttpHeaders expected = newHeaders();
        expected.add("name", "newvalue");
        expected.add("name1", "value1");
        expected.add("name2", "value2");

        headers1.replace(headers2);
        assertEquals(headers1, expected);
    }

    @Test
    public void testAddSelf() {
        final HttpHeaders headers = newHeaders();
        headers.add("name", "value");
        assertEquals(1, headers.size());
        assertSame(headers, headers.add(headers));
        assertEquals(1, headers.size());
    }

    @Test
    public void testSetSelfIsNoOp() {
        final HttpHeaders headers = newHeaders();
        headers.add("name", "value");
        headers.set(headers);
        assertEquals(1, headers.size());
    }

    @Test
    public void testToString() {
        HttpHeaders headers = newHeaders();
        headers.add("name1", "value1");
        headers.add("name1", "value2");
        headers.add("name2", "value3");
        String result = headers.toString((name, value) -> value);
        assertTrue(result, result.startsWith(headers.getClass().getSimpleName() + "["));
        assertTrue(result, result.toLowerCase().contains("name1: value1"));
        assertTrue(result, result.toLowerCase().contains("name1: value2"));
        assertTrue(result, result.toLowerCase().contains("name2: value3"));

        headers = newHeaders();
        headers.add("name1", "value1");
        headers.add("name2", "value2");
        headers.add("name3", "value3");
        result = headers.toString((name, value) -> value);
        assertTrue(result, result.startsWith(headers.getClass().getSimpleName() + "["));
        assertTrue(result, result.toLowerCase().contains("name1: value1"));
        assertTrue(result, result.toLowerCase().contains("name2: value2"));
        assertTrue(result, result.toLowerCase().contains("name3: value3"));

        headers = newHeaders();
        headers.add("name1", "value1");
        result = headers.toString((name, value) -> value);
        assertTrue(result, result.startsWith(headers.getClass().getSimpleName() + "["));
        assertTrue(result, result.toLowerCase().contains("name1: value1"));

        headers = newHeaders();
        assertEquals(headers.getClass().getSimpleName() + "[]", headers.toString((name, value) -> value));
    }

    @Test
    public void testSimultaneousIteratorRemove() {
        final HttpHeaders h = newHeaders();
        h.add("n1", "v11");
        h.add("n2", "v21");
        h.add("n1", "v12");
        h.add("n2", "v22");

        final Iterator<? extends CharSequence> iter1 = h.valuesIterator("n1");
        final Iterator<? extends CharSequence> iter2 = h.valuesIterator("n2");
        assertTrue(iter1.hasNext());
        assertTrue(iter2.hasNext());
        assertNotNull(iter1.next());
        iter1.remove();
        assertNotNull(iter2.next());
        assertNotNull(iter2.next());
    }

    @Test
    public void getValueIteratorRemove() {
        final HttpHeaders headers = newHeaders();
        headers.add("name1", "value1");
        headers.add("name1", "value2");
        headers.add("name2", "value3");

        Iterator<? extends CharSequence> headerItr = headers.valuesIterator("name1");
        assertTrue(headerItr.hasNext());
        headerItr.next();
        headerItr.remove();

        assertTrue(headerItr.hasNext());
        headerItr.next();
        headerItr.remove();

        assertFalse(headerItr.hasNext());

        headerItr = headers.valuesIterator("name1");
        assertFalse(headerItr.hasNext());

        headerItr = headers.valuesIterator("name2");
        assertTrue(headerItr.hasNext());
        assertEquals("value3", headerItr.next());
        assertFalse(headerItr.hasNext());
    }

    @Test
    public void overallIteratorRemoveFirstAndLast() {
        final HttpHeaders headers = newHeaders();
        headers.add("name1", "value1");
        headers.add("name2", "value2");
        headers.add("name2", "value3");
        headers.add("name3", "value4");

        // Overall iteration order isn't defined, so track which elements we don't expect to be present after removal.
        final Set<String> removedNameValue = new HashSet<>();

        // Remove the first and last element
        Iterator<Entry<CharSequence, CharSequence>> headersItr = headers.iterator();
        assertTrue(headersItr.hasNext());
        Entry<CharSequence, CharSequence> entry = headersItr.next();
        removedNameValue.add(entry.getKey() + "=" + entry.getValue());
        headersItr.remove();

        assertTrue(headersItr.hasNext());
        headersItr.next();

        assertTrue(headersItr.hasNext());
        headersItr.next();

        assertTrue(headersItr.hasNext());
        entry = headersItr.next();
        removedNameValue.add(entry.getKey() + "=" + entry.getValue());
        headersItr.remove();
        assertFalse(headersItr.hasNext());

        headersItr = headers.iterator();
        assertTrue(headersItr.hasNext());
        entry = headersItr.next();
        assertFalse(removedNameValue.contains(entry.getKey() + "=" + entry.getValue()));

        assertTrue(headersItr.hasNext());
        entry = headersItr.next();
        assertFalse(removedNameValue.contains(entry.getKey() + "=" + entry.getValue()));

        assertFalse(headersItr.hasNext());
    }

    @Test
    public void overallIteratorRemoveMiddle() {
        final HttpHeaders headers = newHeaders();
        headers.add("name1", "value1");
        headers.add("name2", "value2");
        headers.add("name2", "value3");
        headers.add("name3", "value4");

        // Overall iteration order isn't defined, so track which elements we don't expect to be present after removal.
        final Set<String> removedNameValue = new HashSet<>();

        // Remove the first and last element
        Iterator<Entry<CharSequence, CharSequence>> headersItr = headers.iterator();
        assertTrue(headersItr.hasNext());
        headersItr.next();

        assertTrue(headersItr.hasNext());
        Entry<CharSequence, CharSequence> entry = headersItr.next();
        removedNameValue.add(entry.getKey() + "=" + entry.getValue());
        headersItr.remove();

        assertTrue(headersItr.hasNext());
        entry = headersItr.next();
        removedNameValue.add(entry.getKey() + "=" + entry.getValue());
        headersItr.remove();

        assertTrue(headersItr.hasNext());
        headersItr.next();
        assertFalse(headersItr.hasNext());

        headersItr = headers.iterator();
        assertTrue(headersItr.hasNext());
        entry = headersItr.next();
        assertFalse(removedNameValue.contains(entry.getKey() + "=" + entry.getValue()));

        assertTrue(headersItr.hasNext());
        entry = headersItr.next();
        assertFalse(removedNameValue.contains(entry.getKey() + "=" + entry.getValue()));

        assertFalse(headersItr.hasNext());
    }

    @Test
    public void overallIteratorRemoveAll() {
        final HttpHeaders headers = newHeaders();
        headers.add("name1", "value1");
        headers.add("name2", "value2");
        headers.add("name2", "value3");
        headers.add("name3", "value4");

        // Remove the first and last element
        Iterator<Entry<CharSequence, CharSequence>> headersItr = headers.iterator();
        assertTrue(headersItr.hasNext());
        headersItr.next();
        headersItr.remove();

        assertTrue(headersItr.hasNext());
        headersItr.next();
        headersItr.remove();

        assertTrue(headersItr.hasNext());
        headersItr.next();
        headersItr.remove();

        assertTrue(headersItr.hasNext());
        headersItr.next();
        headersItr.remove();
        assertFalse(headersItr.hasNext());

        headersItr = headers.iterator();
        assertFalse(headersItr.hasNext());
    }

    @Test
    public void removeByNameAndValuePairWhichDoesNotExist() {
        final HttpHeaders headers = newHeaders();
        headers.add("name1", "value1");
        headers.add("name2", asList("value1", "value2"));
        headers.add("name3", "value1");
        assertEquals(4, headers.size());

        assertFalse(headers.remove("nameX", "valueX"));
        assertFalse(headers.removeIgnoreCase("nameX", "valueX"));
        assertFalse(headers.remove("nameX", "value1"));
        assertFalse(headers.removeIgnoreCase("nameX", "value1"));
        assertFalse(headers.remove("name1", "valueX"));
        assertFalse(headers.removeIgnoreCase("name1", "valueX"));
        assertNotNull(headers.get("name1"));
        assertEquals(4, headers.size());
    }

    @Test
    public void removeByNameValueAndCase() {
        final HttpHeaders headers = newHeaders();
        headers.add("name1", "value1");
        headers.add("name2", asList("value1", "value2"));
        headers.add("name3", "value1");
        headers.add("name4", asList("value1", "Value1", "vAlUe1", "vaLue1", "value1"));
        assertEquals(9, headers.size());

        assertTrue(headers.remove("name2", "value2"));
        assertNotNull(headers.get("name2"));
        assertEquals(8, headers.size());

        assertFalse(headers.remove("name2", "value2"));
        assertNotNull(headers.get("name2"));
        assertEquals(8, headers.size());

        assertFalse(headers.remove("name2", "VaLue1"));
        assertNotNull(headers.get("name2"));
        assertEquals(8, headers.size());

        assertTrue(headers.remove("name2", "value1"));
        assertNull(headers.get("name2"));
        assertEquals(7, headers.size());

        assertFalse(headers.remove("name2", "value1"));
        assertNull(headers.get("name2"));
        assertEquals(7, headers.size());

        assertTrue(headers.remove("NaMe1", "value1"));
        assertNull(headers.get("name1"));
        assertEquals(6, headers.size());

        assertTrue(headers.remove("name4", "value1"));
        assertNotNull(headers.get("name4"));
        assertEquals(4, headers.size());
    }

    @Test
    public void removeByNameAndValueCaseInsensitive() {
        final HttpHeaders headers = newHeaders();
        headers.add("name1", "value1");
        headers.add("name2", asList("value1", "value2"));
        headers.add("name3", "value1");
        headers.add("name4", asList("value1", "Value1", "vAlUe1", "vaLue1", "value1"));
        assertEquals(9, headers.size());

        assertTrue(headers.removeIgnoreCase("name2", "value2"));
        assertNotNull(headers.get("name2"));
        assertEquals(8, headers.size());

        assertFalse(headers.removeIgnoreCase("name2", "value2"));
        assertNotNull(headers.get("name2"));
        assertEquals(8, headers.size());

        assertTrue(headers.removeIgnoreCase("name2", "VaLue1"));
        assertNull(headers.get("name2"));
        assertEquals(7, headers.size());

        assertFalse(headers.removeIgnoreCase("name2", "value1"));
        assertNull(headers.get("name2"));
        assertEquals(7, headers.size());

        assertTrue(headers.removeIgnoreCase("NaMe1", "value1"));
        assertNull(headers.get("name1"));
        assertEquals(6, headers.size());

        assertTrue(headers.removeIgnoreCase("NaMe3", "VaLue1"));
        assertNull(headers.get("name3"));
        assertEquals(5, headers.size());

        assertTrue(headers.removeIgnoreCase("name4", "value1"));
        assertNull(headers.get("name4"));
        assertTrue(headers.isEmpty());
    }

    @SafeVarargs
    private static <T> void assertIteratorIs(final Iterator<? extends T> iterator, final T... elements) {
        final List<T> list = new ArrayList<>();
        iterator.forEachRemaining(list::add);
        assertThat(list, is(asList(elements)));
    }
}
