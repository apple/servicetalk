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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import org.reactivestreams.Subscriber;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The equivalent of {@link HttpRequest} but provides the payload as a {@link BlockingIterable}.
 */
public interface BlockingStreamingHttpRequest extends HttpRequestMetaData {
    /**
     * Get the underlying payload as a {@link Publisher} of {@link Buffer}s.
     * @return The {@link Publisher} of {@link Buffer} representation of the underlying
     */
    default BlockingIterable<Buffer> getPayloadBody() {
        return HttpSerializerUtils.getPayloadBody(this);
    }

    /**
     * Get and deserialize the payload body.
     * @param deserializer The function that deserializes the underlying {@link BlockingIterable}.
     * @param <T> The resulting type of the deserialization operation.
     * @return The results of the deserialization operation.
     */
    <T> BlockingIterable<T> getPayloadBody(HttpDeserializer<T> deserializer);

    /**
     * Transform the underlying payload body with the result of serialization.
     * <p>
     * Note this method has the following caveats:
     * <ul>
     *     <li>back pressure on the existing payload body will not be respected!</li>
     *     <li>The new payload body {@link BlockingIterable} may not complete until the existing payload body
     *     completes!</li>
     * </ul>
     * It is preferred to serialize the content during {@link BlockingStreamingHttpRequest} creation to avoid this
     * ambiguity.
     * @param payloadBody The new payload body, prior to serialization.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to serialize.
     * @return A {@link BlockingStreamingHttpRequest} with the new serialized payload body.
     */
    default <T> BlockingStreamingHttpRequest transformPayloadBody(BlockingIterable<T> payloadBody,
                                                                  HttpSerializer<T> serializer) {
        // Ignore content of original Publisher (payloadBody). Due to the blocking APIs we will consume the payload in
        // a blocking fashion, this default behavior can be overriden via the method overload and may change to be in
        // parallel later.
        return transformPayloadBody(old -> {
            old.forEach(buffer -> { });
            return payloadBody;
        }, serializer);
    }

    /**
     * Transform the underlying payload body with the result of serialization.
     * @param transformer A {@link Function} which take as a parameter the existing payload body
     * {@link BlockingIterable} and returns the new payload body {@link BlockingIterable} prior to serialization. It is
     * assumed the existing payload body {@link BlockingIterable} will be transformed/consumed or else no more requests
     * may be processed.
     * @param serializer Used to serialize the payload body.
     * @param <T> The type of objects to serialize.
     * @return A {@link BlockingStreamingHttpRequest} with the new serialized payload body.
     */
    <T> BlockingStreamingHttpRequest transformPayloadBody(
            Function<BlockingIterable<Buffer>, BlockingIterable<T>> transformer, HttpSerializer<T> serializer);

    /**
     * Transform the underlying payload body in the form of {@link Buffer}s.
     * @param transformer A {@link Function} which take as a parameter the existing payload body
     * {@link BlockingIterable} and returns the new payload body {@link BlockingIterable}. It is assumed the existing
     * payload body {@link BlockingIterable} will be transformed/consumed or else no more requests may be processed.
     * @return A {@link BlockingStreamingHttpRequest} with the new payload body.
     */
    BlockingStreamingHttpRequest transformPayloadBody(UnaryOperator<BlockingIterable<Buffer>> transformer);

    /**
     * Transform the underlying payload body. Note that the raw objects of the underlying {@link Iterable} may be
     * exposed. The object types are not guaranteed to be homogeneous.
     * @param transformer Responsible for transforming the payload body.
     * @return A {@link BlockingStreamingHttpRequest} with the new payload body.
     */
    BlockingStreamingHttpRequest transformRawPayloadBody(UnaryOperator<BlockingIterable<?>> transformer);

    /**
     * Transform the underlying payload body in the form of {@link Buffer}s with access to the trailers.
     * @param stateSupplier Create a new state {@link Object} that will be provided to the {@code transformer} on each
     * invocation. The state will be persistent for each {@link Subscriber} of the underlying payload body.
     * @param transformer Responsible for transforming each {@link Buffer} of the payload body.
     * @param trailersTransformer Invoked after all payload has been consumed with the state and the trailers. The
     * return value of this {@link BiFunction} will be the trailers for the {@link BlockingStreamingHttpRequest}.
     * @param <T> The type of state used during the transformation.
     * @return A {@link BlockingStreamingHttpRequest} with the new payload body.
     */
    <T> BlockingStreamingHttpRequest transform(Supplier<T> stateSupplier,
                                               BiFunction<Buffer, T, Buffer> transformer,
                                               BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer);

    /**
     * Transform the underlying payload body in the form of {@link Object}s with access to the trailers.
     * @param stateSupplier Create a new state {@link Object} that will be provided to the {@code transformer} on each
     * invocation. The state will be persistent for each {@link Subscriber} of the underlying payload body.
     * @param transformer Responsible for transforming each {@link Object} of the payload body.
     * @param trailersTransformer Invoked after all payload has been consumed with the state and the trailers. The
     * return value of this {@link BiFunction} will be the trailers for the {@link BlockingStreamingHttpRequest}.
     * @param <T> The type of state used during the transformation.
     * @return A {@link BlockingStreamingHttpRequest} with the new payload body.
     */
    <T> BlockingStreamingHttpRequest transformRaw(Supplier<T> stateSupplier,
                                                  BiFunction<Object, T, ?> transformer,
                                                  BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer);

    /**
     * Translate this {@link BlockingStreamingHttpRequest} to a {@link HttpRequest}.
     * @return a {@link Single} that completes with a {@link HttpRequest} representation of this
     * {@link BlockingStreamingHttpRequest}.
     */
    Single<? extends HttpRequest> toRequest();

    /**
     * Translate this {@link BlockingStreamingHttpRequest} to a {@link StreamingHttpRequest}.
     * @return a {@link StreamingHttpRequest} representation of this {@link BlockingStreamingHttpRequest}.
     */
    StreamingHttpRequest toStreamingRequest();

    @Override
    BlockingStreamingHttpRequest setRawPath(String path);

    @Override
    BlockingStreamingHttpRequest setPath(String path);

    @Override
    BlockingStreamingHttpRequest setRawQuery(String query);

    @Override
    BlockingStreamingHttpRequest setVersion(HttpProtocolVersion version);

    @Override
    BlockingStreamingHttpRequest setMethod(HttpRequestMethod method);

    @Override
    BlockingStreamingHttpRequest setRequestTarget(String requestTarget);
}
