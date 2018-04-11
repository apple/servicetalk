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
import javax.annotation.Nullable;

import static io.servicetalk.http.api.AsciiString.contentEqualsIgnoreCase;
import static io.servicetalk.http.api.AsciiString.regionMatches;
import static io.servicetalk.http.api.CharSequences.newCaseInsensitiveAsciiString;
import static io.servicetalk.http.api.HeaderUtils.validateCookieTokenAndHeaderName;
import static io.servicetalk.http.api.NetUtil.isValidIpV4Address;
import static io.servicetalk.http.api.NetUtil.isValidIpV6Address;
import static java.lang.Long.parseLong;
import static java.lang.Math.min;
import static java.util.Collections.emptyIterator;
import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link HttpCookies}.
 */
public final class DefaultHttpCookies extends MultiMap<CharSequence, HttpCookie> implements HttpCookies {
    /**
     * An underlying size of 8 has been shown with the current AsciiString hash algorithm to have no collisions with
     * the current set of supported cookie names. If more cookie names are supported, or the hash algorithm changes
     * this initial value should be re-evaluated.
     */
    private static final HttpHeaders COOKIE_NAMES = new DefaultHttpHeaders(8, false);

    static {
        COOKIE_NAMES.add(newCaseInsensitiveAsciiString("path"), new ParseStateCharSequence(ParseState.ParsingPath));
        COOKIE_NAMES.add(newCaseInsensitiveAsciiString("domain"), new ParseStateCharSequence(ParseState.ParsingDomain));
        COOKIE_NAMES.add(newCaseInsensitiveAsciiString("expires"), new ParseStateCharSequence(ParseState.ParsingExpires));
        COOKIE_NAMES.add(newCaseInsensitiveAsciiString("max-age"), new ParseStateCharSequence(ParseState.ParsingMaxAge));
    }

    private final HttpHeaders httpHeaders;
    private final CharSequence cookieHeaderName;
    private final boolean validateContent;

    /**
     * Create a new instance.
     * @param httpHeaders the {@link HttpHeaders} to parse cookie headers from
     * @param cookieHeaderName the header name to parse cookies from
     * @param validateContent if true, validate the contents of the cookie headers
     */
    public DefaultHttpCookies(final HttpHeaders httpHeaders, final CharSequence cookieHeaderName, final boolean validateContent) {
        this(httpHeaders, cookieHeaderName, validateContent, 16);
    }

    DefaultHttpCookies(final HttpHeaders httpHeaders, final CharSequence cookieHeaderName, final boolean validateContent,
                       final int arraySizeHint) {
        super(arraySizeHint);
        this.httpHeaders = httpHeaders;
        this.cookieHeaderName = cookieHeaderName;
        this.validateContent = validateContent;

        final Iterator<? extends CharSequence> cookiesFromHeadersItr = httpHeaders.getAll(cookieHeaderName);
        while (cookiesFromHeadersItr.hasNext()) {
            parseCookieTextAndValidate(cookiesFromHeadersItr.next());
        }
    }

    @Nullable
    @Override
    public HttpCookie getCookie(final CharSequence name) {
        return getValue(name);
    }

    @Override
    public Iterator<? extends HttpCookie> getCookies(final CharSequence name) {
        return getValues(name);
    }

    @Override
    public Iterator<? extends HttpCookie> getCookies(final CharSequence name, final CharSequence domain, final CharSequence path) {
        final int keyHash = hashCode(name);
        final BucketHead<CharSequence, HttpCookie> bucketHead = entries[index(keyHash)];
        if (bucketHead == null) {
            return emptyIterator();
        }
        MultiMapEntry<CharSequence, HttpCookie> e = bucketHead.entry;
        assert e != null;
        do {
            if (e.keyHash == keyHash && equals(name, e.getKey()) &&
                    domainMatches(domain, e.value.getDomain()) &&
                    pathMatches(path, e.value.getPath())) {
                break;
            }
            e = e.bucketNext;
        } while (e != null);
        return e == null ? emptyIterator() : new CookiesByNameDomainPathIterator(keyHash, name, e, domain, path);
    }

