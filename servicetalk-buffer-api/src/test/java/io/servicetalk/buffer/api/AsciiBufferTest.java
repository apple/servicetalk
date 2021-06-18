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
package io.servicetalk.buffer.api;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

import static io.servicetalk.buffer.api.CharSequences.caseInsensitiveHashCode;
import static io.servicetalk.buffer.api.CharSequences.contentEquals;
import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static java.lang.Character.toLowerCase;
import static java.lang.Character.toUpperCase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsciiBufferTest {
    @Test
    void testHashCode() {
        for (int i = 0; i < 1000; ++i) {
            StringBuilder sb = new StringBuilder(i);
            for (int x = 0; x < i; ++x) {
                sb.append((char) ThreadLocalRandom.current().nextInt(32, 126));
            }
            verifyCaseInsensitive(sb.toString());
        }
    }

    private static void verifyCaseInsensitive(String s) {
        CharSequence buffer = newAsciiString(s);
        CharSequence buffer2 = newAsciiString(DEFAULT_RO_ALLOCATOR.fromAscii(s));
        verifyDifferentAsciiStringTypes(s, buffer, buffer2);

        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append(toLowerCase(c));
            } else {
                sb.append(toUpperCase(c));
            }
        }
        String flipCase = sb.toString();
        CharSequence bufferFlipCase = newAsciiString(flipCase);
        CharSequence buffer2FlipCase = newAsciiString(DEFAULT_RO_ALLOCATOR.fromAscii(flipCase));
        verifyDifferentAsciiStringTypes(flipCase, bufferFlipCase, buffer2FlipCase);
        if (flipCase.equals(s)) {
            assertEquals(buffer, bufferFlipCase, "failure for " + s + " and " + flipCase);
            assertEquals(buffer2, buffer2FlipCase, "failure for " + s + " and " + flipCase);
        } else {
            assertNotEquals(buffer, bufferFlipCase, "failure for " + s + " and " + flipCase);
            assertNotEquals(buffer2, buffer2FlipCase, "failure for " + s + " and " + flipCase);
        }
        assertTrue(contentEqualsIgnoreCase(buffer, bufferFlipCase), "failure for " + s + " and " + flipCase);
        assertTrue(contentEqualsIgnoreCase(buffer, buffer2FlipCase), "failure for " + s + " and " + flipCase);
        assertTrue(contentEqualsIgnoreCase(s, flipCase), "failure for " + s + " and " + flipCase);
        assertTrue(contentEqualsIgnoreCase(bufferFlipCase, buffer2FlipCase), "failure for " + s + " and " + flipCase);
        assertTrue(contentEqualsIgnoreCase(bufferFlipCase, s), "failure for " + s + " and " + flipCase);
        assertTrue(contentEqualsIgnoreCase(buffer2FlipCase, flipCase), "failure for " + s + " and " + flipCase);
        assertEquals(buffer.hashCode(), bufferFlipCase.hashCode(), "failure for " + s + " and " + flipCase);
        assertEquals(buffer.hashCode(), buffer2FlipCase.hashCode(), "failure for " + s + " and " + flipCase);
        assertEquals(buffer.hashCode(), caseInsensitiveHashCode(s), "failure for " + s + " and " + flipCase);
        assertEquals(buffer.hashCode(), caseInsensitiveHashCode(flipCase), "failure for " + s + " and " + flipCase);
    }

    private static void verifyDifferentAsciiStringTypes(String s, CharSequence buffer, CharSequence buffer2) {
        assertEquals(buffer.hashCode(), buffer2.hashCode(), "failure for " + s);
        assertEquals(buffer.hashCode(), caseInsensitiveHashCode(s), "failure for " + s);
        assertEquals(buffer, buffer2, "failure for " + s);
        assertTrue(contentEqualsIgnoreCase(buffer, buffer2), "failure for " + s);
        assertTrue(contentEqualsIgnoreCase(buffer, s), "failure for " + s);
        assertTrue(contentEqualsIgnoreCase(buffer2, s), "failure for " + s);
        assertTrue(contentEquals(buffer, buffer2), "failure for " + s);
        assertTrue(contentEquals(buffer, s), "failure for " + s);
        assertTrue(contentEquals(buffer2, s), "failure for " + s);
    }

    @Test
    void testSubSequence() {
        testSubSequence(newAsciiString("some-data"));
        testSubSequence(newAsciiString(DEFAULT_RO_ALLOCATOR.fromAscii("some-data")));
    }

    private static void testSubSequence(CharSequence cs) {
        assertEquals("some", cs.subSequence(0, 4));
        assertEquals("data", cs.subSequence(5, 9));
        assertEquals("e-d", cs.subSequence(3, 6));
    }
}
