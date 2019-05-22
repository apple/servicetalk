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

import static io.servicetalk.http.api.HeaderUtils.isTransferEncodingChunked;

final class DefaultPayloadInfo implements PayloadInfo {

    private static final byte SAFE_TO_AGGREGATE = 1;
    private static final byte MAY_HAVE_TRAILERS = 2;
    private static final byte ONLY_EMIT_BUFFERS = 4;

    private byte flags;

    DefaultPayloadInfo() {
    }

    DefaultPayloadInfo(PayloadInfo from) {
        if (from instanceof DefaultPayloadInfo) {
            this.flags = ((DefaultPayloadInfo) from).flags;
        } else {
            setSafeToAggregate(from.isSafeToAggregate());
            setMayHaveTrailers(from.mayHaveTrailers());
            setOnlyEmitsBuffer(from.onlyEmitsBuffer());
        }
    }

    @Override
    public boolean isSafeToAggregate() {
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

    DefaultPayloadInfo setSafeToAggregate(boolean safeToAggregate) {
        return set(SAFE_TO_AGGREGATE, safeToAggregate);
    }

    DefaultPayloadInfo setMayHaveTrailers(boolean mayHaveTrailers) {
        return set(MAY_HAVE_TRAILERS, mayHaveTrailers);
    }

    DefaultPayloadInfo setOnlyEmitsBuffer(boolean onlyEmitsBuffer) {
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
        return newInfoUsingHeaders(httpHeaders).setOnlyEmitsBuffer(true);
    }

    private static DefaultPayloadInfo newInfoUsingHeaders(final HttpHeaders httpHeaders) {
        return isTransferEncodingChunked(httpHeaders) ? new DefaultPayloadInfo().setMayHaveTrailers(true) :
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultPayloadInfo that = (DefaultPayloadInfo) o;

        return flags == that.flags;
    }

    @Override
    public int hashCode() {
        return (int) flags;
    }

    @Override
    public String toString() {
        return "DefaultPayloadInfo{" +
                "flags=" + flags +
                '}';
    }
}
