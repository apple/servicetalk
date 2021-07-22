/*
 * Copyright © 2019-2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.protobuf;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.CompositeBuffer;
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.serialization.api.SerializationException;
import io.servicetalk.serialization.api.SerializationProvider;
import io.servicetalk.serialization.api.StreamingDeserializer;
import io.servicetalk.serialization.api.StreamingSerializer;
import io.servicetalk.serialization.api.TypeHolder;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import static com.google.protobuf.CodedOutputStream.newInstance;
import static com.google.protobuf.UnsafeByteOperations.unsafeWrap;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.encoding.api.Identity.identity;
import static java.lang.Math.max;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@Deprecated
final class ProtoBufSerializationProvider<T extends MessageLite> implements SerializationProvider {
    private static final int LENGTH_PREFIXED_MESSAGE_HEADER_BYTES = 5;

    private static final byte FLAG_UNCOMPRESSED = 0x0;
    private static final byte FLAG_COMPRESSED = 0x1;

    private final Class<T> targetClass;
    private final ContentCodec codec;
    private final ProtoSerializer serializer;
    private final Parser<T> parser;

    ProtoBufSerializationProvider(final Class<T> targetClass, final ContentCodec codec,
                                  final Parser<T> parser) {
        this.targetClass = targetClass;
        this.codec = codec;
        this.serializer = new ProtoSerializer(this.codec);
        this.parser = parser;
    }

    @Override
    public <X> StreamingSerializer getSerializer(final Class<X> classToSerialize) {
        if (targetClass != classToSerialize) {
            throw new SerializationException("Unknown class to serialize: " + classToSerialize.getName());
        }
        return serializer;
    }

    @Override
    public <X> StreamingSerializer getSerializer(final TypeHolder<X> typeToSerialize) {
        throw new UnsupportedOperationException(
                "TypeHolder is not supported for protocol buffers serialization provider.");
    }

    @Override
    public <X> StreamingDeserializer<X> getDeserializer(final Class<X> classToDeSerialize) {
        if (targetClass != classToDeSerialize) {
            throw new SerializationException("Unknown class to deserialize: " + classToDeSerialize.getName());
        }
        @SuppressWarnings("unchecked")
        Parser<X> parser = (Parser<X>) this.parser;
        return new ProtoDeserializer<>(parser, codec);
    }

    @Override
    public <X> StreamingDeserializer<X> getDeserializer(final TypeHolder<X> typeToDeserialize) {
        throw new UnsupportedOperationException(
                "TypeHolder is not supported for protocol buffers serialization provider.");
    }

    private static boolean isCompressed(Buffer buffer) throws SerializationException {
        byte compressionFlag = buffer.readByte();
        if (compressionFlag == FLAG_UNCOMPRESSED) {
            return false;
        } else if (compressionFlag == FLAG_COMPRESSED) {
            return true;
        }
        throw new SerializationException("compression flag must be 0 or 1 but was: " + compressionFlag);
    }

    private static final class ProtoDeserializer<T> implements StreamingDeserializer<T> {
        private final Parser<T> parser;
        private final CompositeBuffer accumulate;
        private final ContentCodec codec;
        /**
         * <ul>
         *     <li>{@code < 0} - read Length-Prefixed-Message header</li>
         *     <li>{@code >= 0} - read Length-Prefixed-Message Message</li>
         * </ul>
         */
        private int lengthOfData = -1;
        private boolean compressed;

        ProtoDeserializer(final Parser<T> parser, final ContentCodec codec) {
            this.parser = parser;
            this.codec = codec;
            accumulate = DEFAULT_ALLOCATOR.newCompositeBuffer(Integer.MAX_VALUE);
        }

        @Override
        public Iterable<T> deserialize(Buffer toDeserialize) {
            if (toDeserialize.readableBytes() <= 0) {
                return emptyList(); // We don't have any additional data to process, so bail for now.
            }
            List<T> parsedData = null;

            for (;;) {
                toDeserialize = addToAccumulateIfAccumulating(toDeserialize);
                if (lengthOfData < 0) {
                    // If we don't have more than a full header, just bail and try again later when more data arrives.
                    if (toDeserialize.readableBytes() < LENGTH_PREFIXED_MESSAGE_HEADER_BYTES) {
                        return addToAccumulateIfRequiredAndReturn(toDeserialize, parsedData);
                    }

                    compressed = isCompressed(toDeserialize);

                    // https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md specifies size as 4 bytes
                    // unsigned int However netty buffers only support up to Integer.MAX_VALUE, and even
                    // grpc-java (Google's implementation) only supports up to Integer.MAX_VALUE, so for
                    // simplicity we will just used signed int for now.
                    lengthOfData = toDeserialize.readInt();
                    if (lengthOfData < 0) {
                        throw new SerializationException("Message-Length invalid: " + lengthOfData);
                    }
                } else {
                    if (toDeserialize.readableBytes() < lengthOfData) {
                        return addToAccumulateIfRequiredAndReturn(toDeserialize, parsedData);
                    }

                    final T t;
                    try {
                        final CodedInputStream in;
                        Buffer buffer = toDeserialize;
                        int decodedLengthOfData = lengthOfData;
                        if (compressed) {
                            buffer = codec.decode(toDeserialize.readSlice(lengthOfData), DEFAULT_ALLOCATOR);
                            decodedLengthOfData = buffer.readableBytes();
                        }

                        if (buffer.nioBufferCount() == 1) {
                            ByteBuffer nioBuffer = buffer.toNioBuffer(buffer.readerIndex(), decodedLengthOfData);
                            in = CodedInputStream.newInstance(nioBuffer);
                        } else {
                            // Aggregated payload body may consist of multiple Buffers. In this case,
                            // CompositeBuffer.toNioBuffer(idx, length) may return a single ByteBuffer (when requested
                            // length < components[0].length) or create a new ByteBuffer and copy multiple components
                            // into it. Later, proto parser will copy data from this temporary ByteBuffer again.
                            // To avoid unnecessary copying, we use newCodedInputStream(buffers, lengthOfData).
                            final ByteBuffer[] buffers = buffer.toNioBuffers(buffer.readerIndex(),
                                    decodedLengthOfData);

                            in = buffers.length == 1 ?
                                    CodedInputStream.newInstance(buffers[0]) :
                                    newCodedInputStream(buffers, decodedLengthOfData);
                        }

                        t = parser.parseFrom(in);
                    } catch (InvalidProtocolBufferException e) {
                        throw new SerializationException(e);
                    }

                    if (!compressed) {
                        // The NIO buffer indexes are not connected to the Buffer indexes, so we need to update
                        // our indexes and discard any bytes if necessary.
                        toDeserialize.skipBytes(lengthOfData);
                    }

                    if (toDeserialize == accumulate) {
                        accumulate.discardSomeReadBytes();
                    }

                    // We parsed the expected data, update the state to prepare for parsing the next frame.
                    final int oldLengthOfData = lengthOfData;
                    lengthOfData = -1;
                    compressed = false;

                    // If we don't have more than a full header, just bail and try again later when more data arrives.
                    if (toDeserialize.readableBytes() < LENGTH_PREFIXED_MESSAGE_HEADER_BYTES) {
                        // Before we bail out, we need to save the accumulated data for next time.
                        if (toDeserialize != accumulate && toDeserialize.readableBytes() != 0) {
                            accumulate.addBuffer(toDeserialize, true);
                        }
                        if (parsedData == null) {
                            return singletonList(t);
                        }
                        parsedData.add(t);
                        return parsedData;
                    } else {
                        if (parsedData == null) {
                            // assume roughly uniform message sizes when estimating the initial size of the array.
                            parsedData = new ArrayList<>(1 + max(1, toDeserialize.readableBytes() /
                                    (oldLengthOfData + LENGTH_PREFIXED_MESSAGE_HEADER_BYTES)));
                        }
                        parsedData.add(t);
                    }
                }
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

        @Override
        public boolean hasData() {
            return accumulate.readableBytes() > 0;
        }

        @Override
        public void close() {
            if (hasData()) {
                throw new SerializationException("Deserializer disposed with left over data.");
            }
        }

        private Buffer addToAccumulateIfAccumulating(Buffer toDeserialize) {
            if (toDeserialize != accumulate && accumulate.readableBytes() > 0) {
                accumulate.addBuffer(toDeserialize, true);
                return accumulate;
            }
            return toDeserialize;
        }

        private Iterable<T> addToAccumulateIfRequiredAndReturn(final Buffer toDeserialize,
                                                               @Nullable final Iterable<T> parsed) {
            if (accumulate != toDeserialize) {
                accumulate.addBuffer(toDeserialize, true);
            }
            return parsed == null ? emptyList() : parsed;
        }
    }

    private static final class ProtoSerializer implements StreamingSerializer {

        private final ContentCodec codec;
        private final boolean encode;

        ProtoSerializer(final ContentCodec codec) {
            this.codec = codec;
            this.encode = !identity().equals(codec);
        }

        @Override
        public void serialize(final Object toSerialize, final Buffer destination) {
            if (!(toSerialize instanceof MessageLite)) {
                throw new SerializationException("Unknown type to serialize (expected MessageLite): " +
                        toSerialize.getClass().getName());
            }

            if (encode) {
                serializeAndEncode((MessageLite) toSerialize, destination);
            } else {
                serializeOnly((MessageLite) toSerialize, destination);
            }
        }

        private void serializeOnly(final MessageLite msg, final Buffer destination) {
            final int size = msg.getSerializedSize();
            destination.writeByte(FLAG_UNCOMPRESSED);
            destination.writeInt(size);
            destination.ensureWritable(size);

            serialize0(msg, destination);
        }

        private void serializeAndEncode(final MessageLite msg, final Buffer destination) {
            final int size = msg.getSerializedSize();
            Buffer serialized = DEFAULT_ALLOCATOR.newBuffer(size);
            serialize0(msg, serialized);

            Buffer encoded = codec.encode(serialized, DEFAULT_ALLOCATOR);

            destination.writeByte(FLAG_COMPRESSED);
            destination.writeInt(encoded.readableBytes());
            destination.ensureWritable(encoded.readableBytes());
            destination.writeBytes(encoded);
        }

        private void serialize0(final MessageLite msg, final Buffer destination) {
            final int size = msg.getSerializedSize();
            final int writerIdx = destination.writerIndex();
            final int writableBytes = destination.writableBytes();
            final CodedOutputStream out = destination.hasArray() ?
                    newInstance(destination.array(), destination.arrayOffset() + writerIdx, writableBytes) :
                    newInstance(destination.toNioBuffer(writerIdx, writableBytes));

            try {
                msg.writeTo(out);
            } catch (IOException e) {
                throw new SerializationException(e);
            }

            // Forward write index of our buffer
            destination.writerIndex(writerIdx + size);
        }
    }
}
