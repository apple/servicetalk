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
package io.servicetalk.serialization.api;

import io.servicetalk.buffer.api.Buffer;

/**
 * A contract capable of serializing a stream of {@link Object}s into a stream of {@link Buffer}s.
 * This interface is designed to be used as a function that can convert an {@link Object} into a {@link Buffer}.
 * {@link #serialize(Object, Buffer)} maybe called multiple times.
 * <p>
 *
 * A {@link StreamingSerializer} implementation may chose to be stateful or stateless. This contract does not assume
 * either.
 * <em>Implementations are assumed to be synchronous.</em>
 * @deprecated Use {@link io.servicetalk.serializer.api.StreamingSerializer}.
 */
@Deprecated
@FunctionalInterface
public interface StreamingSerializer {

    /**
     * Serializes the passed {@link Object} {@code toSerialize} into the passed {@link Buffer} synchronously.
     * @deprecated Use {@link io.servicetalk.serializer.api.StreamingSerializer}.
     * @param toSerialize {@link Object} to serialize.
     * @param destination {@link Buffer} to which the serialized representation of {@code toSerialize} is to be written.
     */
    @Deprecated
    void serialize(Object toSerialize, Buffer destination);
}
