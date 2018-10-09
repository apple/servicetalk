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

import io.servicetalk.buffer.api.Buffer;

/**
 * An HTTP response. The payload is represented as a single {@link Object}.
 */
public interface HttpResponse extends HttpResponseMetaData {
    /**
     * Gets the underlying payload as a {@link Buffer}.
     *
     * @return The {@link Buffer} representation of the underlying payload.
     */
    Buffer payloadBody();

    /**
     * Gets and deserialize the payload body.
     *
     * @param deserializer The function that deserializes the underlying {@link Object}.
     * @param <T> The resulting type of the deserialization operation.
     * @return The results of the deserialization operation.
     */
    default <T> T payloadBody(HttpDeserializer<T> deserializer) {
        return deserializer.deserialize(headers(), payloadBody());
    }

    /**
     * Gets the <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a>.
     *
     * @return the <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a>.
     */
    HttpHeaders trailers();

    /**
     * Sets the underlying payload.
     *
     * @param payloadBody the underlying payload.
     * @return {@code this}.
     */
    HttpResponse payloadBody(Buffer payloadBody);

    /**
     * Sets the underlying payload to be the results of serialization of {@code pojo}.
     *
     * @param pojo The object to serialize.
     * @param serializer The {@link HttpSerializer} which converts {@code pojo} into bytes.
     * @param <T> The of object to serialize.
     * @return {@code this}.
     */
    <T> HttpResponse payloadBody(T pojo, HttpSerializer<T> serializer);

    /**
     * Translates this {@link HttpResponse} to a {@link StreamingHttpResponse}.
     *
     * @return a {@link StreamingHttpResponse} representation of this {@link HttpResponse}.
     */
    StreamingHttpResponse toStreamingResponse();

    /**
     * Translates this {@link HttpResponse} to a {@link BlockingStreamingHttpResponse}.
     *
     * @return a {@link BlockingStreamingHttpResponse} representation of this {@link HttpResponse}.
     */
    BlockingStreamingHttpResponse toBlockingStreamingResponse();

    @Override
    HttpResponse version(HttpProtocolVersion version);

    @Override
    HttpResponse status(HttpResponseStatus status);

    @Override
    HttpResponse addHeader(CharSequence name, CharSequence value);

    @Override
    HttpResponse addHeaders(HttpHeaders headers);

    @Override
    HttpResponse setHeader(CharSequence name, CharSequence value);

    @Override
    HttpResponse setHeaders(HttpHeaders headers);

    @Override
    HttpResponse addCookie(HttpCookie cookie);

    @Override
    HttpResponse addCookie(CharSequence name, CharSequence value);

    @Override
    HttpResponse addSetCookie(HttpCookie cookie);

    @Override
    HttpResponse addSetCookie(CharSequence name, CharSequence value);

    /**
     * Adds a new trailer with the specified {@code name} and {@code value}.
     *
     * @param name the name of the trailer.
     * @param value the value of the trailer.
     * @return {@code this}.
     */
    HttpResponse addTrailer(CharSequence name, CharSequence value);

    /**
     * Adds all trailer names and values of {@code trailer} object.
     *
     * @param trailers the trailers to add.
     * @return {@code this}.
     * @throws IllegalArgumentException if {@code trailers == trailers()}.
     */
    HttpResponse addTrailer(HttpHeaders trailers);

    /**
     * Sets a trailer with the specified {@code name} and {@code value}. Any existing trailers with the same name are
     * overwritten.
     *
     * @param name the name of the trailer.
     * @param value the value of the trailer.
     * @return {@code this}.
     */
    HttpResponse setTrailer(CharSequence name, CharSequence value);

    /**
     * Clears the current trailer entries and copies all trailer entries of the specified {@code trailers} object.
     *
     * @param trailers the trailers object which contains new values.
     * @return {@code this}.
     */
    HttpResponse setTrailer(HttpHeaders trailers);
}
