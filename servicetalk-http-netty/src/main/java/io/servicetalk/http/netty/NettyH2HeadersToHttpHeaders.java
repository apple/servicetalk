/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HeaderUtils;
import io.servicetalk.http.api.HttpCookiePair;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpSetCookie;
import io.servicetalk.utils.internal.IllegalCharacterException;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.Http2Headers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.contentEquals;
import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.buffer.api.CharSequences.forEachByte;
import static io.servicetalk.http.api.DefaultHttpSetCookie.parseSetCookie;
import static io.servicetalk.http.api.HeaderUtils.DEFAULT_HEADER_FILTER;
import static io.servicetalk.http.api.HeaderUtils.domainMatches;
import static io.servicetalk.http.api.HeaderUtils.isSetCookieNameMatches;
import static io.servicetalk.http.api.HeaderUtils.parseCookiePair;
import static io.servicetalk.http.api.HeaderUtils.pathMatches;
import static io.servicetalk.http.api.HeaderUtils.removeCookiePairs;
import static io.servicetalk.http.api.HttpHeaderNames.COOKIE;
import static io.servicetalk.http.api.HttpHeaderNames.SET_COOKIE;
import static java.util.Collections.emptyIterator;

final class NettyH2HeadersToHttpHeaders implements HttpHeaders {

    // ASCII symbols:
    private static final byte HT = 9;
    private static final byte DEL = 127;
    private static final byte CONTROL_CHARS_MASK = (byte) 0xE0;

    private final Http2Headers nettyHeaders;
    private final boolean validateCookies;
    private final boolean validateValues;

