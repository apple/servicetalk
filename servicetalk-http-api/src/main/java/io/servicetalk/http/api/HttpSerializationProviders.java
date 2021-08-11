/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.serializer.api.SerializerDeserializer;
import io.servicetalk.serializer.api.StreamingSerializerDeserializer;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HeaderUtils.hasContentType;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpStringDeserializer.UTF_8_STRING_DESERIALIZER;
import static io.servicetalk.http.api.HttpStringSerializer.UTF8_STRING_SERIALIZER;

/**
 * A factory to create {@link HttpSerializationProvider}s.
 * @deprecated Use {@link HttpSerializers}.
 */
@Deprecated
public final class HttpSerializationProviders {

    private HttpSerializationProviders() {
        // No instances.
    }

    /**
     * Creates an {@link HttpSerializer} that can serialize a key-values {@link Map}s
     * with {@link StandardCharsets#UTF_8} {@code Charset} to urlencoded forms.
     * @deprecated Use {@link HttpSerializers#formUrlEncodedSerializer()}.
     * @return {@link HttpSerializer} that could serialize key-value {@link Map}.
     * @see <a
     * href="https://url.spec.whatwg.org/#application/x-www-form-urlencoded">x-www-form-urlencoded specification</a>
     */
    @Deprecated
    public static HttpSerializer<Map<String, List<String>>> formUrlEncodedSerializer() {
        return FormUrlEncodedHttpSerializer.UTF8;
    }

    /**
     * Creates an {@link HttpSerializer} that can serialize key-values {@link Map}s with the specified {@link Charset}
     * to to urlencoded forms.
     * @deprecated Use {@link HttpSerializers#formUrlEncodedSerializer(Charset)}.
     * @param charset {@link Charset} for the key-value {@link Map} that will be serialized.
     * @return {@link HttpSerializer} that could serialize from key-value {@link Map}.
     * @see <a
     * href="https://url.spec.whatwg.org/#application/x-www-form-urlencoded">x-www-form-urlencoded specification</a>
     */
    @Deprecated
    public static HttpSerializer<Map<String, List<String>>> formUrlEncodedSerializer(Charset charset) {
        final CharSequence contentType = newAsciiString(APPLICATION_X_WWW_FORM_URLENCODED + "; charset=" +
                charset.name());
        return formUrlEncodedSerializer(charset, headers -> headers.set(CONTENT_TYPE, contentType));
    }

    /**
     * Creates an {@link HttpSerializer} that can serialize a key-values {@link Map}s with the specified {@link Charset}
     * to urlencoded forms.
     * @deprecated Use {@link HttpSerializers#formUrlEncodedSerializer(Charset)}.
     * @param charset {@link Charset} for the key-value {@link Map} that will be serialized.
     * @param addContentType A {@link Consumer} that adds relevant headers to the passed {@link HttpHeaders} matching
     * the serialized payload. Typically, this involves adding a {@link HttpHeaderNames#CONTENT_TYPE} header.
     * @return {@link HttpSerializer} that could serialize from key-value {@link Map}.
     * @see <a
     * href="https://url.spec.whatwg.org/#application/x-www-form-urlencoded">x-www-form-urlencoded specification</a>
     */
    @Deprecated
    public static HttpSerializer<Map<String, List<String>>> formUrlEncodedSerializer(
            Charset charset, Consumer<HttpHeaders> addContentType) {
        return new FormUrlEncodedHttpSerializer(charset, addContentType);
    }

    /**
     * Creates an {@link HttpDeserializer} that can deserialize key-values {@link Map}s
     * with {@link StandardCharsets#UTF_8} from urlencoded forms.
     * @deprecated Use {@link HttpSerializers#formUrlEncodedSerializer()}.
     * @return {@link HttpDeserializer} that could deserialize a key-values {@link Map}.
     * @see <a
     * href="https://url.spec.whatwg.org/#application/x-www-form-urlencoded">x-www-form-urlencoded specification</a>
     */
    @Deprecated
    public static HttpDeserializer<Map<String, List<String>>> formUrlEncodedDeserializer() {
        return FormUrlEncodedHttpDeserializer.UTF8;
    }

