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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.BlockingStreamingHttpClient.ReservedBlockingStreamingHttpConnection;
import io.servicetalk.http.api.HttpClientToBlockingHttpClient.ReservedHttpConnectionToReservedBlockingHttpConnection;
import io.servicetalk.http.api.HttpClientToStreamingHttpClient.ReservedHttpConnectionToReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient.UpgradableStreamingHttpResponse;

import java.util.function.Function;

/**
 * The equivalent of {@link StreamingHttpClient} but that accepts {@link HttpRequest} and returns
 * {@link HttpResponse}.
 */
public abstract class HttpClient extends HttpRequester {
    /**
     * Reserve a {@link HttpConnection} for handling the provided {@link HttpRequest}
     * but <b>does not execute it</b>!
     *
     * @param request Allows the underlying layers to know what {@link HttpConnection}s are valid to reserve.
     * For example this may provide some insight into shard or other info.
     * @return a {@link Single} that provides the {@link ReservedHttpConnection} upon completion.
     * @see StreamingHttpClient#reserveConnection(StreamingHttpRequest)
     */
    public abstract Single<? extends ReservedHttpConnection> reserveConnection(
            HttpRequest<HttpPayloadChunk> request);

    /**
     * Attempt a <a href="https://tools.ietf.org/html/rfc7230.html#section-6.7">protocol upgrade</a>.
     * As part of the <a href="https://tools.ietf.org/html/rfc7230.html#section-6.7">protocol upgrade</a> process there
     * cannot be any pipelined requests pending or any pipeline requests issued during the upgrade process. That means
     * the {@link HttpConnection} associated with the {@link UpgradableHttpResponse} will be
     * reserved for exclusive use. The code responsible for determining the result of the upgrade attempt is responsible
     * for calling {@link UpgradableHttpResponse#getHttpConnection(boolean)}.
     *
     * @param request the request which initiates the upgrade.
     * @return An object that provides the {@link UpgradableHttpResponse} for the upgrade attempt and also
     * contains the {@link HttpConnection} used for the upgrade.
     * @see StreamingHttpClient#upgradeConnection(StreamingHttpRequest)
     */
    public abstract Single<? extends UpgradableHttpResponse<HttpPayloadChunk>> upgradeConnection(
            HttpRequest<HttpPayloadChunk> request);

    /**
     * Convert this {@link HttpClient} to the {@link StreamingHttpClient} API.
     *
     * @return a {@link StreamingHttpClient} representation of this {@link HttpClient}.
     */
    public final StreamingHttpClient asStreamingClient() {
        return asStreamingClientInternal();
    }

    /**
     * Convert this {@link HttpClient} to the {@link BlockingStreamingHttpClient} API.
     *
     * @return a {@link BlockingStreamingHttpClient} representation of this {@link HttpClient}.
     */
    public final BlockingStreamingHttpClient asBlockingStreamingClient() {
        return asStreamingClient().asBlockingStreamingClient();
    }

    /**
     * Convert this {@link HttpClient} to the {@link BlockingHttpClient} API.
     *
     * @return a {@link BlockingHttpClient} representation of this {@link HttpClient}.
     */
    public final BlockingHttpClient asBlockingClient() {
        return asBlockingClientInternal();
    }

    StreamingHttpClient asStreamingClientInternal() {
        return new HttpClientToStreamingHttpClient(this);
    }

    BlockingHttpClient asBlockingClientInternal() {
        return new HttpClientToBlockingHttpClient(this);
    }

    /**
     * A special type of {@link HttpConnection} for the exclusive use of the caller of
     * {@link #reserveConnection(HttpRequest)}.
     * @see ReservedStreamingHttpConnection
     */
    public abstract static class ReservedHttpConnection extends HttpConnection {
        /**
         * Releases this reserved {@link ReservedHttpConnection} to be used for subsequent requests.
         * This method must be idempotent, i.e. calling multiple times must not have side-effects.
         *
         * @return the {@code Completable} that is notified on releaseAsync.
         */
        public abstract Completable releaseAsync();

