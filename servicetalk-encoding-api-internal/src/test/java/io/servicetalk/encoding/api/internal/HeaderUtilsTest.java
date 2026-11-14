/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.encoding.api.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.encoding.api.internal.HeaderUtils.negotiateAcceptedEncodingRaw;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class HeaderUtilsTest {

    private static final List<String> SUPPORTED = asList("gzip", "deflate");
    private static final Function<String, CharSequence> NAME = name -> name;

    @Test
    void nullHeader() {
        assertThat(negotiateAcceptedEncodingRaw(null, SUPPORTED, NAME), is(nullValue()));
    }

    @Test
    void noSupportedEncoders() {
        assertThat(negotiateAcceptedEncodingRaw("gzip", emptyList(), NAME),
                is(nullValue()));
    }

    @ParameterizedTest(name = "{displayName} [{index}] header=''{0}'' expected={1}")
    @CsvSource(delimiter = '|', nullValues = "null", value = {
            // No qvalue: first listed supported encoding wins.
            "gzip|gzip",
            "deflate|deflate",
            "deflate, gzip|deflate",
            "br, gzip|gzip",
            "br|null",
            "identity|null",
            "GZIP|gzip",
            "gzip,,deflate|gzip",
            ",deflate|deflate",
            // qvalue prioritization.
            "gzip;q=0.5, deflate|deflate",
            "gzip;q=0.5, deflate;q=0.8|deflate",
            "gzip;q=0.8, deflate;q=0.5|gzip",
            "gzip;q=0.5, deflate;q=0.5|gzip",
            "deflate;q=0.5, gzip;q=0.5|deflate",
            "gzip;q=1.0, deflate;q=0.9|gzip",
            "gzip;q=0.001, deflate;q=0.002|deflate",
            "gzip ; q=0.1 , deflate ; q=0.2|deflate",
            "gzip;Q=0.1, deflate;Q=0.2|deflate",
            "br;q=1.0, gzip;q=0.1|gzip",
            // q=0 is not acceptable.
            "gzip;q=0|null",
            "gzip;q=0.0|null",
            "gzip;q=0.000|null",
            "gzip;q=0, deflate|deflate",
            "gzip;q=0, deflate;q=0|null",
            "deflate;q=0, gzip;q=0.1|gzip",
            // Malformed qvalues are treated as if absent.
            "gzip;q=, deflate;q=0.5|gzip",
            "gzip;q=abc, deflate;q=0.5|gzip",
            "gzip;level=5, deflate;q=0.5|gzip",
            "gzip;q=2, deflate;q=0.5|gzip",
            "gzip;q=0.5xyz, deflate;q=0.4|gzip",
            // Wild card.
            "*|gzip",
            "*;q=0|null",
            "*;q=0.5, deflate;q=0.1|gzip",
            "gzip;q=0, *|deflate",
            "gzip;q=0, deflate;q=0, *|null",
            "gzip;q=0.5, *|deflate",
            "gzip;q=0.5, *;q=0.5|gzip",
            "gzip;q=0.5, *;q=0.1|gzip",
            "gzip;q=0, *;q=0.5|deflate",
            "deflate;q=0.5, *;q=0.9|gzip",
            "*;q=0.2, gzip;q=0.1|deflate",
            "deflate, *;q=0.2|deflate",
            "gzip;q=0, deflate;q=0, br, *;q=0.5|null",
    })
    void negotiate(final String header, @Nullable final String expected) {
        assertThat(negotiateAcceptedEncodingRaw(header, SUPPORTED, NAME), is(expected));
    }
}
