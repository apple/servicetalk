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

import java.util.Arrays;
import java.util.List;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.PREFER_DIRECT_RO_ALLOCATOR;
import static io.servicetalk.http.api.HttpRequestMethod.Properties.CACHEABLE;
import static io.servicetalk.http.api.HttpRequestMethod.Properties.IDEMPOTENT;
import static io.servicetalk.http.api.HttpRequestMethod.Properties.NONE;
import static io.servicetalk.http.api.HttpRequestMethod.Properties.SAFE_IDEMPOTENT;
import static io.servicetalk.http.api.HttpRequestMethod.Properties.SAFE_IDEMPOTENT_CACHEABLE;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;

/**
 * HTTP <a href="https://tools.ietf.org/html/rfc7231#section-4">Request Methods</a>.
 */
public final class HttpRequestMethod {

    /**
     * HTTP <a href="https://tools.ietf.org/html/rfc7231#section-4.3.1">GET</a> method.
     */
    public static final HttpRequestMethod GET =
            new HttpRequestMethod(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("GET"), SAFE_IDEMPOTENT_CACHEABLE);

    /**
     * HTTP <a href="https://tools.ietf.org/html/rfc7231#section-4.3.2">HEAD</a> method.
     */
    public static final HttpRequestMethod HEAD =
            new HttpRequestMethod(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("HEAD"), SAFE_IDEMPOTENT_CACHEABLE);

    /**
     * HTTP <a href="https://tools.ietf.org/html/rfc7231#section-4.3.3">POST</a> method.
     */
    public static final HttpRequestMethod POST =
            new HttpRequestMethod(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("POST"), CACHEABLE);

    /**
     * HTTP <a href="https://tools.ietf.org/html/rfc7231#section-4.3.4">PUT</a> method.
     */
    public static final HttpRequestMethod PUT =
            new HttpRequestMethod(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("PUT"), IDEMPOTENT);

    /**
     * HTTP <a href="https://tools.ietf.org/html/rfc7231#section-4.3.5">DELETE</a> method.
     */
    public static final HttpRequestMethod DELETE =
            new HttpRequestMethod(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("DELETE"), IDEMPOTENT);

    /**
     * HTTP <a href="https://tools.ietf.org/html/rfc7231#section-4.3.6">CONNECT</a> method.
     */
    public static final HttpRequestMethod CONNECT =
            new HttpRequestMethod(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("CONNECT"), NONE);

    /**
     * HTTP <a href="https://tools.ietf.org/html/rfc7231#section-4.3.7">OPTIONS</a> method.
     */
    public static final HttpRequestMethod OPTIONS =
            new HttpRequestMethod(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("OPTIONS"), SAFE_IDEMPOTENT);

    /**
     * HTTP <a href="https://tools.ietf.org/html/rfc7231#section-4.3.8">TRACE</a> method.
     */
    public static final HttpRequestMethod TRACE =
            new HttpRequestMethod(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("TRACE"), SAFE_IDEMPOTENT);

    /**
     * HTTP <a href="https://tools.ietf.org/html/rfc5789#section-2">PATCH</a> method.
     */
    public static final HttpRequestMethod PATCH =
            new HttpRequestMethod(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("PATCH"), NONE);

    private static final List<HttpRequestMethod> HTTP_REQUEST_METHODS = Arrays.asList(GET, HEAD, POST, PUT, DELETE,
            CONNECT, OPTIONS, TRACE, PATCH);

    private final String nameString;
    private final Buffer name;
    private final Properties properties;

    private HttpRequestMethod(final Buffer name, final Properties properties) {
        if (name.readableBytes() == 0) {
            throw new IllegalArgumentException("Method name cannot be empty");
        }
        this.name = name;
        this.properties = requireNonNull(properties);
        this.nameString = name.toString(US_ASCII);
    }

    /**
     * Returns an {@link HttpRequestMethod} for the specified {@link Buffer} representation of
     * <a href="https://tools.ietf.org/html/rfc7231#section-4.1">method name</a> and {@link Properties}.
     * Generally, the constants in {@link HttpRequestMethod} should be used.
     *
     * @param name a <a href="https://tools.ietf.org/html/rfc7231#section-4.1">method name</a>
     * @param properties <a href="https://tools.ietf.org/html/rfc7231#section-4.2">Common HTTP Method Properties</a>
     * @return an {@link HttpRequestMethod}
     */
    public static HttpRequestMethod of(final Buffer name, final Properties properties) {
        return new HttpRequestMethod(name, properties);
    }

    /**
     * Returns an {@link HttpRequestMethod} for the specified {@link Buffer} representation of
     * <a href="https://tools.ietf.org/html/rfc7231#section-4.1">method name</a>, with the default {@link Properties}
     * for that method.
     *
     * @param name a <a href="https://tools.ietf.org/html/rfc7231#section-4.1">method name</a>
     * @return an {@link HttpRequestMethod}
     * @throws IllegalArgumentException if the method name is not recognized
     */
    public static HttpRequestMethod of(final Buffer name) {
        for (final HttpRequestMethod httpRequestMethod : HTTP_REQUEST_METHODS) {
            if (httpRequestMethod.name.equals(name)) {
                return httpRequestMethod;
            }
        }
        throw new IllegalArgumentException("No HttpRequestMethod found called " + name.toString(US_ASCII));
    }

    /**
     * Returns an {@link HttpRequestMethod} for the specified
     * <a href="https://tools.ietf.org/html/rfc7231#section-4.1">method name</a>, with the default {@link Properties}
     * for that method.
     *
     * @param name a <a href="https://tools.ietf.org/html/rfc7231#section-4.1">method name</a>
     * @return an {@link HttpRequestMethod}
     * @throws IllegalArgumentException if the method name is not recognized
     */
    public static HttpRequestMethod of(final String name) {
        for (final HttpRequestMethod httpRequestMethod : HTTP_REQUEST_METHODS) {
            if (httpRequestMethod.nameString.equals(name)) {
                return httpRequestMethod;
            }
        }
        throw new IllegalArgumentException("No HttpRequestMethod found called " + name);
    }

