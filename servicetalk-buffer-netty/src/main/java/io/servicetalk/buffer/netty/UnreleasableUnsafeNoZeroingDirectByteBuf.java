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
/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.servicetalk.buffer.netty;

import io.netty.buffer.ByteBufAllocator;

import java.nio.ByteBuffer;

import static io.servicetalk.utils.internal.PlatformDependent.allocateMemory;
import static io.servicetalk.utils.internal.PlatformDependent.freeMemory;
import static io.servicetalk.utils.internal.PlatformDependent.newDirectBuffer;
import static io.servicetalk.utils.internal.PlatformDependent.reserveMemory;
import static io.servicetalk.utils.internal.PlatformDependent.unreserveMemory;
import static io.servicetalk.utils.internal.ThrowableUtils.throwException;

final class UnreleasableUnsafeNoZeroingDirectByteBuf extends UnreleasableUnsafeDirectByteBuf {

    UnreleasableUnsafeNoZeroingDirectByteBuf(ByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
        super(alloc, initialCapacity, maxCapacity);
    }

    @Override
    protected ByteBuffer allocateDirect(final int initialCapacity) {
        // Calling malloc with capacity of 0 may return a null ptr or a memory address that can be used.
        // Just use 1 to make it safe to use in all cases:
        // See: http://pubs.opengroup.org/onlinepubs/009695399/functions/malloc.html
        final long size = Math.max(1, initialCapacity);
        reserveMemory(size, initialCapacity);

        final long memoryAddress;
        try {
            memoryAddress = allocateMemory(size);
        } catch (final Throwable e) {
            unreserveMemory(size, initialCapacity);
            return throwException(e);
        }

        try {
            return newDirectBuffer(memoryAddress, size, initialCapacity);
        } catch (final Throwable e) {
            freeMemory(memoryAddress);
            unreserveMemory(size, initialCapacity);
            return throwException(e);
        }
    }
}
