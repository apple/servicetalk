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

import static io.servicetalk.buffer.ReadOnlyBufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.toStatusClass;
import static java.util.Objects.requireNonNull;

final class DefaultHttpResponseStatus implements HttpResponseStatus {

    private final int statusCode;
    private final Buffer reasonPhrase;
    private final Buffer statusCodeBuffer;
    private final StatusClass statusClass;

    DefaultHttpResponseStatus(final int statusCode, final Buffer reasonPhrase) {
        this(statusCode, reasonPhrase, toStatusClass(statusCode));
    }

    DefaultHttpResponseStatus(final int statusCode, final Buffer reasonPhrase, final StatusClass statusClass) {
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
        this.statusClass = requireNonNull(statusClass);
        this.statusCodeBuffer = statusCodeToBuffer(statusCode);
    }

    @Override
    public int getCode() {
        return statusCode;
    }

    @Override
    public Buffer getCodeBuffer() {
        return statusCodeBuffer.duplicate();
    }

    @Override
    public Buffer getReasonPhrase() {
        return reasonPhrase;
    }

    @Override
    public StatusClass getStatusClass() {
        return statusClass;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultHttpResponseStatus that = (DefaultHttpResponseStatus) o;

        /*
         * reasonPhrase is ignored for equals/hashCode because the RFC says:
         *   A client SHOULD ignore the reason-phrase content.
         * https://tools.ietf.org/html/rfc7230#section-3.1.2
         */
        return statusCode == that.statusCode && statusClass.equals(that.statusClass);
    }

    @Override
    public int hashCode() {
        return 31 * statusCode + statusClass.hashCode();
    }

    static Buffer statusCodeToBuffer(int status) {
        return DEFAULT_ALLOCATOR.fromAscii(String.valueOf(status));
    }
}
