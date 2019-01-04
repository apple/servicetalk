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
package io.servicetalk.redis.netty;

import java.util.concurrent.ThreadLocalRandom;

final class RedisTestUtils {

    private RedisTestUtils() {
        // No instances.
    }

    static CharSequence randomStringOfLength(int lengthInUtf8Bytes) {
        if (lengthInUtf8Bytes == 0) {
            return "";
        }

        int oneByteCount = lengthInUtf8Bytes % 2;
        int twoBytesCount = lengthInUtf8Bytes / 2;
        char[] c = new char[oneByteCount + twoBytesCount];
        int j = 0;
        for (int i = 0; i < oneByteCount; i++) {
            c[j++] = (char) ThreadLocalRandom.current().nextInt(0, 128);
        }
        for (int i = 0; i < twoBytesCount; i++) {
            c[j++] = (char) ThreadLocalRandom.current().nextInt(128, 2048);
        }

        return new String(c);
    }
}