        /**
         * Convert this {@link ReservedHttpConnection} to the {@link ReservedStreamingHttpConnection} API.
         *
         * @return a {@link ReservedStreamingHttpConnection} representation of this {@link ReservedHttpConnection}.
         */
        public final ReservedStreamingHttpConnection asReservedStreamingConnection() {
            return asStreamingConnectionInternal();
        }

        /**
         * Convert this {@link ReservedHttpConnection} to the {@link ReservedBlockingStreamingHttpConnection} API.
         *
         * @return a {@link ReservedBlockingStreamingHttpConnection} representation of this
         * {@link ReservedHttpConnection}.
         */
        public final ReservedBlockingStreamingHttpConnection asReservedBlockingStreamingConnection() {
            return asReservedStreamingConnection().asReservedBlockingStreamingConnection();
        }

        /**
         * Convert this {@link ReservedHttpConnection} to the {@link ReservedBlockingHttpConnection}
         * API.
         *
         * @return a {@link ReservedBlockingHttpConnection} representation of this
         * {@link ReservedHttpConnection}.
         */
        public final ReservedBlockingHttpConnection asReservedBlockingConnection() {
            return asBlockingConnectionInternal();
        }

        @Override
        ReservedStreamingHttpConnection asStreamingConnectionInternal() {
            return new ReservedHttpConnectionToReservedStreamingHttpConnection(this);
        }

        @Override
        ReservedBlockingHttpConnection asBlockingConnectionInternal() {
            return new ReservedHttpConnectionToReservedBlockingHttpConnection(this);
        }
    }

    /**
     * A special type of response returned by upgrade requests {@link #upgradeConnection(HttpRequest)}. This object
     * allows the upgrade code to inform the HTTP implementation if the {@link HttpConnection} can continue
     * using the HTTP protocol or not.
     * @param <T> The type of data in the {@link StreamingHttpResponse}.
     * @see UpgradableStreamingHttpResponse
     */
    public interface UpgradableHttpResponse<T> extends HttpResponse<T> {
        /**
         * Called by the code responsible for processing the upgrade response.
         * <p>
         * The caller of this method is responsible for calling {@link ReservedHttpConnection#releaseAsync()}
         * on the return value!
         *
         * @param releaseReturnsToClient
         * <ul>
         *     <li>{@code true} means the {@link StreamingHttpConnection} associated with the return value can be used by
         *     this {@link HttpClient} when {@link ReservedHttpConnection#releaseAsync()} is called.
         *     This typically means the upgrade attempt was unsuccessful, but you can continue talking HTTP. However
         *     this may also be used if the upgrade was successful, but the upgrade protocol shares semantics that are
         *     similar enough to HTTP that the same {@link HttpClient} API can still be used
         *     (e.g. HTTP/2).</li>
         *     <li>{@code false} means the {@link StreamingHttpConnection} associated with the return value can
         *     <strong>not</strong> be used by this {@link HttpClient} when
         *     {@link ReservedHttpConnection#releaseAsync()} is called. This typically means the upgrade
         *     attempt was successful and the semantics of the upgrade protocol are sufficiently different that the
         *     {@link HttpClient} API no longer makes sense.</li>
         * </ul>
         * @return A {@link ReservedHttpConnection} which contains the {@link HttpConnection} used
         * for the upgrade attempt, and controls the lifetime of the {@link StreamingHttpConnection} relative to this
         * {@link HttpClient}.
         */
        ReservedHttpConnection getHttpConnection(boolean releaseReturnsToClient);

        @Override
        <R> UpgradableHttpResponse<R> transformPayloadBody(Function<T, R> transformer);

        @Override
        UpgradableHttpResponse<T> setVersion(HttpProtocolVersion version);

        @Override
        UpgradableHttpResponse<T> setStatus(HttpResponseStatus status);
    }
}
