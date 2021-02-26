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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.api.Publisher;

/**
 * A factory to address deserialization concerns for HTTP request/response payload bodies.
 * @deprecated Use {@link HttpDeserializer2} or {@link HttpStreamingDeserializer}.
 * @param <T> The type of objects to deserialize.
 */
@Deprecated
public interface HttpDeserializer<T> {
    /**
     * Deserialize a single {@link Object} into a {@link T}.
     * @param headers The {@link HttpHeaders} associated with the {@code payload}.
     * @param payload The {@link Object} to deserialize. The contents are assumed to be in memory, otherwise this method
     * may block.
     * @return The result of the deserialization.
     */
    T deserialize(HttpHeaders headers, Buffer payload);

    /**
     * Deserialize a {@link BlockingIterable} of {@link Object}s into a {@link BlockingIterable} of type {@link T}.
     *
     * @param headers The {@link HttpHeaders} associated with the {@code payload}.
     * @param payload Provides the {@link Object}s to deserialize. The contents are assumed to be in memory, otherwise
     * this method may block.
     * @return a {@link BlockingIterable} of type {@link T} which is the result of the deserialization.
     */
    BlockingIterable<T> deserialize(HttpHeaders headers, BlockingIterable<Buffer> payload);

    /**
     * Deserialize a {@link Publisher} of {@link Object}s into a {@link Publisher} of type {@link T}.
     *
     * @param headers The {@link HttpHeaders} associated with the {@code payload}.
     * @param payload Provides the {@link Object}s to deserialize.
     * @return a {@link Publisher} of type {@link T} which is the result of the deserialization.
     */
    Publisher<T> deserialize(HttpHeaders headers, Publisher<Buffer> payload);
}
