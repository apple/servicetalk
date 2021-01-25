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

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.buffer.api.CharSequences.split;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

public class CharSequencesTest {

    // Common strings
    public static final String GZIP = "gzip";
    public static final CharSequence GZIP_ASCII = newAsciiString(GZIP);
    public static final String DEFLATE = "deflate";
    public static final CharSequence DEFLATE_ASCII = newAsciiString(DEFLATE);
    public static final String COMPRESS = "compress";
    public static final CharSequence COMPRESS_ASCII = newAsciiString(COMPRESS);

    @Test
    public void splitString() {
        assertThat(split(" ,      ", ',', false), contains(" ", "      "));
        assertThat(split(" ,      ,", ',', false), contains(" ", "      "));
        assertThat(split(" gzip  ,  deflate  ", ',', false), contains(" gzip  ", "  deflate  "));
        assertThat(split(" gzip  ,  deflate  ,", ',', false), contains(" gzip  ", "  deflate  "));
        assertThat(split("gzip, deflate", ',', false), contains(GZIP, " deflate"));
        assertThat(split("gzip , deflate", ',', false), contains("gzip ", " deflate"));
        assertThat(split("gzip ,  deflate", ',', false), contains("gzip ", "  deflate"));
        assertThat(split(" gzip, deflate", ',', false), contains(" gzip", " deflate"));
        assertThat(split(GZIP, ',', false), contains(GZIP));
        assertThat(split("gzip,", ',', false), contains(GZIP));
        assertThat(split("gzip,deflate,compress", ',', false), contains(GZIP, DEFLATE, COMPRESS));
        assertThat(split("gzip,,compress", ',', false), contains(GZIP, COMPRESS));
        assertThat(split("gzip, ,compress", ',', false), contains(GZIP, " ", COMPRESS));
        assertThat(split("gzip , , compress", ',', false), contains("gzip ", " ", " compress"));
        assertThat(split("gzip , white space word , compress", ',', false),
                contains("gzip ", " white space word ", " compress"));
    }

    @Test
    public void splitStringWithTrim() {
        assertThat(split(" ,      ", ',', true), empty());
        assertThat(split(" ,      ,", ',', true), empty());
        assertThat(split(" gzip  ,  deflate  ", ',', true), contains(GZIP, DEFLATE));
        assertThat(split(" gzip  ,  deflate  ,", ',', true), contains(GZIP, DEFLATE));
        assertThat(split("gzip, deflate", ',', true), contains(GZIP, DEFLATE));
        assertThat(split("gzip , deflate", ',', true), contains(GZIP, DEFLATE));
        assertThat(split("gzip ,  deflate", ',', true), contains(GZIP, DEFLATE));
        assertThat(split(" gzip, deflate", ',', true), contains(GZIP, DEFLATE));
        assertThat(split(GZIP, ',', true), contains(GZIP));
        assertThat(split("gzip,", ',', true), contains(GZIP));
        assertThat(split("gzip,deflate,compress", ',', true), contains(GZIP, DEFLATE, COMPRESS));
        assertThat(split("gzip,,compress", ',', true), contains(GZIP, COMPRESS));
        assertThat(split("gzip, ,compress", ',', true), contains(GZIP, COMPRESS));
        assertThat(split("gzip , , compress", ',', true), contains(GZIP, COMPRESS));
        assertThat(split(",, , ", ',', true), empty());
        assertThat(split("gzip , white space word , compress", ',', true),
                contains("gzip", "white space word", "compress"));
    }

    @Test
    public void splitAsciiString() {
        assertThat(split(newAsciiString(" ,      "), ',', false),
                contains(newAsciiString(" "), newAsciiString("      ")));
        assertThat(split(newAsciiString(" ,      ,"), ',', false),
                contains(newAsciiString(" "), newAsciiString("      ")));
        assertThat(split(newAsciiString(" gzip  ,  deflate  "), ',', false),
                contains(newAsciiString(" gzip  "), newAsciiString("  deflate  ")));
        assertThat(split(newAsciiString(" gzip  ,  deflate  ,"), ',', false),
                contains(newAsciiString(" gzip  "), newAsciiString("  deflate  ")));
        assertThat(split(newAsciiString("gzip, deflate"), ',', false),
                contains(GZIP_ASCII, newAsciiString(" deflate")));
        assertThat(split(newAsciiString("gzip , deflate"), ',', false),
                contains(newAsciiString("gzip "), newAsciiString(" deflate")));
        assertThat(split(newAsciiString("gzip ,  deflate"), ',', false),
                contains(newAsciiString("gzip "), newAsciiString("  deflate")));
        assertThat(split(newAsciiString(" gzip, deflate"), ',', false),
                contains(newAsciiString(" gzip"), newAsciiString(" deflate")));
        assertThat(split(GZIP_ASCII, ',', false), contains(GZIP_ASCII));
        assertThat(split(newAsciiString("gzip,"), ',', false), contains(GZIP_ASCII));
        assertThat(split(newAsciiString("gzip,deflate,compress"), ',', false),
                contains(GZIP_ASCII, DEFLATE_ASCII, COMPRESS_ASCII));
        assertThat(split(newAsciiString("gzip,,compress"), ',', false),
                contains(GZIP_ASCII, COMPRESS_ASCII));
        assertThat(split(newAsciiString("gzip, ,compress"), ',', false),
                contains(GZIP_ASCII, newAsciiString(" "), COMPRESS_ASCII));
        assertThat(split(newAsciiString("gzip , , compress"), ',', false),
                contains(newAsciiString("gzip "), newAsciiString(" "), newAsciiString(" compress")));
    }

    @Test
    public void splitAsciiStringWithTrim() {
        assertThat(split(newAsciiString(" ,      "), ',', true), empty());
        assertThat(split(newAsciiString(" ,      ,"), ',', true), empty());
        assertThat(split(newAsciiString(" gzip  ,  deflate  "), ',', true),
                contains(GZIP_ASCII, DEFLATE_ASCII));
        assertThat(split(newAsciiString(" gzip  ,  deflate  ,"), ',', true),
                contains(GZIP_ASCII, DEFLATE_ASCII));
        assertThat(split(newAsciiString("gzip, deflate"), ',', true),
                contains(GZIP_ASCII, DEFLATE_ASCII));
        assertThat(split(newAsciiString("gzip , deflate"), ',', true),
                contains(GZIP_ASCII, DEFLATE_ASCII));
        assertThat(split(newAsciiString("gzip ,  deflate"), ',', true),
                contains(GZIP_ASCII, DEFLATE_ASCII));
        assertThat(split(newAsciiString(" gzip, deflate"), ',', true),
                contains(GZIP_ASCII, DEFLATE_ASCII));
        assertThat(split(GZIP_ASCII, ',', true), contains(GZIP_ASCII));
        assertThat(split(newAsciiString("gzip,"), ',', true), contains(GZIP_ASCII));
        assertThat(split(newAsciiString("gzip,deflate,compress"), ',', true),
                contains(GZIP_ASCII, DEFLATE_ASCII, COMPRESS_ASCII));
        assertThat(split(newAsciiString("gzip,,compress"), ',', true),
                contains(GZIP_ASCII, COMPRESS_ASCII));
        assertThat(split(newAsciiString("gzip, ,compress"), ',', true),
                contains(GZIP_ASCII, COMPRESS_ASCII));
        assertThat(split(newAsciiString("gzip , , compress"), ',', true),
                contains(GZIP_ASCII, COMPRESS_ASCII));
        assertThat(split(newAsciiString(",, , "), ',', true), empty());
    }
}