    @Override
    public HttpCookies addCookie(final HttpCookie cookie) {
        put(cookie.getName(), cookie);
        return this;
    }

    @Override
    public boolean removeCookies(final CharSequence name) {
        return removeAll(name);
    }

    @Override
    public boolean removeCookies(final CharSequence name, final CharSequence domain, final CharSequence path) {
        final int nameHash = hashCode(name);
        final int bucketIndex = index(nameHash);
        final BucketHead<CharSequence, HttpCookie> bucketHead = entries[bucketIndex];
        if (bucketHead == null) {
            return false;
        }
        final int sizeBefore = size();
        MultiMapEntry<CharSequence, HttpCookie> e = bucketHead.entry;
        assert e != null;
        do {
            if (e.keyHash == nameHash && equals(name, e.getKey()) &&
                    domainMatches(domain, e.value.getDomain()) &&
                    pathMatches(path, e.value.getPath())) {
                final MultiMapEntry<CharSequence, HttpCookie> tmpEntry = e;
                e = e.bucketNext;
                removeEntry(bucketHead, tmpEntry, bucketIndex);
            } else {
                e = e.bucketNext;
            }
        } while (e != null);
        return sizeBefore != size();
    }

    @Override
    public Iterator<HttpCookie> iterator() {
        return valueIterator();
    }

    @Override
    public void encodeToHttpHeaders() {
        httpHeaders.remove(cookieHeaderName);
        BucketHead<CharSequence, HttpCookie> currentBucketHead = lastBucketHead;
        while (currentBucketHead != null) {
            // 32 is an educated guess that each cookie will require 30 characters.
            final StringBuilder sb = new StringBuilder(size() * 32);
            MultiMapEntry<CharSequence, HttpCookie> current = currentBucketHead.entry;
            assert current != null;
            do {
                sb.append(current.getKey()).append('=');
                if (current.value.isWrapped()) {
                    sb.append('"').append(current.value.getValue()).append('"');
                } else {
                    sb.append(current.value.getValue());
                }
                if (current.value.getDomain() != null) {
                    sb.append("; domain=");
                    sb.append(current.value.getDomain());
                }
                if (current.value.getPath() != null) {
                    sb.append("; path=");
                    sb.append(current.value.getPath());
                }
                if (current.value.getExpires() != null) {
                    sb.append("; expires=");
                    sb.append(current.value.getExpires());
                }
                if (current.value.getMaxAge() != null) {
                    sb.append("; max-age=");
                    sb.append(current.value.getMaxAge());
                }
                if (current.value.isHttpOnly()) {
                    sb.append("; httponly");
                }
                if (current.value.isSecure()) {
                    sb.append("; secure");
                }
                httpHeaders.add(cookieHeaderName, sb.toString());
                sb.setLength(0);
                current = current.bucketNext;
            } while (current != null);
            currentBucketHead = currentBucketHead.prevBucketHead;
        }
    }

    @Override
    protected MultiMapEntry<CharSequence, HttpCookie> newEntry(final CharSequence key, final HttpCookie value, final int keyHash) {
        return new CookieMultiMapEntry(value, keyHash);
    }

    @Override
    protected int hashCode(final CharSequence key) {
        return AsciiString.hashCode(key);
    }

    @Override
    protected boolean equals(final CharSequence key1, final CharSequence key2) {
        return contentEqualsIgnoreCase(key1, key2);
    }

    @Override
    protected boolean isKeyEqualityCompatible(final MultiMap<? extends CharSequence, ? extends HttpCookie> multiMap) {
        return multiMap.getClass().equals(getClass());
    }

    @Override
    protected void validateKey(final CharSequence key) {
        if (key == null || key.length() == 0) {
            throw new IllegalArgumentException("cookie name cannot be null or empty");
        }
        if (validateContent) {
            validateCookieTokenAndHeaderName(key);
        }
    }

