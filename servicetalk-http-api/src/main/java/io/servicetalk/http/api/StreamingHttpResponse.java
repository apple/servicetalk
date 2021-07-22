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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.encoding.api.ContentCodec;

import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * The equivalent of {@link HttpResponse} but provides the payload as a {@link Publisher}.
 */
public interface StreamingHttpResponse extends HttpResponseMetaData {

    /**
     * Gets the underlying payload as a {@link Publisher} of {@link Buffer}s.
     * @return A {@link Publisher} of {@link Buffer}s representing the underlying payload body.
     */
    Publisher<Buffer> payloadBody();

    /**
     * Gets and deserializes the payload body.
     * @deprecated Use {@link #payloadBody(HttpStreamingDeserializer)}.
     * @param deserializer The function that deserializes the underlying {@link Publisher}.
     * @param <T> The resulting type of the deserialization operation.
     * @return The results of the deserialization operation.
     */
    @Deprecated
    default <T> Publisher<T> payloadBody(HttpDeserializer<T> deserializer) {
        return deserializer.deserialize(headers(), payloadBody());
    }

    /**
     * Gets and deserializes the payload body.
     *
     * @param deserializer The function that deserializes the underlying {@link Publisher}.
     * @param <T> The resulting type of the deserialization operation.
     * @return The results of the deserialization operation.
     */
    <T> Publisher<T> payloadBody(HttpStreamingDeserializer<T> deserializer);

    /**
     * Get the <a href="https://tools.ietf.org/html/rfc7230#section-3.3">message-body</a> which contains the
     * payload body concatenated with the <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailer</a> (if
     * present).
     * @return a {@link Publisher} that represents the
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.3">message-body</a> which contains the
     * payload body concatenated with the <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailer</a> (if
     * present).
     */
    Publisher<Object> messageBody();

    /**
     * Returns a {@link StreamingHttpResponse} with its underlying payload set to {@code payloadBody}.
     * <p>
     * A best effort will be made to apply back pressure to the existing {@link Publisher} payload body. If this default
     * policy is not sufficient you can use {@link #transformPayloadBody(UnaryOperator)} for more fine grain control.
     * <p>
     * This method reserves the right to delay completion/consumption of {@code payloadBody}. This may occur due to the
     * combination with the existing {@link Publisher} payload body.
     * @param payloadBody The new payload body.
     * @return {@code this}
     */
    StreamingHttpResponse payloadBody(Publisher<Buffer> payloadBody);

    /**
     * Returns a {@link StreamingHttpResponse} with its underlying payload set to the result of serialization.
     * <p>
     * A best effort will be made to apply back pressure to the existing {@link Publisher} payload body. If this default
     * policy is not sufficient you can use {@link #transformPayloadBody(Function, HttpSerializer)} for more fine grain
     * control.
     * <p>
     * This method reserves the right to delay completion/consumption of {@code payloadBody}. This may occur due to the
     * combination with the existing {@link Publisher} payload body.
     * @deprecated Use {@link #payloadBody(Publisher, HttpStreamingSerializer)}.
     * @param payloadBody The new payload body, prior to serialization.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to serialize.
     * @return {@code this}
     */
    @Deprecated
    <T> StreamingHttpResponse payloadBody(Publisher<T> payloadBody, HttpSerializer<T> serializer);

    /**
     * Returns a {@link StreamingHttpResponse} with its underlying payload set to the result of serialization.
     * <p>
     * A best effort will be made to apply back pressure to the existing {@link Publisher} payload body. If this default
     * policy is not sufficient you can use {@link #transformPayloadBody(Function, HttpStreamingSerializer)} for more
     * fine grain control.
     * <p>
     * This method reserves the right to delay completion/consumption of {@code payloadBody}. This may occur due to the
     * combination with the existing {@link Publisher} payload body.
     * @param payloadBody The new payload body, prior to serialization.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to serialize.
     * @return {@code this}
     */
    <T> StreamingHttpResponse payloadBody(Publisher<T> payloadBody, HttpStreamingSerializer<T> serializer);

