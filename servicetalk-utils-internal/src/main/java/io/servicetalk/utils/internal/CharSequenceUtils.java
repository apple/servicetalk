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
package io.servicetalk.utils.internal;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Character.toUpperCase;
import static java.util.Collections.emptyList;

public final class CharSequenceUtils {

    private CharSequenceUtils() {
        // No instances
    }

    /**
     * Split a given {@link CharSequence} to separate ones on the given {@code delimiter}.
     * The returned {@link CharSequence}s are created by invoking the {@link CharSequence#subSequence(int, int)} method
     * on the main one.
     *
     * This method has no support for regex.
     *
     * @param input The initial {@link CharSequence} to split, this experiences no side effects
     * @param delimiter The delimiter character
     * @return a {@link List} of {@link CharSequence} subsequences of the input with the separated values
     */
    public static List<CharSequence> split(final CharSequence input, final char delimiter) {
        if (input.length() == 0) {
            return emptyList();
        }
        int startIndex = 0;
        List<CharSequence> result = new ArrayList<>();
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == delimiter) {
                if ((i - startIndex) > 0) {
                    result.add(input.subSequence(startIndex, i));
                }

                startIndex = i + 1;
            }
        }

        if ((input.length() - startIndex) > 0) {
            result.add(input.subSequence(startIndex, input.length()));
        }
        return result;
    }

    public static boolean contentEqualsIgnoreCaseUnknownTypes(final CharSequence a, final CharSequence b) {
        for (int i = 0; i < a.length(); ++i) {
            if (!equalsIgnoreCase(a.charAt(i), b.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean equalsIgnoreCase(final char a, final char b) {
        return a == b || toLowerCase(a) == toLowerCase(b);
    }

    /**
     * Compare an unknown ascii character {@code a} with a known lowercase character {@code lowerCaseChar} in a case
     * insensitive manner.
     * @param a an unknown ascii character.
     * @param lowerCaseChar a known to be lowercase ascii character.
     * @return {@code true} if {@code a} and {@code lowerCaseChar} are case insensitive equal.
     */
    public static boolean equalsIgnoreCaseLower(final char a, final char lowerCaseChar) {
        return a == lowerCaseChar || toLowerCase(a) == lowerCaseChar;
    }

    private static char toLowerCase(final char c) {
        return isUpperCase(c) ? (char) (c + 32) : c;
    }

    private static boolean isUpperCase(final char value) {
        return value >= 'A' && value <= 'Z';
    }

    /**
     * This methods make regionMatches operation correctly for any chars in strings.
     *
     * @param cs         the {@code CharSequence} to be processed
     * @param ignoreCase specifies if case should be ignored.
     * @param csStart    the starting offset in the {@code cs} CharSequence
     * @param string     the {@code CharSequence} to compare.
     * @param start      the starting offset in the specified {@code string}.
     * @param length     the number of characters to compare.
     * @return {@code true} if the ranges of characters are equal, {@code false} otherwise.
     */
    public static boolean regionMatches(final CharSequence cs, final boolean ignoreCase, final int csStart,
                                 final CharSequence string, final int start, final int length) {
        if (cs instanceof String && string instanceof String) {
            return ((String) cs).regionMatches(ignoreCase, csStart, (String) string, start, length);
        }

        return regionMatchesCharSequences(cs, csStart, string, start, length,
                ignoreCase ? GeneralCaseInsensitiveCharEqualityComparator.INSTANCE :
                        DefaultCharEqualityComparator.INSTANCE);
    }

    private static boolean regionMatchesCharSequences(final CharSequence cs, final int csStart,
                                                      final CharSequence string, final int start, final int length,
                                                      final CharEqualityComparator charEqualityComparator) {
        if (csStart < 0 || length > cs.length() - csStart) {
            return false;
        }
        if (start < 0 || length > string.length() - start) {
            return false;
        }

        int csIndex = csStart;
        final int csEnd = csIndex + length;
        int stringIndex = start;

        while (csIndex < csEnd) {
            final char c1 = cs.charAt(csIndex++);
            final char c2 = string.charAt(stringIndex++);

            if (!charEqualityComparator.equals(c1, c2)) {
                return false;
            }
        }
        return true;
    }

    private interface CharEqualityComparator {
        boolean equals(char a, char b);
    }

    private static final class DefaultCharEqualityComparator implements CharEqualityComparator {
        static final DefaultCharEqualityComparator INSTANCE = new DefaultCharEqualityComparator();

        private DefaultCharEqualityComparator() {
            // No instances
        }

        @Override
        public boolean equals(final char a, final char b) {
            return a == b;
        }
    }

    private static final class GeneralCaseInsensitiveCharEqualityComparator implements CharEqualityComparator {
        static final GeneralCaseInsensitiveCharEqualityComparator
                INSTANCE = new GeneralCaseInsensitiveCharEqualityComparator();

        private GeneralCaseInsensitiveCharEqualityComparator() {
            // No instances
        }

        @Override
        public boolean equals(final char a, final char b) {
            //For motivation, why we need two checks, see comment in String#regionMatches
            return toUpperCase(a) == toUpperCase(b) || Character.toLowerCase(a) == Character.toLowerCase(b);
        }
    }

    public static boolean contentEqualsUnknownTypes(final CharSequence a, final CharSequence b) {
        for (int i = 0; i < a.length(); ++i) {
            if (a.charAt(i) != b.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
