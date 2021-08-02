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
import io.servicetalk.oio.api.PayloadWriter;

import static io.servicetalk.concurrent.api.Publisher.fromIterable;

/**
 * Serialize a {@link Publisher} of {@link T} to {@link Publisher} of {@link Buffer}.
 * @param <T> The type of objects that can be serialized.
 */
@FunctionalInterface
public interface StreamingSerializer<T> {
    /**
     * Serialize a {@link Publisher} of {@link T}s into a {@link Publisher} of {@link Buffer}.
     * @param toSerialize the deserialized stream of data represented in a {@link Publisher} of {@link T}.
     * @param allocator the {@link BufferAllocator} to use if allocation is required.
     * @return the serialized stream of data represented in a {@link Publisher} of {@link Buffer}.
     */
    Publisher<Buffer> serialize(Publisher<T> toSerialize, BufferAllocator allocator);

    /**
     * Serialize a {@link Iterable} of {@link T}s into a {@link Iterable} of {@link Buffer}.
     * @param toSerialize the deserialized stream of data represented in a {@link Iterable} of {@link T}.
     * @param allocator the {@link BufferAllocator} to use if allocation is required.
     * @return the serialized stream of data represented in a {@link Iterable} of {@link Buffer}.
     */
    default BlockingIterable<Buffer> serialize(Iterable<T> toSerialize, BufferAllocator allocator) {
        return serialize(fromIterable(toSerialize), allocator).toIterable();
    }

    /**
     * Serialize a {@link PayloadWriter} of {@link T}s into a {@link PayloadWriter} of {@link Buffer}.
     * @param writer The {@link PayloadWriter} used to write the result of serialization to.
     * @param allocator the {@link BufferAllocator} to use if allocation is required.
     * @return a {@link PayloadWriter} where you can write {@link T}s to.
     */
    default PayloadWriter<T> serialize(PayloadWriter<Buffer> writer, BufferAllocator allocator) {
        return StreamingSerializerUtils.serialize(this, writer, allocator);
    }
}
