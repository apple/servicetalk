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
public interface HttpResponse extends HttpResponseMetaData, TrailersHolder {
    /**
     * Gets the underlying payload as a {@link Buffer}.
     *
     * @return The {@link Buffer} representation of the underlying payload.
     */
    Buffer payloadBody();

    /**
     * Gets and deserializes the payload body.
     * @deprecated Use {@link #payloadBody(HttpDeserializer2)}.
     * @param deserializer The function that deserializes the underlying {@link Object}.
     * @param <T> The resulting type of the deserialization operation.
     * @return The results of the deserialization operation.
     */
    @Deprecated
    default <T> T payloadBody(HttpDeserializer<T> deserializer) {
        return deserializer.deserialize(headers(), payloadBody());
    }

    /**
     * Gets and deserializes the payload body.
     *
     * @param deserializer The function that deserializes the underlying {@link Object}.
     * @param <T> The resulting type of the deserialization operation.
     * @return The results of the deserialization operation.
     */
    <T> T payloadBody(HttpDeserializer2<T> deserializer);

    /**
     * Returns an {@link HttpResponse} with its underlying payload set to {@code payloadBody}.
     *
     * @param payloadBody the underlying payload.
     * @return {@code this}
     */
    HttpResponse payloadBody(Buffer payloadBody);

    /**
     * Returns an {@link HttpResponse} with its underlying payload set to the results of serialization of {@code pojo}.
     * @deprecated Use {@link #payloadBody(Object, HttpSerializer2)}.
     * @param pojo The object to serialize.
     * @param serializer The {@link HttpSerializer} which converts {@code pojo} into bytes.
     * @param <T> The type of object to serialize.
     * @return {@code this}
     */
    @Deprecated
    <T> HttpResponse payloadBody(T pojo, HttpSerializer<T> serializer);

    /**
     * Returns an {@link HttpResponse} with its underlying payload set to the results of serialization of {@code pojo}.
     *
     * @param pojo The object to serialize.
     * @param serializer The {@link HttpSerializer} which converts {@code pojo} into bytes.
     * @param <T> The type of object to serialize.
     * @return {@code this}
     */
    <T> HttpResponse payloadBody(T pojo, HttpSerializer2<T> serializer);

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
    default HttpResponse addHeader(final CharSequence name, final CharSequence value) {
        HttpResponseMetaData.super.addHeader(name, value);
        return this;
    }

    @Override
    default HttpResponse addHeaders(final HttpHeaders headers) {
        HttpResponseMetaData.super.addHeaders(headers);
        return this;
    }

    @Override
    default HttpResponse setHeader(final CharSequence name, final CharSequence value) {
        HttpResponseMetaData.super.setHeader(name, value);
        return this;
    }

    @Override
    default HttpResponse setHeaders(final HttpHeaders headers) {
        HttpResponseMetaData.super.setHeaders(headers);
        return this;
    }

    @Override
    default HttpResponse addCookie(final HttpCookiePair cookie) {
        HttpResponseMetaData.super.addCookie(cookie);
        return this;
    }

    @Override
    default HttpResponse addCookie(final CharSequence name, final CharSequence value) {
        HttpResponseMetaData.super.addCookie(name, value);
        return this;
    }

    @Override
    default HttpResponse addSetCookie(final HttpSetCookie cookie) {
        HttpResponseMetaData.super.addSetCookie(cookie);
        return this;
    }

    @Override
    default HttpResponse addSetCookie(final CharSequence name, final CharSequence value) {
        HttpResponseMetaData.super.addSetCookie(name, value);
        return this;
    }

    @Override
    default HttpResponse addTrailer(final CharSequence name, final CharSequence value) {
        TrailersHolder.super.addTrailer(name, value);
        return this;
    }

    @Override
    default HttpResponse addTrailers(final HttpHeaders trailers) {
        TrailersHolder.super.addTrailers(trailers);
        return this;
    }

    @Override
    default HttpResponse setTrailer(final CharSequence name, final CharSequence value) {
        TrailersHolder.super.setTrailer(name, value);
        return this;
    }

    @Override
    default HttpResponse setTrailers(final HttpHeaders trailers) {
        TrailersHolder.super.setTrailers(trailers);
        return this;
    }
}