    /**
     * Returns a {@link StreamingHttpResponse} with its underlying payload transformed to the result of serialization.
     * @deprecated Use {@link #transformPayloadBody(Function, HttpStreamingSerializer)}.
     * @param transformer A {@link Function} which take as a parameter the existing payload body {@link Publisher} and
     * returns the new payload body {@link Publisher} prior to serialization. It is assumed the existing payload body
     * {@link Publisher} will be transformed/consumed or else no more responses may be processed.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to serialize.
     * @return {@code this}
     */
    @Deprecated
    <T> StreamingHttpResponse transformPayloadBody(Function<Publisher<Buffer>, Publisher<T>> transformer,
                                                   HttpSerializer<T> serializer);

    /**
     * Returns a {@link StreamingHttpResponse} with its underlying payload transformed to the result of serialization.
     * @param transformer A {@link Function} which take as a parameter the existing payload body {@link Publisher} and
     * returns the new payload body {@link Publisher} prior to serialization. It is assumed the existing payload body
     * {@link Publisher} will be transformed/consumed or else no more responses may be processed.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to serialize.
     * @return {@code this}
     */
    <T> StreamingHttpResponse transformPayloadBody(Function<Publisher<Buffer>, Publisher<T>> transformer,
                                                   HttpStreamingSerializer<T> serializer);

    /**
     * Returns a {@link StreamingHttpResponse} with its underlying payload transformed to the result of serialization.
     * @deprecated Use {@link #transformPayloadBody(Function, HttpStreamingDeserializer, HttpStreamingSerializer)}.
     * @param transformer A {@link Function} which take as a parameter the existing payload body {@link Publisher} and
     * returns the new payload body {@link Publisher} prior to serialization. It is assumed the existing payload body
     * {@link Publisher} will be transformed/consumed or else no more requests may be processed.
     * @param deserializer Used to deserialize the existing payload body.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to deserialize.
     * @param <R> The type of objects to serialize.
     * @return {@code this}
     */
    @Deprecated
    default <T, R> StreamingHttpResponse transformPayloadBody(Function<Publisher<T>, Publisher<R>> transformer,
                                                              HttpDeserializer<T> deserializer,
                                                              HttpSerializer<R> serializer) {
        return transformPayloadBody(bufferPublisher ->
                transformer.apply(deserializer.deserialize(headers(), bufferPublisher)), serializer);
    }

    /**
     * Returns a {@link StreamingHttpResponse} with its underlying payload transformed to the result of serialization.
     * @param transformer A {@link Function} which take as a parameter the existing payload body {@link Publisher} and
     * returns the new payload body {@link Publisher} prior to serialization. It is assumed the existing payload body
     * {@link Publisher} will be transformed/consumed or else no more requests may be processed.
     * @param deserializer Used to deserialize the existing payload body.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to deserialize.
     * @param <R> The type of objects to serialize.
     * @return {@code this}
     */
    <T, R> StreamingHttpResponse transformPayloadBody(Function<Publisher<T>, Publisher<R>> transformer,
                                                      HttpStreamingDeserializer<T> deserializer,
                                                      HttpStreamingSerializer<R> serializer);

    /**
     * Returns a {@link StreamingHttpResponse} with its underlying payload transformed to {@link Buffer}s.
     * @param transformer A {@link Function} which take as a parameter the existing payload body {@link Publisher} and
     * returns the new payload body {@link Publisher}. It is assumed the existing payload body {@link Publisher} will be
     * transformed/consumed or else no more responses may be processed.
     * @return {@code this}
     */
    StreamingHttpResponse transformPayloadBody(UnaryOperator<Publisher<Buffer>> transformer);

