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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.serializer.api.SerializationException;
import io.servicetalk.serializer.api.SerializerDeserializer;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;

import java.io.IOException;
import java.nio.ByteBuffer;

import static com.google.protobuf.CodedOutputStream.newInstance;
import static com.google.protobuf.UnsafeByteOperations.unsafeWrap;
import static java.util.Objects.requireNonNull;

/**
 * Serializes and deserializes <a href="https://developers.google.com/protocol-buffers/">protocol buffer</a> objects.
 * @param <T> The type of objects to serialize.
 */
final class ProtobufSerializer<T extends MessageLite> implements SerializerDeserializer<T> {
    private final Parser<T> parser;

    /**
     * Create a new instance.
     * @param parser The {@link Parser} used to serialize and deserialize.
     */
    ProtobufSerializer(Parser<T> parser) {
        this.parser = requireNonNull(parser);
    }

    @Override
    public Buffer serialize(final T toSerialize, final BufferAllocator allocator) {
        Buffer buffer = allocator.newBuffer(toSerialize.getSerializedSize());
        serialize(toSerialize, allocator, buffer);
        return buffer;
    }

    @Override
    public void serialize(final T toSerialize, final BufferAllocator allocator, final Buffer buffer) {
        final int writerIdx = buffer.writerIndex();
        final int writableBytes = buffer.writableBytes();
        final CodedOutputStream out = buffer.hasArray() ?
                newInstance(buffer.array(), buffer.arrayOffset() + writerIdx, writableBytes) :
                newInstance(buffer.toNioBuffer(writerIdx, writableBytes));

        try {
            toSerialize.writeTo(out);
        } catch (IOException e) {
            throw new SerializationException(e);
        }

        // Forward write index of our buffer
        buffer.writerIndex(writerIdx + toSerialize.getSerializedSize());
    }

    @Override
    public T deserialize(final Buffer serializedData, final BufferAllocator allocator) {
        try {
            final CodedInputStream in;
            if (serializedData.nioBufferCount() == 1) {
                in = CodedInputStream.newInstance(serializedData.toNioBuffer());
            } else {
                // Aggregated payload body may consist of multiple Buffers. In this case,
                // CompositeBuffer.toNioBuffer(idx, length) may return a single ByteBuffer (when requested
                // length < components[0].length) or create a new ByteBuffer and copy multiple components
                // into it. Later, proto parser will copy data from this temporary ByteBuffer again.
                // To avoid unnecessary copying, we use newCodedInputStream(buffers, lengthOfData).
                final ByteBuffer[] buffers = serializedData.toNioBuffers();
                in = buffers.length == 1 ?
                        CodedInputStream.newInstance(buffers[0]) :
                        newCodedInputStream(buffers, serializedData.readableBytes());
            }

            T result = parser.parseFrom(in);
            serializedData.skipBytes(result.getSerializedSize());
            return result;
        } catch (InvalidProtocolBufferException e) {
            throw new SerializationException(e);
        }
    }

    private static CodedInputStream newCodedInputStream(final ByteBuffer[] buffers, final int lengthOfData) {
        // Because we allocated a new internal ByteBuffer that will never be mutated we may just wrap it and
        // enable aliasing to avoid an extra copying inside parser for a deserialized message.
        final CodedInputStream in = unsafeWrap(mergeByteBuffers(buffers, lengthOfData)).newCodedInput();
        in.enableAliasing(true);
        return in;
    }

    private static ByteBuffer mergeByteBuffers(final ByteBuffer[] buffers, final int lengthOfData) {
        final ByteBuffer merged = ByteBuffer.allocate(lengthOfData);
        for (ByteBuffer buf : buffers) {
            merged.put(buf);
        }
        merged.flip();
        return merged;
    }
}
