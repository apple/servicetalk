/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static java.lang.Math.max;
import static java.lang.Math.min;

final class UriUtils {
    // https://tools.ietf.org/html/rfc3986 declares that all delimiter/terminal values are US-ASCII
    // https://tools.ietf.org/html/rfc3986#section-2. This means values fall within the [0, 127] range. This range
    // can be represented as a bitset/bitmask with two longs (high, low), which reduces the amount of conditional
    // statements required to validate characters belonging to each character class. The following final variables
    // provide the bitmask for the different character classes defined in rfc3986, and isBitSet can be used to verify if
    // a bit is set for a particular (high, low) mask pair.
    //
    // pct-encoded is omitted below. This criteria is asserted outside this scope because it requires checking multiple
    // bytes and advancing the loop index accordingly.

    private static final long DIGIT_LMASK = lowMask('0', '9');
    private static final long DIGIT_HMASK = highMask('0', '9');

    private static final long ALPHA_LMASK = lowMask('a', 'z') | lowMask('A', 'Z');
    private static final long ALPHA_HMASK = highMask('a', 'z') | highMask('A', 'Z');

    private static final long HEXDIG_LMASK = DIGIT_LMASK | lowMask('a', 'f') | lowMask('A', 'F');
    private static final long HEXDIG_HMASK = DIGIT_HMASK | highMask('a', 'f') | highMask('A', 'F');

    // unreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~"
    private static final long UNRESERVED_LMASK = ALPHA_LMASK | DIGIT_LMASK | lowMask("-._~");
    private static final long UNRESERVED_HMASK = ALPHA_HMASK | DIGIT_HMASK | highMask("-._~");

    // sub-delims    = "!" / "$" / "&" / "'" / "(" / ")"
    //                     / "*" / "+" / "," / ";" / "="
    private static final long SUBDELIM_LMASK = lowMask("!$&'()*+,;=");
    private static final long SUBDELIM_HMASK = highMask("!$&'()*+,;=");

    // pchar         = unreserved / pct-encoded / sub-delims / ":" / "@"
    private static final long PCHAR_LMASK = UNRESERVED_LMASK | SUBDELIM_LMASK | lowMask(";@");
    private static final long PCHAR_HMASK = UNRESERVED_HMASK | SUBDELIM_HMASK | highMask(";@");

    private static final long PCHAR_NOSUBDELIM_LMASK = UNRESERVED_LMASK | lowMask(";@");
    private static final long PCHAR_NOSUBDELIM_HMASK = UNRESERVED_HMASK | highMask(";@");

    // userinfo    = *( unreserved / pct-encoded / sub-delims / ":" )
    static final long USERINFO_LMASK = UNRESERVED_LMASK | SUBDELIM_LMASK | lowMask(":");
    static final long USERINFO_HMASK = UNRESERVED_HMASK | SUBDELIM_HMASK | highMask(":");

    // path        = *(pchar / "/")
    static final long PATH_LMASK = PCHAR_LMASK | lowMask("/");
    static final long PATH_HMASK = PCHAR_HMASK | highMask("/");

    static final long PATH_SEGMENT_LMASK = PCHAR_LMASK;
    static final long PATH_SEGMENT_HMASK = PCHAR_HMASK;

    // query       = *( pchar / "/" / "?" )
    static final long QUERY_LMASK = PCHAR_LMASK | lowMask("/?");
    static final long QUERY_HMASK = PCHAR_HMASK | highMask("/?");

    static final long QUERY_VALUE_LMASK = PCHAR_NOSUBDELIM_LMASK | lowMask("/?");
    static final long QUERY_VALUE_HMASK = PCHAR_NOSUBDELIM_HMASK | highMask("/?");

    // fragment    = *( pchar / "/" / "?" )
    static final long FRAGMENT_LMASK = QUERY_LMASK;
    static final long FRAGMENT_HMASK = QUERY_HMASK;

    // host        = IP-literal / IPv4address / reg-name
    // reg-name    = *( unreserved / pct-encoded / sub-delims )
    static final long HOST_NON_IP_LMASK = UNRESERVED_LMASK | SUBDELIM_LMASK;
    static final long HOST_NON_IP_HMASK = UNRESERVED_HMASK | SUBDELIM_HMASK;

    private UriUtils() {
    }

    /**
     * Decode the specified raw query with the specified {@code charset} for the specified maximum number of parameters.
     */
    static Map<String, List<String>> decodeQueryParams(@Nullable final String rawQuery, final Charset charset,
                                                       final int maxParams) {
        return decodeQueryParams(rawQuery, charset, maxParams, UriUtils::decodeComponent);
    }

