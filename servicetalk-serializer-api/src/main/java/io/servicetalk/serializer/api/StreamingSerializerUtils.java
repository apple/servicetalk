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
import io.servicetalk.concurrent.api.internal.ConnectablePayloadWriter;
import io.servicetalk.oio.api.PayloadWriter;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

final class StreamingSerializerUtils {
    private StreamingSerializerUtils() {
    }

    static <T> PayloadWriter<T> serialize(StreamingSerializer<T> serializer, PayloadWriter<Buffer> writer,
                                          BufferAllocator allocator) {
        ConnectablePayloadWriter<T> connectablePayloadWriter = new ConnectablePayloadWriter<>();
        serializer.serialize(connectablePayloadWriter.connect(), allocator)
                // forEach is safe because:
                // - backpressure is applied on thread calling PayloadWriter<Buffer> methods
                // - terminal events are delivered via the returned PayloadWriter<T> to writer
                .forEach(buffer -> {
                    try {
                        writer.write(requireNonNull(buffer));
                    } catch (IOException e) {
                        throw new SerializationException(e);
                    }
                });
        return new PayloadWriter<T>() {
            @Override
            public void write(final T t) throws IOException {
                connectablePayloadWriter.write(t);
            }

            @Override
            public void close(final Throwable cause) throws IOException {
                try {
                    connectablePayloadWriter.close(cause);
                } finally {
                    writer.close(cause);
                }
            }

            @Override
            public void close() throws IOException {
                try {
                    connectablePayloadWriter.close();
                } finally {
                    writer.close();
                }
            }

            @Override
            public void flush() throws IOException {
                try {
                    connectablePayloadWriter.flush();
                } finally {
                    writer.flush();
                }
            }
        };
    }
}
