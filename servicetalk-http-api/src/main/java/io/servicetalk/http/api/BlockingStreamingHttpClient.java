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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.BlockingStreamingHttpClientToStreamingHttpClient.BlockingToReservedStreamingHttpConnection;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;
import io.servicetalk.http.api.HttpClient.UpgradableHttpResponse;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient.UpgradableStreamingHttpResponse;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The equivalent of {@link StreamingHttpClient} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
public abstract class BlockingStreamingHttpClient extends BlockingStreamingHttpRequester {
    /**
     * Create a new instance.
     *
     * @param reqRespFactory The {@link BlockingStreamingHttpRequestResponseFactory} used to
     * {@link #newRequest(HttpRequestMethod, String) create new requests} and {@link #httpResponseFactory()}.
     */
    protected BlockingStreamingHttpClient(final BlockingStreamingHttpRequestResponseFactory reqRespFactory) {
        super(reqRespFactory);
    }

    /**
     * Reserve a {@link BlockingStreamingHttpConnection} for handling the provided {@link BlockingStreamingHttpRequest}
     * but <b>does not execute it</b>!
     *
     * @param request Allows the underlying layers to know what {@link BlockingStreamingHttpConnection}s are valid to
     * reserve. For example this may provide some insight into shard or other info.
     * @return a {@link ReservedStreamingHttpConnection}.
     * @throws Exception if a exception occurs during the reservation process.
     * @see StreamingHttpClient#reserveConnection(StreamingHttpRequest)
     */
    public abstract ReservedBlockingStreamingHttpConnection reserveConnection(
            BlockingStreamingHttpRequest request) throws Exception;

    /**
     * Attempt a <a href="https://tools.ietf.org/html/rfc7230.html#section-6.7">protocol upgrade</a>.
     * As part of the <a href="https://tools.ietf.org/html/rfc7230.html#section-6.7">protocol upgrade</a> process there
     * cannot be any pipelined requests pending or any pipeline requests issued during the upgrade process. That means
     * the {@link BlockingStreamingHttpConnection} associated with the {@link UpgradableBlockingStreamingHttpResponse}
     * will be reserved for exclusive use. The code responsible for determining the result of the upgrade attempt is
     * responsible for calling {@link UpgradableBlockingStreamingHttpResponse#httpConnection(boolean)}.
     *
     * @param request the request which initiates the upgrade.
     * @return An object that provides the {@link StreamingHttpResponse} for the upgrade attempt and also contains the
     * {@link BlockingStreamingHttpConnection} used for the upgrade.
     * @throws Exception if a exception occurs during the upgrade process.
     * @see StreamingHttpClient#upgradeConnection(StreamingHttpRequest)
     */
    public abstract UpgradableBlockingStreamingHttpResponse upgradeConnection(
            BlockingStreamingHttpRequest request) throws Exception;

    /**
     * Convert this {@link BlockingStreamingHttpClient} to the {@link StreamingHttpClient} API.
     * <p>
     * Note that the resulting {@link StreamingHttpClient} may still be subject to any blocking, in memory aggregation, and
     * other behavior as this {@link BlockingStreamingHttpClient}.
     *
     * @return a {@link StreamingHttpClient} representation of this {@link BlockingStreamingHttpClient}.
     */
    public final StreamingHttpClient asStreamingClient() {
        return asStreamingClientInternal();
    }

    /**
     * Convert this {@link BlockingStreamingHttpClient} to the {@link HttpClient} API.
     * <p>
     * Note that the resulting {@link HttpClient} may still be subject to any blocking, in memory aggregation,
     * and other behavior as this {@link BlockingStreamingHttpClient}.
     *
     * @return a {@link HttpClient} representation of this {@link BlockingStreamingHttpClient}.
     */
    public final HttpClient asClient() {
        return asStreamingClient().asClient();
    }

