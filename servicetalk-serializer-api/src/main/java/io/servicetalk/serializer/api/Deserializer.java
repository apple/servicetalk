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
 * Deserialize objects from {@link Buffer} to {@link T}.
 * @param <T> The type of objects that can be deserialized.
 */
@FunctionalInterface
public interface Deserializer<T> {
    /**
     * Deserialize the contents from the {@link Buffer} parameter.
     * <p>
     * The caller is responsible for assuming the buffer contents contains enough {@link Buffer#readableBytes()} to
     * successfully deserialize.
     * @param serializedData {@link Buffer} whose {@link Buffer#readableBytes()} contains a serialized object. The
     * {@link Buffer#readerIndex()} will be advanced to indicate the content which has been consumed.
     * @param allocator Used to allocate intermediate {@link Buffer}s if required.
     * @return The result of the deserialization.
     */
    T deserialize(Buffer serializedData, BufferAllocator allocator);
}
