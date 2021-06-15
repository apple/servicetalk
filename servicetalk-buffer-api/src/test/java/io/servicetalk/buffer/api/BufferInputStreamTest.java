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

import java.io.InputStream;
import java.util.Scanner;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class BufferInputStreamTest {

    private static final String DATA = "12345";
    private final InputStream is = new BufferInputStream(DEFAULT_RO_ALLOCATOR.fromAscii("12345"));

    @Test
    void skipNegative() throws Exception {
        testSkip(-1, 0, "12345");
    }

    @Test
    void skipZero() throws Exception {
        testSkip(0, 0, "12345");
    }

    @Test
    void skipOne() throws Exception {
        testSkip(1, 1, "2345");
    }

    @Test
    void skipFour() throws Exception {
        testSkip(4, 4, "5");
    }

    @Test
    void skipAll() throws Exception {
        testSkip(5, 5, "");
    }

    @Test
    void skipMore() throws Exception {
        testSkip(10, DATA.length(), "");
    }

    @Test
    void skipMoreThanIntegerMax() throws Exception {
        testSkip(Long.MAX_VALUE, DATA.length(), "");
    }

    private void testSkip(long n, long skipped, String expected) throws Exception {
        assertThat(is.skip(n), is(skipped));
        assertThat((long) is.available(), is(DATA.length() - skipped));
        assertThat(remaining(is), is(expected));
    }

    private static String remaining(InputStream is) {
        try (Scanner scanner = new Scanner(is, US_ASCII.name())) {
            if (!scanner.hasNext()) {
                return "";
            }
            return scanner.next();
        }
    }
}
