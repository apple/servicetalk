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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/**
 * Caches instances of {@link SerializerDeserializer} and {@link StreamingSerializerDeserializer} for
 * <a href="https://developers.google.com/protocol-buffers/">protocol buffer</a>.
 * <p>
 * Use {@link #PROTOBUF} for the default configuration, or construct an instance via
 * {@link #ProtobufSerializerFactory(int)} to bound the maximum size of streaming (VarInt length-prefixed) messages.
 */
public final class ProtobufSerializerFactory {
    /**
     * Singleton instance which creates <a href="https://developers.google.com/protocol-buffers/">protocol buffer</a>
     * serializers; its streaming deserializers warn (rate-limited), rather than rejecting, when a message exceeds the
     * default threshold. Use {@link #ProtobufSerializerFactory(int)} to enforce a limit instead.
     */
    public static final ProtobufSerializerFactory PROTOBUF = new ProtobufSerializerFactory();
    private static final MethodType PARSER_METHOD_TYPE = MethodType.methodType(Parser.class);
    private static final String PARSER_METHOD_NAME = "parser";
    private final Map<Class<?>, Parser<?>> parserMap = new ConcurrentHashMap<>();
    @SuppressWarnings("rawtypes")
    private final Map<Parser<?>, SerializerDeserializer> serializerMap = new ConcurrentHashMap<>();
    @SuppressWarnings("rawtypes")
    private final Map<Parser<?>, StreamingSerializerDeserializer> streamingSerializerMap = new ConcurrentHashMap<>();
    // null => use the streaming serializer's built-in default limit; non-null => the configured limit.
    @Nullable
    private final Integer maxMessageSize;

    private ProtobufSerializerFactory() {
        this.maxMessageSize = null;
    }

    /**
     * Create a factory that bounds the maximum size of messages accepted by its streaming deserializers.
     * @param maxMessageSize The maximum length (in bytes) declared by a streaming frame's length prefix that will be
     * accepted during deserialization. A frame declaring a larger length is rejected before any of its bytes are
     * buffered. {@code 0} disables the limit; {@code -1} warns at the default threshold without rejecting; other
     * negative values are rejected. This applies only to streaming deserialization; single-message serialization is
     * not length-prefixed and is unaffected.
     */
    public ProtobufSerializerFactory(final int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
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
     * Get a {@link SerializerDeserializer}.
     * @param clazz Used to obtain a {@link Parser} which is used to serialize and deserialize.
     * @param <T> The type to serialize and deserialize.
     * @return a {@link SerializerDeserializer}.
     */
    @SuppressWarnings("unchecked")
    public <T extends MessageLite> SerializerDeserializer<T> serializerDeserializer(Class<T> clazz) {
        return serializerDeserializer(
                (Parser<T>) parserMap.computeIfAbsent(clazz, clazz2 -> newParser((Class<T>) clazz2)));
    }

    /**
     * Get a {@link StreamingSerializerDeserializer} which supports &lt;VarInt length, value&gt; encoding as described
     * in <a href="https://developers.google.com/protocol-buffers/docs/techniques">Protobuf Streaming</a>. The
     * deserialized message size is bounded by this factory's configured limit ({@link #PROTOBUF} warns at the default
     * threshold rather than rejecting; configure enforcement via {@link #ProtobufSerializerFactory(int)}).
     * @param parser The {@link Parser} used to serialize and deserialize.
     * @param <T> The type to serialize and deserialize.
     * @return a {@link StreamingSerializerDeserializer} which supports &lt;VarInt length, value&gt; encoding as
     * described in <a href="https://developers.google.com/protocol-buffers/docs/techniques">Protobuf Streaming</a>.
     * @see VarIntLengthStreamingSerializer
     */
    @SuppressWarnings("unchecked")
    public <T extends MessageLite> StreamingSerializerDeserializer<T> streamingSerializerDeserializer(
            Parser<T> parser) {
        return streamingSerializerMap.computeIfAbsent(parser, parser2 -> {
            final SerializerDeserializer<T> sd = serializerDeserializer((Parser<T>) parser2);
            return maxMessageSize == null ?
                    new VarIntLengthStreamingSerializer<>(sd, MessageLite::getSerializedSize) :
                    new VarIntLengthStreamingSerializer<>(sd, MessageLite::getSerializedSize, maxMessageSize);
        });
    }

    /**
     * Get a {@link StreamingSerializerDeserializer} which supports &lt;VarInt length, value&gt; encoding as described
     * in <a href="https://developers.google.com/protocol-buffers/docs/techniques">Protobuf Streaming</a>. The
     * deserialized message size is bounded by this factory's configured limit ({@link #PROTOBUF} warns at the default
     * threshold rather than rejecting; configure enforcement via {@link #ProtobufSerializerFactory(int)}).
     * @param clazz Used to obtain a {@link Parser} which is used to serialize and deserialize.
     * @param <T> The type to serialize and deserialize.
     * @return a {@link StreamingSerializerDeserializer} which supports &lt;VarInt length, value&gt; encoding as
     * described in <a href="https://developers.google.com/protocol-buffers/docs/techniques">Protobuf Streaming</a>.
     * @see VarIntLengthStreamingSerializer
     */
    @SuppressWarnings("unchecked")
    public <T extends MessageLite> StreamingSerializerDeserializer<T> streamingSerializerDeserializer(Class<T> clazz) {
        return streamingSerializerDeserializer(
                (Parser<T>) parserMap.computeIfAbsent(clazz, clazz2 -> newParser((Class<T>) clazz2)));
    }

    @SuppressWarnings("unchecked")
    private <T extends MessageLite> Parser<T> newParser(Class<T> clazz) {
        final MethodHandle mh;
        try {
            mh = MethodHandles.publicLookup().findStatic(clazz, PARSER_METHOD_NAME, PARSER_METHOD_TYPE);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new IllegalArgumentException("Unable to find " + clazz + "." + PARSER_METHOD_NAME, e);
        }
        try {
            return (Parser<T>) mh.invokeExact();
        } catch (Throwable e) {
            throw new IllegalArgumentException(clazz + "." + PARSER_METHOD_NAME + " threw when invoked", e);
        }
    }
}
