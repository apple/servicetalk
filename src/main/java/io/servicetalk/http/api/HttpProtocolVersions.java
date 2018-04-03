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
 * Provides constant instances of {@link HttpProtocolVersion}, as well as a mechanism for creating new instances if the
 * existing constants are not sufficient.
 */
public final class HttpProtocolVersions {
    public static final HttpProtocolVersion HTTP_1_0 = new DefaultHttpProtocolVersion(1, 0);
    public static final HttpProtocolVersion HTTP_1_1 = new DefaultHttpProtocolVersion(1, 1);

    private HttpProtocolVersions() {
        // No instances.
    }

    /**
     * Get a {@link HttpProtocolVersion} for the specified {@code major} and {@code minor}. If the {@code major} and
     * {@code minor} match those of an existing constant, the constant will be returned, otherwise a new instance will
     * be returned.
     *
     * @param major the <strong>&lt;major&gt;</strong> portion of the
     *              <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">http protocol version</a>.
     * @param minor the <strong>&lt;minor&gt;</strong> portion of the
     *              <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">http protocol version</a>.
     * @return a {@link HttpProtocolVersion}.
     */
    public static HttpProtocolVersion getProtocolVersion(final int major, final int minor) {
        if (major == 1) {
            if (minor == 0) {
                return HTTP_1_0;
            }
            if (minor == 1) {
                return HTTP_1_1;
            }
        }
        return new DefaultHttpProtocolVersion(major, minor);
    }
}
