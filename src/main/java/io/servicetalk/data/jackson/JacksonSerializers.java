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
import org.reactivestreams.Subscriber;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;

import static com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;
import static io.servicetalk.buffer.api.Buffer.asOutputStream;
import static io.servicetalk.concurrent.internal.PlatformDependent.throwException;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

/**
 * JSON serialization and deserialization based upon the jackson library.
 */
public final class JacksonSerializers {

    /**
     * This applies a somewhat arbitrary limit (around 500kb) on the auto scaling of the next buffer allocation.
     */
    private static final int MAX_READABLE_BYTES_TO_ADJUST = MAX_VALUE >>> 12;
    private static final int DEFAULT_SERIALIZATION_SIZE_BYTES_ESTIMATE = 512;

    private static final IntUnaryOperator DEFAULT_SIZE_ESTIMATOR = lastSize -> {
        // Approximate the next buffer will be ~33% bigger than the current buffer as a best effort to avoid
        // re-allocation and copy.
        return max(DEFAULT_SERIALIZATION_SIZE_BYTES_ESTIMATE, (min(MAX_READABLE_BYTES_TO_ADJUST, lastSize) << 2) / 3);
    };

    private JacksonSerializers() {
        // no instances
    }

    /**
     * Create a {@link Function} that can serialize a single object {@link T} to a {@link Buffer}.
     * @param mapper Used to write the serialized form of {@link T} to the returned {@link Buffer} per call to the
     * returned {@link Function#apply(Object)}.
     * @param type The class for {@link T}. This helps avoid having to explicitly specify generics arguments on the
     * {@link #serializer(ObjectWriter, BufferAllocator)} in cases where it cannot be automatically inferred.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param <T> The data type to serialize.
     * @return A {@link Function} that can serialize a {@link Publisher} of objects from {@link T} to {@link Buffer}.
     */
    public static <T> Function<T, Buffer> serializer(ObjectMapper mapper, Class<T> type, BufferAllocator allocator) {
        return serializer(mapper.writerFor(type), allocator);
    }

    /**
     * Create a {@link Function} that can serialize a single object {@link T} to a {@link Buffer}.
     * @param mapper Used to write the serialized form of {@link T} to the returned {@link Buffer} per call to the
     * returned {@link Function#apply(Object)}.
     * @param typeReference The type for {@link T}. This helps avoid having to explicitly specify generics arguments on
     * the {@link #serializer(ObjectWriter, BufferAllocator)} in cases where it cannot be automatically inferred.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param <T> The data type to serialize.
     * @return A {@link Function} that can serialize a {@link Publisher} of objects from {@link T} to {@link Buffer}.
     */
    public static <T> Function<T, Buffer> serializer(ObjectMapper mapper, TypeReference<T> typeReference,
                                                     BufferAllocator allocator) {
        return serializer(mapper.writerFor(typeReference), allocator);
    }

    /**
     * Create a {@link Function} that can serialize a single object {@link T} to a {@link Buffer}.
     * @param writer used to serialize objects of type {@link T}.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param <T> The data type to serialize.
     * @return A {@link Function} that can serialize a {@link Publisher} of objects from {@link T} to {@link Buffer}.
     */
    public static <T> Function<T, Buffer> serializer(ObjectWriter writer, BufferAllocator allocator) {
        return serializer(writer, allocator, DEFAULT_SIZE_ESTIMATOR);
    }

    /**
     * Create a {@link Function} that can serialize a single object {@link T} to a {@link Buffer}.
     * @param mapper Used to write the serialized form of {@link T} to the returned {@link Buffer} per call to the
     * returned {@link Function#apply(Object)}.
     * @param type The class for {@link T}. This helps avoid having to explicitly specify generics arguments on the
     * {@link #serializer(ObjectWriter, BufferAllocator, IntUnaryOperator)} in cases where it cannot be
     * automatically inferred.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param bytesEstimator An {@link IntUnaryOperator} that given the last serialized size in bytes, estimates the
     * size of the next object to be serialized in bytes.
     * @param <T> The data type to serialize.
     * @return A {@link Function} that can serialize a {@link Publisher} of objects from {@link T} to {@link Buffer}.
     */
    public static <T> Function<T, Buffer> serializer(ObjectMapper mapper, Class<T> type, BufferAllocator allocator,
                                                     IntUnaryOperator bytesEstimator) {
        return serializer(mapper.writerFor(type), allocator, bytesEstimator);
    }

