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
import io.servicetalk.serialization.api.SerializationProvider;
import io.servicetalk.serialization.api.StreamingDeserializer;
import io.servicetalk.serialization.api.StreamingSerializer;
import io.servicetalk.serialization.api.TypeHolder;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.core.async.ByteBufferFeeder;
import com.fasterxml.jackson.core.async.NonBlockingInputFeeder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.IOException;

import static io.servicetalk.buffer.api.Buffer.asOutputStream;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.Objects.requireNonNull;

/**
 * {@link SerializationProvider} implementation using jackson.
 * @deprecated Use {@link JacksonSerializerFactory}.
 */
@Deprecated
public final class JacksonSerializationProvider implements SerializationProvider {

    private final ObjectMapper mapper;

    /**
     * New instances which will use the default {@link ObjectMapper}.
     */
    public JacksonSerializationProvider() {
        this(new ObjectMapper());
    }

    /**
     * New instance.
     *
     * @param mapper {@link ObjectMapper} to use.
     */
    public JacksonSerializationProvider(final ObjectMapper mapper) {
        this.mapper = requireNonNull(mapper);
    }

    @Override
    public <T> StreamingSerializer getSerializer(final Class<T> classToSerialize) {
        final ObjectWriter writer = mapper.writerFor(classToSerialize);
        return (toSerialize, destination) -> serialize0(writer, toSerialize, destination);
    }

    @Override
    public <T> StreamingSerializer getSerializer(final TypeHolder<T> typeToSerialize) {
        final ObjectWriter writer = mapper.writerFor(mapper.constructType(typeToSerialize.type()));
        return (toSerialize, destination) -> serialize0(writer, toSerialize, destination);
    }

    @Override
    public <T> StreamingDeserializer<T> getDeserializer(final Class<T> classToDeSerialize) {
        return newDeserializer(mapper.readerFor(classToDeSerialize));
    }

    @Override
    public <T> StreamingDeserializer<T> getDeserializer(final TypeHolder<T> typeToDeserialize) {
        return newDeserializer(mapper.readerFor(mapper.constructType(typeToDeserialize.type())));
    }

    @Override
    public <T> void serialize(final T toSerialize, final Buffer destination) {
        serialize0(mapper.writerFor(toSerialize.getClass()), toSerialize, destination);
    }

    private static void serialize0(final ObjectWriter writer, final Object toSerialize, final Buffer destination) {
        try {
            writer.writeValue(asOutputStream(destination), toSerialize);
        } catch (IOException e) {
            throwException(e);
        }
    }

    private static <T> StreamingDeserializer<T> newDeserializer(ObjectReader reader) {
        final JsonFactory factory = reader.getFactory();
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
            return new ByteBufferJacksonDeserializer<>(reader, parser, (ByteBufferFeeder) rawFeeder);
        }
        if (rawFeeder instanceof ByteArrayFeeder) {
            return new ByteArrayJacksonDeserializer<>(reader, parser, (ByteArrayFeeder) rawFeeder);
        }
        throw new IllegalArgumentException("unsupported feeder type: " + rawFeeder);
    }
}
