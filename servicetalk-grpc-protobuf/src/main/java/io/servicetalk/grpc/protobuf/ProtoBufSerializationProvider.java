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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

final class ProtoBufSerializationProvider<T extends MessageLite> implements SerializationProvider {

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
        switch (compressionFlag) {
            case 1:
                return true;
            case 0:
                return false;
            default:
                throw new SerializationException("compression flag must be 0 or 1 but was:  " + compressionFlag);
        }
    }

    private static final class ProtoDeserializer<T> implements StreamingDeserializer<T> {
        private enum State {
            ReadCompressed,
            AccumulateLength,
            ReadLength,
            AccumulateData,
            ParseData
        }

        private final Parser<T> parser;
        private final CompositeBuffer accumulate;

        private boolean compressed;
        private int lengthOfData = -1;
        private State state = State.ReadCompressed;

        ProtoDeserializer(final Parser<T> parser,
                          @SuppressWarnings("unused") final GrpcMessageEncoding grpcMessageEncoding) {
            this.parser = parser;
            accumulate = DEFAULT_ALLOCATOR.newCompositeBuffer(Integer.MAX_VALUE);
        }

        @Override
        public Iterable<T> deserialize(Buffer toDeserialize) {
            if (toDeserialize.readableBytes() == 0) {
                return emptyList(); // Nothing to read
            }
            List<T> parsedData = null;
            InputStream dataToParse = null;

            for (;;) {
                switch (state) {
                    case ReadCompressed:
                        compressed = isCompressed(toDeserialize);
                        // TODO (nkant) : handle compression
                        assert !compressed;
                        state = State.AccumulateLength;
                        break;
                    case AccumulateLength:
                        toDeserialize = addToAccumulateIfAccumulating(toDeserialize);

                        if (toDeserialize.readableBytes() >= 4) {
                            state = State.ReadLength;
                            break;
                        }
                        return addToAccumulateIfRequiredAndReturn(toDeserialize, parsedData);
                    case ReadLength:
                        // https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md specifies size as 4 bytes
                        // unsigned int However netty buffers only support up to Integer.MAX_VALUE, and even
                        // grpc-java (Google's implementation) only supports up to Integer.MAX_VALUE, so for
                        // simplicity we will just used signed int for now.
                        lengthOfData = toDeserialize.readInt();
                        state = State.AccumulateData;
                        break;
                    case AccumulateData:
                        assert lengthOfData >= 0;
                        toDeserialize = addToAccumulateIfAccumulating(toDeserialize);

                        if (toDeserialize.readableBytes() >= lengthOfData) {
                            // TODO: we should be able to use slice() here instead of copying to heap but for some
                            // reason slice is garbling the indices
                            byte[] data = new byte[lengthOfData];
                            toDeserialize.readBytes(data, 0, lengthOfData);
                            dataToParse = new ByteArrayInputStream(data);
                            if (accumulate.readableBytes() == 0) {
                                // No leftover bytes, discard accumulate
                                accumulate.discardSomeReadBytes();
                            }
                            lengthOfData = -1;
                            compressed = false;
                            state = State.ParseData;
                            break;
                        }
                        return addToAccumulateIfRequiredAndReturn(toDeserialize, parsedData);
                    case ParseData:
                        assert dataToParse != null;
                        T t;
                        try {
                            state = State.ReadCompressed;
                            t = parser.parseFrom(dataToParse);
                        } catch (InvalidProtocolBufferException e) {
                            throw new SerializationException(e);
                        }
                        if (toDeserialize.readableBytes() == 0) {
                            if (parsedData == null) {
                                return singletonList(t);
                            } else {
                                parsedData.add(t);
                                return parsedData;
                            }
                        } else {
                            if (parsedData == null) {
                                parsedData = new ArrayList<>();
                            }
                            parsedData.add(t);
                        }
                        break;
                    default:
                        throw new IllegalStateException("Unknown parse state: " + state);
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
            if (accumulate.readableBytes() > 0) {
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
