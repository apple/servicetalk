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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
    MultiMapEntry<CharSequence, CharSequence> newEntry(final CharSequence key,
                                                       final CharSequence value, final int keyHash) {
        return new MultiMapEntry<CharSequence, CharSequence>(value, keyHash) {
            @Override
            public CharSequence getKey() {
                return key;
            }
        };
    }

    @Override
    public boolean contains(final CharSequence name, final CharSequence value, final boolean caseInsensitive) {
        return caseInsensitive ? contains(name, value, CharSequences::contentEqualsIgnoreCase) : contains(name, value);
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
                int start = 0;
                for (;;) {
                    int i = indexOf(e.value, ';', start);
                    // Check if the name of the cookie matches before doing a full parse of the cookie.
                    if (regionMatches(name, true, 0, e.value, start, name.length())) {
                        return parseCookiePair(e.value, start, name.length(), i);
                    } else if (i < 0 || e.value.length() - 2 <= i) {
                        break;
                    }
                    // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
                    start = i + 2;
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
                if (setCookieEntryMatches(e, keyHash, name)) {
                    return parseSetCookie(e.value, validateCookies);
                }
                e = e.bucketNext;
            } while (e != null);
        }
        return null;
    }

    @Override
    public Iterator<? extends HttpCookiePair> getCookies() {
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
    public Iterator<? extends HttpCookiePair> getCookies(final CharSequence name) {
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
    public Iterator<? extends HttpSetCookie> getSetCookies() {
        final int keyHash = hashCode(SET_COOKIE);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[index(keyHash)];
        if (bucketHead == null) {
            return emptyIterator();
        }
        MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
        assert e != null;
        do {
            if (e.keyHash == keyHash && contentEqualsIgnoreCase(SET_COOKIE, e.getKey())) {
                return new SetCookiesIterator(keyHash, e);
            }
            e = e.bucketNext;
        } while (e != null);
        return emptyIterator();
    }

    @Override
    public Iterator<? extends HttpSetCookie> getSetCookies(final CharSequence name) {
        final int keyHash = hashCode(SET_COOKIE);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[index(keyHash)];
        if (bucketHead == null) {
            return emptyIterator();
        }
        MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
        assert e != null;
        do {
            if (setCookieEntryMatches(e, keyHash, name)) {
                return new SetCookiesByNameIterator(keyHash, name, e);
            }
            e = e.bucketNext;
        } while (e != null);
        return emptyIterator();
    }

    @Override
    public Iterator<? extends HttpSetCookie> getSetCookies(final CharSequence name, final CharSequence domain,
                                                           final CharSequence path) {
        final int keyHash = hashCode(SET_COOKIE);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[index(keyHash)];
        if (bucketHead == null) {
            return emptyIterator();
        }
        MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
        assert e != null;
        do {
            if (setCookieEntryMatches(e, keyHash, name)) {
                // In the future we could attempt to delay full parsing of the cookie until after the domain/path have
                // been matched, but for simplicity just do the parsing ahead of time.
                HttpSetCookie cookie = parseSetCookie(e.value, validateCookies);
                if (domainMatches(domain, cookie.domain()) && pathMatches(path, cookie.path())) {
                    return new SetCookiesByNameDomainPathIterator(keyHash, name, domain, path, cookie, e);
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
                CharSequence headerValue = e.value;
                int start = 0;
                int beginCopyIndex = 0;
                StringBuilder sb = null;

                for (;;) {
                    int end = indexOf(headerValue, ';', start);
                    if (regionMatches(name, true, 0, headerValue, start, name.length())) {
                        if (beginCopyIndex != start) {
                            if (sb == null) {
                                sb = new StringBuilder(headerValue.length() - beginCopyIndex);
                            } else {
                                sb.append("; ");
                            }
                            sb.append(headerValue.subSequence(beginCopyIndex, start - 2));
                        }

                        if (end < 0 || headerValue.length() - 2 <= end) {
                            // start is used after the loop to know if a match was found and therefore the entire header
                            // may need removal.
                            start = headerValue.length();
                            break;
                        }
                        // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
                        start = end + 2;
                        beginCopyIndex = start;
                    } else if (end > 0) {
                        if (headerValue.length() - 2 <= end) {
                            throw new IllegalArgumentException("cookie is not allowed to end with ;");
                        }
                        // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
                        start = end + 2;
                    } else {
                        if (beginCopyIndex != 0) {
                            if (sb == null) {
                                sb = new StringBuilder(headerValue.length() - beginCopyIndex);
                            } else {
                                sb.append("; ");
                            }
                            sb.append(headerValue.subSequence(beginCopyIndex, headerValue.length()));
                        }
                        break;
                    }
                }
                if (sb != null) {
                    if (cookiesToAdd == null) {
                        cookiesToAdd = new ArrayList<>(4);
                    }
                    cookiesToAdd.add(sb.toString());
                    final MultiMapEntry<CharSequence, CharSequence> tmpEntry = e;
                    e = e.bucketNext;
                    removeEntry(bucketHead, tmpEntry, bucketIndex);
                } else if (start == headerValue.length()) {
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
            if (setCookieEntryMatches(e, keyHash, name)) {
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
            if (setCookieEntryMatches(e, keyHash, name)) {
                // In the future we could attempt to delay full parsing of the cookie until after the domain/path have
                // been matched, but for simplicity just do the parsing ahead of time.
                HttpSetCookie cookie = parseSetCookie(e.value, false);
                if (domainMatches(domain, cookie.domain()) && pathMatches(path, cookie.path())) {
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

    private static boolean setCookieEntryMatches(MultiMapEntry<CharSequence, CharSequence> e, int cookieHeaderNameHash,
                                                 CharSequence cookieName) {
        return e.keyHash == cookieHeaderNameHash && contentEqualsIgnoreCase(SET_COOKIE, e.getKey()) &&
                // Check if the name of the cookie matches before doing a full parse of the cookie.
                regionMatches(cookieName, true, 0, e.value, 0, cookieName.length());
    }

    private static final class CookiesIterator implements Iterator<HttpCookiePair> {
        private final int cookieHeaderNameHash;
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> current;
        @Nullable
        private HttpCookiePair next;
        private int nextNextStart;

        CookiesIterator(final int cookieHeaderNameHash, final MultiMapEntry<CharSequence, CharSequence> first) {
            this.cookieHeaderNameHash = cookieHeaderNameHash;
            this.current = first;
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
            if (current == null) {
                next = null;
            } else {
                findNext0();
            }
        }

        private void findNext0() {
            assert current != null;
            int end = indexOf(current.value, ';', nextNextStart);
            next = parseCookiePair(current.value, nextNextStart, end);
            if (end > 0) {
                if (current.value.length() - 2 <= end) {
                    current = findNextCookieHeader(current.bucketNext);
                    nextNextStart = 0;
                } else {
                    // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
                    nextNextStart = end + 2;
                }
            } else {
                current = findNextCookieHeader(current.bucketNext);
                nextNextStart = 0;
            }
        }

        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> findNextCookieHeader(
                @Nullable MultiMapEntry<CharSequence, CharSequence> e) {
            while (e != null) {
                if (e.keyHash == cookieHeaderNameHash && contentEqualsIgnoreCase(COOKIE, e.getKey())) {
                    return e;
                }
                e = e.bucketNext;
            }
            return null;
        }
    }

    private static final class CookiesByNameIterator implements Iterator<HttpCookiePair> {
        private final int cookieHeaderNameHash;
        private final CharSequence name;
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> current;
        @Nullable
        private HttpCookiePair next;
        private int nextNextStart;

        CookiesByNameIterator(final int cookieHeaderNameHash, final MultiMapEntry<CharSequence, CharSequence> first,
                              final CharSequence name) {
            this.cookieHeaderNameHash = cookieHeaderNameHash;
            this.current = first;
            this.name = name;
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
            if (current == null) {
                next = null;
            } else {
                findNext0();
            }
        }

        private void findNext0() {
            assert current != null;
            for (;;) {
                int end = indexOf(current.value, ';', nextNextStart);
                if (regionMatches(name, true, 0, current.value, nextNextStart, name.length())) {
                    next = parseCookiePair(current.value, nextNextStart, name.length(), end);
                    if (end > 0) {
                        // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
                        if (current.value.length() - 2 <= end) {
                            current = findNextCookieHeader(current.bucketNext);
                            nextNextStart = 0;
                        } else {
                            // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
                            nextNextStart = end + 2;
                        }
                    } else {
                        current = findNextCookieHeader(current.bucketNext);
                        nextNextStart = 0;
                    }
                    break;
                } else if (end > 0) {
                    if (current.value.length() - 2 <= end) {
                        throw new IllegalArgumentException("cookie is not allowed to end with ;");
                    }
                    // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
                    nextNextStart = end + 2;
                } else {
                    current = findNextCookieHeader(current.bucketNext);
                    if (current == null) {
                        break;
                    }
                    nextNextStart = 0;
                }
            }
        }

        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> findNextCookieHeader(
                @Nullable MultiMapEntry<CharSequence, CharSequence> e) {
            while (e != null) {
                if (e.keyHash == cookieHeaderNameHash && contentEqualsIgnoreCase(COOKIE, e.getKey())) {
                    return e;
                }
                e = e.bucketNext;
            }
            return null;
        }
    }

    private final class SetCookiesIterator implements Iterator<HttpSetCookie> {
        private final int cookieHeaderNameHash;
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> current;
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> previous;

        SetCookiesIterator(final int cookieHeaderNameHash, final MultiMapEntry<CharSequence, CharSequence> first) {
            this.cookieHeaderNameHash = cookieHeaderNameHash;
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
            final int i = index(cookieHeaderNameHash);
            removeEntry(entries[i], previous, i);
            previous = null;
        }

        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> findNext(
                @Nullable MultiMapEntry<CharSequence, CharSequence> e) {
            while (e != null) {
                if (e.keyHash == cookieHeaderNameHash && contentEqualsIgnoreCase(SET_COOKIE, e.getKey())) {
                    return e;
                }
                e = e.bucketNext;
            }
            return null;
        }
    }

    private final class SetCookiesByNameIterator implements Iterator<HttpSetCookie> {
        private final int cookieHeaderNameHash;
        private final CharSequence cookieName;
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> current;
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> previous;

        SetCookiesByNameIterator(final int cookieHeaderNameHash, final CharSequence cookieName,
                                 final MultiMapEntry<CharSequence, CharSequence> first) {
            this.cookieHeaderNameHash = cookieHeaderNameHash;
            this.cookieName = cookieName;
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
            final int i = index(cookieHeaderNameHash);
            removeEntry(entries[i], previous, i);
            previous = null;
        }

        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> findNext(
                @Nullable MultiMapEntry<CharSequence, CharSequence> entry) {
            while (entry != null) {
                if (setCookieEntryMatches(entry, cookieHeaderNameHash, cookieName)) {
                    return entry;
                }
                entry = entry.bucketNext;
            }
            return null;
        }
    }

    private final class SetCookiesByNameDomainPathIterator implements Iterator<HttpSetCookie> {
        private final int cookieHeaderNameHash;
        private final CharSequence cookieName;
        private final CharSequence cookieDomain;
        private final CharSequence cookiePath;
        @Nullable
        private HttpSetCookie cookie;
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> current;
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> previous;

        SetCookiesByNameDomainPathIterator(final int cookieHeaderNameHash, final CharSequence cookieName,
                                           final CharSequence cookieDomain, final CharSequence cookiePath,
                                           final HttpSetCookie cookie,
                                           final MultiMapEntry<CharSequence, CharSequence> first) {
            this.cookieHeaderNameHash = cookieHeaderNameHash;
            this.cookieName = cookieName;
            this.cookieDomain = cookieDomain;
            this.cookiePath = cookiePath;
            this.cookie = cookie;
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
            assert cookie != null;
            HttpSetCookie next = cookie;
            previous = current;
            cookie = null;
            current = findNext(current.bucketNext);
            return next;
        }

        @Override
        public void remove() {
            if (previous == null) {
                throw new IllegalStateException();
            }
            final int i = index(cookieHeaderNameHash);
            removeEntry(entries[i], previous, i);
            previous = null;
        }

        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> findNext(
                @Nullable MultiMapEntry<CharSequence, CharSequence> e) {
            while (e != null) {
                if (setCookieEntryMatches(e, cookieHeaderNameHash, cookieName)) {
                    // In the future we could attempt to delay full parsing of the cookie until after the domain/path
                    // have been matched, but for simplicity just do the parsing ahead of time.
                    HttpSetCookie tmp = parseSetCookie(e.value, validateCookies);
                    if (domainMatches(cookieDomain, tmp.domain()) && pathMatches(cookiePath, tmp.path())) {
                        cookie = tmp;
                        return e;
                    }
                }
                e = e.bucketNext;
            }
            return null;
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
    public Iterator<? extends CharSequence> values(final CharSequence name) {
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
    public boolean remove(final CharSequence name, final CharSequence value, final boolean caseInsensitive) {
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
                    (caseInsensitive ? contentEqualsIgnoreCase(value, e.value) : contentEquals(value, e.value))) {
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
