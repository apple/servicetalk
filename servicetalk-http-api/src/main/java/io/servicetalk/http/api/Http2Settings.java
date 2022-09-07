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

import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * Object representing a <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.1">HTTP/2 Setting</a> frame.
 */
public interface Http2Settings {
    /**
     * Get the value for
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">SETTINGS_HEADER_TABLE_SIZE</a>.
     * @return the value for
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">SETTINGS_HEADER_TABLE_SIZE</a>.
     */
    @Nullable
    Long headerTableSize();

    /**
     * Get the value for
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">SETTINGS_MAX_CONCURRENT_STREAMS</a>.
     * @return the value for
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">SETTINGS_MAX_CONCURRENT_STREAMS</a>.
     */
    @Nullable
    Long maxConcurrentStreams();

    /**
     * Get the value for
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">SETTINGS_INITIAL_WINDOW_SIZE</a>.
     * @return the value for
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">SETTINGS_INITIAL_WINDOW_SIZE</a>.
     */
    @Nullable
    Integer initialWindowSize();

    /**
     * Get the value for
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">SETTINGS_MAX_FRAME_SIZE</a>.
     * @return the value for
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">SETTINGS_MAX_FRAME_SIZE</a>.
     */
    @Nullable
    Integer maxFrameSize();

    /**
     * Get the value for
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">SETTINGS_MAX_HEADER_LIST_SIZE</a>.
     * @return the value for
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.2">SETTINGS_MAX_HEADER_LIST_SIZE</a>.
     */
    @Nullable
    Long maxHeaderListSize();

    /**
     * Get the setting value associated with an
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.1">identifier</a>.
     * @param identifier the <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.1">identifier</a>.
     * @return {@code null} if no setting value corresponding {@code identifier} exists, otherwise the value.
     */
    @Nullable
    Long settingValue(char identifier);

    /**
     * Iterate over all the &lt;identifier, value&gt; tuple in this settings object.
     * @param action Invoked on each &lt;identifier, value&gt; tuple.
     */
    void forEach(BiConsumer<? super Character, ? super Long> action);
}
