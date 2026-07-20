/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.encoding.netty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Resolves the default cumulative-decompressed-bytes cap shared by
 * {@link ZipCompressionBuilder} and {@link ZipContentCodecBuilder}. The hardcoded fallback is
 * 64 MiB; operators can override it via the
 * {@value #MAX_DECOMPRESSED_BYTES_PROPERTY} system property when shipping a build that requires
 * a different bound (the typical use case is dialing the limit up to keep working through an
 * unexpected regression). The property is read once at class load.
 */
final class CompressionDefaults {

    /**
     * System property that, when set to a non-negative {@code long}, overrides the default cap
     * applied to {@link ZipCompressionBuilder#maxDecompressedBytes(long)} and
     * {@link ZipContentCodecBuilder#maxDecompressedBytes(long)}. {@code 0} disables the cap;
     * any other value is the cap in bytes. Invalid values are ignored with a warning, falling
     * back to the hardcoded 64 MiB default.
     */
    static final String MAX_DECOMPRESSED_BYTES_PROPERTY = "io.servicetalk.encoding.netty.maxDecompressedBytes";
    static final long DEFAULT_MAX_DECOMPRESSED_BYTES = 64L << 20; //64MiB

    private static final Logger LOGGER = LoggerFactory.getLogger(CompressionDefaults.class);
    private static final long RESOLVED_MAX_DECOMPRESSED_BYTES =
            parseMaxDecompressedBytes(System.getProperty(MAX_DECOMPRESSED_BYTES_PROPERTY));

    static {
        LOGGER.debug("-D{}={}", MAX_DECOMPRESSED_BYTES_PROPERTY, RESOLVED_MAX_DECOMPRESSED_BYTES);
    }

    private CompressionDefaults() {
        // no instances
    }

    /**
     * Returns the configured default cap in bytes, or the hardcoded fallback if the property is
     * unset or invalid.
     */
    static long defaultMaxDecompressedBytes() {
        return RESOLVED_MAX_DECOMPRESSED_BYTES;
    }

    /**
     * Parses the system-property value into a cap. Package-private so unit tests can exercise
     * the parsing without manipulating system properties.
     */
    static long parseMaxDecompressedBytes(@Nullable final String rawValue) {
        if (rawValue == null) {
            return DEFAULT_MAX_DECOMPRESSED_BYTES;
        }
        final long parsed;
        try {
            parsed = Long.parseLong(rawValue.trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("Ignoring invalid {} value '{}'; using default {}",
                    MAX_DECOMPRESSED_BYTES_PROPERTY, rawValue, DEFAULT_MAX_DECOMPRESSED_BYTES);
            return DEFAULT_MAX_DECOMPRESSED_BYTES;
        }
        if (parsed < 0) {
            LOGGER.warn("Ignoring negative {} value {}; using default {}",
                    MAX_DECOMPRESSED_BYTES_PROPERTY, parsed, DEFAULT_MAX_DECOMPRESSED_BYTES);
            return DEFAULT_MAX_DECOMPRESSED_BYTES;
        }
        return parsed;
    }
}
