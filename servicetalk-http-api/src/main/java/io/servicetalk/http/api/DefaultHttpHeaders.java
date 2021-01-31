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

import io.servicetalk.buffer.api.CharSequences;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.caseInsensitiveHashCode;
import static io.servicetalk.buffer.api.CharSequences.contentEquals;
import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.http.api.DefaultHttpSetCookie.parseSetCookie;
import static io.servicetalk.http.api.HeaderUtils.DEFAULT_HEADER_FILTER;
import static io.servicetalk.http.api.HeaderUtils.domainMatches;
import static io.servicetalk.http.api.HeaderUtils.isSetCookieNameMatches;
import static io.servicetalk.http.api.HeaderUtils.parseCookiePair;
import static io.servicetalk.http.api.HeaderUtils.pathMatches;
import static io.servicetalk.http.api.HeaderUtils.validateCookieTokenAndHeaderName;
import static io.servicetalk.http.api.HttpHeaderNames.COOKIE;
import static io.servicetalk.http.api.HttpHeaderNames.SET_COOKIE;
import static java.util.Collections.emptyIterator;

/**
 * Default implementation of {@link HttpHeaders}.
 */
final class DefaultHttpHeaders extends MultiMap<CharSequence, CharSequence> implements HttpHeaders {
    private final boolean validateNames;
    private final boolean validateCookies;

    /**
     * Create a new instance.
     *
     * @param arraySizeHint A hint as to how large the hash data structure should be.
     *                      The next positive power of two will be used. An upper bound may be enforced.
     * @param validateNames {@code true} to validate header names.
     * @param validateCookies {@code true} to validate cookie contents when parsing.
     */
    DefaultHttpHeaders(final int arraySizeHint, final boolean validateNames, final boolean validateCookies) {
        super(arraySizeHint);
        this.validateNames = validateNames;
        this.validateCookies = validateCookies;
    }

    @Override
    public boolean containsIgnoreCase(final CharSequence name, final CharSequence value) {
        return contains(name, value, CharSequences::contentEqualsIgnoreCase);
    }

    @Nullable
    @Override
    public HttpCookiePair getCookie(final CharSequence name) {
        final int keyHash = hashCode(COOKIE);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[index(keyHash)];
        if (bucketHead == null) {
            return null;
        }
        MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
        assert e != null;
        do {
            if (e.keyHash == keyHash && contentEqualsIgnoreCase(COOKIE, e.getKey())) {
                HttpCookiePair cookiePair = parseCookiePair(e.value, name);
                if (cookiePair != null) {
                    return cookiePair;
                }
            }
            e = e.bucketNext;
        } while (e != null);
        return null;
    }

    @Nullable
    @Override
    public HttpSetCookie getSetCookie(final CharSequence name) {
        final int keyHash = hashCode(SET_COOKIE);
        final int i = index(keyHash);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[i];
        if (bucketHead != null) {
            MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
            assert e != null;
            do {
                if (e.keyHash == keyHash && contentEqualsIgnoreCase(SET_COOKIE, e.getKey())) {
                    HttpSetCookie setCookie = HeaderUtils.parseSetCookie(e.value, name, validateCookies);
                    if (setCookie != null) {
                        return setCookie;
                    }
                }
                e = e.bucketNext;
            } while (e != null);
        }
        return null;
    }

    @Override
    public Iterator<? extends HttpCookiePair> getCookiesIterator() {
        final int keyHash = hashCode(COOKIE);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[index(keyHash)];
        if (bucketHead == null) {
            return emptyIterator();
        }
        MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
        assert e != null;
        do {
            if (e.keyHash == keyHash && contentEqualsIgnoreCase(COOKIE, e.getKey())) {
                return new CookiesIterator(keyHash, e);
            }
            e = e.bucketNext;
        } while (e != null);
        return emptyIterator();
    }

    @Override
    public Iterator<? extends HttpCookiePair> getCookiesIterator(final CharSequence name) {
        final int keyHash = hashCode(COOKIE);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[index(keyHash)];
        if (bucketHead == null) {
            return emptyIterator();
        }
        MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
        assert e != null;
        do {
            if (e.keyHash == keyHash && contentEqualsIgnoreCase(COOKIE, e.getKey())) {
                return new CookiesByNameIterator(keyHash, e, name);
            }
            e = e.bucketNext;
        } while (e != null);
        return emptyIterator();
    }

