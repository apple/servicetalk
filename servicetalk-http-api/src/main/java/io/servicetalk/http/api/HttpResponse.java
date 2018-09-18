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
     * Get the underlying payload as a {@link Buffer}.
     * @return The {@link Buffer} representation of the underlying payload.
     */
    Buffer getPayloadBody();

    /**
     * Get and deserialize the payload body.
     * @param deserializer The function that deserializes the underlying {@link Object}.
     * @param <T> The resulting type of the deserialization operation.
     * @return The results of the deserialization operation.
     */
    default <T> T getPayloadBody(HttpDeserializer<T> deserializer) {
        return deserializer.deserialize(getHeaders(), getPayloadBody());
    }

    /**
     * Get the <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a>.
     * @return the <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a>.
     */
    HttpHeaders getTrailers();

    /**
     * Set the underlying payload.
     * @param payloadBody the underlying payload.
     * @return {@code this}.
     */
    HttpResponse setPayloadBody(Buffer payloadBody);

    /**
     * Set the underlying payload to be the results of serialization of {@code pojo}.
     * @param pojo The object to serialize.
     * @param serializer The {@link HttpSerializer} which converts {@code pojo} into bytes.
     * @param <T> The of object to serialize.
     * @return {@code this}.
     */
    <T> HttpResponse setPayloadBody(T pojo, HttpSerializer<T> serializer);

    /**
     * Translate this {@link HttpResponse} to a {@link StreamingHttpResponse}.
     * @return a {@link StreamingHttpResponse} representation of this {@link HttpResponse}.
     */
    StreamingHttpResponse toStreamingResponse();

    /**
     * Translate this {@link HttpResponse} to a {@link BlockingStreamingHttpResponse}.
     * @return a {@link BlockingStreamingHttpResponse} representation of this {@link HttpResponse}.
     */
    BlockingStreamingHttpResponse toBlockingStreamingResponse();

    @Override
    HttpResponse setVersion(HttpProtocolVersion version);

    @Override
    HttpResponse setStatus(HttpResponseStatus status);
}
