/*
 * Copyright © 2018-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.ByteProcessor;
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.encoding.api.Identity;
import io.servicetalk.serialization.api.SerializationException;
import io.servicetalk.utils.internal.IllegalCharacterException;

import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.caseInsensitiveHashCode;
import static io.servicetalk.buffer.api.CharSequences.contentEquals;
import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.buffer.api.CharSequences.forEachByte;
import static io.servicetalk.buffer.api.CharSequences.indexOf;
import static io.servicetalk.buffer.api.CharSequences.regionMatches;
import static io.servicetalk.encoding.api.Identity.identity;
import static io.servicetalk.encoding.api.internal.HeaderUtils.encodingFor;
import static io.servicetalk.http.api.HttpHeaderNames.ACCEPT_ENCODING;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_ENCODING;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderNames.VARY;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.NetUtils.isValidIpV4Address;
import static io.servicetalk.http.api.NetUtils.isValidIpV6Address;
import static io.servicetalk.http.api.UriUtils.TCHAR_HMASK;
import static io.servicetalk.http.api.UriUtils.TCHAR_LMASK;
import static io.servicetalk.http.api.UriUtils.isBitSet;
import static io.servicetalk.utils.internal.CharsetUtils.standardCharsets;
import static java.lang.Math.min;
import static java.lang.System.lineSeparator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableMap;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.compile;
import static java.util.regex.Pattern.quote;
import static java.util.stream.Collectors.toMap;

/**
 * Utilities to use for {@link HttpHeaders} implementations.
 */
public final class HeaderUtils {
    /**
     * Constant used to seed the hash code generation. Could be anything but this was borrowed from murmur3.
     */
    static final int HASH_CODE_SEED = 0xc2b2ae35;
    public static final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> DEFAULT_HEADER_FILTER =
            (k, v) -> "<filtered>";
    private static final List<CharSequence> DEFAULT_DEBUG_HEADER_NAMES = asList(CONTENT_TYPE, CONTENT_LENGTH,
            TRANSFER_ENCODING);
    static final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> DEFAULT_DEBUG_HEADER_FILTER =
            (key, value) -> {
                for (CharSequence headerName : DEFAULT_DEBUG_HEADER_NAMES) {
                    if (contentEqualsIgnoreCase(key, headerName)) {
                        return value;
                    }
                }
                return "<filtered>";
            };
    private static final ByteProcessor TOKEN_VALIDATOR = value -> {
        validateToken(value);
        return true;
    };

    private static final Pattern HAS_CHARSET_PATTERN = compile(".+;\\s*charset=.+", CASE_INSENSITIVE);
    private static final Map<Charset, Pattern> CHARSET_PATTERNS;

    static {
        CHARSET_PATTERNS = unmodifiableMap(standardCharsets().stream()
                .collect(toMap(Function.identity(), e -> compileCharsetRegex(e.name()))));
    }

    private HeaderUtils() {
        // no instances
    }

    static String toString(final HttpHeaders headers,
                           final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> filter) {
        final String simpleName = headers.getClass().getSimpleName();
        final int size = headers.size();
        if (size == 0) {
            return simpleName + "[]";
        } else {
            // original capacity assumes 20 chars per headers
            final StringBuilder sb = new StringBuilder(simpleName.length() + 2 + size * 20)
                    .append(simpleName)
                    .append('[');
            final Iterator<Map.Entry<CharSequence, CharSequence>> itr = headers.iterator();
            if (itr.hasNext()) {
                for (;;) {
                    final Map.Entry<CharSequence, CharSequence> e = itr.next();
                    sb.append(e.getKey()).append(": ").append(filter.apply(e.getKey(), e.getValue()));
                    if (itr.hasNext()) {
                        sb.append(lineSeparator());
                    } else {
                        break;
                    }
                }
            }
            return sb.append(']').toString();
        }
    }