    static Map<String, List<String>> decodeQueryParams(@Nullable final String rawQuery, final Charset charset,
                                                       final int maxParams,
                                                       BiFunction<String, Charset, String> decoder) {
        if (maxParams <= 0) {
            throw new IllegalArgumentException("maxParams: " + maxParams + " (expected: > 0)");
        }

        if (rawQuery == null || rawQuery.isEmpty()) {
            return new LinkedHashMap<>(2);
        }

        final Map<String, List<String>> params = new LinkedHashMap<>();
        int paramCountDown = maxParams;
        final int from = rawQuery.charAt(0) == '?' ? 1 : 0;
        final int len = rawQuery.length();
        int nameStart = from;
        int valueStart = -1;
        int i;
        loop:
        for (i = from; i < len; i++) {
            switch (rawQuery.charAt(i)) {
                case '=':
                    if (nameStart == i) {
                        nameStart = i + 1;
                    } else if (valueStart < nameStart) {
                        valueStart = i + 1;
                    }
                    break;
                case '&':
                case ';':
                    if (addQueryParam(rawQuery, nameStart, valueStart, i, charset, params, decoder)) {
                        paramCountDown--;
                        if (paramCountDown == 0) {
                            return params;
                        }
                    }
                    nameStart = i + 1;
                    break;
                case '#':
                    break loop;
                default:
                    // continue
            }
        }
        addQueryParam(rawQuery, nameStart, valueStart, i, charset, params, decoder);
        return params;
    }

    static String encodeComponent(UriComponentType type, String component, Charset charset,
                                  boolean preservePctEncoded) {
        byte[] bytes = component.getBytes(charset);
        for (int i = 0; i < bytes.length; ++i) {
            byte b = bytes[i];
            if (type.isValid(b)) {
                // noop
            } else if (preservePctEncoded && bytes.length - 3 >= i && isPctEncoded(bytes, b, i)) {
                i += 2; // only increment by 2 here, the for loop will increment 1 more
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(bytes.length + 16);
                baos.write(bytes, 0, i);
                encodeHexDigits(baos, b);
                for (int j = i + 1; j < bytes.length; ++j) {
                    b = bytes[j];
                    if (type.isValid(b)) {
                        baos.write(b);
                    } else if (preservePctEncoded && bytes.length - 3 >= j && isPctEncoded(bytes, b, j)) {
                        baos.write(bytes, j, 3);
                        j += 2; // only increment by 2 here, the for loop will increment 1 more
                    } else {
                        encodeHexDigits(baos, b);
                    }
                }
                return new String(baos.toByteArray(), charset);
            }
        }
        return component;
    }

    static String decodeComponent(final String s, final Charset charset) {
        if (s.isEmpty()) {
            return s;
        }

        byte[] bytes = s.getBytes(charset);
        for (int i = 0; i < bytes.length; ++i) {
            byte b = bytes[i];
            if (b == '%') {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(bytes.length);
                baos.write(bytes, 0, i);
                baos.write(decodeHexDigits(bytes, i));
                for (int j = i + 3; j < bytes.length; ++j) {
                    b = bytes[j];
                    if (b == '%') {
                        baos.write(decodeHexDigits(bytes, j));
                        j += 2;
                    } else {
                        baos.write(b);
                    }
                }
                return new String(baos.toByteArray(), charset);
            }
        }

        return s;
    }

    static int parsePort(final String uri, final int begin, final int end) {
        final int len = end - begin;
        if (len == 4) {
            return (1000 * toDecimal(uri.charAt(begin))) +
                    (100 * toDecimal(uri.charAt(begin + 1))) +
                    (10 * toDecimal(uri.charAt(begin + 2))) +
                    toDecimal(uri.charAt(begin + 3));
        } else if (len == 3) {
            return (100 * toDecimal(uri.charAt(begin))) +
                    (10 * toDecimal(uri.charAt(begin + 1))) +
                    toDecimal(uri.charAt(begin + 2));
        } else if (len == 2) {
            return (10 * toDecimal(uri.charAt(begin))) +
                    toDecimal(uri.charAt(begin + 1));
        } else if (len == 5) {
            final int port = (10000 * toDecimal(uri.charAt(begin))) +
                    (1000 * toDecimal(uri.charAt(begin + 1))) +
                    (100 * toDecimal(uri.charAt(begin + 2))) +
                    (10 * toDecimal(uri.charAt(begin + 3))) +
                    toDecimal(uri.charAt(begin + 4));
            if (port > 65535) {
                throw new IllegalArgumentException("port out of bounds");
            }
            return port;
        } else if (len == 1) {
            return toDecimal(uri.charAt(begin));
        } else {
            throw new IllegalArgumentException("invalid port");
        }
    }

