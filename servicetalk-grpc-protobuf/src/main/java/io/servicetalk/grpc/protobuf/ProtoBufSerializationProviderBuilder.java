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
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.grpc.api.GrpcSerializationProvider;
import io.servicetalk.grpc.api.MessageEncodingException;
import io.servicetalk.http.api.HttpDeserializer;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.HttpSerializer;
import io.servicetalk.serialization.api.DefaultSerializer;
import io.servicetalk.serialization.api.SerializationException;
import io.servicetalk.serialization.api.Serializer;

import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.encoding.api.Identity.identity;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;

/**
 * A builder for building a {@link GrpcSerializationProvider} that can serialize and deserialize
 * pre-registered <a href="https://developers.google.com/protocol-buffers/">protocol buffer</a> objects.
 * @deprecated The gRPC framing is now built into grpc-netty. This class is no longer necessary and will be removed in
 * a future release.
 * {@link #registerMessageType(Class, Parser)} is used to add one or more {@link MessageLite} message types. Resulting
 * {@link GrpcSerializationProvider} from {@link #build()} will only serialize and deserialize those message types.
 */
@Deprecated
public final class ProtoBufSerializationProviderBuilder {
    private static final CharSequence GRPC_MESSAGE_ENCODING_KEY = newAsciiString("grpc-encoding");
    private static final CharSequence APPLICATION_GRPC_PROTO = newAsciiString("application/grpc+proto");

    private final Map<Class<? extends MessageLite>, Parser<? extends MessageLite>> types = new HashMap<>();
    private final Map<Class, Map<ContentCodec, HttpSerializer>> serializers = new HashMap<>();
    private final Map<Class, Map<ContentCodec, HttpDeserializer>> deserializers = new HashMap<>();

    private List<ContentCodec> supportedCodings = singletonList(identity());

    /**
     * Set the supported message encodings for the serializers and deserializers.
     * The encodings will be advertised on the endpoint's headers and also used to validate each encoded message
     * {@link io.servicetalk.encoding.api.Identity#identity()} is always supported regardless of the config passed
     *
     * @param supportedCodings the set of allowed encodings
     * @param <T> Type of {@link MessageLite} to register.
     * @return {@code this}
     */
    public <T extends MessageLite> ProtoBufSerializationProviderBuilder
    supportedMessageCodings(final List<ContentCodec> supportedCodings) {
        this.supportedCodings = new ArrayList<>(supportedCodings);
        if (!this.supportedCodings.contains(identity())) {
            this.supportedCodings.add(identity()); // Always supported
        }
        return this;
    }

    /**
     * Register the passed {@code messageType} with the provided {@link Parser}.
     *
     * @param messageType {@link Class} of the type of message to register.
     * @param parser {@link Parser} for this message type.
     * @param <T> Type of {@link MessageLite} to register.
     * @return {@code this}
     */
    public <T extends MessageLite> ProtoBufSerializationProviderBuilder
    registerMessageType(Class<T> messageType, Parser<T> parser) {
        this.types.put(messageType, parser);
        return this;
    }

    @SuppressWarnings("unchecked")
    private void build0() {
        for (Map.Entry<Class<? extends MessageLite>, Parser<? extends MessageLite>> entry : types.entrySet()) {
            Class<MessageLite> messageType = (Class<MessageLite>) entry.getKey();
            Parser<MessageLite> parser = (Parser<MessageLite>) entry.getValue();

            Map<ContentCodec, HttpSerializer> serializersForType = new HashMap<>();
            Map<ContentCodec, HttpDeserializer> deserializersForType = new HashMap<>();
            for (ContentCodec codec : supportedCodings) {
                DefaultSerializer serializer = new DefaultSerializer(
                        new ProtoBufSerializationProvider<>(messageType, codec, parser));
                HttpSerializer<MessageLite> httpSerializer = new ProtoHttpSerializer<>(serializer, codec, messageType);
                serializersForType.put(codec, httpSerializer);
                deserializersForType.put(codec, new HttpDeserializer<MessageLite>() {
                    @Override
                    public MessageLite deserialize(final HttpHeaders headers, final Buffer payload) {
                        return serializer.deserializeAggregatedSingle(payload, messageType);
                    }

                    @Override
                    public BlockingIterable<MessageLite> deserialize(final HttpHeaders headers,
                                                                     final BlockingIterable<Buffer> payload) {
                        return serializer.deserialize(payload, messageType);
                    }

                    @Override
                    public Publisher<MessageLite> deserialize(final HttpHeaders headers,
                                                              final Publisher<Buffer> payload) {
                        return serializer.deserialize(payload, messageType);
                    }
                });
            }
            serializers.put(messageType, serializersForType);
            deserializers.put(messageType, deserializersForType);
        }
    }