    /**
     * Creates an {@link HttpDeserializer} that can deserialize key-values {@link Map}s
     * with {@link StandardCharsets#UTF_8} from urlencoded forms.
     * @deprecated Use {@link HttpSerializers#formUrlEncodedSerializer(Charset)}.
     * @param charset {@link Charset} for the key-value {@link Map} that will be deserialized.
     * deserialized payload. If the validation fails, then deserialization will fail with {@link SerializationException}
     * @return {@link HttpDeserializer} that could deserialize a key-value {@link Map}.
     * @see <a
     * href="https://url.spec.whatwg.org/#application/x-www-form-urlencoded">x-www-form-urlencoded specification</a>
     */
    @Deprecated
    public static HttpDeserializer<Map<String, List<String>>> formUrlEncodedDeserializer(Charset charset) {
        return formUrlEncodedDeserializer(charset,
                headers -> hasContentType(headers, APPLICATION_X_WWW_FORM_URLENCODED, charset));
    }

    /**
     * Creates an {@link HttpDeserializer} that can deserialize key-values {@link Map}s
     * with {@link StandardCharsets#UTF_8} from urlencoded forms.
     * @deprecated Use {@link HttpSerializers#formUrlEncodedSerializer(Charset)}.
     * @param charset {@link Charset} for the key-value {@link Map} that will be deserialized.
     * @param checkContentType Checks the {@link HttpHeaders} to see if a compatible encoding is found.
     * deserialized payload. If the validation fails, then deserialization will fail with {@link SerializationException}
     * @return {@link HttpDeserializer} that could deserialize a key-value {@link Map}.
     * @see <a href="https://url.spec.whatwg.org/#application/x-www-form-urlencoded">x-www-form-urlencoded
    specification</a>
     */
    @Deprecated
    public static HttpDeserializer<Map<String, List<String>>> formUrlEncodedDeserializer(
            Charset charset, Predicate<HttpHeaders> checkContentType) {
        return new FormUrlEncodedHttpDeserializer(charset, checkContentType);
    }

    /**
     * Creates an {@link HttpSerializer} that can serialize {@link String}s with {@link StandardCharsets#UTF_8}
     * {@code Charset}.
     * @deprecated Use {@link HttpSerializers#textSerializerUtf8()} for aggregated. For streaming, use one of the
     * following:
     * <ul>
     *     <li>{@link HttpSerializers#appSerializerUtf8FixLen()}</li>
     *     <li>{@link HttpSerializers#appSerializerAsciiVarLen()}</li>
     *     <li>{@link HttpSerializers#stringStreamingSerializer(Charset, Consumer)}</li>
     * </ul>
     * @return {@link HttpSerializer} that could serialize {@link String}.
     */
    @Deprecated
    public static HttpSerializer<String> textSerializer() {
        return UTF8_STRING_SERIALIZER;
    }

    /**
     * Creates an {@link HttpSerializer} that can serialize {@link String}s with the specified {@link Charset}.
     * @deprecated Use {@link HttpSerializers#textSerializer(Charset)} for aggregated. For streaming, use one of the
     * following:
     * <ul>
     *     <li>{@link HttpSerializers#appSerializerUtf8FixLen()}</li>
     *     <li>{@link HttpSerializers#appSerializerAsciiVarLen()}</li>
     *     <li>{@link HttpSerializers#stringStreamingSerializer(Charset, Consumer)}</li>
     * </ul>
     * @param charset {@link Charset} for the {@link String} that will be serialized.
     * @return {@link HttpSerializer} that could serialize from {@link String}.
     */
    @Deprecated
    public static HttpSerializer<String> textSerializer(Charset charset) {
        return textSerializer(charset, headers -> hasContentType(headers, TEXT_PLAIN, charset));
    }

