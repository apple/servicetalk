/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HttpCookiePair;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpSetCookie;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.Http2Headers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.annotation.Nullable;

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

final class NettyH2HeadersToHttpHeaders implements HttpHeaders {
    private final boolean validateCookies;
    private final Http2Headers nettyHeaders;

    NettyH2HeadersToHttpHeaders(final Http2Headers nettyHeaders, final boolean validateCookies) {
        this.nettyHeaders = nettyHeaders;
        this.validateCookies = validateCookies;
    }

    Http2Headers nettyHeaders() {
        return nettyHeaders;
    }

    @Nullable
    @Override
    public CharSequence get(final CharSequence name) {
        return nettyHeaders.get(name);
    }

    @Nullable
    @Override
    public CharSequence getAndRemove(final CharSequence name) {
        return nettyHeaders.getAndRemove(name);
    }

    @Override
    public Iterator<? extends CharSequence> values(final CharSequence name) {
        return nettyHeaders.valueIterator(name);
    }

    @Override
    public boolean contains(final CharSequence name, final CharSequence value, final boolean caseInsensitive) {
        return nettyHeaders.contains(name, value, caseInsensitive);
    }

    @Override
    public int size() {
        return nettyHeaders.size();
    }

    @Override
    public boolean isEmpty() {
        return nettyHeaders.isEmpty();
    }

    @Override
    public Set<? extends CharSequence> names() {
        return nettyHeaders.names();
    }

    @Override
    public HttpHeaders add(final CharSequence name, final CharSequence value) {
        nettyHeaders.add(name, value);
        return this;
    }

    @Override
    public HttpHeaders add(final CharSequence name, final Iterable<? extends CharSequence> values) {
        nettyHeaders.add(name, values);
        return this;
    }

    @Override
    public HttpHeaders add(final CharSequence name, final CharSequence... values) {
        nettyHeaders.add(name, values);
        return this;
    }

    @Override
    public HttpHeaders add(final HttpHeaders headers) {
        for (Entry<CharSequence, CharSequence> entry : headers) {
            nettyHeaders.add(entry.getKey(), entry.getValue());
        }
        return this;
    }

    @Override
    public HttpHeaders set(final CharSequence name, final CharSequence value) {
        nettyHeaders.set(name, value);
        return this;
    }

    @Override
    public HttpHeaders set(final CharSequence name, final Iterable<? extends CharSequence> values) {
        nettyHeaders.set(name, values);
        return this;
    }

    @Override
    public HttpHeaders set(final CharSequence name, final CharSequence... values) {
        nettyHeaders.set(name, values);
        return this;
    }

    @Override
    public boolean remove(final CharSequence name) {
        return nettyHeaders.remove(name);
    }

    @Override
    public boolean remove(final CharSequence name, final CharSequence value, final boolean caseInsensitive) {
        final int sizeBefore = size();
        if (caseInsensitive) {
            Iterator<? extends CharSequence> valuesItr = nettyHeaders.valueIterator(name);
            while (valuesItr.hasNext()) {
                CharSequence next = valuesItr.next();
                if (contentEquals(next, value)) {
                    valuesItr.remove();
                }
            }
        } else {
            Iterator<? extends CharSequence> valuesItr = nettyHeaders.valueIterator(name);
            while (valuesItr.hasNext()) {
                CharSequence next = valuesItr.next();
                if (contentEqualsIgnoreCase(next, value)) {
                    valuesItr.remove();
                }
            }
        }
        return sizeBefore != size();
    }

    @Override
    public HttpHeaders clear() {
        nettyHeaders.clear();
        return this;
    }

    @Override
    public Iterator<Entry<CharSequence, CharSequence>> iterator() {
        return nettyHeaders.iterator();
    }

    @Override
    public String toString() {
        return toString(DEFAULT_HEADER_FILTER);
    }

