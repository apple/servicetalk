/**
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
 * The storage of for {@link HttpCookie} objects is typically backed by a {@link HttpHeaders} object. Modifications
 * made in this interface may not be reflected in the underlying storage until {@link #encodeToHttpHeaders()} is called.
 */
public interface HttpCookies extends Iterable<HttpCookie> {
    /**
     * Get a {@link HttpCookie} identified by {@code name}.
     * @param name The <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a> to look for.
     * @return a {@link HttpCookie} identified by {@code name}.
     */
    @Nullable
    HttpCookie getCookie(String name);

    /**
     * Get the {@link HttpCookie}s with the <a href="https://tools.ietf.org/html/rfc6265#section-4.2.2">same name</a>.
     * @param name the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a> of the {@link HttpCookie}s to get.
     * @return the {@link HttpCookie}s with the <a href="https://tools.ietf.org/html/rfc6265#section-4.2.2">same name</a>.
     */
    Iterator<? extends HttpCookie> getCookies(String name);

    /**
     * Get the {@link HttpCookie}s with the <a href="https://tools.ietf.org/html/rfc6265#section-4.2.2">same name</a>.
     * @param name the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a> of the {@link HttpCookie}s to get.
     * @param domain the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">domain-value</a> of the {@link HttpCookie}s to get.
     *               This value may be matched according to the <a href="https://tools.ietf.org/html/rfc6265#section-5.1.3">Domain Matching</a> algorithm.
     * @param path the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">path-av</a> of the {@link HttpCookie}s to get.
     *             This value may be matched according to the <a href="https://tools.ietf.org/html/rfc6265#section-5.1.4">Path Matching</a> algorithm.
     * @return the {@link HttpCookie}s with the <a href="https://tools.ietf.org/html/rfc6265#section-4.2.2">same name</a>.
     */
    Iterator<? extends HttpCookie> getCookies(String name, String domain, String path);

    /**
     * Add {@code cookie} to this collection.
     * <p>
     * This may result in multiple {@link HttpCookie}s with the <a href="https://tools.ietf.org/html/rfc6265#section-4.2.2">same name</a>.
     * @param cookie The cookie to add.
     * @return this.
     */
    HttpCookies addCookie(HttpCookie cookie);

    /**
     * Remove all {@link HttpCookie} identified by {@code name}.
     * @param name the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a> of the {@link HttpCookie}s to remove.
     * @return the number of {@link HttpCookie}s removed as a result of this operation.
     */
    boolean removeCookies(String name);

    /**
     * Remove all {@link HttpCookie} identified by {@code name}.
     * @param name the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a> of the {@link HttpCookie}s to remove.
     * @param domain the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">domain-value</a> of the {@link HttpCookie}s to remove.
     *               This value may be matched according to the <a href="https://tools.ietf.org/html/rfc6265#section-5.1.3">Domain Matching</a> algorithm.
     * @param path the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">path-av</a> of the {@link HttpCookie}s to remove.
     *             This value may be matched according to the <a href="https://tools.ietf.org/html/rfc6265#section-5.1.4">Path Matching</a> algorithm.
     * @return the number of {@link HttpCookie}s removed as a result of this operation.
     */
    boolean removeCookies(String name, String domain, String path);

    /**
     * Encode the current state of this {@link HttpCookies} to the {@link HttpHeaders}.
     */
    void encodeToHttpHeaders();

    /**
     * Determine if this {@link HttpCookies} contains no {@link HttpCookie} objects.
     * @return {@code true} if this {@link HttpCookies} contains no {@link HttpCookie} objects.
     */
    boolean isEmpty();

    /**
     * Get the total number of {@link HttpCookie} objects contained in this {@link HttpCookies}.
     * @return the total number of {@link HttpCookie} objects contained in this {@link HttpCookies}.
     */
    int size();
}
