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

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.PREFER_DIRECT_RO_ALLOCATOR;
import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * HTTP <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">protocol versioning</a>.
 */
public final class HttpProtocolVersion {

    /**
     * HTTP/1.1 version described in <a href="https://tools.ietf.org/html/rfc7230">RFC 7230</a>.
     */
    public static final HttpProtocolVersion HTTP_1_1 = new HttpProtocolVersion(1, 1);

    /**
     * HTTP/1.0 version described in <a href="https://tools.ietf.org/html/rfc1945">RFC 1945</a>.
     */
    public static final HttpProtocolVersion HTTP_1_0 = new HttpProtocolVersion(1, 0);

    private final int major;
    private final int minor;
    private final Buffer httpVersion;

    private HttpProtocolVersion(final int major, final int minor) {
        if (major < 0 || major > 9) {
            throw new IllegalArgumentException("Illegal major version: " + major + ", expected [0-9]");
        }
        this.major = major;

        if (minor < 0 || minor > 9) {
            throw new IllegalArgumentException("Illegal minor version: " + minor + ", expected [0-9]");
        }
        this.minor = minor;

        this.httpVersion = PREFER_DIRECT_RO_ALLOCATOR.fromAscii("HTTP/" + major + '.' + minor);
    }

    private HttpProtocolVersion(final Buffer httpVersion) {
        // We could delay the parsing of major/minor but this is currently used during decode to validate the
        // correct form of the request.
        if (httpVersion.readableBytes() < 8) {
            throw new IllegalArgumentException("Incorrect httpVersion: " + httpVersion.toString(US_ASCII) +
                    ". Too small, expected 8 or more bytes.");
        }

        if (httpVersion.getByte(httpVersion.readerIndex() + 6) != (byte) '.') {
            char ch = (char) httpVersion.getByte(httpVersion.readerIndex() + 6);
            throw new IllegalArgumentException("Incorrect httpVersion: " + httpVersion.toString(US_ASCII) +
                    ". Invalid character found '" + ch + "' at position 6 (expected '.')");
        }

        this.major = httpVersion.getByte(httpVersion.readerIndex() + 5) - '0';
        if (major < 0 || major > 9) {
            throw new IllegalArgumentException("Incorrect httpVersion: " + httpVersion.toString(US_ASCII) +
                    ". Illegal major version: " + major + ", (expected [0-9])");
        }

        this.minor = httpVersion.getByte(httpVersion.readerIndex() + 7) - '0';
        if (minor < 0 || minor > 9) {
            throw new IllegalArgumentException("Incorrect httpVersion: " + httpVersion.toString(US_ASCII) +
                    ". Illegal minor version: " + minor + ", (expected [0-9])");
        }

        this.httpVersion = httpVersion;
    }

    /**
     * Return an {@link HttpProtocolVersion} for the specified {@code major} and {@code minor}.
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
        return new HttpProtocolVersion(major, minor);
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
        return new HttpProtocolVersion(httpVersion);
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
     * Write the <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">HTTP-version</a> to {@code buffer}.
     *
     * @param buffer The {@link Buffer} to write
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">HTTP-version</a>
     */
    public void writeVersionTo(final Buffer buffer) {
        buffer.writeBytes(httpVersion, httpVersion.readerIndex(), httpVersion.readableBytes());
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
        return httpVersion.toString(US_ASCII);
    }
}
