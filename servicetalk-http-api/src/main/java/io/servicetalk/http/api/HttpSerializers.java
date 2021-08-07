/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.serializer.api.Serializer;
import io.servicetalk.serializer.api.SerializerDeserializer;
import io.servicetalk.serializer.api.StreamingSerializerDeserializer;
import io.servicetalk.serializer.utils.FixedLengthStreamingSerializer;
import io.servicetalk.serializer.utils.VarIntLengthStreamingSerializer;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HeaderUtils.hasContentType;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED_UTF_8;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN_US_ASCII;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN_UTF_8;
import static io.servicetalk.serializer.utils.StringSerializer.stringSerializer;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Factory for creation of {@link HttpSerializerDeserializer} and {@link HttpStreamingSerializerDeserializer}.
 */
public final class HttpSerializers {
    private static final String APPLICATION_TEXT_FIXED_STR = "application/text-fix-int";
    private static final CharSequence APPLICATION_TEXT_FIXED = newAsciiString(APPLICATION_TEXT_FIXED_STR);
    private static final CharSequence APPLICATION_TEXT_FIXED_UTF_8 =
            newAsciiString(APPLICATION_TEXT_FIXED + "; charset=UTF-8");
    private static final CharSequence APPLICATION_TEXT_FIXED_US_ASCII =
            newAsciiString(APPLICATION_TEXT_FIXED + "; charset=US-ASCII");
    private static final String APPLICATION_TEXT_VARINT_STR = "application/text-var-int";
    private static final CharSequence APPLICATION_TEXT_VARINT = newAsciiString(APPLICATION_TEXT_VARINT_STR);
    private static final CharSequence APPLICATION_TEXT_VAR_INT_UTF_8 =
            newAsciiString(APPLICATION_TEXT_VARINT + "; charset=UTF-8");
    private static final CharSequence APPLICATION_TEXT_VAR_INT_US_ASCII =
            newAsciiString(APPLICATION_TEXT_VARINT + "; charset=US-ASCII");

    private static final HttpSerializerDeserializer<Map<String, List<String>>> FORM_ENCODED_UTF_8 =
            new DefaultHttpSerializerDeserializer<>(new FormUrlEncodedSerializer(UTF_8),
                    headers -> headers.set(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED_UTF_8),
                    headers -> hasContentType(headers, APPLICATION_X_WWW_FORM_URLENCODED, UTF_8));
    private static final HttpSerializerDeserializer<String> TEXT_UTF_8 =
            new DefaultHttpSerializerDeserializer<>(stringSerializer(UTF_8),
                    headers -> headers.set(CONTENT_TYPE, TEXT_PLAIN_UTF_8),
                    headers -> hasContentType(headers, TEXT_PLAIN, UTF_8));
    private static final HttpSerializerDeserializer<String> TEXT_ASCII =
            new DefaultHttpSerializerDeserializer<>(stringSerializer(US_ASCII),
            headers -> headers.set(CONTENT_TYPE, TEXT_PLAIN_US_ASCII),
            headers -> hasContentType(headers, TEXT_PLAIN, US_ASCII));
    private static final int MAX_BYTES_PER_CHAR_UTF8 = (int) UTF_8.newEncoder().maxBytesPerChar();
    private static final HttpStreamingSerializerDeserializer<String> APP_STREAMING_FIX_LEN_UTF_8 =
            streamingSerializer(new FixedLengthStreamingSerializer<>(stringSerializer(UTF_8),
                            str -> str.length() * MAX_BYTES_PER_CHAR_UTF8),
                    headers -> headers.set(CONTENT_TYPE, APPLICATION_TEXT_FIXED_UTF_8),
                    headers -> hasContentType(headers, APPLICATION_TEXT_FIXED, UTF_8));
    private static final HttpStreamingSerializerDeserializer<String> APP_STREAMING_FIX_LEN_ASCII =
            streamingSerializer(new FixedLengthStreamingSerializer<>(stringSerializer(US_ASCII), String::length),
                    headers -> headers.set(CONTENT_TYPE, APPLICATION_TEXT_FIXED_US_ASCII),
                    headers -> hasContentType(headers, APPLICATION_TEXT_FIXED, US_ASCII));
    private static final HttpStreamingSerializerDeserializer<String> APP_STREAMING_VAR_LEN_UTF_8 =
            streamingSerializer(new VarIntLengthStreamingSerializer<>(stringSerializer(UTF_8),
                            str -> str.length() * MAX_BYTES_PER_CHAR_UTF8),
                    headers -> headers.set(CONTENT_TYPE, APPLICATION_TEXT_VAR_INT_UTF_8),
                    headers -> hasContentType(headers, APPLICATION_TEXT_VARINT, UTF_8));
    private static final HttpStreamingSerializerDeserializer<String> APP_STREAMING_VAR_LEN_ASCII =
            streamingSerializer(new VarIntLengthStreamingSerializer<>(stringSerializer(US_ASCII), String::length),
                    headers -> headers.set(CONTENT_TYPE, APPLICATION_TEXT_VAR_INT_US_ASCII),
                    headers -> hasContentType(headers, APPLICATION_TEXT_VARINT, US_ASCII));

