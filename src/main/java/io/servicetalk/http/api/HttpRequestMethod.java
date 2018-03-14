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

/**
 * HTTP <a href="https://tools.ietf.org/html/rfc7231#section-4">Request Methods</a>.
 * <p>
 * Instance of this type may be used in an associative array object so implementations are encouraged to implement
 * {@link Object#equals(Object)} and {@link Object#hashCode()}.
 */
public interface HttpRequestMethod extends Comparable<HttpRequestMethod> {
    /**
     * Get the <a href="https://tools.ietf.org/html/rfc7231#section-4.1">method name</a>.
     * @return the <a href="https://tools.ietf.org/html/rfc7231#section-4.1">method name</a>.
     */
    String getMethod();

    /**
     * Get the {@link Properties} associated with this object.
     * @return the {@link Properties} associated with this object.
     */
    Properties getMethodProperties();

    /**
     * <a href="https://tools.ietf.org/html/rfc7231#section-4.2">Common Http Method Properties</a>.
     */
    interface Properties {
        /**
         * <a href="https://tools.ietf.org/html/rfc7231#section-4.2.1">Safe Methods</a> are those that are essentially read-only.
         * @return {@code true} if a <a href="https://tools.ietf.org/html/rfc7231#section-4.2.1">safe method</a>.
         */
        boolean isSafe();

        /**
         * <a href="https://tools.ietf.org/html/rfc7231#section-4.2.2">Idempotent Methods</a> are those that the same action can be
         * repeated indefinitely without changing semantics.
         * @return {@code true} if a <a href="https://tools.ietf.org/html/rfc7231#section-4.2.2">idempotent method</a>.
         */
        boolean isIdempotent();

        /**
         * <a href="https://tools.ietf.org/html/rfc7231#section-4.2.3">Cacheable Methods</a> are those that allow for responses
         * to be cached for future reuse.
         * @return {@code true} if a <a href="https://tools.ietf.org/html/rfc7231#section-4.2.3">cacheable method</a>.
         */
        boolean isCacheable();
    }
}
