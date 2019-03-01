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

/**
 * Provides constant instances of {@link HttpProtocolVersion}, as well as a mechanism for creating new instances if the
 * existing constants are not sufficient.
 */
public final class HttpProtocolVersions {

    /**
     * HTTP/1.1 version described in <a href="https://tools.ietf.org/html/rfc7230">RFC 7230</a>.
     */
    public static final HttpProtocolVersion HTTP_1_1 = new DefaultHttpProtocolVersion(1, 1);

    /**
     * HTTP/1.0 version described in <a href="https://tools.ietf.org/html/rfc1945">RFC 1945</a>.
     */
    public static final HttpProtocolVersion HTTP_1_0 = new DefaultHttpProtocolVersion(1, 0);

    private HttpProtocolVersions() {
        // No instances
    }

    /**
     * Return a {@link HttpProtocolVersion} for the specified {@code major} and {@code minor}.
     *
     * @param major the <strong>&lt;major&gt;</strong> portion of the
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">HTTP protocol version</a>
     * @param minor the <strong>&lt;minor&gt;</strong> portion of the
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">HTTP protocol version</a>
     * @return a cached or new {@link HttpProtocolVersion}
     * @throws IllegalArgumentException if {@code major} or {@code minor} is not a 1-digit integer
     */
    public static HttpProtocolVersion getProtocolVersion(final int major, final int minor) {
        if (major == 1) {
            if (minor == 1) {
                return HTTP_1_1;
            }
            if (minor == 0) {
                return HTTP_1_0;
            }
        }
        return new DefaultHttpProtocolVersion(major, minor);
    }

    /**
     * Create a new {@link HttpProtocolVersion} from its {@link Buffer} representation. The passed {@link Buffer} will
     * be parsed to extract {@code major} and {@code minor} components of the version.
     *
     * @param httpVersion a {@link Buffer} representation of the
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">HTTP protocol version</a>
     * @return a new {@link HttpProtocolVersion}
     * @throws IllegalArgumentException if {@code httpVersion} format is not {@code HTTP/DIGIT.DIGIT}
     */
    public static HttpProtocolVersion newProtocolVersion(final Buffer httpVersion) {
        return new DefaultHttpProtocolVersion(httpVersion);
    }
}
