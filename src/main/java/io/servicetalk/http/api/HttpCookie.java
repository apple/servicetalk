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

import javax.annotation.Nullable;

/**
 * An interface defining a <a href="https://tools.ietf.org/html/rfc6265#section-4">HTTP cookie</a>.
 */
public interface HttpCookie {
    /**
     * Returns the name of this {@link HttpCookie}.
     *
     * @return The name of this {@link HttpCookie}
     */
    CharSequence getName();

    /**
     * Returns the value of this {@link HttpCookie}.
     *
     * @return The value of this {@link HttpCookie}
     */
    CharSequence getValue();

    /**
     * Returns true if the raw value of this {@link HttpCookie},
     * was wrapped with double quotes in original {@code Set-Cookie} header.
     *
     * @return If the value of this {@link HttpCookie} is to be wrapped
     */
    boolean isWrapped();

    /**
     * Returns the domain of this {@link HttpCookie}.
     *
     * @return The domain of this {@link HttpCookie}
     */
    @Nullable
    CharSequence getDomain();

    /**
     * Returns the path of this {@link HttpCookie}.
     *
     * @return The {@link HttpCookie}'s path
     */
    @Nullable
    CharSequence getPath();

    /**
     * Returns the maximum age of this {@link HttpCookie} in seconds if specified.
     *
     * @return The maximum age of this {@link HttpCookie}. {@code null} if none specified.
     */
    @Nullable
    Long getMaxAge();

    /**
     * Returns the expire date of this {@link HttpCookie} according to <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">Expires</a>.
     * @return the expire date of this {@link HttpCookie} according to <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">Expires</a>.
     */
    @Nullable
    CharSequence getExpires();

    /**
     * Checks to see if this {@link HttpCookie} is secure.
     *
     * @return True if this {@link HttpCookie} is secure, otherwise false
     */
    boolean isSecure();

    /**
     * Checks to see if this {@link HttpCookie} can only be accessed via HTTP.
     * If this returns true, the {@link HttpCookie} cannot be accessed through
     * client side script - But only if the browser supports it.
     * For more information, please look <a href="http://www.owasp.org/index.php/HTTPOnly">here</a>
     *
     * @return True if this {@link HttpCookie} is HTTP-only or false if it isn't
     */
    boolean isHttpOnly();
}