    /**
     * Builds a new {@link GrpcSerializationProvider} containing all the message types registered with this builder.
     *
     * @return New {@link GrpcSerializationProvider} that will serialize and deserialize message types that were
     * registered to this builder.
     */
    public GrpcSerializationProvider build() {
        build0();
        return new ProtoSerializationProvider(serializers, deserializers, supportedCodings);
    }

    private static class ProtoSerializationProvider implements GrpcSerializationProvider {
        private final Map<Class, Map<ContentCodec, HttpSerializer>> serializers;
        private final Map<Class, Map<ContentCodec, HttpDeserializer>> deserializers;
        private final List<ContentCodec> supportedCodings;

        ProtoSerializationProvider(final Map<Class, Map<ContentCodec, HttpSerializer>> serializers,
                                   final Map<Class, Map<ContentCodec, HttpDeserializer>> deserializers,
                                   final List<ContentCodec> supportedCodings) {
            this.serializers = unmodifiableMap(serializers);
            this.deserializers = unmodifiableMap(deserializers);
            this.supportedCodings = unmodifiableList(supportedCodings);
        }

        @Override
        public List<ContentCodec> supportedMessageCodings() {
            return supportedCodings;
        }

        @Override
        public <T> HttpSerializer<T> serializerFor(final ContentCodec codec, final Class<T> type) {
            Map<ContentCodec, HttpSerializer> serializersForType = serializers.get(type);
            if (serializersForType == null) {
                throw new SerializationException("Unknown class to serialize: " + type.getName());
            }
            @SuppressWarnings("unchecked")
            HttpSerializer<T> httpSerializer = serializersForType.get(codec);
            if (httpSerializer == null) {
                throw new MessageEncodingException("Unknown encoding: " + codec.name());
            }
            return httpSerializer;
        }

        @Override
        public <T> HttpDeserializer<T> deserializerFor(final ContentCodec codec, final Class<T> type) {
            Map<ContentCodec, HttpDeserializer> deserializersForType = deserializers.get(type);
            if (deserializersForType == null) {
                throw new SerializationException("Unknown class to deserialize: " + type.getName());
            }
            @SuppressWarnings("unchecked")
            HttpDeserializer<T> httpSerializer = deserializersForType.get(codec);
            if (httpSerializer == null) {
                throw new MessageEncodingException("Unknown encoding: " + codec.name());
            }
            return httpSerializer;
        }
    }

    private static final class ProtoHttpSerializer<T extends MessageLite> implements HttpSerializer<T> {
        private static final int METADATA_SIZE = 5; // 1 byte for compression flag and 4 bytes for length of data

        private final Serializer serializer;
        private final ContentCodec codec;
        private final Class<T> type;
        ProtoHttpSerializer(final Serializer serializer, final ContentCodec codec,
                            final Class<T> type) {
            this.serializer = serializer;
            this.codec = codec;
            this.type = type;
        }

        @Override
        public Buffer serialize(final HttpHeaders headers, final T value, final BufferAllocator allocator) {
            addContentHeaders(headers);
            return serializer.serialize(value, allocator, METADATA_SIZE + value.getSerializedSize());
        }

        @Override
        public BlockingIterable<Buffer> serialize(final HttpHeaders headers,
                                                  final BlockingIterable<T> value,
                                                  final BufferAllocator allocator) {
            addContentHeaders(headers);
            return serializer.serialize(value, allocator, type);
        }

        @Override
        public Publisher<Buffer> serialize(final HttpHeaders headers, final Publisher<T> value,
                                           final BufferAllocator allocator) {
            addContentHeaders(headers);
            return serializer.serialize(value, allocator, type);
        }

        @Override
        public HttpPayloadWriter<T> serialize(final HttpHeaders headers,
                                              final HttpPayloadWriter<Buffer> payloadWriter,
                                              final BufferAllocator allocator) {
            addContentHeaders(headers);
            return new HttpPayloadWriter<T>() {
                @Override
                public HttpHeaders trailers() {
                    return payloadWriter.trailers();
                }

                @Override
                public void write(final T t) throws IOException {
                    payloadWriter.write(serializer.serialize(t, allocator));
                }

                @Override
                public void close() throws IOException {
                    payloadWriter.close();
                }

                @Override
                public void close(final Throwable cause) throws IOException {
                    payloadWriter.close(cause);
                }

                @Override
                public void flush() throws IOException {
                    payloadWriter.flush();
                }
            };
        }

        private void addContentHeaders(final HttpHeaders headers) {
            headers.set(CONTENT_TYPE, APPLICATION_GRPC_PROTO);
            if (!identity().equals(codec)) {
                headers.set(GRPC_MESSAGE_ENCODING_KEY, codec.name());
            }
        }
    }
}
