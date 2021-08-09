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
package io.servicetalk.data.protobuf;

import io.servicetalk.serializer.api.SerializerDeserializer;
import io.servicetalk.serializer.api.StreamingSerializerDeserializer;
import io.servicetalk.serializer.utils.VarIntLengthStreamingSerializer;

import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches instances of {@link SerializerDeserializer} and {@link StreamingSerializerDeserializer} for
 * <a href="https://developers.google.com/protocol-buffers/">protocol buffer</a>.
 */
public final class ProtobufSerializerFactory {
    /**
     * Singleton instance which creates <a href="https://developers.google.com/protocol-buffers/">protocol buffer</a>
     * serializers.
     */
    public static final ProtobufSerializerFactory PROTOBUF = new ProtobufSerializerFactory();
    @SuppressWarnings("rawtypes")
    private final Map<Parser<?>, SerializerDeserializer> serializerMap = new ConcurrentHashMap<>();
    @SuppressWarnings("rawtypes")
    private final Map<Parser<?>, StreamingSerializerDeserializer> streamingSerializerMap = new ConcurrentHashMap<>();

    private ProtobufSerializerFactory() {
    }

    /**
     * Get a {@link SerializerDeserializer}.
     * @param parser The {@link Parser} used to serialize and deserialize.
     * @param <T> The type to serialize and deserialize.
     * @return a {@link SerializerDeserializer}.
     */
    @SuppressWarnings("unchecked")
    public <T extends MessageLite> SerializerDeserializer<T> serializerDeserializer(Parser<T> parser) {
        return serializerMap.computeIfAbsent(parser, parser2 -> new ProtobufSerializer<>((Parser<T>) parser2));
    }

    /**
     * Get a {@link StreamingSerializerDeserializer} which supports &lt;VarInt length, value&gt; encoding as described
     * in <a href="https://developers.google.com/protocol-buffers/docs/techniques">Protobuf Streaming</a>.
     * @param parser The {@link Parser} used to serialize and deserialize.
     * @param <T> The type to serialize and deserialize.
     * @return a {@link StreamingSerializerDeserializer} which supports &lt;VarInt length, value&gt; encoding as
     * described in <a href="https://developers.google.com/protocol-buffers/docs/techniques">Protobuf Streaming</a>.
     * @see VarIntLengthStreamingSerializer
     */
    @SuppressWarnings("unchecked")
    public <T extends MessageLite> StreamingSerializerDeserializer<T> streamingSerializerDeserializer(
            Parser<T> parser) {
        return streamingSerializerMap.computeIfAbsent(parser,
                parser2 -> new VarIntLengthStreamingSerializer<>(serializerDeserializer((Parser<T>) parser2),
                        MessageLite::getSerializedSize));
    }
}