    private static boolean addQueryParam(final String s, final int nameStart, int valueStart, final int valueEnd,
                                         final Charset charset, final Map<String, List<String>> params,
                                         final BiFunction<String, Charset, String> decoder) {
        if (nameStart >= valueEnd) {
            return false;
        }
        if (valueStart <= nameStart) {
            valueStart = valueEnd + 1;
        }
        final String name = decoder.apply(s.substring(nameStart, valueStart - 1), charset);
        final String value = decoder.apply(s.substring(valueStart, valueEnd), charset);
        final List<String> values = params.computeIfAbsent(name, k -> new ArrayList<>(1)); // Often there's only 1 value
        values.add(value);
        return true;
    }

    private static void encodeHexDigits(ByteArrayOutputStream baos, byte b) {
        baos.write('%');
        baos.write(encodeHexNibble((b >>> 4) & 0xF));
        baos.write(encodeHexNibble(b & 0xF));
    }

    private static byte decodeHexDigits(byte[] bytes, int i) {
        if (bytes.length - 2 <= i) {
            throw new IllegalArgumentException("Invalid pct-encoded at index " + i);
        }
        final int hi = decodeHexNibble(bytes[i + 1]);
        final int lo = decodeHexNibble(bytes[i + 2]);
        if (hi == -1 || lo == -1) {
            throw new IllegalArgumentException("Invalid HEXDIG at index " + i);
        }
        return (byte) ((hi << 4) + lo);
    }

    private static byte encodeHexNibble(final int b) {
        // Character.forDigit() is not used here, as it addresses a larger
        // set of characters (both ASCII and full-width latin letters).
        if (b < 0 || b >= 16) {
            return 0;
        }
        if (b < 10) {
            return (byte) ('0' + b);
        }
        return (byte) ('A' - 10 + b); // uppercase
    }

    /**
     * Helper to decode half of a hexadecimal number from a string.
     *
     * @param b The ASCII character of the hexadecimal number to decode.
     * Must be in the range {@code [0-9a-fA-F]}.
     * @return The hexadecimal value represented in the ASCII character
     * given, or {@code -1} if the character is invalid.
     */
    private static int decodeHexNibble(final byte b) {
        // Character.digit() is not used here, as it addresses a larger
        // set of characters (both ASCII and full-width latin letters).
        if (b >= '0' && b <= '9') {
            return b - '0';
        }
        if (b >= 'A' && b <= 'F') {
            return b - ('A' - 0xA);
        }
        if (b >= 'a' && b <= 'f') {
            return b - ('a' - 0xA);
        }
        return -1;
    }

    private static int toDecimal(final char c) {
        if (c < '0' || c > '9') {
            throw new IllegalArgumentException("invalid port");
        }
        return c - '0';
    }

    private static boolean isPctEncoded(byte[] bytes, byte b, int bIndex) {
        return b == '%' && isHexDig(bytes[bIndex + 1]) && isHexDig(bytes[bIndex + 2]);
    }

    private static boolean isHexDig(byte b) {
        return isBitSet(b, HEXDIG_LMASK, HEXDIG_HMASK);
    }

    private static long lowMask(String asciiChars) {
        long mask = 0;
        for (int i = 0; i < asciiChars.length(); ++i) {
            final char c = asciiChars.charAt(i);
            if (c < 64) {
                mask |= (1L << c);
            }
        }
        return mask;
    }

    private static long lowMask(char first, char last) {
        assert first <= 127 && last <= 127;
        long mask = 0;
        int begin = min(first, 63);
        int end = min(last, 63);
        for (int i = begin; i <= end; ++i) {
            mask |= 1L << i;
        }
        return mask;
    }

    private static long highMask(String asciiChars) {
        long mask = 0;
        for (int i = 0; i < asciiChars.length(); ++i) {
            final char c = asciiChars.charAt(i);
            if ((c >= 64) && (c < 128)) {
                mask |= (1L << (c - 64));
            }
        }
        return mask;
    }

    private static long highMask(char first, char last) {
        assert first <= 127 && last <= 127;
        long mask = 0;
        int begin = max(first, 63) - 64;
        int end = max(last, 63) - 64;
        for (int i = begin; i <= end; ++i) {
            mask |= 1L << i;
        }
        return mask;
    }

    static boolean isBitSet(byte b, long lowMask, long highMask) {
        return b > 0 && (b < 64 ? ((1L << b) & lowMask) != 0 : ((1L << (b - 64)) & highMask) != 0);
    }
}
