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
import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.CharSequences.caseInsensitiveHashCode;
import static io.servicetalk.http.api.CharSequences.contentEquals;
import static io.servicetalk.http.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.http.api.CharSequences.regionMatches;
import static io.servicetalk.http.api.DefaultHttpCookie.parseCookie;
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
    public Iterator<? extends CharSequence> getAll(final CharSequence name) {
        return new ReadOnlyValueIterator(name);
    }

    @Override
    public boolean contains(final CharSequence name, final CharSequence value) {
        final int nameHash = hashCode(name);
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
    public boolean contains(final CharSequence name, final CharSequence value, final boolean caseInsensitive) {
        if (caseInsensitive) {
            final int nameHash = hashCode(name);
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
    public boolean empty() {
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
    public HttpHeaders setAll(final HttpHeaders headers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(final CharSequence name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(final CharSequence name, final CharSequence value) {
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
    public HttpHeaders copy() {
        return this;
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

    @Override
    public String toString(final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> filter) {
        return HeaderUtils.toString(this, filter);
    }

    @Nullable
    @Override
    public HttpCookie getCookie(final CharSequence name) {
        return getAndParseCookie(COOKIE, name, validateCookies);
    }

    @Nullable
    @Override
    public HttpCookie getSetCookie(final CharSequence name) {
        return getAndParseCookie(SET_COOKIE, name, validateCookies);
    }

    @Override
    public Iterator<? extends HttpCookie> getCookies() {
        return getAndParseCookies(COOKIE, validateCookies);
    }

    @Override
    public Iterator<? extends HttpCookie> getCookies(final CharSequence name) {
        return getAndParseCookies(COOKIE, name, validateCookies);
    }

    @Override
    public Iterator<? extends HttpCookie> getSetCookies() {
        return getAndParseCookies(SET_COOKIE, validateCookies);
    }

    @Override
    public Iterator<? extends HttpCookie> getSetCookies(final CharSequence name) {
        return getAndParseCookies(SET_COOKIE, name, validateCookies);
    }

    @Override
    public Iterator<? extends HttpCookie> getCookies(final CharSequence name, final CharSequence domain,
                                                     final CharSequence path) {
        return getAndParseCookies(COOKIE, name, domain, path, validateCookies);
    }

    @Override
    public Iterator<? extends HttpCookie> getSetCookies(final CharSequence name, final CharSequence domain,
                                                        final CharSequence path) {
        return getAndParseCookies(SET_COOKIE, name, domain, path, validateCookies);
    }

    @Override
    public HttpHeaders addCookie(final HttpCookie cookie) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders addSetCookie(final HttpCookie cookie) {
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
    public boolean removeCookies(final CharSequence name, final CharSequence domain, final CharSequence path) {
        return false;
    }

    @Override
    public boolean removeSetCookies(final CharSequence name, final CharSequence domain, final CharSequence path) {
        return false;
    }

    @Nullable
    private HttpCookie getAndParseCookie(CharSequence cookieHeaderName, CharSequence cookieName, boolean validate) {
        final int nameHash = hashCode(cookieHeaderName);
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            final CharSequence currentName = keyValuePairs[i];
            final CharSequence currentValue = keyValuePairs[i + 1];
            if (nameHash == hashCode(currentName) && equals(currentName, cookieHeaderName) &&
                    // Check if the name of the cookie matches before doing a full parse of the cookie.
                    regionMatches(cookieName, true, 0, currentValue, 0, cookieName.length())) {
                return parseCookie(currentValue, validate);
            }
        }
        return null;
    }

    private Iterator<? extends HttpCookie> getAndParseCookies(final CharSequence cookieHeaderName,
                                                              final boolean validateCookies) {
        final int nameHash = hashCode(cookieHeaderName);
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            final CharSequence currentName = keyValuePairs[i];
            if (nameHash == hashCode(currentName) && equals(currentName, cookieHeaderName)) {
                return new ReadOnlyCookieIterator(i, nameHash, cookieHeaderName, validateCookies);
            }
        }
        return emptyIterator();
    }

    private Iterator<? extends HttpCookie> getAndParseCookies(final CharSequence cookieHeaderName,
                                                              final CharSequence cookieName,
                                                              final boolean validateCookies) {
        final int nameHash = hashCode(cookieHeaderName);
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            final CharSequence currentName = keyValuePairs[i];
            final CharSequence currentValue = keyValuePairs[i + 1];
            if (nameHash == hashCode(currentName) && equals(currentName, cookieHeaderName) &&
                    // Check if the name of the cookie matches before doing a full parse of the cookie.
                    regionMatches(cookieName, true, 0, currentValue, 0, cookieName.length())) {
                return new ReadOnlyCookieNameIterator(i, nameHash, cookieHeaderName, cookieName, validateCookies);
            }
        }
        return emptyIterator();
    }

    private Iterator<? extends HttpCookie> getAndParseCookies(final CharSequence cookieHeaderName,
                                                              final CharSequence cookieName, final CharSequence domain,
                                                              final CharSequence path, final boolean validateCookies) {
        final int nameHash = hashCode(cookieHeaderName);
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            final CharSequence currentName = keyValuePairs[i];
            final CharSequence currentValue = keyValuePairs[i + 1];
            if (nameHash == hashCode(currentName) && equals(currentName, cookieHeaderName) &&
                    // Check if the name of the cookie matches before doing a full parse of the cookie.
                    regionMatches(cookieName, true, 0, currentValue, 0, cookieName.length())) {
                // In the future we could attempt to delay full parsing of the cookie until after the domain/path have
                // been matched, but for simplicity just do the parsing ahead of time.
                HttpCookie cookie = parseCookie(currentValue, validateCookies);
                if (domainMatches(domain, cookie.domain()) && pathMatches(path, cookie.path())) {
                    return new ReadOnlyCookieNameDomainPathIterator(i, nameHash, cookieHeaderName, cookie, cookieName,
                            domain, path, validateCookies);
                }
            }
        }
        return emptyIterator();
    }

    private final class ReadOnlyCookieIterator implements Iterator<HttpCookie> {
        private final int nameHash;
        private final CharSequence cookieHeaderName;
        private final boolean validate;
        private int i;

        ReadOnlyCookieIterator(int keyIndex, int nameHash, CharSequence cookieHeaderName, boolean validate) {
            this.i = keyIndex;
            this.nameHash = nameHash;
            this.cookieHeaderName = cookieHeaderName;
            this.validate = validate;
        }

        @Override
        public boolean hasNext() {
            return i < keyValuePairs.length;
        }

        @Override
        public HttpCookie next() {
            final int end = keyValuePairs.length - 1;
            if (i >= end) {
                throw new NoSuchElementException();
            }
            final HttpCookie next = parseCookie(keyValuePairs[i + 1], validate);
            i += 2;
            for (; i < end; i += 2) {
                final CharSequence currentName = keyValuePairs[i];
                if (nameHash == caseInsensitiveHashCode(currentName) &&
                        contentEqualsIgnoreCase(currentName, cookieHeaderName)) {
                    break;
                }
            }
            return next;
        }
    }

    private final class ReadOnlyCookieNameIterator implements Iterator<HttpCookie> {
        private final int nameHash;
        private final CharSequence cookieHeaderName;
        private final CharSequence cookieName;
        private final boolean validate;
        private int i;

        ReadOnlyCookieNameIterator(int keyIndex, int nameHash, CharSequence cookieHeaderName, CharSequence cookieName,
                                   boolean validate) {
            this.i = keyIndex;
            this.nameHash = nameHash;
            this.cookieHeaderName = cookieHeaderName;
            this.cookieName = cookieName;
            this.validate = validate;
        }

        @Override
        public boolean hasNext() {
            return i < keyValuePairs.length;
        }

        @Override
        public HttpCookie next() {
            final int end = keyValuePairs.length - 1;
            if (i >= end) {
                throw new NoSuchElementException();
            }
            final HttpCookie next = parseCookie(keyValuePairs[i + 1], validate);
            i += 2;
            for (; i < end; i += 2) {
                final CharSequence currentName = keyValuePairs[i];
                final CharSequence currentValue = keyValuePairs[i + 1];
                if (nameHash == caseInsensitiveHashCode(currentName) &&
                        contentEqualsIgnoreCase(currentName, cookieHeaderName) &&
                        // Check if the name of the cookie matches before doing a full parse of the cookie.
                        regionMatches(cookieName, true, 0, currentValue, 0, cookieName.length())) {
                    break;
                }
            }
            return next;
        }
    }

    private final class ReadOnlyCookieNameDomainPathIterator implements Iterator<HttpCookie> {
        private final int nameHash;
        private final CharSequence cookieHeaderName;
        private final CharSequence cookieName;
        private final CharSequence domain;
        private final CharSequence path;
        private final boolean validate;
        private int i;
        @Nullable
        private HttpCookie cookie;

        ReadOnlyCookieNameDomainPathIterator(int keyIndex, int nameHash, CharSequence cookieHeaderName,
                                             HttpCookie cookie, final CharSequence cookieName,
                                             final CharSequence domain, final CharSequence path, boolean validate) {
            this.i = keyIndex;
            this.nameHash = nameHash;
            this.cookieHeaderName = cookieHeaderName;
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
        public HttpCookie next() {
            if (cookie == null) {
                throw new NoSuchElementException();
            }
            final HttpCookie next = cookie;
            i += 2;
            cookie = null;
            final int end = keyValuePairs.length - 1;
            for (; i < end; i += 2) {
                final CharSequence currentName = keyValuePairs[i];
                final CharSequence currentValue = keyValuePairs[i + 1];
                if (nameHash == caseInsensitiveHashCode(currentName) &&
                        contentEqualsIgnoreCase(currentName, cookieHeaderName) &&
                        // Check if the name of the cookie matches before doing a full parse of the cookie.
                        regionMatches(cookieName, true, 0, currentValue, 0, cookieName.length())) {
                    // In the future we could attempt to delay full parsing of the cookie until after the domain/path
                    // have been matched, but for simplicity just do the parsing ahead of time.
                    HttpCookie tmp = parseCookie(currentValue, validate);
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
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            final CharSequence current = nextValue;
            assert current != null;
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
