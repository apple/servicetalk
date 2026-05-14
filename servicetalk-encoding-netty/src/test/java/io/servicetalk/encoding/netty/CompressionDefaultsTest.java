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
package io.servicetalk.encoding.netty;

import org.junit.jupiter.api.Test;

import static io.servicetalk.encoding.netty.CompressionDefaults.DEFAULT_MAX_DECOMPRESSED_BYTES;
import static io.servicetalk.encoding.netty.CompressionDefaults.parseMaxDecompressedBytes;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Covers parsing of the system-property override for the default cap. The integration with the
 * static field happens at class load and is not exercised here — that path is straightforward
 * once parsing is correct.
 */
class CompressionDefaultsTest {

    @Test
    void nullFallsBackToDefault() {
        assertThat(parseMaxDecompressedBytes(null), equalTo(DEFAULT_MAX_DECOMPRESSED_BYTES));
    }

    @Test
    void zeroIsAcceptedAsExplicitOptOut() {
        assertThat(parseMaxDecompressedBytes("0"), equalTo(0L));
    }

    @Test
    void positiveValueIsHonored() {
        assertThat(parseMaxDecompressedBytes("104857600"), equalTo(100L << 20));
    }

    @Test
    void leadingAndTrailingWhitespaceIsTolerated() {
        assertThat(parseMaxDecompressedBytes("  4096  "), equalTo(4096L));
    }

    @Test
    void negativeValueFallsBackToDefault() {
        assertThat(parseMaxDecompressedBytes("-1"), equalTo(DEFAULT_MAX_DECOMPRESSED_BYTES));
    }

    @Test
    void malformedValueFallsBackToDefault() {
        assertThat(parseMaxDecompressedBytes("not-a-number"), equalTo(DEFAULT_MAX_DECOMPRESSED_BYTES));
    }

    @Test
    void emptyStringFallsBackToDefault() {
        assertThat(parseMaxDecompressedBytes(""), equalTo(DEFAULT_MAX_DECOMPRESSED_BYTES));
    }
}
