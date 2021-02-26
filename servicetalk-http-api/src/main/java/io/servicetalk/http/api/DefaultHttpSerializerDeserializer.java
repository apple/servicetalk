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
import io.servicetalk.serializer.api.SerializerDeserializer;

import java.util.function.Consumer;
import java.util.function.Predicate;

import static io.servicetalk.http.api.HeaderUtils.deserializeCheckContentType;
import static java.util.Objects.requireNonNull;

final class DefaultHttpSerializerDeserializer<T> implements HttpSerializerDeserializer<T> {
    private final SerializerDeserializer<T> serializer;
    private final Consumer<HttpHeaders> headersSerializeConsumer;
    private final Predicate<HttpHeaders> headersDeserializePredicate;

    DefaultHttpSerializerDeserializer(final SerializerDeserializer<T> serializer,
                                      final Consumer<HttpHeaders> headersSerializeConsumer,
                                      final Predicate<HttpHeaders> headersDeserializePredicate) {
        this.serializer = requireNonNull(serializer);
        this.headersSerializeConsumer = requireNonNull(headersSerializeConsumer);
        this.headersDeserializePredicate = requireNonNull(headersDeserializePredicate);
    }

    @Override
    public T deserialize(final HttpHeaders headers, final BufferAllocator allocator, final Buffer payload) {
        deserializeCheckContentType(headers, headersDeserializePredicate);
        return serializer.deserialize(payload, allocator);
    }

    @Override
    public Buffer serialize(final HttpHeaders headers, final T value, final BufferAllocator allocator) {
        headersSerializeConsumer.accept(headers);
        return serializer.serialize(value, allocator);
    }
}
