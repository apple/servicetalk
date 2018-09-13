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
import java.util.function.Predicate;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpStringDeserializer.UTF_8_STRING_DESERIALIZER;

/**
 * A factory to create {@link HttpDeserializerProvider}s.
 */
public final class HttpDeserializerProviders {

    private HttpDeserializerProviders() {
        // No instances.
    }

    /**
     * Creates an {@link HttpDeserializer} that can deserialize {@link String}s with {@link StandardCharsets#UTF_8}
     * {@code Charset}.
     *
     * @return {@link HttpDeserializer} that could deserialize {@link String}.
     */
    public static HttpDeserializer<String> forUtf8String() {
        return UTF_8_STRING_DESERIALIZER;
    }

    /**
     * Creates an {@link HttpDeserializer} that can deserialize {@link String}s with the specified {@link Charset}.
     *
     * @param charset {@link Charset} for the {@link String} that will be deserialized.
     * @return {@link HttpDeserializer} that could deserialize {@link String}.
     */
    public static HttpDeserializer<String> forString(Charset charset) {
        final String contentType = TEXT_PLAIN + "; charset=" + charset.name();
        return forString(charset, headers -> headers.contains(CONTENT_TYPE, contentType));
    }

    /**
     * Creates an {@link HttpDeserializer} that can deserialize {@link String}s with the specified {@link Charset}.
     *
     * @param charset {@link Charset} for the {@link String} that will be deserialized.
     * @param checkContentType A {@link Predicate} that validates the passed {@link HttpHeaders} as expected for the
     * deserialized payload. If the validation fails, then deserialization will fail with {@link SerializationException}
     * @return {@link HttpDeserializer} that could deserialize {@link String}.
     */
    public static HttpDeserializer<String> forString(Charset charset, Predicate<HttpHeaders> checkContentType) {
        return new HttpStringDeserializer(charset, checkContentType);
    }

    /**
     * Creates a new {@link HttpDeserializerProvider} that could deserialize from JSON using the passed
     * {@link Serializer}. The returned {@link HttpDeserializerProvider} expects a {@link HttpHeaderNames#CONTENT_TYPE}
     * header with value {@link HttpHeaderValues#APPLICATION_JSON} while deserializing. If the expected header is not
     * present, then deserialization will fail with {@link SerializationException}.
     *
     * @param serializer {@link Serializer} that has the capability to deserialize from JSON.
     * @return {@link HttpDeserializerProvider} that could deserialize from JSON.
     */
    public static HttpDeserializerProvider forJson(Serializer serializer) {
        return forContentType(serializer, headers -> headers.contains(CONTENT_TYPE, APPLICATION_JSON));
    }

    /**
     * Creates a new {@link HttpDeserializerProvider} that could deserialize from JSON using the passed
     * {@link SerializationProvider}. The returned {@link HttpDeserializerProvider} expects a
     * {@link HttpHeaderNames#CONTENT_TYPE} header with value {@link HttpHeaderValues#APPLICATION_JSON} while
     * deserializing. If the expected header is not present, then deserialization will fail with
     * {@link SerializationException}.
     *
     * @param serializationProvider {@link SerializationProvider} that has the capability to deserialize from JSON.
     * @return {@link HttpDeserializerProvider} that could deserialize from JSON.
     */
    public static HttpDeserializerProvider forJson(SerializationProvider serializationProvider) {
        return forJson(new DefaultSerializer(serializationProvider));
    }

    /**
     * Creates a new {@link HttpSerializerProvider} that could deserialize using the passed {@link Serializer} from the
     * expected content-type. The returned {@link HttpSerializerProvider} would validate headers as specified by the
     * passed {@link Predicate checkContentType predicate}. If the validation fails, then deserialization will fail with
     * {@link SerializationException}.
     *
     * @param serializer {@link Serializer} that has the capability to deserialize from desired content-type.
     * @param checkContentType A {@link Predicate} that validates the passed {@link HttpHeaders} as expected for the
     * deserialized payload. If the validation fails, then deserialization will fail with {@link SerializationException}
     * @return {@link HttpDeserializerProvider} that could deserialize from the desired content-type.
     */
    public static HttpDeserializerProvider forContentType(Serializer serializer,
                                                          Predicate<HttpHeaders> checkContentType) {
        return new DefaultHttpDeserializerProvider(serializer, checkContentType);
    }

    /**
     * Creates a new {@link HttpSerializerProvider} that could deserialize using the passed {@link Serializer} from the
     * expected content-type. The returned {@link HttpSerializerProvider} would validate headers as specified by the
     * passed {@link Predicate checkContentType predicate}. If the validation fails, then deserialization will fail with
     * {@link SerializationException}.
     *
     * @param serializationProvider {@link SerializationProvider} that has the capability to deserialize from desired
     * content-type.
     * @param checkContentType A {@link Predicate} that validates the passed {@link HttpHeaders} as expected for the
     * deserialized payload. If the validation fails, then deserialization will fail with
     * {@link SerializationException}.
     * @return {@link HttpDeserializerProvider} that could deserialize from the desired content-type.
     */
    public static HttpDeserializerProvider forContentType(SerializationProvider serializationProvider,
                                                          Predicate<HttpHeaders> checkContentType) {
        return forContentType(new DefaultSerializer(serializationProvider), checkContentType);
    }
}