    private HttpSerializers() {
    }

    /**
     * Get a {@link HttpSerializerDeserializer} that can serialize a key-values {@link Map}s
     * with {@link StandardCharsets#UTF_8} {@link Charset} to urlencoded forms.
     *
     * @return {@link HttpSerializerDeserializer} that could serialize key-value {@link Map}.
     * @see <a
     * href="https://url.spec.whatwg.org/#application/x-www-form-urlencoded">x-www-form-urlencoded specification</a>
     */
    public static HttpSerializerDeserializer<Map<String, List<String>>> formUrlEncodedSerializer() {
        return FORM_ENCODED_UTF_8;
    }

    /**
     * Get a {@link HttpSerializerDeserializer} that can serialize a key-values {@link Map}s
     * with a {@link Charset} to urlencoded forms.
     *
     * @param charset The {@link Charset} to use for value encoding.
     * @return {@link HttpSerializerDeserializer} that could serialize key-value {@link Map}.
     * @see <a
     * href="https://url.spec.whatwg.org/#application/x-www-form-urlencoded">x-www-form-urlencoded specification</a>
     */
    public static HttpSerializerDeserializer<Map<String, List<String>>> formUrlEncodedSerializer(Charset charset) {
        if (UTF_8.equals(charset)) {
            return FORM_ENCODED_UTF_8;
        }
        final CharSequence contentType = newAsciiString(APPLICATION_X_WWW_FORM_URLENCODED + "; charset=" +
                charset.name());
        return new DefaultHttpSerializerDeserializer<>(new FormUrlEncodedSerializer(charset),
                headers -> headers.set(CONTENT_TYPE, contentType),
                headers -> hasContentType(headers, APPLICATION_X_WWW_FORM_URLENCODED, charset));
    }

    /**
     * Creates an {@link HttpSerializerDeserializer} that can serialize {@link String}s with
     * {@link StandardCharsets#UTF_8}.
     *
     * @return {@link HttpSerializerDeserializer} that can serialize {@link String}s.
     */
    public static HttpSerializerDeserializer<String> textSerializerUtf8() {
        return TEXT_UTF_8;
    }

    /**
     * Creates an {@link HttpSerializerDeserializer} that can serialize {@link String}s with
     * {@link StandardCharsets#US_ASCII}.
     *
     * @return {@link HttpSerializerDeserializer} that can serialize {@link String}s.
     */
    public static HttpSerializerDeserializer<String> textSerializerAscii() {
        return TEXT_ASCII;
    }

    /**
     * Creates an {@link HttpSerializerDeserializer} that can serialize {@link String}s with
     * a {@link Charset}.
     *
     * @param charset The {@link Charset} to use for encoding.
     * @return {@link HttpSerializerDeserializer} that can serialize {@link String}s.
     */
    public static HttpSerializerDeserializer<String> textSerializer(Charset charset) {
        if (UTF_8.equals(charset)) {
            return TEXT_UTF_8;
        } else if (US_ASCII.equals(charset)) {
            return TEXT_ASCII;
        }
        final CharSequence contentType = newAsciiString("text/plain; charset=" + charset.name());
        return new DefaultHttpSerializerDeserializer<>(stringSerializer(charset),
                headers -> headers.set(CONTENT_TYPE, contentType),
                headers -> hasContentType(headers, TEXT_PLAIN, charset));
    }

