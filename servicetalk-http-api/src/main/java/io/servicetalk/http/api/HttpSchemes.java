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
package io.servicetalk.http.api;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Provides constant instances of {@link HttpScheme}, as well as a mechanism for creating new instances if the existing
 * constants are not sufficient.
 */
public final class HttpSchemes {

    /**
     * Scheme for non-secure HTTP connection as defined by
     * <a href="https://tools.ietf.org/html/rfc7230#section-2.7.1">RFC 7230, section 2.7.1</a>.
     */
    public static final HttpScheme HTTP = new DefaultHttpScheme("http", 80);

    /**
     * Scheme for secure HTTP connection as defined by
     * <a href="https://tools.ietf.org/html/rfc7230#section-2.7.2">RFC 7230, section 2.7.2</a>.
     */
    public static final HttpScheme HTTPS = new DefaultHttpScheme("https", 443);

    private HttpSchemes() {
        // No instances
    }

    /**
     * Returns an {@link HttpScheme} object for the specified scheme name or {@code null} if none can be found.
     *
     * @param name the name for {@link HttpScheme}
     * @return an {@link HttpScheme} object for the specified scheme name or {@code null} if none can be found
     */
    @Nullable
    public static HttpScheme findScheme(final String name) {
        if (HTTP.name().equalsIgnoreCase(name)) {
            return HTTP;
        }
        if (HTTPS.name().equalsIgnoreCase(name)) {
            return HTTPS;
        }
        return null;
    }

    /**
     * Returns an {@link HttpScheme} object for the specified scheme name and default port number.
     *
     * @param name the name of the {@link HttpScheme}
     * @param defaultPort the default port number of the {@link HttpScheme}
     * @return an {@link HttpScheme} object for the specified scheme name and default port number
     */
    public static HttpScheme httpScheme(final String name, final int defaultPort) {
        if (HTTP.defaultPort() == defaultPort && HTTP.name().equalsIgnoreCase(name)) {
            return HTTP;
        }
        if (HTTPS.defaultPort() == defaultPort && HTTPS.name().equalsIgnoreCase(name)) {
            return HTTPS;
        }
        return new DefaultHttpScheme(name, defaultPort);
    }

    private static final class DefaultHttpScheme implements HttpScheme {

        private final String name;
        private final int defaultPort;

        DefaultHttpScheme(final String name, final int defaultPort) {
            this.name = requireNonNull(name);
            this.defaultPort = defaultPort;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public int defaultPort() {
            return defaultPort;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof HttpScheme)) {
                return false;
            }
            final HttpScheme that = (HttpScheme) o;
            return defaultPort == that.defaultPort() && name.equalsIgnoreCase(that.name());
        }

        @Override
        public int hashCode() {
            return 31 * name.hashCode() + defaultPort;
        }

        @Override
        public String toString() {
            return name();
        }
    }
}
