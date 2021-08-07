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

import java.io.InputStream;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * The equivalent of {@link HttpResponse} but provides the payload as a {@link BlockingIterable}.
 */
public interface BlockingStreamingHttpResponse extends HttpResponseMetaData {
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
     * Get the {@link BlockingStreamingHttpMessageBody} for this response.
     * @return the {@link BlockingStreamingHttpMessageBody} for this response.
     */
    BlockingStreamingHttpMessageBody<Buffer> messageBody();

    /**
     * Get the {@link BlockingStreamingHttpMessageBody} for this response and deserialize to type {@link T}.
     * @param deserializer The function that deserializes the underlying {@link BlockingIterable}.
     * @param <T> The resulting type of the deserialization operation.
     * @return the {@link BlockingStreamingHttpMessageBody} for this response and deserialize to type {@link T}.
     */
    <T> BlockingStreamingHttpMessageBody<T> messageBody(HttpStreamingDeserializer<T> deserializer);

    /**
     * Returns a {@link BlockingStreamingHttpResponse} with its underlying payload set to {@code payloadBody}.
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
    BlockingStreamingHttpResponse payloadBody(Iterable<Buffer> payloadBody);

    /**
     * Returns a {@link BlockingStreamingHttpResponse} with its underlying payload set to {@code payloadBody}.
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
    BlockingStreamingHttpResponse payloadBody(CloseableIterable<Buffer> payloadBody);

    /**
     * Returns a {@link BlockingStreamingHttpResponse} with its underlying payload set to {@code payloadBody}.
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
    BlockingStreamingHttpResponse payloadBody(InputStream payloadBody);

    /**
     * Returns a {@link BlockingStreamingHttpResponse} with its underlying payload set to the result of serialization.
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
    <T> BlockingStreamingHttpResponse payloadBody(Iterable<T> payloadBody, HttpSerializer<T> serializer);

    /**
     * Returns a {@link BlockingStreamingHttpResponse} with its underlying payload set to the result of serialization.
     * <p>
     * A best effort will be made to apply back pressure to the existing payload body which is being replaced. If this
     * default policy is not sufficient you can use {@link #transformPayloadBody(Function, HttpStreamingSerializer)} for
     * more fine grain control.
     * <p>
     * This method reserves the right to delay completion/consumption of {@code payloadBody}. This may occur due to the
     * combination with the existing payload body that is being replaced.
     * @param payloadBody The new payload body, prior to serialization.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to serialize.
     * @return {@code this}
     */
    <T> BlockingStreamingHttpResponse payloadBody(Iterable<T> payloadBody, HttpStreamingSerializer<T> serializer);

    /**
     * Returns a {@link BlockingStreamingHttpResponse} with its underlying payload set to the result of serialization.
     * <p>
     * A best effort will be made to apply back pressure to the existing payload body which is being replaced. If this
     * default policy is not sufficient you can use {@link #transformPayloadBody(Function, HttpSerializer)} for more
     * fine grain control.
     * <p>
     * This method reserves the right to delay completion/consumption of {@code payloadBody}. This may occur due to the
     * combination with the existing payload body that is being replaced.
     * @deprecated Use {@link #payloadBody(CloseableIterable, HttpStreamingSerializer)}.
     * @param payloadBody The new payload body, prior to serialization.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to serialize.
     * @return {@code this}
     */
    @Deprecated
    <T> BlockingStreamingHttpResponse payloadBody(CloseableIterable<T> payloadBody, HttpSerializer<T> serializer);

    /**
     * Returns a {@link BlockingStreamingHttpResponse} with its underlying payload set to the result of serialization.
     * <p>
     * A best effort will be made to apply back pressure to the existing payload body which is being replaced. If this
     * default policy is not sufficient you can use {@link #transformPayloadBody(Function, HttpStreamingSerializer)} for
     * more fine grain control.
     * <p>
     * This method reserves the right to delay completion/consumption of {@code payloadBody}. This may occur due to the
     * combination with the existing payload body that is being replaced.
     * @param payloadBody The new payload body, prior to serialization.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to serialize.
     * @return {@code this}
     */
    <T> BlockingStreamingHttpResponse payloadBody(CloseableIterable<T> payloadBody,
                                                  HttpStreamingSerializer<T> serializer);

    /**
     * Returns a {@link BlockingStreamingHttpResponse} with its underlying payload transformed to the result of
     * serialization.
     * @param transformer A {@link Function} which take as a parameter the existing payload body
     * {@link BlockingIterable} and returns the new payload body {@link BlockingIterable} prior to serialization. It is
     * assumed the existing payload body {@link BlockingIterable} will be transformed/consumed or else no more responses
     * may be processed.
     * @deprecated Use {@link #transformPayloadBody(Function, HttpStreamingSerializer)}.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to serialize.
     * @return {@code this}
     */
    @Deprecated
    <T> BlockingStreamingHttpResponse transformPayloadBody(
            Function<BlockingIterable<Buffer>, BlockingIterable<T>> transformer, HttpSerializer<T> serializer);

    /**
     * Returns a {@link BlockingStreamingHttpResponse} with its underlying payload transformed to the result of
     * serialization.
     * @param transformer A {@link Function} which take as a parameter the existing payload body
     * {@link BlockingIterable} and returns the new payload body {@link BlockingIterable} prior to serialization. It is
     * assumed the existing payload body {@link BlockingIterable} will be transformed/consumed or else no more responses
     * may be processed.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to serialize.
     * @return {@code this}
     */
    <T> BlockingStreamingHttpResponse transformPayloadBody(
            Function<BlockingIterable<Buffer>, BlockingIterable<T>> transformer, HttpStreamingSerializer<T> serializer);

