/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.grpc.api.GrpcMessageEncoding;
import io.servicetalk.serialization.api.SerializationException;
import io.servicetalk.serialization.api.SerializationProvider;
import io.servicetalk.serialization.api.StreamingDeserializer;
import io.servicetalk.serialization.api.StreamingSerializer;
import io.servicetalk.serialization.api.TypeHolder;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static java.lang.Math.max;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

final class ProtoBufSerializationProvider<T extends MessageLite> implements SerializationProvider {
    private static final int LENGTH_PREFIXED_MESSAGE_HEADER_BYTES = 5;
    private final Class<T> targetClass;
    private final GrpcMessageEncoding messageEncoding;
    private final ProtoSerializer serializer;
    private final Parser parser;

    ProtoBufSerializationProvider(final Class<T> targetClass, final GrpcMessageEncoding messageEncoding,
                                  final Parser<T> parser) {
        this.targetClass = targetClass;
        this.messageEncoding = messageEncoding;
        this.serializer = new ProtoSerializer(messageEncoding);
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
        return new ProtoDeserializer<>(parser, messageEncoding);
    }

    @Override
    public <X> StreamingDeserializer<X> getDeserializer(final TypeHolder<X> typeToDeserialize) {
        throw new UnsupportedOperationException(
                "TypeHolder is not supported for protocol buffers serialization provider.");
    }

    private static boolean isCompressed(Buffer buffer) throws SerializationException {
        byte compressionFlag = buffer.readByte();
        if (compressionFlag == 0) {
            return false;
        } else if (compressionFlag == 1) {
            return true;
        }
        throw new SerializationException("compression flag must be 0 or 1 but was:  " + compressionFlag);
    }

    private static final class ProtoDeserializer<T> implements StreamingDeserializer<T> {
        private final Parser<T> parser;
        private final CompositeBuffer accumulate;

        private boolean compressed;
        private int lengthOfData = -1;
        /**
         * <ul>
         *     <li>{@code true} - read Length-Prefixed-Message header</li>
         *     <li>{@code false} - read Length-Prefixed-Message Message</li>
         * </ul>
         */
        private boolean stateReadHeader = true;

        ProtoDeserializer(final Parser<T> parser,
                          @SuppressWarnings("unused") final GrpcMessageEncoding grpcMessageEncoding) {
            this.parser = parser;
            accumulate = DEFAULT_ALLOCATOR.newCompositeBuffer(Integer.MAX_VALUE);
        }

        @Override
        public Iterable<T> deserialize(Buffer toDeserialize) {
            if (toDeserialize.readableBytes() <= 0) {
                return emptyList(); // We don't have any additional data to process, so bail for now.
            }
            @Nullable
            List<T> parsedData = null;

            for (;;) {
                if (stateReadHeader) {
                    toDeserialize = addToAccumulateIfAccumulating(toDeserialize);
                    // If we don't have more than a full header, just bail and try again later when more data arrives.
                    if (toDeserialize.readableBytes() < LENGTH_PREFIXED_MESSAGE_HEADER_BYTES) {
                        return addToAccumulateIfRequiredAndReturn(toDeserialize, parsedData);
                    }

                    compressed = isCompressed(toDeserialize);
                    // TODO (nkant) : handle compression
                    assert !compressed;

                    // https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md specifies size as 4 bytes
                    // unsigned int However netty buffers only support up to Integer.MAX_VALUE, and even
                    // grpc-java (Google's implementation) only supports up to Integer.MAX_VALUE, so for
                    // simplicity we will just used signed int for now.
                    lengthOfData = toDeserialize.readInt();
                    if (lengthOfData < 0) {
                        throw new SerializationException("Message-Length invalid: " + lengthOfData);
                    }

                    stateReadHeader = false;
                } else {
                    assert lengthOfData >= 0;
                    toDeserialize = addToAccumulateIfAccumulating(toDeserialize);
                    if (toDeserialize.readableBytes() < lengthOfData) {
                        return addToAccumulateIfRequiredAndReturn(toDeserialize, parsedData);
                    }

                    final T t;
                    try {
                        t = parser.parseFrom(toDeserialize.toNioBuffer(toDeserialize.readerIndex(), lengthOfData));
                    } catch (InvalidProtocolBufferException e) {
                        throw new SerializationException(e);
                    }

                    // The NIO buffer indexes are not connected to the Buffer indexes, so we need to update
                    // our indexes and discard any bytes if necessary.
                    toDeserialize.skipBytes(lengthOfData);
                    if (toDeserialize == accumulate) {
                        accumulate.discardSomeReadBytes();
                    }

                    // We parsed the expected data, update the state to prepare for parsing the next frame.
                    final int oldLengthOfData = lengthOfData;
                    lengthOfData = -1;
                    stateReadHeader = true;
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

        ProtoSerializer(@SuppressWarnings("unused") final GrpcMessageEncoding encoding) {
        }

        @Override
        public void serialize(final Object toSerialize, final Buffer destination) {
            if (!(toSerialize instanceof MessageLite)) {
                throw new SerializationException("Unknown type to serialize (expected MessageLite): " +
                        toSerialize.getClass().getName());
            }
            MessageLite msg = (MessageLite) toSerialize;
            int size = msg.getSerializedSize();
            // TODO (nkant) : handle compression
            destination.writeByte(0);
            destination.writeInt(size);
            try (OutputStream out = Buffer.asOutputStream(destination)) {
                msg.writeTo(out);
            } catch (IOException e) {
                throw new SerializationException(e);
            }
        }
    }
}