    @Override
    public Iterator<? extends HttpSetCookie> getSetCookiesIterator() {
        final int keyHash = hashCode(SET_COOKIE);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[index(keyHash)];
        if (bucketHead == null) {
            return emptyIterator();
        }
        MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
        assert e != null;
        do {
            if (e.keyHash == keyHash && contentEqualsIgnoreCase(SET_COOKIE, e.getKey())) {
                return new SetCookiesIterator(e);
            }
            e = e.bucketNext;
        } while (e != null);
        return emptyIterator();
    }

    @Override
    public Iterator<? extends HttpSetCookie> getSetCookiesIterator(final CharSequence name) {
        final int keyHash = hashCode(SET_COOKIE);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[index(keyHash)];
        if (bucketHead == null) {
            return emptyIterator();
        }
        MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
        assert e != null;
        do {
            if (e.keyHash == keyHash && contentEqualsIgnoreCase(SET_COOKIE, e.getKey())) {
                HttpSetCookie setCookie = HeaderUtils.parseSetCookie(e.value, name, validateCookies);
                if (setCookie != null) {
                    return new SetCookiesByNameIterator(e, setCookie);
                }
            }
            e = e.bucketNext;
        } while (e != null);
        return emptyIterator();
    }

    @Override
    public Iterator<? extends HttpSetCookie> getSetCookiesIterator(final CharSequence name, final CharSequence domain,
                                                                   final CharSequence path) {
        final int keyHash = hashCode(SET_COOKIE);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[index(keyHash)];
        if (bucketHead == null) {
            return emptyIterator();
        }
        MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
        assert e != null;
        do {
            if (e.keyHash == keyHash && contentEqualsIgnoreCase(SET_COOKIE, e.getKey())) {
                // In the future we could attempt to delay full parsing of the cookie until after the domain/path have
                // been matched, but for simplicity just do the parsing ahead of time.
                HttpSetCookie setCookie = HeaderUtils.parseSetCookie(e.value, name, validateCookies);
                if (setCookie != null && domainMatches(domain, setCookie.domain()) &&
                        pathMatches(path, setCookie.path())) {
                    return new SetCookiesByNameDomainPathIterator(e, setCookie, domain, path);
                }
            }
            e = e.bucketNext;
        } while (e != null);
        return emptyIterator();
    }

    @Override
    public HttpHeaders addCookie(final HttpCookiePair cookie) {
        // HTTP/1.x requires that all cookies/crumbs are combined into a single Cookie header.
        // https://tools.ietf.org/html/rfc6265#section-5.4
        CharSequence encoded = cookie.encoded();
        final int keyHash = hashCode(COOKIE);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[index(keyHash)];
        if (bucketHead != null) {
            MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
            assert e != null;
            do {
                if (e.keyHash == keyHash && contentEqualsIgnoreCase(COOKIE, e.getKey())) {
                    e.value = e.value + "; " + encoded;
                    return this;
                }
                e = e.bucketNext;
            } while (e != null);
        }

        put(COOKIE, encoded);
        return this;
    }

    @Override
    public HttpHeaders addSetCookie(final HttpSetCookie cookie) {
        put(SET_COOKIE, cookie.encoded());
        return this;
    }

    @Override
    public boolean removeCookies(final CharSequence name) {
        final int keyHash = hashCode(COOKIE);
        final int bucketIndex = index(keyHash);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[bucketIndex];
        if (bucketHead == null) {
            return false;
        }
        final int beforeSize = size();
        List<CharSequence> cookiesToAdd = null;
        MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
        assert e != null;
        do {
            if (e.keyHash == keyHash && contentEqualsIgnoreCase(COOKIE, e.getKey())) {
                CharSequence newHeaderValue = HeaderUtils.removeCookiePairs(e.value, name);
                if (newHeaderValue != null) {
                    if (newHeaderValue.length() != 0) {
                        if (cookiesToAdd == null) {
                            cookiesToAdd = new ArrayList<>(4);
                        }
                        cookiesToAdd.add(newHeaderValue);
                    }
                    final MultiMapEntry<CharSequence, CharSequence> tmpEntry = e;
                    e = e.bucketNext;
                    removeEntry(bucketHead, tmpEntry, bucketIndex);
                } else {
                    e = e.bucketNext;
                }
            } else {
                e = e.bucketNext;
            }
        } while (e != null);

        if (cookiesToAdd != null) {
            for (CharSequence cookies : cookiesToAdd) {
                add(COOKIE, cookies);
            }
            return true;
        }
        return beforeSize != size();
    }

