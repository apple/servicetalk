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
import io.servicetalk.serialization.api.SerializationProvider;
import io.servicetalk.serialization.api.Serializer;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpStringSerializer.UTF8_STRING_SERIALIZER;

/**
 * A factory to create {@link HttpSerializerProvider}s.
 */
public final class HttpSerializerProviders {

    private HttpSerializerProviders() {
        // No instances.
    }

    /**
     * Creates an {@link HttpSerializer} that can serialize {@link String}s with {@link StandardCharsets#UTF_8}
     * {@code Charset}.
     *
     * @return {@link HttpSerializer} that could serialize from {@link String}.
     */
    public static HttpSerializer<String> forUtf8PlainText() {
        return UTF8_STRING_SERIALIZER;
    }

    /**
     * Creates an {@link HttpSerializer} that can serialize {@link String}s with the specified {@link Charset}.
     *
     * @param charset {@link Charset} for the {@link String} that will be serialized.
     * @return {@link HttpSerializer} that could serialize from {@link String}.
     */
    public static HttpSerializer<String> forPlainText(Charset charset) {
        final String contentType = TEXT_PLAIN + "; charset=" + charset.name();
        return forPlainText(charset, headers -> headers.set(CONTENT_TYPE, contentType));
    }

    /**
     * Creates an {@link HttpSerializer} that can serialize {@link String}s with the specified {@link Charset}.
     *
     * @param charset {@link Charset} for the {@link String} that will be serialized.
     * @param addContentType A {@link Consumer} that adds relevant headers to the passed {@link HttpHeaders} matching
     * the serialized payload. Typically, this involves adding a {@link HttpHeaderNames#CONTENT_TYPE} header.
     * @return {@link HttpSerializer} that could serialize from {@link String}.
     */
    public static HttpSerializer<String> forPlainText(Charset charset, Consumer<HttpHeaders> addContentType) {
        return new HttpStringSerializer(charset, addContentType);
    }

    /**
     * Creates a new {@link HttpSerializerProvider} that could serialize to JSON using the passed
     * {@link Serializer}. The returned {@link HttpSerializerProvider} adds a {@link HttpHeaderNames#CONTENT_TYPE}
     * header with value {@link HttpHeaderValues#APPLICATION_JSON}.
     *
     * @param serializer {@link Serializer} that has the capability of serializing to JSON.
     * @return {@link HttpSerializerProvider} that could serialize to JSON.
     */
    public static HttpSerializerProvider forJson(Serializer serializer) {
        return forContentType(serializer, headers -> headers.set(CONTENT_TYPE, APPLICATION_JSON));
    }

    /**
     * Creates a new {@link HttpSerializerProvider} that could serialize to JSON using the passed
     * {@link SerializationProvider}. The returned {@link HttpSerializerProvider} adds a
     * {@link HttpHeaderNames#CONTENT_TYPE} header with value {@link HttpHeaderValues#APPLICATION_JSON}.
     *
     * @param serializationProvider {@link SerializationProvider} that has the capability of serializing to JSON.
     * @return {@link HttpSerializerProvider} that could serialize to JSON.
     */
    public static HttpSerializerProvider forJson(SerializationProvider serializationProvider) {
        return forJson(new DefaultSerializer(serializationProvider));
    }

    /**
     * Creates a new {@link HttpSerializerProvider} that could serialize using the passed {@link Serializer} to the
     * desired content-type. The returned {@link HttpSerializerProvider} would update {@link HttpHeaders} appropriately
     * to indicate the content-type using the passed {@link Consumer addContentType}.
     *
     * @param serializer {@link Serializer} that has the capability of serializing to a desired content-type.
     * @param addContentType A {@link Consumer} that adds relevant headers to the passed {@link HttpHeaders} matching
     * the serialized payload. Typically, this involves adding a {@link HttpHeaderNames#CONTENT_TYPE} header.
     * @return {@link HttpSerializerProvider} that could serialize to the specified content-type.
     */
    public static HttpSerializerProvider forContentType(Serializer serializer, Consumer<HttpHeaders> addContentType) {
        return new DefaultHttpSerializerProvider(serializer, addContentType);
    }

    /**
     * Creates a new {@link HttpSerializerProvider} that could serialize using the passed {@link SerializationProvider}
     * to the desired content-type. The returned {@link HttpSerializerProvider} would update {@link HttpHeaders}
     * appropriately to indicate the content-type using the passed {@link Consumer addContentType}.
     *
     * @param serializationProvider {@link SerializationProvider} that has the capability of serializing to a desired
     * content-type.
     * @param addContentType A {@link Consumer} that adds relevant headers to the passed {@link HttpHeaders} matching
     * the serialized payload. Typically, this involves adding a {@link HttpHeaderNames#CONTENT_TYPE} header.
     * @return {@link HttpSerializerProvider} that could serialize to the specified content-type.
     */
    public static HttpSerializerProvider forContentType(SerializationProvider serializationProvider,
                                                        Consumer<HttpHeaders> addContentType) {
        return forContentType(new DefaultSerializer(serializationProvider), addContentType);
    }
}
