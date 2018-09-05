/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.CloseableIterable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.serialization.api.SerializationException;
import io.servicetalk.serialization.api.TypeHolder;

import java.util.function.BiFunction;

/**
 * A provider for {@link HttpDeserializer}s.
 */
public interface HttpDeserializerProvider {

    // TODO(scott): merge this interface with HttpSerializerProvider? HttpSerializerProvider may have state like
    // byteEstimator which may not be shared with HttpDeserializerProvider.

    /**
     * Get a {@link HttpDeserializer} for a {@link Class} of type {@link T}.
     * @param type The {@link Class} type that the return value will serialize.
     * @param <T> The type of object to serialize.
     * @return a {@link HttpDeserializer} for a {@link Class} of type {@link T}.
     */
    <T> HttpDeserializer<T> getDeserializer(Class<T> type);

    /**
     * Get a {@link HttpDeserializer} for a {@link TypeHolder} of type {@link T}.
     * @param type The {@link TypeHolder} type that the return value will serialize.
     * @param <T> The type of object to serialize.
     * @return a {@link HttpDeserializer} for a {@link TypeHolder} of type {@link T}.
     */
    <T> HttpDeserializer<T> getDeserializer(TypeHolder<T> type);
}