    /**
     * Convert this {@link BlockingStreamingHttpClient} to the {@link BlockingHttpClient} API.
     * <p>
     * Note that the resulting {@link BlockingHttpClient} may still be subject to in memory
     * aggregation and other behavior as this {@link BlockingStreamingHttpClient}.
     *
     * @return a {@link BlockingHttpClient} representation of this {@link BlockingStreamingHttpClient}.
     */
    public final BlockingHttpClient asBlockingClient() {
        return asStreamingClient().asBlockingClient();
    }

    StreamingHttpClient asStreamingClientInternal() {
        return new BlockingStreamingHttpClientToStreamingHttpClient(this);
    }

    /**
     * A special type of {@link BlockingStreamingHttpConnection} for the exclusive use of the caller of
     * {@link #reserveConnection(BlockingStreamingHttpRequest)}.
     * @see ReservedStreamingHttpConnection
     */
    public abstract static class ReservedBlockingStreamingHttpConnection extends BlockingStreamingHttpConnection {
        /**
         * Create a new instance.
         *
         * @param reqRespFactory The {@link BlockingStreamingHttpRequestResponseFactory} used to
         * {@link #newRequest(HttpRequestMethod, String) create new requests} and {@link #httpResponseFactory()}.
         */
        protected ReservedBlockingStreamingHttpConnection(
                final BlockingStreamingHttpRequestResponseFactory reqRespFactory) {
            super(reqRespFactory);
        }

        /**
         * Releases this reserved {@link BlockingStreamingHttpConnection} to be used for subsequent requests.
         * This method must be idempotent, i.e. calling multiple times must not have side-effects.
         *
         * @throws Exception if any exception occurs during releasing.
         */
        public abstract void release() throws Exception;

        /**
         * Convert this {@link ReservedBlockingStreamingHttpConnection} to the {@link ReservedStreamingHttpConnection}
         * API.
         * <p>
         * Note that the resulting {@link ReservedStreamingHttpConnection} may still be subject to any blocking, in
         * memory aggregation, and other behavior as this {@link ReservedBlockingStreamingHttpConnection}.
         *
         * @return a {@link ReservedStreamingHttpConnection} representation of this
         * {@link ReservedBlockingStreamingHttpConnection}.
         */
        public final ReservedStreamingHttpConnection asReservedStreamingConnection() {
            return asStreamingConnectionInternal();
        }

        /**
         * Convert this {@link ReservedBlockingStreamingHttpConnection} to the {@link ReservedHttpConnection} API.
         * <p>
         * Note that the resulting {@link ReservedHttpConnection} may still be subject to any blocking, in
         * memory aggregation, and other behavior as this {@link ReservedBlockingStreamingHttpConnection}.
         *
         * @return a {@link ReservedHttpConnection} representation of this
         * {@link ReservedBlockingStreamingHttpConnection}.
         */
        public final ReservedHttpConnection asReservedConnection() {
            return asReservedStreamingConnection().asReservedConnection();
        }

        /**
         * Convert this {@link ReservedBlockingStreamingHttpConnection} to the
         * {@link ReservedBlockingHttpConnection} API.
         * <p>
         * Note that the resulting {@link ReservedBlockingHttpConnection} may still be subject to in memory
         * aggregation and other behavior as this {@link ReservedBlockingStreamingHttpConnection}.
         *
         * @return a {@link ReservedBlockingHttpConnection} representation of this
         * {@link ReservedBlockingStreamingHttpConnection}.
         */
        public final ReservedBlockingHttpConnection asReservedBlockingConnection() {
            return asReservedStreamingConnection().asReservedBlockingConnection();
        }

        @Override
        ReservedStreamingHttpConnection asStreamingConnectionInternal() {
            return new BlockingToReservedStreamingHttpConnection(this);
        }
    }

