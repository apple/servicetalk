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

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.PREFER_DIRECT_RO_ALLOCATOR;
import static java.nio.charset.StandardCharsets.US_ASCII;

final class DefaultHttpProtocolVersion implements HttpProtocolVersion {

    private final int major;
    private final int minor;
    private final Buffer httpVersion;

    DefaultHttpProtocolVersion(final int major, final int minor) {
        if (major < 0 || major > 9) {
            throw new IllegalArgumentException("Illegal major version: " + major + ", expected [0-9]");
        }
        this.major = major;

        if (minor < 0 || minor > 9) {
            throw new IllegalArgumentException("Illegal minor version: " + minor + ", expected [0-9]");
        }
        this.minor = minor;

        this.httpVersion = httpVersionToBuffer(major, minor);
    }

    DefaultHttpProtocolVersion(final Buffer httpVersion) {
        // We could delay the parsing of major/minor but this is currently used during decode to validate the
        // correct form of the request.
        if (httpVersion.getReadableBytes() < 8) {
            throw new IllegalArgumentException("Incorrect httpVersion: " + httpVersion.toString(US_ASCII) +
                    ". Too small, expected 8 or more bytes.");
        }
        if (httpVersion.getByte(httpVersion.getReaderIndex() + 6) != (byte) '.') {
            char ch = (char) httpVersion.getByte(httpVersion.getReaderIndex() + 6);
            throw new IllegalArgumentException("Incorrect httpVersion: " + httpVersion.toString(US_ASCII) +
                    ". Invalid character found '" + ch + "' at position 6 (expected '.')");
        }
        this.major = httpVersion.getByte(httpVersion.getReaderIndex() + 5) - '0';
        if (major < 0 || major > 9) {
            throw new IllegalArgumentException("Incorrect httpVersion: " + httpVersion.toString(US_ASCII) +
                    ". Illegal major version: " + major + ", (expected [0-9])");
        }
        this.minor = httpVersion.getByte(httpVersion.getReaderIndex() + 7) - '0';
        if (minor < 0 || minor > 9) {
            throw new IllegalArgumentException("Incorrect httpVersion: " + httpVersion.toString(US_ASCII) +
                    ". Illegal minor version: " + minor + ", (expected [0-9])");
        }
        this.httpVersion = httpVersion;
    }

    @Override
    public int getMajorVersion() {
        return major;
    }

    @Override
    public int getMinorVersion() {
        return minor;
    }

    @Override
    public void writeHttpVersionTo(final Buffer buffer) {
        buffer.writeBytes(httpVersion, httpVersion.getReaderIndex(), httpVersion.getReadableBytes());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultHttpProtocolVersion that = (DefaultHttpProtocolVersion) o;
        return major == that.major && minor == that.minor;
    }

    @Override
    public int hashCode() {
        return major * 31 + minor;
    }

    @Override
    public String toString() {
        return httpVersion.toString(US_ASCII);
    }

    static Buffer httpVersionToBuffer(int major, int minor) {
        return PREFER_DIRECT_RO_ALLOCATOR.fromAscii("HTTP/" + major + '.' + minor);
    }
}