    NettyH2HeadersToHttpHeaders(final Http2Headers nettyHeaders, final boolean validateCookies,
                                final boolean validateValues) {
        this.nettyHeaders = nettyHeaders;
        this.validateCookies = validateCookies;
        this.validateValues = validateValues;
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
    public Iterator<? extends CharSequence> valuesIterator(final CharSequence name) {
        return nettyHeaders.valueIterator(name);
    }

    @Override
    public boolean contains(final CharSequence name, final CharSequence value) {
        return nettyHeaders.contains(name, value);
    }

    @Override
    public boolean containsIgnoreCase(final CharSequence name, final CharSequence value) {
        return nettyHeaders.contains(name, value, true);
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
        nettyHeaders.add(name, validateHeaderValue(value));
        return this;
    }

    @Override
    public HttpHeaders add(final CharSequence name, final Iterable<? extends CharSequence> values) {
        nettyHeaders.add(name, validateHeaderValue(values));
        return this;
    }

    @Override
    public HttpHeaders add(final CharSequence name, final CharSequence... values) {
        nettyHeaders.add(name, validateHeaderValue(values));
        return this;
    }

    @Override
    public HttpHeaders add(final HttpHeaders headers) {
        for (Entry<CharSequence, CharSequence> entry : headers) {
            nettyHeaders.add(entry.getKey(), validateHeaderValue(entry.getValue()));
        }
        return this;
    }

    @Override
    public HttpHeaders set(final CharSequence name, final CharSequence value) {
        nettyHeaders.set(name, validateHeaderValue(value));
        return this;
    }

    @Override
    public HttpHeaders set(final CharSequence name, final Iterable<? extends CharSequence> values) {
        nettyHeaders.set(name, validateHeaderValue(values));
        return this;
    }

    @Override
    public HttpHeaders set(final CharSequence name, final CharSequence... values) {
        nettyHeaders.set(name, validateHeaderValue(values));
        return this;
    }

    @Override
    public boolean remove(final CharSequence name) {
        return nettyHeaders.remove(name);
    }

    @Override
    public boolean remove(final CharSequence name, final CharSequence value) {
        final int sizeBefore = size();
        Iterator<? extends CharSequence> valuesItr = nettyHeaders.valueIterator(name);
        while (valuesItr.hasNext()) {
            CharSequence next = valuesItr.next();
            if (contentEquals(next, value)) {
                valuesItr.remove();
            }
        }
        return sizeBefore != size();
    }

    @Override
    public boolean removeIgnoreCase(final CharSequence name, final CharSequence value) {
        final int sizeBefore = size();
        Iterator<? extends CharSequence> valuesItr = nettyHeaders.valueIterator(name);
        while (valuesItr.hasNext()) {
            CharSequence next = valuesItr.next();
            if (contentEqualsIgnoreCase(next, value)) {
                valuesItr.remove();
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
            HttpCookiePair cookiePair = parseCookiePair(valueItr.next(), name);
            if (cookiePair != null) {
                return cookiePair;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public HttpSetCookie getSetCookie(final CharSequence name) {
        Iterator<CharSequence> valueItr = nettyHeaders.valueIterator(HttpHeaderNames.SET_COOKIE);
        while (valueItr.hasNext()) {
            HttpSetCookie setCookie = HeaderUtils.parseSetCookie(valueItr.next(), name, validateCookies);
            if (setCookie != null) {
                return setCookie;
            }
        }
        return null;
    }

    @Override
    public Iterator<? extends HttpCookiePair> getCookiesIterator() {
        Iterator<CharSequence> valueItr = nettyHeaders.valueIterator(HttpHeaderNames.COOKIE);
        return valueItr.hasNext() ? new CookiesIterator(valueItr) : emptyIterator();
    }

    @Override
    public Iterator<? extends HttpCookiePair> getCookiesIterator(final CharSequence name) {
        return new CookiesByNameIterator(nettyHeaders.valueIterator(HttpHeaderNames.COOKIE), name);
    }

    @Override
    public Iterator<? extends HttpSetCookie> getSetCookiesIterator() {
        Iterator<CharSequence> valueItr = nettyHeaders.valueIterator(HttpHeaderNames.SET_COOKIE);
        return valueItr.hasNext() ? new SetCookiesIterator(valueItr) : emptyIterator();
    }

    @Override
    public Iterator<? extends HttpSetCookie> getSetCookiesIterator(final CharSequence name) {
        Iterator<CharSequence> valueItr = nettyHeaders.valueIterator(HttpHeaderNames.SET_COOKIE);
        while (valueItr.hasNext()) {
            HttpSetCookie setCookie = HeaderUtils.parseSetCookie(valueItr.next(), name, validateCookies);
            if (setCookie != null) {
                return new SetCookiesByNameIterator(valueItr, setCookie);
            }
        }
        return emptyIterator();
    }

    @Override
    public Iterator<? extends HttpSetCookie> getSetCookiesIterator(final CharSequence name, final CharSequence domain,
                                                                   final CharSequence path) {
        Iterator<CharSequence> valueItr = nettyHeaders.valueIterator(HttpHeaderNames.SET_COOKIE);
        while (valueItr.hasNext()) {
            // In the future we could attempt to delay full parsing of the cookie until after the domain/path have
            // been matched, but for simplicity just do the parsing ahead of time.
            HttpSetCookie setCookie = HeaderUtils.parseSetCookie(valueItr.next(), name, validateCookies);
            if (setCookie != null && domainMatches(domain, setCookie.domain()) &&
                    pathMatches(path, setCookie.path())) {
                return new SetCookiesByNameDomainPathIterator(valueItr, setCookie, domain, path);
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
            CharSequence newHeaderValue = removeCookiePairs(valuesItr.next(), name);
            if (newHeaderValue != null) {
                if (newHeaderValue.length() != 0) {
                    if (cookiesToAdd == null) {
                        cookiesToAdd = new ArrayList<>(4);
                    }
                    cookiesToAdd.add(newHeaderValue);
                }
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
        Iterator<? extends CharSequence> valueItr = nettyHeaders.valueIterator(HttpHeaderNames.SET_COOKIE);
        while (valueItr.hasNext()) {
            if (isSetCookieNameMatches(valueItr.next(), name)) {
                valueItr.remove();
            }
        }
        return sizeBefore != size();
    }

    @Override
    public boolean removeSetCookies(final CharSequence name, final CharSequence domain, final CharSequence path) {
        final int sizeBefore = size();
        Iterator<? extends CharSequence> valueItr = nettyHeaders.valueIterator(HttpHeaderNames.SET_COOKIE);
        while (valueItr.hasNext()) {
            // In the future we could attempt to delay full parsing of the cookie until after the domain/path have
            // been matched, but for simplicity just do the parsing ahead of time.
            HttpSetCookie setCookie = HeaderUtils.parseSetCookie(valueItr.next(), name, false);
            if (setCookie != null && domainMatches(domain, setCookie.domain()) &&
                    pathMatches(path, setCookie.path())) {
                valueItr.remove();
            }
        }
        return sizeBefore != size();
    }

    @SuppressWarnings("ClassNameSameAsAncestorName")
    private static final class CookiesIterator extends HeaderUtils.CookiesIterator {
        private final Iterator<CharSequence> valueItr;
        @Nullable
        private CharSequence headerValue;

        CookiesIterator(final Iterator<CharSequence> valueItr) {
            this.valueItr = valueItr;
            if (valueItr.hasNext()) {
                headerValue = valueItr.next();
                initNext(headerValue);
            }
        }

        @Nullable
        @Override
        protected CharSequence cookieHeaderValue() {
            return headerValue;
        }

        @Override
        protected void advanceCookieHeaderValue() {
            headerValue = valueItr.hasNext() ? valueItr.next() : null;
        }
    }

    @SuppressWarnings("ClassNameSameAsAncestorName")
    private static final class CookiesByNameIterator extends HeaderUtils.CookiesByNameIterator {
        private final Iterator<CharSequence> valueItr;
        @Nullable
        private CharSequence headerValue;

        CookiesByNameIterator(final Iterator<CharSequence> valueItr, final CharSequence name) {
            super(name);
            this.valueItr = valueItr;
            if (valueItr.hasNext()) {
                headerValue = valueItr.next();
                initNext(headerValue);
            }
        }

        @Nullable
        @Override
        protected CharSequence cookieHeaderValue() {
            return headerValue;
        }

        @Override
        protected void advanceCookieHeaderValue() {
            headerValue = valueItr.hasNext() ? valueItr.next() : null;
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
        @Nullable
        private HttpSetCookie next;

        SetCookiesByNameIterator(final Iterator<CharSequence> valueItr, final HttpSetCookie next) {
            this.valueItr = valueItr;
            this.next = next;
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
            HttpSetCookie currentCookie = next;
            next = null;
            while (valueItr.hasNext()) {
                next = HeaderUtils.parseSetCookie(valueItr.next(), currentCookie.name(), validateCookies);
                if (next != null) {
                    break;
                }
            }
            return currentCookie;
        }

        @Override
        public void remove() {
            valueItr.remove();
        }
    }

    private final class SetCookiesByNameDomainPathIterator implements Iterator<HttpSetCookie> {
        private final Iterator<CharSequence> valueItr;
        private final CharSequence domain;
        private final CharSequence path;
        @Nullable
        private HttpSetCookie next;

        SetCookiesByNameDomainPathIterator(final Iterator<CharSequence> valueItr,
                                           final HttpSetCookie next, final CharSequence domain,
                                           final CharSequence path) {
            this.valueItr = valueItr;
            this.domain = domain;
            this.path = path;
            this.next = next;
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
            HttpSetCookie currentCookie = next;
            next = null;
            while (valueItr.hasNext()) {
                // In the future we could attempt to delay full parsing of the cookie until after the domain/path have
                // been matched, but for simplicity just do the parsing ahead of time.
                HttpSetCookie setCookie = HeaderUtils.parseSetCookie(valueItr.next(), currentCookie.name(),
                        validateCookies);
                if (setCookie != null && domainMatches(domain, setCookie.domain()) &&
                        pathMatches(path, setCookie.path())) {
                    next = setCookie;
                    break;
                }
            }
            return currentCookie;
        }

        @Override
        public void remove() {
            valueItr.remove();
        }
    }

    private CharSequence validateHeaderValue(final CharSequence value) {
        if (validateValues) {
            forEachByte(value, NettyH2HeadersToHttpHeaders::validateHeaderValue);
        }
        return value;
    }

    private Iterable<? extends CharSequence> validateHeaderValue(final Iterable<? extends CharSequence> values) {
        if (validateValues) {
            for (CharSequence v : values) {
                forEachByte(v, NettyH2HeadersToHttpHeaders::validateHeaderValue);
            }
        }
        return values;
    }

    private CharSequence[] validateHeaderValue(final CharSequence... values) {
        if (validateValues) {
            for (CharSequence v : values) {
                forEachByte(v, NettyH2HeadersToHttpHeaders::validateHeaderValue);
            }
        }
        return values;
    }

    /**
     * Validate char is a valid <a href="https://tools.ietf.org/html/rfc7230#section-3.2">field-value</a> character.
     *
     * @param value the character to validate.
     */
    private static boolean validateHeaderValue(final byte value) {
        // HEADER
        // header-field   = field-name ":" OWS field-value OWS
        //
        // field-value    = *( field-content / obs-fold )
        // field-content  = field-vchar [ 1*( SP / HTAB ) field-vchar ]
        // field-vchar    = VCHAR / obs-text
        //
        // obs-fold       = CRLF 1*( SP / HTAB )
        //                ; obsolete line folding
        //                ; see Section 3.2.4
        //
        // Note: we do not support obs-fold.
        // Illegal chars are control chars (0-31) except HT (9), and DEL (127):
        if (((value & CONTROL_CHARS_MASK) == 0 && value != HT) || value == DEL) {
            throw new IllegalCharacterException(value,
                    "(VCHAR / obs-text) [ 1*(SP / HTAB) (VCHAR / obs-text) ]");
        }
        return true;
    }
}
