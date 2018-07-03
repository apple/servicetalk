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

/**
 * A collection of {@link HttpCookie} objects.
 * <p>
 * The storage for {@link HttpCookie} objects is typically backed by a {@link HttpHeaders} object. Modifications
 * made in this interface may not be reflected in the underlying storage until {@link #encodeToHttpHeaders()} is called.
 * <p>
 * Note that this interface is used to represent cookies that are sent or received, in client or server use cases.
 * Consequently, the terminology used in this interface (e.g. cookie-name, cookie-value, etc.) is to be interpreted
 * in the context of the syntax and semantics defined
 * in the <a href="https://tools.ietf.org/html/rfc6265#section-4.1">Set-Cookie</a>
 * and <a href="https://tools.ietf.org/html/rfc6265#section-4.2">Cookie</a> sections of RFC-6265.
 */
public interface HttpCookies extends Iterable<HttpCookie> {
    /**
     * Get a {@link HttpCookie} identified by {@code name}.
     *
     * @param name The cookie-name to look for.
     * @return a {@link HttpCookie} identified by {@code name}.
     */
    @Nullable
    HttpCookie getCookie(CharSequence name);

    /**
     * Get the {@link HttpCookie}s with the same name.
     *
     * @param name the cookie-name of the {@link HttpCookie}s to get.
     * @return the {@link HttpCookie}s with the same name.
     */
    Iterator<? extends HttpCookie> getCookies(CharSequence name);

    /**
     * Get the {@link HttpCookie}s with the same name.
     *
     * @param name the cookie-name of the {@link HttpCookie}s to get.
     * @param domain the domain-value of the {@link HttpCookie}s to get. This value may be matched according
     * to the <a href="https://tools.ietf.org/html/rfc6265#section-5.1.3">Domain Matching</a> algorithm.
     * @param path the path-av of the {@link HttpCookie}s to get. This value may be matched according
     * to the <a href="https://tools.ietf.org/html/rfc6265#section-5.1.4">Path Matching</a> algorithm.
     * @return the {@link HttpCookie}s with same name.
     */
    Iterator<? extends HttpCookie> getCookies(CharSequence name, CharSequence domain, CharSequence path);

    /**
     * Add {@code cookie} to this collection.
     * <p>
     * This may result in multiple {@link HttpCookie}s with same name.
     *
     * @param cookie The cookie to add.
     * @return this.
     */
    HttpCookies addCookie(HttpCookie cookie);

    /**
     * Create a new not wrapped, not secure and not HTTP-only {@link HttpCookie} instance, with no path, domain,
     * expire date and maximum age, and add it to this collection.
     * <p>
     * This may result in multiple {@link HttpCookie}s with same name.
     *
     * @param name the cookie-name of the new {@link HttpCookie}.
     * @param value the cookie-value of the new {@link HttpCookie}.
     * @return a new {@link HttpCookie} instance.
     */
    default HttpCookies addCookie(final CharSequence name, final CharSequence value) {
        return addCookie(name, value, false, false, false);
    }

    /**
     * Create a new {@link HttpCookie} instance, with no path, domain, expire date and maximum age,
     * and add it to this collection.
     * <p>
     * This may result in multiple {@link HttpCookie}s with same name.
     *
     * @param name the cookie-name of the new {@link HttpCookie}.
     * @param value the cookie-value of the new {@link HttpCookie}.
     * @param wrapped {@code true} if the value must be wrapped with double quotes in the new {@link HttpCookie}.
     * @param secure {@code true} if this new {@link HttpCookie} is secure.
     * @param httpOnly {@code true} if this new {@link HttpCookie}
     * is <a href="http://www.owasp.org/index.php/HTTPOnly">HTTP-only</a>.
     * @return a new {@link HttpCookie} instance.
     */
    default HttpCookies addCookie(final CharSequence name, final CharSequence value,
                                  final boolean wrapped, final boolean secure, final boolean httpOnly) {
        return addCookie(name, value, null, null, null, null, wrapped, secure, httpOnly);
    }

    /**
     * Creates a new {@link HttpCookie} instance and add it to this collection.
     * <p>
     * This may result in multiple {@link HttpCookie}s with same name.
     *
     * @param name the cookie-name of the new {@link HttpCookie}.
     * @param value the cookie-value of the new {@link HttpCookie}.
     * @param path the path-value of the new {@link HttpCookie}.
     * @param domain the domain-value of the new {@link HttpCookie}.
     * @param expires the expire date of the new {@link HttpCookie}, represented as an RFC-1123 date
     * defined in <a href="https://tools.ietf.org/html/rfc2616#section-3.3.1">RFC-2616, Section 3.3.1</a>.
     * @param maxAge the maximum age in seconds of the new {@link HttpCookie}.
     * @param wrapped {@code true} if the value must be wrapped with double quotes in the new {@link HttpCookie}.
     * @param secure {@code true} if this new {@link HttpCookie} is secure.
     * @param httpOnly {@code true} if this new {@link HttpCookie}
     * is <a href="http://www.owasp.org/index.php/HTTPOnly">HTTP-only</a>.
     * @return a new {@link HttpCookie} instance.
     */
    HttpCookies addCookie(CharSequence name, CharSequence value,
                          @Nullable CharSequence path, @Nullable CharSequence domain,
                          @Nullable CharSequence expires, @Nullable Long maxAge,
                          boolean wrapped, boolean secure, boolean httpOnly);

    /**
     * Remove all {@link HttpCookie} identified by {@code name}.
     *
     * @param name the cookie-name of the {@link HttpCookie}s to remove.
     * @return the number of {@link HttpCookie}s removed as a result of this operation.
     */
    boolean removeCookies(CharSequence name);

    /**
     * Remove all {@link HttpCookie} identified by {@code name}.
     *
     * @param name the cookie-name of the {@link HttpCookie}s to remove.
     * @param domain the domain-value of the {@link HttpCookie}s to remove. This value may be matched according
     * to the <a href="https://tools.ietf.org/html/rfc6265#section-5.1.3">Domain Matching</a> algorithm.
     * @param path the path-av of the {@link HttpCookie}s to remove. This value may be matched according
     * to the <a href="https://tools.ietf.org/html/rfc6265#section-5.1.4">Path Matching</a> algorithm.
     * @return the number of {@link HttpCookie}s removed as a result of this operation.
     */
    boolean removeCookies(CharSequence name, CharSequence domain, CharSequence path);

    /**
     * Encode the current state of this {@link HttpCookies} to the {@link HttpHeaders}.
     */
    void encodeToHttpHeaders();

    /**
     * Determine if this {@link HttpCookies} contains no {@link HttpCookie} objects.
     *
     * @return {@code true} if this {@link HttpCookies} contains no {@link HttpCookie} objects.
     */
    boolean isEmpty();

    /**
     * Get the total number of {@link HttpCookie} objects contained in this {@link HttpCookies}.
     *
     * @return the total number of {@link HttpCookie} objects contained in this {@link HttpCookies}.
     */
    int size();
}
