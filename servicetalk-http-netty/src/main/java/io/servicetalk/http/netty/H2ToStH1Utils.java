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

import io.servicetalk.http.api.HttpHeaders;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaderNames.TE;
import static io.netty.handler.codec.http.HttpHeaderValues.TRAILERS;
import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpHeaderNames.CONNECTION;
import static io.servicetalk.http.api.HttpHeaderNames.COOKIE;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderNames.UPGRADE;
import static io.servicetalk.http.api.HttpHeaderValues.KEEP_ALIVE;
import static io.servicetalk.http.netty.HeaderUtils.indexOf;
import static java.lang.Boolean.parseBoolean;
import static java.lang.System.getProperty;

final class H2ToStH1Utils {

    static final CharSequence PROXY_CONNECTION = newAsciiString("proxy-connection");
    /**
     * Keep consistent with {@link io.servicetalk.http.api.HeaderUtils}.
     * <p>
     * Whether cookie parsing should be strictly spec compliant with
     * <a href="https://www.rfc-editor.org/rfc/rfc6265">RFC6265</a> ({@code true}), or allow some deviations that are
     * commonly observed in practice and allowed by the obsolete
     * <a href="https://www.rfc-editor.org/rfc/rfc2965">RFC2965</a>/
     * <a href="https://www.rfc-editor.org/rfc/rfc2109">RFC2109</a> ({@code false}, the default).
     */
    static final boolean COOKIE_STRICT_RFC_6265 = parseBoolean(getProperty(
            "io.servicetalk.http.api.headers.cookieParsingStrictRfc6265", "false"));

    private H2ToStH1Utils() {
        // no instances.
    }

    static void h2HeadersSanitizeForH1(Http2Headers h2Headers) {
        h2HeadersCompressCookieCrumbs(h2Headers);
    }

    /**
     * Combine the cookie values into 1 header entry as required by
     * <a href="https://tools.ietf.org/html/rfc7540#section-8.1.2.5">RFC 7540, 8.1.2.5</a>.
     *
     * @param h2Headers The headers which may contain cookies.
     */
    static void h2HeadersCompressCookieCrumbs(Http2Headers h2Headers) {
        // Netty's value iterator doesn't return elements in insertion order, this is not strictly compliant with the
        // RFC and may result in reversed order cookies.
        Iterator<? extends CharSequence> cookieItr = h2Headers.valueIterator(HttpHeaderNames.COOKIE);
        if (cookieItr.hasNext()) {
            CharSequence prevCookItr = cookieItr.next();
            if (cookieItr.hasNext()) {
                CharSequence cookieHeader = cookieItr.next();
                // *2 gives some space for an extra cookie.
                StringBuilder sb = new StringBuilder(prevCookItr.length() * 2 + cookieHeader.length() + 2);
                sb.append(prevCookItr).append("; ").append(cookieHeader);
                while (cookieItr.hasNext()) {
                    cookieHeader = cookieItr.next();
                    sb.append("; ").append(cookieHeader);
                }
                h2Headers.set(HttpHeaderNames.COOKIE, sb.toString());
            }
        }
    }

    /**
     * Split up cookies to allow for better compression as described in
     * <a href="https://tools.ietf.org/html/rfc7540#section-8.1.2.5">RFC 7540, 8.1.2.5</a>.
     *
     * @param h1Headers The headers which may contain cookies.
     */
    static void h1HeadersSplitCookieCrumbs(HttpHeaders h1Headers) {
        Iterator<? extends CharSequence> cookieItr = h1Headers.valuesIterator(COOKIE);
        // We want to avoid "concurrent modifications" of the headers while we are iterating. So we insert crumbs
        // into an intermediate collection and insert them after the split process concludes.
        List<CharSequence> cookiesToAdd = null;
        while (cookieItr.hasNext()) {
            CharSequence nextCookie = cookieItr.next();
            int i = indexOf(nextCookie, ';', 0);
            if (i > 0) {
                if (cookiesToAdd == null) {
                    cookiesToAdd = new ArrayList<>(4);
                }
                int start = 0;
                if (COOKIE_STRICT_RFC_6265) {
                    do {
                        final CharSequence cookieCrumb = nextCookie.subSequence(start, i);
                        cookiesToAdd.add(cookieCrumb);
                        if (i + 1 < nextCookie.length() && nextCookie.charAt(i + 1) != ' ') {
                            throwNoSpaceAfterCookieCrumb(cookieCrumb);
                        }
                        // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1).
                        start = i + 2;
                    } while (start >= 0 && start < nextCookie.length() &&
                            (i = indexOf(nextCookie, ';', start)) >= 0);
                } else {
                    do {
                        cookiesToAdd.add(nextCookie.subSequence(start, i));
                        start = i + 1 < nextCookie.length() && nextCookie.charAt(i + 1) == ' ' ? i + 2 : i + 1;
                    } while (start >= 0 && start < nextCookie.length() &&
                            (i = indexOf(nextCookie, ';', start)) >= 0);
                }
                if (start >= 0 && start < nextCookie.length()) {
                    cookiesToAdd.add(nextCookie.subSequence(start, nextCookie.length()));
                }
                cookieItr.remove();
            }
        }
        if (cookiesToAdd != null) {
            for (CharSequence crumb : cookiesToAdd) {
                h1Headers.add(COOKIE, crumb);
            }
        }
    }

