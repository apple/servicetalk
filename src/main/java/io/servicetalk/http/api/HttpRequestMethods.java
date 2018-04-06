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

import io.servicetalk.http.api.DefaultHttpRequestMethod.DefaultHttpRequestMethodProperties;
import io.servicetalk.http.api.HttpRequestMethod.Properties;

import javax.annotation.Nullable;

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

    GET("GET", SAFE_IDEMPOTENT_CACHEABLE),
    HEAD("HEAD", SAFE_IDEMPOTENT_CACHEABLE),
    OPTIONS("OPTIONS", SAFE_IDEMPOTENT),
    TRACE("TRACE", SAFE_IDEMPOTENT),
    PUT("PUT", IDEMPOTENT),
    DELETE("DELETE", IDEMPOTENT),
    POST("POST", CACHEABLE),
    PATCH("PATCH", NONE),
    CONNECT("CONNECT", NONE);

    private final String methodName;
    private final Properties properties;

    HttpRequestMethods(String methodName, Properties properties) {
        this.methodName = methodName;
        this.properties = properties;
    }

    /**
     * Get a {@link HttpRequestMethod} for the specified {@code methodName}, with
     * {@link HttpRequestMethodProperties#NONE} for properties. If the {@code methodName} matches those of this
     * {@code enum}, that {@code enum} value will be returned, otherwise a new instance will be returned.
     *
     * @param methodName the <a href="https://tools.ietf.org/html/rfc7231#section-4.1">method name</a>
     * @return a {@link HttpRequestMethod}.
     */
    public static HttpRequestMethod getRequestMethod(final String methodName) {
        final HttpRequestMethod requestMethod = findRequestMethod(methodName);
        if (requestMethod != null) {
            return requestMethod;
        }
        return new DefaultHttpRequestMethod(methodName);
    }

    /**
     * Get a {@link HttpRequestMethod} for the specified {@code methodName} and {@code properties}. If the
     * {@code methodName} {@link String#equals} and {@code properties} '{@code ==}' those of of this {@code enum}, that
     * {@code enum} value will be returned, otherwise a new instance will be returned.
     *
     * @param methodName the <a href="https://tools.ietf.org/html/rfc7231#section-4.1">method name</a>
     * @param properties the {@link Properties} associated with this request method.
     * @return a {@link HttpRequestMethod}.
     */
    public static HttpRequestMethod getRequestMethod(final String methodName, final Properties properties) {
        final HttpRequestMethod requestMethod = findRequestMethod(methodName);
        if (requestMethod != null && requestMethod.getMethodProperties() == properties) {
            return requestMethod;
        }
        return new DefaultHttpRequestMethod(methodName, properties);
    }

    @Nullable
    private static HttpRequestMethod findRequestMethod(final String methodName) {
        switch (methodName) {
            case "GET":
                return GET;
            case "HEAD":
                return HEAD;
            case "OPTIONS":
                return OPTIONS;
            case "TRACE":
                return TRACE;
            case "PUT":
                return PUT;
            case "DELETE":
                return DELETE;
            case "POST":
                return POST;
            case "PATCH":
                return PATCH;
            case "CONNECT":
                return CONNECT;
            default:
                return null;
        }
    }

    @Override
    public String getName() {
        return methodName;
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
