/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

final class DefaultPayloadInfo implements PayloadInfo {

    private static final byte SAFE_TO_AGGREGATE = 1;
    private static final byte MAY_HAVE_TRAILERS = 2;
    private static final byte ONLY_EMIT_BUFFERS = 4;

    private byte flags;

    @Override
    public boolean safeToAggregate() {
        return isSet(SAFE_TO_AGGREGATE);
    }

    @Override
    public boolean mayHaveTrailers() {
        return isSet(MAY_HAVE_TRAILERS);
    }

    @Override
    public boolean onlyEmitsBuffer() {
        return isSet(ONLY_EMIT_BUFFERS);
    }

    DefaultPayloadInfo updateSafeToAggregate(boolean safeToAggregate) {
        return set(SAFE_TO_AGGREGATE, safeToAggregate);
    }

    DefaultPayloadInfo updateMayHaveTrailers(boolean mayHaveTrailers) {
        return set(MAY_HAVE_TRAILERS, mayHaveTrailers);
    }

    DefaultPayloadInfo updateOnlyEmitsBuffer(boolean onlyEmitsBuffer) {
        return set(ONLY_EMIT_BUFFERS, onlyEmitsBuffer);
    }

    /**
     * Construct a new {@link PayloadInfo} to represent an HTTP message read from the transport.
     *
     * @param httpHeaders {@link HttpHeaders} for an HTTP message read from the transport.
     * @return A new {@link PayloadInfo} representing an HTTP message read from the transport.
     */
    static DefaultPayloadInfo forTransportReceive(HttpHeaders httpHeaders) {
        return newInfoUsingHeaders(httpHeaders);
    }

    /**
     * Construct a new {@link PayloadInfo} to represent an HTTP message created by a user.
     *
     * @param httpHeaders {@link HttpHeaders} for an HTTP message created by a user.
     * @return A new {@link PayloadInfo} representing an HTTP message created by a user.
     */
    static DefaultPayloadInfo forUserCreated(HttpHeaders httpHeaders) {
        return newInfoUsingHeaders(httpHeaders).updateOnlyEmitsBuffer(true);
    }

    private static DefaultPayloadInfo newInfoUsingHeaders(final HttpHeaders httpHeaders) {
        return httpHeaders.contains(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED, true) ?
                new DefaultPayloadInfo().updateMayHaveTrailers(true) :
                new DefaultPayloadInfo();
    }

    private boolean isSet(byte expected) {
        return (flags & expected) == expected;
    }

    private DefaultPayloadInfo set(byte flag, boolean enabled) {
        if (enabled) {
            flags |= flag;
        } else {
            flags &= ~flag;
        }
        return this;
    }
}
