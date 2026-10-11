/*
 * Copyright © 2018-2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.buffer.netty;

import io.servicetalk.buffer.api.Buffer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.servicetalk.buffer.api.CharSequences.caseInsensitiveHashCode;
import static io.servicetalk.buffer.api.CharSequences.contentEquals;
import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.buffer.api.CharSequences.forEachByte;
import static io.servicetalk.buffer.api.CharSequences.indexOf;
import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.buffer.api.CharSequences.parseLong;
import static io.servicetalk.buffer.api.CharSequences.regionMatches;
import static io.servicetalk.buffer.api.CharSequences.split;
import static io.servicetalk.buffer.api.CharSequences.unwrapBuffer;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

// Netty counter-part for AsciiBufferTest
class NettyAsciiBufferTest {
    private static final String PREFIX = "|prefix|";
    private static final String CONTENT = "Some-data, More-data";

    @Test
    void testSubSequence() {
        testSubSequence(newAsciiString(DEFAULT_ALLOCATOR.fromAscii("some-data")));
    }

    private static void testSubSequence(CharSequence cs) {
        assertThat(cs.subSequence(0, 4), is("some"));
        assertThat(cs.subSequence(5, 9), is("data"));
        assertThat(cs.subSequence(3, 6), is("e-d"));
    }

    private static Buffer bufferWithReaderIndex(String content) {
        final Buffer buffer = DEFAULT_ALLOCATOR.fromAscii(PREFIX + content);
        buffer.readerIndex(PREFIX.length());
        return buffer;
    }

    private static CharSequence withReaderIndex(String content) {
        return newAsciiString(bufferWithReaderIndex(content));
    }

    @Test
    void nonZeroReaderIndexLengthAndCharAt() {
        final CharSequence cs = withReaderIndex(CONTENT);
        assertThat(cs.length(), is(CONTENT.length()));
        for (int i = 0; i < CONTENT.length(); ++i) {
            assertThat("charAt(" + i + ')', cs.charAt(i), is(CONTENT.charAt(i)));
        }
        assertThat(cs.toString(), is(CONTENT));
    }

    @Test
    void nonZeroReaderIndexSubSequence() {
        final CharSequence cs = withReaderIndex(CONTENT);
        assertThat(cs.subSequence(0, 4).toString(), is("Some"));
        assertThat(cs.subSequence(11, CONTENT.length()).toString(), is("More-data"));
        assertThat(cs.subSequence(0, CONTENT.length()).toString(), is(CONTENT));
    }

    @Test
    void nonZeroReaderIndexIndexOf() {
        final CharSequence cs = withReaderIndex(CONTENT);
        assertThat(indexOf(cs, '-', 0), is(CONTENT.indexOf('-')));
        assertThat(indexOf(cs, '-', 5), is(CONTENT.indexOf('-', 5)));
        assertThat("must not find a char outside of readable bytes", indexOf(cs, '|', 0), is(-1));
    }

    @Test
    void nonZeroReaderIndexForEachByte() {
        final CharSequence cs = withReaderIndex(CONTENT);
        final int commaIdx = forEachByte(cs, b -> b != ',');
        assertThat(commaIdx, is(CONTENT.indexOf(',')));
        assertThat(cs.charAt(commaIdx), is(','));

        final StringBuilder visited = new StringBuilder();
        assertThat(forEachByte(cs, b -> {
            visited.append((char) b);
            return true;
        }), is(-1));
        assertThat(visited.toString(), is(CONTENT));
    }

    @Test
    void nonZeroReaderIndexSplit() {
        final CharSequence cs = withReaderIndex(CONTENT);
        assertThat(toStrings(split(cs, ',', false)), is(asList("Some-data", " More-data")));
        assertThat(toStrings(split(cs, ',', true)), is(asList("Some-data", "More-data")));
    }

    @Test
    void nonZeroReaderIndexRegionMatches() {
        final CharSequence cs = withReaderIndex(CONTENT);
        assertThat(regionMatches(cs, false, 5, "data", 0, 4), is(true));
        assertThat(regionMatches(cs, true, 0, "SOME", 0, 4), is(true));
    }

    @Test
    void nonZeroReaderIndexEqualityAndHashCode() {
        final CharSequence cs = withReaderIndex(CONTENT);
        final CharSequence expected = newAsciiString(CONTENT);
        assertThat(contentEquals(cs, CONTENT), is(true));
        assertThat(contentEquals(CONTENT, cs), is(true));
        assertThat(contentEquals(cs, expected), is(true));
        assertThat(contentEqualsIgnoreCase(cs, "SOME-DATA, MORE-DATA"), is(true));
        assertThat(contentEquals(cs, PREFIX + CONTENT), is(false));
        assertThat(cs, is(expected));
        assertThat(expected, is(cs));
        assertThat(cs.hashCode(), is(expected.hashCode()));
        assertThat(cs.hashCode(), is(caseInsensitiveHashCode(CONTENT)));
    }

    @Test
    void nonZeroReaderIndexParseLong() {
        assertThat(parseLong(withReaderIndex("-42")), is(-42L));
    }

    @Test
    void nonZeroReaderIndexUnwrapBuffer() {
        final Buffer unwrapped = unwrapBuffer(withReaderIndex(CONTENT));
        assertThat(unwrapped, is(notNullValue()));
        assertThat(unwrapped.readerIndex(), is(0));
        assertThat(unwrapped.toString(US_ASCII), is(CONTENT));
    }

    @Test
    void nonZeroReaderIndexIsDecoupledFromOriginalBuffer() {
        final Buffer buffer = bufferWithReaderIndex(CONTENT);
        final CharSequence cs = newAsciiString(buffer);
        assertThat("readerIndex of the original buffer must not change", buffer.readerIndex(), is(PREFIX.length()));

        buffer.readerIndex(buffer.writerIndex());
        assertThat(cs.length(), is(CONTENT.length()));
        assertThat(cs.toString(), is(CONTENT));
    }

    private static List<String> toStrings(List<CharSequence> list) {
        final List<String> result = new ArrayList<>(list.size());
        for (CharSequence cs : list) {
            result.add(cs.toString());
        }
        return result;
    }
}