    /**
     * Create a {@link Function} that can serialize a single object {@link T} to a {@link Buffer}.
     * @param mapper Used to write the serialized form of {@link T} to the returned {@link Buffer} per call to the
     * returned {@link Function#apply(Object)}.
     * @param typeReference The type for {@link T}. This helps avoid having to explicitly specify generics arguments on
     * the {@link #serializer(ObjectWriter, BufferAllocator, IntUnaryOperator)} in cases where it cannot be
     * automatically inferred.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param bytesEstimator An {@link IntUnaryOperator} that given the last serialized size in bytes, estimates the
     * size of the next object to be serialized in bytes.
     * @param <T> The data type to serialize.
     * @return A {@link Function} that can serialize a {@link Publisher} of objects from {@link T} to {@link Buffer}.
     */
    public static <T> Function<T, Buffer> serializer(ObjectMapper mapper, TypeReference<T> typeReference,
                                                     BufferAllocator allocator, IntUnaryOperator bytesEstimator) {
        return serializer(mapper.writerFor(typeReference), allocator, bytesEstimator);
    }

    /**
     * Create a {@link Function} that can serialize a single object {@link T} to a {@link Buffer}.
     * @param writer used to serialize objects of type {@link T}.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param bytesEstimator An {@link IntUnaryOperator} that given the last serialized size in bytes, estimates the
     * size of the next object to be serialized in bytes.
     * @param <T> The data type to serialize.
     * @return A {@link Function} that can serialize a {@link Publisher} of objects from {@link T} to {@link Buffer}.
     */
    public static <T> Function<T, Buffer> serializer(ObjectWriter writer, BufferAllocator allocator,
                                                     IntUnaryOperator bytesEstimator) {
        return new PojoToBytesMapper<>(writer, allocator, bytesEstimator);
    }

    /**
     * Serializes the passed object {@code toSerialize} to the returned {@link Buffer}.
     * @param writer used to serialize objects of type {@link T}.
     * @param toSerialize Object to serialize.
     * @param allocator The {@link BufferAllocator} used to allocate the returned {@link Buffer}.
     * @param <T> The data type to serialize.
     * @return A {@link Buffer} containing the serialized object.
     */
    public static <T> Buffer serialize(ObjectWriter writer, T toSerialize, BufferAllocator allocator) {
        final Buffer destination = allocator.newBuffer(DEFAULT_SERIALIZATION_SIZE_BYTES_ESTIMATE);
        serialize(writer, toSerialize, destination);
        return destination;
    }

    /**
     * Serializes the passed object {@code toSerialize} to the returned {@link Buffer}.
     * @param writer used to serialize objects of type {@link T}.
     * @param toSerialize Object to serialize.
     * @param allocator The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param bytesEstimate An estimate as to how many bytes each serialization will require.
     * @param <T> The data type to serialize.
     * @return A {@link Buffer} containing the serialized object.
     */
    public static <T> Buffer serialize(ObjectWriter writer, T toSerialize, BufferAllocator allocator,
                                       int bytesEstimate) {
        final Buffer destination = allocator.newBuffer(bytesEstimate);
        serialize(writer, toSerialize, destination);
        return destination;
    }

    /**
     * Serializes the passed object {@code toSerialize} to the passed {@link Buffer}.
     * @param writer used to serialize the passed object.
     * @param toSerialize Object to serialize.
     * @param destination The {@link BufferAllocator} used to allocate {@link Buffer}s.
     * @param <T> The data type to serialize.
     */
    public static <T> void serialize(ObjectWriter writer, T toSerialize, Buffer destination) {
        PojoToBytesMapper.serialize(writer, toSerialize, destination);
    }

