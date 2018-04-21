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

import io.servicetalk.buffer.Buffer;

import static io.servicetalk.buffer.ReadOnlyBufferAllocators.PREFER_DIRECT_ALLOCATOR;

final class DefaultHttpProtocolVersion implements HttpProtocolVersion {

    private final int major;
    private final int minor;
    private final Buffer httpVersion;

    DefaultHttpProtocolVersion(final int major, final int minor) {
        this.major = major;
        this.minor = minor;
        this.httpVersion = httpVersionToBuffer(major, minor);
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
    public Buffer getHttpVersion() {
        return httpVersion.duplicate();
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

    static Buffer httpVersionToBuffer(int major, int minor) {
        return PREFER_DIRECT_ALLOCATOR.fromAscii("HTTP/" + major + "." + minor);
    }
}
