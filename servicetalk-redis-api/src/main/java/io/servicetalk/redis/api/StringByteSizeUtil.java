/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Creative Commons Attribution Unported, Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://creativecommons.org/licenses/by-sa/3.0/us/legalcode
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 *
 * Derivative work of:
 * https://stackoverflow.com/questions/1306727/way-to-get-number-of-digits-in-an-int/1308407#1308407
 *   (The fastest approach: divide and conquer)
 *
 * Original Author:
 * Marian
 */
package io.servicetalk.redis.api;

import static java.lang.Character.isHighSurrogate;

/**
 * String byte size util.
 */
final class StringByteSizeUtil {
    private StringByteSizeUtil() {
        // no instances
    }

    /**
     * Number of bytes in UTF8.
     *
     * @param sequence string
     * @return number of bytes
     */
    static int numberOfBytesUtf8(final CharSequence sequence) {
        final int len = sequence.length();
        int count = len;
        for (int i = 0; i < len; ++i) {
            final char ch = sequence.charAt(i);
            if (ch <= 0x7F) {
                // len is initialized to sequence.length(), so nothing to do here.
            } else if (ch <= 0x7FF) {
                ++count;
            } else if (isHighSurrogate(ch)) {
                count += 3;
                ++i;
            } else {
                count += 2;
            }
        }
        return count;
    }

    /**
     * Number of digits.
     *
     * @param n int number
     * @return number of digits
     */
    static int numberOfDigits(final int n) {
        if (n == Integer.MIN_VALUE) {
            return 11;
        }
        return n < 0 ? numberOfDigitsPositive(-n) + 1 : numberOfDigitsPositive(n);
    }

    /**
     * Number of digits.
     *
     * @param n long number
     * @return number of digits
     */
    static int numberOfDigits(final long n) {
        if (n == Long.MIN_VALUE) {
            return 20;
        }
        return n < 0 ? numberOfDigitsPositive(-n) + 1 : numberOfDigitsPositive(n);
    }

    /**
     * Number of digits for positive numbers only.
     *
     * @param n int number
     * @return number of digits
     */
    static int numberOfDigitsPositive(final int n) {
        if (n < 100000) {
            // 5 or less
            if (n < 100) {
                // 1 or 2
                return n < 10 ? 1 : 2;
            }
            // 3 or 4 or 5
            if (n < 1000) {
                return 3;
            }
            // 4 or 5
            return n < 10000 ? 4 : 5;
        }
        // 6 or more
        if (n < 10000000) {
            // 6 or 7
            return n < 1000000 ? 6 : 7;
        }
        // 8 to 10
        if (n < 100000000) {
            return 8;
        }
        // 9 or 10
        return n < 1000000000 ? 9 : 10;
    }

    /**
     * Number of digits for positive numbers only.
     *
     * @param n long number
     * @return number of digits
     */
    static int numberOfDigitsPositive(final long n) {
        assert n >= 0;

        if (n < 10000L) {
            // from 1 to 4
            if (n < 100L) {
                // 1 or 2
                return n < 10L ? 1 : 2;
            }
            // 3 or 4
            return n < 1000L ? 3 : 4;
        }

        // from 5 to 20
        if (n < 1000000000000L) { // from 5 to 12
            if (n < 100000000L) { // from 5 to 8
                if (n < 1000000L) { // 5 or 6
                    return n < 100000L ? 5 : 6;
                }
                // 7 or 8
                return n < 10000000L ? 7 : 8;
            }
            // from 9 to 12
            if (n < 10000000000L) {
                // 9 or 10
                return n < 1000000000L ? 9 : 10;
            }
            // 11 or 12
            return n < 100000000000L ? 11 : 12;
        }

        // from 13 to (18 or 20)
        if (n < 10000000000000000L) {
            // from 13 to 16
            if (n < 100000000000000L) {
                // 13 or 14
                return n < 10000000000000L ? 13 : 14;
            }
            // 15 or 16
            return n < 1000000000000000L ? 15 : 16;
        }
        // from 17 to 20
        if (n < 1000000000000000000L) {
            // 17 or 18
            return n < 100000000000000000L ? 17 : 18;
        }
        // 10000000000000000000L is not a valid long.
        return 19;
    }
}
