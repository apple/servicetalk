/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.buffer.api.BufferAllocator;

/**
 * Available {@link BufferAllocator}s.
 */
public final class BufferAllocators {
    /**
     * Default {@link BufferAllocator} whose {@link Buffer}s are typically backed by Netty buffers.
     */
    public static final BufferAllocator DEFAULT_ALLOCATOR = BufferUtils.PREFER_DIRECT_ALLOCATOR_WITHOUT_ZEROING;

    /**
     * Default {@link BufferAllocator} whose {@link Buffer}s are typically backed by Netty buffers and prefers direct
     * memory allocation when otherwise not specified.
     */
    public static final BufferAllocator PREFER_DIRECT_ALLOCATOR = BufferUtils.PREFER_DIRECT_ALLOCATOR;

    /**
     * Default {@link BufferAllocator} whose {@link Buffer}s are typically backed by Netty buffers and prefers heap
     * memory allocation when otherwise not specified.
     */
    public static final BufferAllocator PREFER_HEAP_ALLOCATOR = BufferUtils.PREFER_HEAP_ALLOCATOR;

    private BufferAllocators() {
        // no instances
    }
}
