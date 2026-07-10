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
package io.servicetalk.serializer.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.Integer.getInteger;

/**
 * Shared defaults for the length-prefixed streaming serializers in this package.
 */
final class StreamingSerializerDefaults {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamingSerializerDefaults.class);

    static final int DEFAULT_MAX_MESSAGE_SIZE_VALUE = 4 * 1024 * 1024;
    // FIXME: 0.43 - remove this temporary property
    static final String DEFAULT_MAX_MESSAGE_SIZE_PROPERTY =
            "io.servicetalk.serializer.utils.temporaryDefaultMaxMessageSize";
    // The maximum length declared by a frame's length prefix that a 2-arg serializer accepts by default. A value of
    // 0 disables the limit; a negative property value is invalid and falls back to the hardcoded default.
    static final int DEFAULT_MAX_MESSAGE_SIZE;

    static {
        final int value = getInteger(DEFAULT_MAX_MESSAGE_SIZE_PROPERTY, DEFAULT_MAX_MESSAGE_SIZE_VALUE);
        // Mirror the validation applied by the serializer constructors (ensureNonNegative): a negative value is
        // invalid (0 disables the limit). Don't throw from this static initializer - fall back to the default so a
        // bad property can't break serializer construction (e.g. the static HttpSerializers instances).
        if (value < 0) {
            LOGGER.warn("-D{}: {} is invalid (expected >= 0, where 0 disables the limit). Falling back to the " +
                            "default of {} bytes.", DEFAULT_MAX_MESSAGE_SIZE_PROPERTY, value,
                    DEFAULT_MAX_MESSAGE_SIZE_VALUE);
            DEFAULT_MAX_MESSAGE_SIZE = DEFAULT_MAX_MESSAGE_SIZE_VALUE;
        } else {
            DEFAULT_MAX_MESSAGE_SIZE = value;
            if (value != DEFAULT_MAX_MESSAGE_SIZE_VALUE) {
                LOGGER.warn("-D{}: {}. This property is temporary and will be removed in a future release. Configure " +
                                "the limit per serializer via the 3-arg FixedLengthStreamingSerializer / " +
                                "VarIntLengthStreamingSerializer constructor instead.",
                        DEFAULT_MAX_MESSAGE_SIZE_PROPERTY, value);
            }
        }
    }

    private StreamingSerializerDefaults() {
    }
}
