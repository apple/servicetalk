/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import java.io.IOException;

import static java.util.Objects.requireNonNull;

/**
 * An exception that represents a <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">http/2 error</a>.
 */
public class Http2Exception extends IOException {
    private static final long serialVersionUID = -7412275553620283540L;
    private final int streamId;
    private final Http2ErrorCode error;

    /**
     * Create a new instance.
     * @param streamId <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-5.1.1">Stream Identifier</a> the
     * exception relates to. {@code 0} for the connection stream or {@code > 0} for a non-connection stream.
     * @param error The error code.
     * @param message The detail message (which is saved for later retrieval by the {@link #getMessage()} method).
     */
    public Http2Exception(final int streamId, final Http2ErrorCode error, final String message) {
        super(message);
        this.streamId = validateStreamId(streamId);
        this.error = requireNonNull(error);
    }

    /**
     * Create a new instance.
     * @param streamId <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-5.1.1">Stream Identifier</a> the
     * exception relates to. {@code 0} for the connection stream or {@code > 0} for a non-connection stream.
     * @param error The error code.
     * @param cause The original cause which lead to this exception.
     */
    public Http2Exception(final int streamId, final Http2ErrorCode error, final Throwable cause) {
        super(cause);
        this.streamId = validateStreamId(streamId);
        this.error = requireNonNull(error);
    }

    /**
     * Create a new instance.
     * @param streamId <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-5.1.1">Stream Identifier</a> the
     * exception relates to. {@code 0} for the connection stream or {@code > 0} for a non-connection stream.
     * @param error The error code.
     * @param message The detail message (which is saved for later retrieval by the {@link #getMessage()} method).
     * @param cause The original cause which lead to this exception.
     */
    public Http2Exception(final int streamId, final Http2ErrorCode error, final String message, final Throwable cause) {
        super(message, cause);
        this.streamId = validateStreamId(streamId);
        this.error = requireNonNull(error);
    }

    /**
     * Get the error code which caused this exception.
     * @return the error code which caused this exception.
     */
    public final Http2ErrorCode errorCode() {
        return error;
    }

    /**
     * The <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-5.1.1">Stream Identifier</a> associated with
     * the exception.
     * @return {@code 0} for the connection stream, {@code > 0} for a non-connection stream, or {@code < 0} if unknown.
     */
    public final int streamId() {
        return streamId;
    }

    private static int validateStreamId(int streamId) {
        if (streamId < 0) {
            throw new IllegalArgumentException("streamId: " + streamId + "(expected >=0)");
        }
        return streamId;
    }
}
