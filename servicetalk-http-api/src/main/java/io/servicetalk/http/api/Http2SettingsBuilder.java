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

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * Builder to help create a {@link Map} for
 * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.1">HTTP/2 Setting</a>.
 */
public final class Http2SettingsBuilder {
    private static final long MAX_UNSIGNED_INT = 0xffffffffL;
    private static final int MAX_FRAME_SIZE_LOWER_BOUND = 0x4000;
    private static final int MAX_FRAME_SIZE_UPPER_BOUND = 0xffffff;
    /**
     * Identifier <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">SETTINGS_HEADER_TABLE_SIZE</a>.
     */
    private static final char HEADER_TABLE_SIZE = 0x1;
    /**
     * Identifier <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">
     *     SETTINGS_MAX_CONCURRENT_STREAMS</a>.
     */
    private static final char MAX_CONCURRENT_STREAMS = 0x3;
    /**
     * Identifier <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">
     *     SETTINGS_INITIAL_WINDOW_SIZE</a>.
     */
    private static final char INITIAL_WINDOW_SIZE = 0x4;
    /**
     * Identifier <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">SETTINGS_MAX_FRAME_SIZE</a>.
     */
    private static final char MAX_FRAME_SIZE = 0x5;
    /**
     * Identifier <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">
     *     SETTINGS_MAX_HEADER_LIST_SIZE</a>.
     */
    private static final char MAX_HEADER_LIST_SIZE = 0x6;
    private final Map<Character, Long> settings;

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
    public Http2SettingsBuilder headerTableSize(long value) {
        validate32Unsigned(value);
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
    public Http2SettingsBuilder maxConcurrentStreams(long value) {
        validate32Unsigned(value);
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
    public Http2SettingsBuilder initialWindowSize(long value) {
        validate31Unsigned(value);
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
        if (value < MAX_FRAME_SIZE_LOWER_BOUND || value > MAX_FRAME_SIZE_UPPER_BOUND) {
            throw new IllegalArgumentException("value: " + value + "(expected [" + MAX_FRAME_SIZE_LOWER_BOUND + ", " +
                    MAX_FRAME_SIZE_UPPER_BOUND + "]");
        }
        settings.put(MAX_FRAME_SIZE, (long) value);
        return this;
    }

    /**
     * Set the value for
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">SETTINGS_MAX_HEADER_LIST_SIZE</a>.
     *
     * @param value The value.
     * @return {@code this}.
     */
    public Http2SettingsBuilder maxHeaderListSize(long value) {
        validate32Unsigned(value);
        settings.put(MAX_HEADER_LIST_SIZE, value);
        return this;
    }

    /**
     * Build the {@link Map} that represents
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.1">HTTP/2 Setting</a>.
     * @return the {@link Map} that represents
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.1">HTTP/2 Setting</a>.
     */
    public Http2Settings build() {
        return new DefaultHttp2Settings(new HashMap<>(settings));
    }

    private static void validate32Unsigned(long value) {
        if (value < 0 || value >= MAX_UNSIGNED_INT) {
            throw new IllegalArgumentException("value: " + value + "(expected [0, " + MAX_UNSIGNED_INT + "]");
        }
    }

    private static void validate31Unsigned(long value) {
        if (value < 0 || value >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("value: " + value + "(expected [0, " + Integer.MAX_VALUE + "]");
        }
    }

    private static final class DefaultHttp2Settings implements Http2Settings {
        private final Map<Character, Long> settings;

        private DefaultHttp2Settings(final Map<Character, Long> settings) {
            this.settings = settings;
        }

        @Nullable
        @Override
        public Long headerTableSize() {
            return settings.get(HEADER_TABLE_SIZE);
        }

        @Nullable
        @Override
        public Long maxConcurrentStreams() {
            return settings.get(MAX_CONCURRENT_STREAMS);
        }

        @Nullable
        @Override
        public Integer initialWindowSize() {
            return toInteger(settings.get(INITIAL_WINDOW_SIZE));
        }

        @Nullable
        @Override
        public Integer maxFrameSize() {
            return toInteger(settings.get(MAX_FRAME_SIZE));
        }

        @Nullable
        @Override
        public Long maxHeaderListSize() {
            return settings.get(MAX_HEADER_LIST_SIZE);
        }

        @Nullable
        @Override
        public Long settingValue(final char identifier) {
            return settings.get(identifier);
        }

        @Override
        public void forEach(final BiConsumer<? super Character, ? super Long> action) {
            settings.forEach(action);
        }

        @Override
        public String toString() {
            return settings.toString();
        }

        @Override
        public int hashCode() {
            return settings.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof DefaultHttp2Settings && settings.equals(((DefaultHttp2Settings) o).settings);
        }

        @Nullable
        private static Integer toInteger(@Nullable Long value) {
            return value == null ? null : value.intValue();
        }
    }
}
