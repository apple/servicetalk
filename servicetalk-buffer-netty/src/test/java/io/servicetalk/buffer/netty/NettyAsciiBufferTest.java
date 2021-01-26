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
package io.servicetalk.buffer.netty;

import org.junit.Test;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static org.junit.Assert.assertEquals;

// Netty counter-part for AsciiBufferTest
public class NettyAsciiBufferTest {

    @Test
    public void testSubSequence() {
        testSubSequence(newAsciiString(DEFAULT_ALLOCATOR.fromAscii("some-data")));
    }

    private static void testSubSequence(CharSequence cs) {
        assertEquals("some", cs.subSequence(0, 4));
        assertEquals("data", cs.subSequence(5, 9));
        assertEquals("e-d", cs.subSequence(3, 6));
    }
}
