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
     * Get the underlying payload as a {@link Buffer}.
     * @return The {@link Buffer} representation of the underlying payload.
     */
    Buffer payloadBody();

    /**
     * Get and deserialize the payload body.
     * @param deserializer The function that deserializes the underlying {@link Object}.
     * @param <T> The resulting type of the deserialization operation.
     * @return The results of the deserialization operation.
     */
    default <T> T payloadBody(HttpDeserializer<T> deserializer) {
        return deserializer.deserialize(headers(), payloadBody());
    }

    /**
     * Get the <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a>.
     * @return the <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a>.
     */
    HttpHeaders trailers();

    /**
     * Set the underlying payload.
     * @param payloadBody the underlying payload.
     * @return A {@link HttpRequest} with the new serialized payload body.
     */
    HttpRequest payloadBody(Buffer payloadBody);

    /**
     * Set the underlying payload to be the results of serialization of {@code pojo}.
     * @param pojo The object to serialize.
     * @param serializer The {@link HttpSerializer} which converts {@code pojo} into bytes.
     * @param <T> The type of object to serialize.
     * @return A {@link HttpRequest} with the new serialized payload body.
     */
    <T> HttpRequest payloadBody(T pojo, HttpSerializer<T> serializer);

    /**
     * Translate this {@link HttpRequest} to a {@link StreamingHttpRequest}.
     * @return a {@link StreamingHttpRequest} representation of this {@link HttpRequest}.
     */
    StreamingHttpRequest toStreamingRequest();

    /**
     * Translate this {@link HttpRequest} to a {@link BlockingStreamingHttpRequest}.
     * @return a {@link BlockingStreamingHttpRequest} representation of this {@link HttpRequest}.
     */
    BlockingStreamingHttpRequest toBlockingStreamingRequest();

    @Override
    HttpRequest rawPath(String path);

    @Override
    HttpRequest path(String path);

    @Override
    HttpRequest addQueryParameter(String key, String value);

    @Override
    HttpRequest setQueryParameter(String key, String value);

    @Override
    HttpRequest rawQuery(String query);

    @Override
    HttpRequest version(HttpProtocolVersion version);

    @Override
    HttpRequest method(HttpRequestMethod method);

    @Override
    HttpRequest requestTarget(String requestTarget);

    @Override
    HttpRequest addHeaderField(CharSequence name, CharSequence value);

    @Override
    HttpRequest setHeaderField(CharSequence name, CharSequence value);
}
