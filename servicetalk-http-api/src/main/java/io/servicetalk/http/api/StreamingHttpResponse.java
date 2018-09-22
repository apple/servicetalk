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

import org.reactivestreams.Subscriber;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The equivalent of {@link HttpResponse} but provides the payload as a {@link Publisher}.
 */
public interface StreamingHttpResponse extends HttpResponseMetaData {

    /**
     * Get the underlying payload as a {@link Publisher} of {@link Buffer}s.
     * @return The {@link Publisher} of {@link Buffer} representation of the underlying
     */
    Publisher<Buffer> payloadBody();

    /**
     * Get and deserialize the payload body.
     * @param deserializer The function that deserializes the underlying {@link Publisher}.
     * @param <T> The resulting type of the deserialization operation.
     * @return The results of the deserialization operation.
     */
    default <T> Publisher<T> deserializePayloadBody(HttpDeserializer<T> deserializer) {
        return deserializer.deserialize(headers(), payloadBody());
    }

    /**
     * Get a {@link Publisher} that combines the raw payload body concatenated with the {@link HttpHeaders trailers}.
     * @return a {@link Publisher} that combines the raw payload body concatenated with the
     * {@link HttpHeaders trailers}.
     */
    Publisher<Object> payloadBodyAndTrailers();

    /**
     * Set the underlying payload body.
     * <p>
     * A best effort will be made to apply back pressure to the existing {@link Publisher} payload body. If this default
     * policy is not sufficient you can use {@link #transformPayloadBody(UnaryOperator)} for more fine grain control.
     * <p>
     * This method reserves the right to delay completion/consumption of {@code payloadBody}. This may occur due to the
     * combination with the existing {@link Publisher} payload body.
     * @param payloadBody The new payload body.
     * @return A {@link StreamingHttpResponse} with the new serialized payload body.
     */
    StreamingHttpResponse payloadBody(Publisher<Buffer> payloadBody);

    /**
     * Set the underlying payload body with the result of serialization.
     * <p>
     * A best effort will be made to apply back pressure to the existing {@link Publisher} payload body. If this default
     * policy is not sufficient you can use {@link #transformPayloadBody(Function, HttpSerializer)} for more fine grain
     * control.
     * <p>
     * This method reserves the right to delay completion/consumption of {@code payloadBody}. This may occur due to the
     * combination with the existing {@link Publisher} payload body.
     * @param payloadBody The new payload body, prior to serialization.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to serialize.
     * @return A {@link StreamingHttpResponse} with the new serialized payload body.
     */
    <T> StreamingHttpResponse serializePayloadBody(Publisher<T> payloadBody, HttpSerializer<T> serializer);

    /**
     * Transform the underlying payload body with the result of serialization.
     * @param transformer A {@link Function} which take as a parameter the existing payload body {@link Publisher} and
     * returns the new payload body {@link Publisher} prior to serialization. It is assumed the existing payload body
     * {@link Publisher} will be transformed/consumed or else no more responses may be processed.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to serialize.
     * @return A {@link StreamingHttpResponse} with the new serialized payload body.
     */
    <T> StreamingHttpResponse transformPayloadBody(Function<Publisher<Buffer>, Publisher<T>> transformer,
                                                   HttpSerializer<T> serializer);

    /**
     * Transform the underlying payload body with the result of serialization.
     * @param transformer A {@link Function} which take as a parameter the existing payload body {@link Publisher} and
     * returns the new payload body {@link Publisher} prior to serialization. It is assumed the existing payload body
     * {@link Publisher} will be transformed/consumed or else no more requests may be processed.
     * @param deserializer Used to deserialize the existing payload body.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to deserialize.
     * @param <R> The type of objects to serialize.
     * @return A {@link StreamingHttpRequest} with the new serialized payload body.
     */
    default <T, R> StreamingHttpResponse transformPayloadBody(Function<Publisher<T>, Publisher<R>> transformer,
                                                              HttpDeserializer<T> deserializer,
                                                              HttpSerializer<R> serializer) {
        return transformPayloadBody(bufferPublisher ->
                transformer.apply(deserializer.deserialize(headers(), bufferPublisher)), serializer);
    }

    /**
     * Transform the underlying payload body in the form of {@link Buffer}s.
     * @param transformer A {@link Function} which take as a parameter the existing payload body {@link Publisher} and
     * returns the new payload body {@link Publisher}. It is assumed the existing payload body {@link Publisher} will be
     * transformed/consumed or else no more responses may be processed.
     * @return A {@link StreamingHttpResponse} with the new payload body.
     */
    StreamingHttpResponse transformPayloadBody(UnaryOperator<Publisher<Buffer>> transformer);

    /**
     * Transform the underlying payload body. Note that the raw objects of the underlying {@link Publisher} may be
     * exposed. The object types are not guaranteed to be homogeneous.
     * @param transformer Responsible for transforming the payload body.
     * @return A {@link StreamingHttpResponse} with the new payload body.
     */
    StreamingHttpResponse transformRawPayloadBody(UnaryOperator<Publisher<?>> transformer);

    /**
     * Transform the underlying payload body in the form of {@link Buffer}s with access to the trailers.
     * @param stateSupplier Create a new state {@link Object} that will be provided to the {@code transformer} on each
     * invocation. The state will be persistent for each {@link Subscriber} of the underlying payload body.
     * @param transformer Responsible for transforming each {@link Buffer} of the payload body.
     * @param trailersTransformer Invoked after all payload has been consumed with the state and the trailers. The
     * return value of this {@link BiFunction} will be the trailers for the {@link StreamingHttpResponse}.
     * @param <T> The type of state used during the transformation.
     * @return A {@link StreamingHttpResponse} with the new payload body.
     */
    <T> StreamingHttpResponse transform(Supplier<T> stateSupplier,
                                        BiFunction<Buffer, T, Buffer> transformer,
                                        BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer);

    /**
     * Transform the underlying payload body in the form of {@link Object}s with access to the trailers.
     * @param stateSupplier Create a new state {@link Object} that will be provided to the {@code transformer} on each
     * invocation. The state will be persistent for each {@link Subscriber} of the underlying payload body.
     * @param transformer Responsible for transforming each {@link Object} of the payload body.
     * @param trailersTransformer Invoked after all payload has been consumed with the state and the trailers. The
     * return value of this {@link BiFunction} will be the trailers for the {@link StreamingHttpResponse}.
     * @param <T> The type of state used during the transformation.
     * @return A {@link StreamingHttpResponse} with the new payload body.
     */
    <T> StreamingHttpResponse transformRaw(Supplier<T> stateSupplier,
                                           BiFunction<Object, T, ?> transformer,
                                           BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer);

    /**
     * Translate this {@link StreamingHttpResponse} to a {@link HttpResponse}.
     * @return a {@link Single} that completes with a {@link HttpResponse} representation of this
     * {@link StreamingHttpResponse}.
     */
    Single<? extends HttpResponse> toResponse();

    /**
     * Translate this {@link StreamingHttpResponse} to a {@link BlockingStreamingHttpResponse}.
     * @return a {@link BlockingStreamingHttpResponse} representation of this {@link StreamingHttpResponse}.
     */
    BlockingStreamingHttpResponse toBlockingStreamingResponse();

    @Override
    StreamingHttpResponse version(HttpProtocolVersion version);

    @Override
    StreamingHttpResponse status(HttpResponseStatus status);
}