    /**
     * Creates an {@link HttpSerializer} that can serialize {@link String}s with the specified {@link Charset}.
     * @deprecated Use {@link HttpSerializers#textSerializer(Charset)} for aggregated. For streaming, use one of the
     * following:
     * <ul>
     *     <li>{@link HttpSerializers#appSerializerUtf8FixLen()}</li>
     *     <li>{@link HttpSerializers#appSerializerAsciiVarLen()}</li>
     *     <li>{@link HttpSerializers#stringStreamingSerializer(Charset, Consumer)}</li>
     * </ul>
     * @param charset {@link Charset} for the {@link String} that will be serialized.
     * @param addContentType A {@link Consumer} that adds relevant headers to the passed {@link HttpHeaders} matching
     * the serialized payload. Typically, this involves adding a {@link HttpHeaderNames#CONTENT_TYPE} header.
     * @return {@link HttpSerializer} that could serialize from {@link String}.
     */
    @Deprecated
    public static HttpSerializer<String> textSerializer(Charset charset, Consumer<HttpHeaders> addContentType) {
        return new HttpStringSerializer(charset, addContentType);
    }

    /**
     * Creates an {@link HttpDeserializer} that can deserialize {@link String}s with {@link StandardCharsets#UTF_8}
     * {@code Charset}.
     * @deprecated Use {@link HttpSerializers#textSerializerUtf8()} for aggregated. For streaming, use one of the
     * following:
     * <ul>
     *     <li>{@link HttpSerializers#appSerializerUtf8FixLen()}</li>
     *     <li>{@link HttpSerializers#appSerializerAsciiVarLen()}</li>
     *     <li>Aggregate the payload (e.g. {@link StreamingHttpRequest#toRequest()}) and use
     *     {@link HttpSerializers#textSerializer(Charset)} if your payload is text</li>
     *     <li>{@link HttpSerializers#streamingSerializer(StreamingSerializerDeserializer, Consumer, Predicate)}
     *     targeted at your {@link HttpHeaderNames#CONTENT_TYPE}</li>
     * </ul>
     * @return {@link HttpDeserializer} that could deserialize {@link String}.
     */
    @Deprecated
    public static HttpDeserializer<String> textDeserializer() {
        return UTF_8_STRING_DESERIALIZER;
    }

    /**
     * Creates an {@link HttpDeserializer} that can deserialize {@link String}s with the specified {@link Charset}.
     * @deprecated Use {@link HttpSerializers#textSerializer(Charset)} for aggregated. For streaming, use one of the
     * following:
     * <ul>
     *     <li>{@link HttpSerializers#appSerializerUtf8FixLen()}</li>
     *     <li>{@link HttpSerializers#appSerializerAsciiVarLen()}</li>
     *     <li>Aggregate the payload (e.g. {@link StreamingHttpRequest#toRequest()}) and use
     *     {@link HttpSerializers#textSerializer(Charset)} if your payload is text</li>
     *     <li>{@link HttpSerializers#streamingSerializer(StreamingSerializerDeserializer, Consumer, Predicate)}
     *     targeted at your {@link HttpHeaderNames#CONTENT_TYPE}</li>
     * </ul>
     * @param charset {@link Charset} for the {@link String} that will be deserialized.
     * @return {@link HttpDeserializer} that could deserialize {@link String}.
     */
    @Deprecated
    public static HttpDeserializer<String> textDeserializer(Charset charset) {
        return textDeserializer(charset, headers -> hasContentType(headers, TEXT_PLAIN, charset));
    }

    /**
     * Creates an {@link HttpDeserializer} that can deserialize {@link String}s with the specified {@link Charset}.
     * @deprecated Use {@link HttpSerializers#textSerializer(Charset)} for aggregated. For streaming, use one of the
     * following:
     * <ul>
     *     <li>{@link HttpSerializers#appSerializerUtf8FixLen()}</li>
     *     <li>{@link HttpSerializers#appSerializerAsciiVarLen()}</li>
     *     <li>Aggregate the payload (e.g. {@link StreamingHttpRequest#toRequest()}) and use
     *     {@link HttpSerializers#textSerializer(Charset)} if your payload is text</li>
     *     <li>{@link HttpSerializers#streamingSerializer(StreamingSerializerDeserializer, Consumer, Predicate)}
     *     targeted at your {@link HttpHeaderNames#CONTENT_TYPE}</li>
     * </ul>
     * @param charset {@link Charset} for the {@link String} that will be deserialized.
     * @param checkContentType A {@link Predicate} that validates the passed {@link HttpHeaders} as expected for the
     * deserialized payload. If the validation fails, then deserialization will fail with {@link SerializationException}
     * @return {@link HttpDeserializer} that could deserialize {@link String}.
     */
    @Deprecated
    public static HttpDeserializer<String> textDeserializer(Charset charset, Predicate<HttpHeaders> checkContentType) {
        return new HttpStringDeserializer(charset, checkContentType);
    }

