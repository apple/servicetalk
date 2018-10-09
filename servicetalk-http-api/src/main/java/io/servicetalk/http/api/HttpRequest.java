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
 * An HTTP request. The payload is represented as a single {@link Object}.
 */
public interface HttpRequest extends HttpRequestMetaData {
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
     * @return A {@link HttpRequest} with the new serialized payload body.
     */
    HttpRequest payloadBody(Buffer payloadBody);

    /**
     * Sets the underlying payload to be the results of serialization of {@code pojo}.
     *
     * @param pojo The object to serialize.
     * @param serializer The {@link HttpSerializer} which converts {@code pojo} into bytes.
     * @param <T> The type of object to serialize.
     * @return A {@link HttpRequest} with the new serialized payload body.
     */
    <T> HttpRequest payloadBody(T pojo, HttpSerializer<T> serializer);

    /**
     * Translates this {@link HttpRequest} to a {@link StreamingHttpRequest}.
     *
     * @return a {@link StreamingHttpRequest} representation of this {@link HttpRequest}.
     */
    StreamingHttpRequest toStreamingRequest();

    /**
     * Translates this {@link HttpRequest} to a {@link BlockingStreamingHttpRequest}.
     *
     * @return a {@link BlockingStreamingHttpRequest} representation of this {@link HttpRequest}.
     */
    BlockingStreamingHttpRequest toBlockingStreamingRequest();

    @Override
    HttpRequest rawPath(String path);

    @Override
    HttpRequest path(String path);

    @Override
    HttpRequest rawQuery(String query);

    @Override
    HttpRequest version(HttpProtocolVersion version);

    @Override
    HttpRequest method(HttpRequestMethod method);

    @Override
    HttpRequest requestTarget(String requestTarget);

    @Override
    HttpRequest addHeader(CharSequence name, CharSequence value);

    @Override
    HttpRequest addHeaders(HttpHeaders headers);

    @Override
    HttpRequest setHeader(CharSequence name, CharSequence value);

    @Override
    HttpRequest setHeaders(HttpHeaders headers);

    @Override
    HttpRequest addCookie(HttpCookie cookie);

    @Override
    HttpRequest addCookie(CharSequence name, CharSequence value);

    @Override
    HttpRequest addSetCookie(HttpCookie cookie);

    @Override
    HttpRequest addSetCookie(CharSequence name, CharSequence value);

    /**
     * Adds a new trailer with the specified {@code name} and {@code value}.
     *
     * @param name the name of the trailer.
     * @param value the value of the trailer.
     * @return {@code this}.
     */
    HttpRequest addTrailer(CharSequence name, CharSequence value);

    /**
     * Adds all trailer names and values of {@code trailer} object.
     *
     * @param trailers the trailers to add.
     * @return {@code this}.
     * @throws IllegalArgumentException if {@code trailers == trailers()}.
     */
    HttpRequest addTrailer(HttpHeaders trailers);

    /**
     * Sets a trailer with the specified {@code name} and {@code value}. Any existing trailers with the same name are
     * overwritten.
     *
     * @param name the name of the trailer.
     * @param value the value of the trailer.
     * @return {@code this}.
     */
    HttpRequest setTrailer(CharSequence name, CharSequence value);

    /**
     * Clears the current trailer entries and copies all trailer entries of the specified {@code trailers} object.
     *
     * @param trailers the trailers object which contains new values.
     * @return {@code this}.
     */
    HttpRequest setTrailer(HttpHeaders trailers);
}
