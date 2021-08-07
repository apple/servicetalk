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
package io.servicetalk.data.protobuf;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.serialization.api.SerializationException;
import io.servicetalk.serialization.api.SerializationProvider;
import io.servicetalk.serialization.api.StreamingDeserializer;
import io.servicetalk.serialization.api.StreamingSerializer;
import io.servicetalk.serialization.api.TypeHolder;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import static io.servicetalk.buffer.api.Buffer.asInputStream;
import static io.servicetalk.buffer.api.Buffer.asOutputStream;
import static java.util.Collections.singletonList;

/**
 * A {@link SerializationProvider} for serializing/deserializing
 * <a href="https://developers.google.com/protocol-buffers/">protocol buffer</a> objects.
 *
 * Note: This implementation assumes byte streams represent a single message. This implementation currently uses
 * {@code writeTo/parseFrom} and not {@code writeDelimitedTo/parseDelimitedFrom} to serialize/deserialize messages.
 * It cannot be used to process a stream of delimited messages on a single Buffer.
 * @deprecated Use {@link ProtobufSerializerFactory}.
 */
@Deprecated
public final class ProtobufSerializationProvider implements SerializationProvider {

    private final ConcurrentMap<Class, Parser> parsers;
    private final Function<Class<?>, Parser<?>> parserForClass;

    public ProtobufSerializationProvider() {
        this(ProtobufSerializationProvider::reflectionParserFor);
    }

    public ProtobufSerializationProvider(final Function<Class<?>, Parser<?>> parserForClass) {
        this.parsers = new ConcurrentHashMap<>();
        this.parserForClass = parserForClass;
    }

    @Override
    public <T> StreamingSerializer getSerializer(final Class<T> classToSerialize) {
        requireMessageLite(classToSerialize);
        return new ProtobufSerializer();
    }

    @Override
    public <T> StreamingSerializer getSerializer(final TypeHolder<T> typeToSerialize) {
        return getSerializer(classFor(typeToSerialize));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> StreamingDeserializer<T> getDeserializer(final Class<T> classToDeSerialize) {
        requireMessageLite(classToDeSerialize);
        Parser<T> parser = parsers.computeIfAbsent(classToDeSerialize, parserForClass::apply);
        return new ProtobufDeserializer<>(parser);
    }

    @Override
    public <T> StreamingDeserializer<T> getDeserializer(final TypeHolder<T> typeToDeserialize) {
        return getDeserializer(classFor(typeToDeserialize));
    }

    @SuppressWarnings("unchecked")
    private static <X> Class<X> classFor(final TypeHolder<X> typeHolder) {
        Type type = typeHolder.type();
        if (type instanceof Class) {
            return (Class<X>) type;
        }
        throw new SerializationException("Type is not an instance of Class: " + type.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    static <T> Parser<T> reflectionParserFor(final Class<T> classToDeSerialize) {
        requireMessageLite(classToDeSerialize);
        try {
            // using reflection to get the ubiquitous static PARSER field that's been there since at least proto 2.5
            // probably before but that's the oldest version that we currently still need to support with this
            Field field = classToDeSerialize.getDeclaredField("PARSER");
            field.setAccessible(true);
            Object object = field.get(null);
            if (object instanceof Parser) {
                return (Parser<T>) object;
            }
            throw new SerializationException("'PARSER' field from " + classToDeSerialize.getName() +
                    " was not an instance of " + Parser.class.getName());
        } catch (NoSuchFieldException e) {
            throw new SerializationException("Could not find static field 'PARSER' from " +
                    classToDeSerialize.getName());
        } catch (IllegalAccessException e) {
            throw new SerializationException("'PARSER' field on " + classToDeSerialize.getName() +
                    " was not publicly accessible");
        }
    }

    private static <T> void requireMessageLite(final Class<T> type) {
        if (!MessageLite.class.isAssignableFrom(type)) {
            throw new SerializationException("class is not an instance of MessageLite: " + type.getName());
        }
    }

    private static final class ProtobufSerializer implements StreamingSerializer {
        @Override
        public void serialize(final Object toSerialize, final Buffer destination) {
            try (OutputStream out = asOutputStream(destination)) {
                ((MessageLite) toSerialize).writeTo(out);
            } catch (IOException e) {
                throw new SerializationException("error trying to write object", e);
            }
        }
    }

    private static final class ProtobufDeserializer<T> implements StreamingDeserializer<T> {
        private final Parser<T> parser;

        ProtobufDeserializer(final Parser<T> parser) {
            this.parser = parser;
        }

        @Override
        public Iterable<T> deserialize(final Buffer toDeserialize) {
            try (InputStream in = asInputStream(toDeserialize)) {
                // using InputStream as proto 2.5 doesn't have parseFrom(ByteBuffer)
                return singletonList(parser.parseFrom(in));
            } catch (InvalidProtocolBufferException e) {
                throw new SerializationException("error trying to parse protobuf", e);
            } catch (IOException e) {
                throw new SerializationException("error reading from buffer", e);
            }
        }

        @Override
        public boolean hasData() {
            // Since the parser.parseFrom() method we are using consumes all the data, this will always return false.
            return false;
        }

        @Override
        public void close() {
            // noop
        }
    }
}
