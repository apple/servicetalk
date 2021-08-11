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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.serializer.api.Serializer;
import io.servicetalk.serializer.api.StreamingSerializer;

import java.util.function.ToIntFunction;

import static java.util.Objects.requireNonNull;

/**
 * A {@link StreamingSerializer} where the framing is known a-prior in order to deserialize the data.
 * @param <T> The type of objects to serialize.
 */
final class NonFramedStreamingSerializer<T> implements StreamingSerializer<T> {
    private final Serializer<T> serializer;
    private final ToIntFunction<T> bytesEstimator;

    /**
     * Create a new instance.
     * @param serializer The {@link Serializer} to use for each chunk of data.
     * @param bytesEstimator Provide a size estimate for newly allocated {@link Buffer}s to serialize data into.
     */
    NonFramedStreamingSerializer(final Serializer<T> serializer, final ToIntFunction<T> bytesEstimator) {
        this.serializer = requireNonNull(serializer);
        this.bytesEstimator = requireNonNull(bytesEstimator);
    }

    @Override
    public Publisher<Buffer> serialize(final Publisher<T> toSerialize, final BufferAllocator allocator) {
        return toSerialize.map(t -> {
            Buffer buffer = allocator.newBuffer(bytesEstimator.applyAsInt(t));
            serializer.serialize(t, allocator, buffer);
            return buffer;
        });
    }
}
