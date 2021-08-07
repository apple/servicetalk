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

/**
 * Serialize from {@link T} to {@link Buffer}.
 * @param <T> The type of objects that can be serialized.
 */
@FunctionalInterface
public interface Serializer<T> {
    /**
     * Serialize the {@link T} parameter to the {@link Buffer} parameter.
     * @param toSerialize The {@link T} to serialize.
     * @param allocator Used to allocate intermediate {@link Buffer}s if required.
     * @param buffer Where the results of the serialization will be written to.
     */
    void serialize(T toSerialize, BufferAllocator allocator, Buffer buffer);

    /**
     * Serialize the {@link T} parameter to a {@link Buffer}.
     * @param toSerialize The {@link T} to serialize.
     * @param allocator Used to allocate the buffer to serialize to.
     * @return The results of the serialization.
     */
    default Buffer serialize(T toSerialize, BufferAllocator allocator) {
        Buffer b = allocator.newBuffer();
        serialize(toSerialize, allocator, b);
        return b;
    }
}
