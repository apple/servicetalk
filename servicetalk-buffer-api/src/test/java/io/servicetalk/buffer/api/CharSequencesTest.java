/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.buffer.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.function.Function;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.buffer.api.CharSequences.split;
import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CharSequencesTest {

    // Common strings
    public static final String GZIP = "gzip";
    public static final String DEFLATE = "deflate";
    public static final String COMPRESS = "compress";

    private static void splitNoTrim(Function<String, ? extends CharSequence> f) {
        assertThat(split(f.apply(" ,      "), ',', false),
                contains(f.apply(" "), f.apply("      ")));
        assertThat(split(f.apply(" ,      ,"), ',', false),
                contains(f.apply(" "), f.apply("      "), f.apply("")));
        assertThat(split(f.apply(" gzip  ,  deflate  "), ',', false),
                contains(f.apply(" gzip  "), f.apply("  deflate  ")));
        assertThat(split(f.apply(" gzip  ,  deflate  ,"), ',', false),
                contains(f.apply(" gzip  "), f.apply("  deflate  "), f.apply("")));
        assertThat(split(f.apply("gzip, deflate"), ',', false),
                contains(f.apply(GZIP), f.apply(" deflate")));
        assertThat(split(f.apply("gzip , deflate"), ',', false),
                contains(f.apply("gzip "), f.apply(" deflate")));
        assertThat(split(f.apply("gzip ,  deflate"), ',', false),
                contains(f.apply("gzip "), f.apply("  deflate")));
        assertThat(split(f.apply(" gzip, deflate"), ',', false),
                contains(f.apply(" gzip"), f.apply(" deflate")));
        assertThat(split(f.apply(GZIP), ',', false),
                contains(f.apply(GZIP)));
        assertThat(split(f.apply("gzip,"), ',', false),
                contains(f.apply(GZIP), f.apply("")));
        assertThat(split(f.apply("gzip,deflate,compress"), ',', false),
                contains(f.apply(GZIP), f.apply(DEFLATE), f.apply(COMPRESS)));
        assertThat(split(f.apply("gzip,,compress"), ',', false),
                contains(f.apply(GZIP), f.apply(""), f.apply(COMPRESS)));
        assertThat(split(f.apply("gzip, ,compress"), ',', false),
                contains(f.apply(GZIP), f.apply(" "), f.apply(COMPRESS)));
        assertThat(split(f.apply("gzip , , compress"), ',', false),
                contains(f.apply("gzip "), f.apply(" "), f.apply(" compress")));
        assertThat(split(f.apply("gzip , white space word , compress"), ',', false),
                contains(f.apply("gzip "), f.apply(" white space word "), f.apply(" compress")));
        assertThat(split(f.apply("gzip compress"), ' ', false),
                contains(f.apply(GZIP), f.apply(COMPRESS)));
        assertThat(split(f.apply("gzip     compress"), ' ', false),
                contains(f.apply(GZIP), f.apply(""), f.apply(""), f.apply(""), f.apply(""),
                        f.apply(COMPRESS)));
        assertThat(split(f.apply(" gzip     compress "), ' ', false),
                contains(f.apply(""), f.apply(GZIP), f.apply(""), f.apply(""), f.apply(""),
                        f.apply(""), f.apply(COMPRESS), f.apply("")));
        assertThat(split(f.apply("gzip,,,,,compress"), ',', false),
                contains(f.apply(GZIP), f.apply(""), f.apply(""), f.apply(""), f.apply(""),
                        f.apply(COMPRESS)));
        assertThat(split(f.apply(",gzip,,,,,compress,"), ',', false),
                contains(f.apply(""), f.apply(GZIP), f.apply(""), f.apply(""), f.apply(""),
                        f.apply(""), f.apply(COMPRESS), f.apply("")));
        assertThat(split(f.apply(",,,,"), ',', false),
                contains(f.apply(""), f.apply(""), f.apply(""), f.apply(""), f.apply("")));
        assertThat(split(f.apply("    "), ' ', false),
                contains(f.apply(""), f.apply(""), f.apply(""), f.apply(""), f.apply("")));
    }

    private static void splitWithTrim(Function<String, ? extends CharSequence> f) {
        assertThat(split(f.apply(" ,      "), ',', true),
                contains(f.apply(""), f.apply("")));
        assertThat(split(f.apply(" ,      ,"), ',', true),
                contains(f.apply(""), f.apply(""), f.apply("")));
        assertThat(split(f.apply(" gzip  ,  deflate  "), ',', true),
                contains(f.apply(GZIP), f.apply(DEFLATE)));
        assertThat(split(f.apply(" gzip  ,  deflate  ,"), ',', true),
                contains(f.apply(GZIP), f.apply(DEFLATE), f.apply("")));
        assertThat(split(f.apply("gzip, deflate"), ',', true),
                contains(f.apply(GZIP), f.apply(DEFLATE)));
        assertThat(split(f.apply("gzip , deflate"), ',', true),
                contains(f.apply(GZIP), f.apply(DEFLATE)));
        assertThat(split(f.apply("gzip ,  deflate"), ',', true),
                contains(f.apply(GZIP), f.apply(DEFLATE)));
        assertThat(split(f.apply(" gzip, deflate"), ',', true),
                contains(f.apply(GZIP), f.apply(DEFLATE)));
        assertThat(split(f.apply(GZIP), ',', true),
                contains(f.apply(GZIP)));
        assertThat(split(f.apply("gzip,"), ',', true),
                contains(f.apply(GZIP), f.apply("")));
        assertThat(split(f.apply("gzip,deflate,compress"), ',', true),
                contains(f.apply(GZIP), f.apply(DEFLATE), f.apply(COMPRESS)));
        assertThat(split(f.apply("gzip,,compress"), ',', true),
                contains(f.apply(GZIP), f.apply(""), f.apply(COMPRESS)));
        assertThat(split(f.apply("gzip, ,compress"), ',', true),
                contains(f.apply(GZIP), f.apply(""), f.apply(COMPRESS)));
        assertThat(split(f.apply("gzip , , compress"), ',', true),
                contains(f.apply(GZIP), f.apply(""), f.apply(COMPRESS)));
        assertThat(split(f.apply("gzip , white space word , compress"), ',', true),
                contains(f.apply(GZIP), f.apply("white space word"), f.apply(COMPRESS)));
        assertThat(split(f.apply("gzip compress"), ' ', true),
                contains(f.apply(GZIP), f.apply(COMPRESS)));
        assertThat(split(f.apply("gzip     compress"), ' ', true),
                contains(f.apply(GZIP), f.apply(""), f.apply(""), f.apply(""), f.apply(""),
                        f.apply(COMPRESS)));
        assertThat(split(f.apply(" gzip     compress "), ' ', true),
                contains(f.apply(""), f.apply(GZIP), f.apply(""), f.apply(""),
                        f.apply(""), f.apply(""), f.apply(COMPRESS), f.apply("")));
        assertThat(split(f.apply("gzip,,,,,compress"), ',', true),
                contains(f.apply(GZIP), f.apply(""), f.apply(""), f.apply(""), f.apply(""),
                        f.apply(COMPRESS)));
        assertThat(split(f.apply(",gzip,,,,,compress,"), ',', true),
                contains(f.apply(""), f.apply(GZIP), f.apply(""), f.apply(""), f.apply(""),
                        f.apply(""), f.apply(COMPRESS), f.apply("")));
        assertThat(split(f.apply(",,,,"), ',', true),
                contains(f.apply(""), f.apply(""), f.apply(""), f.apply(""), f.apply("")));
        assertThat(split(f.apply("    "), ' ', true),
                contains(f.apply(""), f.apply(""), f.apply(""), f.apply(""), f.apply("")));
    }

    @Test
    public void splitStringNoTrim() {
        splitNoTrim(identity());
    }

    @Test
    public void splitStringWithTrim() {
        splitWithTrim(identity());
    }

    @Test
    public void splitAsciiNoTrim() {
        splitNoTrim(CharSequences::newAsciiString);
    }

    @Test
    public void splitAsciiWithTrim() {
        splitWithTrim(CharSequences::newAsciiString);
    }

    @ParameterizedTest
    @ValueSource(longs = { Long.MIN_VALUE, Long.MIN_VALUE + 1,
            -101, -100, -99, -11, -10, -9, -1, 0, 1, 9, 10, 11, 99, 100, 101,
            Long.MAX_VALUE - 1, Long.MAX_VALUE })
    public void parseLong(final long value) {
        final String strValue = String.valueOf(value);
        assertThat("Unexpected value for String representation", CharSequences.parseLong(strValue), is(value));
        assertThat("Unexpected value for AsciiBuffer representation",
                CharSequences.parseLong(newAsciiString(strValue)), is(value));
    }

    @ParameterizedTest
    @ValueSource(strings = { "-0", "+0", "+1", "+10" })
    public void parseLongSigned(final String value) {
        assertThat("Unexpected value for String representation",
                CharSequences.parseLong(value), is(Long.parseLong(value)));
        assertThat("Unexpected value for AsciiBuffer representation",
                CharSequences.parseLong(newAsciiString(value)), is(Long.parseLong(value)));
    }

    @ParameterizedTest
    @ValueSource(strings = { "-", "+" })
    public void parseLongSignOnly(final String value) {
        assertThrows(NumberFormatException.class, () -> CharSequences.parseLong(value));
        assertThrows(NumberFormatException.class, () -> CharSequences.parseLong(newAsciiString(value)));
    }

    @Test
    public void parseLongFromSlice() {
        Buffer buffer = DEFAULT_RO_ALLOCATOR.fromAscii("text42text");
        // FIXME: ReadOnlyByteBuffer#slice() does not account for the slice offset
        // assertThat("Unexpected value for AsciiBuffer representation",
        //         CharSequences.parseLong(newAsciiString(buffer.slice(4, 2))), is(42L));
        assertThat("Unexpected value for AsciiBuffer representation",
                CharSequences.parseLong(newAsciiString(buffer).subSequence(4, 6)), is(42L));
    }
}
