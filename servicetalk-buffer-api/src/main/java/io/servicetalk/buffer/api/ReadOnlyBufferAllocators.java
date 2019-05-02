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
package io.servicetalk.buffer.api;

/**
 * Utility class containing {@link BufferAllocator}s that create {@link Buffer} instances which cannot be modified and
 * are read only.
 */
public final class ReadOnlyBufferAllocators {
    /**
     * Default {@link BufferAllocator} that creates {@link Buffer} instances which cannot be modified and are read only.
     */
    public static final BufferAllocator DEFAULT_RO_ALLOCATOR = ReadOnlyBufferAllocator.PREFER_HEAP_ALLOCATOR;

    /**
     * {@link BufferAllocator} that creates {@link Buffer} instances which prefer heap allocations when otherwise
     * unspecified which cannot be modified and are read only.
     */
    public static final BufferAllocator PREFER_HEAP_RO_ALLOCATOR = ReadOnlyBufferAllocator.PREFER_HEAP_ALLOCATOR;

    /**
     * {@link BufferAllocator} that creates {@link Buffer} instances which prefer off-heap allocations when otherwise
     * unspecified which cannot be modified and are read only.
     */
    public static final BufferAllocator PREFER_DIRECT_RO_ALLOCATOR = ReadOnlyBufferAllocator.PREFER_DIRECT_ALLOCATOR;

    private ReadOnlyBufferAllocators() {
    }
}