    /**
     * Creates a {@link HttpStreamingSerializerDeserializer} that serializes {@link String}s with
     * {@link StandardCharsets#UTF_8} encoding using fixed {@code int} length delimited framing. The framing is required
     * so the same {@link String} objects can be deserialized by the peer, otherwise the boundaries aren't known. If
     * the desire is to serialize raw data contained in the {@link String}, see
     * {@link #stringStreamingSerializer(Charset, Consumer)}. The {@link HttpHeaderNames#CONTENT_TYPE} value prefix is
     * {@value #APPLICATION_TEXT_FIXED_STR}.
     *
     * @return a {@link HttpStreamingSerializerDeserializer} that serializes {@link String}s with
     * {@link StandardCharsets#UTF_8} encoding using fixed {@code int} length delimited framing.
     * @see FixedLengthStreamingSerializer
     */
    public static HttpStreamingSerializerDeserializer<String> appSerializerUtf8FixLen() {
        return APP_STREAMING_FIX_LEN_UTF_8;
    }

    /**
     * Creates a {@link HttpStreamingSerializerDeserializer} that serializes {@link String}s with
     * {@link StandardCharsets#UTF_8} encoding using variable {@code int} length delimited framing. The framing is
     * required so the same {@link String} objects can be deserialized by the peer, otherwise the boundaries aren't
     * known. If the desire is to serialize raw data contained in the {@link String}, see
     * {@link #stringStreamingSerializer(Charset, Consumer)}.The {@link HttpHeaderNames#CONTENT_TYPE} value prefix is
     * {@value #APPLICATION_TEXT_VARINT_STR}.
     *
     * @return a {@link HttpStreamingSerializerDeserializer} that serializes {@link String}s with
     * {@link StandardCharsets#UTF_8} encoding using variable {@code int} length delimited framing.
     * @see VarIntLengthStreamingSerializer
     */
    public static HttpStreamingSerializerDeserializer<String> appSerializerUtf8VarLen() {
        return APP_STREAMING_VAR_LEN_UTF_8;
    }

    /**
     * Creates a {@link HttpStreamingSerializerDeserializer} that serializes {@link String}s with
     * {@link StandardCharsets#US_ASCII} encoding using fixed {@code int} length delimited framing. The framing is
     * required so the same {@link String} objects can be deserialized by the peer, otherwise the boundaries aren't
     * known. If the desire is to serialize raw data contained in the {@link String}, see
     * {@link #stringStreamingSerializer(Charset, Consumer)}. The {@link HttpHeaderNames#CONTENT_TYPE} value prefix is
     * {@value #APPLICATION_TEXT_FIXED_STR}.
     *
     * @return a {@link HttpStreamingSerializerDeserializer} that serializes {@link String}s with
     * {@link StandardCharsets#US_ASCII} encoding using fixed {@code int} length delimited framing.
     * @see FixedLengthStreamingSerializer
     */
    public static HttpStreamingSerializerDeserializer<String> appSerializerAsciiFixLen() {
        return APP_STREAMING_FIX_LEN_ASCII;
    }

    /**
     * Creates a {@link HttpStreamingSerializerDeserializer} that serializes {@link String}s with
     * {@link StandardCharsets#US_ASCII} encoding using variable {@code int} length delimited framing. The framing is
     * required so the same {@link String} objects can be deserialized by the peer, otherwise the boundaries aren't
     * known. If the desire is to serialize raw data contained in the {@link String}, see
     * {@link #stringStreamingSerializer(Charset, Consumer)}. The {@link HttpHeaderNames#CONTENT_TYPE} value prefix is
     * {@value #APPLICATION_TEXT_VARINT_STR}.
     *
     * @return a {@link HttpStreamingSerializerDeserializer} that serializes {@link String}s with
     * {@link StandardCharsets#US_ASCII} encoding using variable {@code int} length delimited framing.
     * @see VarIntLengthStreamingSerializer
     */
    public static HttpStreamingSerializerDeserializer<String> appSerializerAsciiVarLen() {
        return APP_STREAMING_VAR_LEN_ASCII;
    }

    /**
     * Creates a {@link HttpStreamingSerializerDeserializer} that serializes {@link String}s with
     * {@code charset} encoding using fixed {@code int} length delimited framing. The framing is required so the same
     * {@link String} objects can be deserialized by the peer, otherwise the boundaries aren't known. If the desire is
     * to serialize raw data contained in the {@link String}, see {@link #stringStreamingSerializer(Charset, Consumer)}.
     * The {@link HttpHeaderNames#CONTENT_TYPE} value prefix is {@value #APPLICATION_TEXT_FIXED_STR}.
     *
     * @param charset The character encoding to use for serialization.
     * @return a {@link HttpStreamingSerializerDeserializer} that serializes {@link String}s with
     * {@code charset} encoding using fixed {@code int} length delimited framing.
     * @see FixedLengthStreamingSerializer
     */
    public static HttpStreamingSerializerDeserializer<String> appSerializerFixLen(Charset charset) {
        if (UTF_8.equals(charset)) {
            return APP_STREAMING_FIX_LEN_UTF_8;
        } else if (US_ASCII.equals(charset)) {
            return APP_STREAMING_FIX_LEN_ASCII;
        }
        final int maxBytesPerChar = (int) charset.newEncoder().maxBytesPerChar();
        CharSequence contentType = newAsciiString(APPLICATION_TEXT_FIXED + "; charset=" + charset.name());
        return streamingSerializer(new FixedLengthStreamingSerializer<>(stringSerializer(charset),
                        str -> str.length() * maxBytesPerChar),
                headers -> headers.set(CONTENT_TYPE, contentType),
                headers -> hasContentType(headers, APPLICATION_TEXT_FIXED, charset));
    }

