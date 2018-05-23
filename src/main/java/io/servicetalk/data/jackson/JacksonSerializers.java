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
package io.servicetalk.data.jackson;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.core.async.ByteBufferFeeder;
import com.fasterxml.jackson.core.async.NonBlockingInputFeeder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

import static com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;
import static io.servicetalk.buffer.api.Buffer.asOutputStream;
import static io.servicetalk.concurrent.internal.PlatformDependent.throwException;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * JSON serialization and deserialization based upon the jackson library.
 */
public final class JacksonSerializers {
    private static final int DEFAULT_SERIALIZATION_SIZE_BYTES_ESTIMATE = 512;
    private JacksonSerializers() {
        // no instances
    }

    /**
     * Create a {@link Function} that can serialize a stream of objects from {@link T} to {@link Buffer}.
     * @param mapper Used to map {@link Buffer} to {@link T} when there is enough data.
     * @param type The class for {@link T}. This helps avoid having to explicitly specify generics arguments on the
     * {@link #serializer(ObjectWriter, BufferAllocator)} in cases where it cannot be automatically inferred.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param <T> The data type to serialize.
     * @return A {@link Function} that can serialize a stream of objects from {@link T} to {@link Buffer}.
     */
    public static <T> Function<Publisher<T>, Publisher<Buffer>> serializer(ObjectMapper mapper,
                                                                           Class<T> type,
                                                                           BufferAllocator allocator) {
        return serializer(mapper.writerFor(type), allocator);
    }

    /**
     * Create a {@link Function} that can serialize a stream of objects from {@link T} to {@link Buffer}.
     * @param mapper Used to map {@link Buffer} to {@link T} when there is enough data.
     * @param typeReference The type for {@link T}. This helps avoid having to explicitly specify generics arguments on
     * the {@link #serializer(ObjectWriter, BufferAllocator)} in cases where it cannot be automatically inferred.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param <T> The data type to serialize.
     * @return A {@link Function} that can serialize a stream of objects from {@link T} to {@link Buffer}.
     */
    public static <T> Function<Publisher<T>, Publisher<Buffer>> serializer(ObjectMapper mapper,
                                                                           TypeReference<T> typeReference,
                                                                           BufferAllocator allocator) {
        return serializer(mapper.writerFor(typeReference), allocator);
    }

    /**
     * Create a {@link Function} that can serialize a stream of objects from {@link T} to {@link Buffer}.
     * @param writer used to serialize objects of type {@link T}.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param <T> The data type to serialize.
     * @return A {@link Function} that can serialize a stream of objects from {@link T} to {@link Buffer}.
     */
    public static <T> Function<Publisher<T>, Publisher<Buffer>> serializer(ObjectWriter writer,
                                                                           BufferAllocator allocator) {
        return serializer(writer, allocator, DEFAULT_SERIALIZATION_SIZE_BYTES_ESTIMATE);
    }

    /**
     * Create a {@link Function} that can serialize a stream of objects from {@link T} to {@link Buffer}.
     * @param mapper Used to map {@link Buffer} to {@link T} when there is enough data.
     * @param type The class for {@link T}. This helps avoid having to explicitly specify generics arguments on the
     * {@link #serializer(ObjectWriter, BufferAllocator, int)} in cases where it cannot be automatically inferred.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param bytesEstimate An estimate as to how many bytes each serialization will require.
     * @param <T> The data type to serialize.
     * @return A {@link Function} that can serialize a stream of objects from {@link T} to {@link Buffer}.
     */
    public static <T> Function<Publisher<T>, Publisher<Buffer>> serializer(ObjectMapper mapper,
                                                                           Class<T> type,
                                                                           BufferAllocator allocator,
                                                                           int bytesEstimate) {
        return serializer(mapper.writerFor(type), allocator, bytesEstimate);
    }

    /**
     * Create a {@link Function} that can serialize a stream of objects from {@link T} to {@link Buffer}.
     * @param mapper Used to map {@link Buffer} to {@link T} when there is enough data.
     * @param typeReference The type for {@link T}. This helps avoid having to explicitly specify generics arguments on
     * the {@link #serializer(ObjectWriter, BufferAllocator, int)} in cases where it cannot be automatically inferred.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param bytesEstimate An estimate as to how many bytes each serialization will require.
     * @param <T> The data type to serialize.
     * @return A {@link Function} that can serialize a stream of objects from {@link T} to {@link Buffer}.
     */
    public static <T> Function<Publisher<T>, Publisher<Buffer>> serializer(ObjectMapper mapper,
                                                                           TypeReference<T> typeReference,
                                                                           BufferAllocator allocator,
                                                                           int bytesEstimate) {
        return serializer(mapper.writerFor(typeReference), allocator, bytesEstimate);
    }