    @Override
    protected int hashCodeForValue(final HttpCookie value) {
        return value.hashCode();
    }

    @Override
    protected boolean equalsForValue(final HttpCookie value1, final HttpCookie value2) {
        return value1.equals(value2);
    }

    private static final class CookieMultiMapEntry extends MultiMapEntry<CharSequence, HttpCookie> {
        CookieMultiMapEntry(final HttpCookie cookie, final int keyHash) {
            super(cookie, keyHash);
        }

        @Override
        public CharSequence getKey() {
            return value.getName();
        }
    }

    private enum ParseState {
        ParsingValue,
        ParsingPath,
        ParsingDomain,
        ParsingExpires,
        ParsingMaxAge,
        Unknown
    }

    private static final class ParseStateCharSequence implements CharSequence {
        final ParseState state;

        ParseStateCharSequence(final ParseState state) {
            this.state = state;
        }

        @Override
        public int length() {
            throw new UnsupportedOperationException();
        }

        @Override
        public char charAt(final int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CharSequence subSequence(final int start, final int end) {
            throw new UnsupportedOperationException();
        }
    }

    @SuppressWarnings("Duplicates")
    private void parseCookieTextAndValidate(final CharSequence cookieHeaderValue) {
        CharSequence name = null;
        CharSequence value = null;
        CharSequence path = null;
        CharSequence domain = null;
        CharSequence expires = null;
        Long maxAge = null;
        boolean isWrapped = false;
        boolean isSecure = false;
        boolean isHttpOnly = false;
        ParseState parseState = ParseState.Unknown;
        int begin = 0;
        int i = 0;
        while (i < cookieHeaderValue.length()) {
            final char c = cookieHeaderValue.charAt(i);
            switch (c) {
                case '=':
                    if (name == null) {
                        name = cookieHeaderValue.subSequence(begin, i);
                        parseState = ParseState.ParsingValue;
                    } else if (parseState == ParseState.Unknown) {
                        final CharSequence avName = cookieHeaderValue.subSequence(begin, i);
                        final CharSequence newState = COOKIE_NAMES.get(avName);
                        if (newState != null) {
                            parseState = ((ParseStateCharSequence) newState).state;
                        }
                    } else {
                        throw new IllegalArgumentException("unexpected = at index: " + i);
                    }
                    ++i;
                    begin = i;
                    break;
                case '"':
                    if (parseState == ParseState.ParsingValue) {
                        if (isWrapped) {
                            parseState = ParseState.Unknown;
                            value = cookieHeaderValue.subSequence(begin, i);
                            // Increment by 3 because we are skipping DQUOTE SEMI SP
                            i += 3;
                            begin = i;
                        } else {
                            isWrapped = true;
                            ++i;
                            begin = i;
                        }
                    } else if (value == null) {
                        throw new IllegalArgumentException("unexpected quote at index: " + i);
                    }
                    ++i;
                    break;
                case '%':
                    if (validateContent) {
                        extractAndValidateCookieHexValue(cookieHeaderValue, i);
                    }
                    // Increment by 4 because we are skipping %0x##
                    i += 4;
                    break;
                case ';':
                    // end of value, or end of av-value
                    if (i + 1 == cookieHeaderValue.length()) {
                        throw new IllegalArgumentException("unexpected trailing ';'");
                    }
                    switch (parseState) {
                        case ParsingValue:
                            value = cookieHeaderValue.subSequence(begin, i);
                            break;
                        case ParsingPath:
                            path = cookieHeaderValue.subSequence(begin, i);
                            break;
                        case ParsingDomain:
                            domain = cookieHeaderValue.subSequence(begin, i);
                            break;
                        case ParsingExpires:
                            expires = cookieHeaderValue.subSequence(begin, i);
                            break;
                        case ParsingMaxAge:
                            maxAge = parseLong(cookieHeaderValue.subSequence(begin, i).toString());
                            break;
                        default:
                            if (name == null) {
                                throw new IllegalArgumentException("cookie value not found at index " + i);
                            }
                            final CharSequence avName = cookieHeaderValue.subSequence(begin, i);
                            if (contentEqualsIgnoreCase(avName, "secure")) {
                                isSecure = true;
                            } else if (contentEqualsIgnoreCase(avName, "httponly")) {
                                isHttpOnly = true;
                            }
                            break;
                    }
                    parseState = ParseState.Unknown;
                    i += 2;
                    begin = i;
                    break;
                default:
                    if (validateContent && parseState != ParseState.ParsingExpires) {
                        validateCookieOctetHexValue(c);
                    }
                    ++i;
                    break;
            }
        }

        if (begin < i) {
            // end of value, or end of av-value
            // check for "secure" and "httponly"
            switch (parseState) {
                case ParsingValue:
                    value = cookieHeaderValue.subSequence(begin, i);
                    break;
                case ParsingPath:
                    path = cookieHeaderValue.subSequence(begin, i);
                    break;
                case ParsingDomain:
                    domain = cookieHeaderValue.subSequence(begin, i);
                    break;
                case ParsingExpires:
                    expires = cookieHeaderValue.subSequence(begin, i);
                    break;
                case ParsingMaxAge:
                    maxAge = parseLong(cookieHeaderValue.subSequence(begin, i).toString());
                    break;
                default:
                    if (name == null) {
                        throw new IllegalArgumentException("cookie value not found at index " + i);
                    }
                    final CharSequence avName = cookieHeaderValue.subSequence(begin, i);
                    if (contentEqualsIgnoreCase(avName, "secure")) {
                        isSecure = true;
                    } else if (contentEqualsIgnoreCase(avName, "httponly")) {
                        isHttpOnly = true;
                    }
                    break;
            }
        }

        addCookie(new DefaultHttpCookie(name.toString(), value.toString(), path == null ? null : path.toString(),
                domain == null ? null : domain.toString(), expires == null ? null : expires.toString(),
                maxAge, isWrapped, isSecure, isHttpOnly));
    }

