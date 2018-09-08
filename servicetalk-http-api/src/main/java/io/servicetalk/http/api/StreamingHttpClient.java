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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.BlockingStreamingHttpClient.ReservedBlockingStreamingHttpConnection;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;
import io.servicetalk.http.api.StreamingHttpClientToBlockingHttpClient.ReservedStreamingHttpConnectionToBlocking;
import io.servicetalk.http.api.StreamingHttpClientToBlockingStreamingHttpClient.ReservedStreamingHttpConnectionToBlockingStreaming;
import io.servicetalk.http.api.StreamingHttpClientToHttpClient.ReservedStreamingHttpConnectionToReservedHttpConnection;

import java.util.function.Function;

/**
 * Provides a means to issue requests against HTTP service. The implementation is free to maintain a collection of
 * {@link StreamingHttpConnection} instances and distribute calls to {@link #request(StreamingHttpRequest)} amongst this
 * collection.
 */
public abstract class StreamingHttpClient extends StreamingHttpRequester {
    /**
     * Reserve a {@link StreamingHttpConnection} for handling the provided {@link StreamingHttpRequest}
     * but <b>does not execute it</b>!
     * @param request Allows the underlying layers to know what {@link StreamingHttpConnection}s are valid to reserve.
     * For example this may provide some insight into shard or other info.
     * @return a {@link ReservedStreamingHttpConnection}.
     */
    public abstract Single<? extends ReservedStreamingHttpConnection> reserveConnection(
            StreamingHttpRequest<HttpPayloadChunk> request);

    /**
     * Attempt a <a href="https://tools.ietf.org/html/rfc7230.html#section-6.7">protocol upgrade</a>.
     * As part of the <a href="https://tools.ietf.org/html/rfc7230.html#section-6.7">protocol upgrade</a> process there
     * cannot be any pipelined requests pending or any pipeline requests issued during the upgrade process. That means
     * the {@link StreamingHttpConnection} associated with the {@link UpgradableStreamingHttpResponse} will be reserved
     * for exclusive use. The code responsible for determining the result of the upgrade attempt is responsible for
     * calling {@link UpgradableStreamingHttpResponse#getHttpConnection(boolean)}.
     * @param request the request which initiates the upgrade.
     * @return An object that provides the {@link StreamingHttpResponse} for the upgrade attempt and also contains the
     * {@link StreamingHttpConnection} used for the upgrade.
     */
    public abstract Single<? extends UpgradableStreamingHttpResponse<HttpPayloadChunk>> upgradeConnection(
            StreamingHttpRequest<HttpPayloadChunk> request);

    /**
     * Convert this {@link StreamingHttpClient} to the {@link HttpClient} API.
     * <p>
     * This API is provided for convenience. It is recommended that
     * filters are implemented using the {@link StreamingHttpClient} asynchronous API for maximum portability.
     * @return a {@link HttpClient} representation of this {@link StreamingHttpRequester}.
     */
    public final HttpClient asClient() {
        return asClientInternal();
    }

    /**
     * Convert this {@link StreamingHttpClient} to the {@link BlockingStreamingHttpClient} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link StreamingHttpClient} asynchronous API for maximum portability.
     * @return a {@link BlockingStreamingHttpClient} representation of this {@link StreamingHttpClient}.
     */
    public final BlockingStreamingHttpClient asBlockingStreamingClient() {
        return asBlockingStreamingClientInternal();
    }

    /**
     * Convert this {@link StreamingHttpClient} to the {@link BlockingHttpClient} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link StreamingHttpClient} asynchronous API for maximum portability.
     * @return a {@link BlockingHttpClient} representation of this {@link StreamingHttpClient}.
     */
    public final BlockingHttpClient asBlockingClient() {
        return asBlockingClientInternal();
    }

    HttpClient asClientInternal() {
        return new StreamingHttpClientToHttpClient(this);
    }

    BlockingStreamingHttpClient asBlockingStreamingClientInternal() {
        return new StreamingHttpClientToBlockingStreamingHttpClient(this);
    }

    BlockingHttpClient asBlockingClientInternal() {
        return new StreamingHttpClientToBlockingHttpClient(this);
    }

    /**
     * A special type of {@link StreamingHttpConnection} for the exclusive use of the caller of
     * {@link #reserveConnection(StreamingHttpRequest)}.
     */
    public abstract static class ReservedStreamingHttpConnection extends StreamingHttpConnection {
        /**
         * Releases this reserved {@link StreamingHttpConnection} to be used for subsequent requests.
         * This method must be idempotent, i.e. calling multiple times must not have side-effects.
         *
         * @return the {@code Completable} that is notified on releaseAsync.
         */
        public abstract Completable releaseAsync();