    /**
     * Create a {@link Function} that can serialize a stream of objects from {@link T} to {@link Buffer}.
     * @param writer used to serialize objects of type {@link T}.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param bytesEstimate An estimate as to how many bytes each serialization will require.
     * @param <T> The data type to serialize.
     * @return A {@link Function} that can serialize a stream of objects from {@link T} to {@link Buffer}.
     */
    public static <T> Function<Publisher<T>, Publisher<Buffer>> serializer(ObjectWriter writer,
                                                                           BufferAllocator allocator,
                                                                           int bytesEstimate) {
        return pojoBody -> pojoBody.map(new PojoToBytesMapper<>(writer, allocator, bytesEstimate));
    }

    /**
     * Create a {@link Function} that can deserialize a stream of encoded {@link Buffer}s to objects of type
     * {@link T}.
     * @param factory The factory used to do the streaming deserialization. This will break the stream down into tokens.
     * @param mapper Used to map {@link Buffer} to {@link T} when there is enough data.
     * @param typeReference The type for {@link T}. This helps avoid having to explicitly specify generics arguments on
     * the {@link #deserializer(JsonFactory, ObjectReader)} in cases where it cannot be automatically inferred.
     * @param <T> The data type to deserialize.
     * @return A {@link Function} that can deserialize a stream of encoded {@link Buffer}s to objects of type
     * {@link T}.
     */
    public static <T> Function<Publisher<Buffer>, Publisher<T>> deserializer(JsonFactory factory,
                                                                             ObjectMapper mapper,
                                                                             TypeReference<T> typeReference) {
        return deserializer(factory, mapper.readerFor(typeReference));
    }

    /**
     * Create a {@link Function} that can deserialize a stream of encoded {@link Buffer}s to objects of type
     * {@link T}.
     * @param factory The factory used to do the streaming deserialization. This will break the stream down into tokens.
     * @param mapper Used to map {@link Buffer} to {@link T} when there is enough data.
     * @param type The class for {@link T}. This helps avoid having to explicitly specify generics arguments on the
     * {@link #deserializer(JsonFactory, ObjectReader)} in cases where it cannot be automatically inferred.
     * @param <T> The data type to deserialize.
     * @return A {@link Function} that can deserialize a stream of encoded {@link Buffer}s to objects of type
     * {@link T}.
     */
    public static <T> Function<Publisher<Buffer>, Publisher<T>> deserializer(JsonFactory factory,
                                                                             ObjectMapper mapper,
                                                                             Class<T> type) {
        return deserializer(factory, mapper.readerFor(type));
    }

    /**
     * Create a {@link Function} that can deserialize a stream of encoded {@link Buffer}s to objects of type
     * {@link T}.
     * @param factory The factory used to do the streaming deserialization. This will break the stream down into tokens.
     * @param reader A reader which knows about the type {@link T} and translates from streaming tokens to an instance
     * of {@link T}.
     * @param <T> The data type to deserialize.
     * @return A {@link Function} that can deserialize a stream of encoded {@link Buffer}s to objects of type
     * {@link T}.
     */
    public static <T> Function<Publisher<Buffer>, Publisher<T>> deserializer(JsonFactory factory,
                                                                             ObjectReader reader) {
        // The AsyncJsonDecoder will be used to buffer data in between HttpPayloadChunks. It is not thread safe but the
        // concatMap should ensure there is no concurrency, and will ensure visibility when transitioning between
        // HttpPayloadChunks.
        AsyncJsonDecoder asyncJsonDecoder = AsyncJsonDecoder.newInstance(factory);
        return chunkBody -> chunkBody.concatMapIterable(buffer -> {
            List<T> deserializedPojos = new ArrayList<>(2);
            try {
                asyncJsonDecoder.decode(buffer, node -> {
                    try {
                        deserializedPojos.add(reader.readValue(node));
                    } catch (IOException e) {
                        // Re-throw the exception because we don't want to parse any more data
                        throwException(e);
                    }
                });
            } catch (IOException e) {
                // Re-throw the exception because we don't want to parse any more data
                throwException(e);
            }
            return deserializedPojos;
        }).doAfterComplete(asyncJsonDecoder::endOfInput);
    }

