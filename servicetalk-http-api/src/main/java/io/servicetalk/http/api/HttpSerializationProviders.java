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
package io.servicetalk.http.api;

import io.servicetalk.serialization.api.DefaultSerializer;
import io.servicetalk.serialization.api.SerializationException;
import io.servicetalk.serialization.api.SerializationProvider;
import io.servicetalk.serialization.api.Serializer;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpStringDeserializer.UTF_8_STRING_DESERIALIZER;
import static io.servicetalk.http.api.HttpStringSerializer.UTF8_STRING_SERIALIZER;

/**
 * A factory to create {@link HttpSerializationProvider}s.
 */
public final class HttpSerializationProviders {

    private HttpSerializationProviders() {
        // No instances.
    }

    /**
     * Creates an {@link HttpSerializer} that can serialize {@link String}s with {@link StandardCharsets#UTF_8}
     * {@code Charset}.
     *
     * @return {@link HttpSerializer} that could serialize from {@link String}.
     */
    public static HttpSerializer<String> serializerForUtf8PlainText() {
        return UTF8_STRING_SERIALIZER;
    }

    /**
     * Creates an {@link HttpSerializer} that can serialize {@link String}s with the specified {@link Charset}.
     *
     * @param charset {@link Charset} for the {@link String} that will be serialized.
     * @return {@link HttpSerializer} that could serialize from {@link String}.
     */
    public static HttpSerializer<String> serializerForPlainText(Charset charset) {
        final String contentType = TEXT_PLAIN + "; charset=" + charset.name();
        return serializerForPlainText(charset, headers -> headers.set(CONTENT_TYPE, contentType));
    }

    /**
     * Creates an {@link HttpSerializer} that can serialize {@link String}s with the specified {@link Charset}.
     *
     * @param charset {@link Charset} for the {@link String} that will be serialized.
     * @param addContentType A {@link Consumer} that adds relevant headers to the passed {@link HttpHeaders} matching
     * the serialized payload. Typically, this involves adding a {@link HttpHeaderNames#CONTENT_TYPE} header.
     * @return {@link HttpSerializer} that could serialize from {@link String}.
     */
    public static HttpSerializer<String> serializerForPlainText(Charset charset,
                                                                Consumer<HttpHeaders> addContentType) {
        return new HttpStringSerializer(charset, addContentType);
    }

    /**
     * Creates an {@link HttpDeserializer} that can deserialize {@link String}s with {@link StandardCharsets#UTF_8}
     * {@code Charset}.
     *
     * @return {@link HttpDeserializer} that could deserialize {@link String}.
     */
    public static HttpDeserializer<String> deserializerForUtf8PlainText() {
        return UTF_8_STRING_DESERIALIZER;
    }

    /**
     * Creates an {@link HttpDeserializer} that can deserialize {@link String}s with the specified {@link Charset}.
     *
     * @param charset {@link Charset} for the {@link String} that will be deserialized.
     * @return {@link HttpDeserializer} that could deserialize {@link String}.
     */
    public static HttpDeserializer<String> deserializerForPlainText(Charset charset) {
        final String contentType = TEXT_PLAIN + "; charset=" + charset.name();
        return deserializerForPlainText(charset, headers -> headers.contains(CONTENT_TYPE, contentType));
    }

    /**
     * Creates an {@link HttpDeserializer} that can deserialize {@link String}s with the specified {@link Charset}.
     *
     * @param charset {@link Charset} for the {@link String} that will be deserialized.
     * @param checkContentType A {@link Predicate} that validates the passed {@link HttpHeaders} as expected for the
     * deserialized payload. If the validation fails, then deserialization will fail with {@link SerializationException}
     * @return {@link HttpDeserializer} that could deserialize {@link String}.
     */
    public static HttpDeserializer<String> deserializerForPlainText(Charset charset,
                                                                    Predicate<HttpHeaders> checkContentType) {
        return new HttpStringDeserializer(charset, checkContentType);
    }

    /**
     * Creates a new {@link HttpSerializationProvider} that could serialize/deserialize to/from JSON using the passed
     * {@link Serializer}. For serialization, the returned {@link HttpSerializationProvider} adds a
     * {@link HttpHeaderNames#CONTENT_TYPE} header with value {@link HttpHeaderValues#APPLICATION_JSON}.
     * For deserialization, it expects a {@link HttpHeaderNames#CONTENT_TYPE} header with value
     * {@link HttpHeaderValues#APPLICATION_JSON}. If the expected header is not present, then deserialization will fail
     * with {@link SerializationException}.
     *
     * @param serializer {@link Serializer} that has the capability of serializing/deserializing to/from JSON.
     * @return {@link HttpSerializationProvider} that has the capability of serializing/deserializing to/from JSON.
     */
    public static HttpSerializationProvider forJson(Serializer serializer) {
        return forContentType(serializer, headers -> headers.set(CONTENT_TYPE, APPLICATION_JSON),
                headers -> headers.contains(CONTENT_TYPE, APPLICATION_JSON));
    }