    private static void throwNoSpaceAfterCookieCrumb(CharSequence cookieCrumb) {
        final int nameEnd = indexOf(cookieCrumb, '=', 0);
        final CharSequence name = nameEnd > 0 ? cookieCrumb.subSequence(0, nameEnd) : cookieCrumb;
        throw new IllegalArgumentException("cookie " + name +
                " must have a space after ; in cookie attribute-value lists");
    }

    static Http2Headers h1HeadersToH2Headers(HttpHeaders h1Headers) {
        if (h1Headers.isEmpty()) {
            if (h1Headers instanceof NettyH2HeadersToHttpHeaders) {
                return ((NettyH2HeadersToHttpHeaders) h1Headers).nettyHeaders();
            }
            return new DefaultHttp2Headers(false, 0);
        }

        // H2 doesn't support connection headers, so remove each one, and the headers corresponding to the
        // connection value.
        // https://tools.ietf.org/html/rfc7540#section-8.1.2.2
        Iterator<? extends CharSequence> connectionItr = h1Headers.valuesIterator(CONNECTION);
        if (connectionItr.hasNext()) {
            do {
                String connectionHeader = connectionItr.next().toString();
                connectionItr.remove();
                int i = connectionHeader.indexOf(',');
                if (i != -1) {
                    int start = 0;
                    do {
                        h1Headers.remove(connectionHeader.substring(start, i));
                        start = i + 1;
                    } while (start < connectionHeader.length() && (i = connectionHeader.indexOf(',', start)) != -1);
                    h1Headers.remove(connectionHeader.substring(start));
                } else {
                    h1Headers.remove(connectionHeader);
                }
            } while (connectionItr.hasNext());
        }

        // remove other illegal headers
        h1Headers.remove(KEEP_ALIVE);
        h1Headers.remove(TRANSFER_ENCODING);
        h1Headers.remove(UPGRADE);
        h1Headers.remove(PROXY_CONNECTION);

        // TE header is treated specially https://tools.ietf.org/html/rfc7540#section-8.1.2.2
        // (only value of "trailers" is allowed).
        Iterator<? extends CharSequence> teItr = h1Headers.valuesIterator(TE);
        boolean addTrailers = false;
        while (teItr.hasNext()) {
            String teValue = teItr.next().toString();
            int i = teValue.indexOf(',');
            if (i != -1) {
                int start = 0;
                do {
                    if (teValue.substring(start, i).compareToIgnoreCase(TRAILERS.toString()) == 0) {
                        addTrailers = true;
                        break;
                    }
                } while (start < teValue.length() && (i = teValue.indexOf(',', start)) != -1);
                teItr.remove();
            } else if (teValue.compareToIgnoreCase(TRAILERS.toString()) != 0) {
                teItr.remove();
            }
        }
        if (addTrailers) { // add after iteration to avoid concurrent modification.
            h1Headers.add(TE, TRAILERS);
        }

        h1HeadersSplitCookieCrumbs(h1Headers);

        if (h1Headers instanceof NettyH2HeadersToHttpHeaders) {
            // Assume header field names are already lowercase if they reside in the Http2Headers. We may want to be
            // more strict in the future, but that would require iteration.
            return ((NettyH2HeadersToHttpHeaders) h1Headers).nettyHeaders();
        }

        if (h1Headers.isEmpty()) {
            return new DefaultHttp2Headers(false, 0);
        }

        DefaultHttp2Headers http2Headers = new DefaultHttp2Headers(false);
        for (Map.Entry<CharSequence, CharSequence> h1Entry : h1Headers) {
            // header field names MUST be converted to lowercase prior to their encoding in HTTP/2
            // https://tools.ietf.org/html/rfc7540#section-8.1.2
            http2Headers.add(h1Entry.getKey().toString().toLowerCase(), h1Entry.getValue());
        }
        return http2Headers;
    }
}
