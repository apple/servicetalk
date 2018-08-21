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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.http.api.DefaultHttpRequestMethod.DefaultHttpRequestMethodProperties;

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
public enum HttpRequestMethods implements HttpRequestMethod {

    GET(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("GET"), SAFE_IDEMPOTENT_CACHEABLE),
    HEAD(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("HEAD"), SAFE_IDEMPOTENT_CACHEABLE),
    OPTIONS(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("OPTIONS"), SAFE_IDEMPOTENT),
    TRACE(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("TRACE"), SAFE_IDEMPOTENT),
    PUT(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("PUT"), IDEMPOTENT),
    DELETE(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("DELETE"), IDEMPOTENT),
    POST(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("POST"), CACHEABLE),
    PATCH(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("PATCH"), NONE),
    CONNECT(PREFER_DIRECT_RO_ALLOCATOR.fromAscii("CONNECT"), NONE);

    private final Buffer methodName;
    private final Properties properties;

    HttpRequestMethods(Buffer methodName, Properties properties) {
        this.methodName = methodName;
        this.properties = properties;
    }

    /**
     * Create a new {@link HttpRequestMethod} from a
     * <a href="https://tools.ietf.org/html/rfc7231#section-4.1">method name</a>.
     * @param methodName a <a href="https://tools.ietf.org/html/rfc7231#section-4.1">method name</a>.
     * @return a new {@link HttpRequestMethod}.
     */
    public static HttpRequestMethod newRequestMethod(final Buffer methodName) {
        return new DefaultHttpRequestMethod(methodName);
    }

    @Override
    public void writeNameTo(final Buffer buffer) {
        buffer.writeBytes(methodName, methodName.getReaderIndex(), methodName.getReadableBytes());
    }

    @Override
    public String getName() {
        return toString();
    }

    @Override
    public Properties getMethodProperties() {
        return properties;
    }

    /**
     * Provides constant instances of {@link Properties}, as well as a mechanism for creating new instances if the
     * existing constants are not sufficient.
     */
    public static final class HttpRequestMethodProperties {

        /**
         * As defined in <a href="https://tools.ietf.org/html/rfc7231#section-4.3">Method Definitions</a>, methods which are safe, idempotent, and cacheable.
         */
        public static final Properties SAFE_IDEMPOTENT_CACHEABLE = new DefaultHttpRequestMethodProperties(true, true, true);

        /**
         * As defined in <a href="https://tools.ietf.org/html/rfc7231#section-4.3">Method Definitions</a>, methods which are safe and idempotent, but not cacheable.
         */
        public static final Properties SAFE_IDEMPOTENT = new DefaultHttpRequestMethodProperties(true, true, false);

        /**
         * As defined in <a href="https://tools.ietf.org/html/rfc7231#section-4.3">Method Definitions</a>, methods which are idempotent, but not safe or cacheable.
         */
        public static final Properties IDEMPOTENT = new DefaultHttpRequestMethodProperties(false, true, false);

        /**
         * As defined in <a href="https://tools.ietf.org/html/rfc7231#section-4.3">Method Definitions</a>, methods which are cacheable, but not safe or idempotent.
         */
        public static final Properties CACHEABLE = new DefaultHttpRequestMethodProperties(false, false, true);

        /**
         * As defined in <a href="https://tools.ietf.org/html/rfc7231#section-4.3">Method Definitions</a>, methods which are not safe, idempotent, or cacheable.
         */
        public static final Properties NONE = new DefaultHttpRequestMethodProperties(false, false, false);

        private HttpRequestMethodProperties() {
            // No instances.
        }

        /**
         * Create a new {@link Properties}. Generally, the constants in {@link HttpRequestMethodProperties} should be
         * used.
         *
         * @param safe {@code true} if a <a href="https://tools.ietf.org/html/rfc7231#section-4.2.1">safe method</a>.
         * @param idempotent {@code true} if a <a href="https://tools.ietf.org/html/rfc7231#section-4.2.2">idempotent method</a>.
         * @param cacheable {@code true} if a <a href="https://tools.ietf.org/html/rfc7231#section-4.2.3">cacheable method</a>.
         * @return a new {@link Properties}.
         */
        public static Properties getRequestMethodProperties(final boolean safe, final boolean idempotent, final boolean cacheable) {
            return new DefaultHttpRequestMethodProperties(safe, idempotent, cacheable);
        }
    }
}