    private final class CookiesByNameDomainPathIterator extends ValuesByNameIterator {
        private final CharSequence domain;
        private final CharSequence path;

        CookiesByNameDomainPathIterator(final int entryHashCode, final CharSequence name,
                                        final MultiMapEntry<CharSequence, HttpCookie> first,
                                        final CharSequence domain, final CharSequence path) {
            super(entryHashCode, name, first);
            this.domain = requireNonNull(domain);
            this.path = requireNonNull(path);
        }

        @Nullable
        MultiMapEntry<CharSequence, HttpCookie> findNext(@Nullable MultiMapEntry<CharSequence, HttpCookie> entry) {
            while (entry != null) {
                if (entry.keyHash == keyHashCode &&
                        DefaultHttpCookies.this.equals(key, entry.getKey()) &&
                        domainMatches(domain, entry.value.getDomain()) &&
                        pathMatches(path, entry.value.getPath())) {
                    return entry;
                }
                entry = entry.bucketNext;
            }
            return null;
        }
    }

    /**
     * <a href="https://tools.ietf.org/html/rfc6265#section-5.1.3">Domain Matching</a>.
     * @param requestDomain The domain from the request.
     * @param cookieDomain The domain from the cookie.
     * @return {@code true} if there is a match.
     */
    private static boolean domainMatches(final CharSequence requestDomain, @Nullable final CharSequence cookieDomain) {
        if (cookieDomain == null || requestDomain.length() == 0) {
            return false;
        }
        final int startIndex = cookieDomain.length() - requestDomain.length();
        if (startIndex == 0) {
            // The RFC has an ambiguous statement [1] related to case sensitivity here but since domain names are
            // generally compared in a case insensitive fashion we do the same here.
            // [1] https://tools.ietf.org/html/rfc6265#section-5.1.3
            // the domain string and the string will have been canonicalized to lower case at this point
            return contentEqualsIgnoreCase(cookieDomain, requestDomain);
        }
        final boolean queryEndsInDot = requestDomain.charAt(requestDomain.length() - 1) == '.';
        return ((queryEndsInDot && startIndex >= -1 &&
                regionMatches(cookieDomain, true, startIndex + 1, requestDomain, 0, requestDomain.length() - 1)) ||
                (!queryEndsInDot && startIndex > 0 &&
                        regionMatches(cookieDomain, true, startIndex, requestDomain, 0, requestDomain.length()))) &&
                !isValidIpV4Address(cookieDomain.toString()) && !isValidIpV6Address(cookieDomain.toString());
        // TODO(scott): Netty will support IP validators which don't require the toString()
    }

