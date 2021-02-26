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

/**
 * A factory to address deserialization concerns for HTTP request/response payload bodies.
 * @param <T> The type of objects to deserialize.
 */
public interface HttpDeserializer2<T> {
    /**
     * Deserialize a single {@link Object} into a {@link T}.
     * @param headers The {@link HttpHeaders} associated with the {@code payload}.
     * @param allocator Used to allocate intermediate {@link Buffer}s if required.
     * @param payload The {@link Object} to deserialize. The contents are assumed to be in memory, otherwise this method
     * may block.
     * @return The result of the deserialization.
     */
    T deserialize(HttpHeaders headers, BufferAllocator allocator, Buffer payload);
}
