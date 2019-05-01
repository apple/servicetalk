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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.CharSequences.caseInsensitiveHashCode;
import static io.servicetalk.http.api.CharSequences.contentEquals;
import static io.servicetalk.http.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.http.api.CharSequences.indexOf;
import static io.servicetalk.http.api.CharSequences.regionMatches;
import static io.servicetalk.http.api.DefaultHttpCookiePair.parseCookiePair;
import static io.servicetalk.http.api.DefaultHttpSetCookie.parseSetCookie;
import static io.servicetalk.http.api.HeaderUtils.DEFAULT_HEADER_FILTER;
import static io.servicetalk.http.api.HeaderUtils.domainMatches;
import static io.servicetalk.http.api.HeaderUtils.pathMatches;
import static io.servicetalk.http.api.HttpHeaderNames.COOKIE;
import static io.servicetalk.http.api.HttpHeaderNames.SET_COOKIE;
import static java.util.Collections.emptyIterator;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;

final class ReadOnlyHttpHeaders implements HttpHeaders {
    private final CharSequence[] keyValuePairs;
    private final boolean validateCookies;

    ReadOnlyHttpHeaders(final CharSequence... keyValuePairs) {
        this(true, keyValuePairs);
    }

    ReadOnlyHttpHeaders(boolean validateCookies, final CharSequence... keyValuePairs) {
        if ((keyValuePairs.length & 1) != 0) {
            throw new IllegalArgumentException("keyValuePairs length must be even but was: " + keyValuePairs.length);
        }
        this.keyValuePairs = requireNonNull(keyValuePairs);
        this.validateCookies = validateCookies;
    }

    private static int hashCode(final CharSequence name) {
        return caseInsensitiveHashCode(name);
    }

    private static boolean equals(final CharSequence name1, final CharSequence name2) {
        return contentEqualsIgnoreCase(name1, name2);
    }

    private static boolean equalsValues(final CharSequence name1, final CharSequence name2) {
        return contentEquals(name1, name2);
    }

