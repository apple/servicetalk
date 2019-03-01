/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.http.api.DefaultHttpRequestMethod.DefaultHttpRequestMethodProperties;
import io.servicetalk.http.api.HttpRequestMethod.Properties;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.PREFER_DIRECT_RO_ALLOCATOR;
import static io.servicetalk.http.api.HttpRequestMethods.HttpRequestMethodProperties.CACHEABLE;
import static io.servicetalk.http.api.HttpRequestMethods.HttpRequestMethodProperties.IDEMPOTENT;
import static io.servicetalk.http.api.HttpRequestMethods.HttpRequestMethodProperties.NONE;
import static io.servicetalk.http.api.HttpRequestMethods.HttpRequestMethodProperties.SAFE_IDEMPOTENT;
import static io.servicetalk.http.api.HttpRequestMethods.HttpRequestMethodProperties.SAFE_IDEMPOTENT_CACHEABLE;

/**
 * Provides constant instances of {@link HttpRequestMethod}, as well as a mechanism for creating new instances if the
 * existing constants are not sufficient.
 */
public final class HttpRequestMethods {

    /**
     * HTTP <a href="https://tools.ietf.org/html/rfc7231#section-4.3.1">GET</a> method.
     */
    public static final HttpRequestMethod GET =
            new DefaultHttpRequestMethod(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("GET"), SAFE_IDEMPOTENT_CACHEABLE);

    /**
     * HTTP <a href="https://tools.ietf.org/html/rfc7231#section-4.3.2">HEAD</a> method.
     */
    public static final HttpRequestMethod HEAD =
            new DefaultHttpRequestMethod(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("HEAD"), SAFE_IDEMPOTENT_CACHEABLE);

    /**
     * HTTP <a href="https://tools.ietf.org/html/rfc7231#section-4.3.3">POST</a> method.
     */
    public static final HttpRequestMethod POST =
            new DefaultHttpRequestMethod(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("POST"), CACHEABLE);

    /**
     * HTTP <a href="https://tools.ietf.org/html/rfc7231#section-4.3.4">PUT</a> method.
     */
    public static final HttpRequestMethod PUT =
            new DefaultHttpRequestMethod(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("PUT"), IDEMPOTENT);

    /**
     * HTTP <a href="https://tools.ietf.org/html/rfc7231#section-4.3.5">DELETE</a> method.
     */
    public static final HttpRequestMethod DELETE =
            new DefaultHttpRequestMethod(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("DELETE"), IDEMPOTENT);

    /**
     * HTTP <a href="https://tools.ietf.org/html/rfc7231#section-4.3.6">CONNECT</a> method.
     */
    public static final HttpRequestMethod CONNECT =
            new DefaultHttpRequestMethod(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("CONNECT"), NONE);

    /**
     * HTTP <a href="https://tools.ietf.org/html/rfc7231#section-4.3.7">OPTIONS</a> method.
     */
    public static final HttpRequestMethod OPTIONS =
            new DefaultHttpRequestMethod(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("OPTIONS"), SAFE_IDEMPOTENT);

    /**
     * HTTP <a href="https://tools.ietf.org/html/rfc7231#section-4.3.8">TRACE</a> method.
     */
    public static final HttpRequestMethod TRACE =
            new DefaultHttpRequestMethod(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("TRACE"), SAFE_IDEMPOTENT);

    /**
     * HTTP <a href="https://tools.ietf.org/html/rfc5789#section-2">PATCH</a> method.
     */
    public static final HttpRequestMethod PATCH =
            new DefaultHttpRequestMethod(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("PATCH"), NONE);

    private HttpRequestMethods() {
        // No instances
    }

    /**
     * Create a new {@link HttpRequestMethod} for the specified {@link Buffer} representation of
     * <a href="https://tools.ietf.org/html/rfc7231#section-4.1">method name</a> and {@link Properties}.
     *
     * @param name a <a href="https://tools.ietf.org/html/rfc7231#section-4.1">method name</a>
     * @param properties <a href="https://tools.ietf.org/html/rfc7231#section-4.2">Common HTTP Method Properties</a>
     * @return a new {@link HttpRequestMethod}
     */
    public static HttpRequestMethod newRequestMethod(final Buffer name, final Properties properties) {
        return new DefaultHttpRequestMethod(name, properties);
    }

    /**
     * Provides constant instances of {@link Properties}, as well as a mechanism for creating new instances if the
     * existing constants are not sufficient.
     */
    public static final class HttpRequestMethodProperties {

        /**
         * As defined in <a href="https://tools.ietf.org/html/rfc7231#section-4.3">Method Definitions</a>, methods which
         * are safe, idempotent, and cacheable.
         */
        public static final Properties SAFE_IDEMPOTENT_CACHEABLE =
                new DefaultHttpRequestMethodProperties(true, true, true);

        /**
         * As defined in <a href="https://tools.ietf.org/html/rfc7231#section-4.3">Method Definitions</a>, methods which
         * are safe and idempotent, but not cacheable.
         */
        public static final Properties SAFE_IDEMPOTENT = new DefaultHttpRequestMethodProperties(true, true, false);

        /**
         * As defined in <a href="https://tools.ietf.org/html/rfc7231#section-4.3">Method Definitions</a>, methods which
         * are idempotent, but not safe or cacheable.
         */
        public static final Properties IDEMPOTENT = new DefaultHttpRequestMethodProperties(false, true, false);

        /**
         * As defined in <a href="https://tools.ietf.org/html/rfc7231#section-4.3">Method Definitions</a>, methods which
         * are cacheable, but not safe or idempotent.
         */
        public static final Properties CACHEABLE = new DefaultHttpRequestMethodProperties(false, false, true);

        /**
         * As defined in <a href="https://tools.ietf.org/html/rfc7231#section-4.3">Method Definitions</a>, methods which
         * are not safe, idempotent, or cacheable.
         */
        public static final Properties NONE = new DefaultHttpRequestMethodProperties(false, false, false);

        private HttpRequestMethodProperties() {
            // No instances.
        }

        /**
         * Create a new {@link Properties} object for specified {@code safe}, {@code idempotent}, and {@code cacheable}.
         * Generally, the constants in {@link HttpRequestMethodProperties} should be used.
         *
         * @param safe {@code true} if a <a href="https://tools.ietf.org/html/rfc7231#section-4.2.1">safe method</a>
         * @param idempotent {@code true} if a <a href="https://tools.ietf.org/html/rfc7231#section-4.2.2">idempotent
         * method</a>
         * @param cacheable {@code true} if a <a href="https://tools.ietf.org/html/rfc7231#section-4.2.3">cacheable
         * method</a>
         * @return a new {@link Properties}.
         */
        public static Properties newRequestMethodProperties(final boolean safe,
                                                            final boolean idempotent,
                                                            final boolean cacheable) {
            return new DefaultHttpRequestMethodProperties(safe, idempotent, cacheable);
        }
    }
}
