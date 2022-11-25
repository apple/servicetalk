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

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class BufferInputStreamTest {

    private static final String DATA = "12345";
    private static final int BYTE_COUNT = getBytes(DATA).length;
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

    @Test
    void markSupported() {
        assertThat(is.markSupported(), is(true));
    }

    @Test
    void ignoreResetCalledBeforeMark() {
        assertDoesNotThrow(is::reset);
    }

    @Test
    void readAndResetToLastMark() throws IOException {
        testReadAndReset(2, 6, 2);
    }

    @Test
    void skipAndResetToLastMark() throws IOException {
        testSkipAndReset(2, 6, 2L, getBytes("5"));
    }

    @Test
    void markAndResetToStartOfStream() throws IOException {
        testReadAndReset(0, 6, BYTE_COUNT);
    }

    @Test
    void markAndResetToEndOfStream() throws IOException {
        testReadAndReset(BYTE_COUNT, 6, 1);
        assertThat(is.read(), is(-1));
    }

    @Test
    void ignoreReadlimit() {
        assertDoesNotThrow(() -> testReadAndReset(2, 1, 2));
    }

    @Test
    void ignoreSkipExceedsReadlimit() {
        assertDoesNotThrow(() -> testSkipAndReset(2, 1, 2, "".getBytes(UTF_8)));
    }

    @Test
    void markClosedStream() throws IOException {
        try {
            is.close();
           } finally {
            is.mark(6);
        }
    }

    @Test
    void resetClosedStream() throws IOException {
        try {
            is.mark(6);
            is.close();
        } finally {
            is.reset();
        }
    }

    private static String remaining(InputStream is) {
        try (Scanner scanner = new Scanner(is, US_ASCII.name())) {
            if (!scanner.hasNext()) {
                return "";
            }
            return scanner.next();
        }
    }

    private static byte[] getBytes(final String value) {
        return value.getBytes(UTF_8);
    }

    private static byte[] readBytes(int count, final InputStream is) throws IOException {
        final byte[] b = new byte[count];
        int totalBytesRead = 0;
        while (totalBytesRead < count) {
            final int bytesRead = is.read(b, totalBytesRead, count);
            if (bytesRead == -1) {
                return b;
            } else {
                totalBytesRead += bytesRead;
            }
        }
        return b;
    }

    private void testReadAndReset(final int initialReadCount, final int readLimit, final int readCount) throws
            IOException {
        readBytes(initialReadCount, is);
        is.mark(readLimit);
        final byte[] firstRead = readBytes(readCount, is);
        is.reset();
        final byte[] secondRead = readBytes(readCount, is);
        assertThat(firstRead, is(secondRead));
    }

    private void testSkipAndReset(final long initialSkipCount, final int readLimit, final long skipCount,
                                  final byte[] expected) throws IOException {
        skipBytes(initialSkipCount, is);
        is.mark(readLimit);
        skipBytes(skipCount, is);
        is.reset();
        skipBytes(skipCount, is);
        final byte[] actual = readBytes(expected.length, is);
        assertThat(actual, is(expected));
    }

    private static void skipBytes(final long n, final InputStream inputStream) throws IOException {
        long totalBytesSkipped = 0;
        while (totalBytesSkipped < n) {
            final long bytesSkipped = inputStream.skip(n);
            if (bytesSkipped == 0) {
                return;
            } else {
                totalBytesSkipped += bytesSkipped;
            }
        }
    }
}