    /**
     * Create a {@link Function} that can deserialize a {@link Publisher} of encoded {@link Buffer}s to objects of type
     * {@link T}.
     * @param mapper Used to map {@link Buffer} to {@link T} when there is enough data.
     * @param typeReference The type for {@link T}. This helps avoid having to explicitly specify generics arguments on
     * the {@link #deserializer(ObjectReader)} in cases where it cannot be automatically inferred.
     * @param <T> The data type to deserialize.
     * @return A {@link Function} that can deserialize a {@link Publisher} of encoded {@link Buffer}s to objects of type
     * {@link T}.
     */
    public static <T> Function<Publisher<Buffer>, Publisher<T>> deserializer(ObjectMapper mapper,
                                                                             TypeReference<T> typeReference) {
        return deserializer(mapper.readerFor(typeReference));
    }

    /**
     * Create a {@link Function} that can deserialize a {@link Publisher} of encoded {@link Buffer}s to objects of type
     * {@link T}.
     * @param mapper Used to map {@link Buffer} to {@link T} when there is enough data.
     * @param type The class for {@link T}. This helps avoid having to explicitly specify generics arguments on the
     * {@link #deserializer(ObjectReader)} in cases where it cannot be automatically inferred.
     * @param <T> The data type to deserialize.
     * @return A {@link Function} that can deserialize a {@link Publisher} of encoded {@link Buffer}s to objects of type
     * {@link T}.
     */
    public static <T> Function<Publisher<Buffer>, Publisher<T>> deserializer(ObjectMapper mapper,
                                                                             Class<T> type) {
        return deserializer(mapper.readerFor(type));
    }

