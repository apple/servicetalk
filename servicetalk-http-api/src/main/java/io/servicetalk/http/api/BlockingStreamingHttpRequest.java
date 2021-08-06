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
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.CloseableIterable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.CloseableIteratorBufferAsInputStream;
import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.encoding.api.ContentCodec;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

/**
 * The equivalent of {@link HttpRequest} but provides the payload as a {@link BlockingIterable}.
 */
public interface BlockingStreamingHttpRequest extends HttpRequestMetaData {
    /**
     * Gets the underlying payload as a {@link Publisher} of {@link Buffer}s.
     * @return The {@link Publisher} of {@link Buffer} representation of the underlying payload body.
     */
    BlockingIterable<Buffer> payloadBody();

    /**
     * Gets the underlying payload as a {@link InputStream}.
     * @return The {@link InputStream} representation of the underlying payload body.
     */
    default InputStream payloadBodyInputStream() {
        return new CloseableIteratorBufferAsInputStream(payloadBody().iterator());
    }

    /**
     * Gets and deserializes the payload body.
     * @deprecated Use {@link #payloadBody(HttpStreamingDeserializer)}.
     * @param deserializer The function that deserializes the underlying {@link BlockingIterable}.
     * @param <T> The resulting type of the deserialization operation.
     * @return The results of the deserialization operation.
     */
    @Deprecated
    default <T> BlockingIterable<T> payloadBody(HttpDeserializer<T> deserializer) {
        return deserializer.deserialize(headers(), payloadBody());
    }

    /**
     * Gets and deserializes the payload body.
     * @param deserializer The function that deserializes the underlying {@link BlockingIterable}.
     * @param <T> The resulting type of the deserialization operation.
     * @return The results of the deserialization operation.
     */
    <T> BlockingIterable<T> payloadBody(HttpStreamingDeserializer<T> deserializer);

    /**
     * Get the {@link HttpMessageBodyIterable} for this request.
     * @return the {@link HttpMessageBodyIterable} for this request.
     */
    HttpMessageBodyIterable<Buffer> messageBody();

    /**
     * Get the {@link HttpMessageBodyIterable} for this request and deserialize to type {@link T}.
     * @param deserializer The function that deserializes the underlying {@link BlockingIterable}.
     * @param <T> The resulting type of the deserialization operation.
     * @return the {@link HttpMessageBodyIterable} for this request and deserialize to type {@link T}.
     */
    <T> HttpMessageBodyIterable<T> messageBody(HttpStreamingDeserializer<T> deserializer);

    /**
     * Returns a {@link BlockingStreamingHttpRequest} with its underlying payload set to {@code payloadBody}.
     * <p>
     * A best effort will be made to apply back pressure to the existing payload body which is being replaced. If this
     * default policy is not sufficient you can use {@link #transformPayloadBody(UnaryOperator)} for more fine grain
     * control.
     * <p>
     * This method reserves the right to delay completion/consumption of {@code payloadBody}. This may occur due to the
     * combination with the existing payload body that is being replaced.
     * @param payloadBody The new payload body.
     * @return {@code this}
     */
    BlockingStreamingHttpRequest payloadBody(Iterable<Buffer> payloadBody);

    /**
     * Returns a {@link BlockingStreamingHttpRequest} with its underlying payload set to {@code payloadBody}.
     * <p>
     * A best effort will be made to apply back pressure to the existing payload body which is being replaced. If this
     * default policy is not sufficient you can use {@link #transformPayloadBody(UnaryOperator)} for more fine grain
     * control.
     * <p>
     * This method reserves the right to delay completion/consumption of {@code payloadBody}. This may occur due to the
     * combination with the existing payload body that is being replaced.
     * @param payloadBody The new payload body.
     * @return {@code this}
     */
    BlockingStreamingHttpRequest payloadBody(CloseableIterable<Buffer> payloadBody);

    /**
     * Returns a {@link BlockingStreamingHttpRequest} with its underlying payload set to {@code payloadBody}.
     * <p>
     * A best effort will be made to apply back pressure to the existing payload body which is being replaced. If this
     * default policy is not sufficient you can use {@link #transformPayloadBody(UnaryOperator)} for more fine grain
     * control.
     * <p>
     * This method reserves the right to delay completion/consumption of {@code payloadBody}. This may occur due to the
     * combination with the existing payload body that is being replaced.
     * @param payloadBody The new payload body.
     * @return {@code this}
     */
    BlockingStreamingHttpRequest payloadBody(InputStream payloadBody);

    /**
     * Set the {@link HttpMessageBodyIterable} for this response.
     * @param messageBody The new message body.
     * @return {@code this}.
     */
    BlockingStreamingHttpRequest messageBody(HttpMessageBodyIterable<Buffer> messageBody);

