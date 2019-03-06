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
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.fromStatusCode;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;

final class DefaultHttpResponseStatus implements HttpResponseStatus {

    private final int statusCode;
    private final Buffer statusCodeBuffer;
    private final Buffer reasonPhrase;
    private final StatusClass statusClass;

    DefaultHttpResponseStatus(final int statusCode, final Buffer reasonPhrase) {
        this.statusClass = fromStatusCode(statusCode);
        this.statusCode = statusCode;
        this.statusCodeBuffer = statusCodeToBuffer(statusCode);
        this.reasonPhrase = requireNonNull(reasonPhrase);
    }

    @Override
    public int code() {
        return statusCode;
    }

    @Override
    public void writeCodeTo(final Buffer buffer) {
        buffer.writeBytes(statusCodeBuffer, statusCodeBuffer.readerIndex(), statusCodeBuffer.readableBytes());
    }

    @Override
    public void writeReasonPhraseTo(final Buffer buffer) {
        buffer.writeBytes(reasonPhrase, reasonPhrase.readerIndex(), reasonPhrase.readableBytes());
    }

    @Override
    public StatusClass statusClass() {
        return statusClass;
    }

    @Override
    public String toString() {
        return statusCode + (reasonPhrase.readableBytes() > 0 ? ' ' + reasonPhrase.toString(US_ASCII) : "");
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HttpResponseStatus)) {
            return false;
        }

        final HttpResponseStatus that = (HttpResponseStatus) o;
        /*
         * - statusCodeBuffer is ignored for equals/hashCode because it is inherited from statusCode and the
         *   relationship is idempotent
         *
         * - reasonPhrase is ignored for equals/hashCode because the RFC says:
         *   A client SHOULD ignore the reason-phrase content.
         * https://tools.ietf.org/html/rfc7230#section-3.1.2
         *
         * - statusClass is ignored for equals/hashCode because it is inherited from statusCode and the relationship
         *   is idempotent
         */
        return statusCode == that.code();
    }

    @Override
    public int hashCode() {
        return 31 * statusCode;
    }

    boolean equalsReasonPhrase(final Buffer reasonPhrase) {
        return this.reasonPhrase.equals(reasonPhrase);
    }

    private static Buffer statusCodeToBuffer(int status) {
        return PREFER_DIRECT_RO_ALLOCATOR.fromAscii(String.valueOf(status));
    }
}