    static boolean equals(final HttpHeaders lhs, final HttpHeaders rhs) {
        if (lhs.size() != rhs.size()) {
            return false;
        }

        if (lhs == rhs) {
            return true;
        }

        // The regular iterator is not suitable for equality comparisons because the overall ordering is not
        // in any specific order relative to the content of this MultiMap.
        for (final CharSequence name : lhs.names()) {
            final Iterator<? extends CharSequence> valueItr = lhs.valuesIterator(name);
            final Iterator<? extends CharSequence> h2ValueItr = rhs.valuesIterator(name);
            while (valueItr.hasNext() && h2ValueItr.hasNext()) {
                if (!contentEquals(valueItr.next(), h2ValueItr.next())) {
                    return false;
                }
            }
            if (valueItr.hasNext() != h2ValueItr.hasNext()) {
                return false;
            }
        }
        return true;
    }

    static int hashCode(final HttpHeaders headers) {
        if (headers.isEmpty()) {
            return 0;
        }
        int result = HASH_CODE_SEED;
        for (final CharSequence key : headers.names()) {
            result = 31 * result + caseInsensitiveHashCode(key);
            final Iterator<? extends CharSequence> valueItr = headers.valuesIterator(key);
            while (valueItr.hasNext()) {
                result = 31 * result + caseInsensitiveHashCode(valueItr.next());
            }
        }
        return result;
    }

    /**
     * Returns {@code true} if {@code headers} indicates {@code transfer-encoding} {@code chunked}.
     * <p>
     * The values of all {@link HttpHeaderNames#TRANSFER_ENCODING} headers are interpreted as comma-separated values,
     * with spaces between values trimmed. If any of these values is  {@link HttpHeaderValues#CHUNKED}, this method
     * return {@code true}, otherwise it returns {@code false}.
     *
     * @param headers The {@link HttpHeaders} to check.
     * @return {@code} true if {@code headers} indicates {@code transfer-encoding} {@code chunked}, {@code false}
     * otherwise.
     */
    public static boolean isTransferEncodingChunked(final HttpHeaders headers) {
        // As per https://tools.ietf.org/html/rfc7230#section-3.3.1 the `transfer-encoding` header may contain
        // multiple values, comma separated.
        return containsCommaSeparatedValueIgnoreCase(headers, TRANSFER_ENCODING, CHUNKED);
    }

