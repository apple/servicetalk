/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

/**
 * Utilities for <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.1">HTTP/2 Setting</a>.
 */
public final class Http2Settings {
    /**
     * Identifier <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">SETTINGS_HEADER_TABLE_SIZE</a>.
     */
    public static final char HEADER_TABLE_SIZE = 0x1;

    /**
     * Identifier <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">SETTINGS_ENABLE_PUSH</a>.
     */
    public static final char ENABLE_PUSH = 0x2;

    /**
     * Identifier <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">
     *     SETTINGS_MAX_CONCURRENT_STREAMS</a>.
     */
    public static final char MAX_CONCURRENT_STREAMS = 0x3;

    /**
     * Identifier <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">
     *     SETTINGS_INITIAL_WINDOW_SIZE</a>.
     */
    public static final char INITIAL_WINDOW_SIZE = 0x4;

    /**
     * Identifier <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">SETTINGS_MAX_FRAME_SIZE</a>.
     */
    public static final char MAX_FRAME_SIZE = 0x5;

    /**
     * Identifier <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">
     *     SETTINGS_MAX_HEADER_LIST_SIZE</a>.
     */
    public static final char MAX_HEADER_LIST_SIZE = 0x6;

    private Http2Settings() {
    }
}
