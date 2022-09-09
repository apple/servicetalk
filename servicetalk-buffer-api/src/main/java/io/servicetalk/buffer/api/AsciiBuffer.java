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
package io.servicetalk.buffer.api;

import java.nio.ByteOrder;

import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCaseUnknownTypes;
import static io.servicetalk.buffer.api.CharSequences.contentEqualsUnknownTypes;
import static java.nio.charset.StandardCharsets.US_ASCII;

final class AsciiBuffer implements CharSequence {
    static final CharSequence EMPTY_ASCII_BUFFER = new AsciiBuffer(EmptyBuffer.EMPTY_BUFFER);
    private static final boolean BIG_ENDIAN_NATIVE_ORDER = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    // constants borrowed from murmur3
    private static final int HASH_CODE_ASCII_SEED = 0xc2b2ae35;
    private static final int HASH_CODE_C1 = 0xcc9e2d51;
    private static final int HASH_CODE_C2 = 0x1b873593;

    private final Buffer buffer;
    private int hash;

    AsciiBuffer(Buffer buffer) {
        this.buffer = buffer.asReadOnly();
    }

    @Override
    public int length() {
        return buffer.readableBytes();
    }

    @Override
    public char charAt(int index) {
        return (char) (buffer.getByte(index) & 0xff);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return buffer.toString(start, end - start, US_ASCII);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Provides a case-insensitive hash code for Ascii like byte strings.
     */
    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0) {
            hash = h = hashCodeAscii(buffer, buffer.readerIndex(), buffer.readableBytes());
        }
        return h;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != AsciiBuffer.class) {
            return false;
        }

        final AsciiBuffer that = (AsciiBuffer) o;

        return hashCode() == that.hashCode() && buffer.equals(that.buffer);
    }

    @Override
    public String toString() {
        return buffer.toString(US_ASCII);
    }

    Buffer unwrap() {
        return buffer;
    }

    /**
     * Searches in this string for the index of the specified string. The search for the string starts at the specified
     * offset and moves towards the end of this string.
     *
     * @param ch the char to find.
     * @param start the starting offset.
     * @return the index of the first character of the specified string in this string, -1 if the specified string is
     *         not a substring.
     * @throws NullPointerException if {@code subString} is {@code null}.
     */
    int indexOf(char ch, int start) {
        if (!singleByte(ch)) {
            return -1;
        }
        return buffer.indexOf(start, buffer.writerIndex(), (byte) ch);
    }

    private static boolean singleByte(char ch) {
        return ch >>> 8 == 0;
    }

    /**
     * Iterates over the readable bytes of this buffer with the specified {@code processor} in ascending order.
     *
     * @param visitor the {@link ByteProcessor} visitor of each element.
     * @return {@code -1} if the processor iterated to or beyond the end of the readable bytes.
     * The last-visited index If the {@link ByteProcessor#process(byte)} returned {@code false}.
     */
    int forEachByte(final ByteProcessor visitor) {
        return buffer.forEachByte(visitor);
    }

    boolean contentEquals(CharSequence cs) {
        if (cs.getClass() == AsciiBuffer.class) {
            return buffer.equals(((AsciiBuffer) cs).buffer);
        }
        return contentEqualsUnknownTypes(this, cs);
    }

    boolean contentEqualsIgnoreCase(CharSequence cs) {
        return contentEqualsIgnoreCaseUnknownTypes(this, cs);
    }

    /**
     * Calculate a hash code of a byte array assuming ASCII character encoding.
     * The resulting hash code will be case insensitive.
     * @param buffer The array which contains the data to hash.
     * @param startPos What index to start generating a hash code in {@code bytes}
     * @param length The amount of bytes that should be accounted for in the computation.
     * @return The hash code of {@code bytes} assuming ASCII character encoding.
     * The resulting hash code will be case insensitive.
     */
    private static int hashCodeAscii(Buffer buffer, int startPos, int length) {
        int hash = HASH_CODE_ASCII_SEED;
        final int remainingBytes = length & 7;
        final int end = startPos + remainingBytes;
        for (int i = startPos - 8 + length; i >= end; i -= 8) {
            hash = hashCodeAsciiCompute(buffer.getLong(i), hash);
        }
        switch (remainingBytes) {
            case 7:
                return ((hash * HASH_CODE_C1 + hashCodeAsciiSanitize(buffer.getByte(startPos)))
                        * HASH_CODE_C2 + hashCodeAsciiSanitize(buffer.getShort(startPos + 1)))
                        * HASH_CODE_C1 + hashCodeAsciiSanitize(buffer.getInt(startPos + 3));
            case 6:
                return (hash * HASH_CODE_C1 + hashCodeAsciiSanitize(buffer.getShort(startPos)))
                        * HASH_CODE_C2 + hashCodeAsciiSanitize(buffer.getInt(startPos + 2));
            case 5:
                return (hash * HASH_CODE_C1 + hashCodeAsciiSanitize(buffer.getByte(startPos)))
                        * HASH_CODE_C2 + hashCodeAsciiSanitize(buffer.getInt(startPos + 1));
            case 4:
                return hash * HASH_CODE_C1 + hashCodeAsciiSanitize(buffer.getInt(startPos));
            case 3:
                return (hash * HASH_CODE_C1 + hashCodeAsciiSanitize(buffer.getByte(startPos)))
                        * HASH_CODE_C2 + hashCodeAsciiSanitize(buffer.getShort(startPos + 1));
            case 2:
                return hash * HASH_CODE_C1 + hashCodeAsciiSanitize(buffer.getShort(startPos));
            case 1:
                return hash * HASH_CODE_C1 + hashCodeAsciiSanitize(buffer.getByte(startPos));
            default:
                return hash;
        }
    }

    /**
     * Calculate a hash code of a byte array assuming ASCII character encoding.
     * The resulting hash code will be case insensitive.
     * <p>
     * This method assumes that {@code bytes} is equivalent to a {@code byte[]} but just using {@link CharSequence}
     * for storage. The upper most byte of each {@code char} from {@code bytes} is ignored.
     * @param bytes The array which contains the data to hash (assumed to be equivalent to a {@code byte[]}).
     * @return The hash code of {@code bytes} assuming ASCII character encoding.
     * The resulting hash code will be case insensitive.
     */
    static int hashCodeAscii(CharSequence bytes) {
        int hash = HASH_CODE_ASCII_SEED;
        final int remainingBytes = bytes.length() & 7;
        // Benchmarking shows that by just naively looping for inputs 8~31 bytes long we incur a relatively large
        // performance penalty (only achieve about 60% performance of loop which iterates over each char). So because
        // of this we take special provisions to unroll the looping for these conditions.
        switch (bytes.length()) {
            case 31:
            case 30:
            case 29:
            case 28:
            case 27:
            case 26:
            case 25:
            case 24:
                hash = hashCodeAsciiCompute(bytes, bytes.length() - 24,
                        hashCodeAsciiCompute(bytes, bytes.length() - 16,
                                hashCodeAsciiCompute(bytes, bytes.length() - 8, hash)));
                break;
            case 23:
            case 22:
            case 21:
            case 20:
            case 19:
            case 18:
            case 17:
            case 16:
                hash = hashCodeAsciiCompute(bytes, bytes.length() - 16,
                        hashCodeAsciiCompute(bytes, bytes.length() - 8, hash));
                break;
            case 15:
            case 14:
            case 13:
            case 12:
            case 11:
            case 10:
            case 9:
            case 8:
                hash = hashCodeAsciiCompute(bytes, bytes.length() - 8, hash);
                break;
            case 7:
            case 6:
            case 5:
            case 4:
            case 3:
            case 2:
            case 1:
            case 0:
                break;
            default:
                for (int i = bytes.length() - 8; i >= remainingBytes; i -= 8) {
                    hash = hashCodeAsciiCompute(bytes, i, hash);
                }
                break;
        }
        switch (remainingBytes) {
            case 7:
                return ((hash * HASH_CODE_C1 + hashCodeAsciiSanitizeByte(bytes.charAt(0)))
                        * HASH_CODE_C2 + hashCodeAsciiSanitizeShort(bytes, 1))
                        * HASH_CODE_C1 + hashCodeAsciiSanitizeInt(bytes, 3);
            case 6:
                return (hash * HASH_CODE_C1 + hashCodeAsciiSanitizeShort(bytes, 0))
                        * HASH_CODE_C2 + hashCodeAsciiSanitizeInt(bytes, 2);
            case 5:
                return (hash * HASH_CODE_C1 + hashCodeAsciiSanitizeByte(bytes.charAt(0)))
                        * HASH_CODE_C2 + hashCodeAsciiSanitizeInt(bytes, 1);
            case 4:
                return hash * HASH_CODE_C1 + hashCodeAsciiSanitizeInt(bytes, 0);
            case 3:
                return (hash * HASH_CODE_C1 + hashCodeAsciiSanitizeByte(bytes.charAt(0)))
                        * HASH_CODE_C2 + hashCodeAsciiSanitizeShort(bytes, 1);
            case 2:
                return hash * HASH_CODE_C1 + hashCodeAsciiSanitizeShort(bytes, 0);
            case 1:
                return hash * HASH_CODE_C1 + hashCodeAsciiSanitizeByte(bytes.charAt(0));
            default:
                return hash;
        }
    }

    private static int hashCodeAsciiCompute(long value, int hash) {
        // masking with 0x1f reduces the number of overall bits that impact the hash code but makes the hash
        // code the same regardless of character case (upper case or lower case hash is the same).
        return hash * HASH_CODE_C1 +
                // Low order int
                hashCodeAsciiSanitize((int) value) * HASH_CODE_C2 +
                // High order int
                (int) ((value & 0x1f1f1f1f00000000L) >>> 32);
    }

    private static int hashCodeAsciiSanitize(int value) {
        return value & 0x1f1f1f1f;
    }

    private static int hashCodeAsciiSanitize(short value) {
        return value & 0x1f1f;
    }

    private static int hashCodeAsciiSanitize(byte value) {
        return value & 0x1f;
    }

    /**
     * Identical to {@link #hashCodeAsciiSanitize(byte)} but for {@link CharSequence}.
     */
    private static int hashCodeAsciiSanitizeByte(char value) {
        return value & 0x1f;
    }

    /**
     * Identical to {@link #hashCodeAsciiSanitize(short)} but for {@link CharSequence}.
     */
    private static int hashCodeAsciiSanitizeShort(CharSequence value, int offset) {
        if (BIG_ENDIAN_NATIVE_ORDER) {
            // mimic a unsafe.getShort call on a big endian machine
            return (value.charAt(offset + 1) & 0x1f) << 8 |
                    (value.charAt(offset) & 0x1f);
        }
        return (value.charAt(offset + 1) & 0x1f) |
                (value.charAt(offset) & 0x1f) << 8;
    }

    /**
     * Identical to {@link #hashCodeAsciiSanitize(int)} but for {@link CharSequence}.
     */
    private static int hashCodeAsciiSanitizeInt(CharSequence value, int offset) {
        if (BIG_ENDIAN_NATIVE_ORDER) {
            // mimic a unsafe.getInt call on a big endian machine
            return (value.charAt(offset + 3) & 0x1f) << 24 |
                    (value.charAt(offset + 2) & 0x1f) << 16 |
                    (value.charAt(offset + 1) & 0x1f) << 8 |
                    (value.charAt(offset) & 0x1f);
        }
        return (value.charAt(offset + 3) & 0x1f) |
                (value.charAt(offset + 2) & 0x1f) << 8 |
                (value.charAt(offset + 1) & 0x1f) << 16 |
                (value.charAt(offset) & 0x1f) << 24;
    }

    /**
     * Identical to {@link #hashCodeAsciiCompute(long, int)} but for {@link CharSequence}.
     */
    private static int hashCodeAsciiCompute(CharSequence value, int offset, int hash) {
        if (BIG_ENDIAN_NATIVE_ORDER) {
            return hash * HASH_CODE_C1 +
                    // Low order int
                    hashCodeAsciiSanitizeInt(value, offset) * HASH_CODE_C2 +
                    // High order int
                    hashCodeAsciiSanitizeInt(value, offset + 4);
        }
        return hash * HASH_CODE_C1 +
                // Low order int
                hashCodeAsciiSanitizeInt(value, offset + 4) * HASH_CODE_C2 +
                // High order int
                hashCodeAsciiSanitizeInt(value, offset);
    }
}
