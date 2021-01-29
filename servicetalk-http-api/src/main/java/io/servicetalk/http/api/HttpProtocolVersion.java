/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.api.ConnectionInfo.Protocol;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.PREFER_HEAP_RO_ALLOCATOR;
import static io.servicetalk.http.api.BufferUtils.writeReadOnlyBuffer;

/**
 * HTTP <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">protocol versioning</a>.
 */
public final class HttpProtocolVersion implements Protocol {
    /**
     * HTTP/1.1 version described in <a href="https://tools.ietf.org/html/rfc7230">RFC 7230</a>.
     */
    public static final HttpProtocolVersion HTTP_1_1 = new HttpProtocolVersion(1, 1);

    /**
     * HTTP/1.0 version described in <a href="https://tools.ietf.org/html/rfc1945">RFC 1945</a>.
     */
    public static final HttpProtocolVersion HTTP_1_0 = new HttpProtocolVersion(1, 0);

    /**
     * HTTP/2.0 version described in <a href="https://tools.ietf.org/html/rfc7540">RFC 7540</a>.
     */
    public static final HttpProtocolVersion HTTP_2_0 = new HttpProtocolVersion(2, 0);

    private final int major;
    private final int minor;
    private final String httpVersion;
    private final Buffer encodedAsBuffer;

    private HttpProtocolVersion(final int major, final int minor) {
        if (major < 0 || major > 9) {
            throw new IllegalArgumentException("Illegal major version: " + major + ", expected [0-9]");
        }
        this.major = major;

        if (minor < 0 || minor > 9) {
            throw new IllegalArgumentException("Illegal minor version: " + minor + ", expected [0-9]");
        }
        this.minor = minor;

        httpVersion = "HTTP/" + major + '.' + minor;
        encodedAsBuffer = PREFER_HEAP_RO_ALLOCATOR.fromAscii(httpVersion);
    }

    /**
     * Returns an {@link HttpProtocolVersion} for the specified {@code major} and {@code minor}.
     * Generally, the constants in {@link HttpProtocolVersion} should be used.
     *
     * @param major the <strong>&lt;major&gt;</strong> portion of the
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">HTTP protocol version</a>
     * @param minor the <strong>&lt;minor&gt;</strong> portion of the
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">HTTP protocol version</a>
     * @return an {@link HttpProtocolVersion}
     * @throws IllegalArgumentException if {@code major} or {@code minor} is not a 1-digit integer
     */
    public static HttpProtocolVersion of(final int major, final int minor) {
        if (major == 1) {
            if (minor == 1) {
                return HTTP_1_1;
            }
            if (minor == 0) {
                return HTTP_1_0;
            }
        } else if (major == 2 && minor == 0) {
            return HTTP_2_0;
        }
        return new HttpProtocolVersion(major, minor);
    }

    /**
     * Get the <strong>&lt;major&gt;</strong> portion of the
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">http protocol version</a>.
     *
     * @return the <strong>&lt;major&gt;</strong> portion of the
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">http protocol version</a>
     */
    public int major() {
        return major;
    }

    /**
     * Get the <strong>&lt;minor&gt;</strong> portion of the
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">http protocol version</a>.
     *
     * @return the <strong>&lt;minor&gt;</strong> portion of the
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">http protocol version</a>
     */
    public int minor() {
        return minor;
    }

    /**
     * Write the equivalent of this {@link HttpProtocolVersion} to a {@link Buffer}.
     *
     * @param buffer the {@link Buffer} to write to
     */
    public void writeTo(final Buffer buffer) {
        writeReadOnlyBuffer(encodedAsBuffer, buffer);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HttpProtocolVersion)) {
            return false;
        }

        final HttpProtocolVersion that = (HttpProtocolVersion) o;
        /*
         * - httpVersion Buffer is ignored for equals/hashCode because it is derived from major & minor and the
         *   relationship is idempotent
         */
        return major == that.major() && minor == that.minor();
    }

    @Override
    public int hashCode() {
        return 31 * major + minor;
    }

    @Override
    public String toString() {
        return httpVersion;
    }

    @Override
    public String name() {
        return httpVersion;
    }

    /**
     * Determine if the protocol version is {@link #major()} is {@code 1} and trailers are supported.
     * @param version The version to check.
     * @return {@code true} if the protocol version is {@link #major()} is {@code 1} and trailers are supported.
     */
    static boolean h1TrailersSupported(HttpProtocolVersion version) {
        return version.major() == 1 && version.minor() > 0;
    }
}
