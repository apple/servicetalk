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

import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCounted;
import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.buffer.netty.BufferAllocators;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RefCountedTrapperTest {

    @Test
    public void referenceCountedThrows() {
        EmbeddedChannel channel = new EmbeddedChannel(new TestRefCountedTrapper());
        ReferenceCounted referenceCounted = Unpooled.buffer();
        assertEquals(1, referenceCounted.refCnt());
        try {
            channel.writeInbound(referenceCounted);
            fail();
        } catch (IllegalStateException expected) {
            // expected
        }
        assertEquals(0, referenceCounted.refCnt());
        assertFalse(channel.finish());
    }

    @Test
    public void nonReferenceCountedNotThrows() {
        EmbeddedChannel channel = new EmbeddedChannel(new TestRefCountedTrapper());
        String msg = "ServiceTalk";

        assertTrue(channel.writeInbound(msg));
        assertTrue(channel.finish());

        assertSame(msg, channel.readInbound());
        assertNull(channel.readInbound());
    }

    private static final class TestRefCountedTrapper extends RefCountedTrapper {
        TestRefCountedTrapper() {
            super(BufferAllocators.DEFAULT.getAllocator());
        }

        @Override
        protected Object decode(EventLoop eventLoop, BufferAllocator allocator, Object msg) {
            return msg;
        }
    }
}