    @Nullable
    @Override
    public HttpCookiePair getCookie(final CharSequence name) {
        Iterator<CharSequence> valueItr = nettyHeaders.valueIterator(HttpHeaderNames.COOKIE);
        while (valueItr.hasNext()) {
            CharSequence value = valueItr.next();
            int start = 0;
            for (;;) {
                int i = indexOf(value, ';', start);
                // Check if the name of the cookie matches before doing a full parse of the cookie.
                if (regionMatches(name, true, 0, value, start, name.length())) {
                    return parseCookiePair(value, start, name.length(), i);
                } else if (i < 0 || value.length() - 2 <= i) {
                    break;
                }
                // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
                start = i + 2;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public HttpSetCookie getSetCookie(final CharSequence name) {
        Iterator<CharSequence> valueItr = nettyHeaders.valueIterator(HttpHeaderNames.SET_COOKIE);
        while (valueItr.hasNext()) {
            CharSequence value = valueItr.next();
            // Check if the name of the cookie matches before doing a full parse of the cookie.
            if (regionMatches(name, true, 0, value, 0, name.length())) {
                return parseSetCookie(value, validateCookies);
            }
        }
        return null;
    }

    @Override
    public Iterator<? extends HttpCookiePair> getCookies() {
        Iterator<CharSequence> valueItr = nettyHeaders.valueIterator(HttpHeaderNames.COOKIE);
        return valueItr.hasNext() ? new CookiesIterator(valueItr) : emptyIterator();
    }

    @Override
    public Iterator<? extends HttpCookiePair> getCookies(final CharSequence name) {
        return new CookiesByNameIterator(nettyHeaders.valueIterator(HttpHeaderNames.COOKIE), name);
    }

    @Override
    public Iterator<? extends HttpSetCookie> getSetCookies() {
        Iterator<CharSequence> valueItr = nettyHeaders.valueIterator(HttpHeaderNames.SET_COOKIE);
        return valueItr.hasNext() ? new SetCookiesIterator(valueItr) : emptyIterator();
    }

    @Override
    public Iterator<? extends HttpSetCookie> getSetCookies(final CharSequence name) {
        Iterator<CharSequence> valueItr = nettyHeaders.valueIterator(HttpHeaderNames.SET_COOKIE);
        while (valueItr.hasNext()) {
            CharSequence value = valueItr.next();
            if (regionMatches(name, true, 0, value, 0, name.length())) {
                return new SetCookiesByNameIterator(valueItr, name, value);
            }
        }
        return emptyIterator();
    }

    @Override
    public Iterator<? extends HttpSetCookie> getSetCookies(final CharSequence name, final CharSequence domain,
                                                           final CharSequence path) {
        Iterator<CharSequence> valueItr = nettyHeaders.valueIterator(HttpHeaderNames.SET_COOKIE);
        while (valueItr.hasNext()) {
            CharSequence value = valueItr.next();
            if (regionMatches(name, true, 0, value, 0, name.length())) {
                // In the future we could attempt to delay full parsing of the cookie until after the domain/path have
                // been matched, but for simplicity just do the parsing ahead of time.
                HttpSetCookie cookie = parseSetCookie(value, validateCookies);
                if (domainMatches(domain, cookie.domain()) && pathMatches(path, cookie.path())) {
                    return new SetCookiesByNameDomainPathIterator(valueItr, name, domain, path, cookie);
                }
            }
        }
        return emptyIterator();
    }

    @Override
    public HttpHeaders addCookie(final HttpCookiePair cookie) {
        add(COOKIE, cookie.encoded());
        return this;
    }

    @Override
    public HttpHeaders addSetCookie(final HttpSetCookie cookie) {
        add(SET_COOKIE, cookie.encoded());
        return this;
    }

    @Override
    public boolean removeCookies(final CharSequence name) {
        Iterator<? extends CharSequence> valuesItr = nettyHeaders.valueIterator(HttpHeaderNames.COOKIE);
        List<CharSequence> cookiesToAdd = null;
        final int sizeBefore = size();
        while (valuesItr.hasNext()) {
            CharSequence headerValue = valuesItr.next();
            int start = 0;
            int beginCopyIndex = 0;
            StringBuilder sb = null;

            for (;;) {
                int end = HeaderUtils.indexOf(headerValue, ';', start);
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
                valuesItr.remove();
            } else if (start == headerValue.length()) {
                valuesItr.remove();
            }
        }
        if (cookiesToAdd != null) {
            for (CharSequence cookies : cookiesToAdd) {
                nettyHeaders.add(HttpHeaderNames.COOKIE, cookies);
            }
            return true;
        }
        return sizeBefore != size();
    }

    @Override
    public boolean removeSetCookies(final CharSequence name) {
        final int sizeBefore = size();
        Iterator<? extends CharSequence> valuesItr = nettyHeaders.valueIterator(HttpHeaderNames.SET_COOKIE);
        while (valuesItr.hasNext()) {
            CharSequence next = valuesItr.next();
            if (regionMatches(name, true, 0, next, 0, name.length())) {
                valuesItr.remove();
            }
        }
        return sizeBefore != size();
    }

    @Override
    public boolean removeSetCookies(final CharSequence name, final CharSequence domain, final CharSequence path) {
        final int sizeBefore = size();
        Iterator<? extends CharSequence> valuesItr = nettyHeaders.valueIterator(HttpHeaderNames.SET_COOKIE);
        while (valuesItr.hasNext()) {
            CharSequence next = valuesItr.next();
            if (regionMatches(name, true, 0, next, 0, name.length())) {
                // In the future we could attempt to delay full parsing of the cookie until after the domain/path have
                // been matched, but for simplicity just do the parsing ahead of time.
                HttpSetCookie cookie = parseSetCookie(next, false);
                if (domainMatches(domain, cookie.domain()) && pathMatches(path, cookie.path())) {
                    valuesItr.remove();
                }
            }
        }
        return sizeBefore != size();
    }

    private static final class CookiesIterator implements Iterator<HttpCookiePair> {
        private final Iterator<CharSequence> valueItr;
        @Nullable
        private CharSequence headerValue;
        @Nullable
        private HttpCookiePair next;
        private int nextNextStart;

        CookiesIterator(final Iterator<CharSequence> valueItr) {
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
            int end = HeaderUtils.indexOf(headerValue, ';', nextNextStart);
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

    private static final class CookiesByNameIterator implements Iterator<HttpCookiePair> {
        private final Iterator<CharSequence> valueItr;
        private final CharSequence name;
        @Nullable
        private CharSequence headerValue;
        @Nullable
        private HttpCookiePair next;
        private int nextNextStart;

        CookiesByNameIterator(final Iterator<CharSequence> valueItr, final CharSequence name) {
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
                int end = HeaderUtils.indexOf(headerValue, ';', nextNextStart);
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

    private final class SetCookiesIterator implements Iterator<HttpSetCookie> {
        private final Iterator<CharSequence> valueItr;

         SetCookiesIterator(final Iterator<CharSequence> valueItr) {
            this.valueItr = valueItr;
        }

        @Override
        public boolean hasNext() {
            return valueItr.hasNext();
        }

        @Override
        public HttpSetCookie next() {
            return parseSetCookie(valueItr.next(), validateCookies);
        }

        @Override
        public void remove() {
            valueItr.remove();
        }
    }

    private final class SetCookiesByNameIterator implements Iterator<HttpSetCookie> {
        private final Iterator<CharSequence> valueItr;
        private final CharSequence cookieName;
        @Nullable
        private CharSequence nextValue;

        SetCookiesByNameIterator(final Iterator<CharSequence> valueItr, final CharSequence cookieName,
                                 final CharSequence nextValue) {
            this.valueItr = valueItr;
            this.cookieName = cookieName;
            this.nextValue = nextValue;
        }

        @Override
        public boolean hasNext() {
            return nextValue != null;
        }

        @Override
        public HttpSetCookie next() {
            if (nextValue == null) {
                throw new NoSuchElementException();
            }
            HttpSetCookie currCookie = parseSetCookie(nextValue, validateCookies);
            nextValue = null;
            while (valueItr.hasNext()) {
                CharSequence value = valueItr.next();
                if (regionMatches(cookieName, true, 0, value, 0, cookieName.length())) {
                    nextValue = value;
                    break;
                }
            }
            return currCookie;
        }
    }

    private final class SetCookiesByNameDomainPathIterator implements Iterator<HttpSetCookie> {
        private final Iterator<CharSequence> valueItr;
        private final CharSequence cookieName;
        private final CharSequence domain;
        private final CharSequence path;
        @Nullable
        private HttpSetCookie nextValue;

        SetCookiesByNameDomainPathIterator(final Iterator<CharSequence> valueItr,
                                           final CharSequence cookieName, final CharSequence domain,
                                           final CharSequence path, @Nullable final HttpSetCookie nextValue) {
            this.valueItr = valueItr;
            this.cookieName = cookieName;
            this.domain = domain;
            this.path = path;
            this.nextValue = nextValue;
        }

        @Override
        public boolean hasNext() {
            return nextValue != null;
        }

        @Override
        public HttpSetCookie next() {
            if (nextValue == null) {
                throw new NoSuchElementException();
            }
            HttpSetCookie currCookie = nextValue;
            nextValue = null;
            while (valueItr.hasNext()) {
                CharSequence value = valueItr.next();
                if (regionMatches(cookieName, true, 0, value, 0, cookieName.length())) {
                    HttpSetCookie cookie = parseSetCookie(value, validateCookies);
                    if (domainMatches(domain, cookie.domain()) && pathMatches(path, cookie.path())) {
                        nextValue = cookie;
                        break;
                    }
                }
            }
            return currCookie;
        }
    }
}
