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
public interface HttpRequest extends HttpRequestMetaData, TrailersHolder {
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
    default HttpRequest addHeader(final CharSequence name, final CharSequence value) {
        HttpRequestMetaData.super.addHeader(name, value);
        return this;
    }

    @Override
    default HttpRequest addHeaders(final HttpHeaders headers) {
        HttpRequestMetaData.super.addHeaders(headers);
        return this;
    }

    @Override
    default HttpRequest setHeader(final CharSequence name, final CharSequence value) {
        HttpRequestMetaData.super.setHeader(name, value);
        return this;
    }

    @Override
    default HttpRequest setHeaders(final HttpHeaders headers) {
        HttpRequestMetaData.super.setHeaders(headers);
        return this;
    }

    @Override
    default HttpRequest addCookie(final HttpCookie cookie) {
        HttpRequestMetaData.super.addCookie(cookie);
        return this;
    }

    @Override
    default HttpRequest addCookie(final CharSequence name, final CharSequence value) {
        HttpRequestMetaData.super.addCookie(name, value);
        return this;
    }

    @Override
    default HttpRequest addSetCookie(final HttpCookie cookie) {
        HttpRequestMetaData.super.addSetCookie(cookie);
        return this;
    }

    @Override
    default HttpRequest addSetCookie(final CharSequence name, final CharSequence value) {
        HttpRequestMetaData.super.addSetCookie(name, value);
        return this;
    }

    @Override
    default HttpRequest addTrailer(final CharSequence name, final CharSequence value) {
        TrailersHolder.super.addTrailer(name, value);
        return this;
    }

    @Override
    default HttpRequest addTrailer(final HttpHeaders trailers) {
        TrailersHolder.super.addTrailer(trailers);
        return this;
    }

    @Override
    default HttpRequest setTrailer(final CharSequence name, final CharSequence value) {
        TrailersHolder.super.setTrailer(name, value);
        return this;
    }

    @Override
    default HttpRequest setTrailer(final HttpHeaders trailers) {
        TrailersHolder.super.setTrailer(trailers);
        return this;
    }
}