    /**
     * Returns a {@link BlockingStreamingHttpResponse} with its underlying payload transformed to the result of
     * serialization.
     * @param transformer A {@link Function} which take as a parameter the existing payload body
     * {@link BlockingIterable} and returns the new payload body {@link BlockingIterable} prior to serialization. It is
     * assumed the existing payload body {@link BlockingIterable} will be transformed/consumed or else no more requests
     * may be processed.
     * @deprecated Use {@link #transformPayloadBody(Function, HttpStreamingDeserializer, HttpStreamingSerializer)}.
     * @param deserializer Used to deserialize the existing payload body.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to deserialize.
     * @param <R> The type of objects to serialize.
     * @return {@code this}
     */
    @Deprecated
    default <T, R> BlockingStreamingHttpResponse transformPayloadBody(
            Function<BlockingIterable<T>, BlockingIterable<R>> transformer, HttpDeserializer<T> deserializer,
            HttpSerializer<R> serializer) {
        return transformPayloadBody(buffers -> transformer.apply(payloadBody(deserializer)), serializer);
    }

    /**
     * Returns a {@link BlockingStreamingHttpResponse} with its underlying payload transformed to the result of
     * serialization.
     * @param transformer A {@link Function} which take as a parameter the existing payload body
     * {@link BlockingIterable} and returns the new payload body {@link BlockingIterable} prior to serialization. It is
     * assumed the existing payload body {@link BlockingIterable} will be transformed/consumed or else no more requests
     * may be processed.
     * @param deserializer Used to deserialize the existing payload body.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to deserialize.
     * @param <R> The type of objects to serialize.
     * @return {@code this}
     */
    <T, R> BlockingStreamingHttpResponse transformPayloadBody(
            Function<BlockingIterable<T>, BlockingIterable<R>> transformer, HttpStreamingDeserializer<T> deserializer,
            HttpStreamingSerializer<R> serializer);

    /**
     * Returns a {@link BlockingStreamingHttpResponse} with its underlying payload transformed to {@link Buffer}s.
     * @param transformer A {@link Function} which take as a parameter the existing payload body
     * {@link BlockingIterable} and returns the new payload body {@link BlockingIterable}. It is assumed the existing
     * payload body {@link BlockingIterable} will be transformed/consumed or else no more responses may be processed.
     * @return {@code this}
     */
    BlockingStreamingHttpResponse transformPayloadBody(UnaryOperator<BlockingIterable<Buffer>> transformer);

    /**
     * Returns a {@link BlockingStreamingHttpResponse} with its underlying payload transformed to {@link Buffer}s,
     * with access to the <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailer</a>s.
     * @param trailersTransformer {@link TrailersTransformer} to use for this transform.
     * @param <T> The type of state used during the transformation.
     * @return {@code this}
     */
    <T> BlockingStreamingHttpResponse transform(TrailersTransformer<T, Buffer> trailersTransformer);

    /**
     * Translates this {@link BlockingStreamingHttpResponse} to a {@link HttpResponse}.
     * @return a {@link Single} that completes with a {@link HttpResponse} representation of this
     * {@link BlockingStreamingHttpResponse}.
     */
    Single<HttpResponse> toResponse();

    /**
     * Translates this {@link BlockingStreamingHttpResponse} to a {@link StreamingHttpResponse}.
     * @return a {@link StreamingHttpResponse} representation of this {@link BlockingStreamingHttpResponse}.
     */
    StreamingHttpResponse toStreamingResponse();

    @Override
    BlockingStreamingHttpResponse version(HttpProtocolVersion version);

    @Override
    BlockingStreamingHttpResponse status(HttpResponseStatus status);

    @Override
    default BlockingStreamingHttpResponse addHeader(final CharSequence name, final CharSequence value) {
        HttpResponseMetaData.super.addHeader(name, value);
        return this;
    }

    @Override
    default BlockingStreamingHttpResponse addHeaders(final HttpHeaders headers) {
        HttpResponseMetaData.super.addHeaders(headers);
        return this;
    }

    @Override
    default BlockingStreamingHttpResponse setHeader(final CharSequence name, final CharSequence value) {
        HttpResponseMetaData.super.setHeader(name, value);
        return this;
    }

    @Override
    default BlockingStreamingHttpResponse setHeaders(final HttpHeaders headers) {
        HttpResponseMetaData.super.setHeaders(headers);
        return this;
    }

    @Override
    default BlockingStreamingHttpResponse addCookie(final HttpCookiePair cookie) {
        HttpResponseMetaData.super.addCookie(cookie);
        return this;
    }

    @Override
    default BlockingStreamingHttpResponse addCookie(final CharSequence name, final CharSequence value) {
        HttpResponseMetaData.super.addCookie(name, value);
        return this;
    }

    @Override
    default BlockingStreamingHttpResponse addSetCookie(final HttpSetCookie cookie) {
        HttpResponseMetaData.super.addSetCookie(cookie);
        return this;
    }

    @Override
    default BlockingStreamingHttpResponse addSetCookie(final CharSequence name, final CharSequence value) {
        HttpResponseMetaData.super.addSetCookie(name, value);
        return this;
    }
}
