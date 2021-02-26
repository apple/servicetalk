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
 * Serialize a {@link Publisher} of {@link Object} to {@link Publisher} of {@link Buffer}.
 * @param <T> The type of objects that can be serialized.
 */
@FunctionalInterface
public interface StreamingSerializer<T> {
    Publisher<Buffer> serialize(Publisher<T> toSerialize, BufferAllocator allocator);

    default BlockingIterable<Buffer> serialize(Iterable<T> toSerialize, BufferAllocator allocator) {
        return serialize(fromIterable(toSerialize), allocator).toIterable();
    }

    default PayloadWriter<T> serialize(PayloadWriter<Buffer> writer, BufferAllocator allocator) {
        return StreamingSerializerUtils.serialize(this, writer, allocator);
    }
}