    /**
     * Create a {@link Function} that can deserialize a {@link Publisher} of encoded {@link Buffer}s to objects of type
     * {@link T}.
     * @param reader A reader which knows about the type {@link T} and translates from streaming tokens to an instance
     * of {@link T}.
     * @param <T> The data type to deserialize.
     * @return A {@link Function} that can deserialize a {@link Publisher} of encoded {@link Buffer}s to objects of type
     * {@link T}.
     */
    public static <T> Function<Publisher<Buffer>, Publisher<T>> deserializer(ObjectReader reader) {
        requireNonNull(reader);
        // Create a new Publisher since we have state: AsyncJsonDecoder per subscribe.
        return chunkBody -> new Publisher<T>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super T> subscriber) {
                // The AsyncJsonDecoder will be used to buffer data in between HttpPayloadChunks. It is not thread safe but the
                // concatMap should ensure there is no concurrency, and will ensure visibility when transitioning between
                // HttpPayloadChunks.
                AsyncJsonDecoder<T> asyncJsonDecoder = newDecoder(reader.getFactory());
                chunkBody.concatMapIterable(buffer -> asyncJsonDecoder.<T>decode(buffer, reader))
                        .doAfterComplete(asyncJsonDecoder::endOfInput)
                        .subscribe(subscriber);
            }
        };
    }

    /**
     * Create a new {@link AsyncJsonDecoder}.
     * @param factory The factory used for decoding.
     * @return a new {@link AsyncJsonDecoder}.
     */
    private static <T> AsyncJsonDecoder<T> newDecoder(JsonFactory factory) {
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
            return new ByteBufferAsyncJsonDecoder<>(parser, (ByteBufferFeeder) rawFeeder);
        } else if (rawFeeder instanceof ByteArrayFeeder) {
            return new ByteArrayAsyncJsonDecoder<>(parser, (ByteArrayFeeder) rawFeeder);
        }
        throw new IllegalArgumentException("unsupported feeder type: " + rawFeeder);
    }

    private interface AsyncJsonDecoder<T> {
        /**
         * Add a new {@link Buffer} to the decode operation and call {@code decodedNodeConsumer} if any data is ready to
         * be decoded.
         * @param buffer the new data to decode.
         * @param reader A reader which knows about the type {@link T} and translates from streaming tokens to an instance
         * of {@link T}.
         */
        Iterable<T> decode(Buffer buffer, ObjectReader reader);

        /**
         * Indicates there will be no more calls to {@link #decode(Buffer, ObjectReader)}.
         */
        void endOfInput();
    }

    private static final class ByteArrayAsyncJsonDecoder<T> extends AbstractAsyncJsonDecoder<T> {
        private final ByteArrayFeeder feeder;

        ByteArrayAsyncJsonDecoder(JsonParser parser, ByteArrayFeeder feeder) {
            super(parser);
            this.feeder = feeder;
        }

        @Override
        Iterable<T> doDecode(final Buffer buffer, final ObjectReader reader) throws IOException {
            if (buffer.hasArray()) {
                feeder.feedInput(buffer.getArray(), buffer.getArrayOffset(),
                                 buffer.getArrayOffset() + buffer.getReadableBytes());
            } else {
                int readableBytes = buffer.getReadableBytes();
                if (readableBytes != 0) {
                    byte[] copy = new byte[readableBytes];
                    buffer.readBytes(copy);
                    feeder.feedInput(copy, 0, copy.length);
                }
            }

            return !feeder.needMoreInput() ? consumeParserTokens(reader) : emptyList();
        }

        @Override
        public void endOfInput() {
            feeder.endOfInput();
        }
    }

    private static final class ByteBufferAsyncJsonDecoder<T> extends AbstractAsyncJsonDecoder<T> {
        private final ByteBufferFeeder feeder;

        ByteBufferAsyncJsonDecoder(JsonParser parser, ByteBufferFeeder feeder) {
            super(parser);
            this.feeder = feeder;
        }

        @Override
        Iterable<T> doDecode(final Buffer buffer, final ObjectReader reader) throws IOException {
            feeder.feedInput(buffer.toNioBuffer());
            return !feeder.needMoreInput() ? consumeParserTokens(reader) : emptyList();
        }

        @Override
        public void endOfInput() {
            feeder.endOfInput();
        }
    }

    private abstract static class AbstractAsyncJsonDecoder<T> implements AsyncJsonDecoder<T> {
        private final Deque<JsonNode> nodeStack = new ArrayDeque<>();
        private final JsonParser parser;
        @Nullable
        private String fieldName;

        AbstractAsyncJsonDecoder(JsonParser parser) {
            this.parser = parser;
        }

        @Override
        public final Iterable<T> decode(final Buffer buffer, final ObjectReader reader) {
            try {
                return doDecode(buffer, reader);
            } catch (IOException e) {
                throwException(e);
                return emptyList();
            }
        }

        abstract Iterable<T> doDecode(Buffer buffer, ObjectReader reader) throws IOException;

        final Iterable<T> consumeParserTokens(final ObjectReader reader) throws IOException {
            JsonToken token = parser.nextToken();
            if (token == JsonToken.NOT_AVAILABLE) {
                // Avoid creating list if there are no items available.
                return emptyList();
            }
            List<T> deserializedPojos = new ArrayList<>(2);
            do {
                JsonNode nextRoot = push(token, parser);
                if (nextRoot != null) {
                    deserializedPojos.add(reader.readValue(nextRoot));
                }
            } while ((token = parser.nextToken()) != JsonToken.NOT_AVAILABLE);
            return deserializedPojos;
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
        private final ObjectWriter writer;
        private final BufferAllocator allocator;
        private final IntUnaryOperator bytesEstimator;
        private int allocationSizeEstimate;

        PojoToBytesMapper(ObjectWriter writer, BufferAllocator allocator, IntUnaryOperator bytesEstimator) {
            this.bytesEstimator = requireNonNull(bytesEstimator);
            this.writer = requireNonNull(writer);
            this.allocator = requireNonNull(allocator);
            updateAllocationSizeEstimate(0);
        }

        @Override
        public Buffer apply(final T t) {
            Buffer buffer = allocator.newBuffer(allocationSizeEstimate);
            serialize(writer, t, buffer);
            updateAllocationSizeEstimate(buffer.getReadableBytes());
            return buffer;
        }

        static <T> void serialize(ObjectWriter writer, final T t, final Buffer buffer) {
            try {
                writer.writeValue(asOutputStream(buffer), t);
            } catch (IOException e) {
                throwException(e);
            }
        }

        private void updateAllocationSizeEstimate(int lastSize) {
            allocationSizeEstimate = bytesEstimator.applyAsInt(lastSize);
            if (allocationSizeEstimate <= 0) {
                allocationSizeEstimate = DEFAULT_SERIALIZATION_SIZE_BYTES_ESTIMATE;
            }
        }
    }
}
