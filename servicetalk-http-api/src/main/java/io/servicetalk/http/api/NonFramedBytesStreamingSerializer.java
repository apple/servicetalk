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
import io.servicetalk.serializer.api.StreamingSerializer;

final class NonFramedBytesStreamingSerializer implements StreamingSerializer<byte[]> {
    static final StreamingSerializer<byte[]> INSTANCE = new NonFramedBytesStreamingSerializer();

    private NonFramedBytesStreamingSerializer() {
    }

    @Override
    public Publisher<Buffer> serialize(final Publisher<byte[]> toSerialize, final BufferAllocator allocator) {
        return toSerialize.map(allocator::wrap);
    }
}