    /**
     * Returns a {@link BlockingStreamingHttpRequest} with its underlying payload set to the result of serialization.
     * <p>
     * A best effort will be made to apply back pressure to the existing payload body which is being replaced. If this
     * default policy is not sufficient you can use {@link #transformPayloadBody(Function, HttpSerializer)} for more
     * fine grain control.
     * <p>
     * This method reserves the right to delay completion/consumption of {@code payloadBody}. This may occur due to the
     * combination with the existing payload body that is being replaced.
     * @deprecated Use {@link #payloadBody(Iterable, HttpStreamingSerializer)}.
     * @param payloadBody The new payload body, prior to serialization.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to serialize.
     * @return {@code this}
     */
    @Deprecated
    <T> BlockingStreamingHttpRequest payloadBody(Iterable<T> payloadBody, HttpSerializer<T> serializer);

    /**
     * Returns a {@link BlockingStreamingHttpRequest} with its underlying payload set to the result of serialization.
     * <p>
     * A best effort will be made to apply back pressure to the existing payload body which is being replaced. If this
     * default policy is not sufficient {@link #payloadBody()} can be used to drain with more fine grain control.
     * <p>
     * This method reserves the right to delay completion/consumption of {@code payloadBody}. This may occur due to the
     * combination with the existing payload body that is being replaced.
     * @param payloadBody The new payload body, prior to serialization.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to serialize.
     * @return {@code this}
     */
    <T> BlockingStreamingHttpRequest payloadBody(Iterable<T> payloadBody, HttpStreamingSerializer<T> serializer);

    /**
     * Returns a {@link BlockingStreamingHttpRequest} with its underlying payload set to the result of serialization.
     * <p>
     * A best effort will be made to apply back pressure to the existing payload body which is being replaced. If this
     * default policy is not sufficient {@link #payloadBody()} can be used to drain with more fine grain control.
     * <p>
     * This method reserves the right to delay completion/consumption of {@code payloadBody}. This may occur due to the
     * combination with the existing payload body that is being replaced.
     * @deprecated Use {@link #payloadBody(Iterable, HttpStreamingSerializer)}.
     * @param payloadBody The new payload body, prior to serialization.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to serialize.
     * @return {@code this}
     */
    @Deprecated
    <T> BlockingStreamingHttpRequest payloadBody(CloseableIterable<T> payloadBody, HttpSerializer<T> serializer);

    /**
     * Set the {@link HttpMessageBodyIterable} for this response.
     * @param messageBody The serialized message body.
     * @param serializer The function that serializes the underlying {@link BlockingIterable}.
     * @param <T> The type of the serialized objects.
     * @return {@code this}
     */
    <T> BlockingStreamingHttpRequest messageBody(HttpMessageBodyIterable<T> messageBody,
                                                 HttpStreamingSerializer<T> serializer);

    /**
     * Returns a {@link BlockingStreamingHttpRequest} with its underlying payload transformed to the result of
     * serialization.
     * @param transformer A {@link Function} which take as a parameter the existing payload body
     * {@link BlockingIterable} and returns the new payload body {@link BlockingIterable} prior to serialization. It is
     * assumed the existing payload body {@link BlockingIterable} will be transformed/consumed or else no more requests
     * may be processed.
     * @deprecated Use {@link #payloadBody(HttpStreamingDeserializer)} and
     * {@link #payloadBody(Iterable, HttpStreamingSerializer)}.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to serialize.
     * @return {@code this}
     */
    @Deprecated
    <T> BlockingStreamingHttpRequest transformPayloadBody(
            Function<BlockingIterable<Buffer>, BlockingIterable<T>> transformer, HttpSerializer<T> serializer);

    /**
     * Returns a {@link BlockingStreamingHttpRequest} with its underlying payload transformed to the result of
     * serialization.
     * @param transformer A {@link Function} which take as a parameter the existing payload body
     * {@link BlockingIterable} and returns the new payload body {@link BlockingIterable} prior to serialization. It is
     * assumed the existing payload body {@link BlockingIterable} will be transformed/consumed or else no more requests
     * may be processed.
     * @deprecated Use {@link #payloadBody(HttpStreamingDeserializer)} and
     * {@link #payloadBody(Iterable, HttpStreamingSerializer)}.
     * @param deserializer Used to deserialize the existing payload body.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to deserialize.
     * @param <R> The type of objects to serialize.
     * @return {@code this}
     */
    @Deprecated
    default <T, R> BlockingStreamingHttpRequest transformPayloadBody(
            Function<BlockingIterable<T>, BlockingIterable<R>> transformer, HttpDeserializer<T> deserializer,
            HttpSerializer<R> serializer) {
        return transformPayloadBody(buffers -> transformer.apply(payloadBody(deserializer)), serializer);
    }

