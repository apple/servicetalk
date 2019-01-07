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
package io.servicetalk.redis.api;

import org.junit.Test;

import static io.servicetalk.redis.api.StringByteSizeUtil.numberOfBytesUtf8;
import static io.servicetalk.redis.api.StringByteSizeUtil.numberOfDigits;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

public class StringByteSizeUtilTest {
    @Test
    public void testCalculateLengthInt() {
        assertEquals(3, numberOfDigits(-10));
        assertEquals(2, numberOfDigits(-9));
        assertEquals(2, numberOfDigits(-1));
        assertEquals(1, numberOfDigits(0));
        assertEquals(1, numberOfDigits(1));
        assertEquals(1, numberOfDigits(9));
        assertEquals(2, numberOfDigits(10));
        assertEquals(2, numberOfDigits(19));
        assertEquals(2, numberOfDigits(20));
        assertEquals(2, numberOfDigits(99));
        assertEquals(3, numberOfDigits(100));
        assertEquals(3, numberOfDigits(999));
        assertEquals(4, numberOfDigits(1000));
        assertEquals(4, numberOfDigits(9999));
        assertEquals(5, numberOfDigits(10000));
        assertEquals(5, numberOfDigits(99999));
        assertEquals(6, numberOfDigits(100000));
        assertEquals(6, numberOfDigits(999999));
        assertEquals(7, numberOfDigits(1000000));
        assertEquals(7, numberOfDigits(9999999));
        assertEquals(8, numberOfDigits(10000000));
        assertEquals(8, numberOfDigits(99999999));
        assertEquals(9, numberOfDigits(100000000));
        assertEquals(9, numberOfDigits(999999999));
        assertEquals(10, numberOfDigits(1000000000));
        assertEquals(10, numberOfDigits(Integer.MAX_VALUE));
        assertEquals(11, numberOfDigits(Integer.MIN_VALUE));
    }

    @Test
    public void testCalculateLengthLong() {
        assertEquals(3, numberOfDigits(-10L));
        assertEquals(2, numberOfDigits(-9L));
        assertEquals(2, numberOfDigits(-1L));
        assertEquals(1, numberOfDigits(0L));
        assertEquals(1, numberOfDigits(1L));
        assertEquals(1, numberOfDigits(9L));
        assertEquals(2, numberOfDigits(10L));
        assertEquals(2, numberOfDigits(19L));
        assertEquals(2, numberOfDigits(20L));
        assertEquals(2, numberOfDigits(99L));
        assertEquals(3, numberOfDigits(100L));
        assertEquals(3, numberOfDigits(999L));
        assertEquals(4, numberOfDigits(1000L));
        assertEquals(4, numberOfDigits(9999L));
        assertEquals(5, numberOfDigits(10000L));
        assertEquals(5, numberOfDigits(99999L));
        assertEquals(6, numberOfDigits(100000L));
        assertEquals(6, numberOfDigits(999999L));
        assertEquals(7, numberOfDigits(1000000L));
        assertEquals(7, numberOfDigits(9999999L));
        assertEquals(8, numberOfDigits(10000000L));
        assertEquals(8, numberOfDigits(99999999L));
        assertEquals(9, numberOfDigits(100000000L));
        assertEquals(9, numberOfDigits(999999999L));
        assertEquals(10, numberOfDigits(1000000000L));
        assertEquals(10, numberOfDigits(9999999999L));
        assertEquals(11, numberOfDigits(10000000000L));
        assertEquals(11, numberOfDigits(99999999999L));
        assertEquals(12, numberOfDigits(100000000000L));
        assertEquals(12, numberOfDigits(999999999999L));
        assertEquals(13, numberOfDigits(1000000000000L));
        assertEquals(13, numberOfDigits(9999999999999L));
        assertEquals(14, numberOfDigits(10000000000000L));
        assertEquals(14, numberOfDigits(99999999999999L));
        assertEquals(15, numberOfDigits(100000000000000L));
        assertEquals(19, numberOfDigits(Long.MAX_VALUE));
        assertEquals(20, numberOfDigits(Long.MIN_VALUE));
    }

    @Test
    public void testNumberOfBytesUtf8() {
        // This character is copy-pasted from http://www.fileformat.info/info/unicode/char/10400/browsertest.htm
        testCalculatesCorrectLength("\uD801\uDC00".getBytes(UTF_8));

        // This sequence is the "replacement character" used for bad encodings.
        testCalculatesCorrectLength(new byte[]{-17, -65, -67});

        // Some 2 and 3 byte wide characters.
        testCalculatesCorrectLength("ᕈгø⨯у".getBytes(UTF_8));
    }

    private void testCalculatesCorrectLength(final byte[] b) {
        String foo = new String(b, UTF_8);
        final int fooBytes = numberOfBytesUtf8(foo);
        assertEquals(b.length, fooBytes);
        assertEquals(foo.getBytes(UTF_8).length, fooBytes);
    }
}
