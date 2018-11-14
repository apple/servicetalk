/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.redis.internal;

import java.util.List;

import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.ByteOrder.nativeOrder;

/**
 * A set of utility functions for redis.
 */
public final class RedisUtils {

    private static final boolean BIG_ENDIAN_NATIVE_ORDER = nativeOrder() == BIG_ENDIAN;
    public static final short EOL_SHORT = makeShort('\r', '\n');
    public static final int EOL_LENGTH = 2;

    private RedisUtils() {
        // no instances
    }

    /**
     * Returns a {@code short} value using endian order.
     *
     * @param first the first {@code char} to include in the {@code short}.
     * @param second the second {@code char} to include in the {@code short}.
     * @return a {@code short}.
     */
    public static short makeShort(char first, char second) {
        return BIG_ENDIAN_NATIVE_ORDER ?
                (short) ((second << 8) | first) : (short) ((first << 8) | second);
    }

    /**
     * Marker class which indicates that Redis response should be coerced to the {@link List} of {@link CharSequence}s.
     */
    public static final class ListWithBuffersCoercedToCharSequences {
        private ListWithBuffersCoercedToCharSequences() {
            // no instantiation
        }
    }
}
