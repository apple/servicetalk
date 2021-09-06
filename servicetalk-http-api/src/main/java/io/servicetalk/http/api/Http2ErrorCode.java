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

import javax.annotation.Nullable;

/**
 * An enum containing http/2 <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">error codes</a>.
 */
public enum Http2ErrorCode {
    NO_ERROR(0x0),
    PROTOCOL_ERROR(0x1),
    INTERNAL_ERROR(0x2),
    FLOW_CONTROL_ERROR(0x3),
    SETTINGS_TIMEOUT(0x4),
    STREAM_CLOSED(0x5),
    FRAME_SIZE_ERROR(0x6),
    REFUSED_STREAM(0x7),
    CANCEL(0x8),
    COMPRESSION_ERROR(0x9),
    CONNECT_ERROR(0xA),
    ENHANCE_YOUR_CALM(0xB),
    INADEQUATE_SECURITY(0xC),
    HTTP_1_1_REQUIRED(0xD);

    private final long errorCode;
    private static final Http2ErrorCode[] INT_TO_ENUM_MAP;
    static {
        Http2ErrorCode[] errors = Http2ErrorCode.values();
        Http2ErrorCode[] map = new Http2ErrorCode[errors.length];
        for (Http2ErrorCode error : errors) {
            map[(int) error.errorCode()] = error;
        }
        INT_TO_ENUM_MAP = map;
    }

    Http2ErrorCode(long errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * Get the decimal value of the <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">error code</a>.
     * @return the decimal value of the <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">
     * error code</a>.
     */
    public long errorCode() {
        return errorCode;
    }

    /**
     * Convert from the decimal value of <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">
     * error code</a> into a {@link Http2ErrorCode}.
     * @param errorCode numeric value of
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">error code</a>.
     * @return {@link Http2ErrorCode} representing {@code errorCode}, or {@code null} if no mapping exists.
     */
    @Nullable
    public static Http2ErrorCode valueOf(long errorCode) {
        return errorCode >= INT_TO_ENUM_MAP.length || errorCode < 0 ? null : INT_TO_ENUM_MAP[(int) errorCode];
    }
}