    @Override
    public boolean removeSetCookies(final CharSequence name) {
        final int keyHash = hashCode(SET_COOKIE);
        final int bucketIndex = index(keyHash);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[bucketIndex];
        if (bucketHead == null) {
            return false;
        }
        int sizeBefore = size();
        MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
        assert e != null;
        do {
            if (e.keyHash == keyHash && contentEqualsIgnoreCase(SET_COOKIE, e.getKey()) &&
                    isSetCookieNameMatches(e.value, name)) {
                final MultiMapEntry<CharSequence, CharSequence> tmpEntry = e;
                e = e.bucketNext;
                removeEntry(bucketHead, tmpEntry, bucketIndex);
            } else {
                e = e.bucketNext;
            }
        } while (e != null);
        return sizeBefore != size();
    }

    @Override
    public boolean removeSetCookies(final CharSequence name, final CharSequence domain, final CharSequence path) {
        final int keyHash = hashCode(SET_COOKIE);
        final int bucketIndex = index(keyHash);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[bucketIndex];
        if (bucketHead == null) {
            return false;
        }
        int sizeBefore = size();
        MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
        assert e != null;
        do {
            if (e.keyHash == keyHash && contentEqualsIgnoreCase(SET_COOKIE, e.getKey())) {
                // In the future we could attempt to delay full parsing of the cookie until after the domain/path have
                // been matched, but for simplicity just do the parsing ahead of time.
                HttpSetCookie setCookie = HeaderUtils.parseSetCookie(e.value, name, false);
                if (setCookie != null && domainMatches(domain, setCookie.domain()) &&
                        pathMatches(path, setCookie.path())) {
                    final MultiMapEntry<CharSequence, CharSequence> tmpEntry = e;
                    e = e.bucketNext;
                    removeEntry(bucketHead, tmpEntry, bucketIndex);
                } else {
                    e = e.bucketNext;
                }
            } else {
                e = e.bucketNext;
            }
        } while (e != null);
        return sizeBefore != size();
    }

    private static final class CookiesIterator extends HeaderUtils.CookiesIterator {
        private final int cookieHeaderNameHash;
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> current;

        CookiesIterator(final int cookieHeaderNameHash, final MultiMapEntry<CharSequence, CharSequence> first) {
            this.cookieHeaderNameHash = cookieHeaderNameHash;
            this.current = first;
            initNext(current.value);
        }

        @Nullable
        @Override
        protected CharSequence cookieHeaderValue() {
            return current == null ? null : current.value;
        }

        @Override
        protected void advanceCookieHeaderValue() {
            assert current != null;
            current = findCookieHeader(cookieHeaderNameHash, current.bucketNext);
        }
    }

    private static final class CookiesByNameIterator extends HeaderUtils.CookiesByNameIterator {
        private final int cookieHeaderNameHash;
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> current;

        CookiesByNameIterator(final int cookieHeaderNameHash, final MultiMapEntry<CharSequence, CharSequence> first,
                              final CharSequence name) {
            super(name);
            this.cookieHeaderNameHash = cookieHeaderNameHash;
            this.current = first;
            initNext(current.value);
        }

        @Nullable
        @Override
        protected CharSequence cookieHeaderValue() {
            return current == null ? null : current.value;
        }

        @Override
        protected void advanceCookieHeaderValue() {
            assert current != null;
            current = findCookieHeader(cookieHeaderNameHash, current.bucketNext);
        }
    }

    @Nullable
    private static MultiMapEntry<CharSequence, CharSequence> findCookieHeader(
            int cookieHeaderNameHash, @Nullable MultiMapEntry<CharSequence, CharSequence> current) {
        while (current != null) {
            if (current.keyHash == cookieHeaderNameHash && contentEqualsIgnoreCase(COOKIE, current.getKey())) {
                return current;
            }
            current = current.bucketNext;
        }
        return null;
    }

