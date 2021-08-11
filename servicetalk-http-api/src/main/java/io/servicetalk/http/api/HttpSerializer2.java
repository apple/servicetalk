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
 * A factory to address serialization concerns for HTTP request/response payload bodies.
 * @param <T> The type of objects to serialize.
 */
@FunctionalInterface
public interface HttpSerializer2<T> {
    /**
     * Serialize an object of type {@link T} into a {@link Buffer}. If necessary the {@link HttpHeaders} should be
     * updated to indicate the <a href="https://tools.ietf.org/html/rfc7231#section-3.1.1.5">content-type</a>.
     *
     * @param headers The {@link HttpHeaders} associated with the serialization operation.
     * @param value The object to serialize.
     * @param allocator The {@link BufferAllocator} used to create the returned {@link Buffer}.
     * @return The result of the serialization operation.
     */
    Buffer serialize(HttpHeaders headers, T value, BufferAllocator allocator);
}