        /**
         * Convert this {@link ReservedStreamingHttpConnection} to the {@link ReservedHttpConnection} API.
         * <p>
         * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
         * filters are implemented using the {@link ReservedStreamingHttpConnection} asynchronous API for maximum
         * portability.
         * @return a {@link HttpClient.ReservedHttpConnection} representation of this
         * {@link ReservedStreamingHttpConnection}.
         */
        public final ReservedHttpConnection asReservedConnection() {
            return asConnectionInternal();
        }

        /**
         * Convert this {@link ReservedStreamingHttpConnection} to the {@link BlockingStreamingHttpClient} API.
         * <p>
         * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
         * filters are implemented using the {@link ReservedStreamingHttpConnection} asynchronous API for maximum
         * portability.
         * @return a {@link BlockingStreamingHttpClient} representation of this {@link ReservedStreamingHttpConnection}.
         */
        public final ReservedBlockingStreamingHttpConnection asReservedBlockingStreamingConnection() {
            return asBlockingStreamingConnectionInternal();
        }

        /**
         * Convert this {@link ReservedStreamingHttpConnection} to the {@link ReservedBlockingHttpConnection} API.
         * <p>
         * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
         * filters are implemented using the {@link ReservedStreamingHttpConnection} asynchronous API for maximum
         * portability.
         * @return a {@link ReservedBlockingHttpConnection} representation of this
         * {@link ReservedStreamingHttpConnection}.
         */
        public final ReservedBlockingHttpConnection asReservedBlockingConnection() {
            return asBlockingConnectionInternal();
        }

        @Override
        ReservedHttpConnection asConnectionInternal() {
            return new ReservedStreamingHttpConnectionToReservedHttpConnection(this);
        }

        @Override
        ReservedBlockingStreamingHttpConnection asBlockingStreamingConnectionInternal() {
            return new ReservedStreamingHttpConnectionToBlockingStreaming(this);
        }

        @Override
        ReservedBlockingHttpConnection asBlockingConnectionInternal() {
            return new ReservedStreamingHttpConnectionToBlocking(this);
        }
    }

    /**
     * A special type of response returned by upgrade requests {@link #upgradeConnection(StreamingHttpRequest)}. This
     * object allows the upgrade code to inform the HTTP implementation if the {@link StreamingHttpConnection} can
     * continue using the HTTP protocol or not.
     * @param <T> The type of data in the {@link StreamingHttpResponse}.
     */
    public interface UpgradableStreamingHttpResponse<T> extends StreamingHttpResponse<T> {
        /**
         * Called by the code responsible for processing the upgrade response.
         * <p>
         * The caller of this method is responsible for calling {@link ReservedStreamingHttpConnection#releaseAsync()}
         * on the return value!
         * @param releaseReturnsToClient
         * <ul>
         *     <li>{@code true} means the {@link StreamingHttpConnection} associated with the return value can be used
         *     by this {@link StreamingHttpClient} when {@link ReservedStreamingHttpConnection#releaseAsync()} is
         *     called. This typically means the upgrade attempt was unsuccessful, but you can continue talking HTTP.
         *     However this may also be used if the upgrade was successful, but the upgrade protocol shares semantics
         *     that are similar enough to HTTP that the same {@link StreamingHttpClient} API can still be used
         *     (e.g. HTTP/2).</li>
         *     <li>{@code false} means the {@link StreamingHttpConnection} associated with the return value can
         *     <strong>not</strong> be used by this {@link StreamingHttpClient} when
         *     {@link ReservedStreamingHttpConnection#releaseAsync()} is called. This typically means the upgrade
         *     attempt was successful and the semantics of the upgrade protocol are sufficiently different that the
         *     {@link StreamingHttpClient} API no longer makes sense.</li>
         * </ul>
         * @return A {@link ReservedStreamingHttpConnection} which contains the {@link StreamingHttpConnection} used for
         * the upgrade attempt, and controls the lifetime of the {@link StreamingHttpConnection} relative to this
         * {@link StreamingHttpClient}.
         */
        ReservedStreamingHttpConnection getHttpConnection(boolean releaseReturnsToClient);

        @Override
        <R> UpgradableStreamingHttpResponse<R> transformPayloadBody(Function<Publisher<T>, Publisher<R>> transformer);

        @Override
        UpgradableStreamingHttpResponse<T> setVersion(HttpProtocolVersion version);

        @Override
        UpgradableStreamingHttpResponse<T> setStatus(HttpResponseStatus status);
    }
}