    private interface AsyncJsonDecoder {
        /**
         * Add a new {@link Buffer} to the decode operation and call {@code decodedNodeConsumer} if any data is ready to
         * be decoded.
         * @param buffer the new data to decode.
         * @param decodedNodeConsumer A callback that will be synchronously invoked if new JSON objects can be decoded.
         * @throws IOException If any exceptions occur during the decode process.
         */
        void decode(Buffer buffer, Consumer<JsonNode> decodedNodeConsumer) throws IOException;

        /**
         * Indicates there will be no more calls to {@link #decode(Buffer, Consumer)}.
         */
        void endOfInput();

        /**
         * Create a new {@link AsyncJsonDecoder}.
         * @param factory The factory used for decoding.
         * @return a new {@link AsyncJsonDecoder}.
         */
        static AsyncJsonDecoder newInstance(JsonFactory factory) {
            final JsonParser parser;
            try {
                // TODO(scott): ByteBufferFeeder is currently not supported by jackson, and the current API throws
                // UnsupportedOperationException if not supported. When jackson does support two NonBlockingInputFeeder
                // types we need an approach which doesn't involve catching UnsupportedOperationException to try to get
                // ByteBufferFeeder and then ByteArrayFeeder.
                parser = factory.createNonBlockingByteArrayParser();
            } catch (IOException e) {
                throw new IllegalArgumentException("parser initialization error for factory: " + factory, e);
            }
            NonBlockingInputFeeder rawFeeder = parser.getNonBlockingInputFeeder();
            if (rawFeeder instanceof ByteBufferFeeder) {
                return new ByteBufferAsyncJsonDecoder(parser, (ByteBufferFeeder) rawFeeder);
            } else if (rawFeeder instanceof ByteArrayFeeder) {
                return new ByteArrayAsyncJsonDecoder(parser, (ByteArrayFeeder) rawFeeder);
            }
            throw new IllegalArgumentException("unsupported feeder type: " + rawFeeder);
        }
    }

    private static final class ByteArrayAsyncJsonDecoder extends AbstractAsyncJsonDecoder {
        private final ByteArrayFeeder feeder;

        ByteArrayAsyncJsonDecoder(JsonParser parser, ByteArrayFeeder feeder) {
            super(parser);
            this.feeder = feeder;
        }

        @Override
        public void decode(Buffer buffer,
                           Consumer<JsonNode> decodedNodeConsumer) throws IOException {
            if (buffer.hasArray()) {
                feeder.feedInput(buffer.getArray(), buffer.getArrayOffset(),
                                 buffer.getArrayOffset() + buffer.getReadableBytes());
            } else {
                int readableBytes = buffer.getReadableBytes();
                if (readableBytes == 0) {
                    return;
                }
                byte[] copy = new byte[readableBytes];
                buffer.readBytes(copy);
                feeder.feedInput(copy, 0, copy.length);
            }
            if (!feeder.needMoreInput()) {
                consumeParserTokens(decodedNodeConsumer);
            }
        }

        @Override
        public void endOfInput() {
            feeder.endOfInput();
        }
    }

    private static final class ByteBufferAsyncJsonDecoder extends AbstractAsyncJsonDecoder {
        private final ByteBufferFeeder feeder;

        ByteBufferAsyncJsonDecoder(JsonParser parser, ByteBufferFeeder feeder) {
            super(parser);
            this.feeder = feeder;
        }

        @Override
        public void decode(Buffer buffer,
                           Consumer<JsonNode> decodedNodeConsumer) throws IOException {
            feeder.feedInput(buffer.toNioBuffer());
            if (!feeder.needMoreInput()) {
                consumeParserTokens(decodedNodeConsumer);
            }
        }

        @Override
        public void endOfInput() {
            feeder.endOfInput();
        }
    }

    private abstract static class AbstractAsyncJsonDecoder implements AsyncJsonDecoder {
        private final Deque<JsonNode> nodeStack = new ArrayDeque<>();
        private final JsonParser parser;
        @Nullable
        private String fieldName;

        AbstractAsyncJsonDecoder(JsonParser parser) {
            this.parser = parser;
        }

        final void consumeParserTokens(Consumer<JsonNode> decodedNodeConsumer) throws IOException {
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.NOT_AVAILABLE) {
                JsonNode nextRoot = push(token, parser);
                if (nextRoot != null) {
                    decodedNodeConsumer.accept(nextRoot);
                }
            }
        }