    private final class SetCookiesIterator implements Iterator<HttpSetCookie> {
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> current;
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> previous;

        SetCookiesIterator(final MultiMapEntry<CharSequence, CharSequence> first) {
            this.current = first;
        }

        @Override
        public boolean hasNext() {
            return current != null;
        }

        @Override
        public HttpSetCookie next() {
            if (current == null) {
                throw new NoSuchElementException();
            }
            previous = current;
            current = findNext(current.bucketNext);
            return parseSetCookie(previous.value, validateCookies);
        }

        @Override
        public void remove() {
            if (previous == null) {
                throw new IllegalStateException();
            }
            final int i = index(previous.keyHash);
            removeEntry(entries[i], previous, i);
            previous = null;
        }

        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> findNext(
                @Nullable MultiMapEntry<CharSequence, CharSequence> e) {
            assert previous != null;
            while (e != null) {
                if (e.keyHash == previous.keyHash && contentEqualsIgnoreCase(SET_COOKIE, e.getKey())) {
                    return e;
                }
                e = e.bucketNext;
            }
            return null;
        }
    }

    private final class SetCookiesByNameIterator implements Iterator<HttpSetCookie> {
        @Nullable
        private HttpSetCookie next;
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> nextEntry;
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> previous;

        SetCookiesByNameIterator(final MultiMapEntry<CharSequence, CharSequence> first, final HttpSetCookie next) {
            this.next = next;
            this.nextEntry = first;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public HttpSetCookie next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            assert nextEntry != null;
            HttpSetCookie currentCookie = next;
            previous = nextEntry;
            next = null;
            nextEntry = nextEntry.bucketNext;
            while (nextEntry != null) {
                if (nextEntry.keyHash == previous.keyHash &&
                        contentEqualsIgnoreCase(SET_COOKIE, nextEntry.getKey())) {
                    next = HeaderUtils.parseSetCookie(nextEntry.value, currentCookie.name(), validateCookies);
                    if (next != null) {
                        break;
                    }
                }
                nextEntry = nextEntry.bucketNext;
            }

            return currentCookie;
        }

        @Override
        public void remove() {
            if (previous == null) {
                throw new IllegalStateException();
            }
            final int i = index(previous.keyHash);
            removeEntry(entries[i], previous, i);
            previous = null;
        }
    }

    private final class SetCookiesByNameDomainPathIterator implements Iterator<HttpSetCookie> {
        private final CharSequence domain;
        private final CharSequence path;
        @Nullable
        private HttpSetCookie next;
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> nextEntry;
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> previous;

        SetCookiesByNameDomainPathIterator(final MultiMapEntry<CharSequence, CharSequence> first,
                                           final HttpSetCookie next, final CharSequence domain,
                                           final CharSequence path) {
            this.domain = domain;
            this.path = path;
            this.next = next;
            this.nextEntry = first;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public HttpSetCookie next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            assert nextEntry != null;
            HttpSetCookie currentCookie = next;
            previous = nextEntry;
            next = null;
            nextEntry = nextEntry.bucketNext;
            while (nextEntry != null) {
                if (nextEntry.keyHash == previous.keyHash && contentEqualsIgnoreCase(SET_COOKIE, nextEntry.getKey())) {
                    // In the future we could attempt to delay full parsing of the cookie until after the domain/path
                    // have been matched, but for simplicity just do the parsing ahead of time.
                    next = HeaderUtils.parseSetCookie(nextEntry.value, currentCookie.name(), validateCookies);
                    if (next != null && domainMatches(domain, next.domain()) && pathMatches(path, next.path())) {
                        break;
                    }
                }
                nextEntry = nextEntry.bucketNext;
            }

            return currentCookie;
        }

        @Override
        public void remove() {
            if (previous == null) {
                throw new IllegalStateException();
            }
            final int i = index(previous.keyHash);
            removeEntry(entries[i], previous, i);
            previous = null;
        }
    }

