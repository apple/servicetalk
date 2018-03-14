/**
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

import io.netty.util.AsciiString;
import io.servicetalk.http.api.HttpCookies;
import io.servicetalk.http.api.HttpHeaders;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import static io.servicetalk.http.netty.HeaderUtils.DEFAULT_HEADER_FILTER;
import static io.servicetalk.http.netty.HttpHeaderNames.COOKIE;
import static io.servicetalk.http.netty.HttpHeaderNames.SET_COOKIE;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;

final class ReadOnlyHttpHeaders implements HttpHeaders {
    private final CharSequence[] keyValuePairs;

    ReadOnlyHttpHeaders(CharSequence... keyValuePairs) {
        if ((keyValuePairs.length & 1) != 0) {
            throw new IllegalArgumentException("keyValuePairs length must be even but was: " + keyValuePairs.length);
        }
        this.keyValuePairs = requireNonNull(keyValuePairs);
    }

    private int hashCode(CharSequence name) {
        return AsciiString.hashCode(name);
    }

    private boolean equals(CharSequence name1, CharSequence name2) {
        return AsciiString.contentEqualsIgnoreCase(name1, name2);
    }

    private boolean equalsValues(CharSequence name1, CharSequence name2) {
        return AsciiString.contentEquals(name1, name2);
    }

    @Nullable
    @Override
    public CharSequence get(CharSequence name) {
        int nameHash = hashCode(name);
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            final CharSequence currentName = keyValuePairs[i];
            if (nameHash == hashCode(currentName) && equals(currentName, name)) {
                return keyValuePairs[i + 1];
            }
        }
        return null;
    }

    @Nullable
    @Override
    public CharSequence getAndRemove(CharSequence name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<? extends CharSequence> getAll(CharSequence name) {
        return new ReadOnlyValueIterator(name);
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value) {
        int nameHash = hashCode(name);
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            final CharSequence currentName = keyValuePairs[i];
            if (nameHash == hashCode(currentName) && equals(currentName, name) &&
                    equalsValues(keyValuePairs[i + 1], value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value, boolean caseInsensitive) {
        if (caseInsensitive) {
            int nameHash = hashCode(name);
            final int end = keyValuePairs.length - 1;
            for (int i = 0; i < end; i += 2) {
                final CharSequence currentName = keyValuePairs[i];
                if (nameHash == hashCode(currentName) && equals(currentName, name) &&
                        equals(keyValuePairs[i + 1], value)) {
                    return true;
                }
            }
            return false;
        } else {
            return contains(name, value);
        }
    }

    @Override
    public int size() {
        return keyValuePairs.length >>> 1;
    }

    @Override
    public boolean isEmpty() {
        return keyValuePairs.length == 0;
    }

    @Override
    public Set<? extends CharSequence> getNames() {
        Set<CharSequence> nameSet = new HashSet<>(size());
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            nameSet.add(keyValuePairs[i]);
        }
        return unmodifiableSet(nameSet);
    }

    @Override
    public HttpHeaders add(CharSequence name, CharSequence value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders add(CharSequence name, Iterable<? extends CharSequence> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders add(CharSequence name, CharSequence... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders add(HttpHeaders headers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders set(CharSequence name, CharSequence value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders set(CharSequence name, Iterable<? extends CharSequence> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders set(CharSequence name, CharSequence... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders set(HttpHeaders headers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders setAll(HttpHeaders headers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(CharSequence name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Map.Entry<CharSequence, CharSequence>> iterator() {
        return new ReadOnlyIterator();
    }

    @Override
    public HttpHeaders copy() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof HttpHeaders && HeaderUtils.equals(this, (HttpHeaders) o);
    }

    @Override
    public int hashCode() {
        return HeaderUtils.hashCode(this);
    }

    @Override
    public String toString() {
        return toString(DEFAULT_HEADER_FILTER);
    }

    @Override
    public String toString(BiFunction<? super CharSequence, ? super CharSequence, CharSequence> filter) {
        return HeaderUtils.toString(this, filter);
    }

    @Override
    public HttpCookies parseCookies(boolean validateContent) {
        return new DefaultHttpCookies(this, COOKIE, validateContent);
    }

    @Override
    public HttpCookies parseSetCookies(boolean validateContent) {
        return new DefaultHttpCookies(this, SET_COOKIE, validateContent);
    }

    private final class ReadOnlyIterator implements Map.Entry<CharSequence, CharSequence>,
                                                    Iterator<Map.Entry<CharSequence, CharSequence>> {
        private int keyIndex;
        @Nullable
        private CharSequence key;
        @Nullable
        private CharSequence value;

        @Override
        public boolean hasNext() {
            return keyIndex != keyValuePairs.length;
        }

        @Override
        public Map.Entry<CharSequence, CharSequence> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            key = keyValuePairs[keyIndex];
            value = keyValuePairs[keyIndex + 1];
            keyIndex += 2;
            return this;
        }

        @Override
        public CharSequence getKey() {
            return key;
        }

        @Override
        public CharSequence getValue() {
            return value;
        }

        @Override
        public CharSequence setValue(CharSequence value) {
            throw new UnsupportedOperationException();
        }
    }

    private final class ReadOnlyValueIterator implements Iterator<CharSequence> {
        private final CharSequence name;
        private final int nameHash;
        private int keyIndex;
        @Nullable
        private CharSequence nextValue;

        ReadOnlyValueIterator(CharSequence name) {
            this.name = name;
            nameHash = ReadOnlyHttpHeaders.this.hashCode(name);
            calculateNext();
        }

        @Override
        public boolean hasNext() {
            return nextValue != null;
        }

        @Override
        public CharSequence next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            CharSequence current = nextValue;
            assert current != null;
            calculateNext();
            return current;
        }

        private void calculateNext() {
            final int end = keyValuePairs.length - 1;
            for (; keyIndex < end; keyIndex += 2) {
                final CharSequence currentName = keyValuePairs[keyIndex];
                if (nameHash == ReadOnlyHttpHeaders.this.hashCode(currentName) && ReadOnlyHttpHeaders.this.equals(name, currentName)) {
                    nextValue = keyValuePairs[keyIndex + 1];
                    keyIndex += 2;
                    return;
                }
            }
            nextValue = null;
        }
    }
}
