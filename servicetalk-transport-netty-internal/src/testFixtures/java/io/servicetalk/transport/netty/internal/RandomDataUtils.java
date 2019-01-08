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
package io.servicetalk.transport.netty.internal;

import java.util.concurrent.ThreadLocalRandom;

import static java.lang.Character.isSurrogate;

public final class RandomDataUtils {

    private RandomDataUtils() {
        // No instances.
    }

    /**
     * Generates a new random {@link CharSequence} whose UTF-8 encoding is of the specified size.
     *
     * @param lengthInUtf8Bytes the desired size
     * @return a new {@link CharSequence}
     */
    public static CharSequence randomCharSequenceOfByteLength(final int lengthInUtf8Bytes) {
        if (lengthInUtf8Bytes == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder(lengthInUtf8Bytes / 2);
        int bytesLeft = lengthInUtf8Bytes;

        while (bytesLeft > 0) {
            if (bytesLeft >= 4) {
                sb.appendCodePoint(ThreadLocalRandom.current().nextInt(0x10000, 0x10FFFF + 1));
                bytesLeft -= 4;
            }
            if (bytesLeft >= 3) {
                char c = (char) ThreadLocalRandom.current().nextInt(0x800, 0xFFFF + 1);
                if (isSurrogate(c)) {
                    // surrogates utf-8 encode to 1 byte
                    continue;
                }
                sb.append(c);
                bytesLeft -= 3;
            }
            if (bytesLeft >= 2) {
                sb.append((char) ThreadLocalRandom.current().nextInt(0x80, 0x7FF + 1));
                bytesLeft -= 2;
            }
            if (bytesLeft >= 1) {
                sb.append((char) ThreadLocalRandom.current().nextInt(0, 0x7F + 1));
                bytesLeft--;
            }
        }

        return sb.toString();
    }
}
