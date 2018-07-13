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
package io.servicetalk.http.all.netty;

import javax.annotation.Nullable;

/**
 * Defines the common schemes used for the HTTP protocol as defined by
 * <a href="https://tools.ietf.org/html/rfc7230">rfc7230</a>.
 */
public enum HttpScheme {
    /**
     * Scheme for non-secure HTTP connection.
     */
    HTTP("http", 80),

    /**
     * Scheme for secure HTTP connection.
     */
    HTTPS("https", 443),

    /**
     * Constant which indicates that no scheme is present.
     */
    NONE(null, -1);

    @Nullable
    private final String name;
    private final int defaultPort;

    HttpScheme(@Nullable final String name, final int defaultPort) {
        this.name = name;
        this.defaultPort = defaultPort;
    }

    /**
     * Returns a lower case name of this {@link HttpScheme}.
     *
     * @return A lower case name of this {@link HttpScheme}.
     */
    @Nullable
    public String getLowerCaseName() {
        return name;
    }

    /**
     * Returns a default TCP port number for this {@link HttpScheme}.
     *
     * @return A default TCP port number for this {@link HttpScheme}.
     */
    public int getDefaultPort() {
        return defaultPort;
    }

    static HttpScheme from(@Nullable String scheme) {
        if (scheme == null) {
            return NONE;
        }
        switch (scheme) {
            case "http":
                return HTTP;
            case "https":
                return HTTPS;
            default:
                throw new IllegalArgumentException("Unknown scheme: " + scheme);
        }
    }
}
