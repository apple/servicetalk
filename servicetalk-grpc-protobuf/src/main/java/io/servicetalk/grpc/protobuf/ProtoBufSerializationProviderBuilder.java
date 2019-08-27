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
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.grpc.api.GrpcMessageEncoding;
import io.servicetalk.grpc.api.GrpcMetadata;
import io.servicetalk.grpc.api.GrpcSerializationProvider;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static io.servicetalk.grpc.api.GrpcMessageEncoding.None;
import static io.servicetalk.http.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static java.util.Collections.unmodifiableMap;

/**
 * A builder for building a {@link GrpcSerializationProvider} that can serialize and deserialize
 * pre-registered <a href="https://developers.google.com/protocol-buffers/">protocol buffer</a> objects.
 * {@link #registerMessageType(Class, Parser)} is used to add one or more {@link MessageLite} message types. Resulting
 * {@link GrpcSerializationProvider} from {@link #build()} will only serialize and deserialize those message types.
 */
public final class ProtoBufSerializationProviderBuilder {
    private static final CharSequence GRPC_MESSAGE_ENCODING_KEY = newAsciiString("grpc-encoding");
    private static final CharSequence APPLICATION_GRPC_PROTO = newAsciiString("application/grpc+proto");

    private final Map<Class, EnumMap<GrpcMessageEncoding, HttpSerializer>> serializers = new HashMap<>();
    private final Map<Class, EnumMap<GrpcMessageEncoding, HttpDeserializer>> deserializers = new HashMap<>();

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
        EnumMap<GrpcMessageEncoding, HttpSerializer> serializersForType = new EnumMap<>(GrpcMessageEncoding.class);
        EnumMap<GrpcMessageEncoding, HttpDeserializer> deserializersForType = new EnumMap<>(GrpcMessageEncoding.class);
        for (GrpcMessageEncoding grpcMessageEncoding : GrpcMessageEncoding.values()) {
            DefaultSerializer serializer = new DefaultSerializer(
                    new ProtoBufSerializationProvider<>(messageType, grpcMessageEncoding, parser));
            HttpSerializer<T> httpSerializer = new ProtoHttpSerializer<>(serializer, grpcMessageEncoding, messageType);
            serializersForType.put(grpcMessageEncoding, httpSerializer);
            deserializersForType.put(grpcMessageEncoding, new HttpDeserializer<T>() {
                @Override
                public T deserialize(final HttpHeaders headers, final Buffer payload) {
                    return serializer.deserializeAggregatedSingle(payload, messageType);
                }

                @Override
                public BlockingIterable<T> deserialize(final HttpHeaders headers,
                                                       final BlockingIterable<Buffer> payload) {
                    return serializer.deserialize(payload, messageType);
                }

                @Override
                public Publisher<T> deserialize(final HttpHeaders headers, final Publisher<Buffer> payload) {
                    return serializer.deserialize(payload, messageType);
                }
            });
        }

        serializers.put(messageType, serializersForType);
        deserializers.put(messageType, deserializersForType);
        return this;
    }

    /**
     * Builds a new {@link GrpcSerializationProvider} containing all the message types registered with this builder.
     *
     * @return New {@link GrpcSerializationProvider} that will serialize and deserialize message types that were
     * registered to this builder.
     */
    public GrpcSerializationProvider build() {
        return new ProtoSerializationProvider(serializers, deserializers);
    }

    private static class ProtoSerializationProvider implements GrpcSerializationProvider {
        private final Map<Class, EnumMap<GrpcMessageEncoding, HttpSerializer>> serializers;
        private final Map<Class, EnumMap<GrpcMessageEncoding, HttpDeserializer>> deserializers;

        ProtoSerializationProvider(final Map<Class, EnumMap<GrpcMessageEncoding, HttpSerializer>> serializers,
                                   final Map<Class, EnumMap<GrpcMessageEncoding, HttpDeserializer>> deserializers) {
            this.serializers = unmodifiableMap(serializers);
            this.deserializers = unmodifiableMap(deserializers);
        }

        @Override
        public <T> HttpSerializer<T> serializerFor(final GrpcMetadata metadata, final Class<T> type) {
            EnumMap<GrpcMessageEncoding, HttpSerializer> serializersForType = serializers.get(type);
            if (serializersForType == null) {
                throw new SerializationException("Unknown class to serialize: " + type.getName());
            }
            @SuppressWarnings("unchecked")
            HttpSerializer<T> httpSerializer = serializersForType.get(None); // compression not yet supported.
            return httpSerializer;
        }

        @Override
        public <T> HttpDeserializer<T> deserializerFor(final GrpcMessageEncoding messageEncoding, final Class<T> type) {
            EnumMap<GrpcMessageEncoding, HttpDeserializer> deserializersForType = deserializers.get(type);
            if (deserializersForType == null) {
                throw new SerializationException("Unknown class to deserialize: " + type.getName());
            }
            @SuppressWarnings("unchecked")
            HttpDeserializer<T> httpSerializer = deserializersForType.get(messageEncoding);
            return httpSerializer;
        }
    }

    private static final class ProtoHttpSerializer<T> implements HttpSerializer<T> {
        private final Serializer serializer;
        private final GrpcMessageEncoding grpcMessageEncoding;
        private final Class<T> type;

        ProtoHttpSerializer(final Serializer serializer, final GrpcMessageEncoding grpcMessageEncoding,
                            final Class<T> type) {
            this.serializer = serializer;
            this.grpcMessageEncoding = grpcMessageEncoding;
            this.type = type;
        }

        @Override
        public Buffer serialize(final HttpHeaders headers, final T value, final BufferAllocator allocator) {
            addContentHeaders(headers);
            return serializer.serialize(value, allocator);
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
                public void flush() throws IOException {
                    payloadWriter.flush();
                }
            };
        }

        private void addContentHeaders(final HttpHeaders headers) {
            headers.set(CONTENT_TYPE, APPLICATION_GRPC_PROTO);
            headers.set(GRPC_MESSAGE_ENCODING_KEY, grpcMessageEncoding.encoding());
        }
    }
}
