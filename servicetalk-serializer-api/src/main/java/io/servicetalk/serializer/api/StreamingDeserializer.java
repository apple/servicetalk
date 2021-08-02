/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.serializer.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.api.Publisher;

import static io.servicetalk.concurrent.api.Publisher.fromIterable;

/**
 * Deserialize a {@link Publisher} of {@link Buffer} to {@link Publisher} of {@link T}.
 * @param <T> The type of objects that can be deserialized.
 */
@FunctionalInterface
public interface StreamingDeserializer<T> {
    /**
     * Deserialize a {@link Publisher} of {@link Buffer} into a {@link Publisher} of {@link T}.
     * @param serializedData the serialized stream of data represented in a {@link Publisher} of {@link Buffer}.
     * @param allocator the {@link BufferAllocator} to use if allocation is required.
     * @return The deserialized {@link Publisher} of {@link T}s.
     */
    Publisher<T> deserialize(Publisher<Buffer> serializedData, BufferAllocator allocator);

    /**
     * Deserialize a {@link Iterable} of {@link Buffer} into a {@link Iterable} of {@link T}.
     * @param serializedData the serialized stream data represented in a {@link Iterable} of {@link Buffer}.
     * @param allocator the {@link BufferAllocator} to use if allocation is required.
     * @return The deserialized {@link Iterable} of {@link T}s.
     */
    default BlockingIterable<T> deserialize(Iterable<Buffer> serializedData, BufferAllocator allocator) {
        return deserialize(fromIterable(serializedData), allocator).toIterable();
    }
}