    /**
     * Creates a {@link HttpStreamingSerializerDeserializer} that serializes {@link String}s with
     * {@code charset} encoding using fixed {@code int} length delimited framing. The framing is required so the same
     * {@link String} objects can be deserialized by the peer, otherwise the boundaries aren't known. If the desire is
     * to serialize raw data contained in the {@link String}, see {@link #stringStreamingSerializer(Charset, Consumer)}.
     * The {@link HttpHeaderNames#CONTENT_TYPE} value prefix is {@value #APPLICATION_TEXT_VARINT_STR}.
     *
     * @param charset The character encoding to use for serialization.
     * @return a {@link HttpStreamingSerializerDeserializer} that serializes {@link String}s with
     * {@code charset} encoding using fixed {@code int} length delimited framing.
     * @see VarIntLengthStreamingSerializer
     */
    public static HttpStreamingSerializerDeserializer<String> appSerializerVarLen(Charset charset) {
        if (UTF_8.equals(charset)) {
            return APP_STREAMING_VAR_LEN_UTF_8;
        } else if (US_ASCII.equals(charset)) {
            return APP_STREAMING_VAR_LEN_ASCII;
        }
        final int maxBytesPerChar = (int) charset.newEncoder().maxBytesPerChar();
        CharSequence contentType = newAsciiString(APPLICATION_TEXT_VARINT + "; charset=" + charset.name());
        return streamingSerializer(new VarIntLengthStreamingSerializer<>(stringSerializer(charset),
                        str -> str.length() * maxBytesPerChar),
                headers -> headers.set(CONTENT_TYPE, contentType),
                headers -> hasContentType(headers, APPLICATION_TEXT_VARINT, charset));
    }

    /**
     * Create a {@link HttpStreamingSerializer} that serializes {@link String}. This method is useful if the payload
     * body is provided in {@link String} and the {@link HttpHeaderNames#CONTENT_TYPE} is known a-prior
     * (e.g. streaming raw json data from a stream of {@link String}s). Deserialization should be done using
     * the a-prior knowledge to use a compatible {@link HttpStreamingDeserializer}.
     * @param charset The character encoding to use for serialization.
     * @param headersSerializeConsumer Sets the headers to indicate the appropriate encoding and content type.
     * @return a {@link HttpStreamingSerializer} that uses a {@link Serializer} for serialization.
     */
    public static HttpStreamingSerializer<String> stringStreamingSerializer(
            Charset charset, Consumer<HttpHeaders> headersSerializeConsumer) {
        final int maxBytesPerChar = (int) charset.newEncoder().maxBytesPerChar();
        return streamingSerializer(stringSerializer(charset), str -> str.length() * maxBytesPerChar,
                headersSerializeConsumer);
    }

    /**
     * Create a {@link HttpStreamingSerializer} that serializes {@code byte[]}. This method is useful if the payload
     * body is provided in {@code byte[]} and the {@link HttpHeaderNames#CONTENT_TYPE} is known a-prior
     * (e.g. streaming raw json data from a stream of {@code byte[]}s). Deserialization should be done using
     * the a-prior knowledge to use a compatible {@link HttpStreamingDeserializer}.
     * @param headersSerializeConsumer Sets the headers to indicate the appropriate encoding and content type.
     * @return a {@link HttpStreamingSerializer} that uses a {@link Serializer} for serialization.
     */
    public static HttpStreamingSerializer<byte[]> bytesStreamingSerializer(
            Consumer<HttpHeaders> headersSerializeConsumer) {
        return new DefaultHttpStreamingSerializer<>(NonFramedBytesStreamingSerializer.INSTANCE,
                headersSerializeConsumer);
    }