    /**
     * <a href="https://tools.ietf.org/html/rfc6265#section-5.1.4">Path Matching</a>.
     * @param requestPath The path from the request.
     * @param cookiePath The path from the cookie.
     * @return {@code true} if there is a match.
     */
    private static boolean pathMatches(final CharSequence requestPath, @Nullable final CharSequence cookiePath) {
        // cookiePath cannot be empty, but we check for 0 length to protect against IIOBE below.
        if (cookiePath == null || cookiePath.length() == 0 || requestPath.length() == 0) {
            return false;
        }

        if (requestPath.length() == cookiePath.length()) {
            return requestPath.equals(cookiePath);
        }
        final boolean actualStartsWithSlash = cookiePath.charAt(0) == '/';
        final int length = min(actualStartsWithSlash ? cookiePath.length() - 1 : cookiePath.length(), requestPath.length());
        return regionMatches(requestPath, false, requestPath.charAt(0) == '/' &&
                !actualStartsWithSlash ? 1 : 0, cookiePath, 0, length) &&
                (requestPath.length() > cookiePath.length() || cookiePath.charAt(length) == '/');
    }

    /**
     * Extract a hex value and validate according to the
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-octet</a> format.
     * @param cookieHeaderValue The cookie's value.
     * @param i The index where we detected a '%' character indicating a hex value is to follow.
     */
    private static void extractAndValidateCookieHexValue(final CharSequence cookieHeaderValue, final int i) {
        if (cookieHeaderValue.length() - 3 <= i) {
            throw new IllegalArgumentException("invalid hex encoded value");
        }
        char c2 = cookieHeaderValue.charAt(i + 1);
        if (c2 != 'X' && c2 != 'x') {
            throw new IllegalArgumentException("unexpected hex indicator " + c2);
        }
        c2 = cookieHeaderValue.charAt(i + 2);
        final char c3 = cookieHeaderValue.charAt(i + 3);
        // The MSB can only be 0,1,2 so we do a cheaper conversion of hex -> decimal.
        final int hexValue = (c2 - '0') * 16 + hexToDecimal(c3);
        validateCookieOctetHexValue(hexValue);
    }

    /**
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">
     *     cookie-octet = %x21 / %x23-2B / %x2D-3A / %x3C-5B / %x5D-7E</a>
     * @param hexValue The decimal representation of the hexadecimal value.
     */
    private static void validateCookieOctetHexValue(final int hexValue) {
        if (hexValue != 33 &&
                (hexValue < 35 || hexValue > 43) &&
                (hexValue < 45 || hexValue > 58) &&
                (hexValue < 60 || hexValue > 91) &&
                (hexValue < 93 || hexValue > 126)) {
            throw new IllegalArgumentException("unexpected hex value " + hexValue);
        }
    }

    private static int hexToDecimal(final char c) {
        return c >= '0' && c <= '9' ? c - '0' : c >= 'a' && c <= 'f' ? (c - 'a') + 10 : c >= 'A' && c < 'F' ?
                (c - 'A') + 10 : -1;
    }
}
