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
package io.servicetalk.utils.internal;

import org.junit.jupiter.api.Test;

import static io.servicetalk.utils.internal.HttpGrammarUtils.isFieldValueByte;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class HttpGrammarUtilsTest {

    private static final byte HTAB = 9;
    private static final byte DEL = 127;

    @Test
    void isFieldValueByteMatchesFieldValueGrammarForEveryByte() {
        for (int value = Byte.MIN_VALUE; value <= Byte.MAX_VALUE; ++value) {
            final byte b = (byte) value;
            final int unsigned = value & 0xff;
            // reason-phrase / field-value = *( HTAB / SP / VCHAR / obs-text ), see RFC 7230.
            // HTAB = 0x09, SP / VCHAR = 0x20-0x7E, obs-text = 0x80-0xFF. DEL (0x7F) and all other control
            // characters (0x00-0x1F) are illegal.
            final boolean expected = unsigned == HTAB || (unsigned >= 0x20 && unsigned <= 0x7E) || unsigned >= 0x80;
            assertThat("byte 0x" + Integer.toHexString(unsigned), isFieldValueByte(b), is(expected));
        }
    }

    @Test
    void isFieldValueByteRejectsCrLfAndDel() {
        assertThat("CR", isFieldValueByte((byte) '\r'), is(false));
        assertThat("LF", isFieldValueByte((byte) '\n'), is(false));
        assertThat("NUL", isFieldValueByte((byte) 0), is(false));
        assertThat("DEL", isFieldValueByte(DEL), is(false));
    }

    @Test
    void isFieldValueByteAcceptsHtabSpaceAndVchar() {
        assertThat("HTAB", isFieldValueByte(HTAB), is(true));
        assertThat("SP", isFieldValueByte((byte) ' '), is(true));
        assertThat("VCHAR 'A'", isFieldValueByte((byte) 'A'), is(true));
        assertThat("obs-text 0xA9", isFieldValueByte((byte) 0xA9), is(true));
    }
}