    /**
     * Returns a {@link BlockingStreamingHttpRequest} with its underlying payload transformed to {@link Buffer}s.
     * @deprecated Use {@link #payloadBody()} and {@link #payloadBody(Iterable)}.
     * @param transformer A {@link Function} which take as a parameter the existing payload body
     * {@link BlockingIterable} and returns the new payload body {@link BlockingIterable}. It is assumed the existing
     * payload body {@link BlockingIterable} will be transformed/consumed or else no more requests may be processed.
     * @return {@code this}
     */
    @Deprecated
    BlockingStreamingHttpRequest transformPayloadBody(UnaryOperator<BlockingIterable<Buffer>> transformer);

    /**
     * Returns a {@link BlockingStreamingHttpRequest} with its underlying payload transformed to {@link Buffer}s,
     * with access to the <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailer</a>s.
     * @deprecated Use {@link #messageBody()} and {@link #messageBody(HttpMessageBodyIterable)}.
     * @param trailersTransformer {@link TrailersTransformer} to use for this transform.
     * @param <T> The type of state used during the transformation.
     * @return {@code this}
     */
    @Deprecated
    <T> BlockingStreamingHttpRequest transform(TrailersTransformer<T, Buffer> trailersTransformer);

    /**
     * Translates this {@link BlockingStreamingHttpRequest} to a {@link HttpRequest}.
     * @return a {@link Single} that completes with a {@link HttpRequest} representation of this
     * {@link BlockingStreamingHttpRequest}.
     */
    Single<HttpRequest> toRequest();

    /**
     * Translates this {@link BlockingStreamingHttpRequest} to a {@link StreamingHttpRequest}.
     * @return a {@link StreamingHttpRequest} representation of this {@link BlockingStreamingHttpRequest}.
     */
    StreamingHttpRequest toStreamingRequest();

    @Override
    BlockingStreamingHttpRequest rawPath(String path);

    @Override
    BlockingStreamingHttpRequest path(String path);

    @Override
    BlockingStreamingHttpRequest appendPathSegments(String... segments);

    @Override
    BlockingStreamingHttpRequest rawQuery(@Nullable String query);

    @Override
    BlockingStreamingHttpRequest query(@Nullable String query);

    @Override
    BlockingStreamingHttpRequest addQueryParameter(String key, String value);

    @Override
    BlockingStreamingHttpRequest addQueryParameters(String key, Iterable<String> values);

    @Override
    BlockingStreamingHttpRequest addQueryParameters(String key, String... values);

    @Override
    BlockingStreamingHttpRequest setQueryParameter(String key, String value);

    @Override
    BlockingStreamingHttpRequest setQueryParameters(String key, Iterable<String> values);

    @Override
    BlockingStreamingHttpRequest setQueryParameters(String key, String... values);

    @Override
    BlockingStreamingHttpRequest version(HttpProtocolVersion version);

    @Deprecated
    @Override
    BlockingStreamingHttpRequest encoding(ContentCodec encoding);

    @Override
    BlockingStreamingHttpRequest contentEncoding(@Nullable BufferEncoder encoder);

    @Override
    BlockingStreamingHttpRequest method(HttpRequestMethod method);

    @Override
    BlockingStreamingHttpRequest requestTarget(String requestTarget);

    @Override
    BlockingStreamingHttpRequest requestTarget(String requestTarget, Charset encoding);

    @Override
    default BlockingStreamingHttpRequest addHeader(final CharSequence name, final CharSequence value) {
        HttpRequestMetaData.super.addHeader(name, value);
        return this;
    }

    @Override
    default BlockingStreamingHttpRequest addHeaders(final HttpHeaders headers) {
        HttpRequestMetaData.super.addHeaders(headers);
        return this;
    }

    @Override
    default BlockingStreamingHttpRequest setHeader(final CharSequence name, final CharSequence value) {
        HttpRequestMetaData.super.setHeader(name, value);
        return this;
    }

    @Override
    default BlockingStreamingHttpRequest setHeaders(final HttpHeaders headers) {
        HttpRequestMetaData.super.setHeaders(headers);
        return this;
    }

    @Override
    default BlockingStreamingHttpRequest addCookie(final HttpCookiePair cookie) {
        HttpRequestMetaData.super.addCookie(cookie);
        return this;
    }

    @Override
    default BlockingStreamingHttpRequest addCookie(final CharSequence name, final CharSequence value) {
        HttpRequestMetaData.super.addCookie(name, value);
        return this;
    }

    @Override
    default BlockingStreamingHttpRequest addSetCookie(final HttpSetCookie cookie) {
        HttpRequestMetaData.super.addSetCookie(cookie);
        return this;
    }

    @Override
    default BlockingStreamingHttpRequest addSetCookie(final CharSequence name, final CharSequence value) {
        HttpRequestMetaData.super.addSetCookie(name, value);
        return this;
    }
}