    /**
     * Creates an {@link HttpSerializerDeserializer} that targets {@link HttpHeaderValues#APPLICATION_JSON}.
     *
     * @param serializer Used to serialize each {@link T}.
     * @param <T> Type of object to serialize.
     * @return {@link HttpSerializerDeserializer} that targets {@link HttpHeaderValues#APPLICATION_JSON}.
     */
    public static <T> HttpSerializerDeserializer<T> jsonSerializer(SerializerDeserializer<T> serializer) {
        return new DefaultHttpSerializerDeserializer<>(serializer,
                headers -> headers.set(CONTENT_TYPE, APPLICATION_JSON),
                headers -> hasContentType(headers, APPLICATION_JSON, null));
    }

    /**
     * Creates an {@link HttpStreamingSerializerDeserializer} that targets {@link HttpHeaderValues#APPLICATION_JSON}.
     *
     * @param serializer Used to serialize each {@link T}.
     * @param <T> Type of object to serialize.
     * @return {@link HttpStreamingSerializerDeserializer} that targets {@link HttpHeaderValues#APPLICATION_JSON}.
     */
    public static <T> HttpStreamingSerializerDeserializer<T> jsonStreamingSerializer(
            StreamingSerializerDeserializer<T> serializer) {
        return new DefaultHttpStreamingSerializerDeserializer<>(serializer,
                headers -> headers.set(CONTENT_TYPE, APPLICATION_JSON),
                headers -> hasContentType(headers, APPLICATION_JSON, null));
    }

    /**
     * Creates an {@link HttpSerializerDeserializer} that uses {@link SerializerDeserializer} for serialization.
     *
     * @param serializer Used to serialize each {@link T}.
     * @param headersSerializeConsumer Sets the headers to indicate the appropriate encoding and content type.
     * @param headersDeserializePredicate Validates the headers are of the supported encoding and content type.
     * @param <T> Type of object to serialize.
     * @return {@link HttpSerializerDeserializer} that uses a {@link SerializerDeserializer} for serialization.
     */
    public static <T> HttpSerializerDeserializer<T> serializer(
            SerializerDeserializer<T> serializer, Consumer<HttpHeaders> headersSerializeConsumer,
            Predicate<HttpHeaders> headersDeserializePredicate) {
        return new DefaultHttpSerializerDeserializer<>(serializer, headersSerializeConsumer,
                headersDeserializePredicate);
    }

    /**
     * Creates an {@link HttpStreamingSerializerDeserializer} that uses {@link StreamingSerializerDeserializer} for
     * serialization.
     *
     * @param serializer Used to serialize each {@link T}.
     * @param headersSerializeConsumer Sets the headers to indicate the appropriate encoding and content type.
     * @param headersDeserializePredicate Validates the headers are of the supported encoding and content type.
     * @param <T> Type of object to serialize.
     * @return {@link HttpStreamingSerializerDeserializer} that uses a {@link StreamingSerializerDeserializer} for
     * serialization.
     */
    public static <T> HttpStreamingSerializerDeserializer<T> streamingSerializer(
            StreamingSerializerDeserializer<T> serializer, Consumer<HttpHeaders> headersSerializeConsumer,
            Predicate<HttpHeaders> headersDeserializePredicate) {
        return new DefaultHttpStreamingSerializerDeserializer<>(serializer, headersSerializeConsumer,
                headersDeserializePredicate);
    }

    /**
     * Create a {@link HttpStreamingSerializer} that uses a {@link Serializer} for serialization. This method is useful
     * if the payload body is provided in non-{@link Buffer} type and the {@link HttpHeaderNames#CONTENT_TYPE} is known
     * a-prior (e.g. streaming raw json data from a stream of {@link String}s). Deserialization should be done using
     * the a-prior knowledge to use a compatible {@link HttpStreamingDeserializer}.
     * @param serializer Used to serialize each {@link T} chunk.
     * @param bytesEstimator Provides an estimate of how many bytes to allocate for each {@link Buffer} to serialize to.
     * @param headersSerializeConsumer Sets the headers to indicate the appropriate encoding and content type.
     * @param <T> Type of object to serialize.
     * @return a {@link HttpStreamingSerializer} that uses a {@link Serializer} for serialization.
     */
    public static <T> HttpStreamingSerializer<T> streamingSerializer(
            Serializer<T> serializer, ToIntFunction<T> bytesEstimator, Consumer<HttpHeaders> headersSerializeConsumer) {
        return new DefaultHttpStreamingSerializer<>(serializer, bytesEstimator, headersSerializeConsumer);
    }
}
