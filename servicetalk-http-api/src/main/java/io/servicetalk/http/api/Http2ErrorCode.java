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

import static java.util.Objects.requireNonNull;

/**
 * Represents an <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">http/2 error code</a>.
 */
public final class Http2ErrorCode {
    /**
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">NO_ERROR</a>.
     */
    public static final Http2ErrorCode NO_ERROR = new Http2ErrorCode(0x0, "NO_ERROR");

    /**
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">PROTOCOL_ERROR</a>.
     */
    public static final Http2ErrorCode PROTOCOL_ERROR = new Http2ErrorCode(0x1, "PROTOCOL_ERROR");

    /**
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">INTERNAL_ERROR</a>.
     */
    public static final Http2ErrorCode INTERNAL_ERROR = new Http2ErrorCode(0x2, "INTERNAL_ERROR");

    /**
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">FLOW_CONTROL_ERROR</a>.
     */
    public static final Http2ErrorCode FLOW_CONTROL_ERROR = new Http2ErrorCode(0x3, "FLOW_CONTROL_ERROR");

    /**
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">SETTINGS_TIMEOUT</a>.
     */
    public static final Http2ErrorCode SETTINGS_TIMEOUT = new Http2ErrorCode(0x4, "SETTINGS_TIMEOUT");

    /**
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">STREAM_CLOSED</a>.
     */
    public static final Http2ErrorCode STREAM_CLOSED = new Http2ErrorCode(0x5, "STREAM_CLOSED");

    /**
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">FRAME_SIZE_ERROR</a>.
     */
    public static final Http2ErrorCode FRAME_SIZE_ERROR = new Http2ErrorCode(0x6, "FRAME_SIZE_ERROR");

    /**
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">REFUSED_STREAM</a>.
     */
    public static final Http2ErrorCode REFUSED_STREAM = new Http2ErrorCode(0x7, "REFUSED_STREAM");

    /**
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">CANCEL</a>.
     */
    public static final Http2ErrorCode CANCEL = new Http2ErrorCode(0x8, "CANCEL");

    /**
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">COMPRESSION_ERROR</a>.
     */
    public static final Http2ErrorCode COMPRESSION_ERROR = new Http2ErrorCode(0x9, "COMPRESSION_ERROR");

    /**
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">CONNECT_ERROR</a>.
     */
    public static final Http2ErrorCode CONNECT_ERROR = new Http2ErrorCode(0xA, "CONNECT_ERROR");

    /**
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">ENHANCE_YOUR_CALM</a>.
     */
    public static final Http2ErrorCode ENHANCE_YOUR_CALM = new Http2ErrorCode(0xB, "ENHANCE_YOUR_CALM");

    /**
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">INADEQUATE_SECURITY</a>.
     */
    public static final Http2ErrorCode INADEQUATE_SECURITY = new Http2ErrorCode(0xC, "INADEQUATE_SECURITY");

    /**
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">HTTP_1_1_REQUIRED</a>.
     */
    public static final Http2ErrorCode HTTP_1_1_REQUIRED = new Http2ErrorCode(0xD, "HTTP_1_1_REQUIRED");

    private final int errorCode;
    private final String name;
    private static final Http2ErrorCode[] INT_TO_ENUM_MAP;
    static {
        final Http2ErrorCode[] errors = new Http2ErrorCode[] {NO_ERROR, PROTOCOL_ERROR, INTERNAL_ERROR,
                FLOW_CONTROL_ERROR, SETTINGS_TIMEOUT, STREAM_CLOSED, FRAME_SIZE_ERROR, REFUSED_STREAM, CANCEL,
                COMPRESSION_ERROR, CONNECT_ERROR, ENHANCE_YOUR_CALM, INADEQUATE_SECURITY, HTTP_1_1_REQUIRED};
        final Http2ErrorCode[] map = new Http2ErrorCode[errors.length];
        for (Http2ErrorCode error : errors) {
            map[error.code()] = error;
        }
        INT_TO_ENUM_MAP = map;
    }

    private Http2ErrorCode(int errorCode, String name) {
        this.errorCode = errorCode;
        this.name = requireNonNull(name);
    }

    /**
     * Get the decimal value of the <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">error code</a>.
     * @return the decimal value of the <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">
     * error code</a>.
     */
    public int code() {
        return errorCode;
    }

    /**
     * Get the string description of the error code.
     * @return the string description of the error code.
     */
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int hashCode() {
        return errorCode;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Http2ErrorCode && errorCode == ((Http2ErrorCode) o).errorCode);
    }

    /**
     * Convert from the decimal value of <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">
     * error code</a> into a {@link Http2ErrorCode}.
     * @param errorCode numeric value of
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">error code</a>.
     * @return {@link Http2ErrorCode} representing {@code errorCode}, or {@code null} if no mapping exists.
     */
    @Nullable
    public static Http2ErrorCode of(int errorCode) {
        return errorCode >= INT_TO_ENUM_MAP.length || errorCode < 0 ? null : INT_TO_ENUM_MAP[errorCode];
    }

    /**
     * Returns a {@link Http2ErrorCode} for the specified {@code errorCode} and {@code name}.
     * @param errorCode numeric value of
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-7">error code</a>.
     * @param name The {@link String} name description for the error code.
     * @return a {@link Http2ErrorCode} for the specified {@code errorCode} and {@code name}.
     */
    public static Http2ErrorCode of(int errorCode, String name) {
        return new Http2ErrorCode(errorCode, name);
    }
}
