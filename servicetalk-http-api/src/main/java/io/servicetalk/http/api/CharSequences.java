/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;

import java.util.List;
import javax.annotation.Nullable;

/**
 * Provides factory methods for creating {@link CharSequence} implementations.
 *
 * @deprecated This class is deprecated and will be removed in a future release.
 * For a replacement, see {@link io.servicetalk.buffer.api.CharSequences}.
 */
@Deprecated
public final class CharSequences {

    private CharSequences() {
        // No instances
    }

    /**
     * Create a new {@link CharSequence} from the specified {@code input}, supporting only 8-bit ASCII characters, and
     * with a case-insensitive {@code hashCode}.
     *
     * @param input a string containing only 8-bit ASCII characters.
     * @return a {@link CharSequence}
     */
    public static CharSequence newAsciiString(final String input) {
        return io.servicetalk.buffer.api.CharSequences.newAsciiString(input);
    }

    /**
     * Create a new {@link CharSequence} from the specified {@code input}, supporting only 8-bit ASCII characters, and
     * with a case-insensitive {@code hashCode}.
     *
     * @param input a {@link Buffer} containing
     * @return a {@link CharSequence}.
     */
    public static CharSequence newAsciiString(final Buffer input) {
        return io.servicetalk.buffer.api.CharSequences.newAsciiString(input);
    }

    /**
     * Get a reference to an unmodifiable empty {@link CharSequence} with the same properties as
     * {@link #newAsciiString(Buffer)}.
     * @return a reference to an unmodifiable empty {@link CharSequence} with the same properties as
     *      {@link #newAsciiString(Buffer)}.
     */
    public static CharSequence emptyAsciiString() {
        return io.servicetalk.buffer.api.CharSequences.emptyAsciiString();
    }

    /**
     * Check if the provided {@link CharSequence} is an AsciiString,
     * result of a call to {@link #newAsciiString(String)}.
     *
     * @param sequence The {@link CharSequence} to check.
     * @return {@code true} if the check passes.
     */
    public static boolean isAsciiString(final CharSequence sequence) {
        return io.servicetalk.buffer.api.CharSequences.isAsciiString(sequence);
    }

    /**
     * Attempt to unwrap a {@link CharSequence} and obtain the underlying {@link Buffer} if possible.
     * @param cs the {@link CharSequence} to unwrap.
     * @return the underlying {@link Buffer} or {@code null}.
     */
    @Nullable
    public static Buffer unwrapBuffer(CharSequence cs) {
        return io.servicetalk.buffer.api.CharSequences.unwrapBuffer(cs);
    }

    /**
     * Perform a case-insensitive comparison of two {@link CharSequence}s.
     * <p>
     * NOTE: This only supports 8-bit ASCII.
     *
     * @param a first {@link CharSequence} to compare.
     * @param b second {@link CharSequence} to compare.
     * @return {@code true} if both {@link CharSequence}'s are equals when ignoring the case.
     */
    public static boolean contentEqualsIgnoreCase(@Nullable final CharSequence a, @Nullable final CharSequence b) {
        return io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase(a, b);
    }

    /**
     * Returns {@code true} if the content of both {@link CharSequence}'s are equals. This only supports 8-bit ASCII.
     * @param a left hand side of comparison.
     * @param b right hand side of comparison.
     * @return {@code true} if {@code a}'s content equals {@code b}.
     */
    public static boolean contentEquals(final CharSequence a, final CharSequence b) {
        return io.servicetalk.buffer.api.CharSequences.contentEquals(a, b);
    }

    /**
     * Find the index of {@code c} within {@code sequence} starting at index {@code fromIndex}.
     *
     * @param sequence The {@link CharSequence} to search in.
     * @param c The character to find.
     * @param fromIndex The index to start searching (inclusive).
     * @return The index of {@code c} or {@code -1} otherwise.
     */
    public static int indexOf(CharSequence sequence, char c, int fromIndex) {
       return io.servicetalk.buffer.api.CharSequences.indexOf(sequence, c, fromIndex);
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
        return io.servicetalk.buffer.api.CharSequences.split(input, delimiter);
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
        return io.servicetalk.buffer.api.CharSequences.regionMatches(cs, ignoreCase, csStart, string, start, length);
    }
}