    /**
     * A special type of response returned by upgrade requests {@link #upgradeConnection(BlockingStreamingHttpRequest)}.
     * This object allows the upgrade code to inform the HTTP implementation if the {@link HttpConnection} can
     * continue using the HTTP protocol or not.
     * @see UpgradableStreamingHttpResponse
     */
    public interface UpgradableBlockingStreamingHttpResponse extends BlockingStreamingHttpResponse {
        /**
         * Called by the code responsible for processing the upgrade response.
         * <p>
         * The caller of this method is responsible for calling
         * {@link ReservedBlockingStreamingHttpConnection#release()} on the
         * return value!
         *
         * @param releaseReturnsToClient
         * <ul>
         *     <li>{@code true} means the {@link BlockingStreamingHttpConnection} associated with the return value can
         *     be used by this {@link BlockingStreamingHttpClient} when
         *     {@link ReservedBlockingStreamingHttpConnection#release()} is called. This typically means the upgrade
         *     attempt was unsuccessful, but you can continue talking HTTP. However this may also be used if the upgrade
         *     was successful, but the upgrade protocol shares semantics that are similar enough to HTTP that the same
         *     {@link BlockingStreamingHttpClient} API can still be used (e.g. HTTP/2).</li>
         *     <li>{@code false} means the {@link BlockingStreamingHttpConnection} associated with the return value can
         *     <strong>not</strong> be used by this {@link BlockingStreamingHttpClient} when
         *     {@link ReservedBlockingStreamingHttpConnection#release()} is called. This typically means the upgrade
         *     attempt was successful and the semantics of the upgrade protocol are sufficiently different that the
         *     {@link BlockingStreamingHttpClient} API no longer makes sense.</li>
         * </ul>
         * @return A {@link ReservedBlockingStreamingHttpConnection} which contains the
         * {@link BlockingStreamingHttpConnection} used for the upgrade attempt, and controls the lifetime of the
         * {@link BlockingStreamingHttpConnection} relative to this {@link BlockingStreamingHttpClient}.
         */
        ReservedBlockingStreamingHttpConnection httpConnection(boolean releaseReturnsToClient);

        @Override
        UpgradableBlockingStreamingHttpResponse payloadBody(Iterable<Buffer> payloadBody);

        @Override
        UpgradableBlockingStreamingHttpResponse payloadBody(CloseableIterable<Buffer> payloadBody);

        @Override
        <T> UpgradableBlockingStreamingHttpResponse payloadBody(Iterable<T> payloadBody,
                                                                HttpSerializer<T> serializer);

        @Override
        <T> UpgradableBlockingStreamingHttpResponse payloadBody(CloseableIterable<T> payloadBody,
                                                                HttpSerializer<T> serializer);

        @Override
        <T> UpgradableBlockingStreamingHttpResponse transformPayloadBody(
                Function<BlockingIterable<Buffer>, BlockingIterable<T>> transformer, HttpSerializer<T> serializer);

        @Override
        default <T, R> UpgradableBlockingStreamingHttpResponse transformPayloadBody(
                Function<BlockingIterable<T>, BlockingIterable<R>> transformer, HttpDeserializer<T> deserializer,
                HttpSerializer<R> serializer) {
            return transformPayloadBody(buffers -> transformer.apply(payloadBody(deserializer)), serializer);
        }

        @Override
        UpgradableBlockingStreamingHttpResponse transformPayloadBody(UnaryOperator<BlockingIterable<Buffer>> transformer);

        @Override
        UpgradableBlockingStreamingHttpResponse transformRawPayloadBody(UnaryOperator<BlockingIterable<?>> transformer);

        @Override
        <T> UpgradableBlockingStreamingHttpResponse transform(
                Supplier<T> stateSupplier, BiFunction<Buffer, T, Buffer> transformer,
                BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer);

        @Override
        <T> UpgradableBlockingStreamingHttpResponse transformRaw(
                Supplier<T> stateSupplier, BiFunction<Object, T, ?> transformer,
                BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer);

        @Override
        Single<? extends UpgradableHttpResponse> toResponse();

        @Override
        UpgradableStreamingHttpResponse toStreamingResponse();

        @Override
        UpgradableBlockingStreamingHttpResponse version(HttpProtocolVersion version);

        @Override
        UpgradableBlockingStreamingHttpResponse status(HttpResponseStatus status);
    }
}
