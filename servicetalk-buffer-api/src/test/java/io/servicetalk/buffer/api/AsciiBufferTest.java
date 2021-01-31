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

import org.junit.Test;

import java.util.concurrent.ThreadLocalRandom;

import static io.servicetalk.buffer.api.CharSequences.caseInsensitiveHashCode;
import static io.servicetalk.buffer.api.CharSequences.contentEquals;
import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static java.lang.Character.toLowerCase;
import static java.lang.Character.toUpperCase;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class AsciiBufferTest {
    @Test
    public void testHashCode() {
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
            assertEquals("failure for " + s + " and " + flipCase, buffer, bufferFlipCase);
            assertEquals("failure for " + s + " and " + flipCase, buffer2, buffer2FlipCase);
        } else {
            assertNotEquals("failure for " + s + " and " + flipCase, buffer, bufferFlipCase);
            assertNotEquals("failure for " + s + " and " + flipCase, buffer2, buffer2FlipCase);
        }
        assertTrue("failure for " + s + " and " + flipCase, contentEqualsIgnoreCase(buffer, bufferFlipCase));
        assertTrue("failure for " + s + " and " + flipCase, contentEqualsIgnoreCase(buffer, buffer2FlipCase));
        assertTrue("failure for " + s + " and " + flipCase, contentEqualsIgnoreCase(s, flipCase));
        assertTrue("failure for " + s + " and " + flipCase, contentEqualsIgnoreCase(bufferFlipCase, buffer2FlipCase));
        assertTrue("failure for " + s + " and " + flipCase, contentEqualsIgnoreCase(bufferFlipCase, s));
        assertTrue("failure for " + s + " and " + flipCase, contentEqualsIgnoreCase(buffer2FlipCase, flipCase));
        assertEquals("failure for " + s + " and " + flipCase, buffer.hashCode(), bufferFlipCase.hashCode());
        assertEquals("failure for " + s + " and " + flipCase, buffer.hashCode(), buffer2FlipCase.hashCode());
        assertEquals("failure for " + s + " and " + flipCase, buffer.hashCode(), caseInsensitiveHashCode(s));
        assertEquals("failure for " + s + " and " + flipCase, buffer.hashCode(), caseInsensitiveHashCode(flipCase));
    }

    private static void verifyDifferentAsciiStringTypes(String s, CharSequence buffer, CharSequence buffer2) {
        assertEquals("failure for " + s, buffer.hashCode(), buffer2.hashCode());
        assertEquals("failure for " + s, buffer.hashCode(), caseInsensitiveHashCode(s));
        assertEquals("failure for " + s, buffer, buffer2);
        assertTrue("failure for " + s, contentEqualsIgnoreCase(buffer, buffer2));
        assertTrue("failure for " + s, contentEqualsIgnoreCase(buffer, s));
        assertTrue("failure for " + s, contentEqualsIgnoreCase(buffer2, s));
        assertTrue("failure for " + s, contentEquals(buffer, buffer2));
        assertTrue("failure for " + s, contentEquals(buffer, s));
        assertTrue("failure for " + s, contentEquals(buffer2, s));
    }

    @Test
    public void testSubSequence() {
        testSubSequence(newAsciiString("some-data"));
        testSubSequence(newAsciiString(DEFAULT_RO_ALLOCATOR.fromAscii("some-data")));
    }

    private static void testSubSequence(CharSequence cs) {
        assertEquals("some", cs.subSequence(0, 4));
        assertEquals("data", cs.subSequence(5, 9));
        assertEquals("e-d", cs.subSequence(3, 6));
    }
}
