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

import org.junit.Test;

import java.util.function.Function;

import static io.servicetalk.buffer.api.CharSequences.split;
import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

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
        // System.out.println(split(" gzip  ,  deflate  ", ',', true));
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
}
