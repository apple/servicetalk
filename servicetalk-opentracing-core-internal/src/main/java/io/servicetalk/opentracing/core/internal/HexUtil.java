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
package io.servicetalk.opentracing.core.internal;

/**
 * Utilities for hex strings.
 */
public final class HexUtil {
    private static final char[] HEX_CHARS = new char[]{'0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private HexUtil() {
        // no instantiation
    }

    /**
     * Checks that the provided {@link CharSequence} is a valid hex {@link CharSequence},
     * throwing an {@link IllegalArgumentException} if it is not.
     *
     * @param str the {@link CharSequence} to check.
     * @param <T> The type of {@link CharSequence} to validate.
     * @return the provided {@link CharSequence} unchanged.
     */
    public static <T extends CharSequence> T validateHexBytes(T str) {
        if (str.length() < 16) {
            throw new IllegalArgumentException("Input has wrong length, must be at least 16 chars, got " +
                    str.length());
        }
        for (int i = 0, len = str.length(); i < len; ++i) {
            char ch = str.charAt(i);
            if (!((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F'))) {
                throw new IllegalArgumentException("Invalid hex character '" + ch + '\'');
            }
        }
        return str;
    }

    /**
     * Retrieves the {@code long} value represented by the provided hex {@link String}.
     * Throws {@link StringIndexOutOfBoundsException} if offset is invalid.
     *
     * @param str an hex {@link String}.
     * @param offset The index to start within {@code str} to start the conversion.
     * @return the {@code long} value.
     */
    static long longOfHexBytes(String str, int offset) {
        if (offset < 0) {
            throw new StringIndexOutOfBoundsException(offset);
        }
        if (offset > str.length() - 16) {
            throw new StringIndexOutOfBoundsException(offset + 16);
        }
        return ((long) HexUtil.fromHexChar(str.charAt(offset)) << 60) |
            ((long) HexUtil.fromHexChar(str.charAt(offset + 1)) << 56) |
            ((long) HexUtil.fromHexChar(str.charAt(offset + 2)) << 52) |
            ((long) HexUtil.fromHexChar(str.charAt(offset + 3)) << 48) |
            ((long) HexUtil.fromHexChar(str.charAt(offset + 4)) << 44) |
            ((long) HexUtil.fromHexChar(str.charAt(offset + 5)) << 40) |
            ((long) HexUtil.fromHexChar(str.charAt(offset + 6)) << 36) |
            ((long) HexUtil.fromHexChar(str.charAt(offset + 7)) << 32) |
            ((long) HexUtil.fromHexChar(str.charAt(offset + 8)) << 28) |
            ((long) HexUtil.fromHexChar(str.charAt(offset + 9)) << 24) |
            ((long) HexUtil.fromHexChar(str.charAt(offset + 10)) << 20) |
            ((long) HexUtil.fromHexChar(str.charAt(offset + 11)) << 16) |
            ((long) HexUtil.fromHexChar(str.charAt(offset + 12)) << 12) |
            ((long) HexUtil.fromHexChar(str.charAt(offset + 13)) << 8) |
            ((long) HexUtil.fromHexChar(str.charAt(offset + 14)) << 4) |
            ((long) HexUtil.fromHexChar(str.charAt(offset + 15)));
    }

    /**
     * Builds an hex {@link String} that represents the provided {@code long} value.
     *
     * @param v the {@code long} value.
     * @return an hex {@link String}.
     */
    static String hexBytesOfLong(long v) {
        return String.valueOf(new char[] {
            HEX_CHARS[(int) ((v >>> 60) & 0xf)],
            HEX_CHARS[(int) ((v >>> 56) & 0xf)],
            HEX_CHARS[(int) ((v >>> 52) & 0xf)],
            HEX_CHARS[(int) ((v >>> 48) & 0xf)],
            HEX_CHARS[(int) ((v >>> 44) & 0xf)],
            HEX_CHARS[(int) ((v >>> 40) & 0xf)],
            HEX_CHARS[(int) ((v >>> 36) & 0xf)],
            HEX_CHARS[(int) ((v >>> 32) & 0xf)],
            HEX_CHARS[(int) ((v >>> 28) & 0xf)],
            HEX_CHARS[(int) ((v >>> 24) & 0xf)],
            HEX_CHARS[(int) ((v >>> 20) & 0xf)],
            HEX_CHARS[(int) ((v >>> 16) & 0xf)],
            HEX_CHARS[(int) ((v >>> 12) & 0xf)],
            HEX_CHARS[(int) ((v >>> 8) & 0xf)],
            HEX_CHARS[(int) ((v >>> 4) & 0xf)],
            HEX_CHARS[(int) (v & 0xf)],
        });
    }

    private static byte fromHexChar(final char c) throws IllegalArgumentException {
        if (c >= '0' && c <= '9') {
            return (byte) (c - '0');
        } else if (c >= 'a' && c <= 'f') {
            return (byte) (10 + c - 'a');
        } else if (c >= 'A' && c <= 'F') {
            return (byte) (10 + c - 'A');
        }
        throw new IllegalArgumentException("Invalid hex char: '" + c + "'");
    }
}