    @Nullable
    @Override
    public CharSequence get(final CharSequence name) {
        final int nameHash = hashCode(name);
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
    public CharSequence getAndRemove(final CharSequence name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<? extends CharSequence> values(final CharSequence name) {
        return new ReadOnlyValueIterator(name);
    }

    @Override
    public boolean contains(final CharSequence name, final CharSequence value, final boolean caseInsensitive) {
        final int nameHash = hashCode(name);
        final int end = keyValuePairs.length - 1;
        if (caseInsensitive) {
            for (int i = 0; i < end; i += 2) {
                final CharSequence currentName = keyValuePairs[i];
                if (nameHash == hashCode(currentName) && equals(currentName, name) &&
                        equals(keyValuePairs[i + 1], value)) {
                    return true;
                }
            }
            return false;
        } else {
            for (int i = 0; i < end; i += 2) {
                final CharSequence currentName = keyValuePairs[i];
                if (nameHash == hashCode(currentName) && equals(currentName, name) &&
                        equalsValues(keyValuePairs[i + 1], value)) {
                    return true;
                }
            }
        }
        return false;
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
    public Set<? extends CharSequence> names() {
        final Set<CharSequence> nameSet = new HashSet<>(size());
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            nameSet.add(keyValuePairs[i]);
        }
        return unmodifiableSet(nameSet);
    }

    @Override
    public HttpHeaders add(final CharSequence name, final CharSequence value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders add(final CharSequence name, final Iterable<? extends CharSequence> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders add(final CharSequence name, final CharSequence... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders add(final HttpHeaders headers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders set(final CharSequence name, final CharSequence value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders set(final CharSequence name, final Iterable<? extends CharSequence> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders set(final CharSequence name, final CharSequence... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders set(final HttpHeaders headers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders replace(final HttpHeaders headers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(final CharSequence name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(CharSequence name, CharSequence value, boolean caseInsensitive) {
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
    public boolean equals(final Object o) {
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

    @Nullable
    @Override
    public HttpCookiePair getCookie(final CharSequence name) {
        final int nameHash = hashCode(COOKIE);
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            final CharSequence currentName = keyValuePairs[i];
            final CharSequence currentValue = keyValuePairs[i + 1];
            if (nameHash == hashCode(currentName) && equals(currentName, COOKIE)) {
                int start = 0;
                for (;;) {
                    int j = indexOf(currentValue, ';', start);
                    // Check if the name of the cookie matches before doing a full parse of the cookie.
                    if (regionMatches(name, true, 0, currentValue, start, name.length())) {
                        return parseCookiePair(currentValue, start, name.length(), j);
                    } else if (j < 0 || currentValue.length() - 2 <= j) {
                        break;
                    }
                    // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
                    start = j + 2;
                }
            }
        }
        return null;
    }

    @Nullable
    @Override
    public HttpSetCookie getSetCookie(final CharSequence name) {
        final int nameHash = hashCode(SET_COOKIE);
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            final CharSequence currentName = keyValuePairs[i];
            final CharSequence currentValue = keyValuePairs[i + 1];
            if (nameHash == hashCode(currentName) && equals(currentName, SET_COOKIE) &&
                    // Check if the name of the cookie matches before doing a full parse of the cookie.
                    regionMatches(name, true, 0, currentValue, 0, name.length())) {
                return parseSetCookie(currentValue, validateCookies);
            }
        }
        return null;
    }

    @Override
    public Iterator<? extends HttpCookiePair> getCookies() {
        Iterator<? extends CharSequence> valueItr = values(HttpHeaderNames.COOKIE);
        return valueItr.hasNext() ? new ReadOnlyCookiesIterator(valueItr) : emptyIterator();
    }

    @Override
    public Iterator<? extends HttpCookiePair> getCookies(final CharSequence name) {
        return new ReadOnlyCookiesByNameIterator(values(HttpHeaderNames.COOKIE), name);
    }

    @Override
    public Iterator<? extends HttpSetCookie> getSetCookies() {
        final int nameHash = hashCode(SET_COOKIE);
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            final CharSequence currentName = keyValuePairs[i];
            if (nameHash == hashCode(currentName) && equals(currentName, SET_COOKIE)) {
                return new ReadOnlySetCookieIterator(i, nameHash, validateCookies);
            }
        }
        return emptyIterator();
    }

    @Override
    public Iterator<? extends HttpSetCookie> getSetCookies(final CharSequence name) {
        final int nameHash = hashCode(SET_COOKIE);
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            final CharSequence currentName = keyValuePairs[i];
            final CharSequence currentValue = keyValuePairs[i + 1];
            if (nameHash == hashCode(currentName) && equals(currentName, SET_COOKIE) &&
                    // Check if the name of the cookie matches before doing a full parse of the cookie.
                    regionMatches(name, true, 0, currentValue, 0, name.length())) {
                return new ReadOnlySetCookieNameIterator(i, nameHash, name, validateCookies);
            }
        }
        return emptyIterator();
    }

    @Override
    public Iterator<? extends HttpSetCookie> getSetCookies(final CharSequence name, final CharSequence domain,
                                                           final CharSequence path) {
        final int nameHash = hashCode(SET_COOKIE);
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            final CharSequence currentName = keyValuePairs[i];
            final CharSequence currentValue = keyValuePairs[i + 1];
            if (nameHash == hashCode(currentName) && equals(currentName, SET_COOKIE) &&
                    // Check if the name of the cookie matches before doing a full parse of the cookie.
                    regionMatches(name, true, 0, currentValue, 0, name.length())) {
                // In the future we could attempt to delay full parsing of the cookie until after the domain/path have
                // been matched, but for simplicity just do the parsing ahead of time.
                HttpSetCookie cookie = parseSetCookie(currentValue, validateCookies);
                if (domainMatches(domain, cookie.domain()) && pathMatches(path, cookie.path())) {
                    return new ReadOnlySetCookieNameDomainPathIterator(i, nameHash, cookie, name,
                            domain, path, validateCookies);
                }
            }
        }
        return emptyIterator();
    }

    @Override
    public HttpHeaders addCookie(final HttpCookiePair cookie) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders addSetCookie(final HttpSetCookie cookie) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeCookies(final CharSequence name) {
        return false;
    }

    @Override
    public boolean removeSetCookies(final CharSequence name) {
        return false;
    }

    @Override
    public boolean removeSetCookies(final CharSequence name, final CharSequence domain, final CharSequence path) {
        return false;
    }

    private static final class ReadOnlyCookiesIterator implements Iterator<HttpCookiePair> {
        private final Iterator<? extends CharSequence> valueItr;
        @Nullable
        private CharSequence headerValue;
        @Nullable
        private HttpCookiePair next;
        private int nextNextStart;

        ReadOnlyCookiesIterator(final Iterator<? extends CharSequence> valueItr) {
            this.valueItr = valueItr;
            assert valueItr.hasNext(); // this condition is checked before construction
            headerValue = valueItr.next();
            findNext0();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public HttpCookiePair next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            HttpCookiePair current = next;
            findNext();
            return current;
        }

        private void findNext() {
            if (headerValue == null) {
                if (valueItr.hasNext()) {
                    headerValue = valueItr.next();
                    nextNextStart = 0;
                } else {
                    next = null;
                    return;
                }
            }
            findNext0();
        }

        private void findNext0() {
            assert headerValue != null;
            int end = indexOf(headerValue, ';', nextNextStart);
            next = parseCookiePair(headerValue, nextNextStart, end);
            if (end > 0) {
                if (headerValue.length() - 2 <= end) {
                    headerValue = null;
                } else {
                    // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
                    nextNextStart = end + 2;
                }
            } else {
                headerValue = null;
            }
        }
    }

    private static final class ReadOnlyCookiesByNameIterator implements Iterator<HttpCookiePair> {
        private final Iterator<? extends CharSequence> valueItr;
        private final CharSequence name;
        @Nullable
        private CharSequence headerValue;
        @Nullable
        private HttpCookiePair next;
        private int nextNextStart;

        ReadOnlyCookiesByNameIterator(final Iterator<? extends CharSequence> valueItr, final CharSequence name) {
            this.valueItr = valueItr;
            this.name = name;
            if (valueItr.hasNext()) {
                headerValue = valueItr.next();
                findNext0();
            }
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public HttpCookiePair next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            HttpCookiePair current = next;
            findNext();
            return current;
        }

        private void findNext() {
            if (headerValue == null) {
                if (valueItr.hasNext()) {
                    headerValue = valueItr.next();
                    nextNextStart = 0;
                } else {
                    next = null;
                    return;
                }
            }
            findNext0();
        }

        private void findNext0() {
            assert headerValue != null;
            for (;;) {
                int end = indexOf(headerValue, ';', nextNextStart);
                if (regionMatches(name, true, 0, headerValue, nextNextStart, name.length())) {
                    next = parseCookiePair(headerValue, nextNextStart, name.length(), end);
                    if (end > 0) {
                        // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
                        if (headerValue.length() - 2 <= end) {
                            headerValue = null;
                        } else {
                            // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
                            nextNextStart = end + 2;
                        }
                    } else {
                        headerValue = null;
                    }
                    break;
                } else if (end > 0) {
                    if (headerValue.length() - 2 <= end) {
                        throw new IllegalArgumentException("cookie is not allowed to end with ;");
                    }
                    // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
                    nextNextStart = end + 2;
                } else if (valueItr.hasNext()) {
                    headerValue = valueItr.next();
                    nextNextStart = 0;
                } else {
                    headerValue = null;
                    next = null;
                    break;
                }
            }
        }
    }

    private final class ReadOnlySetCookieIterator implements Iterator<HttpSetCookie> {
        private final int nameHash;
        private final boolean validate;
        private int i;

        ReadOnlySetCookieIterator(int keyIndex, int nameHash, boolean validate) {
            this.i = keyIndex;
            this.nameHash = nameHash;
            this.validate = validate;
        }

        @Override
        public boolean hasNext() {
            return i < keyValuePairs.length;
        }

        @Override
        public HttpSetCookie next() {
            final int end = keyValuePairs.length - 1;
            if (i >= end) {
                throw new NoSuchElementException();
            }
            final HttpSetCookie next = parseSetCookie(keyValuePairs[i + 1], validate);
            i += 2;
            for (; i < end; i += 2) {
                final CharSequence currentName = keyValuePairs[i];
                if (nameHash == caseInsensitiveHashCode(currentName) &&
                        contentEqualsIgnoreCase(currentName, SET_COOKIE)) {
                    break;
                }
            }
            return next;
        }
    }

    private final class ReadOnlySetCookieNameIterator implements Iterator<HttpSetCookie> {
        private final int nameHash;
        private final CharSequence cookieName;
        private final boolean validate;
        private int i;

        ReadOnlySetCookieNameIterator(int keyIndex, int nameHash, CharSequence cookieName, boolean validate) {
            this.i = keyIndex;
            this.nameHash = nameHash;
            this.cookieName = cookieName;
            this.validate = validate;
        }

        @Override
        public boolean hasNext() {
            return i < keyValuePairs.length;
        }

        @Override
        public HttpSetCookie next() {
            final int end = keyValuePairs.length - 1;
            if (i >= end) {
                throw new NoSuchElementException();
            }
            final HttpSetCookie next = parseSetCookie(keyValuePairs[i + 1], validate);
            i += 2;
            for (; i < end; i += 2) {
                final CharSequence currentName = keyValuePairs[i];
                final CharSequence currentValue = keyValuePairs[i + 1];
                if (nameHash == caseInsensitiveHashCode(currentName) &&
                        contentEqualsIgnoreCase(currentName, SET_COOKIE) &&
                        // Check if the name of the cookie matches before doing a full parse of the cookie.
                        regionMatches(cookieName, true, 0, currentValue, 0, cookieName.length())) {
                    break;
                }
            }
            return next;
        }
    }

    private final class ReadOnlySetCookieNameDomainPathIterator implements Iterator<HttpSetCookie> {
        private final int nameHash;
        private final CharSequence cookieName;
        private final CharSequence domain;
        private final CharSequence path;
        private final boolean validate;
        private int i;
        @Nullable
        private HttpSetCookie cookie;

        ReadOnlySetCookieNameDomainPathIterator(int keyIndex, int nameHash, HttpSetCookie cookie,
                                                final CharSequence cookieName, final CharSequence domain,
                                                final CharSequence path, boolean validate) {
            this.i = keyIndex;
            this.nameHash = nameHash;
            this.cookieName = cookieName;
            this.domain = domain;
            this.path = path;
            this.cookie = cookie;
            this.validate = validate;
        }

        @Override
        public boolean hasNext() {
            return cookie != null;
        }

        @Override
        public HttpSetCookie next() {
            if (cookie == null) {
                throw new NoSuchElementException();
            }
            final HttpSetCookie next = cookie;
            i += 2;
            cookie = null;
            final int end = keyValuePairs.length - 1;
            for (; i < end; i += 2) {
                final CharSequence currentName = keyValuePairs[i];
                final CharSequence currentValue = keyValuePairs[i + 1];
                if (nameHash == caseInsensitiveHashCode(currentName) &&
                        contentEqualsIgnoreCase(currentName, SET_COOKIE) &&
                        // Check if the name of the cookie matches before doing a full parse of the cookie.
                        regionMatches(cookieName, true, 0, currentValue, 0, cookieName.length())) {
                    // In the future we could attempt to delay full parsing of the cookie until after the domain/path
                    // have been matched, but for simplicity just do the parsing ahead of time.
                    HttpSetCookie tmp = parseSetCookie(currentValue, validate);
                    if (domainMatches(domain, tmp.domain()) && pathMatches(path, tmp.path())) {
                        cookie = tmp;
                        break;
                    }
                }
            }
            return next;
        }
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
        @Nullable
        public CharSequence getKey() {
            return key;
        }

        @Override
        @Nullable
        public CharSequence getValue() {
            return value;
        }

        @Override
        public CharSequence setValue(final CharSequence value) {
            throw new UnsupportedOperationException();
        }
    }

    private final class ReadOnlyValueIterator implements Iterator<CharSequence> {
        private final CharSequence name;
        private final int nameHash;
        private int keyIndex;
        @Nullable
        private CharSequence nextValue;

        ReadOnlyValueIterator(final CharSequence name) {
            this.name = name;
            nameHash = ReadOnlyHttpHeaders.hashCode(name);
            calculateNext();
        }

        @Override
        public boolean hasNext() {
            return nextValue != null;
        }

        @Override
        public CharSequence next() {
            if (nextValue == null) {
                throw new NoSuchElementException();
            }
            final CharSequence current = nextValue;
            calculateNext();
            return current;
        }

        private void calculateNext() {
            final int end = keyValuePairs.length - 1;
            for (; keyIndex < end; keyIndex += 2) {
                final CharSequence currentName = keyValuePairs[keyIndex];
                if (nameHash == ReadOnlyHttpHeaders.hashCode(currentName) &&
                        ReadOnlyHttpHeaders.equals(name, currentName)) {
                    nextValue = keyValuePairs[keyIndex + 1];
                    keyIndex += 2;
                    return;
                }
            }
            nextValue = null;
        }
    }
}