    @Override
    protected void validateKey(@Nullable final CharSequence name) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("empty header names are not allowed");
        }
        if (validateNames) {
            validateHeaderName(name);
        }
    }

    /**
     * Validate a <a href="https://tools.ietf.org/html/rfc7230#section-3.2.6">field-name</a> of a header-field.
     *
     * @param name The filed-name to validate.
     */
    private static void validateHeaderName(final CharSequence name) {
        validateCookieTokenAndHeaderName(name);
    }

    @Nullable
    @Override
    public CharSequence get(final CharSequence name) {
        return getValue(name);
    }

    @Nullable
    @Override
    public CharSequence getAndRemove(final CharSequence name) {
        return removeAllAndGetFirst(name);
    }

    @Override
    public Iterator<? extends CharSequence> valuesIterator(final CharSequence name) {
        return getValues(name);
    }

    @Override
    public Set<? extends CharSequence> names() {
        return getKeys();
    }

    @Override
    public HttpHeaders clear() {
        clearAll();
        return this;
    }

    @Override
    public Iterator<Map.Entry<CharSequence, CharSequence>> iterator() {
        return entryIterator();
    }

    @Override
    public HttpHeaders add(final CharSequence name, final CharSequence value) {
        put(name, value);
        return this;
    }

    @Override
    public HttpHeaders add(final CharSequence name, final Iterable<? extends CharSequence> values) {
        putAll(name, values);
        return this;
    }

    @Override
    public HttpHeaders add(final CharSequence name, final CharSequence... values) {
        putAll(name, values);
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public HttpHeaders add(final HttpHeaders headers) {
        if (headers == this) {
            return this;
        }
        if (headers instanceof MultiMap) {
            putAll((MultiMap<? extends CharSequence, ? extends CharSequence>) headers);
        } else { // Slow copy
            for (final Map.Entry<? extends CharSequence, ? extends CharSequence> header : headers) {
                add(header.getKey(), header.getValue());
            }
        }
        return this;
    }

    @Override
    public HttpHeaders set(final CharSequence name, final CharSequence value) {
        putExclusive(name, value);
        return this;
    }

    @Override
    public HttpHeaders set(final CharSequence name, final Iterable<? extends CharSequence> values) {
        putExclusive(name, values);
        return this;
    }

    @Override
    public HttpHeaders set(final CharSequence name, final CharSequence... values) {
        putExclusive(name, values);
        return this;
    }

    @Override
    public boolean remove(final CharSequence name) {
        return removeAll(name);
    }

    @Override
    public boolean remove(final CharSequence name, final CharSequence value) {
        return remove(name, value, true);
    }

    @Override
    public boolean removeIgnoreCase(final CharSequence name, final CharSequence value) {
        return remove(name, value, false);
    }

    private boolean remove(final CharSequence name, final CharSequence value, final boolean caseSensitive) {
        final int nameHash = hashCode(name);
        final int bucketIndex = index(nameHash);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[bucketIndex];
        if (bucketHead == null) {
            return false;
        }
        final int sizeBefore = size();
        MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
        assert e != null;
        do {
            if (e.keyHash == nameHash && equals(name, e.getKey()) &&
                    (caseSensitive ? contentEquals(value, e.value) : contentEqualsIgnoreCase(value, e.value))) {
                final MultiMapEntry<CharSequence, CharSequence> tmpEntry = e;
                e = e.bucketNext;
                removeEntry(bucketHead, tmpEntry, bucketIndex);
            } else {
                e = e.bucketNext;
            }
        } while (e != null);
        return sizeBefore != size();
    }

    @Override
    public String toString() {
        return toString(DEFAULT_HEADER_FILTER);
    }

    @Override
    protected int hashCode(final CharSequence name) {
        return caseInsensitiveHashCode(name);
    }

    @Override
    protected boolean equals(final CharSequence name1, final CharSequence name2) {
        return contentEqualsIgnoreCase(name1, name2);
    }

    @Override
    protected boolean isKeyEqualityCompatible(final MultiMap<? extends CharSequence, ? extends CharSequence> multiMap) {
        return multiMap.getClass().equals(getClass());
    }

    @Override
    protected int hashCodeForValue(final CharSequence value) {
        return value.hashCode();
    }

    @Override
    protected boolean equalsForValue(final CharSequence value1, final CharSequence value2) {
        return contentEquals(value1, value2);
    }
}
