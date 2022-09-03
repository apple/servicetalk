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
package io.servicetalk.http.netty;

import java.util.HashMap;
import java.util.Map;

import static io.servicetalk.http.api.Http2Settings.HEADER_TABLE_SIZE;
import static io.servicetalk.http.api.Http2Settings.INITIAL_WINDOW_SIZE;
import static io.servicetalk.http.api.Http2Settings.MAX_CONCURRENT_STREAMS;
import static io.servicetalk.http.api.Http2Settings.MAX_FRAME_SIZE;
import static io.servicetalk.http.api.Http2Settings.MAX_HEADER_LIST_SIZE;

/**
 * Builder to help create a {@link Map} for
 * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.1">HTTP/2 Setting</a>.
 */
public final class Http2SettingsBuilder {
    private final Map<Character, Integer> settings;

    /**
     * Create a new instance.
     */
    public Http2SettingsBuilder() {
        this(8);
    }

    /**
     * Create a new instance.
     * @param initialSize The initial size of the map.
     */
    Http2SettingsBuilder(int initialSize) {
        settings = new HashMap<>(initialSize);
    }

    /**
     * Set the value for
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">SETTINGS_HEADER_TABLE_SIZE</a>.
     *
     * @param value The value.
     * @return {@code this}.
     */
    public Http2SettingsBuilder headerTableSize(int value) {
        settings.put(HEADER_TABLE_SIZE, value);
        return this;
    }

    /**
     * Set the value for
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">SETTINGS_MAX_CONCURRENT_STREAMS</a>.
     *
     * @param value The value.
     * @return {@code this}.
     */
    public Http2SettingsBuilder maxConcurrentStreams(int value) {
        settings.put(MAX_CONCURRENT_STREAMS, value);
        return this;
    }

    /**
     * Set the value for
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">SETTINGS_INITIAL_WINDOW_SIZE</a>.
     *
     * @param value The value.
     * @return {@code this}.
     */
    public Http2SettingsBuilder initialWindowSize(int value) {
        settings.put(INITIAL_WINDOW_SIZE, value);
        return this;
    }

    /**
     * Set the value for
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">SETTINGS_MAX_FRAME_SIZE</a>.
     *
     * @param value The value.
     * @return {@code this}.
     */
    public Http2SettingsBuilder maxFrameSize(int value) {
        settings.put(MAX_FRAME_SIZE, value);
        return this;
    }

    /**
     * Set the value for
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">SETTINGS_MAX_HEADER_LIST_SIZE</a>.
     *
     * @param value The value.
     * @return {@code this}.
     */
    public Http2SettingsBuilder maxHeaderListSize(int value) {
        settings.put(MAX_HEADER_LIST_SIZE, value);
        return this;
    }

    /**
     * Build the {@link Map} that represents
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.1">HTTP/2 Setting</a>.
     * @return the {@link Map} that represents
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.1">HTTP/2 Setting</a>.
     */
    public Map<Character, Integer> build() {
        return settings;
    }
}
