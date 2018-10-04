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

    private DefaultHttpHeaders(final DefaultHttpHeaders rhs) {
        super(rhs);
        this.validateNames = rhs.validateNames;
        this.validateCookies = rhs.validateCookies;
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
        encodeAndAddCookie(COOKIE, cookie);
        return this;
    }

    @Override
    public HttpHeaders addSetCookie(final HttpCookie cookie) {
        encodeAndAddCookie(SET_COOKIE, cookie);
        return this;
    }

    @Override
    public boolean removeCookies(final CharSequence name) {
        return removeCookies(COOKIE, name);
    }

    @Override
    public boolean removeSetCookies(final CharSequence name) {
        return removeCookies(SET_COOKIE, name);
    }

    @Override
    public boolean removeCookies(final CharSequence name, final CharSequence domain, final CharSequence path) {
        return removeCookies(COOKIE, name, domain, path);
    }

    @Override
    public boolean removeSetCookies(final CharSequence name, final CharSequence domain, final CharSequence path) {
        return removeCookies(SET_COOKIE, name, domain, path);
    }

    private static boolean entryMatchesCookieName(MultiMapEntry<CharSequence, CharSequence> e, int cookieHeaderNameHash,
                                                  CharSequence cookieHeaderName, CharSequence cookieName) {
        return e.keyHash == cookieHeaderNameHash && contentEqualsIgnoreCase(cookieHeaderName, e.getKey()) &&
                // Check if the name of the cookie matches before doing a full parse of the cookie.
                regionMatches(cookieName, true, 0, e.value, 0, cookieName.length());
    }

    private void encodeAndAddCookie(CharSequence cookieHeaderName, HttpCookie cookie) {
        StringBuilder sb = new StringBuilder(64);
        sb.append(cookie.name()).append('=');
        if (cookie.wrapped()) {
            sb.append('"').append(cookie.value()).append('"');
        } else {
            sb.append(cookie.value());
        }
        if (cookie.domain() != null) {
            sb.append("; domain=");
            sb.append(cookie.domain());
        }
        if (cookie.path() != null) {
            sb.append("; path=");
            sb.append(cookie.path());
        }
        if (cookie.expires() != null) {
            sb.append("; expires=");
            sb.append(cookie.expires());
        }
        if (cookie.maxAge() != null) {
            sb.append("; max-age=");
            sb.append(cookie.maxAge());
        }
        if (cookie.httpOnly()) {
            sb.append("; httponly");
        }
        if (cookie.secure()) {
            sb.append("; secure");
        }
        // We could return sb.toString() but for now we avoid the intermediate copy operation until we can demonstrate
        // that going to String will be beneficial (e.g. intrinsics?).
        put(cookieHeaderName, sb);
    }

    private boolean removeCookies(CharSequence cookieHeaderName, CharSequence cookieName) {
        final int keyHash = hashCode(cookieHeaderName);
        final int bucketIndex = index(keyHash);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[bucketIndex];
        if (bucketHead == null) {
            return false;
        }
        int sizeBefore = size();
        MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
        assert e != null;
        do {
            if (entryMatchesCookieName(e, keyHash, cookieHeaderName, cookieName)) {
                final MultiMapEntry<CharSequence, CharSequence> tmpEntry = e;
                e = e.bucketNext;
                removeEntry(bucketHead, tmpEntry, bucketIndex);
            } else {
                e = e.bucketNext;
            }
        } while (e != null);
        return sizeBefore != size();
    }

    private boolean removeCookies(final CharSequence cookieHeaderName, final CharSequence name,
                                  final CharSequence domain, final CharSequence path) {
        final int keyHash = hashCode(cookieHeaderName);
        final int bucketIndex = index(keyHash);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[bucketIndex];
        if (bucketHead == null) {
            return false;
        }
        int sizeBefore = size();
        MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
        assert e != null;
        do {
            if (entryMatchesCookieName(e, keyHash, cookieHeaderName, name)) {
                // In the future we could attempt to delay full parsing of the cookie until after the domain/path have
                // been matched, but for simplicity just do the parsing ahead of time.
                HttpCookie cookie = parseCookie(e.value, false);
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

    @Nullable
    private HttpCookie getAndParseCookie(CharSequence cookieHeaderName, CharSequence cookieName, boolean validate) {
        final int keyHash = hashCode(cookieHeaderName);
        final int i = index(keyHash);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[i];
        if (bucketHead != null) {
            MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
            assert e != null;
            do {
                if (entryMatchesCookieName(e, keyHash, cookieHeaderName, cookieName)) {
                    return parseCookie(e.value, validate);
                }
                e = e.bucketNext;
            } while (e != null);
        }
        return null;
    }

    private Iterator<? extends HttpCookie> getAndParseCookies(final CharSequence cookieHeaderName,
                                                              final CharSequence name, final CharSequence domain,
                                                              final CharSequence path, final boolean validateCookies) {
        final int keyHash = hashCode(cookieHeaderName);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[index(keyHash)];
        if (bucketHead == null) {
            return emptyIterator();
        }
        MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
        assert e != null;
        do {
            if (entryMatchesCookieName(e, keyHash, cookieHeaderName, name)) {
                // In the future we could attempt to delay full parsing of the cookie until after the domain/path have
                // been matched, but for simplicity just do the parsing ahead of time.
                HttpCookie cookie = parseCookie(e.value, validateCookies);
                if (domainMatches(domain, cookie.domain()) && pathMatches(path, cookie.path())) {
                    return new CookiesByNameDomainPathIterator(keyHash, cookieHeaderName,
                            validateCookies, name, domain, path, cookie, e);
                }
            }
            e = e.bucketNext;
        } while (e != null);
        return emptyIterator();
    }

    private Iterator<? extends HttpCookie> getAndParseCookies(CharSequence cookieHeaderName,
                                                              boolean validate) {
        final int keyHash = hashCode(cookieHeaderName);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[index(keyHash)];
        if (bucketHead == null) {
            return emptyIterator();
        }
        MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
        assert e != null;
        do {
            if (e.keyHash == keyHash && contentEqualsIgnoreCase(cookieHeaderName, e.getKey())) {
                return new CookiesIterator(keyHash, cookieHeaderName, validate, e);
            }
            e = e.bucketNext;
        } while (e != null);
        return emptyIterator();
    }

    private Iterator<? extends HttpCookie> getAndParseCookies(CharSequence cookieHeaderName,
                                                              CharSequence cookieName,
                                                              boolean validate) {
        final int keyHash = hashCode(cookieHeaderName);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[index(keyHash)];
        if (bucketHead == null) {
            return emptyIterator();
        }
        MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
        assert e != null;
        do {
            if (entryMatchesCookieName(e, keyHash, cookieHeaderName, cookieName)) {
                return new CookiesByNameIterator(keyHash, cookieHeaderName, validate, cookieName, e);
            }
            e = e.bucketNext;
        } while (e != null);
        return emptyIterator();
    }

    private final class CookiesIterator implements Iterator<HttpCookie> {
        private final int cookieHeaderNameHash;
        private final boolean validateCookies;
        private final CharSequence cookieHeaderName;
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> current;
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> previous;

        CookiesIterator(final int cookieHeaderNameHash, final CharSequence cookieHeaderName,
                        final boolean validateCookies, final MultiMapEntry<CharSequence, CharSequence> first) {
            this.cookieHeaderNameHash = cookieHeaderNameHash;
            this.validateCookies = validateCookies;
            this.cookieHeaderName = cookieHeaderName;
            this.current = first;
        }

        @Override
        public boolean hasNext() {
            return current != null;
        }

        @Override
        public HttpCookie next() {
            if (current == null) {
                throw new NoSuchElementException();
            }
            previous = current;
            current = findNext(current.bucketNext);
            return parseCookie(previous.value, validateCookies);
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
                if (e.keyHash == cookieHeaderNameHash && contentEqualsIgnoreCase(cookieHeaderName, e.getKey())) {
                    return e;
                }
                e = e.bucketNext;
            }
            return null;
        }
    }

    private final class CookiesByNameIterator implements Iterator<HttpCookie> {
        private final int cookieHeaderNameHash;
        private final boolean validateCookies;
        private final CharSequence cookieHeaderName;
        private final CharSequence cookieName;
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> current;
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> previous;

        CookiesByNameIterator(final int cookieHeaderNameHash, final CharSequence cookieHeaderName,
                              final boolean validateCookies, final CharSequence cookieName,
                              final MultiMapEntry<CharSequence, CharSequence> first) {
            this.cookieHeaderNameHash = cookieHeaderNameHash;
            this.validateCookies = validateCookies;
            this.cookieHeaderName = cookieHeaderName;
            this.cookieName = cookieName;
            this.current = first;
        }

        @Override
        public boolean hasNext() {
            return current != null;
        }

        @Override
        public HttpCookie next() {
            if (current == null) {
                throw new NoSuchElementException();
            }
            previous = current;
            current = findNext(current.bucketNext);
            return parseCookie(previous.value, validateCookies);
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
                if (entryMatchesCookieName(entry, cookieHeaderNameHash, cookieHeaderName, cookieName)) {
                    return entry;
                }
                entry = entry.bucketNext;
            }
            return null;
        }
    }

    private final class CookiesByNameDomainPathIterator implements Iterator<HttpCookie> {
        private final int cookieHeaderNameHash;
        private final boolean validateCookies;
        private final CharSequence cookieHeaderName;
        private final CharSequence cookieName;
        private final CharSequence cookieDomain;
        private final CharSequence cookiePath;
        @Nullable
        private HttpCookie cookie;
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> current;
        @Nullable
        private MultiMapEntry<CharSequence, CharSequence> previous;

        CookiesByNameDomainPathIterator(final int cookieHeaderNameHash, final CharSequence cookieHeaderName,
                                        final boolean validateCookies, final CharSequence cookieName,
                                        final CharSequence cookieDomain, final CharSequence cookiePath,
                                        final HttpCookie cookie,
                                        final MultiMapEntry<CharSequence, CharSequence> first) {
            this.cookieHeaderNameHash = cookieHeaderNameHash;
            this.validateCookies = validateCookies;
            this.cookieHeaderName = cookieHeaderName;
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
        public HttpCookie next() {
            if (current == null) {
                throw new NoSuchElementException();
            }
            assert cookie != null;
            HttpCookie next = cookie;
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
                if (entryMatchesCookieName(e, cookieHeaderNameHash, cookieHeaderName, cookieName)) {
                    // In the future we could attempt to delay full parsing of the cookie until after the domain/path
                    // have been matched, but for simplicity just do the parsing ahead of time.
                    HttpCookie tmp = parseCookie(e.value, validateCookies);
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
            throw new IllegalArgumentException("can't add to itself");
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
        return remove(name, value, false);
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
    public HttpHeaders copy() {
        return new DefaultHttpHeaders(this);
    }

    @Override
    public String toString() {
        return toString(DEFAULT_HEADER_FILTER);
    }

    @Override
    public String toString(final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> filter) {
        return HeaderUtils.toString(this, filter);
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
