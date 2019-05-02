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
package io.servicetalk.buffer.netty;

import io.servicetalk.buffer.api.Buffer;

import org.junit.Test;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.*;

public class ReadOnlyBufferTest {

    @Test
    public void capacity() {
        Buffer buffer = DEFAULT_ALLOCATOR.newBuffer(10).writeBytes("test".getBytes(US_ASCII));
        Buffer readOnly = buffer.asReadOnly();
        assertEquals(buffer.capacity(), readOnly.capacity());
    }

    @Test
    public void maxCapacity() {
        Buffer buffer = DEFAULT_ALLOCATOR.newBuffer(10).writeBytes("test".getBytes(US_ASCII));
        Buffer readOnly = buffer.asReadOnly();
        assertEquals(buffer.maxCapacity(), readOnly.maxCapacity());
    }
}
