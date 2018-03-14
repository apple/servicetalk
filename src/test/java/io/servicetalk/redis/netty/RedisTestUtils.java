/**
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

import static java.nio.charset.StandardCharsets.UTF_8;

final class RedisTestUtils {

    private RedisTestUtils() {
        // No instances.
    }

    static CharSequence randomStringOfLength(int lengthInBytes) {
        byte[] b = new byte[lengthInBytes];
        ThreadLocalRandom.current().nextBytes(b);
        // While the bytes may not be valid UTF-8, the String constructor is lenient, and
        // "always replaces malformed-input and unmappable-character sequences with this charset's default replacement string."
        return new String(b, UTF_8);
    }
}