    static boolean containsCommaSeparatedValueIgnoreCase(final HttpHeaders headers, final CharSequence name,
                                                         final CharSequence value) {
        final Iterator<? extends CharSequence> values = headers.valuesIterator(name);
        while (values.hasNext()) {
            final CharSequence next = values.next();
            if (containsCommaSeparatedValueIgnoreCase(next, value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if {@code commaSeparatedValues} is comma separated and one of the values (or the whole
     * string) matches {@code needle} case insensitively.
     *
     * @param commaSeparatedValues the comma separated values.
     * @param needle the value to look for.
     * @return {@code true} if the value is found, {@code false} otherwise.
     */
    private static boolean containsCommaSeparatedValueIgnoreCase(final CharSequence commaSeparatedValues,
                                                                 final CharSequence needle) {
        int start = 0;
        int commaPos = indexOf(commaSeparatedValues, ',', 0);
        if (commaPos < 0) {
            return contentEqualsIgnoreCase(commaSeparatedValues, needle);
        }

        // Only convert to a String if we actually have a comma-separated value to parse
        final String commaSeparatedValuesStr = commaSeparatedValues.toString();
        for (;;) {
            if (commaPos < 0) {
                return start > 0 && contentEqualsIgnoreCase(commaSeparatedValuesStr.substring(start).trim(), needle);
            }
            final String subvalue = commaSeparatedValuesStr.substring(start, commaPos).trim();
            if (contentEqualsIgnoreCase(subvalue, needle)) {
                return true;
            }
            start = commaPos + 1;
            commaPos = commaSeparatedValuesStr.indexOf(',', start);
        }
    }

    static boolean hasContentLength(final HttpHeaders headers) {
        return headers.contains(CONTENT_LENGTH);
    }

    static void addContentEncoding(final HttpHeaders headers, CharSequence encoding) {
        // H2 does not support TE / Transfer-Encoding, so we rely in the presentation encoding only.
        // https://tools.ietf.org/html/rfc7540#section-8.1.2.2
        headers.add(CONTENT_ENCODING, encoding);
        headers.add(VARY, CONTENT_ENCODING);
    }

    static boolean hasContentEncoding(final HttpHeaders headers) {
        return headers.contains(CONTENT_ENCODING);
    }

    static void setAcceptEncoding(final HttpHeaders headers, @Nullable final CharSequence encodings) {
        if (encodings != null && !headers.contains(ACCEPT_ENCODING)) {
            headers.set(ACCEPT_ENCODING, encodings);
        }
    }

    static void validateCookieNameAndValue(final CharSequence cookieName, final CharSequence cookieValue) {
        if (cookieName == null || cookieName.length() == 0) {
            throw new IllegalArgumentException("Null or empty cookie names are not allowed.");
        }
        if (cookieValue == null) {
            throw new IllegalArgumentException("Null cookie values are not allowed.");
        }
    }

    /**
     * Validate {@code key} is valid <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a>
     * (aka <a href="https://tools.ietf.org/html/rfc2616#section-2.2">token</a>) and a
     * valid <a href="https://tools.ietf.org/html/rfc7230#section-3.2.6">field-name</a> of a
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.2">header-field</a>. Both of these
     * formats have the same restrictions.
     *
     * @param key the cookie name or header name to validate.
     */
    static void validateCookieTokenAndHeaderName(final CharSequence key) {
        forEachByte(key, TOKEN_VALIDATOR);
    }

    /**
     * <a href="https://tools.ietf.org/html/rfc6265#section-5.1.3">Domain Matching</a>.
     *
     * @param requestDomain The domain from the request.
     * @param cookieDomain The domain from the cookie.
     * @return {@code true} if there is a match.
     */
    public static boolean domainMatches(final CharSequence requestDomain, @Nullable final CharSequence cookieDomain) {
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
                !isValidIpV4Address(cookieDomain) && !isValidIpV6Address(cookieDomain);
    }

    /**
     * <a href="https://tools.ietf.org/html/rfc6265#section-5.1.4">Path Matching</a>.
     *
     * @param requestPath The path from the request.
     * @param cookiePath The path from the cookie.
     * @return {@code true} if there is a match.
     */
    public static boolean pathMatches(final CharSequence requestPath, @Nullable final CharSequence cookiePath) {
        // cookiePath cannot be empty, but we check for 0 length to protect against IIOBE below.
        if (cookiePath == null || cookiePath.length() == 0 || requestPath.length() == 0) {
            return false;
        }

        if (requestPath.length() == cookiePath.length()) {
            return contentEquals(requestPath, cookiePath);
        }
        final boolean actualStartsWithSlash = cookiePath.charAt(0) == '/';
        final int length = min(actualStartsWithSlash ? cookiePath.length() - 1 :
                cookiePath.length(), requestPath.length());
        return regionMatches(requestPath, false, requestPath.charAt(0) == '/' &&
                !actualStartsWithSlash ? 1 : 0, cookiePath, 0, length) &&
                (requestPath.length() > cookiePath.length() || cookiePath.charAt(length) == '/');
    }

    /**
     * Determine if a <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">set-cookie-string</a>'s
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a> matches {@code setCookieName}.
     *
     * @param setCookieString The <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">set-cookie-string</a>.
     * @param setCookieName The <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a>.
     * @return {@code true} if a <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">set-cookie-string</a>'s
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a> matches {@code setCookieName}.
     */
    public static boolean isSetCookieNameMatches(final CharSequence setCookieString,
                                                 final CharSequence setCookieName) {
        int equalsIndex = indexOf(setCookieString, '=', 0);
        return equalsIndex > 0 && setCookieString.length() - 1 > equalsIndex &&
                equalsIndex == setCookieName.length() &&
                regionMatches(setCookieName, true, 0, setCookieString, 0, equalsIndex);
    }

    /**
     * Parse a {@link HttpSetCookie} from a
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">set-cookie-string</a>.
     *
     * @param setCookieString The <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">set-cookie-string</a>.
     * @param setCookieName The <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a>.
     * @param validate {@code true} to attempt extra validation.
     * @return a {@link HttpSetCookie} from a
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">set-cookie-string</a>.
     */
    @Nullable
    public static HttpSetCookie parseSetCookie(final CharSequence setCookieString,
                                               final CharSequence setCookieName,
                                               final boolean validate) {
        if (isSetCookieNameMatches(setCookieString, setCookieName)) {
            return DefaultHttpSetCookie.parseSetCookie(setCookieString, validate,
                    setCookieString.subSequence(0, setCookieName.length()), setCookieName.length() + 1);
        }
        return null;
    }

    /**
     * Parse a single <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie-pair</a> from a
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie-string</a>.
     *
     * @param cookieString The <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie-string</a> that may
     * contain multiple <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie-pair</a>s.
     * @param cookiePairName The <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a> identifying
     * the <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie-pair</a>s to parse.
     * @return
     * <ul>
     * <li>The first <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie-pair</a> from a
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie-string</a> whose
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a> matched {@code cookiePairName}</li>
     * <li>{@code null} if no matches were found</li>
     * </ul>
     */
    @Nullable
    public static HttpCookiePair parseCookiePair(final CharSequence cookieString,
                                                 final CharSequence cookiePairName) {
        int start = 0;
        for (;;) {
            int equalsIndex = indexOf(cookieString, '=', start);
            if (equalsIndex <= 0 || cookieString.length() - 1 <= equalsIndex) {
                break;
            }
            int nameLen = equalsIndex - start;
            int semiIndex = indexOf(cookieString, ';', equalsIndex + 1);
            if (nameLen == cookiePairName.length() &&
                    regionMatches(cookiePairName, true, 0, cookieString, start, nameLen)) {
                return DefaultHttpCookiePair.parseCookiePair(cookieString, start, nameLen, semiIndex);
            }

            if (semiIndex < 0 || cookieString.length() - 2 <= semiIndex) {
                break;
            }
            // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
            start = semiIndex + 2;
        }
        return null;
    }

    /**
     * Remove a single <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie-pair</a> for a
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie-string</a>.
     *
     * @param cookieString The <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie-string</a> that may
     * contain multiple <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie-pair</a>s.
     * @param cookiePairName The <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a> identifying
     * the <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie-pair</a>s to remove.
     * @return
     * <ul>
     * <li>The <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie-string</a> value with all
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie-pair</a>s removed whose
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a> matches {@code cookiePairName}</li>
     * <li>Empty if all the <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie-pair</a>s matched
     * {@code cookiePairName}</li>
     * <li>{@code null} if none of the <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie-pair</a>s
     * matched {@code cookiePairName}</li>
     * </ul>
     */
    @Nullable
    public static CharSequence removeCookiePairs(final CharSequence cookieString,
                                                 final CharSequence cookiePairName) {
        int start = 0;
        int beginCopyIndex = 0;
        StringBuilder sb = null;
        for (;;) {
            int equalsIndex = indexOf(cookieString, '=', start);
            if (equalsIndex <= 0 || cookieString.length() - 1 <= equalsIndex) {
                break;
            }
            int nameLen = equalsIndex - start;
            int semiIndex = indexOf(cookieString, ';', equalsIndex + 1);
            if (nameLen == cookiePairName.length() &&
                    regionMatches(cookiePairName, true, 0, cookieString, start, nameLen)) {
                if (beginCopyIndex != start) {
                    if (sb == null) {
                        sb = new StringBuilder(cookieString.length() - beginCopyIndex);
                    } else {
                        sb.append("; ");
                    }
                    sb.append(cookieString.subSequence(beginCopyIndex, start - 2));
                }
                if (semiIndex < 0 || cookieString.length() - 2 <= semiIndex) {
                    // start is used after the loop to know if a match was found and therefore the entire header
                    // may need removal.
                    start = cookieString.length();
                    break;
                }
                // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
                start = semiIndex + 2;
                beginCopyIndex = start;
            } else if (semiIndex > 0) {
                if (cookieString.length() - 2 <= semiIndex) {
                    throw new IllegalArgumentException("cookie is not allowed to end with ;");
                }
                // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
                start = semiIndex + 2;
            } else {
                if (beginCopyIndex != 0) {
                    if (sb == null) {
                        sb = new StringBuilder(cookieString.length() - beginCopyIndex);
                    } else {
                        sb.append("; ");
                    }
                    sb.append(cookieString.subSequence(beginCopyIndex, cookieString.length()));
                }
                break;
            }
        }

        return sb == null ? (start == cookieString.length() ? "" : null) : sb.toString();
    }

    /**
     * An {@link Iterator} of {@link HttpCookiePair} designed to iterate across multiple values of
     * {@link HttpHeaderNames#COOKIE}.
     */
    public abstract static class CookiesIterator implements Iterator<HttpCookiePair> {
        @Nullable
        private HttpCookiePair next;
        private int nextNextStart;

        @Override
        public final boolean hasNext() {
            return next != null;
        }

        @Override
        public final HttpCookiePair next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            HttpCookiePair current = next;
            CharSequence cookieHeaderValue = cookieHeaderValue();
            next = cookieHeaderValue == null ? null : findNext(cookieHeaderValue);
            return current;
        }

        /**
         * Get the current value for {@link HttpHeaderNames#COOKIE}. This value may change during iteration.
         *
         * @return the current value for {@link HttpHeaderNames#COOKIE}, or {@code null} if all have been iterated.
         */
        @Nullable
        protected abstract CharSequence cookieHeaderValue();

        /**
         * Advance the {@link #cookieHeaderValue()} to the next {@link HttpHeaderNames#COOKIE} header value.
         */
        protected abstract void advanceCookieHeaderValue();

        /**
         * Initialize the next {@link HttpCookiePair} value for {@link #next()}.
         *
         * @param cookieHeaderValue The initial value for {@link HttpHeaderNames#COOKIE}.
         */
        protected final void initNext(CharSequence cookieHeaderValue) {
            next = findNext(cookieHeaderValue);
        }

        /**
         * Find the next {@link HttpCookiePair} value for {@link #next()}.
         *
         * @param cookieHeaderValue The current value for {@link HttpHeaderNames#COOKIE}.
         * @return the next {@link HttpCookiePair} value for {@link #next()}, or {@code null} if all have been parsed.
         */
        private HttpCookiePair findNext(CharSequence cookieHeaderValue) {
            int semiIndex = indexOf(cookieHeaderValue, ';', nextNextStart);
            HttpCookiePair next = DefaultHttpCookiePair.parseCookiePair(cookieHeaderValue, nextNextStart, semiIndex);
            if (semiIndex > 0) {
                if (cookieHeaderValue.length() - 2 <= semiIndex) {
                    advanceCookieHeaderValue();
                    nextNextStart = 0;
                } else {
                    // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
                    nextNextStart = semiIndex + 2;
                }
            } else {
                advanceCookieHeaderValue();
                nextNextStart = 0;
            }
            return next;
        }
    }

    /**
     * An {@link Iterator} of {@link HttpCookiePair} designed to iterate across multiple values of
     * {@link HttpHeaderNames#COOKIE} for a specific {@link HttpCookiePair#name() cookie-name}.
     */
    public abstract static class CookiesByNameIterator implements Iterator<HttpCookiePair> {
        private final CharSequence cookiePairName;
        private int nextNextStart;
        @Nullable
        private HttpCookiePair next;

        /**
         * Create a new instance.
         *
         * @param cookiePairName Each return value of {@link #next()} will have {@link HttpCookiePair#name()} equivalent
         * to this value.
         */
        protected CookiesByNameIterator(final CharSequence cookiePairName) {
            this.cookiePairName = cookiePairName;
        }

        @Override
        public final boolean hasNext() {
            return next != null;
        }

        @Override
        public final HttpCookiePair next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            HttpCookiePair current = next;
            CharSequence cookieHeaderValue = cookieHeaderValue();
            next = cookieHeaderValue == null ? null : findNext(cookieHeaderValue);
            return current;
        }

        /**
         * Get the current value for {@link HttpHeaderNames#COOKIE}. This value may change during iteration.
         *
         * @return the current value for {@link HttpHeaderNames#COOKIE}, or {@code null} if all have been iterated.
         */
        @Nullable
        protected abstract CharSequence cookieHeaderValue();

        /**
         * Advance the {@link #cookieHeaderValue()} to the next {@link HttpHeaderNames#COOKIE} header value.
         */
        protected abstract void advanceCookieHeaderValue();

        /**
         * Initialize the next {@link HttpCookiePair} value for {@link #next()}.
         *
         * @param cookieHeaderValue The initial value for {@link HttpHeaderNames#COOKIE}.
         */
        protected final void initNext(CharSequence cookieHeaderValue) {
            next = findNext(cookieHeaderValue);
        }

        /**
         * Find the next {@link HttpCookiePair} value for {@link #next()}.
         *
         * @param cookieHeaderValue The current value for {@link HttpHeaderNames#COOKIE}.
         * @return the next {@link HttpCookiePair} value for {@link #next()}, or {@code null} if all have been parsed.
         */
        @Nullable
        private HttpCookiePair findNext(CharSequence cookieHeaderValue) {
            for (;;) {
                int equalsIndex = indexOf(cookieHeaderValue, '=', nextNextStart);
                if (equalsIndex <= 0 || cookieHeaderValue.length() - 1 <= equalsIndex) {
                    break;
                }
                int nameLen = equalsIndex - nextNextStart;
                int semiIndex = indexOf(cookieHeaderValue, ';', equalsIndex + 1);
                if (nameLen == cookiePairName.length() &&
                        regionMatches(cookiePairName, true, 0, cookieHeaderValue, nextNextStart, nameLen)) {
                    HttpCookiePair next = DefaultHttpCookiePair.parseCookiePair(cookieHeaderValue, nextNextStart,
                            nameLen, semiIndex);
                    if (semiIndex > 0) {
                        if (cookieHeaderValue.length() - 2 <= semiIndex) {
                            advanceCookieHeaderValue();
                            nextNextStart = 0;
                        } else {
                            // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
                            nextNextStart = semiIndex + 2;
                        }
                    } else {
                        advanceCookieHeaderValue();
                        nextNextStart = 0;
                    }
                    return next;
                } else if (semiIndex > 0) {
                    if (cookieHeaderValue.length() - 2 <= semiIndex) {
                        throw new IllegalArgumentException("cookie is not allowed to end with ;");
                    }
                    // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
                    nextNextStart = semiIndex + 2;
                } else {
                    advanceCookieHeaderValue();
                    cookieHeaderValue = cookieHeaderValue();
                    if (cookieHeaderValue == null) {
                        break;
                    }
                    nextNextStart = 0;
                }
            }
            return null;
        }
    }

    /**
     * Attempts to identify the {@link ContentCodec} from a name, as found in the {@code 'Content-Encoding'}
     * header of a request or a response.
     * If the name can not be matched to any of the supported encodings on this endpoint, then
     * a {@link UnsupportedContentEncodingException} is thrown.
     * If the matched encoding is {@link Identity#identity()} then this returns {@code null}.
     * @deprecated Will be removed along with {@link ContentCodec}.
     * @param headers The headers to read the encoding name from
     * @param allowedEncodings The supported encodings for this endpoint
     * @return The {@link ContentCodec} that matches the name or null if matches to identity
     */
    @Nullable
    @Deprecated
    static ContentCodec identifyContentEncodingOrNullIfIdentity(
            final HttpHeaders headers, final List<ContentCodec> allowedEncodings) {

        final CharSequence encoding = headers.get(CONTENT_ENCODING);
        if (encoding == null) {
            return null;
        }

        ContentCodec enc = encodingFor(allowedEncodings, encoding);
        if (enc == null) {
            throw new UnsupportedContentEncodingException(encoding.toString());
        }

        return identity().equals(enc) ? null : enc;
    }

    /**
     * Checks if the provider headers contain a {@code Content-Type} header that matches the specified content type,
     * and optionally the provided charset.
     *
     * @param headers the {@link HttpHeaders} instance
     * @param expectedContentType the content type to look for, provided as a {@code type/subtype}
     * @param expectedCharset an optional charset constraint.
     * @return {@code true} if a {@code Content-Type} header that matches the specified content type, and optionally
     * the provided charset has been found, {@code false} otherwise.
     * @see <a href="https://tools.ietf.org/html/rfc2045#section-5.1">Syntax of the Content-Type Header Field</a>
     */
    public static boolean hasContentType(final HttpHeaders headers,
                                         final CharSequence expectedContentType,
                                         @Nullable final Charset expectedCharset) {
        final CharSequence contentTypeHeader = headers.get(CONTENT_TYPE);
        if (contentTypeHeader == null || contentTypeHeader.length() == 0) {
            return false;
        }
        if (expectedCharset == null) {
            if (contentEqualsIgnoreCase(expectedContentType, contentTypeHeader)) {
                return true;
            }
            return regionMatches(contentTypeHeader, true, 0, expectedContentType, 0, expectedContentType.length());
        }
        if (!regionMatches(contentTypeHeader, true, 0, expectedContentType, 0, expectedContentType.length())) {
            return false;
        }

        if (UTF_8.equals(expectedCharset)) {
            if (contentTypeHeader.length() == expectedContentType.length()) {
                return true;
            }
            if (regionMatches(contentTypeHeader, true, expectedContentType.length(), "; charset=UTF-8", 0, 15)) {
                return true;
            }
            if (!hasCharset(contentTypeHeader)) {
                return true;
            }
        }

        // None of the fastlane shortcuts have bitten -> use a regex to try to match the charset param wherever it is
        Pattern pattern = CHARSET_PATTERNS.get(expectedCharset);
        if (pattern == null) {
            pattern = compileCharsetRegex(expectedCharset.name());
        }
        return pattern.matcher(contentTypeHeader.subSequence(expectedContentType.length(), contentTypeHeader.length()))
                .matches();
    }

    /**
     * Checks if the provider headers contain a {@code Content-Type} header that satisfies the supplied predicate.
     *
     * @param headers the {@link HttpHeaders} instance
     * @param contentTypePredicate the content type predicate
     */
    static void checkContentType(final HttpHeaders headers, Predicate<HttpHeaders> contentTypePredicate) {
        if (!contentTypePredicate.test(headers)) {
            throw new SerializationException("Unexpected headers, can not deserialize. Headers: "
                    + headers.toString(DEFAULT_DEBUG_HEADER_FILTER));
        }
    }

    /**
     * Checks if the provider headers contain a {@code Content-Type} header that satisfies the supplied predicate.
     *
     * @param headers the {@link HttpHeaders} instance
     * @param contentTypePredicate the content type predicate
     */
    static void deserializeCheckContentType(final HttpHeaders headers, Predicate<HttpHeaders> contentTypePredicate) {
        if (!contentTypePredicate.test(headers)) {
            throw new io.servicetalk.serializer.api.SerializationException(
                    "Unexpected headers, can not deserialize. Headers: "
                            + headers.toString(DEFAULT_DEBUG_HEADER_FILTER));
        }
    }

    private static Pattern compileCharsetRegex(String charsetName) {
        return compile(".*;\\s*charset=\"?" + quote(charsetName) + "\"?\\s*(;.*|$)", CASE_INSENSITIVE);
    }

    private static boolean hasCharset(final CharSequence contentTypeHeader) {
        return HAS_CHARSET_PATTERN.matcher(contentTypeHeader).matches();
    }

    /**
     * Validate char is valid <a href="https://tools.ietf.org/html/rfc7230#section-3.2.6">token</a> character.
     *
     * @param value the character to validate.
     */
    private static void validateToken(final byte value) {
        // HEADER
        // header-field   = field-name ":" OWS field-value OWS
        //
        // field-name     = token
        // token          = 1*tchar
        //
        // tchar          = "!" / "#" / "$" / "%" / "&" / "'" / "*"
        //                    / "+" / "-" / "." / "^" / "_" / "`" / "|" / "~"
        //                    / DIGIT / ALPHA
        //                    ; any VCHAR, except delimiters
        //  Delimiters are chosen
        //   from the set of US-ASCII visual characters not allowed in a token
        //   (DQUOTE and "(),/:;<=>?@[\]{}")
        //
        // COOKIE
        // cookie-pair       = cookie-name "=" cookie-value
        // cookie-name       = token
        // token          = 1*<any CHAR except CTLs or separators>
        // CTL = <any US-ASCII control character
        //       (octets 0 - 31) and DEL (127)>
        // separators     = "(" | ")" | "<" | ">" | "@"
        //                      | "," | ";" | ":" | "\" | <">
        //                      | "/" | "[" | "]" | "?" | "="
        //                      | "{" | "}" | SP | HT
        //
        // field-name's token is equivalent to cookie-name's token, we can reuse the tchar mask for both:
        if (!isTchar(value)) {
            throw new IllegalCharacterException(value,
                    "! / # / $ / % / & / ' / * / + / - / . / ^ / _ / ` / | / ~ / DIGIT / ALPHA");
        }
    }

    // visible for testing
    static boolean isTchar(final byte value) {
        return isBitSet(value, TCHAR_LMASK, TCHAR_HMASK);
    }
}
