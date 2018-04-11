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
/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.servicetalk.http.api;

import io.servicetalk.buffer.ByteProcessor;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A string which has been encoded into a character encoding whose character always takes a single byte, similarly to
 * ASCII. Currently, it internally keeps its content in a {@link String}. It is often used in conjunction with
 * {@code HttpHeaders} that require a {@link CharSequence}.
 */
final class AsciiString implements CharSequence {

    private final int hash;
    private final String string;

    /**
     * Initialize this byte string based upon a {@link String}.
     */
    private AsciiString(final String string) {
        this.string = requireNonNull(string);
        this.hash = hashCode(string);
    }

    /**
     * Returns an {@link AsciiString} containing the given string and retains/caches the input
     * string for later use in {@link #toString()}.
     * Used for the constants (which already stored in the JVM's string table) and in cases
     * where the guaranteed use of the {@link #toString()} method.
     */
    public static AsciiString cached(final String string) {
        return new AsciiString(string);
    }

    @Override
    public int length() {
        return string.length();
    }

    @Override
    public char charAt(final int index) {
        return string.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return string.subSequence(start, end);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Provides a case-insensitive hash code for Ascii like byte strings.
     */
    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != AsciiString.class) {
            return false;
        }

        final AsciiString that = (AsciiString) o;

        return length() == that.length() && this.hashCode() == that.hashCode() && contentEquals(string, that.string);
    }

    /**
     * Iterates over the readable bytes of this buffer with the specified {@code processor} in ascending order.
     *
     * @return {@code -1} if the processor iterated to or beyond the end of the readable bytes.
     * The last-visited index If the {@link ByteProcessor#process(byte)} returned {@code false}.
     */
    public int forEachByte(final ByteProcessor visitor) {
        final int len = length();
        for (int i = 0; i < len; ++i) {
            if (!visitor.process((byte) charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns {@code true} if the content of both {@link CharSequence}'s are equals. This only supports 8-bit ASCII.
     */
    public static boolean contentEquals(@Nullable final CharSequence a, @Nullable final CharSequence b) {
        if (a == null || b == null) {
            return a == b;
        }

        if (a == b) {
            return true;
        }

        if (a.length() != b.length()) {
            return false;
        }
        for (int i = 0; i < a.length(); ++i) {
            if (a.charAt(i) != b.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} if both {@link CharSequence}'s are equals when ignore the case. This only supports 8-bit
     * ASCII.
     */
    public static boolean contentEqualsIgnoreCase(@Nullable final CharSequence a, @Nullable final CharSequence b) {
        if (a == null || b == null) {
            return a == b;
        }

        if (a == b) {
            return true;
        }

        if (a.length() != b.length()) {
            return false;
        }
        for (int i = 0; i < a.length(); ++i) {
            if (!equalsIgnoreCase(a.charAt(i), b.charAt(i))) {
                return false;
            }
        }
        return true;
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

    /**
     * Returns the case-insensitive hash code of the specified string. Note that this method uses the same hashing
     * algorithm with {@link #hashCode()} so that you can put both {@link AsciiString}s and arbitrary
     * {@link CharSequence}s into the same headers.
     */
    public static int hashCode(final CharSequence value) {
        int h = 0;
        final int end = value.length();
        for (int i = 0; i < end; ++i) {
            // masking with 0x1F reduces the number of overall bits that impact the hash code but makes the hash
            // code the same regardless of character case (upper case or lower case hash is the same).
            h = h * 31 + (value.charAt(i) & 0x1F);
        }
        return h;
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

    private static boolean equalsIgnoreCase(final char a, final char b) {
        return a == b || toLowerCase(a) == toLowerCase(b);
    }

    private static char toLowerCase(final char c) {
        return isUpperCase(c) ? (char) (c + 32) : c;
    }

    private static boolean isUpperCase(final char value) {
        return value >= 'A' && value <= 'Z';
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
            return Character.toUpperCase(a) == Character.toUpperCase(b) ||
                    Character.toLowerCase(a) == Character.toLowerCase(b);
        }
    }
}