    /**
     * Transform the <a href="https://tools.ietf.org/html/rfc7230#section-3.3">message-body</a> which contains the
     * payload body concatenated with the <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailer</a> (if
     * present).
     * <p>
     * The transformation is not expected to change the content of the message body {@link Publisher} or presence of
     * trailers in it. For example, behavior is undefined if a content is altered (added/removed/resized) or
     * {@link HttpHeaders trailers} are inserted to or removed from to the returned {@link Publisher}. To alter the
     * payload body content use {@link #transformPayloadBody(UnaryOperator)} method, its overloads, or
     * {@link #transform(TrailersTransformer)} method which can also be used to modify trailers.
     * @param transformer Responsible for transforming the message-body.
     * @return {@code this}.
     */
    StreamingHttpResponse transformMessageBody(UnaryOperator<Publisher<?>> transformer);

    /**
     * Returns a {@link StreamingHttpResponse} with its underlying payload transformed to {@link Buffer}s,
     * with access to the <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailer</a>s.
     * @param trailersTransformer {@link TrailersTransformer} to use for this transform.
     * @param <T> The type of state used during the transformation.
     * @return {@code this}
     */
    <T> StreamingHttpResponse transform(TrailersTransformer<T, Buffer> trailersTransformer);

    /**
     * Returns a {@link StreamingHttpResponse} with its underlying payload transformed to {@link S}s,
     * with access to the <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailer</a>s.
     * @param trailersTransformer {@link TrailersTransformer} to use for this transform.
     * @param deserializer Used to deserialize the existing payload body.
     * @param <T> The type of state used during the transformation.
     * @param <S> The type of objects to deserialize.
     * @return {@code this}
     */
    <T, S> StreamingHttpResponse transform(TrailersTransformer<T, S> trailersTransformer,
                                           HttpStreamingDeserializer<S> deserializer);

    /**
     * Translates this {@link StreamingHttpResponse} to a {@link HttpResponse}.
     * @return a {@link Single} that completes with a {@link HttpResponse} representation of this
     * {@link StreamingHttpResponse}.
     */
    Single<HttpResponse> toResponse();

    /**
     * Translates this {@link StreamingHttpResponse} to a {@link BlockingStreamingHttpResponse}.
     * @return a {@link BlockingStreamingHttpResponse} representation of this {@link StreamingHttpResponse}.
     */
    BlockingStreamingHttpResponse toBlockingStreamingResponse();

    @Override
    StreamingHttpResponse version(HttpProtocolVersion version);

    @Deprecated
    @Override
    StreamingHttpResponse encoding(ContentCodec encoding);

    @Override
    default StreamingHttpResponse addHeader(final CharSequence name, final CharSequence value) {
        HttpResponseMetaData.super.addHeader(name, value);
        return this;
    }

    @Override
    default StreamingHttpResponse addHeaders(final HttpHeaders headers) {
        HttpResponseMetaData.super.addHeaders(headers);
        return this;
    }

    @Override
    default StreamingHttpResponse setHeader(final CharSequence name, final CharSequence value) {
        HttpResponseMetaData.super.setHeader(name, value);
        return this;
    }

    @Override
    default StreamingHttpResponse setHeaders(final HttpHeaders headers) {
        HttpResponseMetaData.super.setHeaders(headers);
        return this;
    }

    @Override
    default StreamingHttpResponse addCookie(final HttpCookiePair cookie) {
        HttpResponseMetaData.super.addCookie(cookie);
        return this;
    }

    @Override
    default StreamingHttpResponse addCookie(final CharSequence name, final CharSequence value) {
        HttpResponseMetaData.super.addCookie(name, value);
        return this;
    }

    @Override
    default StreamingHttpResponse addSetCookie(final HttpSetCookie cookie) {
        HttpResponseMetaData.super.addSetCookie(cookie);
        return this;
    }

    @Override
    default StreamingHttpResponse addSetCookie(final CharSequence name, final CharSequence value) {
        HttpResponseMetaData.super.addSetCookie(name, value);
        return this;
    }
}
