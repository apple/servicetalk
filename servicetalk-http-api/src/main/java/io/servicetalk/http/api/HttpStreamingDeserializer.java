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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.api.Publisher;

/**
 * HTTP request/response deserialization for streaming payload bodies.
 * @param <T> The type of objects to serialize.
 */
public interface HttpStreamingDeserializer<T> {
    /**
     * Deserialize a {@link Publisher} of {@link Object}s into a {@link Publisher} of type {@link T}.
     * @param headers The {@link HttpHeaders} associated with the {@code payload}.
     * @param payload Provides the {@link Object}s to deserialize.
     * @param allocator Used to allocate {@link Buffer}s if necessary during deserialization.
     * @return a {@link Publisher} of type {@link T} which is the result of the deserialization.
     */
    Publisher<T> deserialize(HttpHeaders headers, Publisher<Buffer> payload, BufferAllocator allocator);

    /**
     * Deserialize a {@link BlockingIterable} of {@link Object}s into a {@link BlockingIterable} of type {@link T}.
     * @param headers The {@link HttpHeaders} associated with the {@code payload}.
     * @param payload Provides the {@link Object}s to deserialize. The contents are assumed to be in memory, otherwise
     * this method may block.
     * @param allocator Used to allocate {@link Buffer}s if necessary during deserialization.
     * @return a {@link BlockingIterable} of type {@link T} which is the result of the deserialization.
     */
    default BlockingIterable<T> deserialize(HttpHeaders headers, BlockingIterable<Buffer> payload,
                                            BufferAllocator allocator) {
        return deserialize(headers, Publisher.fromIterable(payload), allocator).toIterable();
    }
}
