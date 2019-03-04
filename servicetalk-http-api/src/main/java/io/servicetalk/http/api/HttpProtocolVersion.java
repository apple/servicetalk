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
 * HTTP <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">protocol versioning</a>.
 *
 * @see HttpProtocolVersions
 */
public interface HttpProtocolVersion {
    /**
     * Get the <strong>&lt;major&gt;</strong> portion of the
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">http protocol version</a>.
     *
     * @return the <strong>&lt;major&gt;</strong> portion of the
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">http protocol version</a>
     */
    int majorVersion();

    /**
     * Get the <strong>&lt;minor&gt;</strong> portion of the
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">http protocol version</a>.
     *
     * @return the <strong>&lt;minor&gt;</strong> portion of the
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">http protocol version</a>
     */
    int minorVersion();

    /**
     * Write the <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">HTTP-version</a> to {@code buffer}.
     *
     * @param buffer The {@link Buffer} to write
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">HTTP-version</a>
     */
    void writeVersionTo(Buffer buffer);

    /**
     * Compares the specified object with this {@link HttpProtocolVersion} for equality.
     * <p>
     * Returns {@code true} if and only if the specified object is also a {@link HttpProtocolVersion}, and both objects
     * have the same {@link #majorVersion} and {@link #minorVersion()}.
     * This definition ensures that the equals method works properly across different implementations of the
     * {@link HttpProtocolVersion} interface.
     *
     * @param o the object to be compared for equality with this {@link HttpProtocolVersion}
     * @return {@code true} if the specified object is equal to this {@link HttpProtocolVersion}
     */
    @Override
    boolean equals(Object o);

    /**
     * Returns the hash code value for this {@link HttpProtocolVersion}.
     * <p>
     * The hash code of an {@link HttpProtocolVersion} MUST be consistent with {@link #equals(Object)} implementation
     * and is defined to be the result of the following calculation:
     * <pre>{@code
     *     public int hashCode() {
     *         return 31 * majorVersion() + minorVersion();
     *     }
     * }</pre>
     * This ensures that {@code version1.equals(version2)} implies that
     * {@code version2.hashCode() == status2.hashCode()} for any two {@link HttpProtocolVersion}s, {@code version1} and
     * {@code version2}, as required by the general contract of {@link Object#hashCode}.
     *
     * @return the hash code value for this {@link HttpResponseStatus}
     * @see Object#equals(Object)
     * @see #equals(Object)
     */
    @Override
    int hashCode();
}