    /**
     * Creates a new {@link HttpSerializationProvider} that could serialize/deserialize to/from JSON using the passed
     * {@link SerializationProvider}. For serialization, the returned {@link HttpSerializationProvider} adds a
     * {@link HttpHeaderNames#CONTENT_TYPE} header with value {@link HttpHeaderValues#APPLICATION_JSON}.
     * For deserialization, it expects a {@link HttpHeaderNames#CONTENT_TYPE} header with value
     * {@link HttpHeaderValues#APPLICATION_JSON}. If the expected header is not present, then deserialization will fail
     * with {@link SerializationException}.
     *
     * @param serializationProvider {@link SerializationProvider} that has the capability of serializing/deserializing
     * to/from JSON.
     * @return {@link HttpSerializationProvider} that has the capability of serializing/deserializing to/from JSON.
     */
    public static HttpSerializationProvider forJson(SerializationProvider serializationProvider) {
        return forJson(new DefaultSerializer(serializationProvider));
    }

    /**
     * Creates a new {@link HttpSerializationProvider} that could serialize/deserialize to/from the desired content-type
     * using the passed {@link Serializer}.<p>
     * For serialization, the returned {@link HttpSerializationProvider}
     * would update {@link HttpHeaders} appropriately to indicate the content-type using the passed
     * {@link Consumer addContentType}.<p>
     * For deserialization, it would validate headers as specified by the passed
     * {@link Predicate checkContentType predicate}. If the validation fails, then deserialization will fail with
     * {@link SerializationException}.
     *
     * @param serializer {@link Serializer} that has the capability of serializing/deserializing to/from a desired
     * content-type.
     * @param addContentType A {@link Consumer} that adds relevant headers to the passed {@link HttpHeaders} matching
     * the serialized payload. Typically, this involves adding a {@link HttpHeaderNames#CONTENT_TYPE} header.
     * @param checkContentType A {@link Predicate} that validates the passed {@link HttpHeaders} as expected for the
     * deserialized payload. If the validation fails, then deserialization will fail with {@link SerializationException}
     * @return {@link HttpSerializationProvider} that has the capability of serializing/deserializing to/from a desired
     * content-type.
     */
    public static HttpSerializationProvider forContentType(Serializer serializer,
                                                           Consumer<HttpHeaders> addContentType,
                                                           Predicate<HttpHeaders> checkContentType) {
        return new DefaultHttpSerializationProvider(serializer, addContentType, checkContentType);
    }

    /**
     * Creates a new {@link HttpSerializationProvider} that could serialize/deserialize to/from the desired content-type
     * using the passed {@link SerializationProvider}.<p>
     * For serialization, the returned {@link HttpSerializationProvider}
     * would update {@link HttpHeaders} appropriately to indicate the content-type using the passed
     * {@link Consumer addContentType}.<p>
     * For deserialization, it would validate headers as specified by the passed
     * {@link Predicate checkContentType predicate}. If the validation fails, then deserialization will fail with
     * {@link SerializationException}.
     *
     * @param serializationProvider {@link SerializationProvider} that has the capability of serializing/deserializing
     * to/from a desired content-type.
     * @param addContentType A {@link Consumer} that adds relevant headers to the passed {@link HttpHeaders} matching
     * the serialized payload. Typically, this involves adding a {@link HttpHeaderNames#CONTENT_TYPE} header.
     * @param checkContentType A {@link Predicate} that validates the passed {@link HttpHeaders} as expected for the
     * deserialized payload. If the validation fails, then deserialization will fail with
     * {@link SerializationException}.
     * @return {@link HttpSerializationProvider} that has the capability of serializing/deserializing to/from a desired
     * content-type.
     */
    public static HttpSerializationProvider forContentType(SerializationProvider serializationProvider,
                                                           Consumer<HttpHeaders> addContentType,
                                                           Predicate<HttpHeaders> checkContentType) {
        return forContentType(new DefaultSerializer(serializationProvider), addContentType, checkContentType);
    }
}
