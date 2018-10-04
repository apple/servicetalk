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

import static io.servicetalk.http.api.DefaultHttpProtocolVersion.httpVersionToBuffer;
import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * Provides constant instances of {@link HttpProtocolVersion}, as well as a mechanism for creating new instances if the
 * existing constants are not sufficient.
 */
public enum HttpProtocolVersions implements HttpProtocolVersion {
    HTTP_1_0(1, 0),
    HTTP_1_1(1, 1);

    private final int major;
    private final int minor;
    private final Buffer httpVersion;
    private final String httpVersionString;

    HttpProtocolVersions(int major, int minor) {
        this.major = major;
        this.minor = minor;
        httpVersion = httpVersionToBuffer(major, minor);
        httpVersionString = httpVersion.toString(US_ASCII);
    }

    /**
     * Create a new {@link HttpProtocolVersion} for the specified {@code major} and {@code minor}.
     *
     * @param major the <strong>&lt;major&gt;</strong> portion of the
     *              <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">HTTP protocol version</a>.
     * @param minor the <strong>&lt;minor&gt;</strong> portion of the
     *              <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">HTTP protocol version</a>.
     * @return a new {@link HttpProtocolVersion}.
     */
    public static HttpProtocolVersion newProtocolVersion(final int major, final int minor) {
        return new DefaultHttpProtocolVersion(major, minor);
    }

    /**
     * Create a new {@link HttpProtocolVersion} from its {@link Buffer} representation. The passed {@link Buffer} will
     * be parsed to extract {@code major} and {@code minor} components of the version.
     *
     * @param httpVersion a {@link Buffer} representation of the
     *       <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">HTTP protocol version</a>
     * @return a new {@link HttpProtocolVersion}.
     */
    public static HttpProtocolVersion newProtocolVersion(final Buffer httpVersion) {
        return new DefaultHttpProtocolVersion(httpVersion);
    }

    @Override
    public int majorVersion() {
        return major;
    }

    @Override
    public int minorVersion() {
        return minor;
    }

    @Override
    public void writeHttpVersionTo(final Buffer buffer) {
        buffer.writeBytes(httpVersion, httpVersion.readerIndex(), httpVersion.readableBytes());
    }

    @Override
    public String toString() {
        return httpVersionString;
    }
}
