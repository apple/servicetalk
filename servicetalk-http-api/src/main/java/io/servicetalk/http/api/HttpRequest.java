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
import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.encoding.api.ContentCodec;

import java.nio.charset.Charset;
import javax.annotation.Nullable;

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
     * Returns an {@link HttpRequest} with its underlying payload set to {@code payloadBody}.
     *
     * @param payloadBody the underlying payload.
     * @return {@code this}
     */
    HttpRequest payloadBody(Buffer payloadBody);

    /**
     * Returns an {@link HttpRequest} with its underlying payload set to the results of serialization of {@code pojo}.
     * @deprecated Use {@link #payloadBody(Object, HttpSerializer2)}.
     * @param pojo The object to serialize.
     * @param serializer The {@link HttpSerializer} which converts {@code pojo} into bytes.
     * @param <T> The type of object to serialize.
     * @return {@code this}
     */
    @Deprecated
    <T> HttpRequest payloadBody(T pojo, HttpSerializer<T> serializer);

    /**
     * Returns an {@link HttpRequest} with its underlying payload set to the results of serialization of {@code pojo}.
     *
     * @param pojo The object to serialize.
     * @param serializer The {@link HttpSerializer2} which converts {@code pojo} into bytes.
     * @param <T> The type of object to serialize.
     * @return {@code this}
     */
    <T> HttpRequest payloadBody(T pojo, HttpSerializer2<T> serializer);

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
    HttpRequest appendPathSegments(String... segments);

    @Override
    HttpRequest rawQuery(@Nullable String query);

    @Override
    HttpRequest query(@Nullable String query);

    @Override
    HttpRequest addQueryParameter(String key, String value);

    @Override
    HttpRequest addQueryParameters(String key, Iterable<String> values);

    @Override
    HttpRequest addQueryParameters(String key, String... values);

    @Override
    HttpRequest setQueryParameter(String key, String value);

    @Override
    HttpRequest setQueryParameters(String key, Iterable<String> values);

    @Override
    HttpRequest setQueryParameters(String key, String... values);

    @Override
    HttpRequest version(HttpProtocolVersion version);

    @Deprecated
    @Override
    HttpRequest encoding(ContentCodec encoding);

    @Override
    HttpRequest contentEncoding(@Nullable BufferEncoder encoder);

    @Override
    HttpRequest method(HttpRequestMethod method);

    @Override
    HttpRequest requestTarget(String requestTarget);

    @Override
    HttpRequest requestTarget(String requestTarget, Charset encoding);

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
    default HttpRequest addCookie(final HttpCookiePair cookie) {
        HttpRequestMetaData.super.addCookie(cookie);
        return this;
    }

    @Override
    default HttpRequest addCookie(final CharSequence name, final CharSequence value) {
        HttpRequestMetaData.super.addCookie(name, value);
        return this;
    }

    @Override
    default HttpRequest addSetCookie(final HttpSetCookie cookie) {
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
    default HttpRequest addTrailers(final HttpHeaders trailers) {
        TrailersHolder.super.addTrailers(trailers);
        return this;
    }

    @Override
    default HttpRequest setTrailer(final CharSequence name, final CharSequence value) {
        TrailersHolder.super.setTrailer(name, value);
        return this;
    }

    @Override
    default HttpRequest setTrailers(final HttpHeaders trailers) {
        TrailersHolder.super.setTrailers(trailers);
        return this;
    }
}
