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
package io.servicetalk.encoding.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.serializer.api.Serializer;
import io.servicetalk.serializer.api.StreamingSerializer;

/**
 * Used to encode buffers for aggregated and streaming use cases.
 */
public interface BufferEncoder {
    /**
     * Get the {@link Serializer} to use for aggregated content.
     * @return the {@link Serializer} to use for aggregated content.
     */
    Serializer<Buffer> encoder();

    /**
     * Get the {@link StreamingSerializer} to use for streaming content.
     * @return the {@link StreamingSerializer} to use for streaming content.
     */
    StreamingSerializer<Buffer> streamingEncoder();

    /**
     * Get the name of the encoding.
     * @return the name of the encoding.
     */
    CharSequence encodingName();
}
