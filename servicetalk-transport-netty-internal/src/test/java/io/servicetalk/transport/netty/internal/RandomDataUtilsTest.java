/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class RandomDataUtilsTest {
    @Test
    public void randomStringOfLength() {
        testRandomStringOfLength(0);
        testRandomStringOfLength(1);
        testRandomStringOfLength(2);
        testRandomStringOfLength(3);

        testRandomStringOfLength(20_000);
    }

    private static void testRandomStringOfLength(int lengthInUtf8Bytes) {
        String s = RandomDataUtils.randomCharSequenceOfByteLength(lengthInUtf8Bytes).toString();
        assertThat(s, s.getBytes(UTF_8).length, is(lengthInUtf8Bytes));
    }
}
