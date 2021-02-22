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
package io.servicetalk.transport.netty.internal;

import org.junit.Test;

import static io.servicetalk.transport.netty.internal.RandomDataUtils.randomCharSequenceOfByteLength;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class RandomDataUtilsTest {
    @Test
    public void charSequenceOfByteLength() {
        for (int i = 0; i < 128; i++) {
            testRandomCharSequenceOfByteLength(i);
        }

        testRandomCharSequenceOfByteLength(32_768);
    }

    private static void testRandomCharSequenceOfByteLength(int lengthInUtf8Bytes) {
        String s = randomCharSequenceOfByteLength(lengthInUtf8Bytes).toString();
        assertThat(s.getBytes(UTF_8).length, is(lengthInUtf8Bytes));
    }
}
