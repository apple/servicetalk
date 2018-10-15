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
package io.servicetalk.examples.http.service.composition.backends;

import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.ThreadLocalRandom.current;

/**
 * A utility class for {@link String}s.
 */
final class StringUtils {

    private StringUtils() {
        // No instances.
    }

    /**
     * Creates a new {@link String} of random characters (from {@code a} to {@code z}) with specified length.
     *
     * @param length the length of a new {@link String}
     * @return a new {@link String} of random characters with specified length
     */
    static String randomString(int length) {
        final ThreadLocalRandom random = current();
        char[] randomChars = new char[length];
        for (int i = 0; i < length; i++) {
            randomChars[i] = (char) random.nextInt('a', 'z' + 1);
        }
        return new String(randomChars);
    }
}