    /**
     * Creates a new {@link HttpSerializationProvider} that could serialize/deserialize to/from JSON using the passed
     * {@link Serializer}. For serialization, the returned {@link HttpSerializationProvider} adds a
     * {@link HttpHeaderNames#CONTENT_TYPE} header with value {@link HttpHeaderValues#APPLICATION_JSON}.
     * For deserialization, it expects a {@link HttpHeaderNames#CONTENT_TYPE} header with value
     * {@link HttpHeaderValues#APPLICATION_JSON}. If the expected header is not present, then deserialization will fail
     * with {@link SerializationException}.
     * @deprecated Use {@link HttpSerializers#jsonSerializer(SerializerDeserializer)} or
     * {@link HttpSerializers#jsonStreamingSerializer(StreamingSerializerDeserializer)}.
     * @param serializer {@link Serializer} that has the capability of serializing/deserializing to/from JSON.
     * @return {@link HttpSerializationProvider} that has the capability of serializing/deserializing to/from JSON.
     */
    @Deprecated
    public static HttpSerializationProvider jsonSerializer(Serializer serializer) {
        return serializationProvider(serializer, headers -> headers.set(CONTENT_TYPE, APPLICATION_JSON),
                headers -> hasContentType(headers, APPLICATION_JSON, null));
    }

    /**
     * Creates a new {@link HttpSerializationProvider} that could serialize/deserialize to/from JSON using the passed
     * {@link SerializationProvider}. For serialization, the returned {@link HttpSerializationProvider} adds a
     * {@link HttpHeaderNames#CONTENT_TYPE} header with value {@link HttpHeaderValues#APPLICATION_JSON}.
     * For deserialization, it expects a {@link HttpHeaderNames#CONTENT_TYPE} header with value
     * {@link HttpHeaderValues#APPLICATION_JSON}. If the expected header is not present, then deserialization will fail
     * with {@link SerializationException}.
     * @deprecated Use {@link HttpSerializers#jsonSerializer(SerializerDeserializer)} or
     * {@link HttpSerializers#jsonStreamingSerializer(StreamingSerializerDeserializer)}.
     * @param serializationProvider {@link SerializationProvider} that has the capability of serializing/deserializing
     * to/from JSON.
     * @return {@link HttpSerializationProvider} that has the capability of serializing/deserializing to/from JSON.
     */
    @Deprecated
    public static HttpSerializationProvider jsonSerializer(SerializationProvider serializationProvider) {
        return jsonSerializer(new DefaultSerializer(serializationProvider));
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
     * @deprecated Use {@link HttpSerializers}, {@link HttpSerializer2}, {@link HttpDeserializer2},
     * {@link HttpStreamingSerializer}, and {@link HttpStreamingDeserializer}.
     * @param serializer {@link Serializer} that has the capability of serializing/deserializing to/from a desired
     * content-type.
     * @param addContentType A {@link Consumer} that adds relevant headers to the passed {@link HttpHeaders} matching
     * the serialized payload. Typically, this involves adding a {@link HttpHeaderNames#CONTENT_TYPE} header.
     * @param checkContentType A {@link Predicate} that validates the passed {@link HttpHeaders} as expected for the
     * deserialized payload. If the validation fails, then deserialization will fail with {@link SerializationException}
     * @return {@link HttpSerializationProvider} that has the capability of serializing/deserializing to/from a desired
     * content-type.
     */
    @Deprecated
    public static HttpSerializationProvider serializationProvider(Serializer serializer,
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
     * @deprecated Use {@link HttpSerializers}, {@link HttpSerializer2}, {@link HttpDeserializer2},
     * {@link HttpStreamingSerializer}, and {@link HttpStreamingDeserializer}.
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
    @Deprecated
    public static HttpSerializationProvider serializationProvider(SerializationProvider serializationProvider,
                                                                  Consumer<HttpHeaders> addContentType,
                                                                  Predicate<HttpHeaders> checkContentType) {
        return serializationProvider(new DefaultSerializer(serializationProvider), addContentType, checkContentType);
    }
}