        @Nullable
        final JsonNode push(JsonToken event, JsonParser parser) throws IOException {
            switch (event) {
                case START_OBJECT:
                    nodeStack.push(createObject(nodeStack.peek()));
                    return null;
                case START_ARRAY:
                    nodeStack.push(createArray(nodeStack.peek()));
                    return null;
                case END_OBJECT:
                case END_ARRAY:
                    JsonNode top = nodeStack.pop();
                    return nodeStack.isEmpty() ? top : null;
                case FIELD_NAME:
                    assert !nodeStack.isEmpty();
                    fieldName = parser.getCurrentName();
                    return null;
                case VALUE_STRING:
                    addValue(peekNonNull(), parser.getValueAsString());
                    return null;
                case VALUE_NUMBER_INT:
                    addValue(peekNonNull(), parser.getLongValue());
                    return null;
                case VALUE_NUMBER_FLOAT:
                    addValue(peekNonNull(), parser.getDoubleValue());
                    return null;
                case VALUE_TRUE:
                    addValue(peekNonNull(), true);
                    return null;
                case VALUE_FALSE:
                    addValue(peekNonNull(), false);
                    return null;
                case VALUE_NULL:
                    addNull(peekNonNull());
                    return null;
                default:
                    throw new IllegalArgumentException("unsupported event: " + event);
            }
        }

        private JsonNode peekNonNull() {
            JsonNode node = nodeStack.peek();
            assert node != null;
            return node;
        }

        private JsonNode createObject(@Nullable JsonNode current) {
            if (current instanceof ObjectNode) {
                return ((ObjectNode) current).putObject(fieldName);
            } else if (current instanceof ArrayNode) {
                return ((ArrayNode) current).addObject();
            } else {
                return instance.objectNode();
            }
        }

        private JsonNode createArray(@Nullable JsonNode current) {
            if (current instanceof ObjectNode) {
                return ((ObjectNode) current).putArray(fieldName);
            } else if (current instanceof ArrayNode) {
                return ((ArrayNode) current).addArray();
            } else {
                return instance.arrayNode();
            }
        }

        private void addValue(JsonNode current, String s) {
            if (current instanceof ObjectNode) {
                ((ObjectNode) current).put(fieldName, s);
            } else {
                ((ArrayNode) current).add(s);
            }
        }

        private void addValue(JsonNode current, long v) {
            if (current instanceof ObjectNode) {
                ((ObjectNode) current).put(fieldName, v);
            } else {
                ((ArrayNode) current).add(v);
            }
        }

        private void addValue(JsonNode current, double v) {
            if (current instanceof ObjectNode) {
                ((ObjectNode) current).put(fieldName, v);
            } else {
                ((ArrayNode) current).add(v);
            }
        }

        private void addValue(JsonNode current, boolean v) {
            if (current instanceof ObjectNode) {
                ((ObjectNode) current).put(fieldName, v);
            } else {
                ((ArrayNode) current).add(v);
            }
        }

        private void addNull(JsonNode current) {
            if (current instanceof ObjectNode) {
                ((ObjectNode) current).putNull(fieldName);
            } else {
                ((ArrayNode) current).addNull();
            }
        }
    }

    private static final class PojoToBytesMapper<T> implements Function<T, Buffer> {
        /**
         * This applies a somewhat arbitrary limit (around 500kb) on the auto scaling of the next buffer allocation.
         */
        private static final int MAX_READABLE_BYTES_TO_ADJUST = MAX_VALUE >>> 12;
        private final ObjectWriter writer;
        private final BufferAllocator allocator;
        private int allocationSizeEstimate;

        PojoToBytesMapper(ObjectWriter writer,
                          BufferAllocator allocator,
                          int serializationSizeBytesEstimate) {
            if (serializationSizeBytesEstimate < 1) {
                throw new IllegalArgumentException(
                        "serializationSizeEstimate: " + serializationSizeBytesEstimate + " (expected >0)");
            }
            this.writer = requireNonNull(writer);
            this.allocator = requireNonNull(allocator);
            this.allocationSizeEstimate = serializationSizeBytesEstimate;
        }

        @Override
        public Buffer apply(final T t) {
            Buffer buffer = allocator.newBuffer(allocationSizeEstimate);
            try {
                writer.writeValue(asOutputStream(buffer), t);
            } catch (IOException e) {
                throwException(e);
            }
            // Approximate the next buffer will be ~33% bigger than the current buffer as a best effort to avoid
            // re-allocation and copy.
            allocationSizeEstimate = (min(MAX_READABLE_BYTES_TO_ADJUST, buffer.getReadableBytes()) << 2) / 3;
            return buffer;
        }
    }
}