    /**
     * Write the equivalent of {@link #name()} to a {@link Buffer}.
     *
     * @param buffer the {@link Buffer} to write to
     */
    public void writeNameTo(final Buffer buffer) {
        buffer.writeBytes(name, name.readerIndex(), name.readableBytes());
    }

    /**
     * Get the <a href="https://tools.ietf.org/html/rfc7231#section-4.1">method name</a>.
     *
     * @return The <a href="https://tools.ietf.org/html/rfc7231#section-4.1">method name</a>
     */
    public String name() {
        return nameString;
    }

    /**
     * Get the {@link Properties} associated with this method.
     *
     * @return the {@link Properties} associated with this method
     */
    public Properties properties() {
        return properties;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HttpRequestMethod)) {
            return false;
        }

        final HttpRequestMethod that = (HttpRequestMethod) o;
        /*
         * - name Buffer is ignored for equals/hashCode because it represents nameString and the relationship is
         *   idempotent
         *
         * - properties is ignored for equals/hashCode because they carry additional information which should not alter
         *   the meaning of the method name
         */
        return nameString.equals(that.name());
    }

    @Override
    public int hashCode() {
        return nameString.hashCode();
    }

    @Override
    public String toString() {
        return nameString;
    }

    /**
     * <a href="https://tools.ietf.org/html/rfc7231#section-4.2">Common HTTP Method Properties</a>.
     */
    public static final class Properties {

        /**
         * As defined in <a href="https://tools.ietf.org/html/rfc7231#section-4.3">Method Definitions</a>, methods which
         * are safe, idempotent, and cacheable.
         */
        public static final Properties SAFE_IDEMPOTENT_CACHEABLE = new Properties(true, true, true);

        /**
         * As defined in <a href="https://tools.ietf.org/html/rfc7231#section-4.3">Method Definitions</a>, methods which
         * are safe and idempotent, but not cacheable.
         */
        public static final Properties SAFE_IDEMPOTENT = new Properties(true, true, false);

        /**
         * As defined in <a href="https://tools.ietf.org/html/rfc7231#section-4.3">Method Definitions</a>, methods which
         * are idempotent, but not safe or cacheable.
         */
        public static final Properties IDEMPOTENT = new Properties(false, true, false);

        /**
         * As defined in <a href="https://tools.ietf.org/html/rfc7231#section-4.3">Method Definitions</a>, methods which
         * are cacheable, but not safe or idempotent.
         */
        public static final Properties CACHEABLE = new Properties(false, false, true);

        /**
         * As defined in <a href="https://tools.ietf.org/html/rfc7231#section-4.3">Method Definitions</a>, methods which
         * are not safe, idempotent, or cacheable.
         */
        public static final Properties NONE = new Properties(false, false, false);

        private final boolean safe;
        private final boolean idempotent;
        private final boolean cacheable;

        private Properties(final boolean safe, final boolean idempotent, final boolean cacheable) {
            this.safe = safe;
            this.idempotent = idempotent;
            this.cacheable = cacheable;
        }

        /**
         * Create a new {@link Properties} object for specified {@code safe}, {@code idempotent}, and {@code cacheable}.
         * Generally, the constants in {@link Properties} should be used.
         *
         * @param safe {@code true} if a <a href="https://tools.ietf.org/html/rfc7231#section-4.2.1">safe method</a>
         * @param idempotent {@code true} if a <a href="https://tools.ietf.org/html/rfc7231#section-4.2.2">idempotent
         * method</a>
         * @param cacheable {@code true} if a <a href="https://tools.ietf.org/html/rfc7231#section-4.2.3">cacheable
         * method</a>
         * @return a new {@link Properties} instance.
         */
        public static Properties newRequestMethodProperties(final boolean safe,
                                                            final boolean idempotent,
                                                            final boolean cacheable) {
            return new Properties(safe, idempotent, cacheable);
        }

        /**
         * <a href="https://tools.ietf.org/html/rfc7231#section-4.2.1">Safe Methods</a> are those that are essentially
         * read-only.
         *
         * @return {@code true} if a <a href="https://tools.ietf.org/html/rfc7231#section-4.2.1">safe method</a>
         */
        public boolean isSafe() {
            return safe;
        }

        /**
         * <a href="https://tools.ietf.org/html/rfc7231#section-4.2.2">Idempotent Methods</a> are those that the same
         * action can be repeated indefinitely without changing semantics.
         *
         * @return {@code true} if an <a href="https://tools.ietf.org/html/rfc7231#section-4.2.2">idempotent method</a>
         */
        public boolean isIdempotent() {
            return idempotent;
        }

        /**
         * <a href="https://tools.ietf.org/html/rfc7231#section-4.2.3">Cacheable Methods</a> are those that allow for
         * responses to be cached for future reuse.
         *
         * @return {@code true} if a <a href="https://tools.ietf.org/html/rfc7231#section-4.2.3">cacheable method</a>
         */
        public boolean isCacheable() {
            return cacheable;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Properties)) {
                return false;
            }

            final Properties that = (Properties) o;
            return safe == that.isSafe() && idempotent == that.isIdempotent() && cacheable == that.isCacheable();
        }

        @Override
        public int hashCode() {
            int result = safe ? 1 : 0;
            result = 31 * result + (idempotent ? 1 : 0);
            result = 31 * result + (cacheable ? 1 : 0);
            return result;
        }
    }
}
